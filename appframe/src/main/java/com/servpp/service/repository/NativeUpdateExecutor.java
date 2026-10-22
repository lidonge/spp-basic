package com.servpp.service.repository;

import java.sql.Connection;
import java.sql.SQLException;

/** Borrows the repository transaction connection; implementations must not commit, roll back or close it. */
@FunctionalInterface
public interface NativeUpdateExecutor {
    int execute(Connection connection, NativeUpdate update) throws SQLException;

    static NativeUpdateExecutor jdbc() {
        return (connection,update) -> {
            try (var statement = connection.prepareStatement(update.sql())) {
                var values = update.parameters();
                for (int index=0; index<values.size(); index++) statement.setObject(index+1,values.get(index).value());
                return statement.executeUpdate();
            }
        };
    }

    static NativeUpdateExecutor load(String implementation, ClassLoader loader) {
        if (implementation == null || implementation.isBlank()) return jdbc();
        try {
            return Class.forName(implementation,true,loader).asSubclass(NativeUpdateExecutor.class).getConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException("NATIVE_UPDATE_EXECUTOR_INVALID: " + implementation,failure);
        }
    }
}
