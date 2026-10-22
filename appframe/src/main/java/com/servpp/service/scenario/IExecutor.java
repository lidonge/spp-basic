package com.servpp.service.scenario;

import com.servpp.service.Service;

/**
 * @author lidong@date 2023-11-20@version 1.0
 */
public interface IExecutor {
    Service getService();

    /** Repositories used by an opaque serial/conditional executor must join its enclosing boundary. */
    default java.util.List<Service> getRepositoryScopeServices() {
        Service service=getService();return service==null?java.util.List.of():java.util.List.of(service);
    }

    default Object[] getArguments() {
        return new Object[0];
    }

    default java.util.List<com.servpp.service.transaction.QuantumInvocationBounds.Bound> getQuantumBounds() {
        return com.servpp.service.transaction.QuantumInvocationBounds.current();
    }

    default com.servpp.service.transaction.TransactionMode getTransactionModeHint() { return null; }
    default boolean isPreparedOrdinaryUpdate() { return false; }
    default boolean isQuantumUpdate() { return false; }
    default void prepareForParallelExecution() { }
    void execute();
}
