package com.servpp.service.scenario;

import java.util.Arrays;

import com.servpp.service.repository.CurrentObjectContext;
import com.servpp.service.transaction.SagaTransactionGroupManager;
import com.servpp.service.transaction.DefaultTransactionManager;
import com.servpp.service.transaction.TransactionMode;
import com.servpp.service.transaction.TransactionalService;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.runtime.GeneratedServiceKind;
import com.servpp.service.runtime.GeneratedServiceKindProvider;

/**
 * @author lidong@date 2023-11-20@version 1.0
 */
public interface ITransactionGroup extends IExecuteGroup{
    /** Null preserves the coordinator behavior of existing, unqualified groups. */
    default TransactionMode getDeclaredTransactionMode() {
        return null;
    }

    default com.servpp.service.transaction.TransactionPropagation getPropagation() {
        return com.servpp.service.transaction.TransactionPropagation.REQUIRED;
    }

    default boolean parallelOrdinaryUpdates() { return false; }

    default boolean usesSourceTransactionPlan() { return false; }

    /** Generated anonymous boundary classes provide a stable identity within one compiled deployment. */
    default String getSourceTransactionId() { return getClass().getName(); }

    @Override
    default void execute(){
        IExecutor[] services = getServices();
        if (usesSourceTransactionPlan()) {
            Object[] participants = Arrays.stream(services)
                    .flatMap(executor -> Arrays.stream(executor.getArguments() == null ? new Object[0] : executor.getArguments()))
                    .toArray();
            com.servpp.service.transaction.TransactionOutcomes.observe(() -> CurrentObjectContext.withObjects(participants,
                    () -> { com.servpp.service.transaction.SourceTransactionPlan.execute(this); return null; }));
            return;
        }
        TransactionMode mode = getDeclaredTransactionMode();
        if (mode != null) {
            if (services.length == 0) {
                throw new IllegalStateException("Empty declared transaction " + mode + " group");
            }
            for (IExecutor executor : services) {
                if (executor.getService() instanceof GeneratedServiceKindProvider generated
                        && generated.getGeneratedServiceKind() == GeneratedServiceKind.SCENARIO) {
                    throw new IllegalStateException("TRANSACTION_REQUIRES_ATOMIC_SERVICES");
                }
                if (executor.getService() instanceof GeneratedServiceKindProvider generated
                        && (generated.getGeneratedServiceKind() == GeneratedServiceKind.ATOMIC_QUERY
                            || generated.getGeneratedServiceKind() == GeneratedServiceKind.ATOMIC_CHECK
                            || generated.getGeneratedServiceKind() == GeneratedServiceKind.ATOMIC_CALCULATE)) {
                    continue;
                }
                if (executor.getService() instanceof TransactionalService transactional
                        && transactional.getTransactionMode() != mode) {
                    throw new IllegalStateException("Declared transaction " + mode + " conflicts with service "
                            + executor.getService().getName() + " mode " + transactional.getTransactionMode());
                }
            }
        }
        Object[] currentObjects = Arrays.stream(services)
                .flatMap(executor -> Arrays.stream(executor.getArguments() == null
                        ? new Object[0]
                        : executor.getArguments()))
                .toArray();
        com.servpp.service.transaction.TransactionOutcomes.observe(() -> com.servpp.service.transaction.SourceTransactionBoundary.execute(getPropagation(), mode, () -> CurrentObjectContext.withObjects(currentObjects, () -> {
            if (mode == null || mode == TransactionMode.SAGA) {
                SagaTransactionGroupManager.execute(Arrays.asList(services));
            } else {
                DefaultTransactionManager.getInstance().execute(Arrays.asList(services));
            }
            return null;
        })));
    }
}
