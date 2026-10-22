package com.servpp.sdk.editor;

import org.eclipse.lsp4j.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class EditorServerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void implicitCrudHasHoverAndRealParticipantNavigation() throws Exception {
        var root = temporary.getRoot().toPath();
        var model = root.resolve("src/main/spp/model.spp");
        var calls = root.resolve("src/main/spp/calls.spp");
        Files.createDirectories(model.getParent());
        Files.writeString(model, "domain shop; Entity Account { int balance; }");
        String text = "domain shop; Scenario serial run(Account) { findAccount; updateAccount; }";
        Files.writeString(calls, text);
        var document = new TextDocumentIdentifier(calls.toUri().toString());
        try (var server = new EditorServer()) {
            var initialize = new InitializeParams();
            initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(root.toUri().toString(), "test")));
            server.initialize(initialize).get(10, TimeUnit.SECONDS);
            for (String method : List.of("findAccount", "updateAccount")) {
                var position = new Position(0, text.indexOf(method));
                var target = server.definition(new DefinitionParams(document, position)).get(10, TimeUnit.SECONDS).getRight().get(0);
                assertEquals(model.toUri().toString(), target.getTargetUri());
                assertEquals(new Position(0, 20), target.getTargetSelectionRange().getStart());
                assertEquals(target.getTargetSelectionRange(), server.declaration(new DeclarationParams(document, position))
                        .get(10, TimeUnit.SECONDS).getRight().get(0).getTargetSelectionRange());
                String hover = server.hover(new HoverParams(document, position)).get(10, TimeUnit.SECONDS).getContents().getRight().getValue();
                assertTrue(hover.contains("built-in (implicit) service: " + method + "(Account)"));
                assertTrue(hover.contains("opens the participant type"));
            }
        }
    }

    @Test public void returnsPreciseNativeLanguageResultsWithoutAuthorization() throws Exception {
        var root = temporary.getRoot().toPath();
        var source = root.resolve("src/main/spp/model.spp");
        Files.createDirectories(source.getParent());
        String text = "domain ledger;\n// Business facts\nRole Facts { int amount; }\n"
                + "atomic calculate twice(Facts) { Facts.amount *= 2; }\nScenario serial preview(Facts) { twice; }\n";
        Files.writeString(source, text);
        try (var server = new EditorServer()) {
            var initialize = new InitializeParams();
            initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(root.toUri().toString(), "test")));
            var result = server.initialize(initialize).get(10, TimeUnit.SECONDS);
            assertTrue(result.getCapabilities().getDefinitionProvider().getLeft());
            var reference = new DefinitionParams(new TextDocumentIdentifier(source.toUri().toString()), new Position(3, 38));
            var target = server.definition(reference).get(10, TimeUnit.SECONDS).getRight().get(0);
            assertEquals(source.toUri().toString(), target.getTargetUri());
            assertEquals(new Position(2, 17), target.getTargetSelectionRange().getStart());
            var declaration = new DeclarationParams(reference.getTextDocument(), reference.getPosition());
            assertEquals(target.getTargetSelectionRange(), server.declaration(declaration).get(10, TimeUnit.SECONDS)
                    .getRight().get(0).getTargetSelectionRange());
            var hover = server.hover(new HoverParams(reference.getTextDocument(), new Position(2, 6))).get(10, TimeUnit.SECONDS);
            assertTrue(hover.getContents().getRight().getValue().contains("Business facts"));
            var outline = server.documentSymbol(new DocumentSymbolParams(reference.getTextDocument())).get(10, TimeUnit.SECONDS);
            assertEquals("ledger", outline.get(0).getRight().getName());
            assertEquals("amount", outline.get(0).getRight().getChildren().get(0).getChildren().get(0).getName());
            assertEquals("twice", server.symbol(new WorkspaceSymbolParams("twice")).get(10, TimeUnit.SECONDS).getLeft().get(0).getName());
            server.shutdown().get(10, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> server.definition(reference).get(10, TimeUnit.SECONDS));
        }
    }

    @Test public void appliesUnsavedVersionsBeforeSubsequentRequests() throws Exception {
        var root = temporary.getRoot().toPath();
        Files.createDirectories(root.resolve("src/main/spp"));
        String uri = root.resolve("src/main/spp/new.spp").toUri().toString();
        try (var server = new EditorServer()) {
            var initialize = new InitializeParams();
            initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(root.toUri().toString(), "test")));
            server.initialize(initialize).get(10, TimeUnit.SECONDS);
            server.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "spp", 1, "domain ledger; Role First;")));
            server.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 2),
                    List.of(new TextDocumentContentChangeEvent("domain ledger; Role Second;"))));
            var outline = server.documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri))).get(10, TimeUnit.SECONDS);
            assertEquals("Second", outline.get(0).getRight().getChildren().get(0).getName());
        }
    }

    @Test public void requiresInitializationButNotAnAccount() throws Exception {
        try (var server = new EditorServer()) {
            var failure = assertThrows(ExecutionException.class,
                    () -> server.symbol(new WorkspaceSymbolParams("test")).get(10, TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("Cannot analyze"));
        }
    }

    @Test public void boundsHoverPayloadsForLargeLiteralDeclarations() throws Exception {
        var root = temporary.getRoot().toPath();
        var source = root.resolve("src/main/spp/large.spp");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "domain ledger; const String VALUE = \"" + "x".repeat(20000) + "\";");
        try (var server = new EditorServer()) {
            var initialize = new InitializeParams();
            initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(root.toUri().toString(), "test")));
            server.initialize(initialize).get(10, TimeUnit.SECONDS);
            var hover = server.hover(new HoverParams(new TextDocumentIdentifier(source.toUri().toString()), new Position(0, 28)))
                    .get(10, TimeUnit.SECONDS);
            assertTrue(hover.getContents().getRight().getValue().startsWith("constStringVALUE"));
            assertTrue(hover.getContents().getRight().getValue().length() <= 16001);
        }
    }
}
