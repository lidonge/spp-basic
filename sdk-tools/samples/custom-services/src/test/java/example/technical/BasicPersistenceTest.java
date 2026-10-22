package example.technical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.ExampleServiceBarrier;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Only input, generated-service calls and assertions; business updates remain in ledger.spp. */
public class BasicPersistenceTest {
    @Test public void generatedFullUpdatePersistsNullWithoutOfficialTracking() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.create();
            var revision = new LinkedHashMap<String,Object>();
            revision.put("note", null);
            revision.put("amount", 14);
            fixture.call("reviseRecord", Map.of("Entry", Map.of("code", "A"), "Revision", revision));
            var entry = fixture.read();
            assertEquals(14, entry.path("amount").asInt());
            assertTrue(entry.path("note").isNull());
            try (var connection = fixture.dataSource.getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT note, amount FROM LEDGER_ENTRIES WHERE code = 'A'")) {
                assertTrue(rows.next());
                assertNull(rows.getString(1));
                assertEquals(14, rows.getInt(2));
                assertFalse(rows.next());
            }
            assertFalse(com.servpp.service.idempotency.BusinessIdempotencyManager.isAvailable());
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("com.servpp.service.runtime.OfficialPropertyChanges"));
        }
    }

    @Test public void concurrentGeneratedUpdatesRejectOneStaleBaseline() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.create();
            var pool = Executors.newFixedThreadPool(2);
            try (var barrier = ExampleServiceBarrier.activate("ledger.applyRevision", 2)) {
                var first = pool.submit(() -> fixture.revise(21));
                var second = pool.submit(() -> fixture.revise(35));
                String a = first.get(40, TimeUnit.SECONDS);
                String b = second.get(40, TimeUnit.SECONDS);
                barrier.verifyReached();
                assertEquals(java.util.Set.of("OK", "PERSISTENCE_VERSION_CONFLICT"), java.util.Set.of(a, b));
                var stored = fixture.read();
                int winner = a.equals("OK") ? 21 : 35;
                assertEquals(winner, stored.path("amount").asInt());
                assertEquals("revision-" + winner, stored.path("note").asText());
            } finally {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        final DriverManagerJdbcDataSource dataSource;
        final LocalGeneratedServiceRuntime runtime;
        final String previousUrl = System.getProperty("spp.basic-ledger.datasource-url");
        Fixture() {
            String url = "jdbc:h2:mem:basic_ledger_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
            System.setProperty("spp.basic-ledger.datasource-url", url);
            dataSource = new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null);
            DefaultServiceContainer.getInstance().clear();
            runtime = LocalGeneratedServiceRuntime.jsonSnapshots(dataSource, mapper, ignored -> null);
            runtime.enableExampleFaults();
            GeneratedApplicationDeployment.install(runtime, "basicLedger");
        }
        JsonNode call(String service, Map<String,?> input) throws Exception {
            var type = Class.forName("example.generated.ledger.model." + Character.toUpperCase(service.charAt(0))
                    + service.substring(1) + "Request");
            Object request = mapper.convertValue(input, type);
            runtime.executeGeneratedService("ledger." + service, request);
            return mapper.valueToTree(request);
        }
        JsonNode read() throws Exception {
            return call("readRecord", Map.of("Entry", Map.of("code", "A"))).path("Entry");
        }
        void create() throws Exception {
            call("createRecord", Map.of("Entry", Map.of("code", "A"),
                    "Revision", Map.of("note", "original", "amount", 7)));
        }
        String revise(int amount) throws Exception {
            try {
                call("reviseRecord", Map.of("Entry", Map.of("code", "A"),
                        "Revision", Map.of("note", "revision-" + amount, "amount", amount)));
                return "OK";
            } catch (IllegalStateException failure) {
                if (!"PERSISTENCE_VERSION_CONFLICT".equals(failure.getMessage())) throw failure;
                return failure.getMessage();
            }
        }
        @Override public void close() {
            DefaultServiceContainer.getInstance().clear();
            CallCounter.reset();
            if (previousUrl == null) System.clearProperty("spp.basic-ledger.datasource-url");
            else System.setProperty("spp.basic-ledger.datasource-url", previousUrl);
        }
    }
}
