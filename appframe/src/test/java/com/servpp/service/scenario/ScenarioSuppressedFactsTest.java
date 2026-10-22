package com.servpp.service.scenario;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ScenarioSuppressedFactsTest {
    public static class Minimal {
        String code;
        public void setCode(String code) { this.code = code; }
    }
    public static class ArrayFacts extends Minimal {
        String[] suppressed;
        public void setSuppressedCodes(String[] suppressed) { this.suppressed = suppressed; }
    }
    public static class ListFacts extends Minimal {
        List<String> suppressed;
        public void setSuppressedCodes(List<String> suppressed) { this.suppressed = suppressed; }
    }
    @Test public void preservesPrimaryCodeAndProvidesAllImmediateSecondaryCodesInOrder() {
        var primary = new IllegalStateException("PRIMARY");
        primary.addSuppressed(new IllegalArgumentException("SECONDARY"));
        primary.addSuppressed(new IllegalStateException());
        var array = ScenarioFailures.describe(primary, new ArrayFacts());
        assertEquals("PRIMARY", array.code);
        assertArrayEquals(new String[]{"SECONDARY", "IllegalStateException"}, array.suppressed);
        var list = ScenarioFailures.describe(primary, new ListFacts());
        assertEquals(List.of("SECONDARY", "IllegalStateException"), list.suppressed);
        assertEquals("PRIMARY", ScenarioFailures.describe(primary, new Minimal()).code);
    }
    @Test public void noSecondaryFailureProducesEmptyCollectionRatherThanNull() {
        var error = new IllegalStateException("PRIMARY");
        assertEquals(0, ScenarioFailures.describe(error, new ArrayFacts()).suppressed.length);
        assertTrue(ScenarioFailures.describe(error, new ListFacts()).suppressed.isEmpty());
        assertEquals(0, error.getSuppressed().length);
    }
}
