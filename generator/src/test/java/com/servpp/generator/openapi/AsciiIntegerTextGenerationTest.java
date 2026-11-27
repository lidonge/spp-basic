package com.servpp.generator.openapi;

import com.servpp.lang.*;
import com.servpp.lang.models.*;
import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class AsciiIntegerTextGenerationTest {
    @Test public void generatedChecksAndConversionsExecuteWithPhysicalIntegerAdapter() throws Exception {
        Path source = Files.createTempFile("spp-text-integer", ".spp");
        Files.writeString(source, """
                domain rules;
                Role Text short text { String digits; int value; boolean valid; }
                atomic calculate decode(Text) {
                    text.valid = isAsciiDigits(text.digits);
                    if (text.valid) { text.value = parseUnsignedInteger(text.digits); }
                }
                """);
        SppProject project = new SppProject();
        new SppCompiler(source.toFile(), project).compile();
        Path phy = Files.createTempFile("spp-text-integer", ".phy");
        Files.writeString(phy, "domain rules; phy { numeric int as BigInteger; }");
        new AppCompiler(phy.toFile(), project).compile();
        SppDomain domain = project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        String body = ((SppService) domain.getSppClass("Decode")).getPhysicalJavaStatements().stream()
                .map(s -> s.getJavaStatement()).collect(java.util.stream.Collectors.joining("\n"));
        Path root = Files.createTempDirectory("spp-text-integer-java");
        Path fixture = root.resolve("TextFixture.java");
        Files.writeString(fixture, """
                import java.math.BigInteger;
                public class TextFixture {
                    public static class Text {
                        private String digits; private BigInteger value = BigInteger.valueOf(999);
                        private Boolean valid;
                        public String getDigits() { return digits; }
                        public BigInteger getValue() { return value; }
                        public void setValue(BigInteger value) { this.value = value; }
                        public Boolean getValid() { return valid; }
                        public void setValid(Boolean valid) { this.valid = valid; }
                    }
                    public static String run(String digits) {
                        Text text = new Text(); text.digits = digits;
                        %s
                        return text.valid + ":" + text.value;
                    }
                }
                """.formatted(body));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", System.getProperty("java.class.path"), "-d", root.toString(), fixture.toString()));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()}, getClass().getClassLoader())) {
            var run = loader.loadClass("TextFixture").getMethod("run", String.class);
            assertEquals("true:123", run.invoke(null, "000123"));
            assertEquals("true:999999999999999999999999999999", run.invoke(null, "999999999999999999999999999999"));
            for (String text : new String[]{null, "", " 1", "1 ", "+1", "\u0661"}) {
                assertEquals("false:999", run.invoke(null, text));
            }
        }
    }
}
