package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.lang.models.SppDomain;
import com.servpp.service.technical.ServiceInterceptors;
import com.servpp.service.technical.TechnicalInvocation;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class OptionalEnhancementsGenerationTest {
    private record Generated(Path root, SppDomain domain, String warnings) { }

    private Generated generate(String switches, String adapter) throws Exception {
        Path root = Files.createTempDirectory("optional-enhancements");
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("demo.spp"), """
                domain demo;
                Contract Facts { int value; }
                atomic calculate compute(Facts) { Facts.value += 1; }
                Scenario serial preview(Facts) { compute; }
                """);
        Files.writeString(source.resolve("demo.app"), "domain demo; " + switches + """
                technical services { trace; custom; }
                intercept services { before: trace, custom; }
                """);
        Files.writeString(source.resolve("demo.phy"), "domain demo; phy { " + adapter + """
                technical service trace { implementation: "com.servpp.service.technical.InvocationTraceService"; }
                technical service custom { implementation: "example.RequiredAudit"; }
                }
                """);
        var domain = new AtomicReference<SppDomain>();
        var diagnostics = new ByteArrayOutputStream();
        var originalOutput = System.out;
        try (var captured = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(),
                    "generated", null, project -> domain.set(project.getDomain("demo")));
        } finally {
            System.setOut(originalOutput);
        }
        return new Generated(root, domain.get(), diagnostics.toString(StandardCharsets.UTF_8));
    }

    private Properties catalog(Generated generated, String name) throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(generated.root().resolve("resources/META-INF/spp/" + name))) {
            properties.load(input);
        }
        return properties;
    }

    @Test public void absentOptionalObserverIsOmittedButCustomBindingsRemainRequired() throws Exception {
        var generated = generate("system services { idempotency: false; duplicateGuard: false; propertyTracking: false; partialUpdate: false; }", "");
        assertTrue(generated.warnings(), generated.warnings().contains("feature=trace; invocation tracing is not generated"));
        assertFalse(generated.warnings().contains("feature=idempotency"));
        assertFalse(generated.warnings().contains("feature=propertyTracking"));
        assertFalse(generated.warnings().contains("feature=partialUpdate"));
        assertEquals("custom", catalog(generated, "technical-services.properties").getProperty("services"));
        assertEquals("true", catalog(generated, "app-ddl.properties").getProperty("optimisticConcurrency"));
        try (var files = Files.walk(generated.root().resolve("java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(file.toString(), Files.readString(file).contains("com.servpp.service.technical.InvocationTraceService"));
            }
        }
        assertThrows(IllegalStateException.class, () -> ServiceInterceptors.create(getClass().getClassLoader(),
                List.of(new ServiceInterceptors.Binding(TechnicalInvocation.Phase.BEFORE, "custom", "example.RequiredAudit", Map.of()))));
    }

    @Test public void requestAndSchedulingFallbacksAreExplicitAndCustomAdaptersAreNotDiscarded() throws Exception {
        var generated = generate("", "config requestIdempotencyAdapter = \"memory\";");
        for (String feature : List.of("idempotency", "duplicateGuard", "partialUpdate", "requestIdempotencyAdapter")) {
            assertTrue(generated.warnings(), generated.warnings().contains("feature=" + feature + ";"));
        }
        assertEquals("jdbc", catalog(generated, "physical.properties").getProperty("config.requestIdempotencyAdapter"));
        generated.domain().getRuleBlock().setOrdinaryUpdates("PARALLEL");
        assertTrue(new OptionalEnhancements().warnings(generated.domain()).toString().contains("automatic update scheduling uses serial execution"));
        assertEquals("example.RequiredRequestStore", new OptionalEnhancements().requestAdapter("example.RequiredRequestStore"));
    }

    @Test public void installedObserverIsRetainedWithoutAnAbsenceWarning() throws Exception {
        Path root = Files.createTempDirectory("optional-observer-provider");
        Path source = root.resolve("InvocationTraceService.java");
        Files.writeString(source, """
                package com.servpp.service.technical;
                public final class InvocationTraceService implements TechnicalService {
                    public void handle(TechnicalInvocation invocation) { }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", System.getProperty("java.class.path"), "-d", root.toString(), source.toString()));
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()}, previous)) {
            Thread.currentThread().setContextClassLoader(loader);
            var generated = generate("", "");
            assertFalse(generated.warnings().contains("feature=trace;"));
            assertEquals("trace,custom", catalog(generated, "technical-services.properties").getProperty("services"));
            assertTrue(new OptionalEnhancements(loader).omittedServices(generated.domain()).isEmpty());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
