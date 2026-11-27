package com.servpp.lang.lint;

import com.servpp.lang.antlr.SppLexer;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalRedundancyTest {
    @Test public void detectsCopiedFieldSetsAndSameNameTakeMappings() {
        var sources = List.of(source("types.spp", "domain d; "
                + "Entity Person { String name; int age; } "
                + "Role Customer { String name; int age; } "
                + "Role PersonView { String name; int age; } "
                + "take Person as PersonView { int age = age; String name = name; }"));
        var warnings = run(sources);
        assertEquals(List.of("SPP_REDUNDANT_COPY_TYPE", "SPP_REDUNDANT_TAKE_MAP"), warnings);
    }

    @Test public void differentFieldTypesAndExplicitDifferentMappingsAreLegitimate() {
        var sources = List.of(source("types.spp", "domain d; "
                + "Entity Person { String name; int age; } "
                + "Role Customer { String name; long age; } "
                + "Role PersonView { int id; } "
                + "take Person as PersonView { int sequence = id; }"));
        assertTrue(run(sources).isEmpty());
    }

    @Test public void takeMappingMustCoverExactlyTheSharedFields() {
        var partial = source("partial.spp", "domain d; "
                + "Role Source { String name; int age; } Role Target { String name; int age; } "
                + "take Source as Target { String name = name; }");
        var mixed = source("mixed.spp", "domain d; "
                + "Role Source { String name; } Role Target { String name; } "
                + "take Source as Target { String name = name; takeall; }");
        for (var source : List.of(partial, mixed)) {
            List<String> warnings = run(List.of(source));
            assertTrue(warnings.toString(), !warnings.contains("SPP_REDUNDANT_TAKE_MAP"));
        }
    }

    @Test public void warnsOnNullNullBoundAndAcceptsRealLimit() {
        var sources = List.of(source("bounds.spp", "domain d; Entity E { int id; } "
                + "atomic calculate read(E) {} "
                + "Scenario all(E) { read bounds { value: [null, null]; }; "
                + "read bounds { value: [0, null]; }; }"));
        assertEquals(List.of("SPP_REDUNDANT_VACUOUS_BOUNDS"), run(sources));
    }

    @Test public void warnsOnEmptyAndSpaceOnlyStringsButNotMeaningfulText() {
        var sources = List.of(source("blank.spp", """
                domain d; Role Input { String value; }
                atomic calculate set(Input) { Input.value = "  "; String local = " ";
                    if (Input.value == "") ; if (Input.value == "x") ;
                    if (Input.value == (" " + " ")) ;
                    Input.value += " "; if (Input.value < " ") ; }
                """));
        assertEquals(List.of("SPP_REDUNDANT_PADDED_BLANK", "SPP_REDUNDANT_PADDED_BLANK",
                "SPP_REDUNDANT_PADDED_BLANK"), run(sources));
    }

    @Test public void preservesFixedStringWidthsAndResolvesNestedScenarioFields() {
        var sources = List.of(
                source("types.spp", """
                        domain d; Entity Address { String(8) code; }
                        Role Note { String(7) code; Address address; }
                        Scenario inspect(Note note) {
                            if (note.address.code == " ") { }
                        }
                        """),
                source("more.spp", "domain d; Role Other { String(9) code; } "
                        + "Role WidthOne { String(8) code; } Role WidthTwo { String(9) code; }"));
        var warnings = run(sources);
        assertEquals(List.of("SPP_REDUNDANT_COPY_TYPE", "SPP_REDUNDANT_COPY_TYPE",
                "SPP_REDUNDANT_PADDED_BLANK"), warnings);
    }

    private static List<String> run(List<RedundancyLint.Source> sources) {
        List<String> warnings = new ArrayList<>();
        LocalRedundancy.check(sources, (source, token, code, message) -> warnings.add(code));
        return warnings;
    }

    private static RedundancyLint.Source source(String path, String text) {
        var lexer = new SppLexer(CharStreams.fromString(text));
        var parser = new SppParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        var tree = parser.program();
        if (parser.getNumberOfSyntaxErrors() != 0) throw new AssertionError("Invalid fixture " + path);
        return new RedundancyLint.Source(path, tree);
    }
}
