package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepositoryWriteSessionTest {
    public record Stored(String id, long version, long value) { }
    private final JdbcDataSource dataSource = new JdbcDataSource();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonObjectSnapshotRepository repository;
    public RepositoryWriteSessionTest() {
        dataSource.setURL("jdbc:h2:mem:write_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        repository = new JsonObjectSnapshotRepository(new JdbcObjectSnapshotStore(dataSource, value -> {
            try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); }
        }), mapper);
    }
    public record Store(String name) { }
    public record Goods(String sku) { }
    public record Inventory(int available) { }

    @Test public void compositePerformerKeysWriteOnlyRoleAndRejectMissingOrAmbiguousIdentity() {
        var role = new Inventory(12);
        var store = new Store("central");
        var goods = new Goods("PET-1");
        var rule = new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT, 0, "Inventory",
                List.of("@performer:Store.name", "@performer:Goods.sku"), null);
        assertEquals("PERSISTENCE_PERFORMER_IDENTITY_REQUIRED", assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(rule, role))).getMessage());
        com.servpp.service.runtime.RoleIdentityContext.attachPerformers(role, "Inventory", store, goods);
        transaction(() -> write(rule, role));
        String identity = CompositeObjectId.encode(List.of("central", "PET-1"));
        assertEquals(12, repository.require("Inventory", identity, Inventory.class).available());
        assertTrue(repository.findAll("Store", Store.class).isEmpty());
        assertTrue(repository.findAll("Goods", Goods.class).isEmpty());
        com.servpp.service.runtime.RoleIdentityContext.attachPerformers(role, "Inventory", store, new Store("other"), goods);
        assertEquals("PERSISTENCE_PERFORMER_IDENTITY_AMBIGUOUS", assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(rule, role))).getMessage());
        assertEquals(1, repository.findAll("Inventory", Inventory.class).size());
        com.servpp.service.runtime.RoleIdentityContext.clear(role);
    }

    public static class GuardedInventory {
        private long version;
        private int available;
        public GuardedInventory() { }
        public GuardedInventory(long version, int available) { this.version = version; this.available = available; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
        public int getAvailable() { return available; }
        public void setAvailable(int available) { this.available = available; }
    }

    @Test public void performerVersionGuardUsesReadIdentityInsteadOfCurrentActorIdentity() {
        var role = new GuardedInventory(0, 12);
        var rule = new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT, 0, "GuardedInventory",
                List.of("@performer:Store.name", "@performer:Goods.sku"), null);
        var update = new RepositoryWriteRule(RepositoryWriteRule.Operation.UPDATE, 0, "GuardedInventory",
                rule.keyPaths(), new RepositoryWriteRule.VersionCheck(0, "version", "version"));
        var goods = new Goods("PET-1");
        com.servpp.service.runtime.RoleIdentityContext.attachPerformers(role, "GuardedInventory", new Store("central"), goods);
        transaction(() -> write(rule, role));
        String identity = CompositeObjectId.encode(List.of("central", "PET-1"));
        RepositoryParticipantSnapshots.loaded(role, repository, repository.find("GuardedInventory", identity).orElseThrow());
        role.setVersion(1);
        transaction(() -> write(update, role));
        com.servpp.service.runtime.RoleIdentityContext.attachPerformers(role, "GuardedInventory", new Store("other"), goods);
        assertEquals("PERSISTENCE_IDENTITY_CHANGED", assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(update, role))).getMessage());
        assertEquals(12, repository.require("GuardedInventory", identity, GuardedInventory.class).getAvailable());
        assertEquals(1, repository.findAll("GuardedInventory", GuardedInventory.class).size());
        com.servpp.service.runtime.RoleIdentityContext.clear(role);
    }

    @Test public void compositePerformerIdentityPersistsNativeRoleColumnsAndRollsBackTogether() throws Exception {
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("create table inventory(store_name varchar(80), goods_sku varchar(80), available integer, primary key(store_name, goods_sku))");
            sql.execute("create table actor_audit(name varchar(80) primary key)");
            sql.execute("insert into actor_audit values('unchanged')");
        }
        var properties = new java.util.Properties();
        properties.setProperty("tables", "inventory");
        properties.setProperty("table.inventory.category", "roles");
        properties.setProperty("table.inventory.businessType", "Inventory");
        properties.setProperty("table.inventory.primaryKeys", "store_name,goods_sku");
        properties.setProperty("table.inventory.columns", "store_name,goods_sku,available");
        for (String column : List.of("store_name", "goods_sku")) {
            properties.setProperty("table.inventory.column." + column + ".type", "VARCHAR");
            properties.setProperty("table.inventory.column." + column + ".objectBacked", "false");
        }
        properties.setProperty("table.inventory.column.available.type", "INTEGER");
        var nativeRepository = new JdbcObjectRepository(new JdbcObjectSnapshotStore(dataSource, value -> {
            try { return mapper.writeValueAsString(value); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }), mapper)
                .useAppDdlCatalog(com.servpp.service.runtime.AppDdlCatalog.fromProperties(properties));
        var role = new Inventory(12);
        com.servpp.service.runtime.RoleIdentityContext.attachPerformers(role, "Inventory", new Store("central"), new Goods("PET-1"));
        var insert = new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT, 0, "Inventory",
                List.of("@performer:Store.name", "@performer:Goods.sku"), null);
        transaction(() -> RepositoryWriteSession.prepare(() -> nativeRepository, List.of(insert), new Object[]{role}).persist());
        String id = CompositeObjectId.encode(List.of("central", "PET-1"));
        assertEquals(12, nativeRepository.require("Inventory", id, Inventory.class).available());
        var delete = new RepositoryWriteRule(RepositoryWriteRule.Operation.DELETE, 0, "Inventory", insert.keyPaths(), null);
        assertEquals("later failure", assertThrows(IllegalStateException.class, () -> transaction(() -> {
            RepositoryWriteSession.prepare(() -> nativeRepository, List.of(delete), new Object[]{role}).persist();
            throw new IllegalStateException("later failure");
        })).getMessage());
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            try (var row = sql.executeQuery("select store_name, goods_sku, available from inventory")) {
                assertTrue(row.next()); assertEquals("central", row.getString(1));
                assertEquals("PET-1", row.getString(2)); assertEquals(12, row.getInt(3)); assertFalse(row.next());
            }
            try (var row = sql.executeQuery("select name from actor_audit")) {
                assertTrue(row.next()); assertEquals("unchanged", row.getString(1)); assertFalse(row.next());
            }
        }
        assertTrue(nativeRepository.find("Inventory", id).isPresent());
        transaction(() -> RepositoryWriteSession.prepare(() -> nativeRepository, List.of(delete), new Object[]{role}).persist());
        assertTrue(nativeRepository.find("Inventory", id).isEmpty());
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement();
             var row = sql.executeQuery("select count(*) from inventory")) { assertTrue(row.next()); assertEquals(0, row.getInt(1)); }
        com.servpp.service.runtime.RoleIdentityContext.clear(role);
    }

    private RepositoryWriteRule insert() {
        return new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT,0,"Stored",List.of("id"),null);
    }
    private RepositoryWriteRule update() {
        return new RepositoryWriteRule(RepositoryWriteRule.Operation.UPDATE,1,"Stored",List.of("id"),
                new RepositoryWriteRule.VersionCheck(0,"version","version"));
    }
    private void transaction(Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> {
            try { JdbcTransactionScope.execute(dataSource, connection -> { action.run(); return null; }); }
            catch(java.sql.SQLException e) { throw new IllegalStateException(e); }
        });
    }
    private void write(RepositoryWriteRule rule, Object... values) {
        RepositoryWriteSession.prepare(() -> repository,List.of(rule),values).persist();
    }
    private RepositoryWriteRule delete() {
        return new RepositoryWriteRule(RepositoryWriteRule.Operation.DELETE,0,"Stored",List.of("id"),null);
    }
    @Test public void deleteRequiresBoundaryRemovesOnlyExistingAndPreservesOtherRows() {
        transaction(() -> write(insert(), (Object)new Stored[]{new Stored("a",0,10),new Stored("b",0,20)}));
        assertEquals("PERSISTENCE_REQUIRES_DECLARED_TRANSACTION", assertThrows(IllegalStateException.class,
                () -> write(delete(), new Stored("a",0,999))).getMessage());
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> assertEquals("PERSISTENCE_REPOSITORY_NOT_ENLISTED",
                assertThrows(IllegalStateException.class, () -> write(delete(), new Stored("a",0,0))).getMessage()));
        transaction(() -> write(delete(), new Stored("a",0,999)));
        assertTrue(repository.find("Stored","a").isEmpty());
        assertEquals(20, repository.require("Stored","b",Stored.class).value());
        assertEquals("PERSISTENCE_NOT_FOUND", assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(delete(), new Stored("missing",0,0)))).getMessage());
    }
    @Test public void aLaterMissingCollectionDeleteRestoresEarlierDeletesAndWrites() {
        transaction(() -> write(insert(), (Object)new Stored[]{new Stored("a",0,10),new Stored("b",0,20)}));
        assertEquals("PERSISTENCE_NOT_FOUND", assertThrows(IllegalStateException.class, () -> transaction(() -> {
            write(insert(), new Stored("new",0,99));
            write(delete(), (Object)new Stored[]{new Stored("a",0,0),new Stored("missing",0,0)});
        })).getMessage());
        assertTrue(repository.find("Stored","new").isEmpty());
        assertEquals(10, repository.require("Stored","a",Stored.class).value());
        transaction(() -> write(delete(), (Object)new Stored[]{new Stored("a",0,0),new Stored("b",0,0)}));
        assertTrue(repository.findAll("Stored",Stored.class).isEmpty());
    }
    @Test public void deleteRejectsRuntimeVersionMetadataInsteadOfIgnoringIt() {
        transaction(() -> write(insert(),new Stored("a",0,10)));
        var guarded = new RepositoryWriteRule(RepositoryWriteRule.Operation.DELETE,1,"Stored",List.of("id"),
                new RepositoryWriteRule.VersionCheck(0,"version","version"));
        assertEquals("PERSISTENCE_DELETE_VERSION_UNSUPPORTED", assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(guarded,new Stored("a",0,10),new Stored("a",1,0)))).getMessage());
        assertEquals(10,repository.require("Stored","a",Stored.class).value());
    }
    @Test public void explicitBoundaryAndEnlistedRepositoryAreRequired() {
        assertEquals("PERSISTENCE_REQUIRES_DECLARED_TRANSACTION",assertThrows(IllegalStateException.class,
                () -> write(insert(),new Stored("a",0,10))).getMessage());
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> assertEquals("PERSISTENCE_REPOSITORY_NOT_ENLISTED",
                assertThrows(IllegalStateException.class, () -> write(insert(),new Stored("a",0,10))).getMessage()));
        assertNull(DeclaredTransactionContext.currentMode());
    }
    @Test public void duplicateInsertCannotOverwriteAndCollectionFailureRollsBack() {
        transaction(() -> write(insert(),new Stored("a",0,10)));
        assertEquals("PERSISTENCE_ALREADY_EXISTS",assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(insert(),(Object)new Stored[]{new Stored("b",0,20),new Stored("a",0,99)}))).getMessage());
        assertFalse(repository.find("Stored","b").isPresent());
        assertEquals(10,repository.require("Stored","a",Stored.class).value());
        assertNull(DeclaredTransactionContext.currentMode());
        assertFalse(JdbcTransactionScope.currentConnection(dataSource).isPresent());
    }
    private RepositoryWriteRule insertIfAbsent() {
        return new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT_IF_ABSENT,0,"Stored",List.of("id"),null);
    }
    @Test public void insertIfAbsentPreservesOriginalAndContinuesTheSameTransaction() {
        transaction(() -> write(insert(),new Stored("a",0,10)));
        transaction(() -> {
            write(insert(),new Stored("before",0,1));
            write(insertIfAbsent(),new Stored("a",0,99));
            assertEquals(10,repository.require("Stored","a",Stored.class).value());
            write(insert(),new Stored("after",0,2));
        });
        assertEquals(3,repository.findAll("Stored",Stored.class).size());
        assertThrows(IllegalStateException.class,()->transaction(()->{
            write(insertIfAbsent(),new Stored("new",0,42));throw new IllegalStateException("injected");
        }));
        assertTrue(repository.find("Stored","new").isEmpty());
    }
    @Test public void concurrentInsertIfAbsentCallersReadTheSameWinner() throws Exception {
        var start=new java.util.concurrent.CountDownLatch(1);
        var values=new java.util.concurrent.ConcurrentLinkedQueue<Long>();
        var errors=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        java.util.function.LongFunction<Thread> thread=value->new Thread(()->{
            try {
                start.await();
                transaction(()->{
                    write(insertIfAbsent(),new Stored("same",0,value));
                    values.add(repository.require("Stored","same",Stored.class).value());
                });
            } catch(Throwable failure) { errors.add(failure); }
        });
        Thread first=thread.apply(10),second=thread.apply(20);first.start();second.start();start.countDown();first.join(10000);second.join(10000);
        assertFalse(first.isAlive());assertFalse(second.isAlive());assertTrue(errors.toString(),errors.isEmpty());
        assertEquals(2,values.size());long winner=repository.require("Stored","same",Stored.class).value();
        assertTrue(winner==10 || winner==20);assertTrue(values.stream().allMatch(value->value==winner));
    }
    @Test public void staleVersionsDoNotOverwriteAndVersionsMustAdvance() {
        transaction(() -> write(insert(),new Stored("a",0,10)));
        transaction(() -> write(update(),new Stored("a",0,10),new Stored("a",1,20)));
        assertEquals("PERSISTENCE_VERSION_CONFLICT",assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(update(),new Stored("a",0,10),new Stored("a",2,99)))).getMessage());
        assertEquals("PERSISTENCE_VERSION_MUST_ADVANCE",assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(update(),new Stored("a",1,20),new Stored("a",1,99)))).getMessage());
        assertEquals(new Stored("a",1,20),repository.require("Stored","a",Stored.class));
    }
    @Test public void updateCannotCreateOrSwitchIdentity() {
        assertEquals("PERSISTENCE_NOT_FOUND",assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(update(),new Stored("a",0,10),new Stored("a",1,20)))).getMessage());
        assertEquals("PERSISTENCE_IDENTITY_CHANGED",assertThrows(IllegalStateException.class,
                () -> transaction(() -> write(update(),new Stored("a",0,10),new Stored("b",1,20)))).getMessage());
    }
    @Test public void readsInsideBoundarySeeUncommittedWrites() {
        transaction(() -> {
            write(insert(), new Stored("a", 0, 10));
            assertEquals(10, repository.require("Stored", "a", Stored.class).value());
            assertEquals(1, repository.findAll("Stored", Stored.class).size());
        });
    }
    @Test public void concurrentVersionUpdatesHaveExactlyOneWinner() throws Exception {
        transaction(() -> write(insert(), new Stored("a",0,10)));
        var start = new java.util.concurrent.CountDownLatch(1);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var conflicts = new java.util.concurrent.atomic.AtomicInteger();
        var errors = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        Runnable update = () -> {
            try {
                start.await();
                transaction(() -> write(update(), new Stored("a",0,10), new Stored("a",1,20)));
                successes.incrementAndGet();
            } catch (IllegalStateException e) {
                if ("PERSISTENCE_VERSION_CONFLICT".equals(e.getMessage())) { conflicts.incrementAndGet(); }
                else { errors.add(e); }
            } catch (Exception e) { errors.add(e); }
        };
        Thread first = new Thread(update), second = new Thread(update);
        first.start(); second.start(); start.countDown(); first.join(10000); second.join(10000);
        assertFalse(first.isAlive()); assertFalse(second.isAlive());
        assertTrue(errors.toString(), errors.isEmpty());
        assertEquals(1, successes.get()); assertEquals(1, conflicts.get());
        assertEquals(new Stored("a",1,20),repository.require("Stored","a",Stored.class));
    }
    @Test public void failureAfterWriteRestoresPersistentState() {
        assertThrows(IllegalStateException.class, () -> transaction(() -> {
            write(insert(),new Stored("a",0,10)); throw new IllegalStateException("injected");
        }));
        assertFalse(repository.find("Stored","a").isPresent());
    }
}
