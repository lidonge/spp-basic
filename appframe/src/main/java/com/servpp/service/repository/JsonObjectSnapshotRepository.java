package com.servpp.service.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonObjectSnapshotRepository implements ObjectRepository {
    private final JdbcObjectSnapshotStore store;
    private final ObjectMapper objectMapper;

    public JsonObjectSnapshotRepository(JdbcObjectSnapshotStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String objectType, String objectId, Object value) {
        store.save(objectType, objectId, value);
    }

    @Override public void verifyWriteTransaction() { store.verifyWriteTransaction(); }
    @Override public void insertIfAbsent(String objectType,String objectId,Object value) { store.insertIfAbsent(objectType,objectId,value); }
    @Override public void insert(String objectType, String objectId, Object value) { store.insert(objectType, objectId, value); }
    @Override public void delete(String objectType, String objectId) { store.delete(objectType, objectId); }
    @Override public void update(String objectType, String objectId, Object value) { store.update(objectType, objectId, value, null, null); }
    @Override public void compareAndSet(String objectType, String objectId, Object value, String versionPath, Object expectedVersion) {
        store.update(objectType, objectId, value, versionPath, expectedVersion);
    }

    @Override
    public Optional<ObjectSnapshot> find(String objectType, String objectId) {
        return store.find(objectType, objectId);
    }

    @Override
    public <T> Optional<T> findForUpdate(String objectType, String objectId, Class<T> type) {
        return store.findForUpdate(objectType, objectId).map(snapshot -> read(snapshot, type));
    }

    public <T> Optional<T> find(String objectType, String objectId, Class<T> type) {
        return store.find(objectType, objectId)
                .map(snapshot -> read(snapshot, type));
    }

    public <T> T require(String objectType, String objectId, Class<T> type) {
        return find(objectType, objectId, type)
                .orElseThrow(() -> new IllegalStateException(objectType + " not found: " + objectId));
    }

    @Override
    public <T> List<T> findAll(String objectType, Class<T> type) {
        List<T> values = new ArrayList<>();
        for (ObjectSnapshot snapshot : store.findAll()) {
            if (snapshot.objectType().equals(objectType)) {
                values.add(read(snapshot, type));
            }
        }
        return List.copyOf(values);
    }

    @Override
    public <T> List<T> findBy(String objectType, String keyPath, Object keyValue, Class<T> type) {
        if (keyPath == null || keyPath.isBlank()) {
            throw new IllegalArgumentException("Object repository keyPath must not be blank.");
        }
        List<T> values = new ArrayList<>();
        for (ObjectSnapshot snapshot : store.findAll()) {
            if (!snapshot.objectType().equals(objectType)) {
                continue;
            }
            if (matches(snapshot, keyPath, keyValue)) {
                values.add(read(snapshot, type));
            }
        }
        return List.copyOf(values);
    }

    @Override
    public <T> T hydrate(String objectType, String objectId, T target) {
        if (!hydrateIfPresent(objectType,objectId,target)) { throw new IllegalStateException(objectType + " not found: " + objectId); }
        return target;
    }

    @Override
    public <T> boolean hydrateIfPresent(String objectType, String objectId, T target) {
        var found=store.find(objectType,objectId);
        if (found.isEmpty()) { return false; }
        ObjectSnapshot snapshot=found.orElseThrow();
        try {
            objectMapper.readerForUpdating(target).readValue(snapshot.payloadJson());
            return true;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot hydrate " + objectType + " snapshot " + objectId, e);
        }
    }

    private <T> T read(ObjectSnapshot snapshot, Class<T> type) {
        try {
            return objectMapper.readValue(snapshot.payloadJson(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read " + snapshot.objectType()
                    + " snapshot " + snapshot.objectId(), e);
        }
    }

    private boolean matches(ObjectSnapshot snapshot, String keyPath, Object keyValue) {
        try {
            JsonNode node = objectMapper.readTree(snapshot.payloadJson());
            for (String property : keyPath.split("\\.")) {
                if (node == null || node.isMissingNode() || node.isNull()) {
                    return keyValue == null;
                }
                node = node.path(property);
            }
            if (node == null || node.isMissingNode() || node.isNull()) {
                return keyValue == null;
            }
            return keyValue != null && node.asText().equals(keyValue.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot filter " + snapshot.objectType()
                    + " snapshot " + snapshot.objectId() + " by " + keyPath, e);
        }
    }
}
