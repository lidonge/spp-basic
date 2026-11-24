package com.servpp.lang.lint;

import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.antlr.AppLexer;
import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppLexer;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DataAccessLintTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final String DOMAIN = "domain ledger;\n";
    private static final String DECLARATIONS = """
            Entity Entry { int postingNumber; }
            Enum PostingError { INVALID_NUMBER }
            atomic query lookup(Entry row);
            atomic check present(Entry row) { require(row.postingNumber > 0, PostingError.INVALID_NUMBER); }
            atomic calculate project(Entry row) { row.postingNumber += 1; }
            atomic update save(Entry row);
            """;

    @Test public void anchorsReadonlyInsertionAndAcceptsEditedSource() throws IOException {
        String entry = DOMAIN + """
                  /* inquiry 📖 */ Scenario /* kept */ serial preview(Entry row) {
                      lookup(row);
                      present(row) { project(row); };
                  }
                """;
        var declarations = spp("services.spp", DOMAIN + DECLARATIONS);
        var source = spp("entry.spp", entry);
        var warnings = findings(source, declarations);
        assertEquals(1, warnings.size());
        var finding = warnings.get(0);
        assertEquals("SPP_MISSING_READONLY", finding.code());
        assertEquals("entry.spp", finding.path());
        var keyword = finding.token();
        assertEquals("Scenario", keyword.getText());
        int offset = keyword.getInputStream().getText(Interval.of(0, keyword.getStartIndex() - 1)).length();
        assertEquals(entry.indexOf("Scenario"), offset);
        String edited = entry.substring(0, offset) + "readonly " + entry.substring(offset);
        assertTrue(findings(spp("entry.spp", edited), declarations).isEmpty());
        var project = new SppProject();
        var servicesFile = temporary.newFile("services.spp");
        var entryFile = temporary.newFile("entry.spp");
        Files.writeString(servicesFile.toPath(), DOMAIN + DECLARATIONS);
        Files.writeString(entryFile.toPath(), edited);
        new SppCompiler(servicesFile, project).compile();
        new SppCompiler(entryFile, project).compile();
        assertEquals(List.of(), project.getDomain("ledger").validateAppDeclarations());
    }

    @Test public void recognizesBuiltinReadsAndHonorsDeclaredKind() {
        var positive = spp("read.spp", DOMAIN + DECLARATIONS
                + "Scenario preview(Entry e) { findEntry(e); }");
        assertEquals(1, forCode("SPP_MISSING_READONLY", positive).size());
        var shadowed = spp("shadowed.spp", DOMAIN + DECLARATIONS
                + "atomic delete findEntry(Entry e); Scenario preview(Entry e) { findEntry(e); }");
        assertTrue(forCode("SPP_MISSING_READONLY", shadowed).isEmpty());
    }

    @Test public void skipsEmptyAlreadyReadonlyOrUnprovenScenarios() {
        for (String body : List.of("{}", "{ missing; }", "{ serial { findUndeclared; } }",
                "{ findEntry(row); transaction { lookup(row); } }",
                "{ present(row) { if (row.postingNumber > 0) { deleteEntry(row); } }; }",
                "{ if (row.postingNumber > 0) { lookup(row); } else { save(row); } }",
                "{ parallel { lookup(row); insertEntry(row); } }",
                "{ quantumCheck(row); }", "{ preview(row); }")) {
            var source = spp("entry.spp", DOMAIN + DECLARATIONS
                    + "quantum check quantumCheck(Entry row); Scenario preview(Entry row) " + body);
            assertTrue(body, forCode("SPP_MISSING_READONLY", source).isEmpty());
        }
    }

    @Test public void suppressesReadonlyForUnresolvedProvidersAndLockingReads() {
        var entry = spp("entry.spp", DOMAIN + DECLARATIONS + "Scenario preview(Entry row) { lookup(row); }");
        for (String config : List.of("bind lookup to alternate.inspect;", "scope { remote { lookup; } }",
                "scope { local { quantum lookup; } }",
                "scope { local { repository(argument(0, Entry), Entry.postingNumber) for update lookup; } }")) {
            assertTrue(config, forCode("SPP_MISSING_READONLY", entry, app(config)).isEmpty());
        }
    }

    @Test public void reportsRepeatedLookupAtSecondCallIncludingInsideAReadOnlyScenario() {
        var source = spp("entry.spp", DOMAIN + DECLARATIONS + """
                readonly Scenario serial preview(Entry row) {
                    if (row.postingNumber > 0) {
                        findEntry(row) by row.postingNumber = 7;
                        findEntry(row) by row.postingNumber = 7;
                    }
                }
                """);
        var warnings = forCode("SPP_REPEATED_QUERY", source);
        assertEquals(1, warnings.size());
        assertEquals("findEntry", warnings.get(0).token().getText());
        assertEquals(11, warnings.get(0).token().getLine());
    }

    @Test public void distinguishesKeysParticipantsAndControlFlow() {
        for (String body : List.of(
                "lookup(row); lookup(alternate);",
                "findEntry(row) by row.postingNumber = 1; findEntry(row) by row.postingNumber = 2;",
                "lookup(row); project(row); lookup(row);",
                "if (row.postingNumber > 0) { lookup(row); } else { lookup(row); }",
                "parallel { lookup(row); lookup(row); }",
                "lookup(row); transaction { lookup(row); }")) {
            var source = spp("entry.spp", DOMAIN + DECLARATIONS + "Scenario serial preview(Entry row, Entry alternate) {"
                    + body + "}");
            assertTrue(body, forCode("SPP_REPEATED_QUERY", source).isEmpty());
        }
    }

    @Test public void reportsAdjacentPersistentWritesOutsideTransaction() {
        var source = spp("entry.spp", DOMAIN + DECLARATIONS + """
                Scenario post(Entry row, Entry alternate) {
                    serial {
                        save(row);
                        insertEntry(alternate);
                    }
                }
                """);
        var warnings = forCode("SPP_WRITES_OUTSIDE_TRANSACTION", source);
        assertEquals(1, warnings.size());
        assertEquals("insertEntry", warnings.get(0).token().getText());
        assertTrue(warnings.get(0).message().contains("save"));
    }

    @Test public void respectsTransactionAndAlternativeBranchBoundaries() {
        for (String body : List.of("transaction { serial { save(row); insertEntry(alternate); } }",
                "save(row);", "parallel { save(row); save(alternate); }",
                "if (row.postingNumber > 0) { save(row); } else { save(alternate); }",
                "transaction { save(row); } transaction REQUIRES_NEW { save(alternate); }",
                "save(row); unknown(alternate);")) {
            var source = spp("entry.spp", DOMAIN + DECLARATIONS + "Scenario serial post(Entry row, Entry alternate) {"
                    + body + "}");
            assertTrue(body, forCode("SPP_WRITES_OUTSIDE_TRANSACTION", source).isEmpty());
        }
    }

    private static RedundancyLint.Source spp(String path, String text) {
        var parser = new SppParser(new CommonTokenStream(new SppLexer(CharStreams.fromString(text))));
        var tree = parser.program();
        assertEquals(text, 0, parser.getNumberOfSyntaxErrors());
        return new RedundancyLint.Source(path, tree);
    }

    private static RedundancyLint.Source app(String rules) {
        var parser = new AppParser(new CommonTokenStream(new AppLexer(CharStreams.fromString(DOMAIN + rules))));
        var tree = parser.program();
        assertEquals(rules, 0, parser.getNumberOfSyntaxErrors());
        return new RedundancyLint.Source("ledger.app", tree);
    }

    private static List<RedundancyLint.Finding> findings(RedundancyLint.Source... sources) {
        var findings = new ArrayList<RedundancyLint.Finding>();
        DataAccessLint.check(List.of(sources), (source, token, code, hint) ->
                findings.add(new RedundancyLint.Finding(source.path(), token, code, hint)));
        return findings;
    }

    private static List<RedundancyLint.Finding> forCode(String code, RedundancyLint.Source... sources) {
        return findings(sources).stream().filter(f -> f.code().equals(code)).toList();
    }
}
