package com.servpp.generator.openapi;

import com.servpp.lang.AppCompiler;
import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import org.junit.Test;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;
import static org.junit.Assert.*;

public class ParticipantFactsGenerationTest {
    @Test public void loweredFactsPredicateCompilesAndComparesNestedExactNumbers() throws Exception { verify(false); }
    @Test public void loweredFactsPredicateIncludesTheSelectedCustomPhyAdapter() throws Exception { verify(true); }

    private void verify(boolean custom) throws Exception {
        var root = Files.createTempDirectory("spp-participant-facts");
        var spp = root.resolve("sample.spp"); var phy = root.resolve("sample.phy");
        Files.writeString(spp, """
                domain sample;
                Role Evidence { String code; double value; Evidence[] children; }
                atomic check compare(Evidence expected, Evidence actual) { require(sameFacts(expected, actual), "CHANGED"); }
                """);
        Files.writeString(phy, "domain sample; phy { numeric double as " + (custom
                ? "fixtures.FactHarness.Wrapped using fixtures.FactHarness.Adapter" : "BigDecimal") + "; }");
        var project = new SppProject(); new SppCompiler(spp.toFile(), project).compile(); new AppCompiler(phy.toFile(), project).compile();
        var domain = project.getDomain("sample");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        var service = (SppService) domain.getSppClass("Compare"); service.setNumericGeneration(true);
        String statement = service.getJavaStatements().get(0).getJavaStatement();
        assertTrue(statement, statement.contains("ParticipantFacts.same"));
        if (custom) assertTrue(statement, statement.contains("NumericAdapters.get(fixtures.FactHarness.Adapter.class)"));
        var source = root.resolve("FactHarness.java");
        String valueType = custom ? "Wrapped" : "java.math.BigDecimal";
        String literal = custom ? "new Wrapped(value)" : "new java.math.BigDecimal(value)";
        Files.writeString(source, """
                package fixtures;
                public class FactHarness {
                    public record Wrapped(String value) {}
                    public static class Adapter implements com.servpp.service.numeric.NumericAdapter<Wrapped> {
                        public Adapter() {}
                        public Wrapped literal(String value) { return new Wrapped(value); }
                        public int compare(Wrapped a, Wrapped b) { return new java.math.BigDecimal(a.value()).compareTo(new java.math.BigDecimal(b.value())); }
                        public Wrapped add(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
                        public Wrapped subtract(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
                        public Wrapped multiply(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
                        public Wrapped divide(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
                        public Wrapped remainder(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
                        public Wrapped negate(Wrapped a) { throw new UnsupportedOperationException(); }
                        public Wrapped round(Wrapped a, int n, java.math.RoundingMode mode) { throw new UnsupportedOperationException(); }
                    }
                    public static class Evidence {
                        String code; %s value; java.util.List<Evidence> children = java.util.List.of();
                        Evidence(String code, String value) { this.code = code; this.value = %s; }
                        public String getCode() { return code; }
                        public %s getValue() { return value; }
                        public java.util.List<Evidence> getChildren() { return children; }
                    }
                    private static void compare(Evidence expected, Evidence actual) {
                        %s
                    }
                    public static String run(String actualValue, boolean changeChild) {
                        Evidence expected = new Evidence("ROW", "1.0"), actual = new Evidence("ROW", actualValue);
                        expected.children = java.util.List.of(new Evidence("CHILD", "3.0"));
                        actual.children = java.util.List.of(new Evidence(changeChild ? "OTHER" : "CHILD", "3.000"));
                        var result = com.servpp.service.runtime.BusinessChecks.evaluate(() -> compare(expected, actual));
                        return result.passed() ? "OK" : result.code();
                    }
                }
                """.formatted(valueType, literal, valueType, statement));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath", System.getProperty("java.class.path"),
                "-d", root.toString(), source.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()}, getClass().getClassLoader())) {
            var run = loader.loadClass("fixtures.FactHarness").getMethod("run", String.class, boolean.class);
            assertEquals("OK", run.invoke(null, "1.0000", false));
            assertEquals("CHANGED", run.invoke(null, "2", false));
            assertEquals("CHANGED", run.invoke(null, "1", true));
        }
    }
}
