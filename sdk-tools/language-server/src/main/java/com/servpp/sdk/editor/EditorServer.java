package com.servpp.sdk.editor;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class EditorServer implements LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService, AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WorkspaceIndex index;
    private LanguageClient client;
    private boolean initialized;
    private boolean stopped;

    @Override public void connect(LanguageClient client) { this.client = client; }

    @Override public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        return task(false, () -> {
            if (initialized) throw new IllegalStateException();
            String uri = params.getWorkspaceFolders() == null || params.getWorkspaceFolders().isEmpty()
                    ? params.getRootUri() : params.getWorkspaceFolders().get(0).getUri();
            if (uri == null || !"file".equals(URI.create(uri).getScheme()))
                throw new IllegalArgumentException();
            List<String> roots = List.of("src/main/spp");
            String physicalProfile = "";
            if (params.getInitializationOptions() != null) {
                var options = new com.google.gson.Gson().toJsonTree(params.getInitializationOptions()).getAsJsonObject();
                if (options.has("sourceRoots")) {
                    roots = new ArrayList<>();
                    for (var root : options.getAsJsonArray("sourceRoots")) roots.add(root.getAsString());
                }
                if (options.has("physicalProfile")) physicalProfile = options.get("physicalProfile").getAsString();
            }
            index = new WorkspaceIndex(Path.of(URI.create(uri)), roots, physicalProfile);
            index.scan();
            initialized = true;
            ServerCapabilities capabilities = new ServerCapabilities();
            capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
            capabilities.setDefinitionProvider(true);
            capabilities.setDeclarationProvider(true);
            capabilities.setHoverProvider(true);
            capabilities.setDocumentSymbolProvider(true);
            capabilities.setWorkspaceSymbolProvider(true);
            return new InitializeResult(capabilities, new ServerInfo("S++ local language service", "0.1.0"));
        });
    }

    @Override public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return navigation(params);
    }

    @Override public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> declaration(DeclarationParams params) {
        return navigation(params);
    }

    private CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> navigation(TextDocumentPositionParams params) {
        return task(true, () -> {
            String uri = index.canonicalUri(params.getTextDocument().getUri());
            var project = index.project(uri);
            List<LocationLink> links = new ArrayList<>();
            for (SourceSymbol symbol : project.resolve(uri, params.getPosition())) {
                var link = new LocationLink(symbol.document().uri(), symbol.range(), symbol.selectionRange());
                links.add(link);
            }
            return Either.forRight(links);
        });
    }

    @Override public CompletableFuture<Hover> hover(HoverParams params) {
        return task(true, () -> {
            String uri = index.canonicalUri(params.getTextDocument().getUri());
            var project = index.project(uri);
            String implicit = project.implicitDescription(uri, params.getPosition());
            if (!implicit.isEmpty()) return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, implicit));
            List<SourceSymbol> matches = project.resolve(uri, params.getPosition());
            if (matches.isEmpty()) return null;
            StringBuilder content = new StringBuilder();
            if (matches.size() > 1) content.append("Ambiguous symbol: ").append(matches.size()).append(" candidates\n\n");
            for (SourceSymbol symbol : matches) {
                if (content.length() > 16000) break;
                content.append(symbol.detail()).append("\ndomain ").append(symbol.domain())
                        .append("\n").append(symbol.document().uri()).append(":")
                        .append(symbol.selectionRange().getStart().getLine() + 1);
                if (symbol.implicit()) content.append("\nImplicit participant name derived by S++ rules.");
                if (symbol.document().uri().endsWith(".phy")) content.append("\nPhysical configuration")
                        .append(symbol.environment().isEmpty() ? "" : "; environment " + symbol.environment())
                        .append(project.active(symbol) ? " (active)" : " (inactive)");
                String documentation = documentation(symbol);
                if (!documentation.isBlank()) content.append("\n").append(documentation);
                content.append("\n\n");
            }
            String rendered = content.toString().strip();
            if (rendered.length() > 16000) {
                int boundary = Character.isHighSurrogate(rendered.charAt(15999)) ? 15999 : 16000;
                rendered = rendered.substring(0, boundary) + "…";
            }
            return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, rendered));
        });
    }

    @Override public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        return task(true, () -> {
            String uri = index.canonicalUri(params.getTextDocument().getUri());
            var project = index.project(uri);
            List<SourceSymbol> symbols = project.documentSymbols(uri);
            List<DocumentSymbol> result = new ArrayList<>();
            for (SourceSymbol symbol : symbols) if (symbol.owner() == null && symbol.kind() != SymbolKind.Namespace)
                result.add(outline(symbol, symbols));
            for (var mapping : project.documentMappings(uri)) {
                var context = mapping.context();
                var target = context.Identifier(context.Identifier().size() - 1).getSymbol();
                var group = new DocumentSymbol("take " + String.join(", ", mapping.performers()) + " as " + mapping.role(),
                        SymbolKind.Object, project.document(uri).range(context), project.document(uri).range(target));
                group.setChildren(symbols.stream().filter(symbol -> symbol.kind() == SymbolKind.Field
                        && symbol.context().getStart().getTokenIndex() >= context.getStart().getTokenIndex()
                        && symbol.context().getStop().getTokenIndex() <= context.getStop().getTokenIndex())
                        .map(symbol -> outline(symbol, symbols)).toList());
                result.add(group);
            }
            SourceSymbol domain = symbols.stream().filter(symbol -> symbol.kind() == SymbolKind.Namespace).findFirst().orElse(null);
            if (domain == null) return result.stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).toList();
            var namespace = new DocumentSymbol(domain.name(), SymbolKind.Namespace, domain.document().range(domain.document().tree()), domain.selectionRange());
            result.sort(java.util.Comparator.comparingInt(symbol -> symbol.getRange().getStart().getLine()));
            namespace.setChildren(result);
            return List.of(Either.forRight(namespace));
        });
    }

    @Override public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
        return task(true, () -> {
            String query = params.getQuery() == null ? "" : params.getQuery().toLowerCase(Locale.ROOT);
            List<SymbolInformation> matches = new ArrayList<>();
            for (SourceSymbol symbol : index.symbols()) {
                if (matches.size() >= 200) break;
                if (symbol.implicit() || symbol.kind() == SymbolKind.Variable || !symbol.name().toLowerCase(Locale.ROOT).contains(query)) continue;
                matches.add(new SymbolInformation(symbol.name(), symbol.kind(), new Location(symbol.document().uri(),
                        symbol.selectionRange()), symbol.domain() + (symbol.owner() == null ? "" : "." + symbol.owner().name())));
            }
            return Either.forLeft(matches);
        });
    }

    @Override public void didOpen(DidOpenTextDocumentParams params) {
        notification(() -> index.open(params.getTextDocument().getUri(), params.getTextDocument().getVersion(), params.getTextDocument().getText()));
    }
    @Override public void didChange(DidChangeTextDocumentParams params) {
        notification(() -> {
            if (params.getContentChanges().size() != 1 || params.getContentChanges().get(0).getRange() != null)
                throw new IllegalArgumentException();
            index.open(params.getTextDocument().getUri(), params.getTextDocument().getVersion(), params.getContentChanges().get(0).getText());
        });
    }
    @Override public void didClose(DidCloseTextDocumentParams params) { notification(() -> index.close(params.getTextDocument().getUri())); }
    @Override public void didSave(DidSaveTextDocumentParams params) { notification(() -> index.scan()); }
    @Override public void didChangeConfiguration(DidChangeConfigurationParams params) { }
    @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) { notification(() -> index.scan()); }
    @Override public TextDocumentService getTextDocumentService() { return this; }
    @Override public WorkspaceService getWorkspaceService() { return this; }
    @Override public CompletableFuture<Object> shutdown() {
        return task(false, () -> {
            if (index != null) index.clear();
            stopped = true;
            return null;
        });
    }
    @Override public void exit() { close(); }
    @Override public void close() {
        worker.shutdownNow();
    }

    private <Result> CompletableFuture<Result> task(boolean requiresInitialization, Operation<Result> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (stopped) throw new IllegalStateException();
                if (requiresInitialization && !initialized) throw new IllegalStateException();
                return action.run();
            } catch (Exception failure) {
                throw new ResponseErrorException(new ResponseError(-32602, "Cannot analyze this S++ workspace or request. Check source roots, file limits and syntax.", null));
            }
        }, worker);
    }

    private void notification(Action action) {
        task(true, () -> { action.run(); return null; }).exceptionally(failure -> {
            if (index != null) index.clear();
            if (client != null) client.logMessage(new MessageParams(MessageType.Warning,
                    "S++ language state could not be updated. Check source roots and file limits."));
            return null;
        });
    }

    private static DocumentSymbol outline(SourceSymbol symbol, List<SourceSymbol> symbols) {
        var result = new DocumentSymbol(symbol.name(), symbol.kind(), symbol.range(), symbol.selectionRange(), symbol.detail());
        result.setChildren(symbols.stream().filter(child -> symbol.equals(child.owner()) && child.kind() != SymbolKind.Variable
                        && child.context().getStart().getTokenIndex() >= symbol.context().getStart().getTokenIndex()
                        && child.context().getStop().getTokenIndex() <= symbol.context().getStop().getTokenIndex())
                .map(child -> outline(child, symbols)).toList());
        return result;
    }

    private static String documentation(SourceSymbol symbol) {
        List<TokenView> previous = new ArrayList<>();
        for (var token : symbol.document().tokens()) {
            if (token.getTokenIndex() >= symbol.context().getStart().getTokenIndex()) break;
            previous.add(new TokenView(token.getChannel(), token.getText()));
        }
        StringBuilder result = new StringBuilder();
        for (int tokenIndex = previous.size() - 1; tokenIndex >= 0; tokenIndex--) {
            TokenView token = previous.get(tokenIndex);
            if (token.channel() == 0) break;
            if (token.text().isBlank()) {
                if (token.text().chars().filter(character -> character == '\n').count() > 1) break;
            } else if (token.text().startsWith("//") || token.text().startsWith("/*")) {
                result.insert(0, token.text() + "\n");
                if (result.length() > 4000) break;
            }
        }
        return result.toString();
    }

    private record TokenView(int channel, String text) { }
    @FunctionalInterface private interface Operation<Result> { Result run() throws Exception; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
