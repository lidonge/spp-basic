package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;
import com.servpp.service.repository.JdbcObjectSnapshotStoreTest.MutableSnapshotObject;
import com.servpp.service.repository.JdbcObjectSnapshotStoreTest.TestQuery;
import com.servpp.service.repository.JdbcObjectSnapshotStoreTest.TestQueryResponse;

public class LockedRepositoryQueryTest {
    private final JdbcDataSource dataSource = new JdbcDataSource();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcObjectSnapshotStore store;
    public LockedRepositoryQueryTest() {
        dataSource.setURL("jdbc:h2:mem:locked_query_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        store = new JdbcObjectSnapshotStore(dataSource, value -> {
            try { return mapper.writeValueAsString(value); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }
    private void transaction(Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> {
            try { JdbcTransactionScope.execute(dataSource, connection -> { action.run(); return null; }); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
        });
    }
    private MutableSnapshotObject key(String id) {
        var key = new MutableSnapshotObject(); key.id = id; return key;
    }
    @Test public void lockedDirectAndEnvelopeQueriesRequireDeclaredEnlistedBoundary() {
        for (boolean jdbc : List.of(false, true)) {
            ObjectRepository repository = jdbc ? new JdbcObjectRepository(store, mapper) : new JsonObjectSnapshotRepository(store, mapper);
            var rule = new RepositoryHydratingServiceProxy.HydrationRule("querySnapshot", 0, "SnapshotObject", "id", "snapshotObject").asOptional().forUpdate();
            Service query = RepositoryHydratingServiceProxy.decorate(new OptionalRepositoryQueryTest.EnvelopeQuery(), repository, List.of(rule));
            MutableSnapshotObject input = key("a");
            assertEquals("PERSISTENCE_REQUIRES_DECLARED_TRANSACTION", assertThrows(IllegalStateException.class,
                    () -> ((TestQuery) query).querySnapshot(input)).getCause().getMessage());
            DeclaredTransactionContext.execute(TransactionMode.AT, () -> assertEquals("PERSISTENCE_REPOSITORY_NOT_ENLISTED",
                    assertThrows(IllegalStateException.class, () -> ((TestQuery) query).querySnapshot(input)).getCause().getMessage()));
            transaction(() -> {
                assertNull(((TestQuery) query).querySnapshot(input).snapshotObject);
                MutableSnapshotObject stored = key("a"); stored.name = "uncommitted";
                repository.insert("SnapshotObject", "a", stored);
                var direct = ((TestQuery) query).querySnapshot(input).snapshotObject;
                assertEquals("uncommitted", direct.name);
                assertNotSame(input, direct); assertNull(input.name);
                var envelope = (TestQueryResponse) query.execute(new OptionalRepositoryQueryTest.QueryRequest(input));
                assertEquals("uncommitted", envelope.snapshotObject.name);
            });
            transaction(() -> repository.delete("SnapshotObject", "a"));
        }
        assertNull(DeclaredTransactionContext.currentMode());
        assertTrue(JdbcTransactionScope.currentConnection(dataSource).isEmpty());
    }
    @Test public void rowRemainsLockedUntilOuterCommitAndIsReleasedAfterRollback() throws Exception {
        MutableSnapshotObject original = key("a"); original.name = "original";
        store.save("SnapshotObject", "a", original);
        try (var competing = dataSource.getConnection(); var update = competing.createStatement()) {
            competing.setAutoCommit(false);
            update.execute("SET LOCK_TIMEOUT 150");
            var rejected = new IllegalStateException("REJECTED_FACTS");
            assertSame(rejected, assertThrows(IllegalStateException.class, () -> transaction(() -> {
                assertTrue(store.findForUpdate("SnapshotObject", "a").isPresent());
                SQLException blocked = assertThrows(SQLException.class, () -> update.executeUpdate(
                        "UPDATE spp_object_snapshot SET payload_json=payload_json WHERE object_type='SnapshotObject' AND object_id='a'"));
                assertEquals(50200, blocked.getErrorCode());
                assertTrue(store.findForUpdate("SnapshotObject", "a").isPresent());
                throw rejected;
            })));
            assertEquals(1, update.executeUpdate("UPDATE spp_object_snapshot SET payload_json=payload_json WHERE object_type='SnapshotObject' AND object_id='a'"));
            competing.commit();
        }
        transaction(() -> assertTrue(store.findForUpdate("SnapshotObject", "a").isPresent()));
    }
}
