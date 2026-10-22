package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

/** Technical repository checks; no business service implementation or SPP test syntax. */
public class JdbcOrdinaryRevisionTest {
    public static class Facts {
        private String name;
        public Facts() { }
        public Facts(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test public void staleFactsCannotOverwriteAConcurrentCommitWithoutExplicitVersionConfiguration() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ordinary_revision_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var mapper = new ObjectMapper();
        var store = new JdbcObjectSnapshotStore(dataSource, value -> {
            try { return mapper.writeValueAsString(value); }
            catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        });
        var repository = new JdbcObjectRepository(store, mapper);
        RepositoryParticipantSnapshots.execute(() -> {
            write(dataSource, () -> repository.insert("Facts", "one", new Facts("initial")));
            Facts stale = repository.require("Facts", "one", Facts.class);
            Facts winner = repository.require("Facts", "one", Facts.class);
            String previous = store.find("Facts", "one").orElseThrow().revision();
            winner.setName("committed");
            write(dataSource, () -> repository.updateFromRead("Facts", "one", winner));
            assertNotEquals(previous, store.find("Facts", "one").orElseThrow().revision());
            stale.setName("stale");
            var conflict = assertThrows(IllegalStateException.class,
                    () -> write(dataSource, () -> repository.updateFromRead("Facts", "one", stale)));
            assertEquals("PERSISTENCE_VERSION_CONFLICT", conflict.getMessage());
            assertEquals("committed", repository.require("Facts", "one", Facts.class).getName());
        });
    }

    @Test public void anUnqueriedParticipantCannotDisableDefaultOptimisticProtection() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:no_revision_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var repository = new JdbcObjectRepository(new JdbcObjectSnapshotStore(dataSource, ignored -> "{}"), new ObjectMapper());
        RepositoryParticipantSnapshots.execute(() -> {
            var failure = assertThrows(IllegalStateException.class,
                    () -> write(dataSource, () -> repository.updateFromRead("Facts", "one", new Facts("unqueried"))));
            assertEquals("PERSISTENCE_READ_REVISION_REQUIRED", failure.getMessage());
        });
    }

    @Test public void disabledOptimisticConcurrencyAllowsStaleAndUnqueriedUpdates() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:disabled_revision_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var mapper = new ObjectMapper();
        var store = new JdbcObjectSnapshotStore(dataSource, value -> mapper.valueToTree(value).toString());
        var properties = new java.util.Properties();
        properties.setProperty("optimisticConcurrency", "false");
        var catalog = com.servpp.service.runtime.AppDdlCatalog.fromProperties(properties);
        assertTrue(catalog.propertyTracking());
        assertFalse(catalog.optimisticConcurrency());
        var repository = new JdbcObjectRepository(store, mapper).useAppDdlCatalog(catalog);
        RepositoryParticipantSnapshots.execute(() -> {
            write(dataSource, () -> repository.insert("Facts", "one", new Facts("initial")));
            Facts stale = repository.require("Facts", "one", Facts.class);
            Facts winner = repository.require("Facts", "one", Facts.class);
            winner.setName("committed");
            write(dataSource, () -> repository.update("Facts", "one", winner));
            stale.setName("stale allowed");
            write(dataSource, () -> repository.update("Facts", "one", stale));
            assertEquals("stale allowed", repository.require("Facts", "one", Facts.class).getName());
            write(dataSource, () -> repository.update("Facts", "one", new Facts("custom protection")));
            assertEquals("custom protection", repository.require("Facts", "one", Facts.class).getName());
        });
    }

    private static void write(JdbcDataSource dataSource, Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> {
            try { JdbcTransactionScope.execute(dataSource, connection -> { action.run(); return null; }); }
            catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        });
    }
}
