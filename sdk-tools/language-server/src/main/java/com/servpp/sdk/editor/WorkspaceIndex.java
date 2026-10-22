package com.servpp.sdk.editor;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkspaceIndex {
    public static final int MAX_FILES = 10000;
    public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final Set<String> EXCLUDED = Set.of(".git", "target", "node_modules");
    private final Path workspace;
    private final List<Path> roots;
    private final String physicalProfile;
    private final Map<Path, SourceDocument> documents = new LinkedHashMap<>();
    private final Map<Path, Integer> openVersions = new HashMap<>();
    private final Map<Path, ProjectAnalysis> projects = new HashMap<>();

    public WorkspaceIndex(Path workspace, List<String> sourceRoots) throws IOException {
        this(workspace, sourceRoots, "");
    }

    public WorkspaceIndex(Path workspace, List<String> sourceRoots, String physicalProfile) throws IOException {
        this.workspace = workspace.toRealPath();
        this.physicalProfile = physicalProfile;
        List<Path> checked = new ArrayList<>();
        for (String sourceRoot : sourceRoots) {
            Path root = this.workspace.resolve(sourceRoot).normalize();
            if (!root.startsWith(this.workspace))
                throw new IOException("Source roots must stay inside the workspace.");
            checkComponents(root);
            for (Path existing : checked) if (root.startsWith(existing) || existing.startsWith(root))
                throw new IOException("Source roots must not overlap.");
            checked.add(root);
        }
        if (checked.isEmpty() || checked.size() > 16) throw new IOException("Configure between one and sixteen source roots.");
        roots = List.copyOf(checked);
    }

    public void scan() throws IOException {
        try {
            scanSnapshot();
        } catch (IOException | RuntimeException failure) {
            clear();
            throw failure;
        }
    }

    private void scanSnapshot() throws IOException {
        Map<Path, SourceDocument> updated = new LinkedHashMap<>();
        long[] bytes = {0};
        int[] entries = {0};
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            checkComponents(root);
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    if (!directory.equals(root) && excluded(directory)) return FileVisitResult.SKIP_SUBTREE;
                    checkComponents(directory);
                    if (++entries[0] > 100000) throw new IOException("Source tree exceeds the editor scan limit.");
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Indexing cancelled.");
                    if (++entries[0] > 100000) throw new IOException("Source tree exceeds the editor scan limit.");
                    if (!sourceFile(file) || excluded(file) || attributes.isSymbolicLink() || !attributes.isRegularFile())
                        return FileVisitResult.CONTINUE;
                    checkComponents(file);
                    if (updated.size() >= MAX_FILES) throw new IOException("Editor index exceeds 10000 files.");
                    long size = attributes.size();
                    SourceDocument existing = documents.get(file);
                    SourceDocument document = openVersions.containsKey(file) && existing != null ? existing : read(file);
                    bytes[0] += document.text().getBytes(StandardCharsets.UTF_8).length;
                    if (size > SourceDocument.MAX_BYTES || bytes[0] > MAX_TOTAL_BYTES)
                        throw new IOException("Editor index exceeds source size limits.");
                    updated.put(file, document);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        for (Path open : openVersions.keySet()) if (!updated.containsKey(open) && documents.containsKey(open)) {
            if (updated.size() >= MAX_FILES) throw new IOException("Editor index exceeds 10000 files.");
            bytes[0] += documents.get(open).text().getBytes(StandardCharsets.UTF_8).length;
            if (bytes[0] > MAX_TOTAL_BYTES) throw new IOException("Editor index exceeds source size limits.");
            updated.put(open, documents.get(open));
        }
        documents.clear();
        documents.putAll(updated);
        projects.clear();
    }

    public void open(String uri, int version, String text) throws IOException {
        Path file = checkedFile(uri);
        Integer current = openVersions.get(file);
        if (current != null && version <= current) return;
        if (!documents.containsKey(file) && documents.size() >= MAX_FILES)
            throw new IOException("Editor index exceeds 10000 files.");
        long bytes = text.getBytes(StandardCharsets.UTF_8).length;
        for (var entry : documents.entrySet()) if (!entry.getKey().equals(file))
            bytes += entry.getValue().text().getBytes(StandardCharsets.UTF_8).length;
        openVersions.put(file, version);
        projects.remove(rootFor(file));
        documents.remove(file);
        if (bytes > MAX_TOTAL_BYTES) throw new IOException("Editor index exceeds source size limits.");
        documents.put(file, new SourceDocument(file.toUri().toString(), version, text));
    }

    public void close(String uri) throws IOException {
        Path file = checkedFile(uri);
        openVersions.remove(file);
        projects.remove(rootFor(file));
        documents.remove(file);
        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) documents.put(file, read(file));
    }

    public ProjectAnalysis project(String uri) throws IOException {
        Path root = rootFor(checkedFile(uri));
        return projects.computeIfAbsent(root, this::analyze);
    }

    public List<SourceSymbol> symbols() {
        List<SourceSymbol> result = new ArrayList<>();
        for (Path root : roots) result.addAll(projects.computeIfAbsent(root, this::analyze).symbols());
        return List.copyOf(result);
    }

    public String canonicalUri(String uri) throws IOException { return checkedFile(uri).toUri().toString(); }

    private ProjectAnalysis analyze(Path sourceRoot) {
        return new ProjectAnalysis(documents.entrySet().stream().filter(entry -> entry.getKey().startsWith(sourceRoot))
                .map(Map.Entry::getValue).toList(), physicalProfile, System.getProperty("spp.environment", "develop"));
    }

    public void clear() {
        projects.clear();
        documents.clear();
        openVersions.clear();
    }

    private SourceDocument read(Path file) throws IOException {
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(SourceDocument.MAX_BYTES + 1);
            if (bytes.length > SourceDocument.MAX_BYTES) throw new IOException("Source exceeds the 1 MiB editor limit.");
            return new SourceDocument(file.toUri().toString(), 0, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private Path checkedFile(String uri) throws IOException {
        URI parsed = URI.create(uri);
        if (!"file".equals(parsed.getScheme())) throw new IOException("Only filesystem sources are supported.");
        Path file = Path.of(parsed).toAbsolutePath().normalize();
        rootFor(file);
        if (!sourceFile(file) || excluded(file)) throw new IOException("Not an indexed S++ source.");
        checkComponents(file);
        return file;
    }

    private Path rootFor(Path file) throws IOException {
        for (Path root : roots) if (file.startsWith(root) && !file.equals(root)) return root;
        throw new IOException("Source is outside configured source roots.");
    }

    private void checkComponents(Path path) throws IOException {
        Path current = workspace;
        for (Path component : workspace.relativize(path)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) throw new IOException("Editor sources must not traverse symbolic links.");
        }
    }

    private boolean excluded(Path file) {
        for (Path component : workspace.relativize(file)) if (EXCLUDED.contains(component.toString())) return true;
        return false;
    }

    private static boolean sourceFile(Path file) {
        return file.getFileName().toString().matches(".*\\.(spp|enum|app|phy|rdb)");
    }
}
