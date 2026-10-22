package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.service.Service;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.InvocationContext;
import com.servpp.service.runtime.ScopeMetadataProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class SystemServicesGenerationTest {
    @Test public void defaultsWrapOnlyUpdatesAndScenarios() throws Exception { verify(null, true, true); }
    @Test public void disabledRequestServicesGenerateNoSystemCoordinator() throws Exception { verify(false, false, false); }
    @Test public void duplicateGuardCanRemainEnabledWithoutRemoteIdempotency() throws Exception { verify(false, true, true); }
    @Test public void remoteIdempotencyCanRemainEnabledWithoutDuplicateGuard() throws Exception { verify(true, false, true); }

    private void verify(Boolean idempotency, boolean duplicateGuard, boolean otherServices) throws Exception {
        Path root = Files.createTempDirectory("system-services-generation"), source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("rules.spp"), """
                domain rules;
                Contract Facts { int value; }
                atomic query readFacts(Facts input);
                atomic check checkFacts(Facts input) { require(input.value >= 0, "NEGATIVE"); }
                atomic calculate calculateFacts(Facts input) { input.value *= 2; }
                atomic update changeFacts(Facts input);
                Scenario serial preview(Facts input) { checkFacts(input); }
                Scenario serial changeFlow(Facts input) { changeFacts(input); }
                """);
        Files.writeString(source.resolve("rules.app"), "domain rules; "
                + (idempotency == null ? "" : "system services { idempotency: " + idempotency
                    + "; duplicateGuard: " + duplicateGuard + "; propertyTracking: " + otherServices
                    + "; partialUpdate: " + otherServices + "; optimisticConcurrency: " + otherServices + "; } ")
                + "scope { local { readFacts; checkFacts; calculateFacts; changeFacts; duplicate key(input.value) facts(input) preview; changeFlow; } } "
                + "services { changeFacts { input.value += 1; } }");
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(), "generated");
        Path java = root.resolve("java/generated/rules"), models = java.resolve("model");
        boolean enabled = idempotency == null || idempotency;
        for (String name : List.of("ReadFacts", "CheckFacts", "CalculateFacts", "ChangeFacts", "Preview", "ChangeFlow")) {
            String implementation = Files.readString(java.resolve("service/Simple" + name + ".java"));
            boolean wrapped = name.equals("ChangeFacts") || name.equals("ChangeFlow") ? enabled
                    : name.equals("Preview") && duplicateGuard;
            assertEquals(name + ":\n" + implementation, wrapped, implementation.contains("BusinessIdempotencyManager"));
            if (wrapped) {
                assertTrue(implementation, implementation.contains("invocation.mode() == InvocationContext.Mode.REMOTE"));
                assertEquals(implementation, enabled && !name.equals("Preview"), implementation.contains("invocation.origin() == InvocationContext.Origin.SERVICE"));
                assertEquals(implementation, duplicateGuard && name.equals("Preview"), implementation.contains("invocation.origin() == InvocationContext.Origin.USER"));
            }
            Files.createDirectories(models);
            Files.writeString(models.resolve(name + "Request.java"), "package generated.rules.model; public class " + name
                    + "Request { private Facts input; public Facts getInput(){return input;} public void setInput(Facts value){input=value;} }");
            Files.writeString(models.resolve(name + "Response.java"), "package generated.rules.model; public class " + name + "Response { }");
        }
        var policies = new Properties();
        try (var in = Files.newInputStream(root.resolve("resources/META-INF/spp/app-ddl.properties"))) { policies.load(in); }
        var catalog = com.servpp.service.runtime.AppDdlCatalog.fromProperties(policies);
        assertFalse(catalog.propertyTracking()); // Basic generator carries no tracking implementation.
        assertEquals(otherServices, catalog.optimisticConcurrency());
        String schema = Files.readString(root.resolve("yaml/rules.spp.yaml"));
        assertFalse(schema.contains("x-spp-property-tracking: true"));
        Files.writeString(models.resolve("Facts.java"), "package generated.rules.model; public class Facts { private Integer value; public Integer getValue(){return value;} public void setValue(Integer x){value=x;} }");
        Path classes = Files.createDirectory(root.resolve("classes"));
        var args = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()));
        try (var files = Files.walk(root.resolve("java"))) { files.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString())); }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            var facts = loader.loadClass("generated.rules.model.Facts").getConstructor().newInstance();
            var setter = facts.getClass().getMethod("setValue", Integer.class);
            Service check = (Service) loader.loadClass("generated.rules.service.SimpleCheckFacts").getConstructor().newInstance();
            Object request = loader.loadClass("generated.rules.model.CheckFactsRequest").getConstructor().newInstance();
            request.getClass().getMethod("setInput", facts.getClass()).invoke(request, facts);
            try {
                setter.invoke(facts, -1);
                assertNotNull(check.execute(request));
                assertFalse(BusinessChecks.scenarioResult().passed());
                assertEquals("NEGATIVE", BusinessChecks.scenarioResult().code());
                setter.invoke(facts, 1);
                BusinessChecks.restoreScenarioResult(null);
                assertNotNull(check.execute(request));
                assertTrue(BusinessChecks.scenarioResult().passed());
                Service update = (Service) loader.loadClass("generated.rules.service.SimpleChangeFacts").getConstructor().newInstance();
                assertEquals(enabled, ((ScopeMetadataProvider) update).isSystemIdempotencyEnabled());
                assertEquals(otherServices, ((ScopeMetadataProvider) update).isSystemPartialUpdateEnabled());
                var updateRequest = loader.loadClass("generated.rules.model.ChangeFactsRequest").getConstructor().newInstance();
                updateRequest.getClass().getMethod("setInput", facts.getClass()).invoke(updateRequest, facts);
                InvocationContext.remote(InvocationContext.Origin.USER, "caller", "serial", () -> update.execute(updateRequest));
                assertEquals(2, facts.getClass().getMethod("getValue").invoke(facts));
                assertEquals(InvocationContext.Mode.LOCAL, InvocationContext.current().mode());
                var container = com.servpp.service.server.DefaultServiceContainer.getInstance();
                Service previousCheck = container.getService("checkFacts"), previousUpdate = container.getService("changeFacts");
                var previousStore = com.servpp.service.idempotency.BusinessIdempotencyManager.getStore();
                try {
                    container.registerService("checkFacts", check); container.registerService("changeFacts", update);
                    com.servpp.service.idempotency.BusinessIdempotencyManager.setStore(null);
                    var preview = (Service) loader.loadClass("generated.rules.service.SimplePreview").getConstructor().newInstance();
                    var previewRequest = loader.loadClass("generated.rules.model.PreviewRequest").getConstructor().newInstance();
                    previewRequest.getClass().getMethod("setInput", facts.getClass()).invoke(previewRequest, facts);
                    com.servpp.service.idempotency.BusinessIdempotencyManager.resetReplay();
                    for (int index = 0; index < 2; index++) {
                        assertNotNull(duplicateGuard ? preview.execute(previewRequest)
                                : InvocationContext.remote(InvocationContext.Origin.USER, "caller", "serial", () -> preview.execute(previewRequest)));
                        assertFalse(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
                    }
                    var flow = (Service) loader.loadClass("generated.rules.service.SimpleChangeFlow").getConstructor().newInstance();
                    var flowRequest = loader.loadClass("generated.rules.model.ChangeFlowRequest").getConstructor().newInstance();
                    flowRequest.getClass().getMethod("setInput", facts.getClass()).invoke(flowRequest, facts);
                    assertNotNull(InvocationContext.remote(InvocationContext.Origin.USER, "caller", "serial", () -> flow.execute(flowRequest)));
                    assertEquals(3, facts.getClass().getMethod("getValue").invoke(facts));
                } finally {
                    com.servpp.service.idempotency.BusinessIdempotencyManager.setStore(previousStore);
                    var field = container.getClass().getDeclaredField("serviceMap"); field.setAccessible(true);
                    @SuppressWarnings("unchecked") var services = (Map<String, Service>) field.get(container);
                    if (previousCheck == null) services.remove("checkFacts"); else services.put("checkFacts", previousCheck);
                    if (previousUpdate == null) services.remove("changeFacts"); else services.put("changeFacts", previousUpdate);
                }
            } finally { BusinessChecks.restoreScenarioResult(null); }
        }
    }
}
