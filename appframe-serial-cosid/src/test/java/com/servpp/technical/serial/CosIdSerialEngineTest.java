package com.servpp.technical.serial;

import com.servpp.service.serial.SerialRequest;
import com.servpp.service.serial.SerialValueService;
import com.servpp.service.technical.RecordInterceptors;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CosIdSerialEngineTest {
    public static class Row {
        private String branch = "B1", reference;
        private OffsetDateTime businessDate = OffsetDateTime.parse("2026-11-26T10:00:00Z");
        private long seqNo;
        public String getBranch() { return branch; }
        public void setBranch(String value) { branch = value; }
        public OffsetDateTime getBusinessDate() { return businessDate; }
        public void setBusinessDate(OffsetDateTime value) { businessDate = value; }
        public String getReference() { return reference; }
        public void setReference(String value) { reference = value; }
        public long getSeqNo() { return seqNo; }
        public void setSeqNo(long value) { seqNo = value; }
    }
    @Before public void clean() { RecordInterceptors.reset(); }

    static final String ENGINE = CosIdSerialEngine.class.getName();
    static Map<String, String> segment(String db, String step) {
        return Map.of("mode", "segment", "step", step, "datasourceUrl", "jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1", "driverClassName", "org.h2.Driver");
    }
    static RecordInterceptors.Binding binding(Map<String, String> engine, String... pairs) {
        var config = new java.util.LinkedHashMap<String, String>();
        config.put("rule", "r"); config.put("engine.name", "cosid"); config.put("engine.class", ENGINE);
        engine.forEach((key, value) -> config.put("engine.config." + key, value));
        for (int i = 0; i < pairs.length; i += 2) config.put(pairs[i], pairs[i + 1]);
        return new RecordInterceptors.Binding("serial.r", SerialValueService.class.getName(), config);
    }
    static void insert(Row row, String field, RecordInterceptors.Binding binding) { RecordInterceptors.insert(row, "Row", field, binding); }

    @Test public void segmentModeIssuesIncreasingNumbersPerScopeFromTheJdbcRangeStore() {
        var binding = binding(segment("cosid_scope", "10"), "scope", "branch", "format", "{branch}-{seq:3}");
        var a1 = new Row(); insert(a1, "reference", binding);
        var a2 = new Row(); insert(a2, "reference", binding);
        var b1 = new Row(); b1.setBranch("B2"); insert(b1, "reference", binding);
        assertEquals("B1-001", a1.getReference());
        assertEquals("B1-002", a2.getReference());
        assertEquals("scopes are independent", "B2-001", b1.getReference());
    }

    @Test public void aBusinessDateScopeResetsDaily() {
        var binding = binding(segment("cosid_daily", "5"), "scope", "branch,businessDate", "format", "{businessDate:yyyyMMdd}-{seq:2}");
        var d1 = new Row(); insert(d1, "reference", binding);
        var d2 = new Row(); insert(d2, "reference", binding);
        var next = new Row(); next.setBusinessDate(OffsetDateTime.parse("2026-11-27T09:00:00Z")); insert(next, "reference", binding);
        assertEquals("20261126-01", d1.getReference());
        assertEquals("20261126-02", d2.getReference());
        assertEquals("20261127-01", next.getReference());
    }

    @Test public void wrapAndRangeAreAppliedByTheRuleNotByTheEngine() {
        var binding = binding(segment("cosid_wrap", "2"), "min", "1", "max", "3", "wrap", "true", "scope", "branch");
        var seen = new ArrayList<Long>();
        for (int i = 0; i < 7; i++) { var row = new Row(); insert(row, "seqNo", binding); seen.add(row.getSeqNo()); }
        assertEquals(List.of(1L, 2L, 3L, 1L, 2L, 3L, 1L), seen);
    }

    @Test public void aNewEngineInstanceOverTheSameStoreContinuesAfterTheReservedSegment() {
        var config = segment("cosid_restart", "10");
        var first = new Row(); insert(first, "seqNo", binding(config));
        assertEquals(1, first.getSeqNo());
        RecordInterceptors.reset(); // a restart: the first segment [1, 11) was reserved, its rest is skipped
        var second = new Row(); insert(second, "seqNo", binding(config));
        assertEquals(11, second.getSeqNo());
    }

    @Test public void concurrentAllocationsAcrossThreadsAndInstancesNeverDuplicate() throws Exception {
        var config = segment("cosid_threads", "50");
        var binding = binding(config, "scope", "branch");
        int threads = 8, each = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        var results = Collections.synchronizedList(new ArrayList<Long>());
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) futures.add(pool.submit(() -> {
            for (int i = 0; i < each; i++) { var row = new Row(); insert(row, "seqNo", binding); results.add(row.getSeqNo()); }
        }));
        for (var future : futures) future.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(threads * each, Set.copyOf(results).size());
    }

    @Test public void twoEnginesSharingOneStoreNeverIssueTheSameNumber() throws Exception {
        var a = new CosIdSerialEngine(); a.configure(segment("cosid_shared", "5"));
        var b = new CosIdSerialEngine(); b.configure(segment("cosid_shared", "5"));
        var seen = new java.util.HashSet<Long>();
        for (int i = 0; i < 40; i++) {
            assertTrue(seen.add(a.next(request("c"))));
            assertTrue(seen.add(b.next(request("c"))));
        }
        a.close(); b.close();
    }

    @Test public void snowflakeModeIssuesDistinctIncreasingNumbers() throws Exception {
        var engine = new CosIdSerialEngine();
        engine.configure(Map.of("mode", "snowflake", "machine", "3"));
        long previous = 0;
        for (int i = 0; i < 1000; i++) { long id = engine.next(request("any")); assertTrue(id > previous); previous = id; }
        engine.close();
    }

    @Test public void configurationIsValidatedByTheEngine() {
        for (Map<String, String> bad : List.of(Map.of("mode", "segment"), Map.of("mode", "nope"), Map.of("mode", "snowflake"),
                Map.of("mode", "snowflake", "machine", "5000"),
                Map.of("mode", "segment", "step", "0", "datasourceUrl", "jdbc:h2:mem:x", "driverClassName", "org.h2.Driver"))) {
            try { new CosIdSerialEngine().configure(bad); fail(bad.toString()); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().startsWith("SERIAL_CONFIGURATION")); }
        }
    }

    private static SerialRequest request(String counter) {
        return new SerialRequest("r", counter, Map.of(), Optional.<LocalDate>empty(), 1, Long.MAX_VALUE, false, Optional.empty());
    }
}
