package com.servpp.service.runtime;

import com.servpp.service.repository.RepositoryParticipantSnapshots;
import java.util.Map;

/** A generated default query recursively resolves the participants that perform its role. */
public final class ParticipantRoleQueries {
    public record Step(String provider, Class<?> performer, String[] facts, MaterializedRolePerformers.Definition definition) { }
    private ParticipantRoleQueries() { }
    public static void begin(Object participant) {
        if (participant instanceof java.util.Collection<?> members) {
            for (Object member : members) ParticipantIdentity.confirm(member,true);
            ParticipantIdentity.confirm(participant,!members.isEmpty());
        } else ParticipantIdentity.confirm(participant,true);
    }
    public static void complete(Object participant, Step step) {
        if (participant instanceof Iterable<?> members) {
            boolean identified = members.iterator().hasNext();
            for (Object role : members) { complete(role,step); identified &= ParticipantIdentity.isIdentified(role); }
            ParticipantIdentity.confirm(participant, identified);
            return;
        }
        if (!ParticipantIdentity.isIdentified(participant)) return;
        if (!MaterializedRolePerformers.isQueryActive(step.definition(),participant)) return;
        var entities = MaterializedRolePerformers.materialize(step.definition(),participant);
        Object performer = entities.get(step.performer().getSimpleName());
        com.servpp.service.repository.TransactionQueryReads.project(new Object[]{participant},new Object[]{performer},
                () -> LocalDomainServices.invoke(step.provider(),new Object[]{performer},new Class<?>[]{step.performer()},
                        new String[][]{step.facts()},new String[][]{new String[0]},new boolean[]{false},true));
        if (!ParticipantIdentity.isIdentified(performer)) { ParticipantIdentity.confirm(participant,false); return; }
        try {
            var captured = new java.util.LinkedHashMap<String,Object>();
            for (String property : step.definition().snapshotFacts())
                captured.put(property, com.servpp.service.repository.ObjectPropertyAccess.read(participant, property));
            step.definition().mapper().getClass().getMethod("entityToRole",Map.class,step.definition().role())
                    .invoke(step.definition().mapper(),entities,participant);
            for (var property : java.beans.Introspector.getBeanInfo(participant.getClass(),Object.class).getPropertyDescriptors())
                if (captured.containsKey(property.getName()))
                    property.getWriteMethod().invoke(participant,captured.get(property.getName()));
            PropertyChanges.clean(participant);
            RepositoryParticipantSnapshots.transfer(performer,participant);
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) { throw new IllegalStateException("ROLE_QUERY_MAPPING_FAILED",failure); }
    }
}
