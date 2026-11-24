package com.servpp.sdk.editor;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class RedundancyDiagnosticsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void publishesLocatedWarningsAndClearsThemWhenAnotherFileChanges() throws Exception {
        var root = temporary.getRoot().toPath();
        var dir = Files.createDirectories(root.resolve("src/main/spp"));
        var original = dir.resolve("a.spp");
        Files.writeString(original, "domain shop; Role Input { String name; }");
        var copy = dir.resolve("b.spp").toUri().toString();
        var notifications = new LinkedBlockingQueue<PublishDiagnosticsParams>();
        try (var server = new EditorServer()) {
            server.connect((LanguageClient) Proxy.newProxyInstance(LanguageClient.class.getClassLoader(),
                    new Class<?>[]{LanguageClient.class}, (proxy, method, args) -> {
                        if (method.getName().equals("publishDiagnostics"))
                            notifications.add((PublishDiagnosticsParams) args[0]);
                        return null;
                    }));
            var init = new InitializeParams();
            init.setWorkspaceFolders(List.of(new WorkspaceFolder(root.toUri().toString(), "shop")));
            server.initialize(init).get(10, TimeUnit.SECONDS);
            server.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(copy, "spp", 1,
                    "domain shop;\nRole Copy { String name; } readonly Scenario preview(Copy) {}")));
            var published = notifications.poll(10, TimeUnit.SECONDS);
            assertNotNull(published);
            assertEquals(copy, published.getUri());
            var warning = published.getDiagnostics().get(0);
            assertTrue(warning.getCode().getLeft().startsWith("SPP_REDUNDANT_"));
            var data = (com.servpp.lang.lint.RepairDiagnostic) warning.getData();
            assertEquals(warning.getCode().getLeft(), data.code());
            assertEquals(copy, data.file());
            assertFalse(data.cause().isBlank());
            assertEquals(DiagnosticSeverity.Warning, warning.getSeverity());
            assertEquals(1, warning.getRange().getStart().getLine());
            Files.writeString(original, "domain shop; Role Input { int age; }");
            server.didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of()));
            var cleared = notifications.poll(10, TimeUnit.SECONDS);
            assertNotNull(cleared);
            assertTrue(cleared.getDiagnostics().isEmpty());
        }
    }
}
