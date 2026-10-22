package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.runtime.AppDdlCatalog;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import java.beans.Introspector;
import java.math.BigDecimal;
import java.util.*;

/** Ordinary snapshots exclude quantum facts; reads hydrate those facts from the chosen ledger. */
final class QuantumRepositoryFacts {
    private final ObjectMapper mapper;
    private final JdbcQuantumJournalStore ledger;
    private final AppDdlCatalog catalog;
    private final JdbcQuantumOpeningTasks openingTasks;
    private final Map<String, Set<String>> attributes = new HashMap<>();

    QuantumRepositoryFacts(ObjectMapper mapper, JdbcQuantumJournalStore ledger,
                           AppDdlCatalog catalog, Collection<String> attributeIds, JdbcObjectSnapshotStore store) {
        this.mapper = mapper; this.ledger = ledger; this.catalog = catalog;
        openingTasks = ledger.getDataSource() == store.getDataSource() ? null
                : new JdbcQuantumOpeningTasks(store, ledger);
        recoverOpenings();
        for (String id : attributeIds) {
            int separator = id.lastIndexOf('.');
            if (separator <= 0) throw new IllegalArgumentException("QUANTUM_ATTRIBUTE_PATH_REQUIRED");
            attributes.computeIfAbsent(id.substring(0, separator), ignored -> new HashSet<>())
                    .add(id.substring(separator + 1));
        }
    }

    Object ordinary(Object value) {
        return project(value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Object project(Object value, Set<Object> seen) {
        if (value == null || simple(value)) return value;
        if (!seen.add(value)) throw new IllegalStateException("PERSISTENCE_CYCLIC_FACT_GRAPH_UNSUPPORTED");
        try {
            if (value instanceof Iterable<?> values) {
                List<Object> result = new ArrayList<>(); values.forEach(item -> result.add(project(item, seen))); return result;
            }
            if (value.getClass().isArray()) {
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) result.add(project(java.lang.reflect.Array.get(value, i), seen));
                return result;
            }
            if (value instanceof Map<?, ?> values) {
                Map<String,Object> result = new LinkedHashMap<>();
                values.forEach((key, item) -> result.put(key.toString(), project(item, seen))); return result;
            }
            Map<String,Object> result = new LinkedHashMap<>();
            Set<String> excluded = attributes.getOrDefault(value.getClass().getSimpleName(), Set.of());
            Set<String> snapshots = snapshotProperties(value.getClass().getSimpleName());
            for (var property : Introspector.getBeanInfo(value.getClass(), Object.class).getPropertyDescriptors())
                if (property.getReadMethod() != null && !excluded.contains(property.getName()))
                    result.put(property.getName(), snapshots.contains(property.getName())
                            ? mapper.convertValue(property.getReadMethod().invoke(value), Object.class)
                            : project(property.getReadMethod().invoke(value), seen));
            return result;
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("PERSISTENCE_FACT_PROJECTION_FAILED", failure);
        } finally { seen.remove(value); }
    }

    void initialize(String type, String id, Object value) {
        for (String property : attributes.getOrDefault(type, Set.of())) {
            Object number = ObjectPropertyAccess.read(value, property);
            // Ordinary insert establishes a zero ledger fact when the entrance omits it.
            if (number == null) number = BigDecimal.ZERO;
            if (!(number instanceof Number)) throw new IllegalStateException("QUANTUM_INITIAL_VALUE_REQUIRED");
            if (new BigDecimal(number.toString()).signum() == 0) {
                if (openingTasks == null) ledger.initializeZeroValueIfAbsent(type + "." + property, id);
                else openingTasks.enqueue(type, id, type + "." + property);
            } else if (com.servpp.service.runtime.DatabaseInitializationContext.isActive())
                ledger.initializeValueIfAbsent(type + "." + property, id, new BigDecimal(number.toString()));
            else ledger.insertInitialValue(type + "." + property, id, new BigDecimal(number.toString()));
        }
    }

    void recoverOpenings() { if (openingTasks != null) openingTasks.recover(); }

    void hydrate(String type, String id, Object value) {
        recoverOpenings();
        hydrate(type, id, value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void hydrate(String type, String id, Object value, Set<Object> seen) {
        if (value == null || simple(value) || !seen.add(value)) return;
        try {
            if (value instanceof Iterable<?> values) { values.forEach(item -> hydrate(null, null, item, seen)); return; }
            if (value.getClass().isArray()) {
                for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) hydrate(null, null, java.lang.reflect.Array.get(value, i), seen);
                return;
            }
            String actualType = type == null ? value.getClass().getSimpleName() : type;
            Set<String> quantum = attributes.getOrDefault(actualType, Set.of());
            Set<String> snapshots = snapshotProperties(actualType);
            String identity = quantum.isEmpty() ? id : id == null ? identity(actualType, value) : id;
            for (var property : Introspector.getBeanInfo(value.getClass(), Object.class).getPropertyDescriptors()) {
                if (snapshots.contains(property.getName())) continue;
                if (quantum.contains(property.getName())) {
                    if (property.getWriteMethod() == null) throw new IllegalStateException("QUANTUM_FACT_UNWRITABLE");
                    BigDecimal number = ledger.findCurrentValue(actualType + "." + property.getName(), identity)
                            .orElseGet(() -> {
                                if (openingTasks != null && openingTasks.pending(actualType, identity,
                                        actualType + "." + property.getName())) return BigDecimal.ZERO;
                                throw new IllegalStateException("QUANTUM_FACT_NOT_PROVISIONED");
                            });
                    Class<?> target = property.getPropertyType();
                    Object converted = target == java.math.BigInteger.class ? number.toBigIntegerExact()
                            : target == BigDecimal.class ? number : mapper.convertValue(number, target);
                    property.getWriteMethod().invoke(value, converted);
                } else if (property.getReadMethod() != null) hydrate(null, null, property.getReadMethod().invoke(value), seen);
            }
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("QUANTUM_FACT_HYDRATION_FAILED", failure);
        }
    }

    private Set<String> snapshotProperties(String type) {
        if (catalog == null) return Set.of();
        return catalog.tableForBusinessType(type)
                .map(table -> new HashSet<>(table.snapshotFacts()))
                .orElseGet(HashSet::new);
    }

    private String identity(String type, Object value) {
        AppDdlCatalog.Table table = catalog.tableForBusinessType(type)
                .orElseThrow(() -> new IllegalStateException("QUANTUM_IDENTITY_MAPPING_REQUIRED"));
        if (table.objectBackedPrimaryKeys().isEmpty() && !table.platformPrimaryKeys().isEmpty()) return "singleton";
        List<String> keys = new ArrayList<>();
        for (String path : table.objectBackedPrimaryKeys()) {
            if (path.startsWith(type + ".")) path = path.substring(type.length() + 1);
            Object key = ObjectPropertyAccess.read(value, path);
            if (key == null || key.toString().isBlank()) throw new IllegalStateException("QUANTUM_FACT_IDENTITY_REQUIRED");
            keys.add(key.toString());
        }
        if (keys.isEmpty()) throw new IllegalStateException("QUANTUM_FACT_IDENTITY_REQUIRED");
        return keys.size() == 1 ? keys.get(0) : CompositeObjectId.encode(keys);
    }

    private boolean simple(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value.getClass().getName().startsWith("java.time.");
    }
}
