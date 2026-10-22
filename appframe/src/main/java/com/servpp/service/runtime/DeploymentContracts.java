package com.servpp.service.runtime;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Validates generated deployment contracts after resolving runtime resource overrides. */
public final class DeploymentContracts {
    private DeploymentContracts() { }

    public static void validate(Properties applications, String prefix, Map<String, Properties> domains) {
        String environment = applications.getProperty(prefix + "environment");
        if (environment == null) return; // Historical catalogs have no environment contract.
        String selected = System.getProperty("spp.environment", environment);
        if (!selected.equals(environment)) throw new IllegalStateException("APP_DEPLOYMENT_ENVIRONMENT_MISMATCH");
        String mode = applications.getProperty(prefix + "transactionMode");
        String system = applications.getProperty(prefix + "quantumSystem");
        String quantumResource = applications.getProperty(prefix + "quantumResource");
        Set<String> localStores = new LinkedHashSet<>(), quantumStores = new LinkedHashSet<>(), coordinators = new LinkedHashSet<>();
        for (var domain : domains.entrySet()) {
            Properties physical = domain.getValue();
            for (String name : Set.of("environment", "transactionMode", "quantumSystem", "quantumResource")) {
                if (!Objects.equals(applications.getProperty(prefix + name), physical.getProperty("deployment." + name)))
                    throw new IllegalStateException("APP_DOMAIN_DEPLOYMENT_CONTRACT_MISMATCH: " + domain.getKey() + ":" + name);
            }
            if (Set.of("XA","SEATA_AT").contains(mode)) {
                String resource=applications.getProperty(prefix+"transactionResource");
                boolean found=false;
                for (String backend : list(physical,"backends")) {
                    String root="backend."+backend+".";
                    String binding=("XA".equals(mode)?"xa:":"at:");
                    Set<String> bindings=list(physical,root+"bindings");
                    if (!bindings.contains(binding+resource) && !bindings.contains(binding+"*")) continue;
                    if (!"seata".equals(physical.getProperty(root+"adapter")))
                        throw new IllegalStateException("DISTRIBUTED_TRANSACTION_SEATA_BACKEND_REQUIRED");
                    if ("XA".equals(mode) && !"XA".equals(value(physical,root+"config.datasourceProxyMode","")))
                        throw new IllegalStateException("XA_DATASOURCE_PROXY_REQUIRED");
                    String identity=value(physical,root+"config.transactionServiceGroup","");
                    if (identity.isBlank()) throw new IllegalStateException("TRANSACTION_COORDINATION_IDENTITY_REQUIRED");
                    coordinators.add(identity); found=true;
                }
                if (!found) throw new IllegalStateException("DISTRIBUTED_TRANSACTION_RESOURCE_REQUIRED: "+domain.getKey());
            }
            if ("LOCAL".equals(mode)) {
                boolean backendFound = false;
                for (String backend : list(physical, "backends")) {
                    backendFound = true;
                    String root = "backend." + backend + ".";
                    if (!Set.of("localJdbc", "local-jdbc").contains(physical.getProperty(root + "adapter", "")))
                        throw new IllegalStateException("LOCAL_REQUIRES_LOCAL_JDBC_BACKEND: " + domain.getKey());
                    localStores.add(identity(physical, root + "config."));
                }
                if (!backendFound && !"false".equals(physical.getProperty("domain." + domain.getKey() + ".requiresLocalStorage")))
                    throw new IllegalStateException("LOCAL_PHYSICAL_BACKEND_REQUIRED: " + domain.getKey());
                for (String repository : list(physical, "repositories")) localStores.add(identity(physical, "repository." + repository + ".config."));
                for (String store : list(physical, "quantumStores")) localStores.add(identity(physical, "quantumStore." + store + ".config."));
            }
            if ("UNIFIED_STORE".equals(system) && !list(physical, "quantumStores").isEmpty()) {
                String root = "quantumStore." + quantumResource + ".";
                if (!"jdbcQuantum".equals(physical.getProperty(root + "adapter")))
                    throw new IllegalStateException("UNIFIED_QUANTUM_STORE_ADAPTER_REQUIRED: " + domain.getKey());
                quantumStores.add(identity(physical, root + "config.") + "|"
                        + value(physical, root + "config.valueTable", "spp_quantum_value") + "|"
                        + value(physical, root + "config.journalTable", "spp_quantum_journal"));
            }
            if ("UNIFIED_SERVICE".equals(system) && !list(physical, "quantumStores").isEmpty()) {
                String root = "quantumStore." + quantumResource + ".";
                if (!"quantumService".equals(physical.getProperty(root + "adapter")))
                    throw new IllegalStateException("UNIFIED_QUANTUM_SERVICE_ADAPTER_REQUIRED: " + domain.getKey());
                quantumStores.add(value(physical, root + "config.endpoint", "") + "|"
                        + value(physical, root + "config.namespace", "") + "|"
                        + value(physical, root + "config.serviceProtocol", ""));
            }
            if ("TCC".equals(system) && !list(physical, "quantumStores").isEmpty()) {
                String root = "quantumStore." + quantumResource + ".";
                if (!"tccObjectStore".equals(physical.getProperty(root + "adapter")))
                    throw new IllegalStateException("QUANTUM_TCC_STORE_REQUIRED: " + domain.getKey());
                for (String backend : list(physical, "backends"))
                    if ("seata".equals(physical.getProperty("backend." + backend + ".adapter")))
                        quantumStores.add(value(physical, "backend." + backend + ".config.transactionServiceGroup", ""));
            }
        }
        if ("LOCAL".equals(mode) && localStores.size() != 1)
            throw new IllegalStateException("LOCAL_REQUIRES_SHARED_DATABASE_SCHEMA_AND_RESOURCE");
        if (Set.of("XA","SEATA_AT").contains(mode) && coordinators.size()!=1)
            throw new IllegalStateException("APP_TRANSACTION_COORDINATION_IDENTITY_MISMATCH");
        if (quantumStores.size() > 1) throw new IllegalStateException("APP_QUANTUM_SYSTEM_IDENTITY_MISMATCH");
    }

    private static String identity(Properties values, String prefix) {
        String url = value(values, prefix + "datasourceUrl", "");
        if (url.isBlank() || url.contains("${")) throw new IllegalStateException("APP_DATASOURCE_IDENTITY_REQUIRED");
        String user=value(values,prefix+"username",value(values,prefix+"user",""));
        String password=value(values,prefix+"password","");
        String credentials;
        try {
            credentials=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    (user.length()+":"+user+password.length()+":"+password).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return url + "|" + value(values, prefix + "schema", "<default>") + "|"
                + value(values, prefix + "driverClassName", "")+"|"+credentials;
    }
    private static String value(Properties values, String key, String defaultValue) {
        return PhysicalRuntimeCatalog.resolveConfigValue(values.getProperty(key, defaultValue));
    }
    private static Set<String> list(Properties values, String key) {
        Set<String> result = new LinkedHashSet<>();
        for (String item : values.getProperty(key, "").split(",")) if (!item.isBlank()) result.add(item.trim());
        return result;
    }
}
