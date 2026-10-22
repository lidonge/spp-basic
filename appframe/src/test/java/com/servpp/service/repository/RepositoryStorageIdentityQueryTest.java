package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.ParticipantIdentity;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

/** Opaque storage keys belong to framework metadata rather than business participants. */
public class RepositoryStorageIdentityQueryTest {
    public static class Opaque {
        public String name;
        public int amount;
    }
    public interface Query extends Service { void find(Opaque participant); }
    public static class QueryService implements Query {
        public void find(Opaque participant) { }
        public Object execute(Object... arguments) { find((Opaque) arguments[0]); return null; }
        public String getName() { return "findOpaque"; }
    }

    @Test public void defaultQueryAndUpdateUseReadIdentityWithoutGuessingFromBusinessFacts() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:opaque_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var mapper = new ObjectMapper();
        var repository = new JdbcObjectRepository(new JdbcObjectSnapshotStore(source, value -> {
            try { return mapper.writeValueAsString(value); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }), mapper);
        RepositoryParticipantSnapshots.execute(() -> {
            var first = new Opaque(); first.name = "same"; first.amount = 10;
            var second = new Opaque(); second.name = "same"; second.amount = 20;
            repository.save("Opaque", "technical-a", first);
            repository.save("Opaque", "technical-b", second);
            var rule = new RepositoryHydratingServiceProxy.HydrationRule("find", 0, "Opaque",
                    "@storageIdentity", "@participant:0", null, true, "APP_DDL_STORAGE_IDENTITY").asOptional();
            var query = (Query) RepositoryHydratingServiceProxy.decorate(new QueryService(), repository, List.of(rule));
            var unknown = new Opaque(); unknown.name = "same"; unknown.amount = 99;
            query.find(unknown);
            assertFalse(ParticipantIdentity.isIdentified(unknown));
            assertEquals(99, unknown.amount);
            var update = new RepositoryWriteRule(RepositoryWriteRule.Operation.UPDATE, 0, "Opaque",
                    List.of("@storageIdentity"), null);
            assertEquals("PERSISTENCE_READ_IDENTITY_REQUIRED", assertThrows(IllegalStateException.class,
                    () -> transaction(source, () -> RepositoryWriteSession.prepare(() -> repository,
                            List.of(update), new Object[]{unknown}).persist())).getMessage());
            var known = repository.require("Opaque", "technical-a", Opaque.class);
            known.amount = 99;
            query.find(known);
            assertTrue(ParticipantIdentity.isIdentified(known));
            assertEquals(10, known.amount);
            known.amount = 15;
            transaction(source, () -> RepositoryWriteSession.prepare(() -> repository,
                    List.of(update), new Object[]{known}).persist());
            assertEquals(15, repository.require("Opaque", "technical-a", Opaque.class).amount);
            assertEquals(20, repository.require("Opaque", "technical-b", Opaque.class).amount);
            transaction(source, () -> repository.delete("Opaque", "technical-a"));
            query.find(known);
            assertFalse(ParticipantIdentity.isIdentified(known));
            assertEquals(15, known.amount);
        });
    }

    private void transaction(JdbcDataSource source, Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> {
            try { JdbcTransactionScope.execute(source, connection -> { action.run(); return null; }); }
            catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        });
    }
}
