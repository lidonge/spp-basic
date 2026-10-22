package com.servpp.service.idempotency;

import com.servpp.service.runtime.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class BasicRequestBoundaryTest {
    private final ScopeMetadataProvider metadata = new ScopeMetadataProvider() {
        public boolean isLocalScope() { return true; }
        public boolean isUpdateEntry() { return true; }
        public String getInputHeaderName() { return null; }
        public String getOutputHeaderName() { return null; }
        public List<String> getDeclaredIdempotencyKeyPaths() { return List.of("id"); }
        public String getDeclaredIdempotencyFactsPath() { return "id"; }
    };
    public static class Input { public String id="request"; }
    @Test public void basicRuntimeContainsNoOfficialRequestAlgorithmAndDoesNotRegisterCalls() {
        assertFalse(BusinessIdempotencyManager.isAvailable());
        for (String type : List.of("DeclaredRequestIdempotency","OfficialRequestCoordinator","OfficialJdbcBusinessIdempotencyStore"))
            assertThrows(ClassNotFoundException.class,() -> Class.forName("com.servpp.service.idempotency."+type));
        assertThrows(ClassNotFoundException.class,() -> Class.forName("com.servpp.service.technical.InvocationTraceService"));
        var calls = new AtomicInteger();
        for (var origin : InvocationContext.Origin.values()) {
            for (int index=0; index<2; index++) {
                int expected = calls.get()+1;
                assertEquals(expected,InvocationContext.remote(origin,"caller","serial",() ->
                        BusinessIdempotencyManager.execute("update",new Input(),metadata,() -> {
                            assertEquals(InvocationContext.Mode.LOCAL,InvocationContext.current().mode()); return calls.incrementAndGet();
                        })));
                assertFalse(BusinessIdempotencyManager.wasReplay());
            }
        }
    }
    @Test public void defaultJdbcHandleCreatesNoRequestTablesAndExplicitCoordinationRequiresProvider() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:basic_request_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var handle = new JdbcBusinessIdempotencyStore(source);
        assertFalse(handle.isAvailable()); assertFalse(handle.contains("key")); assertTrue(handle.findRecord("key").isEmpty());
        try(var connection=source.getConnection();var tables=connection.getMetaData().getTables(null,null,"BUSINESS_IDEMPOTENCY",null)) {
            assertFalse(tables.next());
        }
        assertEquals("REQUEST_COORDINATION_PROVIDER_REQUIRED",assertThrows(IllegalStateException.class,() ->
                handle.executeRequest("key",java.time.Duration.ofSeconds(5),() -> null)).getMessage());
        assertThrows(IllegalStateException.class,InMemoryBusinessIdempotencyStore::new);
    }
}
