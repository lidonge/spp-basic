package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;

/** JDBC representation for APP-declared historical participant facts. */
final class ParticipantSnapshotJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private ParticipantSnapshotJson() { }
    static String encode(Object value) {
        if (value == null) return null;
        try { return MAPPER.writeValueAsString(value); }
        catch (java.io.IOException failure) { throw new IllegalStateException("PARTICIPANT_SNAPSHOT_WRITE_FAILED",failure); }
    }
    static void hydrate(Object target, String property, String json) {
        try {
            var fields = MAPPER.createObjectNode();
            fields.set(property,json == null ? MAPPER.nullNode() : MAPPER.readTree(json));
            MAPPER.readerForUpdating(target).readValue(fields);
        } catch (java.io.IOException failure) { throw new IllegalStateException("PARTICIPANT_SNAPSHOT_READ_FAILED",failure); }
    }
}
