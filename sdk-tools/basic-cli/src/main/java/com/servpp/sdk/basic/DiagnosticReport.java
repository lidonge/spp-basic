package com.servpp.sdk.basic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.lang.lint.RepairDiagnostic;
import com.servpp.sdk.SourceSnapshot;
import java.nio.file.Path;
import java.util.*;

final class DiagnosticReport {
    private final Path snapshot;
    private final List<RepairDiagnostic> diagnostics = new ArrayList<>();
    private boolean failed;

    DiagnosticReport(Path snapshot) { this.snapshot = snapshot; }

    void add(RepairDiagnostic diagnostic) {
        String prefix = snapshot.toString() + java.io.File.separator;
        String file = diagnostic.file();
        if (file.startsWith(prefix) || diagnostic.message().contains(prefix)) {
            diagnostic = new RepairDiagnostic(diagnostic.code(), diagnostic.severity(), file.replace(prefix, ""),
                    diagnostic.line(), diagnostic.column(), diagnostic.message().replace(prefix, ""),
                    diagnostic.cause().replace(prefix, ""), diagnostic.fixHint(), diagnostic.relatedLocations(),
                    diagnostic.edits(), diagnostic.safe());
        }
        diagnostics.add(diagnostic);
    }

    void failed(Exception failure) {
        failed = true;
        Throwable detail = failure;
        while (detail.getCause() != null && detail.getCause().getMessage() != null) detail = detail.getCause();
        String message = Objects.toString(detail.getMessage(), detail.getClass().getSimpleName());
        var location = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(snapshot + java.io.File.separator)
                + "(.+?):(\\d+):(\\d+):?\\s*(.*)");
        var unlocated = new LinkedHashSet<String>();
        for (String entry : message.lines().toList()) {
            var match = location.matcher(entry);
            if (match.find() && !match.group(4).isBlank()) {
                String cause = match.group(4);
                if (diagnostics.stream().anyMatch(d -> d.severity().equals("error")
                        && d.file().equals(match.group(1)) && d.line() == Integer.parseInt(match.group(2))
                        && d.message().equals(cause))) continue;
                add(RepairDiagnostic.error("SPP_SEMANTIC_ERROR", match.group(1), Integer.parseInt(match.group(2)),
                        Integer.parseInt(match.group(3)), cause, "Correct the declaration or expression at this location."));
            } else if (!entry.isBlank() && !entry.endsWith(":")) {
                unlocated.add(entry);
            }
        }
        // A single wrapper follows lexer/parser callbacks. A multiline validator
        // failure can contain independent unlocated errors alongside located ones.
        if (!message.contains("\n") && diagnostics.stream().anyMatch(d -> d.severity().equals("error"))) return;
        for (String entry : unlocated)
            add(RepairDiagnostic.error("SPP_COMPILATION_FAILED", "", 0, 0, entry,
                    "Correct the reported compiler validation failure and run check again."));
        if (diagnostics.stream().noneMatch(d -> d.severity().equals("error")))
            add(RepairDiagnostic.error("SPP_COMPILATION_FAILED", "", 0, 0, message, "Review the compiler validation failure."));
    }

    void write(Path destination) throws Exception {
        var sorted = diagnostics.stream().distinct().sorted(Comparator.comparing(RepairDiagnostic::severity)
                .thenComparing(RepairDiagnostic::code).thenComparing(RepairDiagnostic::file)
                .thenComparingInt(RepairDiagnostic::line).thenComparingInt(RepairDiagnostic::column)).toList();
        var hashes = new TreeMap<String,String>();
        for (var diagnostic : sorted) for (var edit : diagnostic.edits()) {
            if (!hashes.containsKey(edit.path())) hashes.put(edit.path(), SourceSnapshot.hash(snapshot.resolve(edit.path())));
        }
        new ObjectMapper().writeValue(destination.toFile(), Map.of("diagnostics", sorted, "hashes", hashes,
                "failed", failed || sorted.stream().anyMatch(d -> d.severity().equals("error"))));
    }
}
