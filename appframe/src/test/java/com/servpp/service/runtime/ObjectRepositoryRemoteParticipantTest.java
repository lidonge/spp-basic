package com.servpp.service.runtime;

import com.servpp.service.repository.ObjectRepository;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ObjectRepositoryRemoteParticipantTest {
    public static class Customer {
        private String membershipNumber, region, name;
        public String getMembershipNumber() { return membershipNumber; }
        public void setMembershipNumber(String value) { membershipNumber=value; }
        public String getRegion() { return region; }
        public void setRegion(String value) { region=value; }
        public String getName() { return name; }
        public void setName(String value) { name=value; }
    }
    public interface Queries {
        void find(Customer key, Customer result);
        void search(Customer key, List<Customer> result);
    }
    private static Customer customer(String name) {
        Customer value=new Customer(); value.setMembershipNumber("MEMBER"); value.setRegion("NORTH"); value.setName(name); return value;
    }
    private static class Repository implements ObjectRepository {
        List<Customer> rows=List.of(customer("Found"));
        public void save(String type,String id,Object value) { throw new AssertionError("Query must not write"); }
        public Optional<?> find(String type,String id) {
            assertEquals("Customer",type); assertEquals("MEMBER",id); return rows.stream().findFirst();
        }
        public <T> T hydrate(String type,String id,T target) { throw new AssertionError("Use participant hydration"); }
        public <T> List<T> findBy(String type,List<String> paths,List<?> values,Class<T> element) {
            assertEquals("Customer",type); assertEquals(List.of("membershipNumber","region"),paths);
            assertEquals(List.of("MEMBER","NORTH"),values); assertEquals(Customer.class,element);
            return rows.stream().map(element::cast).toList();
        }
    }
    @Test public void scalarResultIsFilledInPlaceAndMissingResultKeepsTheInstance() throws Exception {
        Repository repository=new Repository(); Customer key=customer("Key"), result=customer("Before");
        var adapter=new ObjectRepositoryRemoteServiceAdapter("find",Map.of("objectType","Customer","objectIdPath","membershipNumber"),()->repository);
        var method=Queries.class.getMethod("find",Customer.class,Customer.class);
        ParticipantIdentity.execute(() -> {
            assertNull(adapter.execute("find",method,key,result));
            assertEquals("Found",result.getName()); assertEquals("Key",key.getName());
            assertTrue(ParticipantIdentity.isIdentified(result));
            repository.rows=List.of();
            assertNull(adapter.execute("find",method,key,result));
            assertEquals("Found",result.getName()); assertFalse(ParticipantIdentity.isIdentified(result));
        });
    }
    @Test public void compositeQueryFillsTheSameCollectionAndClearsMissingResults() throws Exception {
        Repository repository=new Repository(); Customer key=customer("Key"), existing=customer("Before");
        List<Customer> result=new ArrayList<>(List.of(existing));
        var adapter=new ObjectRepositoryRemoteServiceAdapter("search",Map.of("objectType","Customer","objectIdPaths","membershipNumber,region"),()->repository);
        var method=Queries.class.getMethod("search",Customer.class,List.class);
        ParticipantIdentity.execute(() -> {
            assertNull(adapter.execute("search",method,key,result));
            assertSame(existing,result.get(0)); assertEquals("Found",existing.getName());
            assertTrue(ParticipantIdentity.isIdentified(result)); assertEquals("Key",key.getName());
            repository.rows=List.of();
            assertNull(adapter.execute("search",method,key,result));
            assertTrue(result.isEmpty()); assertFalse(ParticipantIdentity.isIdentified(result));
        });
    }
    @Test public void missingResultParticipantIsRejected() throws Exception {
        var adapter=new ObjectRepositoryRemoteServiceAdapter("find",Map.of("objectType","Customer","objectIdPath","membershipNumber"),Repository::new);
        var method=Queries.class.getMethod("find",Customer.class,Customer.class);
        assertEquals("PARTICIPANT_INSTANCE_REQUIRED",assertThrows(IllegalStateException.class,
                () -> adapter.execute("find",method,customer("Key"),null)).getMessage());
    }
}
