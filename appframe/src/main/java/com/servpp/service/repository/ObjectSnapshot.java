package com.servpp.service.repository;

/** Facts and their physical revision are read from the same database row. */
public record ObjectSnapshot(String objectType, String objectId, String payloadJson,
                             String updatedAt, String revision) {
    public ObjectSnapshot(String objectType, String objectId, String payloadJson, String updatedAt) {
        this(objectType, objectId, payloadJson, updatedAt, null);
    }
}
