package com.servpp.lang.lint;

import com.servpp.lang.antlr.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ReviewLintTest {
    private static RedundancyLint.Source source(String path, String content) {
        var input = CharStreams.fromString("domain ledger;\n" + content);
        if (path.endsWith(".spp")) {
            var parser = new SppParser(new CommonTokenStream(new SppLexer(input)));
            var tree = parser.program();
            assertEquals(path, 0, parser.getNumberOfSyntaxErrors());
            return new RedundancyLint.Source(path, tree);
        }
        var parser = new AppParser(new CommonTokenStream(new AppLexer(input)));
        var tree = parser.program();
        assertEquals(path, 0, parser.getNumberOfSyntaxErrors());
        return new RedundancyLint.Source(path, tree);
    }

    private static List<RedundancyLint.Finding> run(RedundancyLint.Source... sources) {
        var findings = new ArrayList<RedundancyLint.Finding>();
        ReviewLint.check(List.of(sources), (source, token, code, message) ->
                findings.add(new RedundancyLint.Finding(source.path(), token, code, message)));
        return findings;
    }

    private static List<String> tokens(List<RedundancyLint.Finding> findings, String code) {
        return findings.stream().filter(f -> f.code().equals(code)).map(f -> f.token().getText()).toList();
    }

    @Test public void rawCodesResolveTheFieldTypeAndBothOperandOrders() {
        var findings = run(source("logic.spp", """
                Scenario classify(Envelope request) {
                    if (("O2") == request.details.status) { }
                    if (request.details.status != Status.OPEN) { }
                    if (request.details.label == "O2") { }
                }
                atomic calculate decide(Details) {
                    switch (Details.status) { case "OPEN": break; case CLOSED: break; }
                    if (Details.status == "UNDECLARED") { }
                }
                """), source("types.spp", """
                Enum Status { OPEN:"OPEN","O2", CLOSED }
                Role Details { Status status; String label; }
                Entity Envelope { Details details; }
                """));
        var raw = findings.stream().filter(f -> f.code().equals("SPP_RAW_ENUM_CODE")).toList();
        assertEquals(List.of("\"O2\"", "\"OPEN\""), tokens(raw, "SPP_RAW_ENUM_CODE"));
        assertTrue(raw.get(0).message().contains("using Status.OPEN"));
        assertTrue(raw.get(1).message().contains("using OPEN"));
        assertEquals("logic.spp", raw.get(0).path());
        assertEquals(3, raw.get(0).token().getLine());
        assertEquals(9, raw.get(0).token().getCharPositionInLine());
    }

    @Test public void enumChecksHonorLocalTypesAndBlockScope() {
        var findings = run(source("types.spp", """
                Enum Status { READY }
                Entity Entry { Status label; }
                Entity TextEntry { String label; }
                atomic calculate classify() {
                    { Entry current = new Entry(); if (current.label == "READY") { } }
                    { TextEntry current = new TextEntry(); if (current.label == "READY") { } }
                }
                """));
        assertEquals(List.of("\"READY\""), tokens(findings, "SPP_RAW_ENUM_CODE"));
    }

    @Test public void duplicateCodesRequireDifferentEnumsAndDifferentMemberNames() {
        var findings = run(source("first.spp", "Enum First { OPEN:\"A\",\"B\", CLOSED }"),
                source("other.spp", "Enum Second { OPEN:\"A\", STARTED:\"B\", STOPPED:\"CLOSED\", UNIQUE:\"U\" }"));
        assertEquals(List.of("\"B\"", "\"CLOSED\""), tokens(findings, "SPP_DUPLICATE_ENUM_CODE"));
    }

    @Test public void repeatedLiteralsWarnOnceAtThirdUseAcrossFiles() {
        var findings = run(source("first.spp", """
                Enum Label { QUEUED:"pending" }
                const String UNUSED = "pending";
                Role Ledger { String label; int amount; }
                atomic calculate prepare(Ledger item) {
                    item.label = "pending"; item.label = "pending"; item.amount = -25;
                    item.amount = 25; item.amount = 1; item.amount = 1;
                }
                """), source("next.spp", """
                atomic calculate amend(Ledger item) {
                    item.label = "pending"; item.label = "pending"; item.amount = 25; item.amount = 1;
                    if (true) { } if (true) { } if (true) { }
                    item.label = " "; item.label = " "; item.label = " ";
                }
                """));
        assertEquals(List.of("\"pending\""), tokens(findings, "SPP_REPEATED_LITERAL"));
        var repeated = findings.stream().filter(f -> f.code().equals("SPP_REPEATED_LITERAL")).findFirst().orElseThrow();
        assertEquals("next.spp", repeated.path());
        assertEquals(3, repeated.token().getLine());
    }

    @Test public void countsSignedReadBindingsAndExpressionLiteralsTogether() {
        var findings = run(source("read.spp", """
                Entity Entry { int keyValue; }
                atomic calculate prepare(Entry item) { item.keyValue = -25; }
                Scenario selectEntries(Entry item) {
                    findEntry(item) by item.keyValue = -25;
                    findEntry(item) by item.keyValue = -25;
                }
                """));
        assertEquals(List.of("25"), tokens(findings, "SPP_REPEATED_LITERAL"));
        assertTrue(findings.stream().anyMatch(f -> f.message().startsWith("-25 appears")));
    }

    @Test public void participantAndLegacyNamesOnlyFlagDeclarations() {
        var findings = run(source("orders.spp", """
                Role Entry { String usdHead02; String usdHeading; int year2026; }
                Scenario review(Entry a, Entry g, Entry x1, Entry x2, Entry line10, Entry line11, Entry sha256, Entry orderEntry) {
                    if (orderEntry.usdHead02 == "usdHead02") { }
                }
                """));
        assertEquals(List.of("a", "g", "x1", "x2", "line10", "line11"), tokens(findings, "SPP_OPAQUE_PARTICIPANT"));
        assertEquals(List.of("usdHead02"), tokens(findings, "SPP_LEGACY_NAME"));
    }

    @Test public void productionTestWordsRespectIdentifierAndPathBoundaries() {
        String declarations = """
                Role SettlementFixture { String probeId; String latestQuote; String contestant; }
                const int TEST_LIMIT = 4;
                Scenario testSettlement(SettlementFixture request) { }
                """;
        assertEquals(List.of("SettlementFixture", "probeId", "TEST_LIMIT", "testSettlement"),
                tokens(run(source("file:///project/latest/src/main/settle.spp", declarations)), "SPP_TEST_NAME"));
        for (String path : List.of("file:///project/acceptance/settle.spp", "src\\test\\spp\\settle.spp"))
            assertTrue(tokens(run(source(path, declarations)), "SPP_TEST_NAME").isEmpty());
    }

    @Test public void unusedDeclarationsReportEachKindAtItsName() {
        var findings = run(source("unused.spp", """
                Role SpareRole;
                Entity SpareEntity { SpareEntity parent; }
                Enum SpareEnum { OPEN }
                const int SPARE = 12;
                atomic calculate spareService() { }
                Scenario published() { }
                """));
        assertEquals(List.of("SpareRole"), tokens(findings, "SPP_UNUSED_ROLE"));
        assertEquals(List.of("SpareEntity"), tokens(findings, "SPP_UNUSED_ENTITY"));
        assertEquals(List.of("SpareEnum"), tokens(findings, "SPP_UNUSED_ENUM"));
        assertEquals(List.of("SPARE"), tokens(findings, "SPP_UNUSED_CONSTANT"));
        assertEquals(List.of("spareService"), tokens(findings, "SPP_UNUSED_SERVICE"));
    }

    @Test public void usesAcrossSppAppAndPhyKeepDeclarationsLive() {
        var definitions = source("definitions.spp", """
                Role Request { Status state; }
                Entity Row { int amount; }
                Enum Status { OPEN }
                Enum Failure { BAD:"INVALID" }
                const int LIMIT = 32;
                const String LOCALIZED = "units";
                const int SELECTED = 17;
                atomic calculate prepare(Request request) { request.state = Status.OPEN; }
                atomic check validateRow(Row item) { require(item.amount < LIMIT, Failure.BAD); }
                atomic calculate invokePrepare(Request request) { prepare(request); }
                atomic query exposed(Row);
                """);
        var scenario = source("entry.spp", """
                Scenario review(Row item, Request request) {
                    invokePrepare(request); ValidateRow(item); findRow(item) by item.amount = SELECTED;
                }
                """);
        var app = source("deployment.app", "scope { local { exposed; } }");
        var phy = source("deployment.phy", "phy { constants \"en\" { LOCALIZED = \"units\"; } }");
        assertEquals(List.of(), run(definitions, scenario, app, phy));
    }

    @Test public void sameSpellingInStringsOrFieldDeclarationsIsNotAReference() {
        var findings = run(source("ledger.spp", """
                Role Payload { String UNUSED; String dormant; }
                const String UNUSED = "unused";
                atomic calculate dormant() { }
                Scenario show(Payload item) { if (item.UNUSED == "dormant") { } }
                """));
        assertEquals(List.of("UNUSED"), tokens(findings, "SPP_UNUSED_CONSTANT"));
        assertEquals(List.of("dormant"), tokens(findings, "SPP_UNUSED_SERVICE"));
    }

    @Test public void configurableLimitsWarnOnlyAboveTheirBoundary() {
        var input = source("flow.spp", """
                Role Entry { int amount; }
                Scenario atLimit(Entry input, Entry output) { serial { if (true) { } } }
                Scenario aboveLimit(Entry input, Entry output, Entry backup) { serial { if (true) { transaction { } } } }
                atomic calculate shortBody(Entry item) { item.amount = 2; item.amount = 3; }
                atomic calculate longBody(Entry item) { if (true) { item.amount = 4; item.amount = 5; } }
                atomic check longCheck(Entry item) { if (true) { if (true) { if (true) { } } } }
                """);
        withProperties(Map.of("maxParticipants", "2", "maxCalculateStatements", "2", "maxNesting", "2"), () -> {
            var findings = run(input);
            assertEquals(List.of("aboveLimit"), tokens(findings, "SPP_TOO_MANY_PARTICIPANTS"));
            assertEquals(List.of("longBody"), tokens(findings, "SPP_LONG_CALCULATE"));
            assertEquals(List.of("transaction", "if"), tokens(findings, "SPP_DEEP_NESTING"));
        });
        withProperties(Map.of("maxParticipants", "invalid", "maxCalculateStatements", "0", "maxNesting", "-1"), () -> {
            var findings = run(input);
            assertTrue(tokens(findings, "SPP_TOO_MANY_PARTICIPANTS").isEmpty());
            assertTrue(tokens(findings, "SPP_LONG_CALCULATE").isEmpty());
            assertTrue(tokens(findings, "SPP_DEEP_NESTING").isEmpty());
        });
    }

    @Test public void siblingsFormattingAndElseIfChainsDoNotIncreaseNesting() {
        var input = source("flow.spp", """
                Role Entry { int amount; }
                Scenario choose(Entry item) { if (true) { } else if (true) { } else { } }
                atomic calculate chooseAmount(Entry item) {
                    if (true) { item.amount = 2; } else if (true) { item.amount = 3; }
                    for (int index = 0; index < 4; index++) { }
                }
                """);
        withProperties(Map.of("maxNesting", "1"), () -> assertTrue(tokens(run(input), "SPP_DEEP_NESTING").isEmpty()));
    }

    private static void withProperties(Map<String, String> overrides, Runnable test) {
        var saved = new java.util.HashMap<String, String>();
        overrides.forEach((key, value) -> saved.put(key, System.setProperty("spp.lint." + key, value)));
        try {
            test.run();
        } finally {
            saved.forEach((key, value) -> {
                if (value == null) System.clearProperty("spp.lint." + key);
                else System.setProperty("spp.lint." + key, value);
            });
        }
    }
}
