package com.servpp.service.transaction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.servpp.service.repository.CompositeObjectId;
import com.servpp.service.repository.JdbcObjectRepository;
import com.servpp.service.repository.ObjectRepository;
import com.servpp.service.repository.RepositoryHydratingServiceProxy;

public class TccReservationParticipant implements ArgumentAwareTransactionParticipant, TccTransactionParticipant {
    private final String resourceId;
    private final String actionName;
    private final List<TccReservationRule> rules;
    private final Supplier<ObjectRepository> repositorySupplier;
    private final Supplier<TccReservationStore> reservationStoreSupplier;
    private final Supplier<Long> reservationTtlMillisSupplier;
    private RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver =
            RepositoryHydratingServiceProxy.RoleIdentityResolver.none();
    private Object[] arguments = new Object[0];
    private final List<ReservationState> states = new ArrayList<>();

    public TccReservationParticipant(String resourceId, List<TccReservationRule> rules,
                                     Supplier<ObjectRepository> repositorySupplier) {
        this(resourceId, resourceId, rules, repositorySupplier, null, null);
    }

    public TccReservationParticipant(String resourceId, List<TccReservationRule> rules,
                                     Supplier<ObjectRepository> repositorySupplier,
                                     Supplier<TccReservationStore> reservationStoreSupplier) {
        this(resourceId, resourceId, rules, repositorySupplier, reservationStoreSupplier, null);
    }

    public TccReservationParticipant(String resourceId, String actionName, List<TccReservationRule> rules,
                                     Supplier<ObjectRepository> repositorySupplier,
                                     Supplier<TccReservationStore> reservationStoreSupplier) {
        this(resourceId, actionName, rules, repositorySupplier, reservationStoreSupplier, null);
    }

    public TccReservationParticipant(String resourceId, String actionName, List<TccReservationRule> rules,
                                     Supplier<ObjectRepository> repositorySupplier,
                                     Supplier<TccReservationStore> reservationStoreSupplier,
                                     Supplier<Long> reservationTtlMillisSupplier) {
        this.resourceId = resourceId;
        this.actionName = actionName;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.repositorySupplier = repositorySupplier;
        this.reservationStoreSupplier = reservationStoreSupplier;
        this.reservationTtlMillisSupplier = reservationTtlMillisSupplier;
    }

    public TccReservationParticipant resolveRoleIdentities(
            RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver) {
        this.roleIdentityResolver = roleIdentityResolver == null
                ? RepositoryHydratingServiceProxy.RoleIdentityResolver.none()
                : roleIdentityResolver;
        return this;
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
    public void beforeTry(TransactionContext context) {
        states.clear();
        for (TccReservationRule rule : rules) {
            Object sourceObject = objectArgument(rule.sourceAttributeId());
            BigDecimal sourceBefore = readNumber(sourceObject, objectPath(rule.sourceAttributeId()));
            states.add(new ReservationState(rule, sourceObject, sourceBefore, BigDecimal.ZERO));
        }
    }

    @Override
    public void afterTry(TransactionContext context) {
        for (int i = 0; i < states.size(); i++) {
            ReservationState state = states.get(i);
            BigDecimal sourceAfter = readNumber(state.sourceObject(), objectPath(state.rule().sourceAttributeId()));
            if (state.sourceBefore() == null || sourceAfter == null) {
                continue;
            }
            BigDecimal delta = state.sourceBefore().subtract(sourceAfter);
            if (BigDecimal.ZERO.compareTo(delta) == 0) {
                continue;
            }
            applyReservationDelta(state.rule(), state.sourceObject(), delta);
            String reservationId = reservationId(context, state.rule());
            String branchId = branchId(context);
            saveReservation(context, state.rule(), reservationId, delta, TccReservationStatus.TRYING,
                    "reserved " + delta.stripTrailingZeros().toPlainString(), branchId);
            states.set(i, new ReservationState(state.rule(), state.sourceObject(), state.sourceBefore(), delta,
                    reservationId, branchId));
        }
    }

    @Override
    public void confirm(TransactionContext context) {
        for (ReservationState state : states) {
            applyReservationDelta(state.rule(), state.sourceObject(), state.delta().negate());
            saveReservation(context, state.rule(), state.reservationId(), state.delta(),
                    TccReservationStatus.CONFIRMED, "confirmed", state.branchId());
        }
    }

    @Override
    public void cancel(TransactionContext context, Throwable cause) {
        for (ReservationState state : states) {
            applyReservationDelta(state.rule(), state.sourceObject(), state.delta().negate());
            saveReservation(context, state.rule(), state.reservationId(), state.delta(),
                    TccReservationStatus.CANCELLED,
                    cause == null || cause.getMessage() == null ? "cancelled" : cause.getMessage(),
                    state.branchId());
        }
    }

    private void saveReservation(TransactionContext context, TccReservationRule rule, String reservationId,
                                 BigDecimal quantity, TccReservationStatus status, String message, String branchId) {
        TccReservationStore store = reservationStoreSupplier == null ? null : reservationStoreSupplier.get();
        if (store == null || reservationId == null || quantity == null
                || BigDecimal.ZERO.compareTo(quantity) == 0) {
            return;
        }
        TccReservationRecord existing = store.find(reservationId).orElse(null);
        String expiresAt = existing == null ? expiresAt(status) : existing.expiresAt();
        store.save(new TccReservationRecord(reservationId,
                branchId,
                context == null ? "" : Objects.toString(context.getTraceId(), ""),
                context == null ? "" : Objects.toString(context.getId(), ""),
                resourceId,
                actionName(),
                rule.sourceAttributeId(),
                rule.reservationAttributeId(),
                quantity,
                status,
                message == null ? "" : message,
                JdbcTccReservationStore.updatedNow(),
                expiresAt,
                objectId(objectType(rule.reservationAttributeId()))));
    }

    private String reservationId(TransactionContext context, TccReservationRule rule) {
        String traceId = context == null ? "no-trace" : context.getTraceId();
        String transactionId = context == null ? "no-tx" : context.getId();
        return traceId + ":" + transactionId + ":" + resourceId + ":" + actionName() + ":"
                + rule.sourceAttributeId() + ":" + rule.reservationAttributeId();
    }

    private String branchId(TransactionContext context) {
        String traceId = context == null ? "no-trace" : context.getTraceId();
        return traceId + ":" + resourceId + ":" + actionName();
    }

    private String expiresAt(TccReservationStatus status) {
        if (status != TccReservationStatus.TRYING || reservationTtlMillisSupplier == null) {
            return null;
        }
        Long ttlMillis = reservationTtlMillisSupplier.get();
        if (ttlMillis == null || ttlMillis <= 0L) {
            return null;
        }
        return Instant.now().plusMillis(ttlMillis).toString();
    }

    private String actionName() {
        return actionName;
    }

    private void applyReservationDelta(TccReservationRule rule, Object value, BigDecimal delta) {
        if (value == null || BigDecimal.ZERO.compareTo(delta) == 0) {
            return;
        }
        writeNumber(value, objectPath(rule.reservationAttributeId()), delta);
        ObjectRepository repository = repositorySupplier == null ? null : repositorySupplier.get();
        if (repository instanceof JdbcObjectRepository jdbcObjectRepository) {
            String objectType = objectType(rule.reservationAttributeId());
            jdbcObjectRepository.adjustAppDdlPlatformColumn(objectType, objectId(objectType),
                    value, objectPath(rule.reservationAttributeId()), delta);
        }
    }

    private String objectId(String objectType) {
        Optional<RepositoryHydratingServiceProxy.RoleIdentity> identity =
                roleIdentityResolver.resolve(objectType, arguments)
                        .filter(item -> item.complete() && !item.keyValues().isEmpty());
        if (identity.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object value : identity.orElseThrow().keyValues()) {
            if (value == null || value.toString().isBlank()) {
                return null;
            }
            values.add(value.toString());
        }
        return values.size() == 1 ? values.get(0) : CompositeObjectId.encode(values);
    }

    private Object objectArgument(String attributeId) {
        String objectType = objectType(attributeId);
        for (Object argument : arguments) {
            if (argument != null && objectTypeMatches(argument.getClass().getSimpleName(), objectType)) {
                return argument;
            }
        }
        return null;
    }

    private boolean objectTypeMatches(String className, String objectType) {
        return objectType != null && (objectType.equals(className) || className.endsWith(objectType));
    }

    private BigDecimal readNumber(Object value, String path) {
        Object resolved = readPath(value, path);
        if (resolved instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (resolved instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (resolved instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private Object readPath(Object value, String path) {
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

    private Object readProperty(Object value, String property) {
        if (value == null || property == null || property.isBlank()) {
            return null;
        }
        Class<?> type = value.getClass();
        String accessorSuffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String methodName : List.of("get" + accessorSuffix, "is" + accessorSuffix, property)) {
            try {
                Method method = type.getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next JavaBean shape before falling back to field access.
            }
        }
        Field field = findField(type, property);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read property " + property + " from " + type.getName(), e);
        }
    }

    private void writeNumber(Object value, String path, BigDecimal delta) {
        Field field = findField(value.getClass(), path);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            Object current = field.get(value);
            field.set(value, add(current, delta, field.getType()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot write reservation property " + path
                    + " on " + value.getClass().getName(), e);
        }
    }

    private Object add(Object current, BigDecimal delta, Class<?> targetType) {
        BigDecimal value = (current instanceof Number number)
                ? BigDecimal.valueOf(number.doubleValue()).add(delta)
                : delta;
        if (targetType == int.class || targetType == Integer.class) {
            return value.intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return value.longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return value.doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return value.floatValue();
        }
        if (targetType == BigDecimal.class) {
            return value;
        }
        if (targetType == BigInteger.class) {
            return value.toBigInteger();
        }
        return current;
    }

    private Field findField(Class<?> type, String path) {
        if (path == null || path.isBlank() || path.contains(".")) {
            return null;
        }
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(path);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String objectType(String attributeId) {
        int index = attributeId == null ? -1 : attributeId.indexOf('.');
        return index < 0 ? attributeId : attributeId.substring(0, index);
    }

    private String objectPath(String attributeId) {
        int index = attributeId == null ? -1 : attributeId.indexOf('.');
        return index < 0 ? "" : attributeId.substring(index + 1);
    }

    private record ReservationState(TccReservationRule rule, Object sourceObject,
                                    BigDecimal sourceBefore, BigDecimal delta,
                                    String reservationId, String branchId) {
        private ReservationState(TccReservationRule rule, Object sourceObject,
                                 BigDecimal sourceBefore, BigDecimal delta) {
            this(rule, sourceObject, sourceBefore, delta, null, null);
        }
    }
}
