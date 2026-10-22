package com.servpp.service.scenario;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author lidong@date 2023-11-20@version 1.0
 */
public interface IParallelExecuteGroup extends IExecuteGroup{
    @Override
    default void execute(){
        var outcomes = new com.servpp.service.runtime.BusinessChecks.Result[getServices().length];
        CompletableFuture<?>[] futures = java.util.stream.IntStream.range(0, getServices().length)
                .mapToObj(index -> CompletableFuture.runAsync(com.servpp.service.runtime.ParticipantIdentity.bind(() -> {
                    var previous = com.servpp.service.runtime.BusinessChecks.scenarioResult();
                    com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(null);
                    try {
                        getServices()[index].execute();
                        outcomes[index] = com.servpp.service.runtime.BusinessChecks.scenarioResult();
                    } finally { com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(previous); }
                })))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).join();
            for (var outcome : outcomes)
                if (outcome != null && !outcome.passed()) {
                    com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(outcome);
                    break;
                }
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
