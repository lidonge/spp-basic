package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.AppDdlCatalog;
import com.servpp.service.runtime.RoleIdentityContext;
import com.servpp.service.transaction.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class IndependentEntityStorageTest {
    public static class Person {
        private String email;
        public String getEmail() { return email; }
        public void setEmail(String value) { email = value; }
    }
    public static class Holder {
        private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
    }
    private Properties properties() {
        var p = new Properties();
        p.setProperty("tables", "PEOPLE,HOLDERS");
        for (String table : List.of("PEOPLE", "HOLDERS")) {
            String prefix = "table." + table + ".";
            boolean entity = table.equals("PEOPLE");
            String fact = entity ? "email" : "active";
            p.setProperty(prefix + "category", entity ? "entities" : "roles");
            p.setProperty(prefix + "businessType", entity ? "Person" : "Holder");
            p.setProperty(prefix + "primaryKeys", "rowId");
            p.setProperty(prefix + "columns", "rowId," + fact);
            p.setProperty(prefix + "column.rowId.type", "VARCHAR");
            p.setProperty(prefix + "column.rowId.objectBacked", "false");
            p.setProperty(prefix + "column." + fact + ".type", entity ? "VARCHAR" : "BOOLEAN");
            p.setProperty(prefix + "roleStorage.enabled", "true");
            p.setProperty(prefix + "roleStorage.independent", "true");
        }
        p.setProperty("table.HOLDERS.roleStorage.performers", "Person");
        p.setProperty("table.HOLDERS.roleStorage.performer.Person.javaType", Person.class.getName());
        p.setProperty("table.HOLDERS.roleStorage.performer.Person.keys", "@storageIdentity");
        p.setProperty("table.PEOPLE.indexNames", "contact");
        p.setProperty("table.PEOPLE.index.contact.paths", "Person.email");
        p.setProperty("table.PEOPLE.index.contact.unique", "true");
        return p;
    }
    private void transaction(JdbcDataSource source, Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> {
            try { JdbcTransactionScope.execute(source, connection -> { action.run(); return null; }); }
            catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        });
    }
    private void write(JdbcObjectRepository repository, RepositoryWriteRule.Operation operation, Object value) {
        String type = value.getClass().getSimpleName();
        String key = operation == RepositoryWriteRule.Operation.INSERT ? "@generatedIdentity" : "@storageIdentity";
        RepositoryWriteSession.prepare(() -> repository,
                List.of(new RepositoryWriteRule(operation, 0, type, List.of(key), null)), new Object[]{value}).persist();
    }
    @Test public void contactChangesAndReuseDoNotRetargetExistingRole() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:entity_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var mapper = new ObjectMapper();
        var store = new JdbcObjectSnapshotStore(source, value -> {
            try { return mapper.writeValueAsString(value); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        var catalog = AppDdlCatalog.fromProperties(properties());
        new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        var repository = new JdbcObjectRepository(store, mapper).useAppDdlCatalog(catalog);
        var original = new Person(); original.setEmail("same@example.test");
        var holder = new Holder(); holder.setActive(true);
        transaction(source, () -> {
            write(repository, RepositoryWriteRule.Operation.INSERT, original);
            RoleIdentityContext.attachPerformers(holder, "Holder", original);
            write(repository, RepositoryWriteRule.Operation.INSERT, holder);
        });
        String id = RepositoryParticipantSnapshots.objectIdentity(original, "Person").orElseThrow();
        String holderId = RepositoryParticipantSnapshots.objectIdentity(holder, "Holder").orElseThrow();
        var duplicate = new Person(); duplicate.setEmail("same@example.test");
        assertThrows(IllegalStateException.class, () -> transaction(source,
                () -> write(repository, RepositoryWriteRule.Operation.INSERT, duplicate)));
        assertEquals(1, repository.findAll("Person", Person.class).size());
        var loaded = repository.require("Person", id, Person.class);
        loaded.setEmail(null);
        transaction(source, () -> write(repository, RepositoryWriteRule.Operation.UPDATE, loaded));
        assertNull(repository.require("Person", id, Person.class).getEmail());
        var replacement = new Person(); replacement.setEmail("same@example.test");
        transaction(source, () -> write(repository, RepositoryWriteRule.Operation.INSERT, replacement));
        String newId = RepositoryParticipantSnapshots.objectIdentity(replacement, "Person").orElseThrow();
        assertNotEquals(id, newId);
        var reread = repository.require("Holder", holderId, Holder.class);
        Object actor = RoleIdentityContext.performers(reread, "Holder").orElseThrow().get(0);
        assertEquals(id, RepositoryParticipantSnapshots.objectIdentity(actor, "Person").orElseThrow());
        assertTrue(reread.isActive());
        assertFalse(mapper.writeValueAsString(loaded).contains("rowId"));
        assertThrows(IllegalStateException.class, () -> transaction(source, () -> {
            var current = repository.require("Person", newId, Person.class);
            current.setEmail("rolled-back@example.test");
            write(repository, RepositoryWriteRule.Operation.UPDATE, current);
            throw new IllegalStateException("injected failure");
        }));
        assertEquals("same@example.test", repository.require("Person", newId, Person.class).getEmail());
        try (var connection = source.getConnection(); var sql = connection.prepareStatement("select email from PEOPLE where rowId=?")) {
            sql.setString(1, id);
            try (var rows = sql.executeQuery()) { assertTrue(rows.next()); assertNull(rows.getString(1)); }
        }
        var removable = repository.require("Person", newId, Person.class);
        transaction(source, () -> write(repository, RepositoryWriteRule.Operation.DELETE, removable));
        assertTrue(repository.find("Person", newId).isEmpty());
        assertTrue(repository.find("Person", id).isPresent());
        assertTrue(repository.find("Holder", holderId).isPresent());
    }
    @Test public void entityIdentityCannotIntroduceRolePerformers() {
        var p = properties();
        p.setProperty("table.PEOPLE.roleStorage.performers", "Person");
        assertThrows(IllegalStateException.class, () -> AppDdlCatalog.fromProperties(p));
        var nonIndependent = properties();
        nonIndependent.setProperty("table.PEOPLE.roleStorage.independent", "false");
        assertThrows(IllegalStateException.class, () -> AppDdlCatalog.fromProperties(nonIndependent));
    }
}
