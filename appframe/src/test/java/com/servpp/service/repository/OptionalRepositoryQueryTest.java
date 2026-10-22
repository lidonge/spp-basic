package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;
import com.servpp.service.repository.JdbcObjectSnapshotStoreTest.MutableSnapshotObject;
import com.servpp.service.repository.JdbcObjectSnapshotStoreTest.TestQuery;
import com.servpp.service.repository.JdbcObjectSnapshotStoreTest.TestQueryService;

public class OptionalRepositoryQueryTest {
    public static class QueryRequest {
        private final MutableSnapshotObject key;
        public QueryRequest(MutableSnapshotObject key) { this.key=key; }
        public MutableSnapshotObject getKey() { return key; }
    }
    public static class EnvelopeQuery extends TestQueryService implements com.servpp.service.runtime.ScopeMetadataProvider {
        public boolean isLocalScope() { return true; }
        public String getInputHeaderName() { return null; }
        public String getOutputHeaderName() { return null; }
        public List<String> getServiceParameterNames() { return List.of("key"); }
        public Object execute(Object... args) { return querySnapshot(((QueryRequest)args[args.length-1]).getKey()); }
    }
    @Test public void optionalQueriesReturnAbsenceAndThenHydrateExistingData() {
        for(boolean jdbc:List.of(false,true)) {
            JdbcDataSource dataSource=new JdbcDataSource();dataSource.setURL("jdbc:h2:mem:optional_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
            ObjectMapper mapper=new ObjectMapper();
            JdbcObjectSnapshotStore store=new JdbcObjectSnapshotStore(dataSource,value->{try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}});
            ObjectRepository repository=jdbc?new JdbcObjectRepository(store,mapper):new JsonObjectSnapshotRepository(store,mapper);
            var rule=new RepositoryHydratingServiceProxy.HydrationRule("querySnapshot",0,"SnapshotObject","id","snapshotObject");
            Service optional=RepositoryHydratingServiceProxy.decorate(new EnvelopeQuery(),repository,List.of(rule.asOptional()));
            MutableSnapshotObject key=new MutableSnapshotObject();key.id="a";
            assertNull(((TestQuery)optional).querySnapshot(key).snapshotObject);
            assertNull(((JdbcObjectSnapshotStoreTest.TestQueryResponse)optional.execute(new QueryRequest(key))).snapshotObject);
            assertEquals("a",key.id);assertNull(key.name);
            Service required=RepositoryHydratingServiceProxy.decorate(new TestQueryService(),repository,List.of(rule));
            assertThrows(IllegalStateException.class,()->((TestQuery)required).querySnapshot(key));
            MutableSnapshotObject stored=new MutableSnapshotObject();stored.id="a";stored.name="found";repository.save("SnapshotObject","a",stored);
            assertSame(key,((TestQuery)optional).querySnapshot(key).snapshotObject);assertEquals("found",key.name);
            assertSame(key,((JdbcObjectSnapshotStoreTest.TestQueryResponse)optional.execute(new QueryRequest(key))).snapshotObject);
        }
    }
    @Test public void optionalScalarQuerySelectsCompositeIdentityAndRejectsAmbiguousResults() {
        for (boolean jdbc : List.of(false, true)) {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:optional_composite_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
            ObjectMapper mapper = new ObjectMapper();
            JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(dataSource, value -> {
                try { return mapper.writeValueAsString(value); }
                catch (Exception e) { throw new IllegalStateException(e); }
            });
            ObjectRepository repository = jdbc ? new JdbcObjectRepository(store, mapper) : new JsonObjectSnapshotRepository(store, mapper);
            var rule = new RepositoryHydratingServiceProxy.HydrationRule("querySnapshot", 0, "SnapshotObject", "id", "snapshotObject").asOptional();
            Service service = RepositoryHydratingServiceProxy.decorate(new EnvelopeQuery(), repository, List.of(rule));
            MutableSnapshotObject key = new MutableSnapshotObject(); key.id = "a";
            MutableSnapshotObject first = new MutableSnapshotObject(); first.id = "a"; first.name = "first";
            repository.save("SnapshotObject", CompositeObjectId.encode(List.of("a", "first")), first);
            assertEquals("first", ((TestQuery) service).querySnapshot(key).snapshotObject.name);
            assertEquals("first", ((JdbcObjectSnapshotStoreTest.TestQueryResponse) service.execute(new QueryRequest(key))).snapshotObject.name);
            assertNull(key.name);
            MutableSnapshotObject second = new MutableSnapshotObject(); second.id = "a"; second.name = "second";
            repository.save("SnapshotObject", CompositeObjectId.encode(List.of("a", "second")), second);
            IllegalStateException ambiguous = assertThrows(IllegalStateException.class, () -> ((TestQuery) service).querySnapshot(key));
            assertEquals("REPOSITORY_SCALAR_QUERY_AMBIGUOUS", ambiguous.getCause().getMessage());
            key.id = "absent";
            assertNull(((TestQuery) service).querySnapshot(key).snapshotObject);
        }
    }
    @Test public void storageFailuresAreNotConvertedToMissingData() {
        RuntimeException outage=new IllegalStateException("offline");
        ObjectRepository repository=new ObjectRepository() {
            public void save(String type,String id,Object value){throw outage;}
            public Optional<?> find(String type,String id){throw outage;}
            public <T>T hydrate(String type,String id,T value){throw outage;}
            public <T>boolean hydrateIfPresent(String type,String id,T value){throw outage;}
        };
        Service optional=RepositoryHydratingServiceProxy.decorate(new TestQueryService(),repository,List.of(new RepositoryHydratingServiceProxy.HydrationRule("querySnapshot",0,"SnapshotObject","id","snapshotObject").asOptional()));
        MutableSnapshotObject key=new MutableSnapshotObject();key.id="a";
        assertSame(outage,assertThrows(IllegalStateException.class,()->((TestQuery)optional).querySnapshot(key)).getCause());
    }
}
