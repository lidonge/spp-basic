package com.servpp.service.scenario;

import com.servpp.service.Service;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IParallelExecuteGroupTest {
    @Test
    public void executeRunsAllBranchesAndWaitsForCompletion() {
        AtomicInteger count = new AtomicInteger();
        IParallelExecuteGroup group = () -> new IExecutor[]{
                executor(count::incrementAndGet),
                executor(() -> {
                    sleep(30);
                    count.addAndGet(10);
                })
        };

        group.execute();

        assertEquals(11, count.get());
    }

    @Test
    public void executePropagatesBranchRuntimeException() {
        IParallelExecuteGroup group = () -> new IExecutor[]{
                executor(() -> {
                }),
                executor(() -> {
                    throw new IllegalStateException("branch failed");
                })
        };

        IllegalStateException e = assertThrows(IllegalStateException.class, group::execute);
        assertEquals("branch failed", e.getMessage());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static IExecutor executor(Runnable runnable) {
        return new IExecutor() {
            @Override
            public Service getService() {
                return null;
            }

            @Override
            public void execute() {
                runnable.run();
            }
        };
    }
}
