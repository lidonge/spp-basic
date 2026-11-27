package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.InvocationContext;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.serial.InMemorySerialEngine;
import com.servpp.service.serial.SerialEngine;
import com.servpp.service.serial.SerialRequest;
import com.servpp.service.serial.SerialValueService;
import com.servpp.service.technical.FieldValueContext;
import com.servpp.service.technical.FieldValueInterceptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** E1: serial rules, record-level interception and the built-in value services, end to end on H2. */
public class SerialServiceGenerationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final String SPP = """
            domain ledger;
            Contract Voucher short voucher {
                String voucherCode; String branch; DateTime businessDate; String reference; int seqNo;
                String logNumber; String stamp; boolean rejected;
            }
            atomic update storeVoucher(Voucher);
            Enum Fault { REJECTED }
            atomic check guard(Voucher) { require(voucher.rejected == false, Fault.REJECTED); }
            Scenario serial record(Voucher) { transaction { storeVoucher; guard; } }
            """;

    private static String app(String declarations, String autos, String phyServices, String url) {
        return """
                domain ledger;
                application ledgers { deploy ledger; }
                %s
                tables contracts { table VOUCHERS(Voucher) { primarykey(Voucher.voucherCode); %s } }
                scope { local { persist(insert, argument(0, Voucher.voucherCode)) storeVoucher; } }
                phy {
                    %s
                    repository records {
                        adapter jdbcObjectRepository;
                        config default = true;
                        config persistenceMode = LOCAL; config persistenceResource = records;
                        config appDdlTables = true;
                        config datasourceUrl = "%s"; config driverClassName = "org.h2.Driver";
                    }
                }
                """.formatted(declarations, autos, phyServices, url);
    }

    private void generate(String app, String url, Class<?> fixture, String variant) throws Exception {
        Path root = temp.getRoot().toPath();
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("ledger.spp"), SPP);
        Files.writeString(source.resolve("ledger.app"), app);
        GeneratedDatabaseFixture.run(root, source, "ledger", fixture, variant);
    }
    private static String url(String name) { return "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1"; }

    @Test public void builtinSerialOverInMemoryEngine() throws Exception {
        String url = url("serial_memory");
        generate(app("""
                technical services { memoryEngine; }
                serial voucherSerial using memoryEngine {
                    scope branch; range 1..3 wrap; format "{branch}-{seq:4}";
                }
                serial numberSerial using memoryEngine { scope branch; range 100..9999; }
                """, """
                auto Voucher.reference serial voucherSerial;
                auto Voucher.seqNo serial numberSerial;
                auto Voucher.logNumber context transactionLogNumber;
                """, """
                technical service memoryEngine { implementation: "com.servpp.service.serial.InMemorySerialEngine"; config store = "t1"; }
                """, url), url, MemoryFixture.class, "");
    }

    public static class MemoryFixture {
        public static void main(String[] args) throws Exception {
            var fixture = new Fixture(url("serial_memory"));
            InMemorySerialEngine.reset();
            // Seeded like a legacy counter: the next allocation for B2 is 3.
            InMemorySerialEngine.seed("t1", SerialValueService.counterKey("voucherSerial", "B2"), 2);
            var identifiers = Map.of("transactionLogNumber", "TL-0001");
            for (String id : List.of("a1", "a2", "b1", "a3", "a4")) {
                String branch = id.startsWith("a") ? "B1" : "B2";
                InvocationContext.withIdentifiers(identifiers, () -> fixture.record(id, branch, false));
            }
            assertEquals("B1-0001", fixture.column("a1", "reference"));
            assertEquals("B1-0002", fixture.column("a2", "reference"));
            assertEquals("B2-0003", fixture.column("b1", "reference"));
            assertEquals("B1-0003", fixture.column("a3", "reference"));
            assertEquals("wrap after the range maximum", "B1-0001", fixture.column("a4", "reference"));
            assertEquals("100", fixture.column("a1", "seqNo"));
            assertEquals("101", fixture.column("a2", "seqNo"));
            assertEquals("per scope independence", "100", fixture.column("b1", "seqNo"));
            assertEquals("TL-0001", fixture.column("a1", "logNumber"));
            try { fixture.record("x", "B1", false); fail("context identifier must come from the ingress"); }
            catch (RuntimeException expected) { assertTrue(String.valueOf(expected.getMessage()), true); }
        }
    }

    @Test public void gaplessAllocationRollsBackWithTheBusinessTransaction() throws Exception {
        String url = url("serial_gapless");
        generate(app("""
                transactions { default at resource records; }
                technical services { counterEngine; }
                serial voucherSerial using counterEngine { scope branch; range 1..9999; format "G{seq:4}"; gapless; }
                """, "auto Voucher.reference serial voucherSerial;", """
                technical service counterEngine { implementation: "com.servpp.service.serial.GaplessCounterEngine"; }
                """, url), url, GaplessFixture.class, "");
    }

    public static class GaplessFixture {
        public static void main(String[] args) throws Exception {
            var fixture = new Fixture(url("serial_gapless"));
            try (var connection = fixture.source.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE SPP_SERIAL_COUNTER (COUNTER_KEY VARCHAR(200) PRIMARY KEY, COUNTER_VALUE BIGINT NOT NULL)");
            }
            fixture.record("g1", "B1", false);
            // The insert fails on the duplicate key after the number was allocated: the allocation must roll back too.
            try { fixture.record("g1", "B1", false); fail("duplicate key"); }
            catch (RuntimeException expected) { /* the business transaction rolls back */ }
            fixture.record("g3", "B1", false);
            fixture.record("g4", "B2", false);
            assertEquals("G0001", fixture.column("g1", "reference"));
            assertEquals("the rolled-back number is reused: no gap", "G0002", fixture.column("g3", "reference"));
            assertEquals("scopes are separate counters", "G0001", fixture.column("g4", "reference"));
            assertEquals(3, fixture.count());
            try (var connection = fixture.source.getConnection(); var statement = connection.createStatement();
                 var row = statement.executeQuery("SELECT COUNTER_VALUE FROM SPP_SERIAL_COUNTER WHERE COUNTER_KEY = 'voucherSerial/B1'")) {
                assertTrue(row.next());
                assertEquals(2, row.getLong(1));
            }
        }
    }

    @Test public void dbSequenceEngine() throws Exception {
        String url = url("serial_sequence");
        generate(app("""
                technical services { sequenceEngine; }
                serial voucherSerial using sequenceEngine { range 1..99999; format "S{seq:5}"; }
                """, "auto Voucher.reference serial voucherSerial;", """
                technical service sequenceEngine {
                    implementation: "com.servpp.service.serial.DbSequenceEngine";
                    config sequence = "VOUCHER_SEQ"; config datasourceUrl = "%s"; config driverClassName = "org.h2.Driver";
                }
                """.formatted(url), url), url, SequenceFixture.class, "");
    }

    public static class SequenceFixture {
        public static void main(String[] args) throws Exception {
            var fixture = new Fixture(url("serial_sequence"));
            try (var connection = fixture.source.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE SEQUENCE VOUCHER_SEQ START WITH 41");
            }
            fixture.record("s1", "B1", false);
            fixture.record("s2", "B2", false);
            assertEquals("S00041", fixture.column("s1", "reference"));
            assertEquals("a native sequence is shared by every scope", "S00042", fixture.column("s2", "reference"));
        }
    }

    /** A developer's own value producer: no S++ serial semantics at all. */
    public static class StampService implements FieldValueInterceptor {
        private String label;
        private final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        @Override public void configure(Map<String, String> configuration) { label = configuration.get("label"); }
        @Override public Object produce(FieldValueContext context) {
            assertEquals("Voucher", context.entityName());
            assertEquals("stamp", context.fieldName());
            assertEquals(String.class, context.fieldType());
            assertEquals(label, context.configuration().get("label"));
            return label + context.value("branch") + "#" + count.incrementAndGet();
        }
    }

    @Test public void customTechnicalServiceFillsAFieldThroughIntercept() throws Exception {
        String url = url("serial_intercept");
        generate(app("""
                technical services { stamper; }
                intercept Voucher { insert: stamper -> stamp; }
                """, "", """
                technical service stamper { implementation: "com.servpp.generator.openapi.SerialServiceGenerationTest$StampService"; config label = "ST-"; }
                """, url), url, InterceptFixture.class, "");
    }

    public static class InterceptFixture {
        public static void main(String[] args) throws Exception {
            var fixture = new Fixture(url("serial_intercept"));
            fixture.record("c1", "B1", false);
            fixture.record("c2", "B7", false);
            assertEquals("ST-B1#1", fixture.column("c1", "stamp"));
            assertEquals("ST-B7#2", fixture.column("c2", "stamp"));
        }
    }

    /** A developer's own engine: it only supplies raw numbers; S++ applies range, wrap and format. */
    public static class NearEndEngine implements SerialEngine {
        private final Map<String, java.util.concurrent.atomic.AtomicLong> counters = new java.util.concurrent.ConcurrentHashMap<>();
        private long start;
        @Override public void configure(Map<String, String> configuration) {
            start = Long.parseLong(configuration.getOrDefault("start", "1"));
        }
        @Override public long next(SerialRequest request) {
            return counters.computeIfAbsent(request.counter(), key -> new java.util.concurrent.atomic.AtomicLong(start - 1)).incrementAndGet();
        }
    }

    @Test public void customEngineKeepsSppWrapAndFormat() throws Exception {
        String url = url("serial_engine");
        generate(app("""
                technical services { nearEnd; }
                serial voucherSerial using nearEnd { scope branch; range 1..9999 wrap; format "{branch}/{seq:4}"; }
                """, "auto Voucher.reference serial voucherSerial;", """
                technical service nearEnd { implementation: "com.servpp.generator.openapi.SerialServiceGenerationTest$NearEndEngine"; config start = "9998"; }
                """, url), url, EngineFixture.class, "");
    }

    public static class EngineFixture {
        public static void main(String[] args) throws Exception {
            var fixture = new Fixture(url("serial_engine"));
            for (String id : List.of("e1", "e2", "e3", "e4")) fixture.record(id, "B1", false);
            assertEquals("B1/9998", fixture.column("e1", "reference"));
            assertEquals("B1/9999", fixture.column("e2", "reference"));
            assertEquals("S++ wraps the engine's 10000th number", "B1/0001", fixture.column("e3", "reference"));
            assertEquals("B1/0002", fixture.column("e4", "reference"));
        }
    }

    /** Runs generated services over H2. */
    static final class Fixture {
        final DriverManagerJdbcDataSource source;
        final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        final LocalGeneratedServiceRuntime runtime;
        final Class<?> requestType;
        Fixture(String url) throws Exception {
            source = new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null);
            runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "ledgers");
            requestType = Class.forName("generated.ledger.model.RecordRequest");
        }
        Object record(String id, String branch, boolean rejected) {
            com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(null);
            var data = new HashMap<String, Object>();
            data.put("voucherCode", id); data.put("branch", branch); data.put("rejected", rejected);
            data.put("businessDate", "2026-11-26T10:00:00Z");
            runtime.executeGeneratedService("ledger.record", mapper.convertValue(Map.of("voucher", data), requestType));
            return null;
        }
        String column(String id, String column) throws Exception {
            try (var connection = source.getConnection();
                 var query = connection.prepareStatement("SELECT " + column + " FROM VOUCHERS WHERE voucherCode = ?")) {
                query.setString(1, id);
                try (var row = query.executeQuery()) { return row.next() ? row.getString(1) : null; }
            }
        }
        int count() throws Exception {
            try (var connection = source.getConnection(); var query = connection.createStatement();
                 var row = query.executeQuery("SELECT COUNT(*) FROM VOUCHERS")) { row.next(); return row.getInt(1); }
        }
    }
}
