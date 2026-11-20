package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.runtime.AppDdlCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** PERF-QUERY-1: verify actual result rows and SQL projection on H2. */
public class RepositoryQueryScopeTest {
    public static class Fact { public String id; public String group; }
    public static class NativeFact { public String id; public int sequence; public String groupKey; public String payload; }

    private JdbcDataSource source() {
        var source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:scope_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;QUERY_STATISTICS=TRUE");
        return source;
    }
    private List<String> selects(JdbcDataSource source,String table) throws Exception {
        var result=new ArrayList<String>();
        try(var c=source.getConnection();var s=c.createStatement();var rows=s.executeQuery("select SQL_STATEMENT from information_schema.query_statistics")) {
            while(rows.next()) {
                String sql=rows.getString(1).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
                if(sql.startsWith("select ") && sql.contains(" from "+table)) result.add(sql);
            }
        }
        return result;
    }
    @Test public void collectionFallbacksReadOnlyRequestedCurrentType() throws Exception {
        var source=source();var mapper=new ObjectMapper();
        var store=new JdbcObjectSnapshotStore(source,value->{try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}});
        store.save("Fact","b",Map.of("id","b","group","wanted"));
        store.save("Fact","a",Map.of("id","a","group","wanted"));
        store.save("Unrelated","noise",Map.of("padding","x".repeat(4096)));
        for(ObjectRepository repository:List.of(new JdbcObjectRepository(store,mapper),new JsonObjectSnapshotRepository(store,mapper))) {
            assertEquals(List.of("a","b"),repository.findAll("Fact",Fact.class).stream().map(f->f.id).toList());
            assertEquals(2,repository.findBy("Fact","group","wanted",Fact.class).size());
            assertTrue(repository.findBy("Fact","group","absent",Fact.class).isEmpty());
            assertTrue(repository.findAll("Missing",Fact.class).isEmpty());
        }
        var jdbc=new JdbcObjectRepository(store,mapper);
        assertEquals("a",jdbc.findBy("Fact",List.of("group","id"),List.of("wanted","a"),Fact.class).get(0).id);
        var queries=selects(source,"spp_object_snapshot");assertFalse(queries.isEmpty());
        for(String sql:queries) assertTrue(sql,sql.contains("where object_type = ?"));

    }
    @Test public void historyListingStillKeepsVersionsWhileRepositoriesReadCurrentFacts() {
        var source=source();var mapper=new ObjectMapper();
        var store=new JdbcObjectSnapshotStore(source,value->{try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}});
        store.save("TransactionSnapshot","trace",Map.of("id","first","group","history"));
        store.save("TransactionSnapshot","trace",Map.of("id","latest","group","history"));
        assertEquals(2,store.findByType("TransactionSnapshot").size());
        assertEquals(1,store.findCurrentByType("TransactionSnapshot").size());
        for(ObjectRepository repository:List.of(new JdbcObjectRepository(store,mapper),new JsonObjectSnapshotRepository(store,mapper)))
            assertEquals(List.of("latest"),repository.findAll("TransactionSnapshot",Fact.class).stream().map(f->f.id).toList());
    }
    @Test public void nativeIdentityReadsSelectOnlyCompositeKeysAndFullHydrationStillWorks() throws Exception {
        var source=source();
        var columns=List.of(new AppDdlCatalog.Column("id","VARCHAR",40,0,false,true),
                new AppDdlCatalog.Column("sequence","INTEGER",0,0,false,true),
                new AppDdlCatalog.Column("group_key","VARCHAR",40,0,false,true),
                new AppDdlCatalog.Column("payload","CLOB",0,0,false,true));
        var table=new AppDdlCatalog.Table("evidence","roles","NativeFact",List.of("id","sequence"),List.of(),List.of(),
                List.of(),Map.of(),columns,List.of("id","sequence"),List.of(),List.of(),List.of(),List.of());
        var reader=new JdbcAppDdlObjectTableReader(source,new AppDdlCatalog(List.of(table)));
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("create table evidence(id varchar(40),sequence integer,group_key varchar(40),payload clob,primary key(id,sequence))");
            s.execute("insert into evidence values ('a',2,'wanted','large payload'),('a',1,'wanted','large payload'),('b',1,'other','other payload')");
        }
        var first=CompositeObjectId.encode(List.of("a",1));var second=CompositeObjectId.encode(List.of("a",2));
        assertEquals(List.of(first,second,CompositeObjectId.encode(List.of("b",1))),reader.findAllObjectIds("NativeFact"));
        assertEquals(List.of(first,second),reader.findObjectIdsBy("NativeFact","groupKey","wanted"));
        assertEquals(List.of(second),reader.findObjectIdsBy("NativeFact",List.of("groupKey","sequence"),List.of("wanted",2)));
        assertTrue(reader.findObjectIdsBy("NativeFact","groupKey","absent").isEmpty());
        var queries=selects(source,"evidence");assertEquals(3,queries.size());
        for(String sql:queries) assertEquals(sql,"select id, sequence",sql.substring(0,sql.indexOf(" from ")));
        var fact=reader.find("NativeFact",second,NativeFact.class).orElseThrow();
        assertEquals("wanted",fact.groupKey);assertEquals(2,fact.sequence);
    }
}
