package com.servpp.service.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.servpp.service.repository.ObjectRepository;

public class ObjectRepositoryRemoteServiceAdapter implements MethodAwareRemoteServiceAdapter {
    private static final Object UNRESOLVED_ROLE_PERFORMER_PATH = new Object();

    private final String serviceName;
    private final Map<String, String> configuration;
    private final Supplier<ObjectRepository> repositorySupplier;

    public ObjectRepositoryRemoteServiceAdapter(PhysicalRuntimeCatalog.RemoteAdapter adapter,
                                                Supplier<ObjectRepository> repositorySupplier) {
        this(adapter.serviceName(), adapter.configuration(), repositorySupplier);
    }

    public ObjectRepositoryRemoteServiceAdapter(String serviceName, Map<String, String> configuration,
                                                Supplier<ObjectRepository> repositorySupplier) {
        this.serviceName = serviceName;
        this.configuration = Map.copyOf(configuration);
        this.repositorySupplier = repositorySupplier;
    }

    @Override
    public Set<String> serviceNames() {
        return Set.of(serviceName);
    }

    @Override
    public String implementationType() {
        return "objectRepositoryRemote";
    }

    @Override
    public Map<String, String> configuration() {
        return configuration;
    }

    @Override
    public List<String> validationErrors(String serviceName) {
        List<String> errors = new ArrayList<>();
        validateRequired(errors, serviceName, "objectType");
        validateObjectIdPathConfig(errors, serviceName);
        return List.copyOf(errors);
    }

    @Override
    public Object execute(String serviceName, Object... arguments) {
        return execute(serviceName, null, arguments);
    }

    @Override
    public Object execute(String serviceName, Method method, Object... arguments) {
        if (!this.serviceName.equals(serviceName)) {
            throw new IllegalArgumentException("Adapter for remote service " + this.serviceName
                    + " cannot execute " + serviceName + ".");
        }
        if (arguments == null || (arguments.length != 1 && arguments.length != 2) || arguments[0] == null) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " objectRepositoryRemote adapter requires one key participant and optionally one result participant.");
        }
        String objectType = requiredConfig("objectType");
        List<String> requestObjectIdPaths = requestObjectIdPaths();
        List<String> repositoryObjectIdPaths = repositoryObjectIdPaths(requestObjectIdPaths);
        List<Object> objectIds = readObjectIds(serviceName, arguments[0], requestObjectIdPaths);
        Class<?> returnType = method == null ? arguments[0].getClass() : method.getReturnType();
        if (arguments.length == 2 && returnType != Void.TYPE)
            throw new IllegalStateException("Explicit result participants require a declared void query method.");
        if (returnType == Void.TYPE) {
            Object participant = arguments[arguments.length - 1];
            if (participant == null) throw new IllegalStateException("PARTICIPANT_INSTANCE_REQUIRED");
            if (arguments.length == 2 && participant instanceof List<?> target) {
                Type resultType = method.getGenericParameterTypes()[1];
                if (!(resultType instanceof ParameterizedType parameterized)
                        || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> elementType))
                    throw new IllegalStateException("In-place remote query cannot resolve result participant type.");
                List<?> values = repository().findBy(objectType, repositoryObjectIdPaths, objectIds, elementType);
                ParticipantFacts.hydrateCollection(target, values);
                ParticipantIdentity.confirm(target, !values.isEmpty());
            } else {
                if (requestObjectIdPaths.size() != 1)
                    throw new IllegalStateException("In-place remote query requires one participant identity path.");
                Object found = repository().find(objectType, objectIds.get(0).toString(), participant.getClass()).orElse(null);
                if (found == null) ParticipantIdentity.confirm(participant, false);
                else {
                    ParticipantFacts.hydrate(participant, found);
                    ParticipantIdentity.confirmFacts(participant);
                    com.servpp.service.repository.RepositoryParticipantSnapshots.remember(participant);
                }
            }
            return null;
        }
        ObjectRepository repository = repository();
        Class<?> collectionElementType = collectionElementType(method, returnType);
        if (collectionElementType != null) {
            List<?> values = repositoryObjectIdPaths.size() == 1
                    ? repository.findBy(objectType, repositoryObjectIdPaths.get(0),
                            objectIds.get(0), collectionElementType)
                    : repository.findBy(objectType, repositoryObjectIdPaths, objectIds, collectionElementType);
            if (returnType.isArray()) {
                Object array = Array.newInstance(collectionElementType, values.size());
                for (int i = 0; i < values.size(); i++) {
                    Array.set(array, i, values.get(i));
                }
                return array;
            }
            return values;
        }
        if (requestObjectIdPaths.size() != 1) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " objectRepositoryRemote adapter single-object lookup requires exactly one object id path.");
        }
        Object objectId = objectIds.get(0);
        if (returnType.isInstance(arguments[0])) {
            try {
                repository.hydrate(objectType, objectId.toString(), arguments[0]);
            } catch (IllegalStateException e) {
                if (!arguments[0].getClass().isRecord()) {
                    throw e;
                }
            }
        }
        return repository.require(objectType, objectId.toString(), returnType);
    }

    private ObjectRepository repository() {
        ObjectRepository repository = repositorySupplier == null ? null : repositorySupplier.get();
        if (repository == null) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " objectRepositoryRemote adapter requires an ObjectRepository.");
        }
        return repository;
    }

    private List<Object> readObjectIds(String serviceName, Object request, List<String> objectIdPaths) {
        List<Object> values = new ArrayList<>();
        for (String objectIdPath : objectIdPaths) {
            Object objectId = readPath(request, objectIdPath);
            if (objectId == null || objectId.toString().isBlank()) {
                throw new IllegalStateException("Remote service " + serviceName
                        + " object id path " + objectIdPath + " is required.");
            }
            values.add(objectId);
        }
        return values;
    }

    private Class<?> collectionElementType(Method method, Class<?> returnType) {
        if (returnType.isArray()) {
            return returnType.getComponentType();
        }
        if (!List.class.isAssignableFrom(returnType) || method == null) {
            return null;
        }
        Type genericReturnType = method.getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType parameterizedType) {
            Type elementType = parameterizedType.getActualTypeArguments()[0];
            if (elementType instanceof Class<?> elementClass) {
                return elementClass;
            }
        }
        throw new IllegalStateException("Remote service " + serviceName
                + " objectRepositoryRemote adapter cannot resolve List return element type.");
    }

    private void validateRequired(List<String> errors, String serviceName, String key) {
        String value = configuration.get(key);
        if (value == null || value.isBlank()) {
            errors.add("Remote service " + serviceName
                    + " objectRepositoryRemote adapter must configure " + key + ".");
        }
    }

    private void validateObjectIdPathConfig(List<String> errors, String serviceName) {
        boolean hasSinglePath = hasText(configuration.get("objectIdPath"));
        List<String> objectIdPaths = configuredPaths("objectIdPaths");
        if (!hasSinglePath && objectIdPaths.isEmpty()) {
            errors.add("Remote service " + serviceName
                    + " objectRepositoryRemote adapter must configure objectIdPath or objectIdPaths.");
        }
        if (configuration.containsKey("objectIdPath") && !hasSinglePath
                && !configuration.containsKey("objectIdPaths")) {
            errors.add("Remote service " + serviceName
                    + " objectRepositoryRemote adapter must configure objectIdPath.");
        }
        if (configuration.containsKey("objectIdPaths") && objectIdPaths.isEmpty()) {
            errors.add("Remote service " + serviceName
                    + " objectRepositoryRemote adapter config objectIdPaths must contain at least one path.");
        }
        List<String> repositoryObjectIdPaths = configuredPaths("repositoryObjectIdPaths");
        validatePathSegments(errors, serviceName, "objectIdPath", configuration.get("objectIdPath"));
        validatePathSegments(errors, serviceName, "objectIdPaths", configuration.get("objectIdPaths"));
        validatePathSegments(errors, serviceName, "repositoryObjectIdPaths",
                configuration.get("repositoryObjectIdPaths"));
        if (configuration.containsKey("repositoryObjectIdPaths")) {
            if (repositoryObjectIdPaths.isEmpty()) {
                errors.add("Remote service " + serviceName
                        + " objectRepositoryRemote adapter config repositoryObjectIdPaths"
                        + " must contain at least one path.");
            } else {
                int requestPathCount = objectIdPaths.isEmpty() ? 1 : objectIdPaths.size();
                if (repositoryObjectIdPaths.size() != requestPathCount) {
                    errors.add("Remote service " + serviceName
                            + " objectRepositoryRemote adapter config repositoryObjectIdPaths"
                            + " must have the same number of paths as objectIdPath/objectIdPaths.");
                }
            }
        }
    }

    private void validatePathSegments(List<String> errors, String serviceName, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String path : value.split(",", -1)) {
            String trimmedPath = path.trim();
            if (trimmedPath.isEmpty()) {
                errors.add("Remote service " + serviceName
                        + " objectRepositoryRemote adapter config " + key
                        + " must not contain blank paths.");
                return;
            }
            for (String segment : trimmedPath.split("\\.", -1)) {
                if (segment.isBlank()) {
                    errors.add("Remote service " + serviceName
                            + " objectRepositoryRemote adapter config " + key
                            + " path " + trimmedPath + " must not contain blank segments.");
                    return;
                }
            }
        }
    }

    private String requiredConfig(String key) {
        String value = configuration.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " objectRepositoryRemote adapter must configure " + key + ".");
        }
        return value;
    }

    private List<String> requestObjectIdPaths() {
        List<String> objectIdPaths = configuredPaths("objectIdPaths");
        if (!objectIdPaths.isEmpty()) {
            return objectIdPaths;
        }
        return List.of(requiredConfig("objectIdPath"));
    }

    private List<String> repositoryObjectIdPaths(List<String> requestObjectIdPaths) {
        List<String> repositoryObjectIdPaths = configuredPaths("repositoryObjectIdPaths");
        if (!repositoryObjectIdPaths.isEmpty()) {
            if (repositoryObjectIdPaths.size() != requestObjectIdPaths.size()) {
                throw new IllegalStateException("Remote service " + serviceName
                        + " objectRepositoryRemote adapter config repositoryObjectIdPaths"
                        + " must have the same number of paths as objectIdPath/objectIdPaths.");
            }
            return repositoryObjectIdPaths;
        }
        return requestObjectIdPaths;
    }

    private List<String> configuredPaths(String key) {
        String value = configuration.get(key);
        if (!hasText(value)) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String path : value.split(",")) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return List.copyOf(paths);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Object readPath(Object value, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " objectRepositoryRemote adapter object id path must not be blank.");
        }
        Object rolePerformerValue = readRolePerformerPath(value, path);
        if (rolePerformerValue != UNRESOLVED_ROLE_PERFORMER_PATH) {
            return rolePerformerValue;
        }
        Object current = value;
        String[] properties = path.split("\\.", -1);
        for (int i = 0; i < properties.length; i++) {
            String property = properties[i];
            if (property.isBlank()) {
                throw new IllegalStateException("Remote service " + serviceName
                        + " objectRepositoryRemote adapter object id path " + path
                        + " must not contain blank segments.");
            }
            if (current == null) {
                return null;
            }
            try {
                current = readProperty(current, property);
            } catch (IllegalStateException e) {
                if (i == 0 && properties.length > 1) {
                    throw new IllegalStateException("Remote service " + serviceName
                            + " objectRepositoryRemote adapter object id path " + path
                            + " is not readable from " + value.getClass().getName()
                            + " or attached Role performer identity " + property + ".", e);
                }
                throw e;
            }
        }
        return current;
    }

    private Object readRolePerformerPath(Object value, String path) {
        if (value == null || path == null || !path.contains(".")) {
            return UNRESOLVED_ROLE_PERFORMER_PATH;
        }
        String[] segments = path.split("\\.", 2);
        if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
            return UNRESOLVED_ROLE_PERFORMER_PATH;
        }
        String performerRuntimeName = segments[0];
        String performerPath = segments[1];
        return RoleIdentityContext.performers(value, value.getClass().getSimpleName())
                .flatMap(performers -> performers.stream()
                        .filter(performer -> performer != null
                                && performerRuntimeName.equals(runtimeName(performer.getClass().getSimpleName())))
                        .findFirst())
                .map(performer -> readPath(performer, performerPath))
                .orElse(UNRESOLVED_ROLE_PERFORMER_PATH);
    }

    private String runtimeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "";
        }
        return Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
    }

    private Object readProperty(Object value, String property) {
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
}
