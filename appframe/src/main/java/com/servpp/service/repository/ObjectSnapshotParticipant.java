package com.servpp.service.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.servpp.service.transaction.ArgumentAwareTransactionParticipant;
import com.servpp.service.transaction.BusinessTraceContext;
import com.servpp.service.transaction.TransactionContext;

public class ObjectSnapshotParticipant implements ArgumentAwareTransactionParticipant {
    private final String resourceId;
    private final Supplier<ObjectRepository> repositorySupplier;
    private final List<SnapshotTarget> targets = new ArrayList<>();
    private RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver =
            RepositoryHydratingServiceProxy.RoleIdentityResolver.none();
    private Object[] arguments = new Object[0];

    public ObjectSnapshotParticipant(String resourceId, JdbcObjectSnapshotStore store) {
        this(resourceId, new StoreBackedObjectRepository(store));
    }

    public ObjectSnapshotParticipant(String resourceId, ObjectRepository repository) {
        this(resourceId, () -> repository);
    }

    public ObjectSnapshotParticipant(String resourceId, Supplier<ObjectRepository> repositorySupplier) {
        this.resourceId = resourceId;
        this.repositorySupplier = repositorySupplier;
    }

    public ObjectSnapshotParticipant resolveRoleIdentities(
            RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver) {
        this.roleIdentityResolver = roleIdentityResolver == null
                ? RepositoryHydratingServiceProxy.RoleIdentityResolver.none()
                : roleIdentityResolver;
        return this;
    }

    public <T> ObjectSnapshotParticipant argument(int index, String objectType,
                                                  Function<T, String> objectIdExtractor) {
        return object(objectType, arguments -> argument(arguments, index), objectIdExtractor,
                ObjectSnapshotRule.Timing.AFTER_COMMIT);
    }

    public <T> ObjectSnapshotParticipant object(String objectType, Function<Object[], T> objectResolver,
                                                Function<T, String> objectIdExtractor) {
        return object(objectType, objectResolver, objectIdExtractor, ObjectSnapshotRule.Timing.AFTER_COMMIT);
    }

    public <T> ObjectSnapshotParticipant object(String objectType, Function<Object[], T> objectResolver,
                                                Function<T, String> objectIdExtractor,
                                                ObjectSnapshotRule.Timing timing) {
        targets.add(new SnapshotTarget(objectType,
                (arguments, context, phase) -> objectResolver.apply(arguments),
                (value, arguments) -> objectIdExtractor.apply(cast(value)),
                timing));
        return this;
    }

    private <T> ObjectSnapshotParticipant object(String objectType, Function<Object[], T> objectResolver,
                                                 SnapshotObjectIdExtractor<T> objectIdExtractor,
                                                 ObjectSnapshotRule.Timing timing) {
        targets.add(new SnapshotTarget(objectType,
                (arguments, context, phase) -> objectResolver.apply(arguments),
                (value, arguments) -> objectIdExtractor.extract(cast(value), arguments),
                timing));
        return this;
    }

    public ObjectSnapshotParticipant rules(List<ObjectSnapshotRule> rules,
                                           Function<String, Object> currentObjectResolver) {
        for (ObjectSnapshotRule rule : rules) {
            rule(rule, currentObjectResolver);
        }
        return this;
    }

    public ObjectSnapshotParticipant rule(ObjectSnapshotRule rule, Function<String, Object> currentObjectResolver) {
        if (rule.source() == ObjectSnapshotRule.Source.ARGUMENT) {
            return object(rule.objectType(), arguments -> argument(arguments, rule.argumentIndex()),
                    (value, arguments) -> objectId(rule, value, arguments), rule.timing());
        }
        if (rule.source() == ObjectSnapshotRule.Source.TRANSACTION) {
            targets.add(new SnapshotTarget(rule.objectType(),
                    (arguments, context, phase) -> transactionSnapshot(rule.objectType(), context, phase),
                    (value, arguments) -> ((TransactionSnapshotEvidence) value).globalSerialNumber(),
                    rule.timing()));
            return this;
        }
        return object(rule.objectType(), arguments -> CurrentObjectContext.find(rule.objectType(), rule.objectIdPath())
                        .orElseGet(() -> currentObjectResolver.apply(rule.objectType())),
                (value, arguments) -> objectId(rule, value, arguments), rule.timing());
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void setArguments(Object[] arguments) {
        this.arguments = arguments == null ? new Object[0] : arguments;
    }

    @Override
    public void beforeExecute(TransactionContext context) {
        saveTargets(ObjectSnapshotRule.Timing.BEFORE, context);
    }

    @Override
    public void afterExecute(TransactionContext context) {
        saveTargets(ObjectSnapshotRule.Timing.AFTER, context);
    }

    @Override
    public void commit(TransactionContext context) {
        saveTargets(ObjectSnapshotRule.Timing.COMMIT, context);
    }

    private void saveTargets(ObjectSnapshotRule.Timing phase, TransactionContext context) {
        for (SnapshotTarget target : targets) {
            if (target.matches(phase)) {
                target.save(repositorySupplier.get(), arguments, context, phase);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T argument(Object[] arguments, int index) {
        return arguments.length > index ? (T) arguments[index] : null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private static String readString(Object value, String path) {
        Object resolved = readPath(value, path);
        return resolved == null ? null : resolved.toString();
    }

    private static String objectId(Object value, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        if (paths.size() == 1) {
            return readString(value, paths.get(0));
        }
        List<String> values = new ArrayList<>();
        for (String path : paths) {
            String item = readString(value, path);
            if (item == null || item.isBlank()) {
                return null;
            }
            values.add(item);
        }
        return CompositeObjectId.encode(values);
    }

    private String objectId(ObjectSnapshotRule rule, Object value, Object[] arguments) {
        Optional<RepositoryHydratingServiceProxy.RoleIdentity> identity =
                roleIdentityResolver.resolve(rule.objectType(), arguments)
                        .filter(item -> item.complete()
                                && item.keyValues().size() == rule.objectIdPaths().size());
        if (identity.isPresent()) {
            return objectIdFromValues(identity.orElseThrow().keyValues());
        }
        return objectId(value, rule.objectIdPaths());
    }

    private static String objectIdFromValues(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> stringValues = new ArrayList<>();
        for (Object value : values) {
            if (value == null || value.toString().isBlank()) {
                return null;
            }
            stringValues.add(value.toString());
        }
        return stringValues.size() == 1 ? stringValues.get(0) : CompositeObjectId.encode(stringValues);
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

    private TransactionSnapshotEvidence transactionSnapshot(String objectType, TransactionContext context,
                                                            ObjectSnapshotRule.Timing phase) {
        String globalSerialNumber = context == null || context.getGlobalSerialNumber() == null
                ? BusinessTraceContext.currentGlobalSerialNumber().orElse(null)
                : context.getGlobalSerialNumber();
        if (globalSerialNumber == null || globalSerialNumber.isBlank()) {
            throw new IllegalStateException("Object snapshot " + objectType
                    + " requires a current business trace id.");
        }
        return new TransactionSnapshotEvidence(globalSerialNumber, context == null ? null : context.getId(),
                resourceId, phase.name(), System.currentTimeMillis());
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

    @FunctionalInterface
    private interface SnapshotResolver {
        Object resolve(Object[] arguments, TransactionContext context, ObjectSnapshotRule.Timing phase);
    }

    @FunctionalInterface
    private interface SnapshotObjectIdExtractor<T> {
        String extract(T value, Object[] arguments);
    }

    private record SnapshotTarget(String objectType, SnapshotResolver objectResolver,
                                  SnapshotObjectIdExtractor<Object> objectIdExtractor,
                                  ObjectSnapshotRule.Timing timing) {
        private boolean matches(ObjectSnapshotRule.Timing phase) {
            return timing == phase
                    || timing == ObjectSnapshotRule.Timing.AFTER_COMMIT
                    && (phase == ObjectSnapshotRule.Timing.AFTER || phase == ObjectSnapshotRule.Timing.COMMIT);
        }

        private void save(ObjectRepository repository, Object[] arguments, TransactionContext context,
                          ObjectSnapshotRule.Timing phase) {
            Object value = objectResolver.resolve(arguments, context, phase);
            if (value == null) {
                throw new IllegalStateException("Object snapshot " + objectType
                        + " requires a non-null object.");
            }
            String objectId = objectIdExtractor.extract(value, arguments);
            if (objectId == null || objectId.isBlank()) {
                throw new IllegalStateException("Object snapshot " + objectType
                        + " requires a non-empty object id.");
            }
            repository.save(objectType, objectId, value);
        }
    }

    public record TransactionSnapshotEvidence(String globalSerialNumber, String transactionId, String resourceId, String phase,
                                              long capturedAtEpochMillis) {
        public String traceId() {
            return globalSerialNumber;
        }
    }

    private static class StoreBackedObjectRepository implements ObjectRepository {
        private final JdbcObjectSnapshotStore store;

        private StoreBackedObjectRepository(JdbcObjectSnapshotStore store) {
            this.store = store;
        }

        @Override
        public void save(String objectType, String objectId, Object value) {
            store.save(objectType, objectId, value);
        }

        @Override
        public Optional<?> find(String objectType, String objectId) {
            return store.find(objectType, objectId);
        }

        @Override
        public <T> T hydrate(String objectType, String objectId, T target) {
            throw new IllegalStateException("Object snapshot participant store adapter does not support hydration.");
        }
    }
}
