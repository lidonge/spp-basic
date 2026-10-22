package com.servpp.service.repository;

import com.servpp.service.runtime.AppDdlCatalog;
import java.util.Map;
import java.util.Optional;

/** APP-only association keys come from repository metadata, never contact or other business facts. */
final class ParticipantPlatformKeys {
    private ParticipantPlatformKeys() { }

    static Optional<String> read(Object participant, String column, AppDdlCatalog catalog) {
        if (participant == null) return Optional.empty();
        String type = participant.getClass().getSimpleName();
        String identity = null;
        if (participant instanceof Map<?,?> facts) {
            if (!(facts.get(ParticipantStoredIdentities.MARKER) instanceof Map<?,?> stored)
                    || !(stored.get("type") instanceof String storedType)
                    || !(stored.get("id") instanceof String storedId)) return Optional.empty();
            type = storedType; identity = storedId;
        }
        var table = catalog.tableForBusinessType(type);
        if (table.isEmpty() || table.get().primaryKeys().size() != 1
                || table.get().platformPrimaryKeys().stream().noneMatch(key -> key.equalsIgnoreCase(column))) return Optional.empty();
        return identity == null ? RepositoryParticipantSnapshots.objectIdentity(participant,type) : Optional.of(identity);
    }

    static boolean restore(Object participant, String column, Object identity, AppDdlCatalog catalog) {
        if (participant == null || identity == null) return false;
        String type = participant.getClass().getSimpleName();
        var table = catalog.tableForBusinessType(type);
        if (table.isEmpty() || table.get().primaryKeys().size() != 1
                || table.get().platformPrimaryKeys().stream().noneMatch(key -> key.equalsIgnoreCase(column))) return false;
        RepositoryParticipantSnapshots.attachIdentity(participant,type,identity.toString());
        return true;
    }
}
