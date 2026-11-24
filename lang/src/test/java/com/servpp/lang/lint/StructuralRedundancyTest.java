package com.servpp.lang.lint;

import com.servpp.lang.antlr.SppLexer;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StructuralRedundancyTest {
    private List<RedundancyLint.Finding> analyze(String text) {
        var lexer = new SppLexer(CharStreams.fromString(text));
        var parser = new SppParser(new CommonTokenStream(lexer));
        var tree = parser.program();
        assertEquals(0, parser.getNumberOfSyntaxErrors());
        return RedundancyLint.analyze(List.of(new RedundancyLint.Source("test.spp", tree)));
    }

    private long count(List<RedundancyLint.Finding> findings, String code) {
        return findings.stream().filter(f -> code.equals(f.code())).count();
    }

    @Test public void duplicateServicesWarnForEqualOrSingleParticipantOrConstantDifference() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "atomic calculate first(E a) { a.name = \"one\"; } "
                + "atomic calculate second(E b) { b.name = \"one\"; } "
                + "atomic calculate third(E c) { c.name = \"two\"; }");
        assertTrue(count(findings, "SPP_REDUNDANT_DUPLICATE_SERVICE") >= 1);
    }

    @Test public void differentServiceBodiesDoNotWarnAsDuplicates() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "atomic calculate first(E a) { a.name = \"one\"; } "
                + "atomic calculate second(E b) { b.name = \"two\"; b.name = \"three\"; }");
        assertEquals(0, count(findings, "SPP_REDUNDANT_DUPLICATE_SERVICE"));
    }

    @Test public void differentFieldNamesAreNotAlphaRenamedAsDuplicateBodies() {
        var findings = analyze("domain D; Entity E { String name; String label; } "
                + "atomic calculate first(E a) { a.name = \"one\"; } "
                + "atomic calculate second(E b) { b.label = \"one\"; }");
        assertEquals(0, count(findings, "SPP_REDUNDANT_DUPLICATE_SERVICE"));
    }

    @Test public void oneLinePresenceCheckUsesIdentifyForBuiltinRejection() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "atomic check present(E item) { require(item, \"PARTICIPANT_NOT_IDENTIFIED\"); } "
                + "Scenario flow(E item) { present(item); }");
        assertEquals(1, count(findings, "SPP_REDUNDANT_PRESENCE_CHECK"));
        assertTrue(findings.stream().filter(f -> f.code().equals("SPP_REDUNDANT_PRESENCE_CHECK"))
                .anyMatch(f -> f.message().contains("identify(item)")));
    }

    @Test public void multiStatementCheckIsNotAOneLinePresenceCheck() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "atomic check valid(E item) { require(item, MISSING); require(item.name != null, INVALID); }");
        assertEquals(0, count(findings, "SPP_REDUNDANT_PRESENCE_CHECK"));
    }

    @Test public void oneLineCheckCalledMoreThanOnceDoesNotWarn() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "atomic check present(E item) { require(item, \"PARTICIPANT_NOT_IDENTIFIED\"); } "
                + "Scenario flow(E item) { present(item); present(item); }");
        assertEquals(0, count(findings, "SPP_REDUNDANT_PRESENCE_CHECK"));
    }

    @Test public void customPresenceCodeNeedsAnAdjacentCheckThatCanPreserveIt() {
        String defs = "domain shop; Role Item { int count; } "
                + "atomic check exists(Item i) { require(i, \"MISSING\"); } "
                + "atomic check quantity(Item it) { require(it.count > 0, \"COUNT\"); } ";
        assertEquals(0, count(analyze(defs + "Scenario serial run(Item i) { exists(i); }"), "SPP_REDUNDANT_PRESENCE_CHECK"));
        var warnings = analyze(defs + "Scenario serial run(Item i) { exists(i); quantity(i); }");
        assertTrue(warnings.stream().anyMatch(f -> f.code().equals("SPP_REDUNDANT_PRESENCE_CHECK")
                && f.message().contains("require(it, \"MISSING\")")));
    }

    @Test public void pairedParticipantsWarnWhenMirroredOperationsAreIdentical() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "atomic calculate move(E source, E target) { source.name = \"active\"; target.name = \"active\"; }");
        assertEquals(1, count(findings, "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
    }

    @Test public void pairedParticipantsDoNotWarnWhenOperationsDiffer() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "atomic calculate move(E source, E target) { source.name = \"active\"; target.name = \"closed\"; }");
        assertEquals(0, count(findings, "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
    }

    @Test public void pairedScenarioParticipantsWithMatchingCallsWarn() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "Scenario flow(E sourceAccount, E targetAccount) { inspect(sourceAccount); inspect(targetAccount); }");
        assertEquals(1, count(findings, "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
    }

    @Test public void pairedScenarioParticipantsWithDifferentCallsDoNotWarn() {
        var findings = analyze("domain D; Entity E { String name; } "
                + "Scenario flow(E sourceAccount, E targetAccount) { inspect(sourceAccount); archive(targetAccount); }");
        assertEquals(0, count(findings, "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
    }
}
