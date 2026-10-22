package com.servpp.service.runtime;

import java.util.List;

public record RoleParticipantRequestBindingPlan(String serviceName,
                                                String roleType,
                                                String roleParameterName,
                                                List<String> performerTypes,
                                                String requestTypeName,
                                                List<String> sidecarFieldNames,
                                                List<String> missingSidecarFieldNames,
                                                String identityBindingSource,
                                                boolean requestTypeAvailable,
                                                boolean complete,
                                                String readiness,
                                                List<String> readyReasons,
                                                List<String> missingReasons,
                                                List<String> actions,
                                                String bindingContract) {
    public RoleParticipantRequestBindingPlan(String serviceName,
                                             String roleType,
                                             String roleParameterName,
                                             List<String> performerTypes,
                                             String requestTypeName,
                                             List<String> sidecarFieldNames,
                                             List<String> missingSidecarFieldNames,
                                             String identityBindingSource,
                                             boolean requestTypeAvailable,
                                             boolean complete) {
        this(serviceName, roleType, roleParameterName, performerTypes, requestTypeName, sidecarFieldNames,
                missingSidecarFieldNames, identityBindingSource, requestTypeAvailable, complete,
                "", List.of(), List.of(), List.of(), "");
    }

    public RoleParticipantRequestBindingPlan(String serviceName,
                                             String roleType,
                                             String roleParameterName,
                                             List<String> performerTypes,
                                             String requestTypeName,
                                             List<String> sidecarFieldNames,
                                             List<String> missingSidecarFieldNames,
                                             boolean requestTypeAvailable,
                                             boolean complete) {
        this(serviceName, roleType, roleParameterName, performerTypes, requestTypeName, sidecarFieldNames,
                missingSidecarFieldNames,
                requestTypeAvailable ? "REQUEST_ENVELOPE" : "UNBOUND_REQUEST",
                requestTypeAvailable, complete, "", List.of(), List.of(), List.of(), "");
    }

    public RoleParticipantRequestBindingPlan {
        performerTypes = performerTypes == null ? List.of() : List.copyOf(performerTypes);
        sidecarFieldNames = sidecarFieldNames == null ? List.of() : List.copyOf(sidecarFieldNames);
        missingSidecarFieldNames = missingSidecarFieldNames == null
                ? List.of()
                : List.copyOf(missingSidecarFieldNames);
        identityBindingSource = identityBindingSource == null || identityBindingSource.isBlank()
                ? "UNBOUND_REQUEST"
                : identityBindingSource;
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
