package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.servpp.service.transaction.TransactionContext;

public interface SeataTransactionBridge {
    default void configure(Map<String, String> configuration) {
    }

    default String bridgeType() {
        return getClass().getName();
    }

    default List<String> validationErrors(String backendName, String applicationId, String transactionServiceGroup) {
        return List.of();
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

    default DataSource wrapDataSource(String applicationId, String transactionServiceGroup,
                                      String backendName, String resourceKind, String resourceName,
                                      DataSource dataSource) {
        return dataSource;
    }

    default void begin(String applicationId, String transactionServiceGroup,
                       PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void beforeExecute(String applicationId, String transactionServiceGroup,
                               PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void afterExecute(String applicationId, String transactionServiceGroup,
                              PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void commit(String applicationId, String transactionServiceGroup,
                        PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
    }

    default void rollback(String applicationId, String transactionServiceGroup,
                          PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                          Throwable cause) {
    }

    default void beforeTry(String applicationId, String transactionServiceGroup,
                           PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        beforeExecute(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    default void afterTry(String applicationId, String transactionServiceGroup,
                          PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        afterExecute(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    default void confirm(String applicationId, String transactionServiceGroup,
                         PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        commit(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    default void cancel(String applicationId, String transactionServiceGroup,
                        PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                        Throwable cause) {
        rollback(applicationId, transactionServiceGroup, assignment, context, arguments, cause);
    }
}
