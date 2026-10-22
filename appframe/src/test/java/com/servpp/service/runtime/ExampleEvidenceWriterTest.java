package com.servpp.service.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleEvidenceWriterTest {
    public static class Facts {
        @JsonProperty("amount") public BigDecimal getAmount() { return new BigDecimal("12345678901234567890.0000000000000000000000001"); }
        @JsonProperty("notes") public List<String> getNotes() { return List.of("源参与者说明", "未执行候选"); }
        public Object getRuntimeHelper() { throw new AssertionError("Runtime helper must not be read"); }
    }
    @Test public void declaredFactsPreserveExactNumbersAndNestedSourceDescriptions() throws Exception {
        var directory = Files.createTempDirectory("spp-example-evidence-");
        ExampleEvidenceWriter.write(directory, "sampleRun", Map.of("facts", new Facts()), Map.of("count", 2), null);
        String json = Files.readString(directory.resolve("sampleRun.json"));
        assertTrue(json.contains("12345678901234567890.0000000000000000000000001"));
        var evidence = new ObjectMapper().readTree(json);
        assertEquals("PASS", evidence.path("status").asText());
        assertEquals(2, evidence.path("participants").path("facts").path("notes").size());
        assertFalse(json.contains("runtimeHelper"));
        String md = Files.readString(directory.resolve("sampleRun.md"));
        assertTrue(md.contains("源参与者说明")); assertTrue(md.contains("未执行候选"));
        assertTrue(md.contains("result.count"));
        assertTrue(md.contains("12345678901234567890.0000000000000000000000001"));
    }
    @Test public void failuresRetainPrimaryAndSuppressedFactsWithoutRecursiveThrowableSerialization() throws Exception {
        var directory = Files.createTempDirectory("spp-example-failure-evidence-");
        var primary = new IllegalStateException("primary"); var secondary = new IllegalArgumentException("secondary");
        primary.addSuppressed(secondary); secondary.addSuppressed(primary);
        ExampleEvidenceWriter.write(directory, "failedRun", Map.of("after", 7), null, primary);
        var evidence = new ObjectMapper().readTree(directory.resolve("failedRun.json").toFile());
        assertEquals("FAIL", evidence.path("status").asText());
        assertEquals("primary", evidence.path("failure").path("message").asText());
        assertEquals("secondary", evidence.path("failure").path("suppressed").get(0).path("message").asText());
        assertTrue(evidence.path("failure").path("suppressed").get(0).path("suppressed").get(0).path("repeatedReference").asBoolean());
    }
    @Test public void outputNamesCannotEscapeTheConfiguredDirectory() throws Exception {
        var directory = Files.createTempDirectory("spp-example-evidence-path-");
        assertThrows(IllegalArgumentException.class, () -> ExampleEvidenceWriter.write(directory, "../escaped", null, null, null));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
}
