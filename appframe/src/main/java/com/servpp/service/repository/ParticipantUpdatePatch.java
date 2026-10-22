package com.servpp.service.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.servpp.service.runtime.PropertyChanges;
import java.util.*;

/** Merge only business writes into the current persisted facts, preserving all other facts. */
final class ParticipantUpdatePatch {
    private ParticipantUpdatePatch() { }
    record Patch(Object facts, Set<String> paths) { }
    static Patch create(Object ordinary,ObjectSnapshot stored,Set<String> selectedFields,ObjectMapper mapper) {
        try {
            ObjectNode result = (ObjectNode)mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(stored.payloadJson());
            JsonNode current = mapper.valueToTree(ordinary);
            Set<String> paths = new LinkedHashSet<>();
            for (String path : selectedFields) {
                String root = path.split("\\.")[0];
                // APP ownership and quantum projection determine which facts this repository may write.
                if (!current.has(root)) continue;
                String[] segments = path.split("\\.");
                JsonNode incoming = current;
                boolean supported = true;
                for (String segment : segments) {
                    if (!incoming.isObject()) { supported = false; break; }
                    incoming = incoming.path(segment);
                }
                if (!supported || incoming.isMissingNode()) continue;
                ObjectNode cursor = result;
                for (int i=0;i<segments.length-1;i++) {
                    JsonNode child = cursor.get(segments[i]);
                    if (!(child instanceof ObjectNode)) { child = mapper.createObjectNode(); cursor.set(segments[i],child); }
                    cursor = (ObjectNode)child;
                }
                cursor.set(segments[segments.length-1],incoming.deepCopy());
                paths.add(path);
            }
            return new Patch(mapper.readerFor(Object.class).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(result.traverse(mapper)),Set.copyOf(paths));
        } catch (java.io.IOException failure) { throw new IllegalStateException("PERSISTENCE_PATCH_INVALID",failure); }
    }
    static boolean affects(String column,Set<String> paths) {
        String normalized = column.replace("_", ".").toLowerCase(Locale.ROOT);
        return paths.stream().anyMatch(path -> PropertyChanges.overlaps(normalized,path.toLowerCase(Locale.ROOT))
                || normalized.replace(".","").equals(path.replace(".","").toLowerCase(Locale.ROOT)));
    }
}
