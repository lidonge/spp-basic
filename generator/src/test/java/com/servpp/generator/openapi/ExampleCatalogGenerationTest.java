package com.servpp.generator.openapi;

import com.servpp.lang.*;
import com.servpp.lang.models.*;
import java.nio.file.*;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleCatalogGenerationTest {
    @Test public void catalogPreservesDeclaredOrderAndGeneratedRequestNames() throws Exception {
        Path root = Files.createTempDirectory("spp-example-catalog");
        Path spp = root.resolve("sample.spp"), app = root.resolve("sample.app");
        Files.writeString(spp, "domain sample; Contract Facts { int value; } atomic update save(Facts facts); atomic calculate init(Facts facts) {} Scenario serial first(Facts facts) { init(facts); } Scenario serial second(Facts facts) { init(facts); } Scenario serial setup(Facts facts) { init(facts); } Scenario serial jdbcCase(Facts facts) { save(facts); }");
        Files.writeString(app, "domain sample; scope { local { save; init; first; second; setup; jdbcCase; } } web { expose first; start setup; } examples { run second fault after init after save times 2; run first barrier before init 2; run setup restart second using spp.sample.datasource; run jdbcCase jdbcFault commitAfter save; }");
        var project = new SppProject();
        new SppCompiler(spp.toFile(), project).compile();
        new AppCompiler(app.toFile(), project).compile();
        var domain = project.getDomain("sample");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        Path java = root.resolve("java"); Files.createDirectories(java);
        var generator = new OpenApiGenerator();
        generator.generate(domain, java.toFile(), "free.example.generated");
        generator.createServicesProperties(java.toFile());
        Properties catalog = new Properties();
        try (var input = Files.newInputStream(root.resolve("resources/META-INF/spp/examples.properties"))) { catalog.load(input); }
        Properties web = new Properties();
        try (var input = Files.newInputStream(root.resolve("resources/META-INF/spp/web.properties"))) { web.load(input); }
        assertEquals("1", web.getProperty("expose.count"));
        assertEquals("first", web.getProperty("expose.0.service"));
        assertEquals("free.example.generated.sample.model.FirstRequest", web.getProperty("expose.0.request"));
        assertEquals("setup", web.getProperty("start.0.service"));
        assertEquals("facts", web.getProperty("start.0.participants"));
        assertEquals("4", catalog.getProperty("example.count"));
        assertEquals("second", catalog.getProperty("example.0.service"));
        assertEquals("free.example.generated.sample.model.SecondRequest", catalog.getProperty("example.0.request"));
        assertEquals("facts", catalog.getProperty("example.0.participants"));
        assertEquals("first", catalog.getProperty("example.1.service"));
        assertEquals("init", catalog.getProperty("example.0.fault.service"));
        assertEquals("after", catalog.getProperty("example.0.fault.timing"));
        assertEquals("2", catalog.getProperty("example.0.fault.times"));
        assertEquals("save", catalog.getProperty("example.0.fault.afterService"));
        assertNull(catalog.getProperty("example.1.fault.service"));
        assertEquals("init", catalog.getProperty("example.1.barrier.service"));
        assertEquals("2", catalog.getProperty("example.1.barrier.parties"));
        assertNull(catalog.getProperty("example.0.barrier.service"));
        assertEquals("second", catalog.getProperty("example.2.restart.service"));
        assertEquals("free.example.generated.sample.model.SecondRequest", catalog.getProperty("example.2.restart.request"));
        assertEquals("facts", catalog.getProperty("example.2.restart.participants"));
        assertEquals("spp.sample.datasource", catalog.getProperty("example.2.restart.datasourceProperty"));
        assertEquals("save", catalog.getProperty("example.3.jdbcFault.service"));
        assertEquals("commitAfter", catalog.getProperty("example.3.jdbcFault.point"));
    }
}
