package com.servpp.sdk.editor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class WorkspaceIndexTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void isolatesSourceRootsAndUsesLatestUnsavedVersion() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path first = write(root, "one/model.spp", "domain same; Role First;");
        Path second = write(root, "two/model.spp", "domain same; Role Second;");
        var index = new WorkspaceIndex(root, List.of("one", "two"));
        index.scan();
        assertTrue(index.project(first.toUri().toString()).symbols().stream().noneMatch(symbol -> symbol.name().equals("Second")));
        assertTrue(index.project(second.toUri().toString()).symbols().stream().noneMatch(symbol -> symbol.name().equals("First")));
        index.open(first.toUri().toString(), 3, "domain same; Role Unsaved;");
        index.open(first.toUri().toString(), 2, "domain same; Role Stale;");
        index.scan();
        assertTrue(index.symbols().stream().anyMatch(symbol -> symbol.name().equals("Unsaved")));
        assertTrue(index.symbols().stream().noneMatch(symbol -> symbol.name().equals("Stale")));
        index.close(first.toUri().toString());
        assertTrue(index.symbols().stream().anyMatch(symbol -> symbol.name().equals("First")));
        index.clear();
        assertTrue(index.symbols().isEmpty());
    }

    @Test public void tracksDiskRenameAndDeletionAndExcludesOutputs() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path file = write(root, "src/main/spp/model.spp", "domain sample; Role First;");
        write(root, "src/main/spp/target/ignored.spp", "domain sample; Role Ignored;");
        var index = new WorkspaceIndex(root, List.of("src/main/spp"));
        index.scan();
        assertTrue(index.symbols().stream().noneMatch(symbol -> symbol.name().equals("Ignored")));
        Files.move(file, file.resolveSibling("renamed.spp"));
        index.scan();
        assertTrue(index.symbols().stream().allMatch(symbol -> symbol.document().uri().endsWith("renamed.spp")));
        Files.delete(file.resolveSibling("renamed.spp"));
        index.scan();
        assertTrue(index.symbols().isEmpty());
    }

    @Test public void rejectsEscapesOverlapsAndSymlinkTraversal() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path file = write(root, "source/model.spp", "domain sample;");
        assertThrows(IOException.class, () -> new WorkspaceIndex(root, List.of("../escape")));
        assertThrows(IOException.class, () -> new WorkspaceIndex(root, List.of("source", "source/nested")));
        var index = new WorkspaceIndex(root, List.of("source"));
        assertThrows(IOException.class, () -> index.open(root.resolve("other.spp").toUri().toString(), 1, "domain sample;"));
        Files.createSymbolicLink(root.resolve("linked"), file.getParent());
        assertThrows(IOException.class, () -> new WorkspaceIndex(root, List.of("linked")));
        Files.createSymbolicLink(root.resolve("source/linked.spp"), file);
        assertThrows(IOException.class, () -> index.open(root.resolve("source/linked.spp").toUri().toString(), 1, "domain sample;"));
        index.scan();
        assertEquals(1, index.symbols().size());
    }

    @Test public void failingUpdatesCannotReturnAStalePriorDocument() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path file = write(root, "source/model.spp", "domain sample; Role Original;");
        var index = new WorkspaceIndex(root, List.of("source"));
        index.scan();
        assertFalse(index.symbols().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> index.open(file.toUri().toString(), 2, "X".repeat(SourceDocument.MAX_BYTES + 1)));
        assertTrue(index.symbols().isEmpty());
        index.open(file.toUri().toString(), 1, "domain sample; Role Stale;");
        assertTrue(index.symbols().isEmpty());
        index.open(file.toUri().toString(), 3, "domain sample; Role Current;");
        assertTrue(index.symbols().stream().anyMatch(symbol -> symbol.name().equals("Current")));
        Files.writeString(root.resolve("source/large.spp"), "X".repeat(SourceDocument.MAX_BYTES + 1));
        assertThrows(IOException.class, index::scan);
        assertTrue(index.symbols().isEmpty());
    }

    @Test public void explicitlyConfiguredWorkspaceRootSupportsNonstandardLayouts() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path file = write(root, "model.spp", "domain sample; Role RootModel;");
        write(root, "target/generated.spp", "domain sample; Role Generated;");
        var index = new WorkspaceIndex(root, List.of("."));
        index.scan();
        assertTrue(index.project(file.toUri().toString()).symbols().stream().anyMatch(symbol -> symbol.name().equals("RootModel")));
        assertTrue(index.symbols().stream().noneMatch(symbol -> symbol.name().equals("Generated")));
    }

    private static Path write(Path root, String filename, String text) throws Exception {
        Path file = root.resolve(filename);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }
}
