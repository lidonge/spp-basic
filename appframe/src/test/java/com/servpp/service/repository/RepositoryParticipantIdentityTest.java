package com.servpp.service.repository;

import org.junit.Test;
import static org.junit.Assert.*;

public class RepositoryParticipantIdentityTest {
    public static class RoleFacts { public int amount; }
    public static class EntityFacts { public String name; }

    @Test public void transferredFactsRetainBothRoleAndPerformerStorageIdentities() {
        var role = new RoleFacts();
        var performer = new EntityFacts();
        RepositoryParticipantSnapshots.execute(() -> {
            RepositoryParticipantSnapshots.loaded(role, null, new ObjectSnapshot("Role", "role-key", "{}", "", "r1"));
            RepositoryParticipantSnapshots.loaded(performer, null, new ObjectSnapshot("Entity", "entity-key", "{}", "", "r2"));
            RepositoryParticipantSnapshots.transfer(performer, role);
            assertEquals("role-key", RepositoryParticipantSnapshots.objectIdentity(role, "Role").orElseThrow());
            assertEquals("entity-key", RepositoryParticipantSnapshots.objectIdentity(role, "Entity").orElseThrow());
            var projection = new RoleFacts();
            RepositoryParticipantSnapshots.transfer(role, projection);
            assertEquals("role-key", RepositoryParticipantSnapshots.objectIdentity(projection, "Role").orElseThrow());
        });
        assertTrue(RepositoryParticipantSnapshots.objectIdentity(role, "Role").isEmpty());
    }

    @Test public void conflictingIdentitiesAreRejectedInsteadOfSelectingAnArbitraryRow() {
        RepositoryParticipantSnapshots.execute(() -> {
            var role = new RoleFacts();
            RepositoryParticipantSnapshots.loaded(role, null, new ObjectSnapshot("Role", "first", "{}", "", "r1"));
            RepositoryParticipantSnapshots.loaded(role, null, new ObjectSnapshot("Role", "second", "{}", "", "r2"));
            assertEquals("PERSISTENCE_QUERY_IDENTITY_AMBIGUOUS", assertThrows(IllegalStateException.class,
                    () -> RepositoryParticipantSnapshots.objectIdentity(role, "Role")).getMessage());
        });
    }
}
