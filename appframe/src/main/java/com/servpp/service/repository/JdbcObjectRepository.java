package com.servpp.service.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.servpp.service.runtime.AppDdlCatalog;

public class JdbcObjectRepository implements ObjectRepository {
    private final JdbcObjectSnapshotStore store;
    private final ObjectMapper objectMapper;
    private JdbcAppDdlObjectTableWriter appDdlObjectTableWriter;
    private JdbcAppDdlObjectTableReader appDdlObjectTableReader;
    private AppDdlCatalog appDdlCatalog;
    private QuantumRepositoryFacts quantumFacts;
    private JdbcRoleRelationshipStore roleRelationships;
    private NativeUpdateExecutor nativeUpdates = NativeUpdateExecutor.jdbc();

    public JdbcObjectRepository useNativeUpdateExecutor(NativeUpdateExecutor executor) {
        nativeUpdates = Objects.requireNonNull(executor);
        if (appDdlObjectTableWriter != null)
            appDdlObjectTableWriter = new JdbcAppDdlObjectTableWriter(store.getDataSource(),appDdlCatalog,nativeUpdates);
        return this;
    }

    public void useQuantumFacts(com.servpp.service.transaction.JdbcQuantumJournalStore ledger, java.util.Collection<String> attributes) {
        quantumFacts = new QuantumRepositoryFacts(objectMapper, ledger, appDdlCatalog, attributes, store);
    }

    /** Replay durable technical opening tasks after restart or a transient ledger outage. */
    public void recoverQuantumOpenings() { if (quantumFacts != null) quantumFacts.recoverOpenings(); }

    private Object ordinary(Object value) {
        Object facts = quantumFacts == null ? value : quantumFacts.ordinary(value);
        return ParticipantStorageFacts.project(facts,value,appDdlCatalog,objectMapper);
    }
    private void hydrateQuantum(ObjectSnapshot snapshot, Object value) {
        if (quantumFacts != null) quantumFacts.hydrate(snapshot.objectType(), snapshot.objectId(), value);
    }

    public JdbcObjectRepository(JdbcObjectSnapshotStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public JdbcObjectSnapshotStore getStore() {
        return store;
    }
    @Override public Object transactionResourceIdentity() { return store.getDataSource(); }
    public boolean hasIndependentIdentity(String objectType) {
        if(appDdlCatalog==null)return false;
        var storage=appDdlCatalog.roleStorage().get(objectType);
        return storage!=null && storage.independent();
    }

    public JdbcObjectRepository useAppDdlCatalog(AppDdlCatalog catalog) {
        appDdlCatalog = catalog;
        roleRelationships = catalog == null ? null : new JdbcRoleRelationshipStore(store.getDataSource(),catalog,objectMapper);
        if (catalog == null || catalog.tables().isEmpty()) {
            this.appDdlObjectTableWriter = null;
            this.appDdlObjectTableReader = null;
        } else {
            this.appDdlObjectTableWriter = new JdbcAppDdlObjectTableWriter(store.getDataSource(), catalog, nativeUpdates);
            this.appDdlObjectTableReader = new JdbcAppDdlObjectTableReader(store.getDataSource(), catalog);
        }
        return this;
    }

    public boolean isAppDdlCatalogEnabled() {
        return appDdlObjectTableWriter != null;
    }

    public void adjustAppDdlPlatformColumn(String objectType, Object value, String columnName, BigDecimal delta) {
        adjustAppDdlPlatformColumn(objectType, null, value, columnName, delta);
    }

    public void adjustAppDdlPlatformColumn(String objectType, String objectId, Object value,
                                           String columnName, BigDecimal delta) {
        if (appDdlObjectTableWriter != null) {
            appDdlObjectTableWriter.adjustPlatformColumn(objectType, objectId, value, columnName, delta);
        }
    }

    public void writeAppDdlPlatformColumn(String objectType, Object value, String columnName, Object columnValue) {
        writeAppDdlPlatformColumn(objectType, null, value, columnName, columnValue);
    }

    public void writeAppDdlPlatformColumn(String objectType, String objectId, Object value,
                                          String columnName, Object columnValue) {
        if (appDdlObjectTableWriter != null) {
            appDdlObjectTableWriter.writePlatformColumn(objectType, objectId, value, columnName, columnValue);
        }
    }

    public Optional<Object> readAppDdlPlatformColumn(String objectType, Object value, String columnName) {
        if (appDdlObjectTableReader == null) {
            return Optional.empty();
        }
        return appDdlObjectTableReader.readPlatformColumn(objectType, value, columnName);
    }

    public Optional<Object> readAppDdlPlatformColumnByObjectId(String objectType, String objectId, String columnName) {
        if (appDdlObjectTableReader == null) {
            return Optional.empty();
        }
        return appDdlObjectTableReader.readPlatformColumnByObjectId(objectType, objectId, columnName);
    }

    @Override
    public void save(String objectType, String objectId, Object value) {
        store.save(objectType, objectId, ordinary(value));
        if (appDdlObjectTableWriter != null) {
            appDdlObjectTableWriter.save(objectType, objectId, ordinary(value));
        }
        saveRoleRelationships(objectType,objectId,value);
    }

    @Override public void verifyWriteTransaction() { store.verifyWriteTransaction(); }

    @Override public void insert(String objectType, String objectId, Object value) {
        if (com.servpp.service.runtime.DatabaseInitializationContext.isActive()) {
            insertIfAbsent(objectType, objectId, value);
            return;
        }
        store.insert(objectType, objectId, ordinary(value));
        if (appDdlObjectTableWriter != null) appDdlObjectTableWriter.save(objectType, objectId, ordinary(value));
        saveRoleRelationships(objectType,objectId,value);
        if (quantumFacts != null) quantumFacts.initialize(objectType, objectId, value);
        store.find(objectType, objectId).ifPresent(snapshot -> RepositoryParticipantSnapshots.loaded(value, this, snapshot));
        acknowledgeAfterCommit(value);
    }

    @Override public void insertIfAbsent(String objectType, String objectId, Object value) {
        if (store.insertIfAbsent(objectType,objectId,ordinary(value))) {
            if (appDdlObjectTableWriter != null) appDdlObjectTableWriter.save(objectType,objectId,ordinary(value));
            saveRoleRelationships(objectType,objectId,value);
            if (quantumFacts != null) quantumFacts.initialize(objectType, objectId, value);
            store.find(objectType, objectId).ifPresent(snapshot -> RepositoryParticipantSnapshots.loaded(value, this, snapshot));
            acknowledgeAfterCommit(value);
        } else if (com.servpp.service.runtime.DatabaseInitializationContext.isActive() && quantumFacts != null) {
            quantumFacts.initialize(objectType, objectId, value);
        }
    }

    @Override public void update(String objectType, String objectId, Object value) {
        String revision = requiredRevision(value, objectType, objectId);
        String next = persistUpdate(objectType,objectId,value,revision);
        saveRoleRelationships(objectType,objectId,value);
        RepositoryParticipantSnapshots.advanced(value, this, objectType, objectId, next);
    }

    private void acknowledgeAfterCommit(Object value) {
        if (value instanceof com.servpp.service.runtime.ChangeTrackedParticipant)
            com.servpp.service.jdbc.JdbcTransactionScope.afterCommit(store.getDataSource(),
                    com.servpp.service.runtime.PropertyChanges.acknowledgement(value));
    }

    private String persistUpdate(String objectType,String objectId,Object value,String revision) {
        Object facts = ordinary(value);
        var selectedFields = com.servpp.service.technical.UpdateFieldPlans.fields(value);
        if (selectedFields.isPresent()) {
            var stored = store.find(objectType,objectId).orElseThrow(() -> new IllegalStateException("PERSISTENCE_NOT_FOUND"));
            var patch = ParticipantUpdatePatch.create(facts,stored,selectedFields.get(),objectMapper);
            if (appDdlObjectTableWriter != null) {
                try { appDdlObjectTableWriter.validateUpdateIdentity(objectType,objectMapper.readerFor(Object.class).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(stored.payloadJson()),patch.facts()); }
                catch (java.io.IOException failure) { throw new IllegalStateException("PERSISTENCE_PATCH_INVALID",failure); }
            }
            String next = writeRevision(objectType,objectId,patch.facts(),revision);
            if (appDdlObjectTableWriter != null) appDdlObjectTableWriter.updateChanged(objectType,objectId,patch.facts(),patch.paths());
            com.servpp.service.jdbc.JdbcTransactionScope.afterCommit(store.getDataSource(),
                    com.servpp.service.runtime.PropertyChanges.acknowledgement(value));
            return next;
        }
        validateFullUpdateIdentity(objectType,objectId,facts);
        String next = writeRevision(objectType,objectId,facts,revision);
        updateAllMappedFacts(objectType,objectId,facts);
        acknowledgeAfterCommit(value);
        return next;
    }

    private void validateFullUpdateIdentity(String objectType,String objectId,Object facts) {
        if (appDdlObjectTableWriter == null) return;
        var stored = store.find(objectType,objectId).orElseThrow(() -> new IllegalStateException("PERSISTENCE_NOT_FOUND"));
        try {
            Object previous = objectMapper.readerFor(Object.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(stored.payloadJson());
            appDdlObjectTableWriter.validateUpdateIdentity(objectType,previous,facts);
        } catch (java.io.IOException failure) { throw new IllegalStateException("PERSISTENCE_FACTS_INVALID",failure); }
    }
    private void updateAllMappedFacts(String objectType,String objectId,Object facts) {
        if (appDdlObjectTableWriter == null) return;
        var fields = new java.util.LinkedHashSet<String>();
        objectMapper.valueToTree(facts).fieldNames().forEachRemaining(fields::add);
        // Update the existing row, including explicit nulls; do not delete/reinsert identities or foreign-key targets.
        appDdlObjectTableWriter.updateChanged(objectType,objectId,facts,fields);
    }

    private boolean optimisticConcurrency() {
        return appDdlCatalog == null || appDdlCatalog.optimisticConcurrency();
    }

    private String requiredRevision(Object value, String objectType, String objectId) {
        return optimisticConcurrency() ? RepositoryParticipantSnapshots.requireRevision(value, this, objectType, objectId) : null;
    }

    private String writeRevision(String objectType, String objectId, Object facts, String revision) {
        if (optimisticConcurrency()) return store.updateRevision(objectType, objectId, facts, revision);
        store.update(objectType, objectId, facts, null, null);
        return store.find(objectType, objectId).orElseThrow().revision();
    }

    @Override public void updateFromRead(String objectType, String objectId, Object value) {
        update(objectType, objectId, value);
    }

    @Override public void delete(String objectType, String objectId) {
        ObjectSnapshot original = store.delete(objectType, objectId);
        if (roleRelationships != null) roleRelationships.delete(objectType,objectId);
        if (appDdlObjectTableWriter != null) {
            try {
                appDdlObjectTableWriter.delete(objectType, objectId,
                        objectMapper.readerFor(java.util.Map.class)
                                .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                                .readValue(original.payloadJson()));
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("PERSISTENCE_DELETE_PAYLOAD_INVALID", failure);
            }
        }
    }

    @Override public void prepareUnidentifiedParticipant(String objectType,Object participant) {
        if(appDdlCatalog!=null && appDdlCatalog.roleStorage().containsKey(objectType)
                && appDdlCatalog.roleStorage().get(objectType).independent())
            RepositoryParticipantSnapshots.prepareTransientIdentity(participant,objectType);
    }

    @Override public java.util.Map<String,Object> platformFacts(String objectType,String objectId) {
        var result = new java.util.HashMap<String,Object>();
        if (appDdlCatalog != null) appDdlCatalog.tableForBusinessType(objectType).ifPresent(table -> {
            for(var column:table.columns()) if(!column.objectBacked() && !column.name().startsWith("spp_performer_"))
                readAppDdlPlatformColumnByObjectId(objectType,objectId,column.name()).ifPresent(value -> result.put(column.name(),value));
        });
        return java.util.Map.copyOf(result);
    }
    @Override public void compareAndSetPlatform(String objectType,String objectId,Object value,String column,Object expectedVersion) {
        verifyWriteTransaction();
        if (appDdlObjectTableWriter == null) throw new IllegalStateException("PLATFORM_VERSION_REQUIRES_APP_DDL");
        String revision = requiredRevision(value, objectType, objectId);
        appDdlObjectTableWriter.compareVersion(objectType,objectId,value,column,expectedVersion);
        String next = persistUpdate(objectType,objectId,value,revision);
        saveRoleRelationships(objectType,objectId,value);
        RepositoryParticipantSnapshots.advanced(value, this, objectType, objectId, next);
    }

    @Override public void compareAndSet(String objectType, String objectId, Object value,
                                       String versionPath, Object expectedVersion) {
        Object facts = ordinary(value);
        var selectedFields = com.servpp.service.technical.UpdateFieldPlans.fields(value);
        if (selectedFields.isPresent()) {
            var stored = store.find(objectType,objectId).orElseThrow(() -> new IllegalStateException("PERSISTENCE_NOT_FOUND"));
            // Explicit APP version advancement occurs after update.before and is always persisted.
            var fields = new java.util.LinkedHashSet<>(selectedFields.get()); fields.add(versionPath);
            var patch = ParticipantUpdatePatch.create(facts,stored,fields,objectMapper);
            if (appDdlObjectTableWriter != null) {
                try { appDdlObjectTableWriter.validateUpdateIdentity(objectType,objectMapper.readerFor(Object.class).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(stored.payloadJson()),patch.facts()); }
                catch (java.io.IOException failure) { throw new IllegalStateException("PERSISTENCE_PATCH_INVALID",failure); }
            }
            store.update(objectType,objectId,patch.facts(),versionPath,expectedVersion);
            if (appDdlObjectTableWriter != null) appDdlObjectTableWriter.updateChanged(objectType,objectId,patch.facts(),patch.paths());
            com.servpp.service.jdbc.JdbcTransactionScope.afterCommit(store.getDataSource(),com.servpp.service.runtime.PropertyChanges.acknowledgement(value));
            store.find(objectType,objectId).ifPresent(snapshot -> RepositoryParticipantSnapshots.advanced(value,this,objectType,objectId,snapshot.revision()));
        } else {
            validateFullUpdateIdentity(objectType,objectId,facts);
            store.update(objectType,objectId,facts,versionPath,expectedVersion);
            updateAllMappedFacts(objectType,objectId,facts);
            acknowledgeAfterCommit(value);
        }
        saveRoleRelationships(objectType,objectId,value);
    }

    @Override
    public Optional<ObjectSnapshot> find(String objectType, String objectId) {
        return store.find(objectType, objectId);
    }

    @Override
    public <T> Optional<T> findForUpdate(String objectType, String objectId, Class<T> type) {
        // The immutable snapshot is authoritative. Do not fall back to an unlocked native-table read.
        return store.findForUpdate(objectType, objectId).map(value -> read(value, type));
    }

    @Override public void executeInTransaction(Runnable action) {
        try {
            com.servpp.service.jdbc.JdbcTransactionScope.execute(store.getDataSource(), connection -> {
                action.run(); return null;
            });
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Cannot execute repository transaction", failure);
        }
    }

    public <T> Optional<T> find(String objectType, String objectId, Class<T> type) {
        Optional<T> snapshot = store.find(objectType, objectId)
                .map(value -> read(value, type));
        if (snapshot.isPresent()) {
            return snapshot;
        }
        return appDdlObjectTableReader == null ? Optional.empty()
                : appDdlObjectTableReader.find(objectType, objectId, type);
    }

    public <T> T require(String objectType, String objectId, Class<T> type) {
        return find(objectType, objectId, type)
                .orElseThrow(() -> new IllegalStateException(objectType + " not found: " + objectId));
    }

    @Override
    public <T> List<T> findAll(String objectType, Class<T> type) {
        if (appDdlObjectTableReader != null) {
            List<T> values = findAppDdlObjects(objectType, appDdlObjectTableReader.findAllObjectIds(objectType), type);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return store.findAll().stream()
                .filter(snapshot -> snapshot.objectType().equals(objectType))
                .map(snapshot -> read(snapshot, type))
                .toList();
    }

    @Override
    public <T> List<T> findBy(String objectType, String keyPath, Object keyValue, Class<T> type) {
        if (objectType == null || keyPath == null || type == null) {
            return List.of();
        }
        if (appDdlObjectTableReader != null) {
            List<T> values = findAppDdlObjects(objectType,
                    appDdlObjectTableReader.findObjectIdsBy(objectType, keyPath, keyValue), type);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return store.findAll().stream()
                .filter(snapshot -> snapshot.objectType().equals(objectType))
                .map(snapshot -> read(snapshot, type))
                .filter(value -> Objects.equals(readPath(completeQueryFacts(objectType, value, List.of(keyPath)), keyPath), keyValue))
                .toList();
    }

    @Override
    public <T> List<T> findBy(String objectType, List<String> keyPaths, List<?> keyValues, Class<T> type) {
        if (objectType == null || keyPaths == null || keyValues == null || keyPaths.size() != keyValues.size()
                || type == null) {
            return List.of();
        }
        if (keyPaths.isEmpty()) {
            return findAll(objectType, type);
        }
        if (keyPaths.size() == 1) {
            return findBy(objectType, keyPaths.get(0), keyValues.get(0), type);
        }
        if (appDdlObjectTableReader != null) {
            List<T> values = findAppDdlObjects(objectType,
                    appDdlObjectTableReader.findObjectIdsBy(objectType, keyPaths, keyValues), type);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return store.findAll().stream()
                .filter(snapshot -> snapshot.objectType().equals(objectType))
                .map(snapshot -> read(snapshot, type))
                .filter(value -> pathsMatch(completeQueryFacts(objectType, value, keyPaths), keyPaths, keyValues))
                .toList();
    }

    @Override
    public <T> List<T> findByForUpdate(String objectType, List<String> keyPaths, List<?> keyValues, Class<T> type) {
        verifyWriteTransaction();
        if (keyPaths.size() != keyValues.size()) throw new IllegalArgumentException("QUERY_KEY_ARITY");
        // Lock only candidate identities, in stable order, and check their locked values again.
        List<T> values = new java.util.ArrayList<>();
        store.findByType(objectType).stream()
                .filter(snapshot -> pathsMatch(completeQueryFacts(objectType, read(snapshot, type), keyPaths), keyPaths, keyValues))
                .sorted(java.util.Comparator.comparing(ObjectSnapshot::objectId))
                .forEach(snapshot -> findForUpdate(objectType, snapshot.objectId(), type)
                        .filter(value -> pathsMatch(completeQueryFacts(objectType, value, keyPaths), keyPaths, keyValues)).ifPresent(values::add));
        return values;
    }

    private static final ThreadLocal<java.util.Set<List<String>>> QUERY_COMPLETIONS =
            ThreadLocal.withInitial(java.util.HashSet::new);

    /** Conditions on inherited facts must be evaluated after the role's identity query completes its performers. */
    private <T> T completeQueryFacts(String objectType, T value, List<String> paths) {
        if (appDdlCatalog == null) return value;
        var table = appDdlCatalog.tableForBusinessType(objectType).orElse(null);
        if (table == null || table.queryService().isBlank() || paths.stream().noneMatch(path -> {
            String local = path.startsWith(objectType + ".") ? path.substring(objectType.length() + 1) : path;
            return table.excludedFacts().contains(local.split("\\.")[0]);
        })) return value;
        String identity = RepositoryParticipantSnapshots.objectIdentity(value, objectType).orElseThrow();
        var key = List.of(table.queryService(), identity);
        var active = QUERY_COMPLETIONS.get();
        if (!active.add(key)) throw new IllegalStateException("PERSISTENCE_ROLE_QUERY_CYCLE: " + table.queryService());
        try {
            com.servpp.service.runtime.LocalDomainServices.invoke(table.queryService(),
                    new Object[]{value}, new Class<?>[]{value.getClass()},
                    new String[][]{table.excludedFacts().toArray(String[]::new)},
                    new String[][]{new String[0]}, new boolean[]{false}, true);
            com.servpp.service.runtime.PropertyChanges.clean(value);
            return value;
        } finally {
            active.remove(key);
            if (active.isEmpty()) QUERY_COMPLETIONS.remove();
        }
    }

    private <T> List<T> findAppDdlObjects(String objectType, List<String> objectIds, Class<T> type) {
        if (objectIds == null || objectIds.isEmpty()) {
            return List.of();
        }
        return objectIds.stream()
                .map(objectId -> find(objectType, objectId, type))
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .toList();
    }

    @Override
    public <T> T hydrate(String objectType, String objectId, T target) {
        if (!hydrateIfPresent(objectType,objectId,target)) { throw new IllegalStateException(objectType + " not found: " + objectId); }
        return target;
    }

    @Override
    public <T> boolean hydrateIfPresent(String objectType, String objectId, T target) {
        Optional<ObjectSnapshot> snapshot = store.find(objectType, objectId);
        if (snapshot.isPresent()) { readInto(snapshot.orElseThrow(), target); return true; }
        boolean appDdlHydrated = appDdlObjectTableReader != null && appDdlObjectTableReader.hydrate(objectType,objectId,target);
        return snapshot.isPresent() || appDdlHydrated;
    }

    private <T> void readInto(ObjectSnapshot snapshot, T target) {
        try {
            ParticipantStoredIdentities.decodeInto(objectMapper,snapshot.payloadJson(),target);
            // Relationship query keys need the private identity before completing nested facts.
            RepositoryParticipantSnapshots.attachIdentity(target, snapshot.objectType(), snapshot.objectId());
            hydrateQuantum(snapshot, target);
            if (roleRelationships != null) roleRelationships.load(snapshot.objectType(),snapshot.objectId(),target);
            com.servpp.service.runtime.ParticipantRelationshipQueries.complete(target,appDdlCatalog);
            com.servpp.service.runtime.PropertyChanges.clean(target);
            RepositoryParticipantSnapshots.loaded(target, this, snapshot);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot hydrate " + snapshot.objectType()
                    + " object " + snapshot.objectId(), e);
        }
    }

    private <T> T read(ObjectSnapshot snapshot, Class<T> type) {
        try {
            T value = ParticipantStoredIdentities.decode(objectMapper,snapshot.payloadJson(),type);
            // Recursive relationship queries must cache this row under its actual private identity.
            RepositoryParticipantSnapshots.attachIdentity(value, snapshot.objectType(), snapshot.objectId());
            hydrateQuantum(snapshot, value);
            if (roleRelationships != null) roleRelationships.load(snapshot.objectType(),snapshot.objectId(),value);
            com.servpp.service.runtime.ParticipantRelationshipQueries.complete(value,appDdlCatalog);
            com.servpp.service.runtime.PropertyChanges.clean(value);
            RepositoryParticipantSnapshots.loaded(value, this, snapshot);
            return value;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read " + snapshot.objectType()
                    + " object " + snapshot.objectId(), e);
        }
    }

    private void saveRoleRelationships(String type,String identity,Object value) {
        if (roleRelationships != null) roleRelationships.save(type,identity,value);
    }

    private Object readPath(Object value, String keyPath) {
        Object current = value;
        for (String segment : keyPath.split("\\.")) {
            current = readProperty(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private boolean pathsMatch(Object value, List<String> keyPaths, List<?> keyValues) {
        for (int i = 0; i < keyPaths.size(); i++) {
            if (!Objects.equals(readPath(value, keyPaths.get(i)), keyValues.get(i))) {
                return false;
            }
        }
        return true;
    }

    private Object readProperty(Object value, String propertyName) {
        if (value == null) {
            return null;
        }
        Class<?> type = value.getClass();
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0 && methodNameMatches(method.getName(), propertyName)) {
                try {
                    return method.invoke(value);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read " + method.getName()
                            + " from " + type.getName(), e);
                }
            }
        }
        Field field = findField(type, propertyName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + field.getName()
                    + " from " + type.getName(), e);
        }
    }

    private Field findField(Class<?> type, String propertyName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (propertyMatches(field.getName(), propertyName)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean methodNameMatches(String methodName, String propertyName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return propertyMatches(methodName.substring(3), propertyName);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return propertyMatches(methodName.substring(2), propertyName);
        }
        return propertyMatches(methodName, propertyName);
    }

    private boolean propertyMatches(String actual, String expected) {
        return normalized(actual).equals(normalized(expected));
    }

    private String normalized(String value) {
        return value == null ? "" : value.replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }
}
