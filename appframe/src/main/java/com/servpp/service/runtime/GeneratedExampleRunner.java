package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.server.DefaultServiceContainer;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Runs APP-declared scenarios; fixtures and assertions execute in generated SPP services. */
public final class GeneratedExampleRunner {
    private GeneratedExampleRunner() {}

    public static void main(String[] args) throws Exception {
        Properties examples = new Properties();
        try (var input = GeneratedExampleRunner.class.getResourceAsStream("/META-INF/spp/examples.properties")) {
            if (input == null) throw new IllegalStateException("Generated APP examples catalog is missing.");
            examples.load(input);
        }
        int count = Integer.parseInt(examples.getProperty("example.count", "0"));
        boolean child = args.length == 3 && "--spp-example-phase".equals(args[0]);
        int phase = child ? Integer.parseInt(args[2]) : -1;
        Set<String> selected = new LinkedHashSet<>(Arrays.asList(args));
        List<Integer> entries = new ArrayList<>();
        if (child) {
            int index = Integer.parseInt(args[1]);
            if (index < 0 || index >= count || phase < 0 || phase > 1
                || examples.getProperty("example." + index + ".restart.service") == null) {
                throw new IllegalArgumentException("Undeclared APP restart stage");
            }
            entries.add(index); selected.clear();
        } else {
        for (int index = 0; index < count; index++) {
            String name = examples.getProperty("example." + index + ".service");
            if (args.length == 0 || selected.remove(name)) entries.add(index);
        }
        }
        if (!selected.isEmpty()) throw new IllegalArgumentException("Undeclared APP examples: " + selected);
        if (entries.isEmpty()) throw new IllegalArgumentException("No APP examples are declared.");

        var source = new DriverManagerJdbcDataSource(
                System.getProperty("spp.runtime.jdbc.driver", "org.h2.Driver"),
                System.getProperty("spp.runtime.jdbc.url", "jdbc:h2:mem:spp_examples_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"),
                System.getProperty("spp.runtime.jdbc.username"), System.getProperty("spp.runtime.jdbc.password"));
        var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, new ObjectMapper().findAndRegisterModules(), ignored -> null);
        if (GeneratedExampleRunner.class.getResource("/META-INF/spp/physical.properties") != null) {
            runtime.loadPhysicalRuntimeCatalog("/META-INF/spp/physical.properties");
        }
        if (examples.stringPropertyNames().stream().anyMatch(key -> key.endsWith(".fault.service") || key.endsWith(".barrier.service") || key.endsWith(".jdbcFault.service"))) runtime.enableExampleFaults();
        runtime.registerGeneratedServices("/META-INF/spp/services.properties");
        String configuredEvidence = System.getProperty("spp.examples.evidenceDirectory", "");
        Path evidenceDirectory = configuredEvidence.isBlank() ? null : Path.of(configuredEvidence);
        int failures = 0;
        for (int index : entries) {
            String prefix = "example." + index + ".";
            String name = examples.getProperty(prefix + "service");
            boolean restarting = !child && examples.getProperty(prefix + "restart.service") != null;
            Object request = null;
            Object result = null;
            try {
                if (restarting) {
                    ExampleProcesses.run(examples, index);
                    if (evidenceDirectory != null) { ExampleEvidenceWriter.write(evidenceDirectory, name + "__restart", null,
                            java.util.Map.of("completedStages", 2), null); }
                    System.out.println("PASS " + name);
                    continue;
                }
                String stagePrefix = child && phase == 1 ? prefix + "restart." : prefix;
                name = examples.getProperty(stagePrefix + "service");
                Service service = DefaultServiceContainer.getInstance().getService(name);
                if (service == null) throw new IllegalStateException("Generated service is missing: " + name);
                request = Class.forName(examples.getProperty(stagePrefix + "request")).getConstructor().newInstance();
                String participants = examples.getProperty(stagePrefix + "participants", "");
                for (String participant : participants.split(",")) {
                    if (!participant.isBlank()) initializeProperty(request, participant);
                }
                if (service instanceof ScopeMetadataProvider metadata && metadata.getInputHeaderName() != null) {
                    Object header = initializeProperty(request, "appHeader");
                    if (metadata.hasIdempotencyKeyField()) {
                        setter(header, metadata.getIdempotencyKeyFieldName()).invoke(header, name + ":" + UUID.randomUUID());
                    }
                }
                try (var fault = ExampleServiceFaults.activate(examples.getProperty(prefix + "fault.service"),
                        examples.getProperty(prefix + "fault.timing"),
                        Integer.parseInt(examples.getProperty(prefix + "fault.times", "1")),
                        examples.getProperty(prefix + "fault.afterService"));
                        var barrier = ExampleServiceBarrier.activate(examples.getProperty(prefix + "barrier.service"),
                            Integer.parseInt(examples.getProperty(prefix + "barrier.parties", "0")));
                        var jdbcFault = ExampleJdbcFaults.activate(examples.getProperty(prefix + "jdbcFault.service"),
                            examples.getProperty(prefix + "jdbcFault.point"))) {
                    result = service.execute(request);
                    fault.verifyTriggered();
                    barrier.verifyReached();
                    jdbcFault.verifyTriggered();
                }
                if (evidenceDirectory != null) { ExampleEvidenceWriter.write(evidenceDirectory, name, request, result, null); }
                System.out.println("PASS " + name);
            } catch (Exception | Error failure) {
                failures++;
                if (evidenceDirectory != null) {
                    try { ExampleEvidenceWriter.write(evidenceDirectory, restarting ? name + "__restart" : name, request, result, failure); }
                    catch (Exception exportFailure) { if (exportFailure != failure) { failure.addSuppressed(exportFailure); } }
                }
                System.err.println("FAIL " + name + ": " + failure);
                failure.printStackTrace(System.err);
            }
        }
        System.out.println("APP examples: " + (entries.size() - failures) + " passed, " + failures + " failed");
        if (failures != 0) throw new IllegalStateException("APP examples failed: " + failures);
    }

    private static Object initializeProperty(Object owner, String name) throws ReflectiveOperationException {
        Method setter = setter(owner, name);
        Object value = setter.getParameterTypes()[0].getConstructor().newInstance();
        setter.invoke(owner, value);
        return value;
    }

    private static Method setter(Object owner, String name) throws NoSuchMethodException {
        String methodName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (Method method : owner.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) return method;
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + methodName);
    }
}
