package com.servpp.lang;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppScenario;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ReadonlyScenarioTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private SppDomain compile(String declarations) throws IOException {
        var source = temporary.newFile();
        Files.writeString(source.toPath(), """
                domain ledger;
                Entity Entry { int amount; }
                atomic query read(Entry entry);
                atomic check enough(Entry entry) { require(entry.amount > 0, "EMPTY"); }
                atomic calculate project(Entry entry) { entry.amount += 1; }
                atomic insert create(Entry entry);
                atomic update save(Entry entry);
                atomic delete remove(Entry entry);
                """ + declarations);
        var project = new SppProject();
        new SppCompiler(source, project).compile();
        return project.getDomain("ledger");
    }

    @Test public void acceptsReadsChecksAndCalculationsBeforeAndAfterOtherDeclarations() throws Exception {
        var domain = compile("""
                readonly Scenario serial preview(Entry entry) {
                    read(entry);
                    enough(entry) { project(entry); };
                }
                Scenario serial ordinary(Entry entry) { read(entry); }
                readonly Scenario parallel another(Entry entry) { read(entry); }
                """);
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        assertTrue(((SppScenario) domain.getSppClass("Preview")).isReadonly());
        assertFalse(((SppScenario) domain.getSppClass("Ordinary")).isReadonly());
        assertTrue(((SppScenario) domain.getSppClass("Another")).isReadonly());
    }

    @Test public void rejectsDeclaredAndBuiltInWrites() throws Exception {
        rejects("create(entry);", "insert service create");
        rejects("save(entry);", "update service save");
        rejects("remove(entry);", "delete service remove");
        rejects("insertEntry(entry);", "insert service insertEntry");
        rejects("updateEntry(entry);", "update service updateEntry");
        rejects("deleteEntry(entry);", "delete service deleteEntry");
    }

    @Test public void rejectsAQuantumServiceContainingATransfer() throws Exception {
        var domain = compile("""
                Role Funds { quantum int debit; quantum int credit; }
                quantum update move(Funds funds) {
                    funds.debit -> funds.credit : 1;
                }
                readonly Scenario serial preview(Funds funds) { move(funds); }
                """);
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.contains("readonly Scenario preview cannot call quantum service move."));
    }

    @Test public void rejectsTransactionsAndWritesInGuardedAndConditionalBranches() throws Exception {
        rejects("transaction {}", "transaction block");
        rejects("enough(entry) { transaction REQUIRES_NEW {} };", "transaction block");
        rejects("enough(entry) { parallel { remove(entry); } };", "delete service remove");
        rejects("if (entry.amount > 0) {} else if (alternate.amount > 0) { save(alternate); }",
                "update service save");
    }

    private void rejects(String body, String diagnostic) throws IOException {
        var domain = compile("readonly Scenario serial preview(Entry entry, Entry alternate) {" + body + "}");
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error ->
                error.startsWith("readonly Scenario preview ") && error.contains(diagnostic)));
    }

    @Test public void rejectsUnknownModifiersAndUnsupportedSyntax() {
        String valid = "Scenario serial ordinary(Entry entry) { read(entry); }\n";
        for (String invalid : new String[]{
                "unknown Scenario serial preview(Entry entry) {}",
                "readonly unknown Scenario preview(Entry entry) {}",
                "readonly Scenario unknown preview(Entry entry) {}",
                "readonly Scenario preview(Entry entry Entry alternate) {}",
                "readonly Scenario preview(Entry entry, Entry alternate) { entry.balance -> alternate.balance : 1; }",
                "readonly Scenario preview(Entry entry) { transaction UNKNOWN {} }",
                "readonly Scenario preview(Entry entry) { read(entry),abortTransaction; }"
        }) {
            var error = assertThrows(invalid, IOException.class, () -> compile(valid + invalid));
            assertTrue(error.getMessage(), error.getMessage().contains("syntax error"));
        }
        var error = assertThrows(IOException.class, () -> compile(valid + "@"));
        assertTrue(error.getMessage(), error.getMessage().contains("lexical error"));
    }

    @Test public void rejectsCallsAndArgumentsThatCannotBeGenerated() throws Exception {
        var domain = compile("""
                Scenario serial unknownService(Entry entry) { missing(entry); }
                Scenario serial nonService(Entry entry) { Entry(entry); }
                Scenario serial redundantArgument(Entry entry) { read(entry, entry); }
                Scenario serial duplicate(Entry entry) {}
                readonly Scenario serial duplicate(Entry entry) {}
                """);
        var errors = domain.validateAppDeclarations();
        for (String diagnostic : new String[]{"undefined atomic service", "not an atomic service",
                "Duplicate parameter entry", "Duplicate service declaration: duplicate"}) {
            assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(diagnostic)));
        }
    }
}
