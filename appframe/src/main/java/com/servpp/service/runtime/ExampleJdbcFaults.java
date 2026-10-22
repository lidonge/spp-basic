package com.servpp.service.runtime;

import com.servpp.service.jdbc.JdbcTransactionScope;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/** One physical lifecycle fault, active only while a declared example runs. */
public final class ExampleJdbcFaults {
    private static final ThreadLocal<Plan> CURRENT = new ThreadLocal<>();
    private static final Set<String> POINTS = Set.of("beforeCommit", "commitBefore", "commitAfter", "afterCommit");
    private ExampleJdbcFaults() {}
    public static Runnable bind(Runnable action) {
        Plan captured = CURRENT.get();
        return () -> {
            Plan previous = CURRENT.get();
            if (captured == null) CURRENT.remove(); else CURRENT.set(captured);
            try { action.run(); }
            finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
        };
    }
    public static Scope activate(String service, String point) {
        if (service == null && point != null || service != null && (service.isBlank() || point == null || !POINTS.contains(point))) {
            throw new IllegalArgumentException("Invalid example JDBC fault");
        }
        Plan previous = CURRENT.get(), plan = service == null ? null : new Plan(service, point);
        if (plan == null) CURRENT.remove(); else CURRENT.set(plan);
        return new Scope(plan, previous);
    }
    static void arm(String service) {
        Plan plan = CURRENT.get();
        if (plan == null || plan.connection != null || !plan.service.equals(service)) return;
        var connections = JdbcTransactionScope.currentConnections();
        if (connections.size() != 1) throw new IllegalStateException("EXAMPLE_JDBC_FAULT_REQUIRES_ONE_CONNECTION");
        plan.connection = connections.get(0);
    }
    public static void beforeCommit(Connection connection) {
        if (claim(connection, "beforeCommit")) throw new IllegalStateException("EXAMPLE_JDBC_BEFORE_COMMIT");
    }
    /** Presents the same caller-visible contract as a JDBC driver commit invocation. */
    public static void commit(Connection connection) throws SQLException {
        if (claim(connection, "commitBefore")) throw new SQLException("EXAMPLE_JDBC_COMMIT_FAULT", "08006");
        connection.commit();
        if (claim(connection, "commitAfter")) throw new SQLException("EXAMPLE_JDBC_COMMIT_FAULT", "08006");
    }
    public static void afterCommit(Connection connection) {
        if (claim(connection, "afterCommit")) throw new IllegalStateException("EXAMPLE_JDBC_RESPONSE_FAILURE");
    }
    private static boolean claim(Connection connection, String point) {
        Plan plan = CURRENT.get();
        if (plan == null || plan.fired || plan.connection != connection || !plan.point.equals(point)) return false;
        plan.fired = true; return true;
    }
    private static final class Plan {
        final String service, point;
        volatile Connection connection;
        volatile boolean fired;
        Plan(String service, String point) { this.service = service; this.point = point; }
    }
    public static final class Scope implements AutoCloseable {
        private final Plan plan, previous;
        private Scope(Plan plan, Plan previous) { this.plan = plan; this.previous = previous; }
        public void verifyTriggered() {
            if (plan != null && !plan.fired) throw new IllegalStateException("EXAMPLE_JDBC_FAULT_NOT_TRIGGERED: " + plan.service);
        }
        @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
