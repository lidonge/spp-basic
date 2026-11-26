package com.servpp.service.serial;

import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.InvocationContext;
import com.servpp.service.technical.FieldValueContext;
import com.servpp.service.technical.FieldValueInterceptor;
import com.servpp.service.technical.RecordInterceptors;
import com.servpp.service.transaction.BusinessTransactionRejection;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SerialRuntimeTest {
    public static class Row {
        private String branch = "B1", reference, logNumber;
        private java.time.OffsetDateTime businessDate = OffsetDateTime.parse("2026-11-26T23:30:00Z");
        private int seqNo;
        public Row() { }
        public Row(String branch) { this.branch = branch; }
        public String getBranch() { return branch; }
        public void setBranch(String value) { branch = value; }
        public OffsetDateTime getBusinessDate() { return businessDate; }
        public void setBusinessDate(OffsetDateTime value) { businessDate = value; }
        public String getReference() { return reference; }
        public void setReference(String value) { reference = value; }
        public String getLogNumber() { return logNumber; }
        public void setLogNumber(String value) { logNumber = value; }
        public int getSeqNo() { return seqNo; }
        public void setSeqNo(int value) { seqNo = value; }
    }

    static final String MEMORY = InMemorySerialEngine.class.getName();

    @Before public void clean() { RecordInterceptors.reset(); InMemorySerialEngine.reset(); }

    static Map<String, String> rule(String engineClass, Map<String, String> engineConfig, String... pairs) {
        var config = new LinkedHashMap<String, String>();
        config.put("rule", "r"); config.put("engine.name", "engine"); config.put("engine.class", engineClass);
        engineConfig.forEach((key, value) -> config.put("engine.config." + key, value));
        for (int i = 0; i < pairs.length; i += 2) config.put(pairs[i], pairs[i + 1]);
        return config;
    }
    static RecordInterceptors.Binding binding(Map<String, String> config) {
        return new RecordInterceptors.Binding("serial.r", SerialValueService.class.getName(), config);
    }
    static void insert(Row row, String field, RecordInterceptors.Binding binding) {
        RecordInterceptors.insert(row, "Row", field, binding);
    }

    @Test public void rangeWrapAndFormatAreAppliedToTheEnginesRawNumber() {
        var binding = binding(rule(MEMORY, Map.of("store", "a"), "scope", "branch", "min", "1", "max", "3", "wrap", "true",
                "format", "{branch}/{seq:4}"));
        var seen = new ArrayList<String>();
        for (int i = 0; i < 5; i++) { var row = new Row(); insert(row, "reference", binding); seen.add(row.getReference()); }
        assertEquals(List.of("B1/0001", "B1/0002", "B1/0003", "B1/0001", "B1/0002"), seen);
    }

    @Test public void withoutWrapTheRangeEndIsARejection() {
        var binding = binding(rule(MEMORY, Map.of("store", "b"), "min", "100", "max", "101"));
        var first = new Row(); insert(first, "seqNo", binding);
        var second = new Row(); insert(second, "seqNo", binding);
        assertEquals(100, first.getSeqNo()); assertEquals(101, second.getSeqNo());
        try { insert(new Row(), "seqNo", binding); fail("exhausted"); }
        catch (BusinessTransactionRejection exhausted) { assertEquals("SERIAL_EXHAUSTED", exhausted.getMessage()); }
    }

    @Test public void scopesAreIndependentAndADateScopeResetsDaily() {
        var binding = binding(rule(MEMORY, Map.of("store", "c"), "scope", "branch,businessDate", "format", "{branch}{businessDate:yyyyMMdd}-{seq:3}"));
        var one = new Row("B1"); insert(one, "reference", binding);
        var two = new Row("B1"); insert(two, "reference", binding);
        var other = new Row("B2"); insert(other, "reference", binding);
        var nextDay = new Row("B1"); nextDay.setBusinessDate(OffsetDateTime.parse("2026-11-27T00:10:00Z")); insert(nextDay, "reference", binding);
        assertEquals("B120261126-001", one.getReference());
        assertEquals("B120261126-002", two.getReference());
        assertEquals("B220261126-001", other.getReference());
        assertEquals("a new business date starts a new counter", "B120261127-001", nextDay.getReference());
    }

    @Test public void aSeededStoreIsDeterministic() {
        var binding = binding(rule(MEMORY, Map.of("store", "d"), "scope", "branch"));
        InMemorySerialEngine.seed("d", SerialValueService.counterKey("r", "B9"), 41);
        var row = new Row("B9"); insert(row, "seqNo", binding);
        assertEquals(42, row.getSeqNo());
        InMemorySerialEngine.reset();
        RecordInterceptors.reset();
        var again = new Row("B9"); insert(again, "seqNo", binding);
        assertEquals(1, again.getSeqNo());
    }

    @Test public void anEmptyScopeValueIsRejectedAndAFilledFieldIsKept() {
        var binding = binding(rule(MEMORY, Map.of("store", "e"), "scope", "branch"));
        var blank = new Row(""); 
        try { insert(blank, "seqNo", binding); fail(); } catch (BusinessTransactionRejection expected) { assertEquals("SERIAL_SCOPE_VALUE_REQUIRED", expected.getMessage()); }
        var filled = new Row(); filled.setReference("KEEP"); insert(filled, "reference", binding);
        assertEquals("an explicitly present value is not replaced", "KEEP", filled.getReference());
    }

    @Test public void gaplessEngineRequiresAGaplessImplementation() {
        try { insert(new Row(), "seqNo", binding(rule(MEMORY, Map.of(), "gapless", "true"))); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("SERIAL_GAPLESS_UNSUPPORTED")); }
    }

    // ---- JDBC engines -------------------------------------------------------------------------------------------

    private static DataSource h2(String name) throws Exception {
        var ds = new DriverManagerJdbcDataSource("org.h2.Driver", "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", null, null);
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE SPP_SERIAL_COUNTER (COUNTER_KEY VARCHAR(200) PRIMARY KEY, COUNTER_VALUE BIGINT NOT NULL)");
        }
        return ds;
    }
    private static long counter(DataSource ds, String key) throws Exception {
        try (var c = ds.getConnection(); var s = c.prepareStatement("SELECT COUNTER_VALUE FROM SPP_SERIAL_COUNTER WHERE COUNTER_KEY = ?")) {
            s.setString(1, key);
            try (var r = s.executeQuery()) { return r.next() ? r.getLong(1) : -1; }
        }
    }

    @Test public void gaplessAllocationRollsBackWithItsTransaction() throws Exception {
        var ds = h2("serial_rt_rollback");
        var binding = binding(rule(GaplessCounterEngine.class.getName(), Map.of(), "scope", "branch", "gapless", "true", "format", "{seq:4}"));
        var rows = new ArrayList<Row>();
        JdbcTransactionScope.execute(ds, c -> { var r = new Row(); insert(r, "reference", binding); rows.add(r); return null; });
        try {
            JdbcTransactionScope.execute(ds, c -> { insert(new Row(), "reference", binding); throw new java.sql.SQLException("business failure"); });
            fail();
        } catch (java.sql.SQLException expected) { /* rolled back */ }
        JdbcTransactionScope.execute(ds, c -> { var r = new Row(); insert(r, "reference", binding); rows.add(r); return null; });
        assertEquals("0001", rows.get(0).getReference());
        assertEquals("no gap after the rollback", "0002", rows.get(1).getReference());
        assertEquals(2, counter(ds, SerialValueService.counterKey("r", "B1")));
    }

    @Test public void gaplessNeedsABusinessTransaction() throws Exception {
        h2("serial_rt_notx");
        var binding = binding(rule(GaplessCounterEngine.class.getName(), Map.of(), "gapless", "true"));
        try { insert(new Row(), "seqNo", binding); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage(), expected.getMessage().startsWith("SERIAL_GAPLESS_REQUIRES_TRANSACTION")); }
    }

    @Test public void gaplessEngineCoexistsWithALegacyCounterTable() throws Exception {
        var ds = h2("serial_rt_legacy");
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE CCMSRL2 (BRANCH_KEY VARCHAR(200) PRIMARY KEY, LAST_NO BIGINT NOT NULL)");
            s.executeUpdate("INSERT INTO CCMSRL2 VALUES ('r', 41)");
        }
        var binding = binding(rule(GaplessCounterEngine.class.getName(),
                Map.of("table", "CCMSRL2", "keyColumn", "BRANCH_KEY", "valueColumn", "LAST_NO"), "gapless", "true"));
        var row = new Row();
        JdbcTransactionScope.execute(ds, c -> { insert(row, "seqNo", binding); return null; });
        assertEquals("the legacy counter is the last number issued", 42, row.getSeqNo());
    }

    @Test public void concurrentGaplessAllocationsAreUniqueAndContiguous() throws Exception {
        var ds = h2("serial_rt_threads");
        var binding = binding(rule(GaplessCounterEngine.class.getName(), Map.of(), "scope", "branch", "gapless", "true"));
        int threads = 8, each = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        var results = Collections.synchronizedList(new ArrayList<Integer>());
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) futures.add(pool.submit((Callable<Void>) () -> {
            for (int i = 0; i < each; i++) JdbcTransactionScope.execute(ds, c -> { var r = new Row(); insert(r, "seqNo", binding); results.add(r.getSeqNo()); return null; });
            return null;
        }));
        for (var future : futures) future.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(threads * each, Set.copyOf(results).size());
        assertEquals(threads * each, Collections.max(results).intValue());
        assertEquals(1, Collections.min(results).intValue());
    }

    @Test public void concurrentInMemoryAllocationsAreUnique() throws Exception {
        var binding = binding(rule(MEMORY, Map.of("store", "threads"), "scope", "branch"));
        int threads = 8, each = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        var results = Collections.synchronizedList(new ArrayList<Integer>());
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) futures.add(pool.submit(() -> { for (int i = 0; i < each; i++) { var r = new Row(); insert(r, "seqNo", binding); results.add(r.getSeqNo()); } }));
        for (var future : futures) future.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(threads * each, Set.copyOf(results).size());
    }

    @Test public void dbSequenceEngineDrawsFromANativeSequence() throws Exception {
        var ds = h2("serial_rt_sequence");
        try (var c = ds.getConnection(); var s = c.createStatement()) { s.executeUpdate("CREATE SEQUENCE GL_DAILY_SEQ START WITH 7"); }
        var binding = binding(rule(DbSequenceEngine.class.getName(), Map.of("sequence", "GL_DAILY_SEQ",
                "datasourceUrl", "jdbc:h2:mem:serial_rt_sequence;DB_CLOSE_DELAY=-1", "driverClassName", "org.h2.Driver"), "scope", "branch", "format", "GL{seq:3}"));
        var a = new Row("B1"); insert(a, "reference", binding);
        var b = new Row("B2"); insert(b, "reference", binding);
        assertEquals("GL007", a.getReference());
        assertEquals("GL008", b.getReference());
    }

    @Test public void engineConfigurationIsValidatedByTheEngine() {
        try { insert(new Row(), "seqNo", binding(rule(DbSequenceEngine.class.getName(), Map.of("sequence", "x; drop table y")))); fail(); }
        catch (IllegalStateException | IllegalArgumentException expected) {
            assertTrue(expected.getMessage() + " / " + expected.getCause(), String.valueOf(expected.getMessage()).contains("TECHNICAL_SERVICE_INITIALIZATION_FAILED")
                    || String.valueOf(expected.getMessage()).contains("SERIAL_CONFIGURATION_INVALID"));
        }
    }

    // ---- record interception and context ------------------------------------------------------------------------

    public static class Echo implements FieldValueInterceptor {
        final AtomicInteger calls = new AtomicInteger();
        @Override public Object produce(FieldValueContext context) {
            calls.incrementAndGet();
            return context.entityName() + "." + context.fieldName() + ":" + context.value("branch") + ":" + context.fieldType().getSimpleName()
                    + ":" + context.invocation().mode();
        }
    }
    @Test public void aCustomValueInterceptorSeesTheRecordAndItsContext() {
        var echo = new RecordInterceptors.Binding("echo", Echo.class.getName(), Map.of());
        var row = new Row("B5");
        insert(row, "reference", echo);
        assertEquals("Row.reference:B5:String:LOCAL", row.getReference());
    }
    @Test public void aServiceWithoutTheValueContractIsRefusedAtRuntime() {
        var wrong = new RecordInterceptors.Binding("wrong", MEMORY, Map.of());
        try { insert(new Row(), "reference", wrong); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("TECHNICAL_SERVICE_CONTRACT_REQUIRED")); }
    }
    @Test public void aProducedValueIsConvertedToTheFieldType() {
        var echoNumber = new RecordInterceptors.Binding("n", NumberService.class.getName(), Map.of());
        var row = new Row(); insert(row, "seqNo", echoNumber);
        assertEquals(12, row.getSeqNo());
        try { insert(new Row(), "reference", new RecordInterceptors.Binding("n", NullService.class.getName(), Map.of())); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().startsWith("FIELD_VALUE_REQUIRED")); }
    }
    public static class NumberService implements FieldValueInterceptor { @Override public Object produce(FieldValueContext c) { return 12L; } }
    public static class NullService implements FieldValueInterceptor { @Override public Object produce(FieldValueContext c) { return null; } }

    @Test public void contextIdentifiersComeFromTheIngressNotFromTheRequest() {
        var binding = new RecordInterceptors.Binding("context.transactionLogNumber", ContextValueService.class.getName(), Map.of("name", "transactionLogNumber"));
        var row = new Row();
        InvocationContext.withIdentifiers(Map.of("transactionLogNumber", "TL-7"), () -> { insert(row, "logNumber", binding); return null; });
        assertEquals("TL-7", row.getLogNumber());
        try { insert(new Row(), "logNumber", binding); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().startsWith("CONTEXT_IDENTIFIER_UNAVAILABLE")); }
        var remote = new Row();
        InvocationContext.remote(InvocationContext.Origin.SERVICE, "caller", "serial-1", Map.of("transactionLogNumber", "TL-8"), () -> {
            // Internal calls run as LOCAL but keep the ingress identifiers.
            InvocationContext.local(() -> { insert(remote, "logNumber", binding); return null; });
            return null;
        });
        assertEquals("TL-8", remote.getLogNumber());
    }
    @Test public void onlyTheDeclaredContextNamesExist() {
        try { new InvocationContext.Header(InvocationContext.Mode.LOCAL, InvocationContext.Origin.SERVICE, "", "", Map.of("sessionToken", "x")); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().startsWith("CONTEXT_IDENTIFIER_UNKNOWN")); }
        try { new ContextValueService().configure(Map.of("name", "sessionToken")); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().startsWith("CONTEXT_IDENTIFIER_UNKNOWN")); }
    }
}
