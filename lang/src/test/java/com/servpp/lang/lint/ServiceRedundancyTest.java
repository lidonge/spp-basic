package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppLexer;
import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppLexer;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ServiceRedundancyTest {
    @Test public void recommendsTakeForPureParticipantFieldCopy() {
        var source = spp("domain ledger; Entity Source { String name; int amount; } Role Target { String name; int amount; } "
                + "atomic calculate project(Source source, Target target) { target.name = source.name; target.amount = source.amount; }");
        var findings = analyze(source);
        var finding = findings.stream().filter(item -> item.code().equals("SPP_REDUNDANT_FIELD_COPY")).findFirst().orElseThrow();
        assertTrue(finding.message(), finding.message().contains("take Source as Target { mapsame; }"));
    }

    @Test public void keepsCalculationsThatTransformValuesOrContainOtherLogic() {
        var transformed = spp("domain ledger; Entity Source { String name; } Role Target { String name; } "
                + "atomic calculate project(Source source, Target target) { target.name = source.name + \"!\"; }");
        var branching = spp("domain ledger; Entity Source { String name; } Role Target { String name; } "
                + "atomic calculate project(Source source, Target target) { if (source.name != null) { target.name = source.name; } }");
        assertFalse(codes(analyze(transformed)).contains("SPP_REDUNDANT_FIELD_COPY"));
        assertFalse(codes(analyze(branching)).contains("SPP_REDUNDANT_FIELD_COPY"));
    }

    @Test public void recommendsDirectFindBindingForPrimaryKeySetter() {
        String spp = "domain ledger; const String KEY = \"A\"; Role Lookup { String id; String label; } "
                + "atomic query findLookup(Lookup selection); "
                + "atomic calculate selectLookup(Lookup selection) { selection.id = KEY; } "
                + "Scenario serial fetch(Lookup selection) { selectLookup(selection); findLookup(selection); }";
        var findings = analyze(spp(spp), app("domain ledger; tables records { table LOOKUPS(Lookup) { primarykey(Lookup.id); } }"));
        var finding = findings.stream().filter(item -> item.code().equals("SPP_REDUNDANT_KEY_SETTER")).findFirst().orElseThrow(() -> new AssertionError(findings.toString()));
        assertTrue(finding.message(), finding.message().contains("findLookup(selection) by selection.id = KEY"));
    }

    @Test public void keepsNonKeySettersAndSettersSeparatedFromTheFind() {
        String spp = "domain ledger; const String KEY = \"A\"; Role Lookup { String id; String label; } "
                + "atomic query findLookup(Lookup selection); "
                + "atomic calculate selectLookup(Lookup selection) { selection.label = KEY; } "
                + "atomic check validate(Lookup selection) { check(selection.id != null); } "
                + "Scenario serial fetch(Lookup selection) { selectLookup(selection); validate(selection); findLookup(selection); }";
        var findings = analyze(spp(spp), app("domain ledger; tables records { table LOOKUPS(Lookup) { primarykey(Lookup.id); } }"));
        assertFalse(codes(findings).contains("SPP_REDUNDANT_KEY_SETTER"));
    }

    @Test public void reportsExplicitDeclarationThatMatchesImplicitCrud() {
        var explicit = spp("domain shop; Role Buyer { String id; } atomic query findBuyer(Buyer); ");
        var findings = analyze(explicit);
        assertTrue(findings.toString(), codes(findings).contains("SPP_REDUNDANT_IMPLICIT_CRUD"));
    }

    @Test public void keepsCustomQueryWithAdditionalInputAndRealBody() {
        var customSignature = spp("domain shop; Role Buyer { String id; } atomic query findBuyer(Buyer buyer, int page); ");
        var customBody = spp("domain shop; Role Buyer { String id; } atomic query findBuyer(Buyer buyer) { check(buyer.id != null); }");
        assertFalse(codes(analyze(customSignature)).contains("SPP_REDUNDANT_IMPLICIT_CRUD"));
        assertFalse(codes(analyze(customBody)).contains("SPP_REDUNDANT_IMPLICIT_CRUD"));
    }

    @Test public void implicitFindCanConsumeMappedKeyAndConstant() {
        var source = spp("""
                domain ledger;
                Role Input { String id; }
                Role Record { String id; int branch; }
                take Input as Record { mapsame; }
                atomic calculate prepare(Input i, Record r) { r.id = i.id; r.branch = 7; }
                Scenario serial fetch(Input input, Record record) { prepare(input, record); findRecord(record); }
                """);
        var schema = app("domain ledger; tables roles { table RECORDS(Record) { primarykey(Record.id, Record.branch); } }");
        assertTrue(analyze(source, schema).stream().anyMatch(f -> f.code().equals("SPP_REDUNDANT_KEY_SETTER")
                && f.message().contains("findRecord(record) by input, record.branch = 7;")));
    }

    @Test public void suggestsTheMissingTakeForARenamedKey() {
        var source = spp("""
                domain ledger;
                Role Input { String code; }
                Role Record { String id; }
                atomic calculate prepare(Input i, Record r) { r.id = i.code; }
                Scenario serial fetch(Input input, Record record) { prepare(input, record); findRecord(record); }
                """);
        var schema = app("domain ledger; tables roles { table RECORDS(Record) { primarykey(Record.id); } }");
        assertTrue(analyze(source, schema).stream().anyMatch(f -> f.code().equals("SPP_REDUNDANT_KEY_SETTER")
                && f.message().contains("take Input as Record { String id = code; }")
                && f.message().contains("findRecord(record) by input;")));
    }

    private static Set<String> codes(List<RedundancyLint.Finding> findings) {
        return findings.stream().map(RedundancyLint.Finding::code).collect(Collectors.toSet());
    }

    private static List<RedundancyLint.Finding> analyze(RedundancyLint.Source... sources) {
        return RedundancyLint.analyze(List.of(sources));
    }

    private static RedundancyLint.Source spp(String text) {
        var parser = new SppParser(new CommonTokenStream(new SppLexer(CharStreams.fromString(text))));
        var program = parser.program();
        assertEquals(0, parser.getNumberOfSyntaxErrors());
        return new RedundancyLint.Source("test.spp", program);
    }

    private static RedundancyLint.Source app(String text) {
        var parser = new AppParser(new CommonTokenStream(new AppLexer(CharStreams.fromString(text))));
        var program = parser.program();
        assertEquals(0, parser.getNumberOfSyntaxErrors());
        return new RedundancyLint.Source("test.app", program);
    }
}
