package com.servpp.lang.lint;

import com.servpp.lang.antlr.*;
import org.antlr.v4.runtime.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;
import static com.servpp.lang.lint.RedundancyLint.*;

public class MechanicalEditsTest {
    private static Source parse(String path, String text) {
        var input = CharStreams.fromString(text);
        Parser parser = path.endsWith(".app") ? new AppParser(new CommonTokenStream(new AppLexer(input)))
                : new SppParser(new CommonTokenStream(new SppLexer(input)));
        var tree = parser instanceof AppParser app ? app.program() : ((SppParser) parser).program();
        assertEquals(text, 0, parser.getNumberOfSyntaxErrors());
        return new Source(path, tree);
    }

    private static List<Edit> plan(Map<String, String> files, String suffix) {
        var domain = files.entrySet().stream().map(e -> parse(e.getKey(), e.getValue())).toList();
        var finding = analyze(domain).stream().filter(f -> f.code().equals("SPP_REDUNDANT_" + suffix)).findFirst().orElseThrow();
        return MechanicalEdits.forFinding(domain, finding);
    }

    private static Map<String, String> apply(Map<String, String> files, String suffix) {
        var edits = plan(files, suffix);
        assertFalse(suffix, edits.isEmpty());
        Map<String, String> result = new HashMap<>(files);
        edits.stream().sorted(Comparator.comparingInt(Edit::start).reversed()).forEach(edit -> {
            String original = result.get(edit.path());
            result.put(edit.path(), original.substring(0, edit.start()) + edit.replacement() + original.substring(edit.end()));
        });
        var sources = result.entrySet().stream().map(e -> parse(e.getKey(), e.getValue())).toList();
        assertFalse(result.toString(), analyze(sources).stream().anyMatch(f -> f.code().equals("SPP_REDUNDANT_" + suffix)));
        return result;
    }

    @Test public void deletesOnlyTheCrudDeclarationWithUtf16AndCrlf() {
        String first = "domain shop; /*🧾*/ Role Item { int id; }\r\n";
        String last = "// keep\r\nScenario serial lookup(Item i) { findItem(i); }\r\n";
        var source = Map.of("app.spp", first + "  atomic query findItem(Item);\r\n" + last);
        assertEquals(first + last, apply(source, "IMPLICIT_CRUD").get("app.spp"));
        assertTrue(plan(Map.of("app.spp", source.get("app.spp"), "scope.app",
                "domain shop; scope { remote { findItem; } }"), "IMPLICIT_CRUD").isEmpty());
    }

    @Test public void dropsVacuousEntriesAndRemovesAllNullClauseAsOneEdit() {
        String prefix = "domain d; Scenario serial step(R r) { change(r) ";
        for (String clause : List.of("bounds { r.x: [(null), null]; }", "bounds { r.x: [null,null]; r.y: [null,null]; }")) {
            var source = Map.of("s.spp", prefix + clause + "; }");
            assertEquals(prefix + "; }", apply(source, "VACUOUS_BOUNDS").get("s.spp"));
            assertEquals(1, plan(source, "VACUOUS_BOUNDS").size());
        }
        var mixed = Map.of("s.spp", prefix + "bounds { r.x: [null,null]; r.y: [0,null]; }; }");
        assertEquals(prefix + "bounds {  r.y: [0,null]; }; }", apply(mixed, "VACUOUS_BOUNDS").get("s.spp"));
        assertTrue(plan(Map.of("s.spp", prefix + "bounds { r.x: [/*audit*/null,null]; }; }"), "VACUOUS_BOUNDS").isEmpty());
    }

    @Test public void inlinesBuiltinWithoutDeletingGuardedBranchOrBreak() {
        var files = Map.of("defs.spp", """
            domain d; Role Item { int id; }
            Enum Error { MISSING:"PARTICIPANT_NOT_IDENTIFIED" }
            atomic check present(Item i) { require((i), Error.MISSING); }
            """, "flow.spp", "domain d; Scenario serial run(Item i) { present,breakTransaction { use(i); }; }",
            "wiring.app", "domain d; scope { local { present; } }");
        var result = apply(files, "PRESENCE_CHECK");
        assertEquals("domain d; Scenario serial run(Item i) { identify(i),breakTransaction { use(i); }; }", result.get("flow.spp"));
        assertFalse(result.get("wiring.app").contains("present;"));
    }

    @Test public void foldsNamedGuardIntoAdjacentCheckAcrossFilesAndPositions() {
        var files = Map.of("first.spp", "domain d; Role I { int id; } Enum E { MISSING, INVALID } "
                        + "atomic check found(I input) { require(input, E.MISSING); }",
                "second.spp", "domain d; atomic check validate(I left,I right){require(right.id>0,E.INVALID);}",
                "flow.spp", "domain d; Scenario serial process(I a,I b){ if (true) { found(b); validate(a,b); } }");
        var result = apply(files, "PRESENCE_CHECK");
        assertTrue(result.get("second.spp"), result.get("second.spp").contains("require(right, E.MISSING); require(right.id"));
        assertFalse(result.get("flow.spp").contains("found(b)"));
        var withHook = new HashMap<>(files);
        withHook.put("scope.app", "domain d; intercept services { before: observe; }");
        assertTrue(plan(withHook, "PRESENCE_CHECK").isEmpty());
    }

    @Test public void replacesIsolatedProjectionWithTypedTakeAndPreservesLiveCopies() {
        String defs = "domain d; Entity Sender { int number; } Role Result { int id; } ";
        String copy = "atomic calculate project(Sender s, Result r) { r.id=s.number; }";
        var result = apply(Map.of("x.spp", defs + copy), "FIELD_COPY");
        assertEquals(defs + "take Sender as Result { int id = number; }", result.get("x.spp"));
        assertTrue(plan(Map.of("x.spp", defs + copy + " Scenario serial f(Sender s,Result r){project(s,r);}"),
                "FIELD_COPY").isEmpty());
    }

    @Test public void movesLiveCopyToAnEquivalentTypedRead() {
        String defs = "domain d; Entity Sender { int id; } Role Result { int id; } take Sender as Result { mapsame; } ";
        String copy = "atomic calculate project(Sender s, Result r) { r.id=s.id; } ";
        String call = "Scenario serial f(Sender s,Result r){project(s,r); findResult(r);}";
        var files = Map.of("x.spp", defs + copy + call, "x.app", "domain d; tables t { table R(Result) { primarykey(Result.id); } }");
        assertTrue(apply(files, "FIELD_COPY").get("x.spp").contains("{ findResult(r) by s;}"));
        assertTrue(plan(Map.of("x.spp", defs + copy + call.replace(" findResult", " change(s); findResult"),
                "x.app", files.get("x.app")), "FIELD_COPY").isEmpty());
    }

    @Test public void fullSameNameProjectionUsesMapsame() {
        String text = "domain d; Entity Sender { int id; } Role Result { int id; } take Sender as Result { int id=id; }";
        assertTrue(apply(Map.of("s.spp", text), "TAKE_MAP").get("s.spp").endsWith("{ mapsame; }"));
    }
}
