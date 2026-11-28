package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Basic package D: roles with several sources, role-to-role finds, quantum through roles. */
public class RoleGapsDTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final String PHY = """
            phy {
                repository data {
                    adapter jdbcObjectRepository;
                    config default = true;
                    config appDdlTables = true;
                    config datasourceUrl = "jdbc:h2:mem:%s";
                    config driverClassName = "org.h2.Driver";
                }
                backend database {
                    adapter localJdbc;
                    config datasourceUrl = "jdbc:h2:mem:%s";
                    config driverClassName = "org.h2.Driver";
                }
            }
            """;

    private static final String QUANTUM_PHY = """
            phy {
                repository records {
                    adapter jdbcObjectRepository;
                    config default = true;
                    config appDdlTables = true;
                    config datasourceUrl = "jdbc:h2:mem:%s";
                    config driverClassName = "org.h2.Driver";
                }
                backend database {
                    adapter localJdbc;
                    quantum movement;
                    config datasourceUrl = "jdbc:h2:mem:%s";
                    config driverClassName = "org.h2.Driver";
                }
                quantumStore values {
                    adapter jdbcQuantum;
                    config datasourceUrl = "jdbc:h2:mem:%s";
                    config driverClassName = "org.h2.Driver";
                    config journalTable = "d_journal";
                    config valueTable = "d_value";
                    config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                }
            }
            """;
    private String schema;

    private Path generate(String spp, String app, String name) throws Exception {
        var root = temporary.newFolder().toPath();
        var source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("d.spp"), spp);
        if (app != null) {
            Files.writeString(source.resolve("d.app"), app);
            Files.writeString(source.resolve("d.phy"), "domain d;\n"
                    + (app.contains("quantumStore") ? QUANTUM_PHY.formatted(name, name, name) : PHY.formatted(name, name)));
        }
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(), "generated");
        schema = Files.readString(root.resolve("yaml/d.spp.yaml"));
        return root.resolve("java");
    }

    /** The generated schema of a participant type, as the text of its YAML block. */
    private String schema(String type) {
        int start = schema.indexOf("\n    " + type + ":\n");
        assertTrue(type + " is not generated: " + schema, start >= 0);
        int end = schema.indexOf("\n    ", start + 5 + type.length());
        while (end >= 0 && schema.startsWith("      ", end + 1)) end = schema.indexOf("\n    ", end + 1);
        return schema.substring(start, end < 0 ? schema.length() : end);
    }

    @Test public void d1FieldlessRoleWithSeveralTakesCarriesItsFirstSourcesFacts() throws Exception {
        var java = generate("""
                domain d;
                Entity Account { String accountNumber; String owner; }
                Contract AccountRequest { String accountNumber; String owner; }
                Role Subject short subject;
                take Account as Subject;
                take AccountRequest as Subject;
                atomic calculate touch(Account) { account.owner = "x"; }
                atomic calculate describe(Subject) { subject.owner = "y"; }
                Scenario run(Account, Subject) { touch(subject); describe(account); }
                """, null, "d1a");
        String model = schema("Subject");
        assertTrue(model, model.contains("owner") && model.contains("accountNumber"));
    }

    @Test public void d1ExtraTakeDoesNotAddItsOwnFacts() throws Exception {
        var java = generate("""
                domain d;
                Entity Account { String accountNumber; String owner; }
                Contract AccountRequest { String accountNumber; String note; }
                Role Subject short subject;
                take Account as Subject;
                take AccountRequest as Subject { takeall; }
                atomic calculate touch(Account) { account.owner = "x"; }
                Scenario run(Subject) { touch(subject); }
                """, null, "d1b");
        String model = schema("Subject");
        assertTrue(model, model.contains("owner"));
        assertFalse(model, model.contains("note"));
    }

    @Test public void d1PlayWithPlainTakeKeepsThePlayersFacts() throws Exception {
        var java = generate("""
                domain d;
                Entity Account { String accountNumber; String owner; }
                Contract AccountRequest { String accountNumber; String requestNote; }
                Role Subject short subject;
                take AccountRequest as Subject;
                atomic calculate touch(Account) { account.owner = "x"; }
                Scenario run(AccountRequest, Subject) { touch(subject); }
                """, """
                domain d;
                play Subject by d.Account { accountNumber = accountNumber; }
                tables entities { table ACCOUNTS(Account) { primarykey(Account.accountNumber); } }
                scope { local {} }
                """, "d1c");
        String model = schema("Subject");
        assertTrue(model, model.contains("owner"));
        assertFalse(model, model.contains("requestNote"));
    }

    @Test public void d1QuantumOfANonPlayerSourceIsNotPulledIntoAPlayRole() throws Exception {
        generate("""
                domain d;
                Entity Wallet { String account; quantum decimal funds; }
                Entity Pending { String account; quantum decimal pendingFunds; }
                Role Holder short holder;
                Role Payee short payee;
                take Pending as Holder { takeall; }
                quantum update pay(Holder, Payee) { holder.funds -> payee.funds : 2; }
                """, """
                domain d;
                play Holder by d.Wallet { account = account; funds = funds; }
                play Payee by d.Wallet { account = account; funds = funds; }
                tables entities { table WALLETS(Wallet) { primarykey(Wallet.account); } table PENDING(Pending) { primarykey(Pending.account); } }
                quantumStore values { attribute Wallet.funds; attribute Pending.pendingFunds; }
                scope { local { quantum resource(movement, Wallet.funds) pay; } }
                """, "d1d");
        String model = schema("Holder");
        assertTrue(model, model.contains("funds"));
        assertFalse(model, model.contains("pendingFunds"));
    }

    @Test public void d2FindARoleByAnotherRoleThroughItsPlayerAndTakeMappings() throws Exception {
        var java = generate("""
                domain d;
                Entity Relation { String branch; String relatedBranch; }
                Entity Office { String officeCode; String officeName; }
                Role RegionLink short regionLink;
                Role RegionOffice short region;
                take Relation as RegionLink;
                take RegionLink as RegionOffice { String officeCode = relatedBranch; }
                Scenario locate(RegionLink, RegionOffice) { findRegionOffice by regionLink; }
                """, """
                domain d;
                play RegionOffice by d.Office { officeCode = officeCode; }
                tables entities {
                    table RELATIONS(Relation) { primarykey(Relation.branch); }
                    table OFFICES(Office) { primarykey(Office.officeCode); }
                }
                scope { local {} }
                """, "d2a");
        String find = Files.readString(java.resolve("generated/d/service/SimpleLocate.java"));
        assertTrue(find, find.contains("getRelatedBranch"));
        String model = schema("RegionOffice");
        assertTrue(model, model.contains("officeName"));
        assertFalse(model, model.contains("branch"));
    }

    @Test public void d2FindATakeRoleByAnotherRoleOfDifferentPlayers() throws Exception {
        var java = generate("""
                domain d;
                Entity Relation { String branch; String relatedBranch; }
                Entity Office { String officeCode; String officeName; }
                Role RegionLink short regionLink;
                Role RegionOffice short region;
                take Relation as RegionLink;
                take Office as RegionOffice;
                take RegionLink as RegionOffice { String officeCode = relatedBranch; }
                Scenario locate(RegionLink, RegionOffice) { findRegionOffice by regionLink; }
                """, """
                domain d;
                tables entities {
                    table RELATIONS(Relation) { primarykey(Relation.branch); }
                    table OFFICES(Office) { primarykey(Office.officeCode); }
                }
                scope { local {} }
                """, "d2b");
        String find = Files.readString(java.resolve("generated/d/service/SimpleLocate.java"));
        assertTrue(find, find.contains("getRelatedBranch"));
    }

    @Test public void d2FindAnEntityByARoleOverTheEntityThatItIsTakenFrom() throws Exception {
        var java = generate("""
                domain d;
                Entity Relation { String branch; String relatedBranch; }
                Entity Office { String officeCode; String officeName; }
                take Relation as Office { String officeCode = relatedBranch; }
                Role RegionLink short regionLink;
                take Relation as RegionLink { String linkedBranch = relatedBranch; }
                Scenario locate(RegionLink, Office) { findOffice by regionLink; }
                """, """
                domain d;
                tables entities {
                    table RELATIONS(Relation) { primarykey(Relation.branch); }
                    table OFFICES(Office) { primarykey(Office.officeCode); }
                }
                scope { local {} }
                """, "d2c");
        String scenario = Files.readString(java.resolve("generated/d/service/SimpleLocate.java"));
        assertTrue(scenario, scenario.contains("regionLink.getLinkedBranch()"));
    }

    @Test public void d2FindARoleByASiblingRoleOfTheRoleThatTheTargetIsTakenFrom() throws Exception {
        var java = generate("""
                domain d;
                Entity Relation { String branch; String relatedBranch; }
                Entity Office { String officeCode; String officeName; }
                Role LinkRecord short linkRecord;
                take Relation as LinkRecord;
                take LinkRecord as Office { String officeCode = relatedBranch; }
                Role RegionLink short regionLink;
                take Relation as RegionLink { String linkedBranch = relatedBranch; }
                Role RegionOffice short region;
                take Office as RegionOffice;
                Scenario locate(RegionLink, RegionOffice) { findRegionOffice by regionLink; }
                """, """
                domain d;
                tables entities {
                    table RELATIONS(Relation) { primarykey(Relation.branch); }
                    table OFFICES(Office) { primarykey(Office.officeCode); }
                }
                scope { local {} }
                """, "d2d");
        String scenario = Files.readString(java.resolve("generated/d/service/SimpleLocate.java"));
        assertTrue(scenario, scenario.contains("regionLink.getLinkedBranch()"));
    }
}
