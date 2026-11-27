package com.servpp.sdk.compiler;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
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
        var conflict = result.conflicts().getFirst();
        assertEquals("name-conflict", conflict.kind());
        assertEquals("original=1, otherLoss=1", conflict.names());
        assertEquals("loss", conflict.suggestion());
        assertTrue(result.changed().isEmpty());
    }

    @Test public void parsesNestedStatementIncludesAndKeepsOriginalLocations() throws Exception {
        var root = folder.getRoot().toPath();
        var fragments = Files.createDirectories(root.resolve("shared/src/main/spp/statements"));
        var nested = Files.writeString(fragments.resolve("nested.spp"), "audit(input);\n");
        var entry = Files.writeString(fragments.resolve("entry.spp"), "include nested(input=subject);\n");
        var source = Files.writeString(root.resolve("services.spp"), """
                domain example;
                Entity LedgerEntry { int amount; }
                atomic check validate(LedgerEntry chosen) {
                    // an include can also expand into calculation statements
                    include valid(subject=chosen);
                }
                Scenario register(LedgerEntry chosen) {
                    /*
                    include nonexistent;
                    */
                    transaction {
                        include entry(subject=chosen);
                    }
                }
                atomic calculate after(LedgerEntry left, LedgerEntry right) {}
                """);
        var check = Files.writeString(fragments.resolve("valid.spp"), """
                require(subject.amount > 0, Codes.BAD_ENTRY);
                require(subject.amount < 100, Codes.BAD_ENTRY);
                """);
        var paths = List.of(source, entry, nested, check);
        var result = new ParticipantMigration().migrate(paths, false, Map.of("LedgerEntry", "chosen"));
        assertEquals(2, result.servicesRewritten());
        assertEquals(1, result.conflicts().size());
        var duplicate = result.conflicts().getFirst();
        assertEquals("duplicate-type", duplicate.kind());
        assertEquals("left, right", duplicate.names());
        assertTrue(duplicate.tsv(root), duplicate.tsv(root).contains("services.spp:15\tafter"));
        assertEquals(1, result.changed().size());
        String changed = result.changed().get(source);
        assertTrue(changed.contains("Scenario register(LedgerEntry)"));
        assertTrue(changed.contains("include entry(subject=chosen);"));
        assertTrue(changed.contains("include valid(subject=chosen);"));
        assertTrue(changed.contains("include nonexistent;"));

        var renamed = new ParticipantMigration().migrate(paths, false, Map.of("LedgerEntry", "item"));
        assertEquals(0, renamed.servicesRewritten());
        assertEquals(3, renamed.conflicts().size());
        assertTrue(renamed.conflicts().stream().anyMatch(conflict -> conflict.detail().contains("include binding")));
    }

    @Test public void reportsBrokenExpansionAtTheIncludeLine() throws Exception {
        var root = folder.getRoot().toPath();
        var fragments = Files.createDirectories(root.resolve("src/main/spp/statements"));
        var fragment = Files.writeString(fragments.resolve("broken.spp"), "require(, invalid);\n");
        var source = Files.writeString(root.resolve("services.spp"), """
                domain example;
                atomic check validate() {
                    include broken;
                }
                """);
        var result = new ParticipantMigration().migrate(List.of(source, fragment), false);
        assertEquals(1, result.conflicts().size());
        var parse = result.conflicts().getFirst();
        assertEquals("parse", parse.kind());
        assertTrue(parse.tsv(root), parse.tsv(root).contains("services.spp:3"));
    }

    @Test public void countsAliasesAndAppliesQualifiedScalarAndArrayMappings() throws Exception {
        var source = folder.newFile("services.spp").toPath();
        Files.writeString(source, """
                domain example;
                Entity LedgerEntry short existing /* keep */, existingItems { int amount; }
                atomic calculate first(LedgerEntry old) { old.amount = 1; }
                atomic calculate second(LedgerEntry old) { old.amount = 2; }
                atomic calculate third(LedgerEntry alternate) { alternate.amount = 3; }
                atomic calculate fourth(LedgerEntry, LedgerEntry[]) {
                    existing.amount = 4;
                    for (LedgerEntry row : existingItems) { existing.amount += row.amount; }
                }
                """);
        var paths = List.of(source);
        var report = new ParticipantMigration().migrate(paths, false);
        assertEquals(1, report.conflicts().size());
        assertEquals("old=2, alternate=1", report.conflicts().getFirst().names());
        assertEquals("old", report.conflicts().getFirst().suggestion());
        var map = folder.newFile("names.tsv").toPath();
        Files.writeString(map, "# selected aliases\nLedgerEntry\tglobal\nexample.LedgerEntry\tentry\n"
                + "example.LedgerEntry[]\trows\n");
        var mapping = ParticipantMigration.readMapping(map);
        var result = new ParticipantMigration().migrate(paths, false, mapping);
        assertEquals(List.of(), result.conflicts());
        assertEquals(4, result.servicesRewritten());
        String changed = result.changed().get(source);
        assertTrue(changed.contains("LedgerEntry short entry /* keep */, rows"));
        assertTrue(changed.contains("calculate third(LedgerEntry) { entry.amount = 3; }"));
        assertTrue(changed.contains("for (LedgerEntry row : rows) { entry.amount += row.amount; }"));
        Files.writeString(source, changed);
        assertTrue(new ParticipantMigration().migrate(paths, false, mapping).changed().isEmpty());
    }
}
