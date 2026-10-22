package com.servpp.service.transaction;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.servpp.service.Service;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.scenario.ITransactionGroup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SagaTransactionGroupManagerTest {
    @After
    public void resetCoordinator() {
        SagaTransactionGroupManager.resetCoordinator();
    }

    @Test
    public void transactionGroupCarriesSagaAndBusinessIdentityToCoordinator() {
        List<String> events = new ArrayList<>();
        SagaTransactionGroupManager.useCoordinator((scope, executors) -> {
            events.add(scope.sagaServiceName());
            events.add(scope.businessKey());
            executors.forEach(IExecutor::execute);
        });

        BusinessTraceContext.execute("GSN-1001", () ->
                SagaExecutionContext.execute("submitOrder", () -> {
                    new ITransactionGroup() {
                        @Override
                        public IExecutor[] getServices() {
                            return new IExecutor[]{executor(() -> events.add("executed"))};
                        }
                    }.execute();
                    return null;
                }));

        assertEquals(List.of("submitOrder", "GSN-1001", "executed"), events);
    }

    @Test
    public void delegatesPendingRecoveryScanToConfiguredCoordinator() {
        SagaTransactionGroupManager.useCoordinator(new SagaTransactionGroupCoordinator() {
            @Override
            public void execute(SagaExecutionScope scope, List<IExecutor> executors) {
            }

            @Override
            public SagaRecoveryScanResult recoverPending(int limit) {
                return new SagaRecoveryScanResult(limit, limit - 1, 1, List.of("retry later"));
            }
        });

        assertEquals(new SagaRecoveryScanResult(5, 4, 1, List.of("retry later")),
                SagaTransactionGroupManager.recoverPending(5));
    }

    @Test
    public void closesManagedCoordinatorWhenItIsReplacedOrReset() {
        CloseableCoordinator first = new CloseableCoordinator();
        CloseableCoordinator second = new CloseableCoordinator();

        SagaTransactionGroupManager.useCoordinator(first);
        SagaTransactionGroupManager.useCoordinator(second);
        SagaTransactionGroupManager.resetCoordinator();

        assertTrue(first.closed);
        assertTrue(second.closed);
    }

    private IExecutor executor(Runnable action) {
        return new IExecutor() {
            @Override
            public Service getService() {
                return null;
            }

            @Override
            public void execute() {
                action.run();
            }
        };
    }

    private static final class CloseableCoordinator
            implements SagaTransactionGroupCoordinator, AutoCloseable {
        private boolean closed;

        @Override
        public void execute(SagaExecutionScope scope, List<IExecutor> executors) {
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
