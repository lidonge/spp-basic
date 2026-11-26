package com.servpp.service.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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
            if (value != null && mapped(objectType) && delta != null && delta.signum() != 0) {
                var table = appDdlCatalog.tableForBusinessType(objectType).orElseThrow();
                if (table.columns().stream().noneMatch(c -> !c.objectBacked() && c.name().equals(columnName))) return;
                executeInTransaction(() -> {
                    String id = appDdlObjectTableWriter.physicalId(table, objectId, value);
                    save(objectType, id, value);
                    appDdlObjectTableWriter.adjustPlatformColumn(objectType, id, null, columnName, delta);
                });
            } else appDdlObjectTableWriter.adjustPlatformColumn(objectType, objectId, value, columnName, delta);
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
        if (!mapped(objectType)) {
            store.save(objectType, objectId, ordinary(value));
            saveRoleRelationships(objectType, objectId, value);
            return;
        }
        executeInTransaction(() -> {
            var table = appDdlCatalog.tableForBusinessType(objectType).orElseThrow();
            Object facts = ordinary(value);
            String id = appDdlObjectTableWriter.physicalId(table, objectId, facts);
            var row = physicalRow(objectType, id, true);
            if (row.isPresent()) {
                store.find(objectType, id).ifPresent(stored ->
                        appDdlObjectTableWriter.verifyImage(table, storedFacts(stored), row.get().columns()));
                appDdlObjectTableWriter.save(objectType, id, facts);
            } else appDdlObjectTableWriter.insert(table, id, facts, false);
            store.save(objectType, id, facts);
            saveRoleRelationships(objectType, id, value);
        });
    }

    @Override public void verifyWriteTransaction() { store.verifyWriteTransaction(); }

    @Override public void insert(String objectType, String objectId, Object value) {
        if (com.servpp.service.runtime.DatabaseInitializationContext.isActive()) {
            insertIfAbsent(objectType, objectId, value);
            return;
        }
        insertFacts(objectType, objectId, value, false);
    }

    @Override public void insertIfAbsent(String objectType, String objectId, Object value) {
        insertFacts(objectType, objectId, value, true);
    }

    private void insertFacts(String objectType, String objectId, Object value, boolean ifAbsent) {
        verifyWriteTransaction();
        Object facts = ordinary(value);
        boolean inserted;
        if (mapped(objectType)) {
            var table = appDdlCatalog.tableForBusinessType(objectType).orElseThrow();
            inserted = appDdlObjectTableWriter.insert(table, objectId, facts, ifAbsent);
            if (inserted) store.save(objectType, objectId, facts);
        } else if (ifAbsent) inserted = store.insertIfAbsent(objectType, objectId, facts);
        else {
            store.insert(objectType, objectId, facts);
            inserted = true;
        }
        if (inserted) {
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

    private Object storedFacts(ObjectSnapshot snapshot) {
        try { return ParticipantStoredIdentities.encoded(objectMapper, snapshot.payloadJson()); }
        catch (java.io.IOException failure) { throw new IllegalStateException("PERSISTENCE_FACTS_INVALID", failure); }
    }

    // Hold the physical lock through both the revision CAS and the native write.
    private String prepareMappedUpdate(String type, String id, Object value, String revision) {
        if (!mapped(type)) return revision;
        verifyWriteTransaction();
        var row = physicalRow(type, id, true).orElseThrow(() -> new IllegalStateException("PERSISTENCE_NOT_FOUND"));
        var table = appDdlCatalog.tableForBusinessType(type).orElseThrow();
        if (revision != null && !revision.equals(row.revision())) throw new IllegalStateException("PERSISTENCE_VERSION_CONFLICT");
        var stored = store.find(type, id);
        if (stored.isPresent()) {
            appDdlObjectTableWriter.verifyImage(table, storedFacts(stored.get()), row.columns());
            return revision;
        }
        // A first writer must also protect the interval between its revision-zero read and this lock.
        Object previous;
        if (revision != null) {
            previous = ordinary(RepositoryParticipantSnapshots.require(value));
            appDdlObjectTableWriter.verifyImage(table, previous, row.columns());
        } else {
            Object baseline = appDdlObjectTableReader.newInstance(value.getClass());
            appDdlObjectTableReader.populate(table, row, baseline);
            previous = ordinary(baseline);
        }
        store.insert(type, id, previous);
        return store.find(type, id).orElseThrow().revision();
    }

    private String persistUpdate(String objectType,String objectId,Object value,String revision) {
        revision = prepareMappedUpdate(objectType, objectId, value, revision);
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
        Object original;
        if (mapped(objectType)) {
            verifyWriteTransaction();
            var row = physicalRow(objectType, objectId, true).orElseThrow(() -> new IllegalStateException("PERSISTENCE_NOT_FOUND"));
            var snapshot = store.find(objectType, objectId);
            if (snapshot.isPresent()) {
                original = storedFacts(snapshot.get());
                appDdlObjectTableWriter.verifyImage(appDdlCatalog.tableForBusinessType(objectType).orElseThrow(), original, row.columns());
                store.delete(objectType, objectId);
            } else original = row.columns();
        } else {
            store.delete(objectType, objectId);
            if (roleRelationships != null) roleRelationships.delete(objectType, objectId);
            return;
        }
        if (roleRelationships != null) roleRelationships.delete(objectType,objectId);
        if (appDdlObjectTableWriter != null) {
            appDdlObjectTableWriter.delete(objectType, objectId, original);
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
        if (mapped(objectType)) prepareMappedUpdate(objectType, objectId, value, requiredRevision(value, objectType, objectId));
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
        if (mapped(objectType)) return physicalRow(objectType, objectId, false).map(row ->
                new ObjectSnapshot(objectType, row.id(), objectMapper.valueToTree(physicalFacts(objectType, row)).toString(), null, row.revision()));
        return store.find(objectType, objectId);
    }

    @Override
    public <T> Optional<T> findForUpdate(String objectType, String objectId, Class<T> type) {
        verifyWriteTransaction();
        if (mapped(objectType)) return physicalRow(objectType, objectId, true)
                .map(row -> materialize(objectType, row, type));
        return store.findForUpdate(objectType, objectId).map(value -> read(value, type));
    }

    @Override public java.util.Optional<java.sql.Connection> transactionConnection() {
        return com.servpp.service.jdbc.JdbcTransactionScope.currentConnection(store.getDataSource());
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
        if (mapped(objectType)) return physicalRow(objectType, objectId, false)
                .map(row -> materialize(objectType, row, type));
        return store.find(objectType, objectId).map(row -> read(row, type));
    }

    public <T> T require(String objectType, String objectId, Class<T> type) {
        return find(objectType, objectId, type)
                .orElseThrow(() -> new IllegalStateException(objectType + " not found: " + objectId));
    }

    @Override
    public <T> List<T> findAll(String objectType, Class<T> type) {
        if (mapped(objectType)) return physicalValues(objectType, List.of(), List.of(), type, false, null);
        return store.findCurrentByType(objectType).stream()
                .filter(snapshot -> snapshot.objectType().equals(objectType))
                .map(snapshot -> read(snapshot, type))
                .toList();
    }

    @Override
    public <T> List<T> findBy(String objectType, String keyPath, Object keyValue, Class<T> type) {
        if (objectType == null || keyPath == null || type == null) {
            return List.of();
        }
        if (mapped(objectType)) return physicalValues(objectType, List.of(keyPath), java.util.Collections.singletonList(keyValue), type, false, null);
        return store.findCurrentByType(objectType).stream()
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
        if (mapped(objectType)) return physicalValues(objectType, keyPaths, keyValues, type, false, null);
        return store.findCurrentByType(objectType).stream()
                .filter(snapshot -> snapshot.objectType().equals(objectType))
                .map(snapshot -> read(snapshot, type))
                .filter(value -> pathsMatch(completeQueryFacts(objectType, value, keyPaths), keyPaths, keyValues))
                .toList();
    }

    @Override
    public <T> Optional<T> findUniqueBy(String objectType, List<String> paths, List<?> values, Class<T> type,
                                        String rejectionCode, boolean locked) {
        if (appDdlObjectTableReader == null) throw new IllegalStateException("Unique reads require mapped APP columns");
        if (locked) verifyWriteTransaction();
        if (paths.isEmpty() || paths.size() != values.size()) throw new IllegalArgumentException("QUERY_KEY_ARITY");
        return physicalValues(objectType, paths, values, type, locked, rejectionCode).stream().findFirst();
    }

    @Override
    public <T> List<T> findByForUpdate(String objectType, List<String> keyPaths, List<?> keyValues, Class<T> type) {
        verifyWriteTransaction();
        if (keyPaths.size() != keyValues.size()) throw new IllegalArgumentException("QUERY_KEY_ARITY");
        if (mapped(objectType)) return physicalValues(objectType, keyPaths, keyValues, type, true, null);
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

    boolean mapped(String type) {
        return appDdlObjectTableReader != null && appDdlCatalog.tableForBusinessType(type).isPresent();
    }

    private Optional<JdbcAppDdlObjectTableReader.PhysicalRow> physicalRow(String type, String id, boolean locked) {
        var table = appDdlCatalog.tableForBusinessType(type).orElseThrow();
        List<String> keys = table.primaryKeys().size() == 1 ? List.of(id)
                : CompositeObjectId.decode(id, table.primaryKeys().size());
        if (keys.isEmpty()) return Optional.empty();
        return appDdlObjectTableReader.rows(table, store.getTableName(), table.primaryKeys(), keys, locked, null)
                .stream().findFirst();
    }

    private <T> List<T> physicalValues(String type, List<String> paths, List<?> values, Class<T> model,
                                        boolean locked, String rejection) {
        var table = appDdlCatalog.tableForBusinessType(type).orElseThrow();
        boolean local = paths.stream().allMatch(path -> appDdlObjectTableReader.columnForPath(table, path).isPresent());
        if (!local && rejection != null) throw new IllegalArgumentException("No mapped column for " + paths);
        var rows = appDdlObjectTableReader.rows(table, store.getTableName(), local ? paths : List.of(),
                local ? values : List.of(), locked, rejection);
        return rows.stream().map(row -> materialize(type, row, model))
                .filter(value -> local || pathsMatch(completeQueryFacts(type, value, paths), paths, values)).toList();
    }

    private java.util.Map<String,Object> physicalFacts(String type, JdbcAppDdlObjectTableReader.PhysicalRow row) {
        var table = appDdlCatalog.tableForBusinessType(type).orElseThrow();
        var facts = new java.util.LinkedHashMap<String,Object>();
        for (var column : table.columns()) {
            if (!column.objectBacked()) continue;
            Object value = row.columns().get(column.name());
            if (value != null && column.json(table)) {
                try { value = ParticipantStoredIdentities.encoded(objectMapper, (String)value); }
                catch (java.io.IOException failure) { throw new IllegalStateException("PERSISTENCE_FACTS_INVALID", failure); }
            }
            facts.put(column.name(), value);
        }
        return facts;
    }

    private <T> T materialize(String type, JdbcAppDdlObjectTableReader.PhysicalRow row, Class<T> model) {
        if (model.isRecord() || model == Object.class || java.util.Map.class.isAssignableFrom(model)) {
            var facts = physicalFacts(type, row);
            if (model.isRecord()) {
                var fields = java.util.Arrays.stream(model.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
                        .collect(java.util.stream.Collectors.toSet());
                facts.keySet().retainAll(fields);
            }
            T value = objectMapper.convertValue(facts, model);
            finishRead(type, row, value);
            return value;
        }
        T target = appDdlObjectTableReader.newInstance(model);
        populate(type, row, target);
        return target;
    }

    private void populate(String type, JdbcAppDdlObjectTableReader.PhysicalRow row, Object target) {
        appDdlObjectTableReader.populate(appDdlCatalog.tableForBusinessType(type).orElseThrow(), row, target);
        finishRead(type, row, target);
    }

    private void finishRead(String type, JdbcAppDdlObjectTableReader.PhysicalRow row, Object target) {
        if (row.id() != null) RepositoryParticipantSnapshots.attachIdentity(target, type, row.id());
        var evidence = new ObjectSnapshot(type, row.id(), null, null, row.revision());
        if (quantumFacts != null) quantumFacts.hydrate(type, row.id(), target, true);
        roleRelationships.loadMapped(type, row.columns(), target);
        com.servpp.service.runtime.ParticipantRelationshipQueries.complete(target,appDdlCatalog);
        com.servpp.service.runtime.PropertyChanges.clean(target);
        if (row.id() != null) {
            var platform = new java.util.HashMap<String,Object>();
            for (var column : appDdlCatalog.tableForBusinessType(type).orElseThrow().columns()) {
                Object value = row.columns().get(column.name());
                if (value != null && !column.objectBacked() && !column.name().startsWith("spp_performer_"))
                    platform.put(column.name(), value);
            }
            RepositoryParticipantSnapshots.loaded(target, this, evidence, platform);
        }
    }

    @Override
    public <T> T hydrate(String objectType, String objectId, T target) {
        if (!hydrateIfPresent(objectType,objectId,target)) { throw new IllegalStateException(objectType + " not found: " + objectId); }
        return target;
    }

    @Override
    public <T> boolean hydrateIfPresent(String objectType, String objectId, T target) {
        if (mapped(objectType)) {
            var table = appDdlCatalog.tableForBusinessType(objectType).orElseThrow();
            var key = appDdlObjectTableReader.keyPredicate(table, objectId, target);
            if (key.values().isEmpty()) return false;
            var rows = appDdlObjectTableReader.rows(table, store.getTableName(), key.columns(), key.values(), false, null);
            if (rows.size() > 1) throw new IllegalStateException("APP DDL table " + table.name()
                    + " has multiple rows for " + key.columns() + ".");
            if (rows.isEmpty()) return false;
            populate(objectType, rows.get(0), target);
            return true;
        }
        var snapshot = store.find(objectType, objectId);
        snapshot.ifPresent(value -> readInto(value, target));
        return snapshot.isPresent();
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
        var storage = appDdlCatalog == null ? null : appDdlCatalog.roleStorage().get(type.getSimpleName());
        if ("@storageIdentity".equals(propertyName)
                || storage != null && storage.independent() && storage.primaryKey().equalsIgnoreCase(propertyName)) {
            return RepositoryParticipantSnapshots.objectIdentity(value, type.getSimpleName())
                    .orElseThrow(() -> new IllegalStateException("PERSISTENCE_READ_IDENTITY_REQUIRED"));
        }
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
