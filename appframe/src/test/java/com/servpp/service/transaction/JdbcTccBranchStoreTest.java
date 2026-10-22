package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import com.servpp.service.jdbc.JdbcTransactionScope;

public class JdbcTccBranchStoreTest {
    @Test
    public void persistsUpdatesAndClearsTccBranch() {
        JdbcTccBranchStore store = new JdbcTccBranchStore(h2DataSource());

        store.save(new TccBranchRecord("BR-1", "REQ-1", "inventory", "lockInventory",
                TccBranchStatus.TRYING, "inventory locked", "2026-06-27T00:00:00Z", "[{\"className\":null}]"));
        store.save(new TccBranchRecord("BR-1", "REQ-1", "inventory", "lockInventory",
                TccBranchStatus.CONFIRMED, "inventory confirmed", "2026-06-27T00:00:01Z",
                "[{\"className\":null}]"));

        TccBranchRecord record = store.find("BR-1").orElseThrow();
        assertEquals("REQ-1", record.globalSerialNumber());
        assertEquals("inventory", record.resourceId());
        assertEquals(TccBranchStatus.CONFIRMED, record.status());
        assertEquals("inventory confirmed", record.message());
        assertEquals("[{\"className\":null}]", record.recoveryPayload());
        assertEquals(1, store.findAll().size());

        store.clear();
        assertTrue(store.findAll().isEmpty());
    }

    @Test
    public void persistsBranchStateOutsideSharedJdbcRollback() throws Exception {
        JdbcDataSource dataSource = h2DataSource();
        JdbcTccBranchStore store = new JdbcTccBranchStore(dataSource);

        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                store.save(new TccBranchRecord("BR-2", "REQ-2", "inventory", "lockInventory",
                        TccBranchStatus.TRYING, "inventory locked", "2026-06-27T00:00:00Z"));
                throw new SQLException("rollback tcc branch");
            });
            fail("Expected rollback");
        } catch (SQLException expected) {
            assertEquals("rollback tcc branch", expected.getMessage());
        }

        assertEquals(1, store.findAll().size());
        assertEquals(TccBranchStatus.TRYING, store.findAll().get(0).status());
    }

    @Test
    public void findsTccBranchesByStatusForRecoveryCandidates() {
        JdbcTccBranchStore store = new JdbcTccBranchStore(h2DataSource());

        store.save(new TccBranchRecord("BR-TRY", "REQ-TRY", "inventory", "lockInventory",
                TccBranchStatus.TRYING, "inventory locked", "2026-06-27T00:00:00Z"));
        store.save(new TccBranchRecord("BR-DONE", "REQ-DONE", "inventory", "lockInventory",
                TccBranchStatus.CONFIRMED, "inventory confirmed", "2026-06-27T00:00:01Z"));

        assertEquals(1, store.findByStatus(TccBranchStatus.TRYING).size());
        assertEquals("BR-TRY", store.findByStatus(TccBranchStatus.TRYING).get(0).branchId());
        assertTrue(store.findByStatus(TccBranchStatus.CANCELLED).isEmpty());
    }

    private JdbcDataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tcc-branch-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
