package com.servpp.sdk.editor;

import org.eclipse.lsp4j.SymbolKind;
import org.junit.Test;

import static org.junit.Assert.*;

public class DocumentAnalysisTest {
    @Test public void extractsNamesOwnersAndImplicitParticipants() {
        var analysis = analyze("spp", "domain ledger; Role Facts { int amount; }\n"
                + "atomic calculate twice(Facts, Facts[] entries) { int total = 0; Facts.amount *= 2; }\n"
                + "Scenario serial preview(Facts) { twice; }");
        assertTrue(analysis.document().problems().isEmpty());
        var facts = find(analysis, "Facts", SymbolKind.Interface);
        var amount = find(analysis, "amount", SymbolKind.Field);
        assertSame(facts, amount.owner());
        var participant = find(analysis, "Facts", SymbolKind.Variable);
        assertTrue(participant.implicit());
        assertEquals("twice", participant.owner().name());
        assertEquals("Facts", participant.type());
        assertEquals("Facts[]", find(analysis, "entries", SymbolKind.Variable).type());
        assertEquals("int", find(analysis, "total", SymbolKind.Variable).type());
    }

    @Test public void implicitArrayNamesAndRoleFieldNamesFollowCompilerRules() {
        var analysis = analyze("spp", "domain ledger; Entity Account { String id; }\n"
                + "Role Facts { Account; Account[]; } atomic calculate run(Account[]) {}\n");
        assertEquals("Account", find(analysis, "account", SymbolKind.Field).type());
        assertEquals("Account[]", find(analysis, "accountArray", SymbolKind.Field).type());
        assertTrue(find(analysis, "AccountArray", SymbolKind.Variable).implicit());
    }

    @Test public void extractsEnumsConstantsAndConfigurationOwners() {
        var business = analyze("enum", "domain ledger; Enum State { Pending, Complete } const int MAX = 10;");
        assertEquals("State", find(business, "Pending", SymbolKind.EnumMember).owner().name());
        assertEquals("int", find(business, "MAX", SymbolKind.Constant).type());
        var configuration = analyze("rdb", "domain ledger; header Request { String trace; }\n"
                + "tables Facts { table records(Facts) { notnull String id; } }\n");
        assertTrue(configuration.document().problems().toString(), configuration.document().problems().isEmpty());
        assertEquals("Request", find(configuration, "trace", SymbolKind.Field).owner().name());
        assertEquals("records", find(configuration, "id", SymbolKind.Field).owner().name());
    }

    @Test public void incompleteInputDoesNotCreateSyntheticMissingNames() {
        var analysis = analyze("spp", "domain ledger; Role Facts { int ; }");
        assertFalse(analysis.document().problems().isEmpty());
        assertTrue(analysis.symbols().stream().noneMatch(symbol -> symbol.name().contains("missing")));
    }

    @Test public void toleratesEveryPrefixOfBusinessAndConfigurationDocuments() {
        for (var sample : java.util.Map.of(
                "spp", "domain ledger; Role Facts { int amount; } atomic calculate run(Facts, int value) { Facts.amount += value; }",
                "app", "domain ledger; header Request { String trace; } services { run { Facts.amount += 1; } }",
                "rdb", "domain ledger; tables Facts { table records(Facts) { notnull String id; } }").entrySet()) {
            for (int end = 0; end <= sample.getValue().length(); end++) {
                try {
                    var document = new SourceDocument("file:///prefix." + sample.getKey(), 1, sample.getValue().substring(0, end));
                    var project = new ProjectAnalysis(java.util.List.of(document));
                    project.symbols().forEach(symbol -> {
                        symbol.domain();
                        symbol.range();
                    });
                    if (end > 0) project.resolve(document.uri(), document.position(end - 1));
                } catch (RuntimeException failure) {
                    throw new AssertionError(sample.getKey() + " prefix " + end, failure);
                }
            }
        }
    }

    @Test public void reportsExcessivelyDeepSyntaxRatherThanSilentlyReturningPartialSymbols() {
        String expression = "1 + ".repeat(400) + "1";
        var document = new SourceDocument("file:///deep.spp", 1,
                "domain ledger; Role Facts { int value; } atomic calculate run(Facts) { Facts.value = " + expression + "; }");
        assertThrows(IllegalArgumentException.class, () -> new DocumentAnalysis(document));
    }

    @Test public void shippedSnippetDefaultsAreValidForTheirDeclaredDialects() throws Exception {
        var snippets = new com.fasterxml.jackson.databind.ObjectMapper().readTree(java.nio.file.Files.readString(
                java.nio.file.Path.of("../vscode/snippets/spp.json")));
        var entries = snippets.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            var lines = new java.util.ArrayList<String>();
            entry.getValue().path("body").forEach(line -> lines.add(line.asText()));
            String template = String.join("\n", lines);
            var matcher = java.util.regex.Pattern.compile("\\$\\{\\d+:([^}]*)}|\\$\\d+").matcher(template);
            StringBuilder expanded = new StringBuilder();
            while (matcher.find()) matcher.appendReplacement(expanded,
                    java.util.regex.Matcher.quoteReplacement(matcher.group(1) == null ? "" : matcher.group(1)));
            matcher.appendTail(expanded);
            String text = (entry.getKey().equals("Domain") ? "" : "domain example;\n") + expanded + "\n";
            String extension = entry.getKey().equals("Local service scope") ? "app" : "spp";
            var document = new SourceDocument("file:///snippet." + extension, 1, text);
            assertTrue(entry.getKey() + ": " + document.problems(), document.problems().isEmpty());
        }
    }

    private static DocumentAnalysis analyze(String extension, String text) {
        return new DocumentAnalysis(new SourceDocument("file:///test." + extension, 1, text));
    }

    private static SourceSymbol find(DocumentAnalysis analysis, String name, SymbolKind kind) {
        return analysis.symbols().stream().filter(symbol -> symbol.name().equals(name) && symbol.kind() == kind)
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + kind + " " + name));
    }
}
