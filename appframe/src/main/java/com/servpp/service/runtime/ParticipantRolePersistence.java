package com.servpp.service.runtime;

import com.servpp.service.repository.ObjectRepository;
import com.servpp.service.repository.RepositoryWriteRule;
import com.servpp.service.repository.RepositoryWriteSession;
import java.util.*;
import java.util.function.Supplier;

/** Only owned performers with APP tables participate in a role's ordinary persistence. */
public final class ParticipantRolePersistence {
    public record Step(Class<?> performer, List<String> keys, MaterializedRolePerformers.Definition definition) { }
    private ParticipantRolePersistence() { }
    public static void persist(Object participant, Supplier<ObjectRepository> repository, RepositoryWriteRule.Operation operation, Step step) {
        if (participant instanceof Iterable<?> members) { for (Object member : members) persist(member,repository,operation,step); return; }
        Object performer = MaterializedRolePerformers.materialize(step.definition(),participant).get(step.performer().getSimpleName());
        var rule = new RepositoryWriteRule(operation,0,step.performer().getSimpleName(),step.keys(),null);
        RepositoryWriteSession.prepare(repository,List.of(rule),new Object[]{performer}).persist();
    }
}
