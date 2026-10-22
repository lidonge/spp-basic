package com.servpp.service.runtime;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record RoleParticipantIdentity(String roleType,
                                      String roleTableName,
                                      String mapperName,
                                      List<String> performerTypes,
                                      List<String> keyPaths,
                                      List<String> platformColumns,
                                      List<Object> keyValues,
                                      String source,
                                      boolean complete,
                                      String binding) {
    public RoleParticipantIdentity {
        performerTypes = performerTypes == null ? List.of() : List.copyOf(performerTypes);
        keyPaths = keyPaths == null ? List.of() : List.copyOf(keyPaths);
        platformColumns = platformColumns == null ? List.of() : List.copyOf(platformColumns);
        keyValues = keyValues == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(keyValues));
    }
}
