package com.servpp.lang.lint;

import com.servpp.lang.antlr.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class QueryLintTest {
    private static final String MODEL = """
            Role Entry { int entryNumber; int tag; int area; }
            Contract Summary { int observed; }
            atomic query listEntries(key Entry selector, Entry[] rows);
            atomic calculate consume(Entry[] items, Summary summary) {
                for (Entry row : items) { summary.observed = row.tag; break; }
            }
            atomic calculate count(Entry[] rows, Summary summary) { summary.observed = rows.length; }
            atomic calculate clear(Entry[] rows) { rows = new Entry[]{}; }
            """;
    private static final String SCENE = "Scenario serial inspect(Entry selector, Entry[] rows, Summary summary) ";
    private static final String QUANTUM = """
            Role Drawer { quantum long cash; }
            Role Till { quantum long cash; }
            Contract Payment { long amount; }
            quantum update move(Drawer source, Till target, Payment input) {
                source.cash -> target.cash : input.amount;
            }
            Scenario serial pay(Drawer wallet, Till till, Payment receipt) {
                transaction { move(wallet, till, receipt) %s; }
            }
            """;

    @Test public void firstOnlyFollowsExplicitAndImplicitCallsAcrossFiles() {
        var result = warnings("SPP_QUERY_FIRST_ONLY", spp("services.spp", MODEL),
                spp("view.spp", SCENE + "{ listEntries; consume(rows, summary); }"));
        assertEquals(1, result.size());
        assertEquals("view.spp", result.get(0).path());
        assertEquals("listEntries", result.get(0).token().getText());
        assertTrue(result.get(0).message().contains("unique find"));
        assertEquals(1, warnings("SPP_QUERY_FIRST_ONLY", spp("nested.spp", MODEL + SCENE.replace(" serial", "")
                + "{ serial { listEntries; consume; } }")).size());
    }

    @Test public void firstOnlySkipsMixedReadsConditionalBreaksAndUnorderedCalls() {
        String body = "{ listEntries; consume; }";
        for (String model : List.of(MODEL.replace("break;", "if (row.tag > 0) { break; }"),
                MODEL.replace("summary.observed = row.tag;", "if (row.tag > 0) { continue; } summary.observed = row.tag;"),
                MODEL.replace("for (Entry row", "summary.observed = items.length; for (Entry row"))) {
            assertNone("SPP_QUERY_FIRST_ONLY", spp("scene.spp", model + SCENE + body));
        }
        assertNone("SPP_QUERY_FIRST_ONLY", scene("{ listEntries; consume; count; }"));
        assertNone("SPP_QUERY_FIRST_ONLY", scene("{ parallel { listEntries; consume; } }"));
        assertNone("SPP_QUERY_FIRST_ONLY", scene("{ listEntries; count; consume; }"));
    }

    @Test public void unusedResultPointsToTheDiscardedRead() {
        for (String next : List.of("listEntries(selector, rows); count;", "clear(rows);")) {
            var result = warnings("SPP_UNUSED_QUERY_RESULT", scene("{ listEntries; " + next + " }"));
            assertEquals(1, result.size());
            assertEquals("listEntries", result.get(0).token().getText());
            assertEquals(SCENE.length() + 2, result.get(0).token().getCharPositionInLine());
        }
    }

    @Test public void reboundScalarIdentityDiscardsPreviousLookupButAnImplicitKeyMayConsumeIt() {
        var second = "findEntry by Entry.entryNumber = 2;";
        String start = "Scenario serial read(Entry) { findEntry by Entry.entryNumber = 1; ";
        assertEquals(1, warnings("SPP_UNUSED_QUERY_RESULT", spp("read.spp", MODEL + start + second + " }"), table("")).size());
        assertNone("SPP_UNUSED_QUERY_RESULT", spp("read.spp", MODEL + start + "findEntry; }"), table(""));
    }

    @Test public void keepsReturnedResultsAndReadsBeforeAReplacement() {
        for (String body : List.of("{ listEntries; }", "{ listEntries; count; clear; }",
                "{ listEntries; if (selector.entryNumber > 0) { clear; } }",
                "{ parallel { listEntries; clear; } }", "{ parallel { serial { listEntries; clear; } count; } }",
                "{ listEntries; identify(rows); clear; }",
                "{ listEntries; unknown; clear; }"))
            assertNone("SPP_UNUSED_QUERY_RESULT", scene(body));
        assertNone("SPP_UNUSED_QUERY_RESULT", scene("{ listEntries; clear; }"),
                app("scope { remote { listEntries; } }"));
    }

    @Test public void missingIndexUsesMappedFieldsAndQuerySource() {
        var result = warnings("SPP_FIND_MISSING_INDEX", lookup("record.tag = 4, Entry.area = 2"),
                table("index byId(Entry.entryNumber, Entry.tag);"));
        assertEquals(1, result.size());
        assertEquals("lookup.spp", result.get(0).path());
        assertEquals("findEntry", result.get(0).token().getText());
        assertTrue(result.get(0).message().contains("index(Entry.tag, Entry.area)"));
    }

    @Test public void missingIndexResolvesARenamedTakeField() {
        var source = spp("read.spp", MODEL + """
                Role Filter { int selected; }
                take Filter as Entry { int tag = selected; }
                Scenario serial read(Entry, Filter input) { unique findEntry by input; }
                """);
        assertEquals(1, warnings("SPP_FIND_MISSING_INDEX", source, table("")).size());
        assertNone("SPP_FIND_MISSING_INDEX", source, table("index byTag(Entry.tag);"));
    }

    @Test public void missingIndexSkipsPrimaryAndUsableIndexesAndUnknownMappings() {
        assertNone("SPP_FIND_MISSING_INDEX", lookup("Entry.entryNumber = 1, Entry.tag = 2"), table(""));
        assertNone("SPP_FIND_MISSING_INDEX", lookup("Entry.tag = 2"),
                table("unique index byTag(Entry.tag, Entry.area);"));
        assertNone("SPP_FIND_MISSING_INDEX", lookup("Entry.tag = 2"), app("phy { backend database; }"));
        assertNone("SPP_FIND_MISSING_INDEX", lookup("Entry.tag = 2"), table(""), app("scope { remote { findEntry; } }"));
    }

    @Test public void readOnlyTransactionChecksNestedReads() {
        var result = warnings("SPP_READ_ONLY_TRANSACTION", scene("{ transaction { serial { findEntry(selector); listEntries; } } }"));
        assertEquals(1, result.size());
        assertEquals("transaction", result.get(0).token().getText());
    }

    @Test public void readOnlyTransactionSkipsWritesLocksAndDeliberateBoundaries() {
        for (String block : List.of("transaction {}", "transaction { findEntry(selector); unknown; }",
                "transaction { if (selector.entryNumber > 0) { updateEntry(selector); } listEntries; }",
                "transaction { listEntries; } fail { clear; }", "transaction REQUIRES_NEW { listEntries; }"))
            assertNone("SPP_READ_ONLY_TRANSACTION", scene("{ " + block + " }"));
        assertNone("SPP_READ_ONLY_TRANSACTION", scene("{ transaction { listEntries; } }"),
                app("scope { local { repository(argument(0, Entry.tag), rows) for update listEntries; } }"));
    }

    @Test public void transferChecksTheActualDebitedPositionOnce() {
        String repeated = QUANTUM.replace("source.cash ->", "source.cash -> target.cash : 1; source.cash ->");
        var result = warnings("SPP_UNBOUNDED_TRANSFER", spp("pay.spp", repeated.formatted("bounds { till.cash: [0, null]; }")));
        assertEquals(1, result.size());
        assertEquals("move", result.get(0).token().getText());
        assertTrue(result.get(0).message().contains("wallet.cash: [0, null]"));
    }

    @Test public void transferRecognizesCallAndPhysicalBoundsAndZeroAmounts() {
        assertNone("SPP_UNBOUNDED_TRANSFER", spp("pay.spp", QUANTUM.formatted("bounds { wallet.cash: [0, null]; }")));
        assertNone("SPP_UNBOUNDED_TRANSFER", spp("pay.spp", QUANTUM.formatted("").replace(": input.amount;", ": (0);")));
        assertNone("SPP_UNBOUNDED_TRANSFER", spp("pay.spp", QUANTUM.formatted("")
                .replace(": input.amount;", ": input.amount; check(source.cash >= 0);")));
        assertNone("SPP_UNBOUNDED_TRANSFER", spp("pay.spp", QUANTUM.formatted("")),
                app("phy { quantumStore store { adapter quantumService; quantum \"Drawer.cash\" { minValue \"0\"; } } }"));
    }

    @Test public void transferResolvesLoopElementsToTheBoundCollection() {
        String service = QUANTUM.replace("Drawer source", "Drawer[] drawers")
                .replace("source.cash -> target.cash : input.amount;",
                        "for (Drawer drawer : drawers) { drawer.cash -> target.cash : input.amount; }")
                .replace("pay(Drawer wallet", "pay(Drawer[] wallet");
        assertEquals(1, warnings("SPP_UNBOUNDED_TRANSFER", spp("pay.spp", service.formatted(""))).size());
        assertNone("SPP_UNBOUNDED_TRANSFER", spp("pay.spp", service.formatted("bounds { wallet.cash: [0, null]; }")));
    }

    private static RedundancyLint.Source scene(String body) {
        return spp("scene.spp", MODEL + SCENE + body);
    }

    private static RedundancyLint.Source lookup(String predicates) {
        return spp("lookup.spp", MODEL + "Scenario serial lookup(Entry record) { unique findEntry by " + predicates + "; }");
    }

    private static RedundancyLint.Source table(String index) {
        return app("tables entities { table ENTRIES(Entry) { primarykey(Entry.entryNumber); " + index + " } }");
    }

    private static RedundancyLint.Source spp(String path, String text) {
        var parser = new SppParser(new CommonTokenStream(new SppLexer(CharStreams.fromString("domain ledger;\n" + text))));
        var tree = parser.program();
        assertEquals(text, 0, parser.getNumberOfSyntaxErrors());
        return new RedundancyLint.Source(path, tree);
    }

    private static RedundancyLint.Source app(String text) {
        var parser = new AppParser(new CommonTokenStream(new AppLexer(CharStreams.fromString("domain ledger;\n" + text))));
        var tree = parser.program();
        assertEquals(text, 0, parser.getNumberOfSyntaxErrors());
        return new RedundancyLint.Source("ledger.app", tree);
    }

    private static List<RedundancyLint.Finding> warnings(String code, RedundancyLint.Source... sources) {
        var findings = new ArrayList<RedundancyLint.Finding>();
        QueryLint.check(List.of(sources), (source, token, id, message) -> {
            if (id.equals(code)) findings.add(new RedundancyLint.Finding(source.path(), token, id, message));
        });
        return findings;
    }

    private static void assertNone(String code, RedundancyLint.Source... sources) {
        assertEquals(List.of(), warnings(code, sources));
    }
}
