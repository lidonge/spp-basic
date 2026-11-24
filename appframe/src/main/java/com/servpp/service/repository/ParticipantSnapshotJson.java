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
    static boolean matches(Object value, String stored) {
        if (value == null || stored == null) return value == null && stored == null;
        try {
            var reader = MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            var actual = reader.readTree(stored);
            var expected = reader.readTree(encode(value));
            return expected.equals((left, right) -> {
                if (left.isNumber() && right.isNumber()) return left.decimalValue().compareTo(right.decimalValue());
                return left.equals(right) ? 0 : 1;
            }, actual);
        } catch (java.io.IOException failure) { throw new IllegalStateException("PARTICIPANT_SNAPSHOT_READ_FAILED", failure); }
    }

    static void hydrate(Object target, String property, String json) {
        try {
            Object encoded = json == null ? null : ParticipantStoredIdentities.encoded(MAPPER, json);
            var fields = new java.util.LinkedHashMap<String,Object>();
            fields.put(property, ParticipantStoredIdentities.strip(encoded));
            MAPPER.readerForUpdating(target).readValue(MAPPER.writeValueAsBytes(fields));
            ParticipantStoredIdentities.restore(ObjectPropertyAccess.read(target, property), encoded);
        } catch (java.io.IOException failure) { throw new IllegalStateException("PARTICIPANT_SNAPSHOT_READ_FAILED",failure); }
    }
}
