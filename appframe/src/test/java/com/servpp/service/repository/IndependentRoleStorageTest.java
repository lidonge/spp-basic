package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.*;
import com.servpp.service.transaction.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class IndependentRoleStorageTest {
    public static class Buyer {
        private String number;
        public Buyer() { }
        public Buyer(String number) { this.number = number; }
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
    }
    public static class Extra {
        private String code;
        public Extra() { }
        public Extra(String code) { this.code = code; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }
    public static class Line {
        private Buyer buyer;
        private Buyer secondBuyer;
        public Buyer getSecondBuyer() { return secondBuyer; }
        public void setSecondBuyer(Buyer value) { secondBuyer = value; }
        private int quantity;
        public Buyer getBuyer() { return buyer; }
        public void setBuyer(Buyer buyer) { this.buyer = buyer; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
    public static class Cart {
        private List<Line> lines = new ArrayList<>();
        public List<Line> getLines() { return lines; }
        public void setLines(List<Line> lines) { this.lines = lines; }
    }
    private final JdbcDataSource source = new JdbcDataSource();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcObjectSnapshotStore store;
    public IndependentRoleStorageTest() {
        source.setURL("jdbc:h2:mem:independent_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        store = new JdbcObjectSnapshotStore(source,value -> {
            try { return mapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException(error); }
        });
    }
    private AppDdlCatalog catalog(boolean separate,boolean extra) {
        var p = new Properties();
        p.setProperty("tables","LINES"); p.setProperty("table.LINES.category","roles");
        p.setProperty("table.LINES.businessType","Line"); p.setProperty("table.LINES.primaryKeys","lineId");
        p.setProperty("table.LINES.columns","lineId,quantity");
        p.setProperty("table.LINES.column.lineId.type","VARCHAR"); p.setProperty("table.LINES.column.lineId.objectBacked","false");
        p.setProperty("table.LINES.column.quantity.type","INTEGER");
        String prefix = "table.LINES.roleStorage.";
        p.setProperty(prefix + "enabled","true"); p.setProperty(prefix + "independent","true");
        p.setProperty(prefix + "performerTable",separate ? "LINE_PLAYERS" : "");
        p.setProperty(prefix + "performers",extra ? "Buyer,Extra" : "Buyer");
        p.setProperty(prefix + "performer.Buyer.javaType",Buyer.class.getName());
        p.setProperty(prefix + "performer.Buyer.member","buyer"); p.setProperty(prefix + "performer.Buyer.keys","number");
        p.setProperty(prefix + "performer.Extra.javaType",Extra.class.getName());
        p.setProperty(prefix + "performer.Extra.keys","code");
        return AppDdlCatalog.fromProperties(p);
    }
    private void transaction(Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.AT,() -> {
            try { JdbcTransactionScope.execute(source,connection -> { action.run(); return null; }); }
            catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
        });
    }
    private Line line(String number) { Line line = new Line(); line.setBuyer(new Buyer(number)); line.setQuantity(2); return line; }
    private void write(JdbcObjectRepository repository,RepositoryWriteRule.Operation operation,String key,Object value) {
        transaction(() -> RepositoryWriteSession.prepare(() -> repository,
                List.of(new RepositoryWriteRule(operation,0,"Line",List.of(key),null)),new Object[]{value}).persist());
    }
    private void verifyLayout(boolean separate) throws Exception {
        var catalog = catalog(separate,true); new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        var repository = new JdbcObjectRepository(store,mapper).useAppDdlCatalog(catalog);
        Line first = line("B1"), second = line("B1");
        write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",first);
        write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",second);
        String identity = RepositoryParticipantSnapshots.objectIdentity(first,"Line").orElseThrow();
        assertNotEquals(identity,RepositoryParticipantSnapshots.objectIdentity(second,"Line").orElseThrow());
        Line loaded = repository.require("Line",identity,Line.class);
        assertEquals("B1",loaded.getBuyer().getNumber());
        // Extend an existing identity with another performer; its declared structure stays unchanged.
        RoleIdentityContext.attachPerformers(loaded,"Line",new Extra("E1")); loaded.setQuantity(3);
        write(repository,RepositoryWriteRule.Operation.UPDATE,"@storageIdentity",loaded);
        Line reread = repository.require("Line",identity,Line.class);
        assertEquals(3,reread.getQuantity());
        assertEquals(identity,RepositoryParticipantSnapshots.objectIdentity(reread,"Line").orElseThrow());
        assertTrue(RoleIdentityContext.performers(reread,"Line").orElseThrow().stream().anyMatch(actor -> actor instanceof Extra e && "E1".equals(e.getCode())));
        assertFalse(mapper.writeValueAsString(reread).contains("lineId"));
        assertEquals(2,repository.findAll("Line",Line.class).size());
        try (var connection = source.getConnection(); var sql = connection.createStatement();
             var rows = sql.executeQuery(separate ? "select count(*) from LINE_PLAYERS where role_id='" + identity + "'" : "select count(spp_performer_Extra) from LINES")) {
            rows.next(); assertEquals(separate ? 2 : 1,rows.getInt(1));
        }
        write(repository,RepositoryWriteRule.Operation.DELETE,"@storageIdentity",reread);
        assertTrue(repository.find("Line",identity).isEmpty());
        if (separate) try (var connection = source.getConnection();var sql = connection.createStatement();var rows = sql.executeQuery("select count(*) from LINE_PLAYERS where role_id='" + identity + "'")) {
            rows.next(); assertEquals(0,rows.getInt(1));
        }
    }
    @Test public void inlineIdentityLinksKeepStableIndependentRoleIdentity() throws Exception { verifyLayout(false); }
    @Test public void separateIdentityLinksKeepStableIndependentRoleIdentity() throws Exception { verifyLayout(true); }
    @Test public void sameTypePlayersUseDistinctNamedStorageSlots() throws Exception {
        var initial = catalog(true,false);
        var role = initial.roleStorage().get("Line");
        var slots = List.of(new AppDdlCatalog.Performer("Buyer","Buyer_first",Buyer.class.getName(),"buyer",List.of("number")),
                new AppDdlCatalog.Performer("Buyer","Buyer_second",Buyer.class.getName(),"secondBuyer",List.of("number")));
        var catalog = new AppDdlCatalog(initial.tables(),Map.of("Line",new AppDdlCatalog.RoleStorage(role.table(),role.type(),role.primaryKey(),true,role.performerTable(),slots)));
        new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        var repository = new JdbcObjectRepository(store,mapper).useAppDdlCatalog(catalog);
        Line line = line("B1"); line.setSecondBuyer(new Buyer("B2"));
        write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",line);
        var loaded = repository.require("Line",RepositoryParticipantSnapshots.objectIdentity(line,"Line").orElseThrow(),Line.class);
        assertEquals("B1",loaded.getBuyer().getNumber()); assertEquals("B2",loaded.getSecondBuyer().getNumber());
    }
    @Test public void roleAndRelationshipsRollbackTogether() throws Exception {
        var catalog = catalog(true,false); new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        var repository = new JdbcObjectRepository(store,mapper).useAppDdlCatalog(catalog);
        Line line = line("B1");
        assertThrows(IllegalStateException.class,() -> transaction(() -> {
            RepositoryWriteSession.prepare(() -> repository,List.of(new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT,0,"Line",List.of("@generatedIdentity"),null)),new Object[]{line}).persist();
            throw new IllegalStateException("injected failure");
        }));
        assertTrue(repository.findAll("Line",Line.class).isEmpty());
        try (var connection = source.getConnection(); var sql = connection.createStatement(); var rows = sql.executeQuery("select count(*) from LINE_PLAYERS")) {
            rows.next(); assertEquals(0,rows.getInt(1));
        }
    }
    private AppDdlCatalog withVariants(boolean separate,List<List<String>> variants) {
        var initial = catalog(separate,true); var r = initial.roleStorage().get("Line");
        return new AppDdlCatalog(initial.tables(),Map.of("Line",new AppDdlCatalog.RoleStorage(
                r.table(),r.type(),r.primaryKey(),r.independent(),r.performerTable(),r.performers(),variants)));
    }
    @Test public void alternativesStoreOnlyChosenPlayerAndRemovePreviousBranchInBothLayouts() throws Exception {
        for (boolean separate : new boolean[]{false,true}) {
            var catalog = withVariants(separate,List.of(List.of("Buyer"),List.of("Extra")));
            new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
            var repository = new JdbcObjectRepository(store,mapper).useAppDdlCatalog(catalog);
            Line line = line("B1"); write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",line);
            String identity = RepositoryParticipantSnapshots.objectIdentity(line,"Line").orElseThrow();
            var loaded = repository.require("Line",identity,Line.class);
            assertEquals(1,RoleIdentityContext.performers(loaded,"Line").orElseThrow().size());
            loaded.setBuyer(null); RoleIdentityContext.attachPerformers(loaded,"Line",new Extra("E1"));
            write(repository,RepositoryWriteRule.Operation.UPDATE,"@storageIdentity",loaded);
            var changed = repository.require("Line",identity,Line.class);
            assertNull(changed.getBuyer());
            var actors = RoleIdentityContext.performers(changed,"Line").orElseThrow();
            assertEquals(1,actors.size()); assertTrue(actors.get(0) instanceof Extra);
            var copied = ParticipantCopies.copy(changed);
            assertEquals(1,RoleIdentityContext.performers(copied,"Line").orElseThrow().size());
            assertNotSame(actors.get(0),RoleIdentityContext.performers(copied,"Line").orElseThrow().get(0));
            copied.setQuantity(4); write(repository,RepositoryWriteRule.Operation.UPDATE,"@storageIdentity",copied);
            assertEquals(4,repository.require("Line",identity,Line.class).getQuantity());
            assertEquals(identity,RepositoryParticipantSnapshots.objectIdentity(changed,"Line").orElseThrow());
            Line invalid = line("B2"); RoleIdentityContext.attachPerformers(invalid,"Line",new Extra("E2"));
            assertThrows(IllegalStateException.class,() -> write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",invalid));
        }
    }
    @Test public void jointPlayersMustAllHaveIdentityBeforeRoleIsStored() throws Exception {
        var catalog = withVariants(true,List.of(List.of("Buyer","Extra")));
        new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        var repository = new JdbcObjectRepository(store,mapper).useAppDdlCatalog(catalog);
        Line incomplete = line("B1");
        assertThrows(IllegalStateException.class,() -> write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",incomplete));
        assertTrue(repository.findAll("Line",Line.class).isEmpty());
        Line complete = line("B2"); RoleIdentityContext.attachPerformers(complete,"Line",new Extra("E2"));
        write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",complete);
        var loaded = repository.require("Line",RepositoryParticipantSnapshots.objectIdentity(complete,"Line").orElseThrow(),Line.class);
        assertEquals(2,RoleIdentityContext.performers(loaded,"Line").orElseThrow().size());
    }
    @Test public void nestedSnapshotAndCopiesKeepTechnicalIdentityOutsideBusinessFields() throws Exception {
        var catalog = catalog(true,false); new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        var repository = new JdbcObjectRepository(store,mapper).useAppDdlCatalog(catalog);
        Line line = line("B1"); write(repository,RepositoryWriteRule.Operation.INSERT,"@generatedIdentity",line);
        Cart cart = new Cart(); cart.getLines().add(line);
        transaction(() -> repository.insert("Cart","C1",cart));
        var loaded = repository.require("Cart","C1",Cart.class);
        var copy = ParticipantCopies.copy(loaded.getLines().get(0));
        assertEquals(RepositoryParticipantSnapshots.objectIdentity(line,"Line"),RepositoryParticipantSnapshots.objectIdentity(copy,"Line"));
        write(repository,RepositoryWriteRule.Operation.DELETE,"@storageIdentity",copy);
        assertTrue(repository.find("Line",RepositoryParticipantSnapshots.objectIdentity(line,"Line").orElseThrow()).isEmpty());
    }
}
