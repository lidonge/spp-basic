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

    @Test public void findsRepositorySelectorAtANonzeroPositionInASerialBranch() {
        var body = """
                domain ledger; Role Key { int id; } Role Row { int id; }
                take Key as Row { mapsame; }
                atomic calculate setup(Key k, Row r) { r.id = k.id; }
                atomic query readRows(Row[] results, Row filter);
                Scenario serial search(Key k, Row r, Row[] rows) {
                    if (true) { setup(k,r); readRows(rows,r); }
                }
                """;
        var schema = app("domain ledger; tables t { table R(Row) { primarykey(Row.id); } } "
                + "scope { local { repository(argument(1, Row.id), results) readRows; } }");
        assertTrue(analyze(spp(body), schema).stream().anyMatch(f -> f.code().endsWith("KEY_SETTER")
                && f.message().contains("readRows(rows,r) by k")));
        assertFalse(codes(analyze(spp(body.replace("if (true)", "parallel")), schema)).contains("SPP_REDUNDANT_KEY_SETTER"));
        assertFalse(codes(analyze(spp(body), app("domain ledger; tables t { table R(Row) { primarykey(Row.id); } }")))
                .contains("SPP_REDUNDANT_KEY_SETTER"));
    }

    @Test public void recognizesOnlyTheActualConstantRepositoryFilter() {
        var source = spp("""
                domain d; Role Item { int id; String kind; }
                atomic calculate prepare(Item filter) { filter.kind = "C"; }
                atomic query listItems(Item filter, Item[] results);
                Scenario serial run(Item f, Item[] rows) { prepare(f); listItems(f, rows); }
                """);
        String config = "domain d; tables t { table I(Item) { primarykey(Item.id); } } "
                + "scope { local { repository(argument(0, Item.kind), results) listItems; } }";
        assertTrue(analyze(source, app(config)).stream().anyMatch(f -> f.code().endsWith("KEY_SETTER")
                && f.message().contains("intercept key { kind = \"C\"; }")));
        assertFalse(codes(analyze(source, app(config.replace("Item.kind", "Item.id")))).contains("SPP_REDUNDANT_KEY_SETTER"));
    }

    @Test public void followsAnExplicitByOperandOnlyForItsCompleteMappedKey() {
        String text = """
                domain d; Role Order { int sku; } Role Input { int requested; } Role Selector { int code; int branch; }
                take Selector as Order { int sku = code; }
                atomic calculate prepare(Input i, Selector s) { s.code = i.requested; }
                Scenario serial lookup(Input i, Selector s, Order o) { prepare(i,s); findOrder(o) by s; }
                """;
        var table = app("domain d; tables roles { table ORDERS(Order){ primarykey(Order.sku); } }");
        assertTrue(analyze(spp(text), table).stream().anyMatch(f -> f.code().endsWith("KEY_SETTER")
                && f.message().contains("code=i.requested")));
        assertFalse(codes(analyze(spp(text.replace("s.code =", "s.branch =")), table)).contains("SPP_REDUNDANT_KEY_SETTER"));
    }

    @Test public void matchesTheWholeSecondaryPredicateAndKeepsNonKeyWrites() {
        String text = """
                domain d; Role Request { int unit; } Role Row { int sku; int unit; int fee; }
                atomic calculate choose(Request i, Row r) { r.unit = i.unit; }
                atomic query listRows(Row sel, Row[] rows);
                Scenario serial lookup(Request i, Row r, Row[] result) { choose(i,r); listRows(r,result); }
                """;
        var app = app("domain d; tables roles { table R(Row){ primarykey(Row.sku); } } "
                + "scope { local { repository(argument(0, Row.unit), rows) listRows; } }");
        assertTrue(analyze(spp(text), app).stream().anyMatch(f -> f.code().endsWith("KEY_SETTER")
                && f.message().contains("unit=i.unit")));
        assertFalse(codes(analyze(spp(text.replace("r.unit = i.unit;", "r.fee = i.unit;")), app)).contains("SPP_REDUNDANT_KEY_SETTER"));
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
