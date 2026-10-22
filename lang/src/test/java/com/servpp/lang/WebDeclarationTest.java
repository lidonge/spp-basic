package com.servpp.lang;
import java.nio.file.Files;
import com.servpp.lang.models.SppProject;
import org.junit.Test;
import static org.junit.Assert.*;
public class WebDeclarationTest {
    private java.util.List<String> validate(String web) throws Exception {
        var root = Files.createTempDirectory("web-declaration");
        var spp = root.resolve("sample.spp"); var app = root.resolve("sample.app");
        Files.writeString(spp, "domain sample; Contract Work { int value; } atomic calculate init(Work work) {} Scenario serial route(Work work) { init(work); }");
        Files.writeString(app, "domain sample; scope { local { init; route; } } web { " + web + " }");
        var project = new SppProject(); new SppCompiler(spp.toFile(), project).compile(); new AppCompiler(app.toFile(), project).compile();
        return project.getDomain("sample").validateAppDeclarations();
    }
    @Test public void onlyDeclaredLocalScenariosMayBeExposedOrStarted() throws Exception {
        assertTrue(validate("expose route; start route;").isEmpty());
        assertFalse(validate("expose init;").isEmpty());
        assertFalse(validate("start absent;").isEmpty());
        assertFalse(validate("expose route; expose route;").isEmpty());
        assertFalse(validate("publish route;").isEmpty());
    }
}
