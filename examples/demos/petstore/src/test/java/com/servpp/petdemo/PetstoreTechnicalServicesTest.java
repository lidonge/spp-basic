package com.servpp.petdemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.servpp.service.runtime.ExampleServiceFaults;
import com.servpp.service.idempotency.BusinessIdempotencyManager;
import java.sql.DriverManager;
import java.util.*;
import java.util.logging.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Exercises generated services; logs are technical observations, not business implementation. */
public class PetstoreTechnicalServicesTest {
    private final Logger logger = Logger.getLogger("spp.petstore.invocations");
    private final List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        public void publish(LogRecord record) { messages.add(record.getMessage()); }
        public void flush() { } public void close() { }
    };
    private PetstoreTestSupport shop;
    private Level oldLevel;
    private boolean oldParents;
    @Before public void prepare() {
        oldLevel=logger.getLevel(); oldParents=logger.getUseParentHandlers();
        logger.setLevel(Level.ALL); logger.setUseParentHandlers(false); logger.addHandler(handler);
        shop=new PetstoreTestSupport("jdbc:h2:mem:technical_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
    }
    @After public void close() throws Exception {
        try {
            if (shop != null) {
                shop.close();
                try (var connection=DriverManager.getConnection(shop.jdbcUrl); var statement=connection.createStatement()) { statement.execute("SHUTDOWN"); }
            }
        } finally { logger.removeHandler(handler); logger.setLevel(oldLevel); logger.setUseParentHandlers(oldParents); }
    }
    private long count(String phase, String service) {
        return messages.stream().filter(message -> message.contains("phase="+phase+" service=petshop."+service+" ")).count();
    }
    private boolean tracingAvailable() {
        return getClass().getClassLoader().getResource("com/servpp/service/technical/InvocationTraceService.class") != null;
    }
    @Test public void scenarioAndTypedDefaultQueryAreObservedOnce() {
        var session=shop.openStore("TRACE-READ",false); messages.clear();
        shop.call("readShopCustomer",Map.of("Buyer",Map.of("membershipNumber",session.customerId)));
        if (!tracingAvailable()) {
            assertTrue(messages.toString(), messages.isEmpty());
            assertEquals(session.customerId, shop.customer(session).path("membershipNumber").asText());
            return;
        }
        assertEquals(messages.toString(),1,count("BEFORE","readShopCustomer")); assertEquals(1,count("AFTER","readShopCustomer"));
        assertEquals(messages.toString(),1,count("BEFORE","findBuyer")); assertEquals(1,count("AFTER","findBuyer"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("service=petshop.findBuyer kind=ATOMIC_QUERY")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("elapsedMicros=")));
    }
    @Test public void handledReservationFailureAndReplayDoNotRepeatAtomicWork() {
        var session=shop.openStore("TRACE-RESERVE",false); messages.clear();
        Object request=shop.request("reserveShopPet",Map.of("customer",Map.of("membershipNumber",session.customerId),
                "goods",Map.of("sku","FOOD-001"),"agreement",Map.of("quantity",11)));
        Object retry=shop.mapper.convertValue(request,request.getClass());
        JsonNode result=shop.execute("reserveShopPet",request);
        assertEquals("REJECTED",result.path("agreement").path("status").asText());
        if (!tracingAvailable()) {
            assertTrue(messages.toString(), messages.isEmpty());
            JsonNode repeated = shop.execute("reserveShopPet", retry);
            assertEquals(BusinessIdempotencyManager.isAvailable(), BusinessIdempotencyManager.wasReplay());
            assertEquals("REJECTED", repeated.path("agreement").path("status").asText());
            assertTrue(messages.toString(), messages.isEmpty());
            return;
        }
        assertEquals(1,count("BEFORE","reserveShopPet")); assertEquals(1,count("AFTER","reserveShopPet"));
        assertEquals(0,count("FAILURE","reserveShopPet"));
        assertEquals(1,count("BEFORE","reserveRequestedInventory")); assertEquals(1,count("AFTER","reserveRequestedInventory"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("service=petshop.rejectPetReservation kind=ATOMIC_CALCULATE")));
        messages.clear(); shop.execute("reserveShopPet",retry); assertTrue(BusinessIdempotencyManager.wasReplay());
        assertEquals(1,count("BEFORE","reserveShopPet")); assertEquals(1,count("AFTER","reserveShopPet"));
        assertEquals(0,count("BEFORE","reserveRequestedInventory")); assertEquals(0,count("BEFORE","insertPetReservationAgreement"));
    }
    @Test public void unhandledTechnicalFailureIsObservedAtAtomAndScenario() {
        var session=shop.openStore("TRACE-FAIL",false); messages.clear();
        try (var fault=ExampleServiceFaults.activate("petshop.findBuyer","before")) {
            assertThrows(IllegalStateException.class,()->shop.call("readShopCustomer",Map.of("Buyer",Map.of("membershipNumber",session.customerId))));
            fault.verifyTriggered();
        }
        assertEquals(tracingAvailable() ? 1 : 0,count("FAILURE","findBuyer"));
        assertEquals(tracingAvailable() ? 1 : 0,count("FAILURE","readShopCustomer"));
        assertEquals(0,count("AFTER","readShopCustomer"));
    }
}
