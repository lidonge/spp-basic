package com.servpp.service.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public record PhysicalRuntimeCatalog(List<PhysicalBackend> backends,
                                     List<RemoteAdapter> remoteAdapters,
                                     List<RepositoryAdapter> repositoryAdapters,
                                     List<QuantumStore> quantumStores,
                                     String physicalProfile,
                                     boolean physicalProfileExplicit,
                                     List<String> physicalSourceFiles,
                                     Map<String, String> configuration) {
    public PhysicalRuntimeCatalog {
        backends = backends == null ? List.of() : List.copyOf(backends);
        remoteAdapters = remoteAdapters == null ? List.of() : List.copyOf(remoteAdapters);
        repositoryAdapters = repositoryAdapters == null ? List.of() : List.copyOf(repositoryAdapters);
        quantumStores = quantumStores == null ? List.of() : List.copyOf(quantumStores);
        physicalProfile = normalizeCatalogValue(physicalProfile);
        physicalSourceFiles = normalizeCatalogValues(physicalSourceFiles);
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }

    public static PhysicalRuntimeCatalog empty() {
        return new PhysicalRuntimeCatalog(List.of(), List.of(), List.of(), List.of(), "", false,
                List.of(), Map.of());
    }

    public static PhysicalRuntimeCatalog fromResource(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalStateException("Cannot find generated physical runtime catalog: resource name is required");
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = classLoader == null
                ? PhysicalRuntimeCatalog.class.getResourceAsStream(resourceName)
                : classLoader.getResourceAsStream(stripLeadingSlash(resourceName));
        if (stream == null) {
            throw new IllegalStateException("Cannot find generated physical runtime catalog " + resourceName);
        }
        return fromProperties(stream);
    }

    public static PhysicalRuntimeCatalog fromProperties(InputStream stream) {
        if (stream == null) {
            throw new IllegalStateException("Cannot load generated physical runtime catalog: input stream is null");
        }
        Properties properties = new Properties();
        try (InputStream input = stream) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load generated physical runtime catalog", e);
        }
        return fromProperties(properties);
    }

    public static PhysicalRuntimeCatalog fromProperties(Properties properties) {
        if (properties == null) {
            throw new IllegalStateException("Cannot load generated physical runtime catalog: properties are null");
        }
        String physicalProfile = properties.getProperty("physicalProfile", "").trim();
        boolean physicalProfileExplicit = parseBoolean(properties, "physicalProfileExplicit", false);
        List<String> physicalSourceFiles = parsePhysicalSourceFiles(
                properties.getProperty("physicalSourceFiles", ""));
        String backendNames = properties.getProperty("backends", "");
        if (backendNames.isBlank()) {
            return new PhysicalRuntimeCatalog(List.of(), parseRemoteAdapters(properties),
                    parseRepositoryAdapters(properties), parseQuantumStores(properties),
                    physicalProfile, physicalProfileExplicit, physicalSourceFiles,
                    parsePhysicalConfiguration(properties));
        }
        List<PhysicalBackend> backends = new ArrayList<>();
        Set<String> seenBackendNames = new LinkedHashSet<>();
        for (String normalizedName : parseCatalogNames(backendNames, "physical runtime catalog backend")) {
            requireUnique("physical runtime catalog backend", seenBackendNames, normalizedName);
            String quantumStoreName = firstNonBlank(
                    properties.getProperty("backend." + normalizedName + ".quantumStore"),
                    properties.getProperty("backend." + normalizedName + ".config.quantumStore"));
            backends.add(new PhysicalBackend(normalizedName,
                    properties.getProperty("backend." + normalizedName + ".adapter", normalizedName).trim(),
                    parseBindings(properties.getProperty("backend." + normalizedName + ".bindings", "")),
                    quantumStoreName,
                    parseConfiguration(properties, normalizedName)));
        }
        return new PhysicalRuntimeCatalog(List.copyOf(backends), parseRemoteAdapters(properties),
                parseRepositoryAdapters(properties), parseQuantumStores(properties),
                physicalProfile, physicalProfileExplicit, physicalSourceFiles,
                parsePhysicalConfiguration(properties));
    }

    public Optional<PhysicalBackend> findBackend(String name) {
        for (PhysicalBackend backend : backends) {
            if (backend.name().equals(name)) {
                return Optional.of(backend);
            }
        }
        return Optional.empty();
    }

    public List<PhysicalBinding> bindingsFor(String kind) {
        List<PhysicalBinding> matches = new ArrayList<>();
        for (PhysicalBackend backend : backends) {
            for (PhysicalBinding binding : backend.bindings()) {
                if (binding.kind().equals(kind)) {
                    matches.add(binding);
                }
            }
        }
        return List.copyOf(matches);
    }

    public Optional<PhysicalBackend> backendFor(String kind, String targetName) {
        for (PhysicalBackend backend : backends) {
            if (backend.hasExactBinding(kind, targetName)) {
                return Optional.of(backend);
            }
        }
        for (PhysicalBackend backend : backends) {
            if (backend.hasAllTargetsBinding(kind)) {
                return Optional.of(backend);
            }
        }
        if (backends.size() == 1 && backends.get(0).bindings().isEmpty()) {
            return Optional.of(backends.get(0));
        }
        return Optional.empty();
    }

    public Optional<RemoteAdapter> remoteAdapterFor(String serviceName) {
        for (RemoteAdapter adapter : remoteAdapters) {
            if (adapter.serviceName().equals(serviceName)) {
                return Optional.of(adapter);
            }
        }
        return Optional.empty();
    }

    public Optional<RepositoryAdapter> repositoryAdapter(String repositoryName) {
        for (RepositoryAdapter adapter : repositoryAdapters) {
            if (adapter.repositoryName().equals(repositoryName)) {
                return Optional.of(adapter);
            }
        }
        return Optional.empty();
    }

    public Optional<RepositoryAdapter> defaultRepositoryAdapter() {
        RepositoryAdapter defaultAdapter = null;
        for (RepositoryAdapter adapter : repositoryAdapters) {
            if (!isTrue(adapter.configuration().get("default"))) {
                continue;
            }
            if (defaultAdapter != null) {
                return Optional.empty();
            }
            defaultAdapter = adapter;
        }
        if (defaultAdapter != null) {
            return Optional.of(defaultAdapter);
        }
        if (repositoryAdapters.size() == 1) {
            return Optional.of(repositoryAdapters.get(0));
        }
        return Optional.empty();
    }

    public boolean hasAmbiguousDefaultRepositoryAdapters() {
        int count = 0;
        for (RepositoryAdapter adapter : repositoryAdapters) {
            if (isTrue(adapter.configuration().get("default"))) {
                count++;
            }
        }
        return count > 1;
    }

    private static boolean isTrue(String value) {
        return value != null && "true".equals(value.trim());
    }

    private static boolean parseBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new IllegalStateException("Generated physical runtime catalog " + key
                + " must be true or false, but was " + value);
    }

    private static List<PhysicalBinding> parseBindings(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<PhysicalBinding> bindings = new ArrayList<>();
        Set<String> seenBindings = new LinkedHashSet<>();
        for (String token : value.split(",", -1)) {
            String item = token.trim();
            if (item.isEmpty()) {
                throw new IllegalStateException("Generated physical runtime catalog backend binding contains blank value");
            }
            int separator = item.indexOf(':');
            if (separator <= 0 || separator == item.length() - 1) {
                throw new IllegalStateException("Invalid physical runtime binding: " + item);
            }
            String kind = item.substring(0, separator).trim();
            String targetName = item.substring(separator + 1).trim();
            if (kind.isEmpty() || targetName.isEmpty()) {
                throw new IllegalStateException("Invalid physical runtime binding: " + item);
            }
            requireUnique("physical runtime catalog backend binding", seenBindings, kind + ":" + targetName);
            bindings.add(new PhysicalBinding(kind, targetName));
        }
        return List.copyOf(bindings);
    }

    private static Map<String, String> parseConfiguration(Properties properties, String backendName) {
        Map<String, String> values = new LinkedHashMap<>();
        String prefix = "backend." + backendName + ".config.";
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                String key = propertyName.substring(prefix.length());
                if ("quantumStore".equals(key)) {
                    continue;
                }
                values.put(key, resolveConfigValue(properties.getProperty(propertyName)));
            }
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> parsePhysicalConfiguration(Properties properties) {
        Map<String, String> values = new LinkedHashMap<>();
        String prefix = "config.";
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                values.put(propertyName.substring(prefix.length()),
                        resolveConfigValue(properties.getProperty(propertyName)));
            } else if (propertyName.startsWith("numeric.") || propertyName.startsWith("numericAdapter.")) {
                values.put(propertyName, properties.getProperty(propertyName));
            }
        }
        return Map.copyOf(values);
    }

    /** Compile-time numeric representations retained for deployment inspection. */
    public Map<String, String> numericBindings() {
        Map<String, String> bindings = new LinkedHashMap<>();
        configuration.forEach((key, value) -> {
            if (key.startsWith("numeric.")) { bindings.put(key.substring("numeric.".length()), value); }
        });
        return Map.copyOf(bindings);
    }

    public Map<String, String> numericAdapters() {
        Map<String, String> adapters = new LinkedHashMap<>();
        configuration.forEach((key, value) -> {
            if (key.startsWith("numericAdapter.")) { adapters.put(key.substring("numericAdapter.".length()), value); }
        });
        return Map.copyOf(adapters);
    }

    private static List<RemoteAdapter> parseRemoteAdapters(Properties properties) {
        String remoteServices = properties.getProperty("remoteServices", "");
        if (remoteServices.isBlank()) {
            return List.of();
        }
        List<RemoteAdapter> adapters = new ArrayList<>();
        Set<String> seenServiceNames = new LinkedHashSet<>();
        for (String normalizedName : parseCatalogNames(remoteServices, "physical runtime catalog remote service")) {
            requireUnique("physical runtime catalog remote service", seenServiceNames, normalizedName);
            adapters.add(new RemoteAdapter(normalizedName,
                    properties.getProperty("remote." + normalizedName + ".adapter", "").trim(),
                    parseRemoteConfiguration(properties, normalizedName)));
        }
        return List.copyOf(adapters);
    }

    private static Map<String, String> parseRemoteConfiguration(Properties properties, String serviceName) {
        Map<String, String> values = new LinkedHashMap<>();
        String prefix = "remote." + serviceName + ".config.";
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                values.put(propertyName.substring(prefix.length()),
                        resolveConfigValue(properties.getProperty(propertyName)));
            }
        }
        return Map.copyOf(values);
    }

    private static List<RepositoryAdapter> parseRepositoryAdapters(Properties properties) {
        String repositoryNames = properties.getProperty("repositories", "");
        if (repositoryNames.isBlank()) {
            return List.of();
        }
        List<RepositoryAdapter> adapters = new ArrayList<>();
        Set<String> seenRepositoryNames = new LinkedHashSet<>();
        for (String normalizedName : parseCatalogNames(repositoryNames, "physical runtime catalog repository")) {
            requireUnique("physical runtime catalog repository", seenRepositoryNames, normalizedName);
            adapters.add(new RepositoryAdapter(normalizedName,
                    properties.getProperty("repository." + normalizedName + ".adapter", "").trim(),
                    parseRepositoryConfiguration(properties, normalizedName)));
        }
        return List.copyOf(adapters);
    }

    private static List<QuantumStore> parseQuantumStores(Properties properties) {
        String storeNames = properties.getProperty("quantumStores", "");
        if (storeNames.isBlank()) {
            return List.of();
        }
        List<QuantumStore> stores = new ArrayList<>();
        Set<String> seenStoreNames = new LinkedHashSet<>();
        for (String normalizedName : parseCatalogNames(storeNames, "physical runtime catalog quantum store")) {
            requireUnique("physical runtime catalog quantum store", seenStoreNames, normalizedName);
            stores.add(new QuantumStore(normalizedName,
                    parseQuantumStoreAttributes(
                            properties.getProperty("quantumStore." + normalizedName + ".attributes", "")),
                    properties.getProperty("quantumStore." + normalizedName + ".adapter", "").trim(),
                    parseQuantumStoreConfiguration(properties, normalizedName),
                    parseQuantumDefinitions(properties, normalizedName)));
        }
        return List.copyOf(stores);
    }

    private static List<QuantumDefinition> parseQuantumDefinitions(Properties properties, String storeName) {
        String countProperty = "quantumStore." + storeName + ".definitionCount";
        String rawCount = properties.getProperty(countProperty, "").trim();
        if (rawCount.isEmpty()) {
            return List.of();
        }
        int count;
        try {
            count = Integer.parseInt(rawCount);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Generated physical runtime catalog " + countProperty
                    + " must be a non-negative integer", e);
        }
        if (count < 0) {
            throw new IllegalStateException("Generated physical runtime catalog " + countProperty
                    + " must be a non-negative integer");
        }
        List<QuantumDefinition> definitions = new ArrayList<>(count);
        Set<String> seenAttributeIds = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            String prefix = "quantumStore." + storeName + ".definition." + i + ".";
            String attributeId = requireProperty(properties, prefix + "attributeId");
            requireUnique("physical runtime catalog quantum definition", seenAttributeIds, attributeId);
            String numberType = requireProperty(properties, prefix + "numberType");
            String minValue = requireProperty(properties, prefix + "minValue");
            String maxValue = requireProperty(properties, prefix + "maxValue");
            String scaleValue = requireProperty(properties, prefix + "scale");
            int scale;
            try {
                scale = Integer.parseInt(scaleValue);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Generated physical runtime catalog " + prefix
                        + "scale must be a non-negative integer", e);
            }
            definitions.add(new QuantumDefinition(attributeId, numberType, minValue, maxValue, scale));
        }
        return List.copyOf(definitions);
    }

    private static String requireProperty(Properties properties, String propertyName) {
        String value = properties.getProperty(propertyName, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Generated physical runtime catalog " + propertyName
                    + " must not be blank");
        }
        return value;
    }

    private static Map<String, String> parseQuantumStoreConfiguration(Properties properties, String storeName) {
        Map<String, String> values = new LinkedHashMap<>();
        String prefix = "quantumStore." + storeName + ".config.";
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                values.put(propertyName.substring(prefix.length()),
                        resolveConfigValue(properties.getProperty(propertyName)));
            }
        }
        return Map.copyOf(values);
    }

    private static List<String> parsePhysicalSourceFiles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Set<String> seenFiles = new LinkedHashSet<>();
        for (String item : value.split(",", -1)) {
            String normalized = item.trim();
            if (normalized.isEmpty()) {
                throw new IllegalStateException(
                        "Generated physical runtime catalog source file contains blank value");
            }
            requireUnique("physical runtime catalog source file", seenFiles, normalized);
            values.add(normalized);
        }
        return List.copyOf(values);
    }

    private static List<String> parseCatalogNames(String value, String label) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",", -1)) {
            String normalized = item.trim();
            if (normalized.isEmpty()) {
                throw new IllegalStateException("Generated " + label + " contains blank value");
            }
            values.add(normalized);
        }
        return List.copyOf(values);
    }

    private static List<String> parseQuantumStoreAttributes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Set<String> seenAttributes = new LinkedHashSet<>();
        for (String item : value.split(",", -1)) {
            String normalized = item.trim();
            if (normalized.isEmpty()) {
                throw new IllegalStateException(
                        "Generated physical runtime catalog quantum store attribute contains blank value");
            }
            requireUnique("physical runtime catalog quantum store attribute", seenAttributes, normalized);
            values.add(normalized);
        }
        return List.copyOf(values);
    }

    private static void requireUnique(String label, Set<String> seen, String value) {
        if (!seen.add(value)) {
            throw new IllegalStateException("Generated " + label + " declares duplicate value " + value);
        }
    }

    private static Map<String, String> parseRepositoryConfiguration(Properties properties, String repositoryName) {
        Map<String, String> values = new LinkedHashMap<>();
        String prefix = "repository." + repositoryName + ".config.";
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                values.put(propertyName.substring(prefix.length()),
                        resolveConfigValue(properties.getProperty(propertyName)));
            }
        }
        return Map.copyOf(values);
    }

    public static String resolveConfigValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        StringBuilder resolved = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int start = value.indexOf("${", cursor);
            if (start < 0) {
                resolved.append(value.substring(cursor));
                break;
            }
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                resolved.append(value.substring(cursor));
                break;
            }
            resolved.append(value, cursor, start);
            resolved.append(resolvePlaceholder(value.substring(start + 2, end)));
            cursor = end + 1;
        }
        return resolved.toString();
    }

    private static String resolvePlaceholder(String expression) {
        int defaultSeparator = expression.indexOf(':');
        String key = defaultSeparator < 0 ? expression : expression.substring(0, defaultSeparator);
        String defaultValue = defaultSeparator < 0 ? "" : expression.substring(defaultSeparator + 1);
        String resolved = lookupConfigValue(key);
        return resolved == null ? defaultValue : resolved;
    }

    private static String lookupConfigValue(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String systemValue = System.getProperty(key);
        if (systemValue != null) {
            return systemValue;
        }
        String environmentValue = System.getenv(key);
        if (environmentValue != null) {
            return environmentValue;
        }
        return System.getenv(key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private static String stripLeadingSlash(String resourceName) {
        return resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
    }

    public record PhysicalBackend(String name, String adapterName, List<PhysicalBinding> bindings,
                                  String quantumStoreName,
                                  Map<String, String> configuration) {
        public PhysicalBackend {
            name = normalizeCatalogValue(name);
            adapterName = normalizeCatalogValue(adapterName);
            if (adapterName.isBlank()) {
                adapterName = name;
            }
            bindings = bindings == null ? List.of() : List.copyOf(bindings);
            quantumStoreName = normalizeCatalogValue(quantumStoreName);
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }

        public boolean hasBinding(String kind, String targetName) {
            return hasExactBinding(kind, targetName) || hasAllTargetsBinding(kind);
        }

        public boolean hasExactBinding(String kind, String targetName) {
            for (PhysicalBinding binding : bindings) {
                if (binding.kind().equals(kind) && binding.targetName().equals(targetName)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasAllTargetsBinding(String kind) {
            for (PhysicalBinding binding : bindings) {
                if (binding.kind().equals(kind) && "*".equals(binding.targetName())) {
                    return true;
                }
            }
            return false;
        }
    }

    public record PhysicalBinding(String kind, String targetName) {
        public PhysicalBinding {
            kind = normalizeCatalogValue(kind);
            targetName = normalizeCatalogValue(targetName);
        }
    }

    public record RemoteAdapter(String serviceName, String adapterName, Map<String, String> configuration) {
        public RemoteAdapter {
            serviceName = normalizeCatalogValue(serviceName);
            adapterName = normalizeCatalogValue(adapterName);
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }

    public record RepositoryAdapter(String repositoryName, String adapterName, Map<String, String> configuration) {
        public RepositoryAdapter {
            repositoryName = normalizeCatalogValue(repositoryName);
            adapterName = normalizeCatalogValue(adapterName);
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        }
    }

    public record QuantumStore(String name, List<String> attributeIds, String adapterName,
                               Map<String, String> configuration, List<QuantumDefinition> definitions) {
        public QuantumStore {
            name = normalizeCatalogValue(name);
            attributeIds = normalizeCatalogValues(attributeIds);
            adapterName = normalizeCatalogValue(adapterName);
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
            definitions = definitions == null ? List.of() : List.copyOf(definitions);
        }

        public QuantumStore(String name, List<String> attributeIds, String adapterName,
                            Map<String, String> configuration) {
            this(name, attributeIds, adapterName, configuration, List.of());
        }
    }

    public record QuantumDefinition(String attributeId, String numberType, String minValue,
                                    String maxValue, int scale) {
        public QuantumDefinition {
            attributeId = normalizeCatalogValue(attributeId);
            numberType = normalizeCatalogValue(numberType);
            minValue = normalizeCatalogValue(minValue);
            maxValue = normalizeCatalogValue(maxValue);
            if (attributeId.isEmpty() || numberType.isEmpty() || minValue.isEmpty() || maxValue.isEmpty()) {
                throw new IllegalArgumentException("Quantum definition values must not be blank");
            }
            if (scale < 0) {
                throw new IllegalArgumentException("Quantum definition scale must not be negative");
            }
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private static String normalizeCatalogValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeCatalogValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            normalized.add(normalizeCatalogValue(value));
        }
        return List.copyOf(normalized);
    }
}
