package com.servpp.service.transaction;

import com.servpp.service.Service;
import com.servpp.service.scenario.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class BasicUpdateSchedulingTest {
    @Test public void basicClasspathContainsNoOfficialSchedulingAlgorithm() {
        assertFalse(UpdateScheduling.isAvailable());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.servpp.service.transaction.OfficialUpdateScheduling"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.servpp.service.transaction.TransactionTasks"));
    }
    @Test public void requestedAutomaticParallelUpdatesRemainSerialWithoutAProvider() {
        var events = new ArrayList<Integer>(); Thread owner = Thread.currentThread();
        var calls = new ArrayList<IExecutor>();
        for (int index = 0; index < 3; index++) {
            int expected = index;
            calls.add(new IExecutor() {
                public Service getService() { return null; }
                public boolean isPreparedOrdinaryUpdate() { return true; }
                public Object[] getArguments() { return new Object[]{new Object()}; }
                public void prepareForParallelExecution() { fail("No automatic scheduling provider is installed"); }
                public void execute() { assertSame(owner, Thread.currentThread()); assertEquals(expected, events.size()); events.add(expected); }
            });
        }
        new ITransactionGroup() {
            public boolean usesSourceTransactionPlan() { return true; }
            public boolean parallelOrdinaryUpdates() { return true; }
            public TransactionMode getDeclaredTransactionMode() { return TransactionMode.LOCAL; }
            public IExecutor[] getServices() { return calls.toArray(IExecutor[]::new); }
        }.execute();
        assertEquals(List.of(0, 1, 2), events);
        DefaultTransactionManager.clearLastContext();
    }
    @Test public void explicitBusinessParallelGroupStillExecutesConcurrently() {
        var entered = new CountDownLatch(2);
        var threads = ConcurrentHashMap.<Thread>newKeySet();
        IExecutor call = new IExecutor() {
            public Service getService() { return null; }
            public void execute() {
                threads.add(Thread.currentThread()); entered.countDown();
                try { assertTrue(entered.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            }
        };
        new IParallelExecuteGroup() { public IExecutor[] getServices() { return new IExecutor[]{call, call}; } }.execute();
        assertEquals(2, threads.size());
    }
}
