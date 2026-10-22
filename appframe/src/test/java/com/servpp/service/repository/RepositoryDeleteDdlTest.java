package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.AppDdlCatalog;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

public class RepositoryDeleteDdlTest {
    public static class Stored {
        private String id, region;
        private long amount;
        public Stored() { }
        public Stored(String id, String region, long amount) { this.id=id; this.region=region; this.amount=amount; }
        public String getId() { return id; } public void setId(String id) { this.id=id; }
        public String getRegion() { return region; } public void setRegion(String region) { this.region=region; }
        public long getAmount() { return amount; } public void setAmount(long amount) { this.amount=amount; }
        public String id() { return id; } public String region() { return region; } public long amount() { return amount; }
    }
    private static final class Fixture {
        final JdbcDataSource source = new JdbcDataSource();
        final ObjectMapper mapper = new ObjectMapper();
        final JdbcObjectRepository repository;
        Fixture() throws Exception {
            source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
            try(var c=source.getConnection(); var s=c.createStatement()) {
                s.execute("CREATE TABLE stored_record(id VARCHAR(64),region VARCHAR(64),amount BIGINT,PRIMARY KEY(id,region))");
            }
            var store=new JdbcObjectSnapshotStore(source, value -> {
                try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); }
            });
            String properties="tables=STORED_RECORD\ntable.STORED_RECORD.businessType=Stored\n"
                +"table.STORED_RECORD.category=roles\ntable.STORED_RECORD.columns=id,region,amount\n"
                +"table.STORED_RECORD.primaryKeys=id,region\n"
                +"table.STORED_RECORD.column.id.type=VARCHAR\ntable.STORED_RECORD.column.id.objectBacked=true\n"
                +"table.STORED_RECORD.column.region.type=VARCHAR\ntable.STORED_RECORD.column.region.objectBacked=true\n"
                +"table.STORED_RECORD.column.amount.type=BIGINT\ntable.STORED_RECORD.column.amount.objectBacked=true\n";
            repository=new JdbcObjectRepository(store,mapper).useAppDdlCatalog(AppDdlCatalog.fromProperties(
                    new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8))));
        }
        void transaction(Runnable action) {
            DeclaredTransactionContext.execute(TransactionMode.AT,()->{
                try { JdbcTransactionScope.execute(source,c->{action.run();return null;}); }
                catch(SQLException e) { throw new IllegalStateException(e); }
            });
        }
        void seed(Stored value) { transaction(()->repository.insert("Stored",key(value.id(),value.region()),value)); }
        int nativeCount() throws Exception {
            try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM stored_record")) {
                r.next(); return r.getInt(1);
            }
        }
    }
    private static String key(String id,String region) { return CompositeObjectId.encode(List.of(id,region)); }
    @Test public void decimalNativeIdentityRetainsAllDigitsWhenOriginalSnapshotIsDecoded() throws Exception {
        var f=new Fixture();
        try(var c=f.source.getConnection();var statement=c.createStatement()) {
            statement.execute("CREATE TABLE precise_record(id DECIMAL(38,20) PRIMARY KEY,amount BIGINT)");
        }
        String properties="tables=PRECISE_RECORD\ntable.PRECISE_RECORD.businessType=Precise\n"
                +"table.PRECISE_RECORD.category=roles\ntable.PRECISE_RECORD.columns=id,amount\n"
                +"table.PRECISE_RECORD.primaryKeys=id\ntable.PRECISE_RECORD.column.id.type=DECIMAL\n"
                +"table.PRECISE_RECORD.column.id.objectBacked=true\ntable.PRECISE_RECORD.column.amount.type=BIGINT\n"
                +"table.PRECISE_RECORD.column.amount.objectBacked=true\n";
        f.repository.useAppDdlCatalog(AppDdlCatalog.fromProperties(new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8))));
        var id=new java.math.BigDecimal("0.12345678901234567890");
        String key=id.toPlainString();
        f.transaction(()->f.repository.insert("Precise",key,java.util.Map.of("id",id,"amount",10)));
        f.transaction(()->f.repository.delete("Precise",key));
        assertTrue(f.repository.getStore().find("Precise",key).isEmpty());
        try(var c=f.source.getConnection();var statement=c.createStatement();var result=statement.executeQuery("SELECT COUNT(*) FROM precise_record")) {
            result.next();assertEquals(0,result.getInt(1));
        }
    }
    @Test public void compositeDeleteRemovesSnapshotAndNativeMirrorWithoutGhostQueryRows() throws Exception {
        var f=new Fixture();f.seed(new Stored("a","east",10));f.seed(new Stored("a","west",20));
        f.transaction(()->f.repository.delete("Stored",key("a","east")));
        assertTrue(f.repository.find("Stored",key("a","east"),Stored.class).isEmpty());
        assertEquals(20,f.repository.require("Stored",key("a","west"),Stored.class).amount());
        assertEquals(1,f.nativeCount());assertEquals(1,f.repository.findAll("Stored",Stored.class).size());
    }
    @Test public void laterFailureRestoresSnapshotAndNativeMirrorTogether() throws Exception {
        var f=new Fixture();f.seed(new Stored("a","east",10));
        assertThrows(IllegalStateException.class,()->f.transaction(()->{
            f.repository.delete("Stored",key("a","east"));throw new IllegalStateException("LATER_FAILURE");
        }));
        assertEquals(10,f.repository.require("Stored",key("a","east"),Stored.class).amount());assertEquals(1,f.nativeCount());
    }
    @Test public void nativeForeignKeyFailureRestoresAlreadyDeletedSnapshot() throws Exception {
        var f=new Fixture();f.seed(new Stored("a","east",10));
        try(var c=f.source.getConnection();var s=c.createStatement()) {
            s.execute("CREATE TABLE dependant(id VARCHAR(64),region VARCHAR(64),FOREIGN KEY(id,region) REFERENCES stored_record(id,region))");
            s.execute("INSERT INTO dependant VALUES('a','east')");
        }
        assertEquals("PERSISTENCE_DELETE_NATIVE_FAILED",assertThrows(IllegalStateException.class,
                ()->f.transaction(()->f.repository.delete("Stored",key("a","east")))).getMessage());
        assertTrue(f.repository.getStore().find("Stored",key("a","east")).isPresent());assertEquals(1,f.nativeCount());
    }
}
