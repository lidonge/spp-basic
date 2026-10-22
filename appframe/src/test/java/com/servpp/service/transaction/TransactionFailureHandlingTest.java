package com.servpp.service.transaction;

import java.util.List;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransactionFailureHandlingTest {
    public static class FailureInfo {
        private String code, reason, serviceName, resourceName, phase, transactionOutcome, recoveryStatus, attribute, actualValue, minimum, maximum;
        private List<String> causes, suppressedCodes;
        public String getCode(){return code;} public void setCode(String v){code=v;}
        public String getReason(){return reason;} public void setReason(String v){reason=v;}
        public String getServiceName(){return serviceName;} public void setServiceName(String v){serviceName=v;}
        public String getResourceName(){return resourceName;} public void setResourceName(String v){resourceName=v;}
        public String getPhase(){return phase;} public void setPhase(String v){phase=v;}
        public String getTransactionOutcome(){return transactionOutcome;} public void setTransactionOutcome(String v){transactionOutcome=v;}
        public String getRecoveryStatus(){return recoveryStatus;} public void setRecoveryStatus(String v){recoveryStatus=v;}
        public String getAttribute(){return attribute;} public void setAttribute(String v){attribute=v;}
        public String getActualValue(){return actualValue;} public void setActualValue(String v){actualValue=v;}
        public String getMinimum(){return minimum;} public void setMinimum(String v){minimum=v;}
        public String getMaximum(){return maximum;} public void setMaximum(String v){maximum=v;}
        public List<String> getCauses(){return causes;} public void setCauses(List<String> v){causes=v;}
        public List<String> getSuppressedCodes(){return suppressedCodes;} public void setSuppressedCodes(List<String> v){suppressedCodes=v;}
    }
    public static class ResultInfo {
        private String status; private Boolean handled; private FailureInfo failure;
        public String getStatus(){return status;} public void setStatus(String v){status=v;}
        public Boolean getHandled(){return handled;} public void setHandled(Boolean v){handled=v;}
        public FailureInfo getFailure(){return failure;} public void setFailure(FailureInfo v){failure=v;}
    }
    public static class Agreement {
        private ResultInfo resultInfo;
        public ResultInfo getResultInfo(){return resultInfo;} public void setResultInfo(ResultInfo v){resultInfo=v;}
    }
    @Test public void confirmedRecoveryPrecedesDispositionAndExposesFailureFacts() {
        var order = new ArrayList<String>(); var result = new ResultInfo(); var agreement = new Agreement();
        var failure = new IllegalStateException("STOCK_BOUND", new IllegalStateException("INSUFFICIENT_STOCK"));
        TransactionFailureHandling.recordOrigin(failure,"reserveStock","stockLedger","EXECUTION");
        TransactionFailureHandling.execute(result,agreement, () -> {
            order.add("recover"); TransactionOutcomes.record(failure, TransactionOutcomes.Outcome.ROLLED_BACK); throw failure;
        }, () -> {
            assertEquals(List.of("recover"),order);
            assertSame(result,agreement.getResultInfo());
            assertEquals("reserveStock",result.getFailure().getServiceName());
            assertEquals(List.of("INSUFFICIENT_STOCK"),result.getFailure().getCauses());
            order.add("retain application");
        });
        assertEquals(List.of("recover","retain application"),order);
        assertEquals("FAILED",result.getStatus()); assertTrue(result.getHandled());
    }
    @Test public void uncertainOutcomeAllowsRecordingButStillPropagatesOriginalFailure() {
        var result = new ResultInfo(); var failure = new IllegalStateException("COMMIT_CONFIRMATION_LOST");
        TransactionOutcomes.record(failure,TransactionOutcomes.Outcome.UNKNOWN);
        assertSame(failure,assertThrows(IllegalStateException.class, () -> TransactionFailureHandling.execute(result,new Agreement(),
                () -> { throw failure; }, () -> assertEquals("RECOVERY_REQUIRED",result.getFailure().getRecoveryStatus()))));
        assertFalse(result.getHandled());
    }
    @Test public void failedDispositionPreservesPrimaryFailureAndItsAdditionalCause() {
        var result = new ResultInfo(); var primary = new IllegalStateException("STOCK_BOUND");
        var secondary = new IllegalStateException("REJECTION_SAVE_FAILED");
        TransactionOutcomes.record(primary,TransactionOutcomes.Outcome.ROLLED_BACK);
        assertSame(primary,assertThrows(IllegalStateException.class, () -> TransactionFailureHandling.execute(result,new Agreement(),
                () -> { throw primary; }, () -> { throw secondary; })));
        assertArrayEquals(new Throwable[]{secondary},primary.getSuppressed()); assertFalse(result.getHandled());
        assertEquals("STOCK_BOUND",result.getFailure().getCode());
        assertEquals(List.of("REJECTION_SAVE_FAILED"),result.getFailure().getSuppressedCodes());
    }
    @Test public void completedReversalIsDistinctFromLocalRollback() {
        var result = new ResultInfo(); var failure = new IllegalStateException("ORDINARY_STAGE_FAILED");
        TransactionFailureHandling.recordReversed(failure);
        TransactionFailureHandling.execute(result,new Agreement(),() -> { throw failure; },
                () -> assertEquals("REVERSED",result.getFailure().getRecoveryStatus()));
        assertTrue(result.getHandled());
    }
    @Test public void successfulTransactionDoesNotRunFailOrProduceFailureFacts() {
        var result = new ResultInfo(); ScenarioResults.initialize(result);
        TransactionFailureHandling.execute(result,new Agreement(),() -> {},() -> fail("fail must not run"));
        ScenarioResults.complete(result); assertEquals("SUCCEEDED",result.getStatus()); assertNull(result.getFailure());
    }
    public static class Stock {
        private int available = 10, reserved;
        public Integer getAvailable(){return available;} public void setAvailable(Integer v){available=v;}
        public Integer getReserved(){return reserved;} public void setReserved(Integer v){reserved=v;}
    }
    private record StageService(String name, TransactionMode mode) implements com.servpp.service.Service, TransactionalService {
        public String getName(){return name;}
        public Object execute(Object... args){return null;}
        public TransactionMode getTransactionMode(){return mode;}
        public String getTransactionResourceId(){return mode == TransactionMode.QUANTUM ? "ledger" : "records";}
        public TransactionParticipant getTransactionParticipant(){
            return mode == TransactionMode.QUANTUM
                    ? new QuantumConservationParticipant("ledger").conserveArgument(0,"inventory","Stock.available","available")
                            .conserveArgument(0,"inventory","Stock.reserved","reserved")
                    : TransactionParticipant.noop("records");
        }
    }
    @Test public void sourcePlanActuallyReversesCommittedQuantumBeforeEnteringFail() {
        var manager = DefaultTransactionManager.getInstance();
        var previousStore = manager.getQuantumJournalStore(); var previousAdapter = manager.getQuantumAccessAdapter();
        var source = new com.servpp.service.jdbc.DriverManagerJdbcDataSource("org.h2.Driver",
                "jdbc:h2:mem:fail_reversal_"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var store = new JdbcQuantumJournalStore(source,"journal","quantum_values","text");
        store.seedCurrentValue("inventory","Stock.available",java.math.BigDecimal.TEN);
        store.seedCurrentValue("inventory","Stock.reserved",java.math.BigDecimal.ZERO);
        manager.setQuantumJournalStore(store);
        try {
            var stock = new Stock();
            var quantum = new com.servpp.service.scenario.IExecutor() {
                final StageService service = new StageService("reserveStock",TransactionMode.QUANTUM);
                public com.servpp.service.Service getService(){return service;}
                public Object[] getArguments(){return new Object[]{stock};}
                public void execute(){stock.available -= 2; stock.reserved += 2;}
            };
            var ordinary = new com.servpp.service.scenario.IExecutor() {
                final StageService service = new StageService("saveReservation",TransactionMode.AT);
                public com.servpp.service.Service getService(){return service;}
                public void execute(){
                    assertEquals(0,new java.math.BigDecimal("8").compareTo(store.findCurrentValue("Stock.available").orElseThrow()));
                    throw new IllegalStateException("RESERVATION_SAVE_FAILED");
                }
            };
            var boundary = new com.servpp.service.scenario.ITransactionGroup() {
                public boolean usesSourceTransactionPlan(){return true;}
                public TransactionMode getDeclaredTransactionMode(){return TransactionMode.SAGA;}
                public com.servpp.service.scenario.IExecutor[] getServices(){return new com.servpp.service.scenario.IExecutor[]{quantum,ordinary};}
            };
            var result = new ResultInfo();
            TransactionFailureHandling.execute(result,new Agreement(),boundary::execute,() -> {
                assertEquals("REVERSED",result.getFailure().getRecoveryStatus());
                assertEquals("saveReservation",result.getFailure().getServiceName());
                assertEquals(0,java.math.BigDecimal.TEN.compareTo(store.findCurrentValue("Stock.available").orElseThrow()));
                assertEquals(0,java.math.BigDecimal.ZERO.compareTo(store.findCurrentValue("Stock.reserved").orElseThrow()));
            });
            assertTrue(result.getHandled());
        } finally {
            manager.setQuantumJournalStore(previousStore); manager.setQuantumAccessAdapter(previousAdapter);
            DefaultTransactionManager.clearLastContext();
            try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
            catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        }
    }

    private static class EqualFailure extends IllegalStateException {
        EqualFailure(String message){super(message);}
        public boolean equals(Object other){return other instanceof EqualFailure;}
        public int hashCode(){return 1;}
    }
    @Test public void failureMetadataUsesExactIdentityEvenWhenExceptionsCompareEqual() {
        var first = new EqualFailure("FIRST"); var second = new EqualFailure("SECOND");
        TransactionFailureHandling.recordOrigin(first,"firstService","records","EXECUTION");
        TransactionFailureHandling.recordOrigin(second,"secondService","records","EXECUTION");
        for (var failure : List.of(first,second)) {
            TransactionOutcomes.record(failure,TransactionOutcomes.Outcome.ROLLED_BACK);
            var result = new ResultInfo();
            TransactionFailureHandling.execute(result,new Agreement(),() -> { throw failure; },
                    () -> assertEquals(failure == first ? "firstService" : "secondService",result.getFailure().getServiceName()));
        }
    }

}
