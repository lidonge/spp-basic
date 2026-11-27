package com.servpp.sdk.compiler;

import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ParticipantMigrationTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void migratesNamesWithoutChangingMembersCommentsAndLocals() throws Exception {
        var types = folder.newFile("types.spp").toPath();
        Files.writeString(types, "domain example; Entity ExternalLoss { int amount; }");
        var services = folder.newFile("services.spp").toPath();
        Files.writeString(services, """
                domain example;
                atomic calculate total(ExternalLoss loss, ExternalLoss[] records) {
                    // loss and records are former parameter names.
                    for (ExternalLoss record : records) { loss.amount += record.amount; }
                    { int loss = 0; loss += 1; }
                }
                """);
        var paths = List.of(types, services);
        var result = new ParticipantMigration().migrate(paths, false);
        assertEquals(List.of(), result.conflicts());
        assertEquals(1, result.servicesRewritten());
        assertTrue(result.changed().get(types).contains("ExternalLoss short loss, records"));
        assertTrue(result.changed().get(services).contains("total(ExternalLoss, ExternalLoss[])"));
        assertTrue(result.changed().get(services).contains("{ int loss = 0; loss += 1; }"));
        for (var edit : result.changed().entrySet()) Files.writeString(edit.getKey(), edit.getValue());
        var second = new ParticipantMigration().migrate(paths, false);
        assertTrue(second.conflicts().toString(), second.conflicts().isEmpty());
        assertTrue(second.changed().isEmpty());
    }

    @Test public void reportsConflictsAndPreservesTheAffectedServices() throws Exception {
        var source = folder.newFile("services.spp").toPath();
        Files.writeString(source, """
                domain example;
                Role Loss { int amount; }
                atomic calculate first(Loss original) { original.amount = 1; }
                atomic calculate second(Loss otherLoss) { otherLoss.amount = 2; }
                """);
        var result = new ParticipantMigration().migrate(List.of(source), false);
        assertEquals(1, result.conflicts().size());
        assertTrue(result.conflicts().getFirst().contains("conflicting names"));
        assertTrue(result.changed().isEmpty());
    }
}
