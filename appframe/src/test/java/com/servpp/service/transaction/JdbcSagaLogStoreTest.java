package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import com.servpp.service.jdbc.JdbcTransactionScope;

public class JdbcSagaLogStoreTest {
    @Test
    public void persistsAndClearsSagaLogs() {
        JdbcSagaLogStore store = new JdbcSagaLogStore(h2DataSource());

        store.append(new SagaLogEntry("SAGA-1", "REQ-1", "submitOrder", "DONE",
                "submit order completed", "TX-1", "creditAccounting+inventoryAccounting",
                "2026-06-27T00:00:00Z"));

        List<SagaLogEntry> entries = store.findAll();
        assertEquals(1, entries.size());
        assertEquals("SAGA-1", entries.get(0).sagaId());
        assertEquals("REQ-1", entries.get(0).globalSerialNumber());
        assertEquals("submitOrder", entries.get(0).serviceName());
        assertEquals("DONE", entries.get(0).status());
        assertEquals("TX-1", entries.get(0).transactionId());
        assertEquals("creditAccounting+inventoryAccounting", entries.get(0).quantumBoundaryServiceId());
        assertEquals(null, entries.get(0).recoveryPayload());

        store.clear();
        assertTrue(store.findAll().isEmpty());
    }

    @Test
    public void persistsRecoveryPayloadWhenPresent() {
        JdbcSagaLogStore store = new JdbcSagaLogStore(h2DataSource());

        store.append(new SagaLogEntry("SAGA-PAYLOAD", "REQ-PAYLOAD", "remoteReleasePricing",
                "REMOTE_COMPENSATION_FAILED", "remote release failed", "TX-PAYLOAD",
                "creditAccounting", "2026-06-27T00:00:02Z", "[{\"className\":\"example.Request\"}]"));

        List<SagaLogEntry> entries = store.findAll();
        assertEquals(1, entries.size());
        assertEquals("[{\"className\":\"example.Request\"}]", entries.get(0).recoveryPayload());
    }

    @Test
    public void keepsSagaLogWhenBusinessJdbcTransactionRollsBack() throws Exception {
        JdbcDataSource dataSource = h2DataSource();
        JdbcSagaLogStore store = new JdbcSagaLogStore(dataSource);

        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                store.append(new SagaLogEntry("SAGA-2", "REQ-2", "submitOrder", "COMPENSATED",
                        "simulated failure", "TX-2", "creditAccounting+inventoryAccounting",
                        "2026-06-27T00:00:01Z"));
                throw new SQLException("rollback saga log");
            });
            fail("Expected rollback");
        } catch (SQLException expected) {
            assertEquals("rollback saga log", expected.getMessage());
        }

        List<SagaLogEntry> entries = store.findAll();
        assertEquals(1, entries.size());
        assertEquals("SAGA-2", entries.get(0).sagaId());
        assertEquals("COMPENSATED", entries.get(0).status());
    }

    private JdbcDataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:saga-log-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
