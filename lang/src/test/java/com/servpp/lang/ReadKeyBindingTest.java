package com.servpp.lang;

import com.servpp.lang.models.ExecuteBlock;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppScenario;
import com.servpp.lang.models.SppServiceCall;
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
                Role Selection { String(8) wanted; String(9) wider; int edition; }
                Role Copy { Item item; String serialNumber; }
                const int EDITION = 3;
                atomic query listCopies(key Item item, Copy[] matches);
                """ + scenario);
        new SppCompiler(spp, project).compile();
        var app = temporary.newFile("reads.app");
        Files.writeString(app.toPath(), """
                domain reads;
                tables roles {
                    table ITEMS(Item) { primarykey(Item.code, Item.edition); }
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

    @Test public void resolvesCompositeImplicitAndDeclaredListKeysInAppOrder() throws Exception {
        var domain = compile("""
                Scenario serial read(Item item, Selection fromInput, Copy[] matches) {
                    findItem by fromInput.wanted, EDITION;
                    listCopies(item, matches) by "RS", -2;
                }
                """);
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var scene = (SppScenario) domain.getSppClass("Read");
        var block = (ExecuteBlock) scene.getServiceBody().getSppServiceCallList().get(0);
        var calls = block.getExecuteCallList();
        var implicit = (SppServiceCall) calls.get(0);
        assertEquals(List.of("fromInput.wanted", "EDITION"),
                implicit.getReadKeys().stream().map(SppServiceCall.ReadKey::text).toList());
        assertEquals(List.of("item.code", "item.edition"),
                implicit.getBoundReadKeys().stream().map(SppServiceCall.BoundReadKey::target).toList());
        var list = (SppServiceCall) calls.get(1);
        assertEquals(List.of("\"RS\"", "-2"), list.getReadKeys().stream().map(SppServiceCall.ReadKey::text).toList());
        assertEquals(2, list.getBoundReadKeys().size());
        assertTrue(domain.unusedParticipantWarnings().toString(), domain.unusedParticipantWarnings().isEmpty());
    }

    @Test public void rejectsWrongKeyCount() throws Exception {
        reject("findItem by source.wanted;", "wrong key count");
    }

    @Test public void rejectsIncompatibleType() throws Exception {
        reject("findItem by source.edition, EDITION;", "type mismatch");
    }

    @Test public void rejectsIncompatibleDeclaredWidth() throws Exception {
        reject("findItem by source.wider, EDITION;", "declared-width mismatch");
    }

    @Test public void rejectsAnOverwidthStringLiteral() throws Exception {
        reject("findItem by \"NINECHARS\", EDITION;", "declared-width mismatch");
    }

    @Test public void rejectsUnknownField() throws Exception {
        reject("findItem by source.missing, EDITION;", "unknown or ambiguous source");
    }

    @Test public void rejectsAnUnqualifiedTypeWithMultipleSourceInstances() throws Exception {
        var domain = compile("""
                Scenario serial read(Item item, Selection first, Selection second) {
                    findItem by Selection.wanted, EDITION;
                }
                """);
        assertDiagnostic(domain, "unknown or ambiguous source: Selection.wanted");
    }

    @Test public void rejectsUnimplementedParticipantShorthand() throws Exception {
        reject("findItem by source, EDITION;", "unknown or ambiguous source");
    }

    @Test public void rejectsBindingOutsideScenarios() throws Exception {
        assertDiagnostic(compile("atomic calculate derive(Item item) { findItem by item.code, EDITION; }"),
                "allowed only in scenarios");
    }

    @Test public void rejectsBindingToNonQuery() throws Exception {
        assertDiagnostic(compile("""
                atomic calculate derive(Item item) {}
                Scenario serial read(Item item) { derive by item.code, EDITION; }
                """), "requires a keyed query");
    }

    private void reject(String call, String message) throws Exception {
        assertDiagnostic(compile("Scenario serial read(Item item, Selection source) { " + call + " }"), message);
    }

    private void assertDiagnostic(SppDomain domain, String fragment) {
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(fragment)));
    }
}
