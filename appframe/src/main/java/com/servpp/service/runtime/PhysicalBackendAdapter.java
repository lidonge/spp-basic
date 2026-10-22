package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import com.servpp.service.transaction.TransactionContext;

public interface PhysicalBackendAdapter {
    Set<String> backendNames();

    default boolean supportsBackend(String backendName) {
        return backendNames().contains(backendName);
    }

    default boolean supportsTransaction(String transactionKind, String targetName) {
        return true;
    }

    default List<String> validationErrors(String backendName) {
        return List.of();
    }

    default String implementationType() {
        return getClass().getName();
    }

    default Map<String, String> configuration() {
        return Map.of();
    }

    default void useTccPhaseTwoRecovery(TccPhaseTwoRecoveryHandler recoveryHandler) {
    }

    default boolean ownsTccPhaseTwo(PhysicalBackendAssignment assignment) {
        return false;
    }

    default void registerTccPhaseTwoCallback(PhysicalBackendAssignment assignment,
                                             TransactionContext context,
                                             TccPhaseTwoRecoveryHandler callback) {
    }

    default void unregisterTccPhaseTwoCallback(PhysicalBackendAssignment assignment,
                                               TransactionContext context) {
    }

    default DataSource wrapDataSource(String backendName, String resourceKind, String resourceName,
                                      DataSource dataSource) {
        return dataSource;
    }

    default void begin(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void beforeExecute(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void afterExecute(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void commit(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void rollback(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                          Throwable cause) {
    }

    default void beforeTry(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        beforeExecute(assignment, context, arguments);
    }

    default void afterTry(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        afterExecute(assignment, context, arguments);
    }

    default void confirm(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        commit(assignment, context, arguments);
    }

    default void cancel(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                        Throwable cause) {
        rollback(assignment, context, arguments, cause);
    }
}
