package com.servpp.service.transaction;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.servpp.service.repository.CompositeObjectId;
import com.servpp.service.repository.RepositoryHydratingServiceProxy;

public class QuantumConservationParticipant extends ObjectSnapshotTransactionParticipant {
    private static final String LEDGER_ATTRIBUTE = QuantumConservationParticipant.class.getName() + ".ledger";

    private final List<QuantumAttribute> attributes = new ArrayList<>();
    private final List<CollectionAttribute> collectionAttributes = new ArrayList<>();
    private int scalarAttributeCount;
    private List<QuantumValue> beforeValues = List.of();
    private Object[] arguments = new Object[0];
    private RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver =
            RepositoryHydratingServiceProxy.RoleIdentityResolver.none();
    private ObjectIdentityResolver objectIdentityResolver = ObjectIdentityResolver.none();

    public QuantumConservationParticipant(String resourceId) {
        super(resourceId);
    }

    public static List<QuantumConservationRecord> getRecords(TransactionContext context) {
        if (context == null) {
            return List.of();
        }
        return context.getOrCreateAttribute(LEDGER_ATTRIBUTE, ConservationLedger::new).records();
    }

    public QuantumConservationParticipant conserve(String groupId, String attributeId, Supplier<? extends Number> reader) {
        attributes.add(new QuantumAttribute(groupId, attributeId, reader, -1, null, null));
        scalarAttributeCount++;
        return this;
    }

    public QuantumConservationParticipant conserveArgument(int argumentIndex, String groupId, String attributeId,
                                                           String propertyName) {
        attributes.add(new QuantumAttribute(groupId, attributeId, () -> readNumberProperty(argumentIndex, propertyName),
                argumentIndex, propertyName, null));
        scalarAttributeCount++;
        return this;
    }

    /** Each collection member conserves its own group, without cancelling another member's deficit. */
    public QuantumConservationParticipant conserveEachArgument(int argumentIndex, String groupId,
                                                               String attributeId, String propertyName) {
        collectionAttributes.add(new CollectionAttribute(argumentIndex, groupId, attributeId, propertyName));
        return this;
    }

    public QuantumConservationParticipant resolveRoleIdentities(
            RepositoryHydratingServiceProxy.RoleIdentityResolver roleIdentityResolver) {
        this.roleIdentityResolver = roleIdentityResolver == null
                ? RepositoryHydratingServiceProxy.RoleIdentityResolver.none()
                : roleIdentityResolver;
        return this;
    }

    public QuantumConservationParticipant resolveObjectIdentities(ObjectIdentityResolver objectIdentityResolver) {
        this.objectIdentityResolver = objectIdentityResolver == null
                ? ObjectIdentityResolver.none()
                : objectIdentityResolver;
        return this;
    }

    @Override
    public void setArguments(Object[] arguments) {
        super.setArguments(arguments);
        this.arguments = arguments == null ? new Object[0] : arguments;
    }

    @Override
    public void beforeExecute(TransactionContext context) {
        expandCollectionAttributes();
        syncCurrentValuesFromStore(context);
        super.beforeExecute(context);
        beforeValues = readValues();
    }

    @Override
    public void afterExecute(TransactionContext context) {
        super.afterExecute(context);
        ConservationLedger ledger = context.getOrCreateAttribute(LEDGER_ATTRIBUTE, ConservationLedger::new);
        List<QuantumValue> afterValues = readValues();
        for (int i = 0; i < attributes.size(); i++) {
            QuantumAttribute attribute = attributes.get(i);
            BigDecimal before = beforeValues.get(i).value();
            BigDecimal after = afterValues.get(i).value();
            Object participant = attribute.collectionMember() != null ? attribute.collectionMember()
                    : attribute.isArgumentProperty() ? arguments[attribute.argumentIndex()] : null;
            var bound = QuantumInvocationBounds.find(participant, attribute.propertyName());
            QuantumConservationRecord record = ledger.add(attribute.groupId(), attribute.attributeId(),
                    objectId(attribute), before, after, attribute.collectionMember() != null,
                    bound == null ? null : QuantumInvocationBounds.decimal(bound.minimum()),
                    bound == null ? null : QuantumInvocationBounds.decimal(bound.maximum()));
            QuantumParticipantResults.register(context, participant, attribute.propertyName(), attribute.attributeId(), objectId(attribute),
                    value -> writeNumberProperty(participant, attribute.propertyName(), value));
            QuantumEntanglementBoundary boundary = context.getQuantumEntanglementBoundary();
            if (boundary != null) {
                boundary.addRecord(record);
            }
        }
    }

    @Override
    public void commit(TransactionContext context) {
        super.commit(context);
        ConservationLedger ledger = context.getOrCreateAttribute(LEDGER_ATTRIBUTE, ConservationLedger::new);
        ledger.validate();
    }

    private void expandCollectionAttributes() {
        attributes.subList(scalarAttributeCount, attributes.size()).clear();
        for (CollectionAttribute definition : collectionAttributes) {
            Object collection = arguments[definition.argumentIndex()];
            if (collection == null) throw new IllegalStateException("QUANTUM_COLLECTION_REQUIRED");
            List<Object> members = new ArrayList<>();
            if (collection instanceof Iterable<?> values) values.forEach(members::add);
            else if (collection.getClass().isArray()) {
                for (int i = 0; i < java.lang.reflect.Array.getLength(collection); i++)
                    members.add(java.lang.reflect.Array.get(collection, i));
            } else throw new IllegalStateException("QUANTUM_COLLECTION_REQUIRED");
            java.util.Set<String> identities = new java.util.HashSet<>();
            for (Object member : members) {
                if (member == null) throw new IllegalStateException("QUANTUM_PARTICIPANT_REQUIRED");
                QuantumAttribute attribute = new QuantumAttribute(definition.groupId(), definition.attributeId(),
                        () -> readNumberProperty(member, definition.propertyName()), definition.argumentIndex(),
                        definition.propertyName(), member);
                String identity = objectId(attribute);
                if (JdbcQuantumJournalStore.DEFAULT_OBJECT_ID.equals(identity) || !identities.add(identity))
                    throw new IllegalStateException("QUANTUM_COLLECTION_IDENTITY_REQUIRED_OR_DUPLICATED");
                attributes.add(attribute);
            }
        }
    }

    private Object participant(QuantumAttribute attribute) {
        return attribute.collectionMember() != null ? attribute.collectionMember() : arguments[attribute.argumentIndex()];
    }

    private List<QuantumValue> readValues() {
        List<QuantumValue> values = new ArrayList<>(attributes.size());
        for (QuantumAttribute attribute : attributes) {
            values.add(new QuantumValue(toBigDecimal(attribute.reader().get())));
        }
        return values;
    }

    private BigDecimal toBigDecimal(Number value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        return BigDecimal.valueOf(value.doubleValue());
    }

    private Number readNumberProperty(int argumentIndex, String propertyName) {
        if (argumentIndex < 0 || argumentIndex >= arguments.length || arguments[argumentIndex] == null) {
            return BigDecimal.ZERO;
        }
        return readNumberProperty(arguments[argumentIndex], propertyName);
    }

    private Number readNumberProperty(Object argument, String propertyName) {
        try {
            Object value = readProperty(argument, propertyName);
            if (value == null) {
                return BigDecimal.ZERO;
            }
            if (!(value instanceof Number number)) {
                throw new IllegalStateException("Quantum property " + propertyName + " is not a number");
            }
            return number;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read quantum property " + propertyName
                    + " on " + argument.getClass().getName(), e);
        }
    }

    private void syncCurrentValuesFromStore(TransactionContext context) {
        if (!(context.getQuantumJournalStore() instanceof JdbcQuantumJournalStore store)) {
            return;
        }
        for (QuantumAttribute attribute : attributes) {
            if (!attribute.isArgumentProperty()) {
                continue;
            }
            String identity = objectId(attribute);
            Optional<BigDecimal> currentValue = getRecords(context).stream()
                    .filter(record -> record.getAttributeId().equals(attribute.attributeId()) && record.getObjectId().equals(identity))
                    .reduce((previous,next) -> next).map(QuantumConservationRecord::getAfterValue);
            if (currentValue.isEmpty()) currentValue = store.findCurrentValueForUpdate(attribute.attributeId(), identity);
            currentValue.ifPresent(value -> writeNumberProperty(participant(attribute), attribute.propertyName(), value));
        }
    }

    private String objectId(QuantumAttribute attribute) {
        String objectType = objectType(attribute.attributeId());
        Object[] identityArguments = arguments;
        if (attribute.collectionMember() != null) {
            identityArguments = arguments.clone();
            identityArguments[attribute.argumentIndex()] = attribute.collectionMember();
        }
        Optional<String> objectIdentity = objectIdentityResolver.resolve(objectType, identityArguments)
                .filter(value -> !value.isBlank());
        if (objectIdentity.isPresent()) {
            return objectIdentity.orElseThrow();
        }
        Optional<RepositoryHydratingServiceProxy.RoleIdentity> identity =
                roleIdentityResolver.resolve(objectType, identityArguments)
                        .filter(item -> item.complete() && !item.keyValues().isEmpty());
        if (identity.isEmpty()) {
            return JdbcQuantumJournalStore.DEFAULT_OBJECT_ID;
        }
        List<String> values = new ArrayList<>();
        for (Object value : identity.orElseThrow().keyValues()) {
            if (value == null || value.toString().isBlank()) {
                return JdbcQuantumJournalStore.DEFAULT_OBJECT_ID;
            }
            values.add(value.toString());
        }
        return values.size() == 1 ? values.get(0) : CompositeObjectId.encode(values);
    }

    @FunctionalInterface
    public interface ObjectIdentityResolver {
        Optional<String> resolve(String objectType, Object[] arguments);

        static ObjectIdentityResolver none() {
            return (objectType, arguments) -> Optional.empty();
        }
    }

    private String objectType(String attributeId) {
        int index = attributeId == null ? -1 : attributeId.indexOf('.');
        return index < 0 ? attributeId : attributeId.substring(0, index);
    }

    private void writeNumberProperty(Object argument, String propertyName, BigDecimal value) {
        try { writeProperty(argument, propertyName, value); }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot write quantum property " + propertyName
                    + " on " + argument.getClass().getName(), e);
        }
    }

    private Object readProperty(Object target, String propertyName) throws ReflectiveOperationException {
        try {
            Method getter = target.getClass().getMethod(propertyMethodName("get", propertyName));
            return getter.invoke(target);
        } catch (NoSuchMethodException e) {
            Field field = target.getClass().getField(propertyName);
            return field.get(target);
        }
    }

    private void writeProperty(Object target, String propertyName, BigDecimal value) throws ReflectiveOperationException {
        try {
            Method getter = target.getClass().getMethod(propertyMethodName("get", propertyName));
            Method setter = target.getClass().getMethod(propertyMethodName("set", propertyName), getter.getReturnType());
            setter.invoke(target, toPropertyValue(value, getter.getReturnType()));
        } catch (NoSuchMethodException e) {
            Field field = target.getClass().getField(propertyName);
            field.set(target, toPropertyValue(value, field.getType()));
        }
    }

    private String propertyMethodName(String prefix, String propertyName) {
        return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private Object toPropertyValue(BigDecimal value, Class<?> propertyType) {
        if (propertyType == BigDecimal.class) {
            return value;
        }
        if (propertyType == BigInteger.class) return value.toBigIntegerExact();
        if (propertyType == Double.class || propertyType == double.class) {
            return value.doubleValue();
        }
        if (propertyType == Float.class || propertyType == float.class) {
            return value.floatValue();
        }
        if (propertyType == Long.class || propertyType == long.class) {
            return value.longValueExact();
        }
        if (propertyType == Integer.class || propertyType == int.class) {
            return value.intValueExact();
        }
        if (propertyType == Short.class || propertyType == short.class) {
            return value.shortValueExact();
        }
        if (propertyType == Byte.class || propertyType == byte.class) {
            return value.byteValueExact();
        }
        if (Number.class.isAssignableFrom(propertyType)) {
            return value;
        }
        throw new IllegalStateException("Quantum property type " + propertyType.getName() + " is not supported");
    }

    private record QuantumAttribute(String groupId, String attributeId, Supplier<? extends Number> reader,
                                    int argumentIndex, String propertyName, Object collectionMember) {
        private QuantumAttribute {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(attributeId, "attributeId");
            Objects.requireNonNull(reader, "reader");
        }

        private boolean isArgumentProperty() {
            return argumentIndex >= 0 && propertyName != null;
        }
    }

    private record CollectionAttribute(int argumentIndex, String groupId, String attributeId, String propertyName) { }

    private record QuantumValue(BigDecimal value) {
    }

    private static class ConservationLedger {
        private final Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final List<QuantumConservationRecord> records = new ArrayList<>();

        QuantumConservationRecord add(String groupId, String attributeId, String objectId,
                                      BigDecimal before, BigDecimal after, boolean perMember, BigDecimal minimum, BigDecimal maximum) {
            String conservationKey = perMember ? CompositeObjectId.encode(List.of(groupId, objectId)) : groupId;
            BigDecimal delta = after.subtract(before);
            if (QuantumConservationRecord.isConservedGroup(groupId)) {
                deltas.merge(conservationKey, delta, BigDecimal::add);
                counts.merge(conservationKey, 1, Integer::sum);
            }
            QuantumConservationRecord record =
                    new QuantumConservationRecord(groupId, attributeId, objectId, before, delta, after, minimum, maximum);
            records.add(record);
            return record;
        }

        List<QuantumConservationRecord> records() {
            return List.copyOf(records);
        }

        void validate() {
            for (Map.Entry<String, BigDecimal> entry : deltas.entrySet()) {
                String groupId = entry.getKey();
                if (counts.getOrDefault(groupId, 0) < 2) {
                    throw new IllegalStateException("quantum group " + groupId
                            + " must contain at least two quantum attributes");
                }
                if (entry.getValue().compareTo(BigDecimal.ZERO) != 0) {
                    throw new IllegalStateException("quantum group " + groupId
                            + " is not conserved: " + entry.getValue());
                }
            }
        }
    }
}
