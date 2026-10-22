package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.servpp.service.transaction.TransactionMode;

public class PhysicalBackendRegistry {
    private final PhysicalRuntimeCatalog catalog;
    private final Map<String, PhysicalBackendAdapter> adapters = new LinkedHashMap<>();

    public PhysicalBackendRegistry(PhysicalRuntimeCatalog catalog) {
        this.catalog = catalog;
    }

    public PhysicalBackendRegistry registerAdapter(PhysicalBackendAdapter adapter) {
        for (String backendName : adapter.backendNames()) {
            adapters.put(backendName, adapter);
        }
        return this;
    }

    public Optional<PhysicalBackendAdapter> adapterFor(String backendName) {
        return Optional.ofNullable(adapters.get(backendName));
    }

    public List<PhysicalBackendAssignment> resolve(List<GeneratedServiceMetadata> services) {
        if (catalog.backends().isEmpty()) {
            return List.of();
        }
        List<PhysicalBackendAssignment> assignments = new ArrayList<>();
        for (GeneratedServiceMetadata service : services) {
            if (service.remote() || service.transactionMode() == null || !service.physicalBindingRequired()) {
                continue;
            }
            assignments.add(resolve(service));
        }
        return List.copyOf(assignments);
    }

    public List<String> validate(List<PhysicalBackendAssignment> assignments) {
        List<String> errors = new ArrayList<>();
        Set<String> validatedAdapters = new LinkedHashSet<>();
        validateDuplicateBackendNames(errors);
        validateDeclaredBackendAdapters(errors, validatedAdapters);
        validateDeclaredBindings(assignments, errors);
        for (PhysicalBackendAssignment assignment : assignments) {
            if (!assignment.resolved()) {
                errors.add("Physical backend is not resolved for " + assignment.transactionKind()
                        + " " + assignment.targetName() + " used by service " + assignment.serviceName() + ".");
                continue;
            }
            if (!assignment.adapterAvailable()) {
                errors.add("Physical backend " + assignment.backendName()
                        + " has no registered adapter for " + assignment.transactionKind()
                        + " " + assignment.targetName() + " used by service " + assignment.serviceName() + ".");
            } else {
                adapterFor(assignment.adapterName()).ifPresent(adapter -> {
                    String validationKey = assignment.backendName() + ":" + adapter.implementationType();
                    if (validatedAdapters.add(validationKey)) {
                        errors.addAll(adapter.validationErrors(assignment.backendName()));
                    }
                });
                if (!assignment.adapterSupportsTransaction()) {
                    errors.add("Physical backend " + assignment.backendName()
                            + " adapter " + assignment.adapterImplementationType()
                            + " does not support " + assignment.transactionKind()
                            + " " + assignment.targetName() + " used by service " + assignment.serviceName() + ".");
                }
            }
        }
        return List.copyOf(errors);
    }

    private void validateDuplicateBackendNames(List<String> errors) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (PhysicalRuntimeCatalog.PhysicalBackend backend : catalog.backends()) {
            if (!seen.add(backend.name()) && duplicated.add(backend.name())) {
                errors.add("Physical backend " + backend.name() + " is declared multiple times.");
            }
        }
    }

    private void validateDeclaredBindings(List<PhysicalBackendAssignment> assignments, List<String> errors) {
        Set<String> resolvedTargets = new LinkedHashSet<>();
        Map<String, Set<String>> resolvedTargetsByKind = new LinkedHashMap<>();
        for (PhysicalBackendAssignment assignment : assignments) {
            String targetKey = assignment.transactionKind() + ":" + assignment.targetName();
            resolvedTargets.add(targetKey);
            resolvedTargetsByKind.computeIfAbsent(assignment.transactionKind(), key -> new LinkedHashSet<>())
                    .add(targetKey);
        }
        Map<String, String> backendByBinding = new LinkedHashMap<>();
        Set<String> duplicatedBindings = new LinkedHashSet<>();
        for (PhysicalRuntimeCatalog.PhysicalBackend backend : catalog.backends()) {
            for (PhysicalRuntimeCatalog.PhysicalBinding binding : backend.bindings()) {
                if ("repository".equals(binding.kind())) {
                    validateRepositoryBinding(backend, binding, backendByBinding, duplicatedBindings, errors);
                    continue;
                }
                String bindingKey = binding.kind() + ":" + binding.targetName();
                if ("*".equals(binding.targetName())) {
                    Set<String> matchedTargets = resolvedTargetsByKind.getOrDefault(binding.kind(), Set.of());
                    String previousBackend = backendByBinding.putIfAbsent(bindingKey, backend.name());
                    if (previousBackend != null && duplicatedBindings.add(bindingKey)) {
                        if (previousBackend.equals(backend.name())) {
                            errors.add("Physical backend " + backend.name()
                                    + " declares duplicate binding " + bindingKey + ".");
                        } else {
                            errors.add("Physical binding " + bindingKey
                                    + " is assigned to multiple backends: "
                                    + previousBackend + " and " + backend.name() + ".");
                        }
                    }
                    for (String targetKey : matchedTargets) {
                        validateBindingAssignment(targetKey, backend.name(), backendByBinding,
                                duplicatedBindings, errors);
                    }
                    if (matchedTargets.isEmpty()) {
                        errors.add("Physical backend " + backend.name()
                                + " declares unused binding " + binding.kind()
                                + " " + binding.targetName()
                                + "; no registered generated service uses this transaction kind.");
                    }
                } else {
                    validateBindingAssignment(bindingKey, backend.name(), backendByBinding,
                            duplicatedBindings, errors);
                }
                if (!"*".equals(binding.targetName()) && !resolvedTargets.contains(bindingKey)) {
                    errors.add("Physical backend " + backend.name()
                            + " declares unused binding " + binding.kind()
                            + " " + binding.targetName()
                            + "; no registered generated service uses this transaction target.");
                }
            }
        }
    }

    private void validateRepositoryBinding(PhysicalRuntimeCatalog.PhysicalBackend backend,
                                           PhysicalRuntimeCatalog.PhysicalBinding binding,
                                           Map<String, String> backendByBinding,
                                           Set<String> duplicatedBindings,
                                           List<String> errors) {
        String bindingKey = binding.kind() + ":" + binding.targetName();
        validateBindingAssignment(bindingKey, backend.name(), backendByBinding, duplicatedBindings, errors);
        if ("*".equals(binding.targetName())) {
            if (catalog.repositoryAdapters().isEmpty()) {
                errors.add("Physical backend " + backend.name()
                        + " repository binding * requires at least one physical repository adapter.");
            }
            return;
        }
        if (catalog.repositoryAdapter(binding.targetName()).isEmpty()) {
            errors.add("Physical backend " + backend.name() + " repository binding "
                    + binding.targetName() + " references an undefined physical repository adapter.");
        }
    }

    private void validateBindingAssignment(String bindingKey, String backendName,
                                           Map<String, String> backendByBinding,
                                           Set<String> duplicatedBindings, List<String> errors) {
        String previousBackend = backendByBinding.putIfAbsent(bindingKey, backendName);
        if (previousBackend == null || !duplicatedBindings.add(bindingKey)) {
            return;
        }
        if (previousBackend.equals(backendName)) {
            errors.add("Physical backend " + backendName
                    + " declares duplicate binding " + bindingKey + ".");
        } else {
            errors.add("Physical binding " + bindingKey
                    + " is assigned to multiple backends: "
                    + previousBackend + " and " + backendName + ".");
        }
    }

    private void validateDeclaredBackendAdapters(List<String> errors, Set<String> validatedAdapters) {
        for (PhysicalRuntimeCatalog.PhysicalBackend backend : catalog.backends()) {
            validateSupportedTransactions(backend, errors);
            Optional<PhysicalBackendAdapter> adapter = adapterFor(backend.adapterName());
            if (adapter.isEmpty()) {
                errors.add("Physical backend " + backend.name()
                        + " declares adapter " + backend.adapterName()
                        + " but no registered adapter is available.");
                continue;
            }
            String validationKey = backend.name() + ":" + adapter.get().implementationType();
            if (validatedAdapters.add(validationKey)) {
                errors.addAll(adapter.get().validationErrors(backend.name()));
            }
        }
    }

    private void validateSupportedTransactions(PhysicalRuntimeCatalog.PhysicalBackend backend, List<String> errors) {
        String configured = backend.configuration().get("supportedTransactions");
        if (configured == null) {
            return;
        }
        if (configured.isBlank()) {
            errors.add("Physical backend " + backend.name()
                    + " config supportedTransactions must not be blank when declared.");
            return;
        }
        Set<String> supported = Set.of("at", "xa", "tcc", "quantum", "saga");
        List<String> unsupported = new ArrayList<>();
        for (String item : configured.split(",", -1)) {
            String transaction = item.trim();
            if (transaction.isEmpty() || !supported.contains(transaction)) {
                unsupported.add(transaction.isEmpty() ? "<blank>" : transaction);
            }
        }
        if (!unsupported.isEmpty()) {
            errors.add("Physical backend " + backend.name()
                    + " config supportedTransactions must be a comma-separated list of at, xa, tcc, quantum, and saga; "
                    + "unsupported values: " + String.join(", ", unsupported) + ".");
        }
    }

    public PhysicalBackendAssignment resolve(GeneratedServiceMetadata service) {
        String kind = transactionKind(service.transactionMode());
        String targetName = targetName(service);
        Optional<PhysicalRuntimeCatalog.PhysicalBackend> backend = catalog.backendFor(kind, targetName);
        boolean exactBinding = backend.map(item -> item.hasExactBinding(kind, targetName)).orElse(false);
        boolean kindBinding = backend.map(item -> item.hasAllTargetsBinding(kind)).orElse(false);
        boolean explicit = exactBinding || kindBinding;
        String bindingScope = bindingScope(backend, kind, targetName);
        String bindingKey = bindingKey(kind, targetName, exactBinding, kindBinding);
        Optional<PhysicalBackendAdapter> adapter = backend.flatMap(item -> adapterFor(item.adapterName()));
        return new PhysicalBackendAssignment(service.serviceName(), kind, targetName,
                backend.map(PhysicalRuntimeCatalog.PhysicalBackend::name).orElse(null),
                backend.map(PhysicalRuntimeCatalog.PhysicalBackend::adapterName).orElse(null),
                explicit, bindingScope, bindingKey, backend.isPresent(), adapter.isPresent(),
                backendSupportsTransaction(backend, adapter, kind, targetName),
                adapter.map(PhysicalBackendAdapter::implementationType).orElse(null),
                effectiveAdapterConfiguration(backend, adapter));
    }

    private boolean backendSupportsTransaction(Optional<PhysicalRuntimeCatalog.PhysicalBackend> backend,
                                               Optional<PhysicalBackendAdapter> adapter,
                                               String kind, String targetName) {
        if (adapter.isEmpty()) {
            return false;
        }
        Optional<List<String>> configuredTransactions = backend.flatMap(this::configuredSupportedTransactions);
        if (configuredTransactions.isPresent()) {
            return configuredTransactions.orElseThrow().contains(kind);
        }
        return adapter.orElseThrow().supportsTransaction(kind, targetName);
    }

    private Optional<List<String>> configuredSupportedTransactions(PhysicalRuntimeCatalog.PhysicalBackend backend) {
        String configured = backend.configuration().get("supportedTransactions");
        if (configured == null) {
            return Optional.empty();
        }
        if (configured.isBlank()) {
            return Optional.of(List.of());
        }
        Set<String> supported = Set.of("at", "xa", "tcc", "quantum", "saga");
        List<String> values = new ArrayList<>();
        for (String item : configured.split(",", -1)) {
            String transaction = item.trim();
            if (transaction.isEmpty() || !supported.contains(transaction)) {
                return Optional.of(List.of());
            }
            values.add(transaction);
        }
        return Optional.of(List.copyOf(values));
    }

    private Map<String, String> effectiveAdapterConfiguration(
            Optional<PhysicalRuntimeCatalog.PhysicalBackend> backend,
            Optional<PhysicalBackendAdapter> adapter) {
        Map<String, String> values = new LinkedHashMap<>();
        adapter.map(PhysicalBackendAdapter::configuration).ifPresent(values::putAll);
        backend.map(PhysicalRuntimeCatalog.PhysicalBackend::configuration).ifPresent(values::putAll);
        backend.map(PhysicalRuntimeCatalog.PhysicalBackend::adapterName)
                .filter("seata"::equals)
                .ifPresent(ignored -> normalizeSeataConfiguration(values));
        return Map.copyOf(values);
    }

    private static void normalizeSeataConfiguration(Map<String, String> values) {
        trimValue(values, "applicationId");
        trimValue(values, "transactionServiceGroup");
        trimValue(values, "transactionBridge");
        trimValue(values, "transactionBridgeClass");
        trimValue(values, ReflectiveSeataGlobalTransactionBridge.TRANSACTION_TIMEOUT_MILLIS_CONFIG);
        trimValue(values, "quantumSupported");
        trimValue(values, "supportedTransactions");
    }

    private static void trimValue(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value != null) {
            values.put(key, value.trim());
        }
    }

    private String bindingScope(Optional<PhysicalRuntimeCatalog.PhysicalBackend> backend,
                                String kind, String targetName) {
        if (backend.isEmpty()) {
            return "UNRESOLVED";
        }
        PhysicalRuntimeCatalog.PhysicalBackend resolved = backend.orElseThrow();
        if (resolved.hasExactBinding(kind, targetName)) {
            return "EXACT_TARGET";
        }
        if (resolved.hasAllTargetsBinding(kind)) {
            return "TRANSACTION_KIND";
        }
        return "DEFAULT_BACKEND";
    }

    private String bindingKey(String kind, String targetName, boolean exactBinding, boolean kindBinding) {
        if (exactBinding) {
            return kind + ":" + targetName;
        }
        if (kindBinding) {
            return kind + ":*";
        }
        return "";
    }

    private String targetName(GeneratedServiceMetadata service) {
        if (service.transactionMode() == TransactionMode.SAGA && service.serviceKind() != GeneratedServiceKind.ATOMIC_UPDATE) {
            return service.serviceName();
        }
        return service.transactionResourceId();
    }

    private String transactionKind(TransactionMode mode) {
        return mode == TransactionMode.LOCAL ? "at" : mode.name().toLowerCase(Locale.ROOT);
    }
}
