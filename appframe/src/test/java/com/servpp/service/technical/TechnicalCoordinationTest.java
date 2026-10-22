package com.servpp.service.technical;

import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.repository.RepositoryParticipantSnapshots;
import com.servpp.service.runtime.GeneratedServiceKind;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import com.servpp.service.transaction.TransactionOutcomes;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TechnicalCoordinationTest {
    public static class Facts {
        private String name;
        public String getName() { return name; }
        public void setName(String value) { name = value; }
    }
    public static class Replay implements TechnicalService {
        public void handle(TechnicalInvocation event) { event.call().complete("saved response"); }
    }
    public static class SelectFields implements TechnicalService {
        public void handle(TechnicalInvocation event) {
            for (var participant : event.call().participants()) {
                assertTrue(participant.baseline().isPresent());
                ((com.fasterxml.jackson.databind.node.ObjectNode)participant.facts()).put("name","must not escape");
                participant.selectUpdateFields(Set.of("name"));
            }
        }
    }
    public static class RequiredCompletion implements TechnicalService {
        public boolean critical() { return true; }
        public void handle(TechnicalInvocation event) { throw new IllegalStateException("COMPLETION_STORE_UNAVAILABLE"); }
    }
    public static class RejectedReplay implements TechnicalService {
        public void handle(TechnicalInvocation event) {
            event.call().complete(null,new BusinessChecks.Result(false,"CACHED_REJECTION"));
        }
    }
    public static class OutputFacts implements TechnicalService {
        public void handle(TechnicalInvocation event) {
            assertEquals("after",event.call().participants().get(0).facts().path("name").asText());
            assertEquals("after",event.call().argumentFacts().get(0).path("name").asText());
        }
    }

    @Test public void beforeCanReturnAResultWithoutExecutingTheBusinessAction() {
        var plan = plan(TechnicalInvocation.Phase.BEFORE,Replay.class);
        BusinessChecks.recordRejection("PREVIOUS_REQUEST");
        assertEquals("saved response",plan.invoke("scene",GeneratedServiceKind.SCENARIO,
                () -> { throw new AssertionError("A replay must not run the business action"); }));
        assertTrue(BusinessChecks.scenarioResult().passed());
    }

    @Test public void replayRestoresBusinessRejectionAndActiveCheckFrame() {
        var plan = plan(TechnicalInvocation.Phase.BEFORE,RejectedReplay.class);
        try {
            BusinessChecks.Result result = BusinessChecks.evaluate(() -> plan.invoke("check",GeneratedServiceKind.ATOMIC_CHECK,
                    () -> { throw new AssertionError("replayed check executed"); }));
            assertFalse(result.passed()); assertEquals("CACHED_REJECTION",result.code());
            assertEquals(result,BusinessChecks.scenarioResult());
            plan.invoke("scene",GeneratedServiceKind.SCENARIO,() -> { throw new AssertionError("replayed scene executed"); });
            assertEquals(result,BusinessChecks.scenarioResult());
        } finally { BusinessChecks.restoreScenarioResult(null); BusinessChecks.restoreCheckResult(null); }
    }

    @Test public void afterReceivesCurrentFactsInsteadOfTheBeforeSnapshot() {
        var plan = ServiceInterceptors.create(getClass().getClassLoader(),List.of(
                new ServiceInterceptors.Binding(TechnicalInvocation.Phase.BEFORE,"select",SelectFields.class.getName(),Map.of()),
                new ServiceInterceptors.Binding(TechnicalInvocation.Phase.AFTER,"facts",OutputFacts.class.getName(),Map.of())));
        RepositoryParticipantSnapshots.execute(() -> {
            var value = new Facts(); value.setName("before"); RepositoryParticipantSnapshots.remember(value);
            plan.invoke("change",GeneratedServiceKind.ATOMIC_UPDATE,new Object[]{value},() -> { value.setName("after"); return value; });
        });
    }

    @Test public void fieldSetsAndReadOnlyFactsBelongToOneInvocation() {
        var outer = plan(TechnicalInvocation.Phase.BEFORE,SelectFields.class);
        var inner = plan(TechnicalInvocation.Phase.BEFORE,SelectFields.class);
        RepositoryParticipantSnapshots.execute(() -> {
            var first = new Facts(); first.setName("first"); RepositoryParticipantSnapshots.remember(first);
            var second = new Facts(); second.setName("second"); RepositoryParticipantSnapshots.remember(second);
            outer.invoke("update",GeneratedServiceKind.ATOMIC_UPDATE,new Object[]{first},() -> {
                assertEquals(Set.of("name"),UpdateFieldPlans.fields(first).orElseThrow());
                ServiceInterceptors.none().invoke("disabled",GeneratedServiceKind.ATOMIC_UPDATE,new Object[]{first},() -> {
                    assertFalse(UpdateFieldPlans.fields(first).isPresent()); return null;
                });
                assertEquals(Set.of("name"),UpdateFieldPlans.fields(first).orElseThrow());
                inner.invoke("nested",GeneratedServiceKind.ATOMIC_UPDATE,new Object[]{second},() -> {
                    assertFalse(UpdateFieldPlans.fields(first).isPresent());
                    assertEquals(Set.of("name"),UpdateFieldPlans.fields(second).orElseThrow());
                    return null;
                });
                assertEquals(Set.of("name"),UpdateFieldPlans.fields(first).orElseThrow());
                assertFalse(UpdateFieldPlans.fields(second).isPresent());
                assertEquals("first",first.getName()); assertEquals("second",second.getName());
                return null;
            });
            assertFalse(UpdateFieldPlans.fields(first).isPresent());
        });
    }

    @Test public void criticalCompletionFailureAfterCommitPreservesTheCommittedFact() throws Exception {
        var source = database();
        var plan = plan(TechnicalInvocation.Phase.AFTER,RequiredCompletion.class);
        var failure = assertThrows(IllegalStateException.class, () -> plan.invoke("scene",GeneratedServiceKind.SCENARIO,() -> {
            DeclaredTransactionContext.execute(TransactionMode.LOCAL,() -> transaction(source,() -> insert(source)));
            return "done";
        }));
        assertEquals("COMPLETION_STORE_UNAVAILABLE",failure.getMessage());
        assertEquals(TransactionOutcomes.Outcome.UNKNOWN,TransactionOutcomes.outcome(failure));
        assertEquals(1,count(source));
    }

    @Test public void criticalLocalAfterFailureRollsBackTheOwningTransaction() throws Exception {
        var source = database();
        var plan = plan(TechnicalInvocation.Phase.AFTER,RequiredCompletion.class);
        var failure = assertThrows(IllegalStateException.class, () ->
                DeclaredTransactionContext.execute(TransactionMode.LOCAL,() -> transaction(source,() ->
                        plan.invoke("update",GeneratedServiceKind.ATOMIC_UPDATE,() -> { insert(source); return null; }))));
        assertEquals(TransactionOutcomes.Outcome.ROLLED_BACK,TransactionOutcomes.outcome(failure));
        assertEquals(0,count(source));
    }
    private ServiceInterceptors plan(TechnicalInvocation.Phase phase,Class<? extends TechnicalService> type) {
        return ServiceInterceptors.create(getClass().getClassLoader(),List.of(
                new ServiceInterceptors.Binding(phase,type.getSimpleName(),type.getName(),Map.of())));
    }
    private JdbcDataSource database() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:technical_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.execute("create table facts(id int primary key)");
        }
        return source;
    }
    private void transaction(JdbcDataSource source,Runnable action) {
        try { JdbcTransactionScope.execute(source,connection -> { action.run(); return null; }); }
        catch(java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }
    private void insert(JdbcDataSource source) {
        try(var statement=JdbcTransactionScope.currentConnection(source).orElseThrow().createStatement()) {
            statement.executeUpdate("insert into facts values(1)");
        } catch(java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }
    private int count(JdbcDataSource source) throws Exception {
        try(var connection=source.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("select count(*) from facts")) {
            rows.next(); return rows.getInt(1);
        }
    }
}
