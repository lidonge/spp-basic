package com.servpp.generator.openapi;

import com.servpp.lang.*;
import com.servpp.lang.models.*;
import com.servpp.lang.general.IConstance;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmptyCatchGenerationTest {
    @Test public void businessExceptionHandlersAreRejectedBeforeGeneration() throws Exception {
        Path root = Files.createTempDirectory("spp-unsupported-catch");
        Path source = root.resolve("rules.spp");
        Files.writeString(source, """
                domain rules;
                Contract Facts { int value; }
                atomic update write(Facts facts);
                Scenario serial run(Facts facts) {
                    try { transaction { write(facts); } } catch (Facts facts) { }
                }
                """);
        var project = new SppProject();
        var error = assertThrows(java.io.IOException.class,
                () -> new SppCompiler(source.toFile(), project).compile());
        assertTrue(error.getMessage(), error.getMessage().contains("syntax error"));
        assertFalse(Files.exists(root.resolve("handler/Run.java")));
    }
    @Test public void templateGetterFailureStopsGenerationBeforeWritingHandler() throws Exception {
        var scenario = new SppScenario("Broken") {
            @Override public String getScenarioBodyJava() { throw new IllegalStateException("BODY_RENDER_FAILED"); }
        };
        scenario.setType(IConstance.CompilationUnitType.scenario);
        Path root = Files.createTempDirectory("spp-broken-body");
        var writer = new MustacheClassWriter(root.toFile(), scenario, "free.example", "rules");
        var error = assertThrows(java.io.IOException.class, writer::generate);
        assertTrue(error.getCause().getMessage().contains("BODY_RENDER_FAILED"));
        assertFalse(Files.exists(root.resolve("handler/Broken.java")));
    }
}
