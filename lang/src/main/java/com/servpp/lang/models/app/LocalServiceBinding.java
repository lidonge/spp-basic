package com.servpp.lang.models.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** APP delegation uses participant identity or explicit take; legacy fact projections remain compatible. */
public final class LocalServiceBinding {
    private final String serviceName;
    private final String target;
    private final Map<Integer, List<String>> participantFacts = new LinkedHashMap<>();
    private final Map<Integer, List<String>> participantIdentities = new LinkedHashMap<>();
    private final java.util.Set<Integer> existingCollections = new java.util.HashSet<>();
    public LocalServiceBinding(String serviceName, String target) {
        this.serviceName = serviceName;
        this.target = target;
    }
    private boolean roleBinding;
    private final Map<Integer, ParticipantRole> participantRoles = new LinkedHashMap<>();
    public record ParticipantRole(com.servpp.lang.models.SppClass view,
                                  com.servpp.lang.models.SppRoleMapper mapper, String performerName, boolean performerView, String mapperDomain) {
        public ParticipantRole(com.servpp.lang.models.SppClass view, com.servpp.lang.models.SppRoleMapper mapper, String performerName) {
            this(view, mapper, performerName, false, null);
        }
    }
    public boolean isRoleBinding() { return roleBinding; }
    public void setRoleBinding(boolean value) { roleBinding = value; }
    public Map<Integer, ParticipantRole> getParticipantRoles() { return participantRoles; }
    public String getServiceName() { return serviceName; }
    public String getTarget() { return target; }
    public Map<Integer, List<String>> getParticipantFacts() { return participantFacts; }
    public Map<Integer, List<String>> getParticipantIdentities() { return participantIdentities; }
    public java.util.Set<Integer> getExistingCollections() { return existingCollections; }
}
