package com.servpp.sdk.editor;

import org.eclipse.lsp4j.launch.LSPLauncher;

import java.util.concurrent.Executors;
import java.util.function.Function;

public final class EditorMain {
    private EditorMain() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) throw new IllegalArgumentException("The S++ language service uses LSP stdio only.");
        var protocolThreads = Executors.newCachedThreadPool();
        try (EditorServer server = new EditorServer()) {
            var launcher = LSPLauncher.createServerLauncher(server, new BoundedLspInput(System.in), System.out, protocolThreads, Function.identity());
            server.connect(launcher.getRemoteProxy());
            launcher.startListening().get();
        } finally {
            protocolThreads.shutdownNow();
        }
    }
}
