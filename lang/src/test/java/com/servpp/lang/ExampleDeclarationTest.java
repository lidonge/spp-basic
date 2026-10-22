package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleDeclarationTest {
    private SppDomain compile(String examples) throws Exception {
        Path spp = Files.createTempFile("spp-examples", ".spp");
        Path app = Files.createTempFile("spp-examples", ".app");
        try {
            Files.writeString(spp, "domain sample; Contract Facts { int value; } atomic update save(Facts facts); atomic calculate init(Facts facts) {} Scenario serial sample(Facts facts) { init(facts); } Scenario serial resume(Facts facts) { init(facts); }");
            Files.writeString(app, "domain sample; scope { local { save; init; sample; resume; } } " + examples);
            var project = new SppProject();
            new SppCompiler(spp.toFile(), project).compile();
            new AppCompiler(app.toFile(), project).compile();
            return project.getDomain("sample");
        } finally { Files.deleteIfExists(spp); Files.deleteIfExists(app); }
    }
    @Test public void acceptsLocalScene() throws Exception {
        var domain = compile("examples { run sample; }");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertEquals(java.util.List.of("sample"), domain.getRuleBlock().getExampleServiceNames());
    }
    @Test public void rejectsUndefinedAtomicAndDuplicate() throws Exception {
        for (String declarations : java.util.List.of("run missing;", "run init;", "run sample; run sample;")) {
            assertTrue(compile("examples { " + declarations + " }").validateAppDeclarations().stream().anyMatch(message -> message.startsWith("APP example")));
        }
    }
    @Test public void acceptsOneShotAtomicFault() throws Exception {
        for (String timing : java.util.List.of("before", "after")) {
            var domain = compile("examples { run sample fault " + timing + " init; }");
            assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
            assertEquals(timing, domain.getRuleBlock().getExampleFaults().get("sample").timing());
        }
    }
    @Test public void acceptsRepeatedFaultAfterSuccessfulAtomicAnchorAndRejectsInvalidControls() throws Exception {
        var domain = compile("examples { run sample fault before save after init times 2; }");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        var fault = domain.getRuleBlock().getExampleFaults().get("sample");
        assertEquals("save", fault.serviceName()); assertEquals("init", fault.afterServiceName()); assertEquals(2, fault.times());
        for (String modifier : java.util.List.of("fault before init times 0", "fault before init times 999999999999999999999",
                "fault before init after missing", "fault before init after sample")) {
            assertTrue(compile("examples { run sample " + modifier + "; }").validateAppDeclarations().stream()
                    .anyMatch(message -> message.contains("fault")));
        }
    }
    @Test public void rejectsInvalidFaultTimingAndTargets() throws Exception {
        for (String modifier : java.util.List.of("fault during init", "fault after missing", "fault after sample")) {
            assertTrue(compile("examples { run sample " + modifier + "; }").validateAppDeclarations().stream()
                .anyMatch(message -> message.contains("fault")));
        }
    }
    @Test public void acceptsBoundedAtomicBarrierAndRejectsInvalidTargetsAndCounts() throws Exception {
        var domain = compile("examples { run sample barrier before init 2; }");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertEquals(2, domain.getRuleBlock().getExampleBarriers().get("sample").parties());
        for (String modifier : java.util.List.of("barrier before init 1", "barrier before init 65", "barrier before missing 2",
                "barrier before sample 2", "fault before init barrier before init 2")) {
            assertTrue(compile("examples { run sample " + modifier + "; }").validateAppDeclarations().stream()
                .anyMatch(message -> message.contains("barrier")));
        }
    }
    @Test public void acceptsFreshProcessStagesAndRejectsAtomicUnknownOrAmbiguousRestart() throws Exception {
        var domain = compile("examples { run sample restart resume using spp.sample.datasource; }");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertEquals("spp.sample.datasource", domain.getRuleBlock().getExampleRestarts().get("sample").datasourceProperty());
        var quoted = compile("examples { run sample restart resume using \"spp.sample.datasource-url\"; }");
        assertTrue(quoted.validateAppDeclarations().toString(), quoted.validateAppDeclarations().isEmpty());
        assertEquals("spp.sample.datasource-url", quoted.getRuleBlock().getExampleRestarts().get("sample").datasourceProperty());
        for (String modifier : java.util.List.of("restart missing using spp.sample.datasource", "restart init using spp.sample.datasource",
                "fault before init restart resume using spp.sample.datasource", "barrier before init 2 restart resume using spp.sample.datasource",
                "restart resume using \"\"")) {
            assertTrue(compile("examples { run sample " + modifier + "; }").validateAppDeclarations().stream()
                .anyMatch(message -> message.contains("restart")));
        }
    }
    @Test public void acceptsJdbcLifecyclePointsAndRejectsInvalidOrAmbiguousBindings() throws Exception {
        for (String point : java.util.List.of("beforeCommit", "commitBefore", "commitAfter", "afterCommit")) {
            var domain = compile("examples { run sample jdbcFault " + point + " save; }");
            assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
            assertEquals(point, domain.getRuleBlock().getExampleJdbcFaults().get("sample").point());
        }
        for (String modifier : java.util.List.of("jdbcFault unknown save", "jdbcFault commitBefore missing", "jdbcFault commitBefore sample",
            "jdbcFault commitBefore init", "fault before init jdbcFault beforeCommit save",
            "barrier before init 2 jdbcFault beforeCommit save", "restart resume using spp.sample.url jdbcFault beforeCommit save")) {
            assertTrue(compile("examples { run sample " + modifier + "; }").validateAppDeclarations().stream()
                .anyMatch(message -> message.contains("JDBC fault")));
        }
    }
}
