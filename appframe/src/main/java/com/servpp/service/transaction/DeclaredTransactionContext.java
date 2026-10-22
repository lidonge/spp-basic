package com.servpp.service.transaction;

/** Execution context of a transaction boundary declared in the source language. */
public final class DeclaredTransactionContext {
    private static final ThreadLocal<TransactionMode> CURRENT = new ThreadLocal<>();
    private DeclaredTransactionContext() { }

    public static TransactionMode currentMode() { return CURRENT.get(); }

    public static void execute(TransactionMode mode, Runnable action) {
        TransactionMode previous = CURRENT.get();
        if (mode != null) { CURRENT.set(mode); }
        try { action.run(); }
        finally {
            if (previous == null) { CURRENT.remove(); } else { CURRENT.set(previous); }
        }
    }
}
