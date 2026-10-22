package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import com.servpp.service.transaction.TccBranchStatus;
import com.servpp.service.transaction.TccTransactionParticipant;
import com.servpp.service.transaction.TransactionContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PhysicalCoordinatedTccTransactionParticipantTest {
    @Test
    public void physicalCoordinatorOwnsConfirmAndCallsLiveBusinessParticipant() {
        RecordingBusinessParticipant business = new RecordingBusinessParticipant();
        RecordingPhysicalAdapter adapter = new RecordingPhysicalAdapter(true);
        PhysicalCoordinatedTccTransactionParticipant participant = participant(business, adapter);
        TransactionContext context = new TransactionContext(Map.of());

        participant.begin(context);
        participant.beforeTry(context);
        participant.afterTry(context);
        participant.confirm(context);

        assertEquals(List.of("begin", "beforeTry", "afterTry", "confirm"), business.events);
        assertEquals(List.of("begin", "register", "beforeTry", "afterTry", "confirm"), adapter.events);
    }

    @Test
    public void physicalCoordinatorOwnsCancelAndCallsLiveBusinessParticipant() {
        RecordingBusinessParticipant business = new RecordingBusinessParticipant();
        RecordingPhysicalAdapter adapter = new RecordingPhysicalAdapter(true);
        PhysicalCoordinatedTccTransactionParticipant participant = participant(business, adapter);
        TransactionContext context = new TransactionContext(Map.of());

        participant.begin(context);
        participant.beforeTry(context);
        participant.afterTry(context);
        participant.cancel(context, new IllegalStateException("order failed"));

        assertEquals(List.of("begin", "beforeTry", "afterTry", "cancel"), business.events);
        assertEquals(List.of("begin", "register", "beforeTry", "afterTry", "cancel"), adapter.events);
    }

    @Test
    public void localCoordinatorKeepsSynchronousBusinessLifecycle() {
        RecordingBusinessParticipant business = new RecordingBusinessParticipant();
        RecordingPhysicalAdapter adapter = new RecordingPhysicalAdapter(false);
        PhysicalCoordinatedTccTransactionParticipant participant = participant(business, adapter);
        TransactionContext context = new TransactionContext(Map.of());

        participant.begin(context);
        participant.beforeTry(context);
        participant.afterTry(context);
        participant.confirm(context);

        assertEquals(List.of("begin", "beforeTry", "afterTry", "confirm"), business.events);
        assertEquals(List.of("begin", "beforeTry", "afterTry", "confirm"), adapter.events);
    }

    @Test
    public void failedNativePrepareCancelsBusinessTryAndUnregistersCallback() {
        RecordingBusinessParticipant business = new RecordingBusinessParticipant();
        RecordingPhysicalAdapter adapter = new RecordingPhysicalAdapter(true);
        adapter.failBeforeTry = true;
        PhysicalCoordinatedTccTransactionParticipant participant = participant(business, adapter);
        TransactionContext context = new TransactionContext(Map.of());

        participant.begin(context);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> participant.beforeTry(context));

        assertEquals("branch registration failed", failure.getMessage());
        assertEquals(List.of("begin", "beforeTry", "cancel"), business.events);
        assertEquals(List.of("begin", "register", "beforeTry", "unregister"), adapter.events);
    }

    private PhysicalCoordinatedTccTransactionParticipant participant(
            RecordingBusinessParticipant business, RecordingPhysicalAdapter adapter) {
        PhysicalBackendAssignment assignment = new PhysicalBackendAssignment(
                "reserveInventory", "tcc", "inventoryTcc", "creditTx", "seata",
                true, "RESOURCE", "tcc:inventoryTcc", true, true, true,
                adapter.implementationType(), Map.of());
        PhysicalBackendTransactionParticipant physical =
                new PhysicalBackendTransactionParticipant("inventoryTcc",
                        () -> Optional.of(new PhysicalBackendTransactionParticipant.ResolvedBackend(
                                assignment, adapter)));
        return new PhysicalCoordinatedTccTransactionParticipant("inventoryTcc", business, physical);
    }

    private static final class RecordingBusinessParticipant implements TccTransactionParticipant {
        private final List<String> events = new ArrayList<>();

        @Override
        public String getResourceId() {
            return "inventoryTcc";
        }

        @Override
        public void begin(TransactionContext context) {
            events.add("begin");
        }

        @Override
        public void beforeTry(TransactionContext context) {
            events.add("beforeTry");
        }

        @Override
        public void afterTry(TransactionContext context) {
            events.add("afterTry");
        }

        @Override
        public void confirm(TransactionContext context) {
            events.add("confirm");
        }

        @Override
        public void cancel(TransactionContext context, Throwable cause) {
            events.add("cancel");
        }
    }

    private static final class RecordingPhysicalAdapter implements PhysicalBackendAdapter {
        private final boolean ownsPhaseTwo;
        private final List<String> events = new ArrayList<>();
        private TccPhaseTwoRecoveryHandler callback;
        private boolean failBeforeTry;

        private RecordingPhysicalAdapter(boolean ownsPhaseTwo) {
            this.ownsPhaseTwo = ownsPhaseTwo;
        }

        @Override
        public Set<String> backendNames() {
            return Set.of("seata");
        }

        @Override
        public boolean ownsTccPhaseTwo(PhysicalBackendAssignment assignment) {
            return ownsPhaseTwo;
        }

        @Override
        public void registerTccPhaseTwoCallback(PhysicalBackendAssignment assignment,
                                                TransactionContext context,
                                                TccPhaseTwoRecoveryHandler callback) {
            events.add("register");
            this.callback = callback;
        }

        @Override
        public void unregisterTccPhaseTwoCallback(PhysicalBackendAssignment assignment,
                                                  TransactionContext context) {
            events.add("unregister");
            callback = null;
        }

        @Override
        public void begin(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            events.add("begin");
        }

        @Override
        public void beforeTry(PhysicalBackendAssignment assignment,
                              TransactionContext context, Object[] arguments) {
            events.add("beforeTry");
            if (failBeforeTry) {
                throw new IllegalStateException("branch registration failed");
            }
        }

        @Override
        public void afterTry(PhysicalBackendAssignment assignment,
                             TransactionContext context, Object[] arguments) {
            events.add("afterTry");
        }

        @Override
        public void confirm(PhysicalBackendAssignment assignment,
                            TransactionContext context, Object[] arguments) {
            events.add("confirm");
            if (ownsPhaseTwo) {
                callback.recover(branchId(context, assignment), TccBranchStatus.CONFIRMED);
            }
        }

        @Override
        public void cancel(PhysicalBackendAssignment assignment, TransactionContext context,
                           Object[] arguments, Throwable cause) {
            events.add("cancel");
            if (ownsPhaseTwo) {
                callback.recover(branchId(context, assignment), TccBranchStatus.CANCELLED);
            }
        }

        private String branchId(TransactionContext context, PhysicalBackendAssignment assignment) {
            return context.getTraceId() + ":" + assignment.targetName() + ":" + assignment.serviceName();
        }
    }
}
