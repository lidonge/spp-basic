package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Private snapshot metadata for nested APP identities, absent from SPP and public beans. */
final class ParticipantStoredIdentities {
    static final String MARKER = "$sppStorageIdentity";
    private ParticipantStoredIdentities() { }

    static Object strip(Object encoded) {
        if (encoded instanceof Map<?,?> values) {
            Map<String,Object> result = new LinkedHashMap<>();
            values.forEach((key,value) -> { if (!MARKER.equals(key)) result.put(key.toString(),strip(value)); });
            return result;
        }
        if (encoded instanceof List<?> values) return values.stream().map(ParticipantStoredIdentities::strip).toList();
        return encoded;
    }

    static void restore(Object target, Object encoded) {
        if (target == null) return;
        if (encoded instanceof Map<?,?> values) {
            if (values.get(MARKER) instanceof Map<?,?> identity)
                RepositoryParticipantSnapshots.attachIdentity(target,identity.get("type").toString(),identity.get("id").toString());
            values.forEach((name,value) -> {
                if (!MARKER.equals(name) && value != null) restore(ObjectPropertyAccess.read(target,name.toString()),value);
            });
        } else if (encoded instanceof List<?> values && target instanceof List<?> members) {
            for (int i=0;i<Math.min(values.size(),members.size());i++) restore(members.get(i),values.get(i));
        }
    }

    static Object encoded(ObjectMapper mapper,String json) throws java.io.IOException {
        return mapper.readerFor(Object.class).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(json);
    }
    static <T> T decode(ObjectMapper mapper,String json,Class<T> type) throws java.io.IOException {
        Object encoded = encoded(mapper,json);
        T target = mapper.convertValue(strip(encoded),type);
        restore(target,encoded);
        return target;
    }
    static Object decodeInto(ObjectMapper mapper, String json, Object target) throws java.io.IOException {
        Object encoded = encoded(mapper,json);
        mapper.readerForUpdating(target).readValue(mapper.writeValueAsString(strip(encoded)));
        restore(target,encoded);
        return target;
    }
}
