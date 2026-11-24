package com.servpp.lang;

import com.servpp.lang.models.ExecuteBlock;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppScenario;
import com.servpp.lang.models.SppServiceCall;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ReadKeyBindingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private SppDomain compile(String scenario) throws Exception {
        var project = new SppProject();
        var spp = temporary.newFile("reads.spp");
        Files.writeString(spp.toPath(), """
                domain reads;
                Role Item { String(8) code; int edition; String title; }
                Role Selection { String(8) wanted; String note; }
                Role Release { int edition; }
                Role EditionOverride { int number; }
                Role Wide { String(9) wider; }
                Role Unspecified { String wider; }
                Role Wrong { int number; }
                Role Unrelated { String(8) code; }
                Role Outer { String(8) external; }
                take Selection as Item { String code = wanted; String title = note; }
                take Release as Item { mapsame; }
                take EditionOverride as Item { int edition = number; }
                take Wide as Item { String code = wider; }
                take Unspecified as Item { String code = wider; }
                take Wrong as Item { String code = number; }
                take Outer as Selection { String wanted = external; }
                Role Copy { Item item; String serialNumber; }
                const int EDITION = 3;
                atomic query listCopies(key Item item, Copy[] matches);
                """ + scenario);
        new SppCompiler(spp, project).compile();
        var app = temporary.newFile("reads.app");
        Files.writeString(app.toPath(), """
                domain reads;
                tables roles {
                    table ITEMS(Item) { primarykey(Item.edition, Item.code); }
                    table COPIES(Copy) {
                        primarykey(Copy.serialNumber);
                        foreignkey(Copy.item) references Item;
                    }
                }
                scope { local {} }
                phy { repository records {
                    adapter jdbcObjectRepository;
                    config default = true;
                    config datasourceUrl = "jdbc:h2:mem:reads";
                    config driverClassName = "org.h2.Driver";
                } }
                """);
        new AppCompiler(app, project).compile();
        return project.getDomain("reads");
    }

    @Test public void resolvesTwoParticipantsAndBothConstantFormsRegardlessOfAppOrder() throws Exception {
        var domain = compile("""
                Scenario serial read(Item item, Selection fromInput, Release release, Copy[] matches) {
                    findItem by Selection, release;
                    listCopies(item, matches) by EditionOverride.number = EDITION, Item.code = "RS";
                }
                """);
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var scene = (SppScenario) domain.getSppClass("Read");
        var block = (ExecuteBlock) scene.getServiceBody().getSppServiceCallList().get(0);
        var implicit = (SppServiceCall) block.getExecuteCallList().get(0);
        assertEquals(List.of("item.edition", "item.code"),
                implicit.getBoundReadKeys().stream().map(SppServiceCall.BoundReadKey::target).toList());
        assertEquals(List.of("release.getEdition()", "fromInput.getWanted()"),
                implicit.getBoundReadKeys().stream().map(SppServiceCall.BoundReadKey::value).toList());
        var list = (SppServiceCall) block.getExecuteCallList().get(1);
        assertEquals(List.of("EDITION", "\"RS\""),
                list.getBoundReadKeys().stream().map(SppServiceCall.BoundReadKey::value).toList());
        assertTrue(domain.unusedParticipantWarnings().toString(), domain.unusedParticipantWarnings().isEmpty());
    }

    @Test public void resolvesRenamesThroughTheTakeChain() throws Exception {
        var domain = compile("""
                Scenario serial read(Item item, Outer outer) {
                    findItem by Outer, Item.edition = 3;
                }
                """);
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var scene = (SppScenario) domain.getSppClass("Read");
        var block = (ExecuteBlock) scene.getServiceBody().getSppServiceCallList().get(0);
        var call = (SppServiceCall) block.getExecuteCallList().get(0);
        assertEquals("outer.getExternal()", call.getBoundReadKeys().get(1).value());
    }

    @Test public void uniqueAcceptsPartialKeysAndMappedNonKeyFields() throws Exception {
        var domain = compile("""
                Enum Problems { DUPLICATE:"D02" }
                Scenario serial read(Item item, Selection lookup) {
                    unique findItem by lookup else Problems.DUPLICATE;
                    unique findItem by Item.title = "sample";
                }
                """);
        assertEquals(List.of(), domain.validateAppDeclarations());
        var scene = (SppScenario) domain.getSppClass("Read");
        var block = (ExecuteBlock) scene.getServiceBody().getSppServiceCallList().get(0);
        var call = (SppServiceCall) block.getExecuteCallList().get(0);
        assertEquals(List.of("item.code", "item.title"), call.getBoundReadKeys().stream()
                .map(SppServiceCall.BoundReadKey::target).toList());
        assertEquals("\"D02\"", call.getUniqueCodeJava());
        assertFalse(call.isPrimaryKeyCovered());
    }

    @Test public void uniqueWarnsWhenAllPrimaryKeyFieldsAreBound() throws Exception {
        var domain = compile("""
                Scenario serial read(Item item, Selection source, Release release) {
                    unique findItem by source, release;
                }
                """);
        assertEquals(List.of(), domain.validateAppDeclarations());
        assertTrue(domain.unusedParticipantWarnings().stream().anyMatch(w -> w.contains("SPP_REDUNDANT_UNIQUE")));
    }

    @Test public void uniqueRejectsCollection() throws Exception {
        assertDiagnostic(compile("Scenario serial read(Item[] items) { unique findItem by Item.edition = 3; }"),
                "scalar");
    }

    @Test public void elseRequiresUnique() throws Exception {
        reject("Release source", "findItem by source else Problems.DUPLICATE;", "requires unique");
    }

    @Test public void uniqueElseRequiresEnumMember() throws Exception {
        reject("Release source", "unique findItem by source else EDITION;", "scalar enum member");
    }

    @Test public void rejectsMissingSource() throws Exception {
        reject("Selection source", "findItem by source;", "missing source for edition");
    }

    @Test public void rejectsDuplicateSource() throws Exception {
        reject("Selection source", "findItem by source, Item.code = \"RS\", Item.edition = EDITION;",
                "duplicate source for code");
    }

    @Test public void rejectsTargetConstantOnNonKey() throws Exception {
        reject("Selection source", "findItem by source, Item.title = \"RS\";", "constant does not bind a primary-key field");
    }

    @Test public void rejectsPerformerConstantThatDoesNotMapToKey() throws Exception {
        reject("Selection source", "findItem by Selection.note = \"RS\";", "constant field does not map to a primary key");
    }

    @Test public void rejectsParticipantWithoutTakeEvenWithSameNamedField() throws Exception {
        reject("Unrelated source", "findItem by source, Item.edition = EDITION;", "has no take relation to Item");
    }

    @Test public void rejectsIncompatibleType() throws Exception {
        reject("Wrong source", "findItem by source, Item.edition = EDITION;", "type mismatch for code");
    }

    @Test public void rejectsIncompatibleDeclaredWidth() throws Exception {
        reject("Wide source", "findItem by source, Item.edition = EDITION;", "declared-width mismatch for code");
    }

    @Test public void rejectsAnUnspecifiedSourceWidthForADeclaredWidth() throws Exception {
        reject("Unspecified source", "findItem by source, Item.edition = EDITION;", "declared-width mismatch for code");
    }

    @Test public void rejectsAnIncompatibleConstant() throws Exception {
        reject("Selection source", "findItem by source, Item.edition = \"3\";", "type mismatch for edition");
    }

    @Test public void rejectsAnOverwidthStringLiteral() throws Exception {
        reject("Selection source", "findItem by Selection.wanted = \"NINECHARS\", Item.edition = EDITION;",
                "declared-width mismatch for Selection.wanted");
    }

    @Test public void rejectsAnAmbiguousParticipantType() throws Exception {
        reject("Selection first, Selection second", "findItem by Selection, Item.edition = EDITION;",
                "unknown or ambiguous scalar participant: Selection");
    }

    @Test public void rejectsPositionalBinding() {
        assertThrows(IOException.class, () -> compile("""
                Scenario serial read(Item item, Selection source) { findItem by source.wanted, EDITION; }
                """));
    }

    @Test public void rejectsBindingOutsideScenarios() throws Exception {
        assertDiagnostic(compile("atomic calculate derive(Item item) { findItem by Item.code = \"RS\"; }"),
                "allowed only in scenarios");
    }

    @Test public void rejectsBindingToNonQuery() throws Exception {
        assertDiagnostic(compile("""
                atomic calculate derive(Item item) {}
                Scenario serial read(Item item) { derive by Item.edition = EDITION; }
                """), "requires a keyed query");
    }

    private void reject(String parameters, String call, String message) throws Exception {
        assertDiagnostic(compile("Scenario serial read(Item item, " + parameters + ") { " + call + " }"), message);
    }

    private void assertDiagnostic(SppDomain domain, String fragment) {
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(fragment)));
    }
}
