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

    @Test public void aParticipantDependentRejectionCannotMoveToAnotherFormalScope() {
        String text = "domain d; Enum E { MISSING } Role R { E failure; } "
                + "atomic check first(R item){require(item,item.failure);} atomic check second(R otherItem){require(true,E.MISSING);} "
                + "Scenario serial run(R r){first(r);second(r);}";
        assertEquals(0, count(analyze(text), "SPP_REDUNDANT_PRESENCE_CHECK"));
    }

    @Test public void sharesOnlyGuardsWithTheSameNamedRejection() {
        String defs = "domain d; Role R { int id; } Enum E { MISSING, CLOSED } "
                + "atomic check common(R x) { require(x,E.MISSING); } atomic check extra(R y) { require((y),E.MISSING); } "
                + "Scenario serial run(R a,R b){common(a);common(b);extra(b);}";
        assertTrue(analyze(defs).stream().anyMatch(f -> f.code().endsWith("PRESENCE_CHECK")
                && f.message().contains("common(b)")));
        assertEquals(0, count(analyze(defs.replace("(y),E.MISSING", "(y),E.CLOSED")), "SPP_REDUNDANT_PRESENCE_CHECK"));
    }

    @Test public void customPresenceChecksAcrossAReadKeepBothCodesAndOrder() {
        String text = "domain d; Role A {int id;} Role B {int num;} Enum E { FIRST, SECOND } "
                + "atomic check first(A a){require(a,E.FIRST);} atomic check second(B b){require(b,E.SECOND);} "
                + "Scenario serial run(A a,B b){transaction { first(a),breakTransaction; findB(b); second(b),breakTransaction; }}";
        assertTrue(analyze(text).stream().anyMatch(f -> f.code().endsWith("PRESENCE_CHECK") && f.message().contains("if (a)")
                && f.message().contains("E.FIRST then E.SECOND")));
        assertEquals(0, count(analyze(text.replace("findB(b);", "updateB(b);")), "SPP_REDUNDANT_PRESENCE_CHECK"));
    }

    @Test public void comparesNamedOutputsAndKeepsDifferentRightHandFieldsDistinct() {
        String text = "domain D; Role R { int level; int rank; } Role Result { int sourceLevel; int targetLevel; } "
                + "atomic calculate assess(R source,R target,Result out){ out.sourceLevel=source.level; out.targetLevel=target.level; }";
        assertEquals(1, count(analyze(text), "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
        assertEquals(0, count(analyze(text.replace("=target.level", "=target.rank")), "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
    }

    @Test public void localAndInternalReadsMayHaveJointConsumersButMustShareControlFlow() {
        String body = "domain d; Role R { int id; } Scenario serial flow(R localView,R internalView){ "
                + "if (true) {findR(localView); findR(internalView);} process(localView,internalView); }";
        assertEquals(1, count(analyze(body), "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
        assertEquals(0, count(analyze(body.replace("findR(localView); ", "findR(localView);} else { ")),
                "SPP_REDUNDANT_PAIRED_PARTICIPANTS"));
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
