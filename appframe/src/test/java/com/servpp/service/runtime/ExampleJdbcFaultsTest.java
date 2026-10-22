package com.servpp.service.runtime;

import com.servpp.service.AtomicService;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.transaction.TransactionOutcomes;
import org.h2.jdbcx.JdbcDataSource;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleJdbcFaultsTest {
    public interface Save extends AtomicService { void save(); }
    public static class Action implements Save {
        final DataSource source; int id;
        Action(DataSource source) { this.source = source; }
        public String getName() { return "save"; }
        public void save() {
            try (var statement = JdbcTransactionScope.currentConnection(source).orElseThrow().createStatement()) {
                statement.execute("insert into sample_record values (" + ++id + ")");
            } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        }
        public Object execute(Object... values) { save(); return null; }
    }
    private void verify(String point, TransactionOutcomes.Outcome outcome, int rows, int rollbacks) throws Exception {
        JdbcDataSource raw = new JdbcDataSource(); raw.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var c = raw.getConnection(); var s = c.createStatement()) { s.execute("create table sample_record(id int primary key)"); }
        AtomicInteger rollbackCalls = new AtomicInteger();
        DataSource source = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{DataSource.class}, (proxy, method, args) -> {
            Object value;
            try { value = method.invoke(raw, args); } catch (InvocationTargetException failure) { throw failure.getCause(); }
            if (!(value instanceof Connection connection)) return value;
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p,m,a) -> {
                if (m.getName().equals("rollback")) rollbackCalls.incrementAndGet();
                try { return m.invoke(connection, a); } catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        });
        Save proxy = (Save) ExampleServiceFaults.decorate(new Action(source));
        try (var fault = ExampleJdbcFaults.activate("save", point)) {
            // An unrelated earlier transaction cannot consume the armed lifecycle fault.
            JdbcTransactionScope.execute(source, connection -> null);
            Exception failure = assertThrows(Exception.class, () -> JdbcTransactionScope.execute(source, connection -> { proxy.save(); return null; }));
            assertEquals(outcome, TransactionOutcomes.outcome(failure));
            fault.verifyTriggered(); assertEquals(rollbacks, rollbackCalls.get());
            try (var c = raw.getConnection(); var s = c.createStatement(); var r = s.executeQuery("select count(*) from sample_record")) {
                r.next(); assertEquals(rows, r.getInt(1));
            }
            // The same active declaration is one-shot: the retry must genuinely commit.
            JdbcTransactionScope.execute(source, connection -> { proxy.save(); return null; });
            try (var c = raw.getConnection(); var s = c.createStatement(); var r = s.executeQuery("select count(*) from sample_record")) {
                r.next(); assertEquals(rows + 1, r.getInt(1));
            }
        }
        JdbcTransactionScope.execute(source, connection -> null);
        try (var c = raw.getConnection(); var s = c.createStatement()) { s.execute("SHUTDOWN"); }
    }
    @Test public void checkpointBeforeCommitIsConfirmedRollback() throws Exception { verify("beforeCommit", TransactionOutcomes.Outcome.ROLLED_BACK, 0, 1); }
    @Test public void driverFailureBeforePhysicalCommitIsUnknownDespiteEmptyStorage() throws Exception { verify("commitBefore", TransactionOutcomes.Outcome.UNKNOWN, 0, 1); }
    @Test public void lostCommitAcknowledgementIsUnknownWithPersistedStorage() throws Exception { verify("commitAfter", TransactionOutcomes.Outcome.UNKNOWN, 1, 1); }
    @Test public void responseFailureAfterConfirmedCommitNeverCallsRollback() throws Exception { verify("afterCommit", TransactionOutcomes.Outcome.COMMITTED, 1, 0); }
    @Test public void missingAnchorAndAmbiguousConnectionCannotSatisfyFault() throws Exception {
        try (var scope = ExampleJdbcFaults.activate("save", "commitBefore")) {
            assertThrows(IllegalStateException.class, scope::verifyTriggered);
            assertThrows(IllegalStateException.class, () -> ExampleJdbcFaults.arm("save"));
        }
        assertThrows(IllegalArgumentException.class, () -> ExampleJdbcFaults.activate("save", null));
        var a = new JdbcDataSource(); a.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var b = new JdbcDataSource(); b.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        try (var scope = ExampleJdbcFaults.activate("save", "commitBefore")) {
            assertThrows(IllegalStateException.class, () -> JdbcTransactionScope.execute(a, ca -> JdbcTransactionScope.execute(b, cb -> {
                ExampleJdbcFaults.arm("save"); return null;
            })));
            assertThrows(IllegalStateException.class, scope::verifyTriggered);
        }
    }
}
