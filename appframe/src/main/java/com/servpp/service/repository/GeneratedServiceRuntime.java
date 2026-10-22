package com.servpp.service.repository;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.servpp.service.Scenario;
import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.transaction.ChainedTransactionParticipant;
import com.servpp.service.transaction.QuantumConservationParticipant;
import com.servpp.service.transaction.TccReservationParticipant;
import com.servpp.service.transaction.TccReservationRule;
import com.servpp.service.transaction.TccReservationRuleProvider;
import com.servpp.service.transaction.TccReservationStore;
import com.servpp.service.transaction.TransactionParticipantProxy;
import com.servpp.service.transaction.TransactionalService;

public class GeneratedServiceRuntime {
    private JdbcObjectSnapshotStore snapshotStore;
    private ObjectRepository objectRepository;
    private final Function<String, Object> currentObjectResolver;
    private boolean exampleFaultsEnabled;
    public void enableExampleFaults() { exampleFaultsEnabled = true; }

    private Function<Service, Service> serviceDecorator = Function.identity();
    private RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver =
            RepositoryHydratingServiceProxy.RoleIdentityResolver.none();
    private QuantumConservationParticipant.ObjectIdentityResolver quantumObjectIdentityResolver =
            QuantumConservationParticipant.ObjectIdentityResolver.none();
    private Supplier<TccReservationStore> tccReservationStoreSupplier;
    private Supplier<Long> tccReservationTtlMillisSupplier;

    public GeneratedServiceRuntime(JdbcObjectSnapshotStore snapshotStore,
                                   JsonObjectSnapshotRepository objectRepository,
                                   Function<String, Object> currentObjectResolver) {
        this(snapshotStore, (ObjectRepository) objectRepository, currentObjectResolver);
    }

    public GeneratedServiceRuntime(JdbcObjectSnapshotStore snapshotStore,
                                   ObjectRepository objectRepository,
                                   Function<String, Object> currentObjectResolver) {
        this.snapshotStore = snapshotStore;
        this.objectRepository = objectRepository;
        this.currentObjectResolver = objectType -> CurrentObjectContext.find(objectType)
                .orElseGet(() -> currentObjectResolver == null ? null : currentObjectResolver.apply(objectType));
    }

    public static GeneratedServiceRuntime jsonSnapshots(DataSource dataSource, ObjectMapper objectMapper,
                                                        Function<String, Object> currentObjectResolver) {
        JdbcObjectSnapshotStore snapshotStore =
                new JdbcObjectSnapshotStore(dataSource, value -> toJson(objectMapper, value));
        return new GeneratedServiceRuntime(snapshotStore,
                new JsonObjectSnapshotRepository(snapshotStore, objectMapper), currentObjectResolver);
    }

    public JdbcObjectSnapshotStore getSnapshotStore() {
        return snapshotStore;
    }

    public ObjectRepository getRepository() {
        return objectRepository;
    }

    public GeneratedServiceRuntime installJsonSnapshotRepository(JdbcObjectSnapshotStore snapshotStore,
                                                                 ObjectMapper objectMapper) {
        this.snapshotStore = snapshotStore;
        this.objectRepository = new JsonObjectSnapshotRepository(snapshotStore, objectMapper);
        return this;
    }

    public GeneratedServiceRuntime installObjectRepository(JdbcObjectSnapshotStore objectStore,
                                                           ObjectMapper objectMapper) {
        this.snapshotStore = objectStore;
        this.objectRepository = new JdbcObjectRepository(objectStore, objectMapper);
        return this;
    }

    public JsonObjectSnapshotRepository getObjectRepository() {
        if (objectRepository instanceof JsonObjectSnapshotRepository repository) {
            return repository;
        }
        throw new IllegalStateException("Object repository is not a JSON snapshot repository: "
                + objectRepository.getClass().getName());
    }

    public GeneratedServiceRuntime decorateServices(Function<Service, Service> serviceDecorator) {
        this.serviceDecorator = serviceDecorator == null ? Function.identity() : serviceDecorator;
        return this;
    }

    public GeneratedServiceRuntime resolveRepositoryRoleIdentities(
            RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver) {
        this.roleIdentityResolver = roleIdentityResolver == null
                ? RepositoryHydratingServiceProxy.RoleIdentityResolver.none()
                : roleIdentityResolver;
        return this;
    }

    public GeneratedServiceRuntime resolveQuantumObjectIdentities(
            QuantumConservationParticipant.ObjectIdentityResolver quantumObjectIdentityResolver) {
        this.quantumObjectIdentityResolver = quantumObjectIdentityResolver == null
                ? QuantumConservationParticipant.ObjectIdentityResolver.none()
                : quantumObjectIdentityResolver;
        return this;
    }

    public Optional<Object> findCurrentObject(String objectType) {
        return Optional.ofNullable(currentObjectResolver.apply(objectType));
    }

    public GeneratedServiceRuntime useTccReservationStore(Supplier<TccReservationStore> tccReservationStoreSupplier) {
        this.tccReservationStoreSupplier = tccReservationStoreSupplier;
        return this;
    }

    public GeneratedServiceRuntime useTccReservationTtlMillis(Supplier<Long> tccReservationTtlMillisSupplier) {
        this.tccReservationTtlMillisSupplier = tccReservationTtlMillisSupplier;
        return this;
    }

    public Service register(DefaultServiceContainer services, Service service) {
        Service decorated = decorate(service);
        services.registerService(service.getName(), decorated);
        return decorated;
    }

    public List<Service> registerFromProperties(DefaultServiceContainer services, String resourceName) {
        Properties properties = loadProperties(resourceName);
        return registerFromProperties(services, properties);
    }

    public Service registerFromProperties(DefaultServiceContainer services, String resourceName, String serviceName) {
        Properties properties = loadProperties(resourceName);
        return registerFromProperties(services, properties, resourceName, serviceName);
    }

    public Service registerFromProperties(DefaultServiceContainer services, InputStream stream, String serviceName) {
        return registerFromProperties(services, loadProperties(stream), "stream", serviceName);
    }

    private Service registerFromProperties(DefaultServiceContainer services, Properties properties,
                                           String sourceName, String serviceName) {
        String className = properties.getProperty(serviceName);
        if (className == null) {
            throw new IllegalStateException("Generated service " + serviceName
                    + " is not declared in " + sourceName);
        }
        return register(services, instantiate(serviceDefinition(serviceName, className)));
    }

    public List<Service> registerFromProperties(DefaultServiceContainer services, InputStream stream) {
        return registerFromProperties(services, loadProperties(stream));
    }

    private Properties loadProperties(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = classLoader == null
                ? GeneratedServiceRuntime.class.getResourceAsStream(resourceName)
                : classLoader.getResourceAsStream(stripLeadingSlash(resourceName));
        if (stream == null) {
            throw new IllegalStateException("Cannot find generated service registry " + resourceName);
        }
        return loadProperties(stream);
    }

    private Properties loadProperties(InputStream stream) {
        Properties properties = new Properties();
        try (InputStream input = stream) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load generated service registry", e);
        }
        return properties;
    }

    private List<Service> registerFromProperties(DefaultServiceContainer services, Properties properties) {
        List<ServiceDefinition> atomicServices = new ArrayList<>();
        List<ServiceDefinition> scenarioServices = new ArrayList<>();
        for (String serviceName : properties.stringPropertyNames()) {
            ServiceDefinition definition = serviceDefinition(serviceName, properties.getProperty(serviceName));
            if (Scenario.class.isAssignableFrom(definition.type())) {
                scenarioServices.add(definition);
            } else {
                atomicServices.add(definition);
            }
        }
        List<Service> registered = new ArrayList<>();
        for (ServiceDefinition definition : atomicServices) {
            registered.add(register(services, instantiate(definition)));
        }
        for (ServiceDefinition definition : orderedScenarios(scenarioServices)) {
            registered.add(register(services, instantiate(definition)));
        }
        return registered;
    }

    private List<ServiceDefinition> orderedScenarios(List<ServiceDefinition> definitions) {
        var byName = new java.util.LinkedHashMap<String, ServiceDefinition>();
        for (var definition : definitions) byName.put(definition.serviceName(), definition);
        List<ServiceDefinition> ordered = new ArrayList<>();
        var visiting = new java.util.LinkedHashSet<String>();
        var visited = new java.util.HashSet<String>();
        for (var definition : definitions) orderScenario(definition, byName, visiting, visited, ordered);
        return ordered;
    }

    private void orderScenario(ServiceDefinition definition, java.util.Map<String, ServiceDefinition> definitions,
                               java.util.Set<String> visiting, java.util.Set<String> visited,
                               List<ServiceDefinition> ordered) {
        String name = definition.serviceName();
        if (visited.contains(name)) return;
        if (!visiting.add(name)) throw new IllegalStateException("Cyclic generated scenario dependencies: " + visiting + " -> " + name);
        for (Class<?> type = definition.type(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && Scenario.class.isAssignableFrom(field.getType())) {
                    var dependency = definitions.get(field.getName());
                    if (dependency != null) orderScenario(dependency, definitions, visiting, visited, ordered);
                }
            }
        }
        visiting.remove(name); visited.add(name); ordered.add(definition);
    }

    public Service decorate(Service service) {
        if (service instanceof RepositoryWriteRuleProvider writer) {
            writer.configureRepositoryWrites(this::getRepository);
        }
        Service decorated = decorateRepositoryHydration(service);
        decorated = decorateQuantumRoleIdentities(decorated);
        decorated = decorateObjectSnapshots(decorated);
        decorated = decorateTccReservations(decorated);
        decorated = serviceDecorator.apply(decorated);
        decorated = exampleFaultsEnabled ? com.servpp.service.runtime.ExampleServiceFaults.decorate(decorated) : decorated;
        return com.servpp.service.technical.ServiceInterceptors.decorate(decorated);
    }

    private Service decorateRepositoryHydration(Service service) {
        if (!(service instanceof RepositoryHydrationRuleProvider provider)) {
            return service;
        }
        List<RepositoryHydratingServiceProxy.HydrationRule> rules = provider.getRepositoryHydrationRules();
        if (rules == null || rules.isEmpty()) {
            return service;
        }
        return RepositoryHydratingServiceProxy.decorate(service, this::getRepository, rules, roleIdentityResolver);
    }

    private Service decorateQuantumRoleIdentities(Service service) {
        // Generated scenario handlers are classes; quantum identities belong to atomic operations.
        if (!(service instanceof TransactionalService) || service instanceof Scenario) {
            return service;
        }
        List<ObjectSnapshotRule> snapshotRules = service instanceof ObjectSnapshotRuleProvider provider
                ? provider.getObjectSnapshotRules()
                : List.of();
        return TransactionParticipantProxy.decorate(service, participant -> {
            if (participant instanceof QuantumConservationParticipant quantumParticipant) {
                QuantumConservationParticipant.ObjectIdentityResolver snapshotCompatibilityResolver =
                        quantumObjectIdentityResolver(snapshotRules);
                return quantumParticipant
                        .resolveRoleIdentities(roleIdentityResolver)
                        .resolveObjectIdentities((objectType, arguments) ->
                                quantumObjectIdentityResolver.resolve(objectType, arguments)
                                        .or(() -> snapshotCompatibilityResolver.resolve(objectType, arguments)));
            }
            return participant;
        });
    }

    private QuantumConservationParticipant.ObjectIdentityResolver quantumObjectIdentityResolver(
            List<ObjectSnapshotRule> rules) {
        List<ObjectSnapshotRule> safeRules = rules == null ? List.of() : List.copyOf(rules);
        List<ObjectSnapshotRule> appDdlArgumentRules = safeRules.stream()
                .filter(rule -> rule.source() == ObjectSnapshotRule.Source.ARGUMENT)
                .filter(ObjectSnapshotRule::appDdlInferred)
                .toList();
        List<ObjectSnapshotRule> currentRules = safeRules.stream()
                .filter(rule -> rule.source() == ObjectSnapshotRule.Source.CURRENT)
                .toList();
        if (appDdlArgumentRules.isEmpty() && currentRules.isEmpty()) {
            return QuantumConservationParticipant.ObjectIdentityResolver.none();
        }
        return (objectType, arguments) -> appDdlArgumentObjectId(objectType, arguments, appDdlArgumentRules)
                .or(() -> roleIdentityResolver.resolve(objectType, arguments)
                        .filter(identity -> identity.complete() && !identity.keyValues().isEmpty())
                        .flatMap(identity -> compositionObjectId(objectType, arguments, currentRules, identity)));
    }

    private Optional<String> appDdlArgumentObjectId(String objectType, Object[] arguments,
                                                   List<ObjectSnapshotRule> rules) {
        if (objectType == null || objectType.isBlank() || arguments == null) {
            return Optional.empty();
        }
        for (ObjectSnapshotRule rule : rules) {
            if (!objectType.equals(rule.objectType())
                    || rule.argumentIndex() < 0
                    || rule.argumentIndex() >= arguments.length) {
                continue;
            }
            Object argument = arguments[rule.argumentIndex()];
            if (argument == null) {
                continue;
            }
            List<Object> values = new ArrayList<>();
            for (String path : rule.objectIdPaths()) {
                Object value;
                try {
                    value = readPath(argument, path);
                } catch (IllegalStateException ignored) {
                    values.clear();
                    break;
                }
                if (value == null || value.toString().isBlank()) {
                    values.clear();
                    break;
                }
                values.add(value);
            }
            if (!values.isEmpty()) {
                return Optional.of(values.size() == 1
                        ? values.get(0).toString()
                        : CompositeObjectId.encode(values));
            }
        }
        return Optional.empty();
    }

    private Optional<String> compositionObjectId(String objectType, Object[] arguments,
                                                 List<ObjectSnapshotRule> currentRules,
                                                 RepositoryHydratingServiceProxy.RoleIdentity identity) {
        Optional<Object> roleArgument = roleArgument(objectType, arguments);
        if (roleArgument.isEmpty()) {
            return Optional.empty();
        }
        for (ObjectSnapshotRule rule : currentRules) {
            Object currentObject = CurrentObjectContext.find(rule.objectType(), rule.objectIdPath())
                    .orElseGet(() -> currentObjectResolver.apply(rule.objectType()));
            if (currentObject == null
                    || !containsIdentityMember(currentObject, roleArgument.orElseThrow(), objectType, identity)) {
                continue;
            }
            List<Object> values = new ArrayList<>();
            for (String path : rule.objectIdPaths()) {
                Object value = readPath(currentObject, path);
                if (value == null || value.toString().isBlank()) {
                    values.clear();
                    break;
                }
                values.add(value);
            }
            if (values.isEmpty()) {
                continue;
            }
            values.addAll(identity.keyValues());
            return Optional.of(values.size() == 1
                    ? values.get(0).toString()
                    : CompositeObjectId.encode(values));
        }
        return Optional.empty();
    }

    private Optional<Object> roleArgument(String objectType, Object[] arguments) {
        if (objectType == null || objectType.isBlank() || arguments == null) {
            return Optional.empty();
        }
        return java.util.Arrays.stream(arguments)
                .filter(java.util.Objects::nonNull)
                .filter(argument -> objectType.equals(simpleName(argument.getClass().getName())))
                .findFirst();
    }

    private boolean containsIdentityMember(Object owner, Object member, String objectType,
                                           RepositoryHydratingServiceProxy.RoleIdentity identity) {
        if (owner == null || member == null) {
            return false;
        }
        if (matchesIdentityMember(owner, member, objectType, identity)) {
            return true;
        }
        Class<?> ownerType = owner.getClass();
        for (Method method : ownerType.getMethods()) {
            if (method.getParameterCount() != 0
                    || method.getDeclaringClass() == Object.class
                    || "getClass".equals(method.getName())) {
                continue;
            }
            String name = method.getName();
            if (!name.startsWith("get") && !name.startsWith("is")) {
                continue;
            }
            try {
                if (memberValueContains(method.invoke(owner), member, objectType, identity)) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try fields below.
            }
        }
        Class<?> current = ownerType;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    if (memberValueContains(field.get(owner), member, objectType, identity)) {
                        return true;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Ignore inaccessible fields and keep scanning.
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private boolean memberValueContains(Object value, Object member, String objectType,
                                        RepositoryHydratingServiceProxy.RoleIdentity identity) {
        if (value == null) {
            return false;
        }
        if (matchesIdentityMember(value, member, objectType, identity)) {
            return true;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> matchesIdentityMember(item, member, objectType, identity));
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                if (matchesIdentityMember(item, member, objectType, identity)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesIdentityMember(Object candidate, Object member, String objectType,
                                          RepositoryHydratingServiceProxy.RoleIdentity identity) {
        return candidate == member || Objects.equals(candidate, member)
                || sameRoleIdentity(candidate, objectType, identity);
    }

    private boolean sameRoleIdentity(Object candidate, String objectType,
                                     RepositoryHydratingServiceProxy.RoleIdentity identity) {
        if (candidate == null || objectType == null || objectType.isBlank() || identity == null
                || !objectType.equals(simpleName(candidate.getClass().getName()))) {
            return false;
        }
        return roleIdentityResolver.resolve(objectType, new Object[] {candidate})
                .filter(candidateIdentity -> candidateIdentity.complete()
                        && candidateIdentity.keyValues().equals(identity.keyValues()))
                .isPresent();
    }

    private Service decorateObjectSnapshots(Service service) {
        if (!(service instanceof ObjectSnapshotRuleProvider provider)
                || !(service instanceof TransactionalService)) {
            return service;
        }
        List<ObjectSnapshotRule> rules = provider.getObjectSnapshotRules();
        if (rules == null || rules.isEmpty()) {
            return service;
        }
        TransactionalService transactionalService = (TransactionalService) service;
        ObjectSnapshotParticipant snapshotParticipant =
                new ObjectSnapshotParticipant(transactionalService.getTransactionResourceId(), this::getRepository)
                        .resolveRoleIdentities(roleIdentityResolver)
                        .rules(rules, currentObjectResolver);
        return TransactionParticipantProxy.decorate(service, participant ->
                new SnapshotAwareTransactionParticipant(participant, snapshotParticipant));
    }

    private Service decorateTccReservations(Service service) {
        if (!(service instanceof TccReservationRuleProvider provider)
                || !(service instanceof TransactionalService)) {
            return service;
        }
        List<TccReservationRule> rules = provider.getTccReservationRules();
        if (rules == null || rules.isEmpty()) {
            return service;
        }
        TransactionalService transactionalService = (TransactionalService) service;
        TccReservationParticipant reservationParticipant =
                new TccReservationParticipant(transactionalService.getTransactionResourceId(),
                        service.getName(), rules, this::getRepository, tccReservationStoreSupplier,
                        tccReservationTtlMillisSupplier)
                        .resolveRoleIdentities(roleIdentityResolver);
        return TransactionParticipantProxy.decorate(service, participant ->
                new ChainedTransactionParticipant(participant.getResourceId(), reservationParticipant, participant));
    }

    private ServiceDefinition serviceDefinition(String serviceName, String className) {
        try {
            return new ServiceDefinition(serviceName, Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load generated service " + serviceName
                    + " from " + className, e);
        }
    }

    private Service instantiate(ServiceDefinition definition) {
        try {
            Object service = definition.type().getDeclaredConstructor().newInstance();
            if (!(service instanceof Service)) {
                throw new IllegalStateException(definition.type().getName() + " is not a Service");
            }
            return (Service) service;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate generated service " + definition.serviceName()
                    + " from " + definition.type().getName(), e);
        }
    }

    private String stripLeadingSlash(String resourceName) {
        return resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
    }

    private static String simpleName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "";
        }
        int index = typeName.lastIndexOf('.');
        return index < 0 ? typeName : typeName.substring(index + 1);
    }

    private static Object readPath(Object value, String path) {
        Object current = value;
        if (path == null || path.isBlank()) {
            return current;
        }
        for (String property : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = readProperty(current, property);
        }
        return current;
    }

    private static Object readProperty(Object value, String property) {
        Class<?> type = value.getClass();
        String accessorSuffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String methodName : List.of("get" + accessorSuffix, "is" + accessorSuffix, property)) {
            try {
                Method method = type.getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next JavaBean style before falling back to field access.
            }
        }
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(property);
                field.setAccessible(true);
                return field.get(value);
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("Cannot read property " + property + " from " + type.getName());
    }

    private static String toJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize generated service object snapshot", e);
        }
    }

    private record ServiceDefinition(String serviceName, Class<?> type) {
    }
}
