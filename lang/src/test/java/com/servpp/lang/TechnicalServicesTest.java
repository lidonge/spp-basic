package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TechnicalServicesTest {
    private SppDomain compile(String app, String phy) throws Exception {
        var directory = Files.createTempDirectory("spp-technical-services");
        try {
            var source = directory.resolve("demo.spp");
            Files.writeString(source, """
                    domain demo;
                    Role Buyer { String name; }
                    atomic calculate compute(Buyer) { Buyer.name = "computed"; }
                    Scenario serial preview(Buyer) { findBuyer; compute; }
                    """);
            var project = new SppProject(); new SppCompiler(source.toFile(), project).compile();
            for (var file : Map.of("demo.app", app, "demo.phy", phy).entrySet()) {
                var path = directory.resolve(file.getKey()); Files.writeString(path, "domain demo; " + file.getValue());
                new AppCompiler(path.toFile(), project).compile();
            }
            return project.getDomain("demo");
        } finally {
            try (var files = Files.walk(directory)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    private String implementation(String name) {
        return "technical service " + name + " { implementation: \"com.servpp.service.technical.InvocationTraceService\"; config logger = \"spp.test\"; }";
    }
    @Test public void allServicesIncludeImplicitCrudAndGeneratedMetadata() throws Exception {
        var domain = compile("technical services { trace; } intercept services { before: trace; after: trace; failure: trace; }",
                "phy { " + implementation("trace") + " }");
        assertEquals(List.of(), domain.validateAppDeclarations());
        for (String name : List.of("Compute", "FindBuyer", "Preview")) {
            var service = (SppService)domain.getSppClass(name);
            assertNotNull(name, service); assertEquals(3, service.getTechnicalBindings().size());
            assertTrue(service.getGeneratedServiceKindMethods().contains("getTechnicalInterceptors"));
        }
        assertTrue(((SppService)domain.getSppClass("Preview")).getScenarioBodyJava().contains("executeWithTechnicalInterceptors"));
    }
    @Test public void sceneAndExplicitMatchingDoNotIncludeOtherServices() throws Exception {
        var domain = compile("technical services { trace; } intercept scenarios { before: trace; } intercept compute { after: trace; }",
                "phy { " + implementation("trace") + " }");
        assertEquals(List.of(), domain.validateAppDeclarations());
        assertEquals(1, ((SppService)domain.getSppClass("Preview")).getTechnicalBindings().size());
        assertEquals(1, ((SppService)domain.getSppClass("Compute")).getTechnicalBindings().size());
        assertEquals(0, ((SppService)domain.getSppClass("FindBuyer")).getTechnicalBindings().size());
    }
    @Test public void rejectsUnknownTargetsPhasesAndMissingImplementations() throws Exception {
        var domain = compile("technical services { trace; } intercept Buyer, missing { later: trace, unknown; }", "");
        String errors = domain.validateAppDeclarations().toString();
        assertTrue(errors, errors.contains("requires a PHY implementation"));
        assertTrue(errors, errors.contains("Unknown interception phase later"));
        assertTrue(errors, errors.contains("Unknown interception service Buyer"));
        assertTrue(errors, errors.contains("Unknown interception service missing"));
        assertTrue(errors, errors.contains("Undeclared technical service unknown"));
    }
    @Test public void rejectsDuplicateMatchingAndDefinitions() throws Exception {
        var domain = compile("technical services { trace; trace; } intercept services { before: trace; } intercept preview { before: trace; }",
                "phy { " + implementation("trace") + implementation("trace") + " }");
        String errors = domain.validateAppDeclarations().toString();
        assertTrue(errors, errors.contains("Duplicate technical service"));
        assertTrue(errors, errors.contains("Duplicate technical implementation"));
        assertTrue(errors, errors.contains("Duplicate interception preview before trace"));
    }
    @Test public void physicalEnvironmentsSelectOnlyTheirOwnImplementation() throws Exception {
        var domain = compile("technical services { trace; } intercept compute { before: trace; }", "phy { environment develop { "
                + implementation("trace") + " } environment testing { " + implementation("trace").replace("spp.test", "spp.testing") + " } }");
        assertEquals(List.of(), domain.validateAppDeclarations());
        assertEquals("spp.test", domain.getRuleBlock().getTechnicalServices().implementations().get("trace").configuration().get("logger"));
    }
    @Test public void unconfiguredServicesRemainCompatible() throws Exception {
        var domain = compile("", ""); assertEquals(List.of(), domain.validateAppDeclarations());
        assertFalse(((SppService)domain.getSppClass("Compute")).getGeneratedServiceKindMethods().contains("getTechnicalInterceptors"));
        assertFalse(((SppService)domain.getSppClass("Preview")).getScenarioBodyJava().contains("executeWithTechnicalInterceptors"));
    }
}
