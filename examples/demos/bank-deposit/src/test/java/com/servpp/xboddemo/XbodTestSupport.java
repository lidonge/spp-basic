package com.servpp.xboddemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.runtime.ScopeMetadataProvider;
import com.servpp.service.server.DefaultServiceContainer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Test-only input preparation and invocation. All bank rules execute in generated services. */
final class XbodTestSupport implements AutoCloseable {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final LocalGeneratedServiceRuntime runtime;
    final String previousUrl;
    final String previousLedgerUrl;
    XbodTestSupport() {
        this(2000);
    }
    XbodTestSupport(int lockTimeoutMillis) {
        this("jdbc:h2:mem:xbod_acceptance_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=" + lockTimeoutMillis,
                "jdbc:h2:mem:xbod_ledger_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=" + lockTimeoutMillis);
    }
    XbodTestSupport(String url, String ledgerUrl) {
        previousUrl = System.getProperty("spp.xbod.datasource-url");
        previousLedgerUrl = System.getProperty("spp.xbod.testing.ledger-url");
        System.setProperty("spp.xbod.testing.ledger-url", ledgerUrl);
        System.setProperty("spp.xbod.datasource-url", url);
        DefaultServiceContainer.getInstance().clear();
        runtime = LocalGeneratedServiceRuntime.jsonSnapshots(new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null), mapper, ignored -> null);
        runtime.enableExampleFaults();
        com.servpp.service.runtime.GeneratedApplicationDeployment.install(runtime, "xbodDeposit");
    }
    Object request(String service, Map<String, ?> input) throws Exception {
        Class<?> type = Class.forName("com.servpp.xbod.generated.cboddeposit.model." + Character.toUpperCase(service.charAt(0)) + service.substring(1) + "Request");
        Object request = mapper.convertValue(input, type);
        var metadata = (ScopeMetadataProvider) DefaultServiceContainer.getInstance().getService("cboddeposit", service);
        for (String name : metadata.getServiceParameterNames()) {
            var setter = Arrays.stream(type.getMethods()).filter(m -> m.getName().equalsIgnoreCase("set" + name) && m.getParameterCount() == 1).findFirst().orElseThrow();
            var getter = Arrays.stream(type.getMethods()).filter(m -> m.getName().equalsIgnoreCase("get" + name) && m.getParameterCount() == 0).findFirst().orElseThrow();
            Object participant = getter.invoke(request);
            if (participant == null) { participant = List.class.isAssignableFrom(setter.getParameterTypes()[0]) ? new ArrayList<>() : setter.getParameterTypes()[0].getConstructor().newInstance(); setter.invoke(request, participant); }
            defaults(participant, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        return request;
    }
    private void defaults(Object value, Set<Object> visited) throws Exception {
        if (value == null || !visited.add(value)) return;
        if (value instanceof Iterable<?> collection) { for (Object item : collection) defaults(item, visited); return; }
        if (!value.getClass().getPackageName().contains("generated.cboddeposit.model")) return;
        for (var setter : value.getClass().getMethods()) {
            if (!setter.getName().startsWith("set") || setter.getName().length() < 4 || !Character.isUpperCase(setter.getName().charAt(3)) || setter.getParameterCount() != 1) continue;
            var getter = value.getClass().getMethod("get" + setter.getName().substring(3));
            Object existing = getter.invoke(value); Class<?> type = setter.getParameterTypes()[0];
            if (existing != null) { defaults(existing, visited); continue; }
            Object initial = type == BigDecimal.class ? BigDecimal.ZERO : type == BigInteger.class ? BigInteger.ZERO : type == Integer.class ? 0 : type == Long.class ? 0L : type == Double.class ? 0d : type == Boolean.class ? false : type == String.class ? "" : type.isEnum() ? type.getEnumConstants()[0] : null;
            if (initial != null) setter.invoke(value, initial);
        }
    }
    JsonNode execute(String service, Object request) {
        com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(null);
        runtime.executeGeneratedService("cboddeposit." + service, request);
        var checked = com.servpp.service.runtime.BusinessChecks.scenarioResult();
        if (!checked.passed()) throw new IllegalStateException(checked.code());
        return mapper.valueToTree(request);
    }
    JsonNode call(String service, Map<String, ?> input) throws Exception { return execute(service, request(service, input)); }
    @Override public void close() {
        DefaultServiceContainer.getInstance().clear();
        if (previousUrl == null) System.clearProperty("spp.xbod.datasource-url"); else System.setProperty("spp.xbod.datasource-url", previousUrl);
        if (previousLedgerUrl == null) System.clearProperty("spp.xbod.testing.ledger-url"); else System.setProperty("spp.xbod.testing.ledger-url", previousLedgerUrl);
    }
}
