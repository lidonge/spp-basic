package io.seata.tm.api;

public final class GlobalTransactionContext {
    private static GlobalTransaction current;

    private GlobalTransactionContext() {
    }

    public static GlobalTransaction getCurrentOrCreate() {
        if (current == null) {
            current = new GlobalTransaction();
        }
        return current;
    }

    public static GlobalTransaction current() {
        return current;
    }

    public static void reset() {
        current = null;
    }
}
