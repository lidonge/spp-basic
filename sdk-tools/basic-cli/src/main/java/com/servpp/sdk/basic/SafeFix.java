package com.servpp.sdk.basic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.lang.lint.RedundancyLint.Edit;
import com.servpp.sdk.CompilationException;
import com.servpp.sdk.SourceSnapshot;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class SafeFix {
    interface Check { Map<String,Object> run() throws Exception; }
    private record Changed(Path path, String original, String replacement) { }

    static Map<String,Object> apply(Path root, String code, Check check) throws Exception {
        var report = check.run();
        int applied = 0;
        var json = new ObjectMapper();
        while (Boolean.TRUE.equals(report.get("checked"))) {
            JsonNode diagnostics = (JsonNode) report.get("diagnostics");
            Map<String,List<Edit>> selected = new TreeMap<>();
            int groups = 0;
            for (var diagnostic : diagnostics) {
                if (!diagnostic.path("safe").asBoolean() || code != null && !code.equals(diagnostic.path("code").asText())) continue;
                List<Edit> edits = Arrays.asList(json.treeToValue(diagnostic.path("edits"), Edit[].class));
                // Keep a multi-file suggestion together. Recompute deferred conflicts after this batch.
                if (edits.stream().anyMatch(e -> selected.getOrDefault(e.path(), List.of()).stream().anyMatch(other ->
                        e.start() == other.start() || e.start() < other.end() && other.start() < e.end()))) continue;
                for (Edit edit : edits) selected.computeIfAbsent(edit.path(), ignored -> new ArrayList<>()).add(edit);
                groups++;
            }
            if (selected.isEmpty()) return Map.of("checked", true, "applied", applied);
            var changes = new ArrayList<Changed>();
            JsonNode hashes = (JsonNode) report.get("hashes");
            for (var entry : selected.entrySet()) {
                Path path = root.resolve(entry.getKey()).normalize();
                if (!path.startsWith(root) || !path.toRealPath().equals(path)) throw new IOException("Invalid edit path.");
                String original = Files.readString(path);
                if (!SourceSnapshot.hash(path).equals(hashes.path(entry.getKey()).asText()))
                    throw new IOException("Source changed during analysis: " + entry.getKey());
                var text = new StringBuilder(original);
                int previous = original.length();
                for (Edit edit : entry.getValue().stream().sorted(Comparator.comparingInt(Edit::start).reversed()).toList()) {
                    if (edit.start() < 0 || edit.end() < edit.start() || edit.end() > previous)
                        throw new IOException("Invalid edit range for " + entry.getKey());
                    text.replace(edit.start(), edit.end(), edit.replacement());
                    previous = edit.start();
                }
                if (original.contentEquals(text)) throw new IOException("Edit did not change " + entry.getKey());
                changes.add(new Changed(path, original, text.toString()));
            }
            var written = new ArrayList<Changed>();
            try {
                for (var change : changes) {
                    replace(change.path(), change.original(), change.replacement());
                    written.add(change);
                }
                report = check.run();
                if (!Boolean.TRUE.equals(report.get("checked"))) throw new IOException("Autofix failed compiler re-check.");
            } catch (Exception failed) {
                Collections.reverse(written);
                for (var change : written) try { replace(change.path(), change.replacement(), change.original()); }
                catch (IOException conflict) { failed.addSuppressed(conflict); }
                throw failed;
            }
            applied += groups;
        }
        throw new CompilationException("CHECK_FAILED", "Correct compilation errors before applying safe fixes.");
    }

    private static void replace(Path path, String expected, String replacement) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), ".spp-fix-", ".tmp");
        try {
            Files.copy(path, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Files.writeString(temp, replacement);
            if (!path.toRealPath().equals(path) || !expected.equals(Files.readString(path)))
                throw new IOException("Concurrent change to " + path);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
