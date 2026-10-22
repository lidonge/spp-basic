package com.servpp.service.transaction;

import java.util.List;

import com.servpp.service.scenario.IExecutor;

@FunctionalInterface
public interface SagaTransactionGroupCoordinator {
    void execute(SagaExecutionScope scope, List<IExecutor> executors);

    default void executeSourceStages(SagaExecutionScope scope, List<SourceSagaStage> stages) {
        throw new IllegalStateException("SOURCE_SAGA_COORDINATOR_UNSUPPORTED");
    }

    default SagaRecoveryScanResult recoverPending(int limit) {
        return SagaRecoveryScanResult.empty();
    }
}
