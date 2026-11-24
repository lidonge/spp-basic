package com.servpp.lang.lint;

import java.io.File;
import java.util.function.Consumer;

/** Optional observer for located compiler errors on the current compilation thread. */
public final class CompilerDiagnostics {
    private CompilerDiagnostics() { }
    private static final ThreadLocal<Consumer<RepairDiagnostic>> observer = new ThreadLocal<>();

    public interface Scope extends AutoCloseable { void close(); }

    public static Scope observe(Consumer<RepairDiagnostic> receiver) {
        var previous = observer.get();
        observer.set(receiver);
        return () -> { if (previous == null) observer.remove(); else observer.set(previous); };
    }

    public static boolean observed() { return observer.get() != null; }

    public static void report(RepairDiagnostic diagnostic) {
        var receiver = observer.get();
        if (receiver != null) receiver.accept(diagnostic);
    }

    public static void error(String code, File file, int line, int column, String message, String hint) {
        var receiver = observer.get();
        if (receiver != null && file != null && message != null)
            receiver.accept(RepairDiagnostic.error(code, file.toString(), line, column, message, hint));
    }
}
