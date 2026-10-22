package com.servpp.service.transaction;

import java.util.List;
import java.util.Objects;

import com.servpp.service.scenario.IExecutor;

public final class SagaTransactionGroupManager {
    private static final SagaTransactionGroupCoordinator DEFAULT_COORDINATOR =
            (scope, executors) -> DefaultTransactionManager.getInstance().execute(executors);
    private static volatile SagaTransactionGroupCoordinator coordinator = DEFAULT_COORDINATOR;

    private SagaTransactionGroupManager() {
    }

    public static void execute(List<IExecutor> executors) {
        List<IExecutor> safeExecutors = executors == null ? List.of() : List.copyOf(executors);
        coordinator.execute(SagaExecutionContext.currentScope(), safeExecutors);
    }

    public static SagaRecoveryScanResult recoverPending(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Saga recovery scan limit must be positive");
        }
        return coordinator.recoverPending(limit);
    }

    static void executeSourceStages(String boundaryId, java.util.function.Supplier<List<SourceSagaStage>> stages, Runnable localFallback) {
        var selected = coordinator;
        if (selected == DEFAULT_COORDINATOR) localFallback.run();
        else selected.executeSourceStages(SagaExecutionContext.nextSourceScope(boundaryId), List.copyOf(stages.get()));
    }

    public static synchronized void useCoordinator(
            SagaTransactionGroupCoordinator nextCoordinator) {
        SagaTransactionGroupCoordinator previous = coordinator;
        coordinator = Objects.requireNonNull(nextCoordinator, "nextCoordinator");
        close(previous);
    }

    public static synchronized void resetCoordinator() {
        SagaTransactionGroupCoordinator previous = coordinator;
        coordinator = DEFAULT_COORDINATOR;
        close(previous);
    }

    private static void close(SagaTransactionGroupCoordinator previous) {
        if (previous == DEFAULT_COORDINATOR || previous == coordinator) {
            return;
        }
        if (previous instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot close Saga transaction coordinator", e);
            }
        }
    }
}
