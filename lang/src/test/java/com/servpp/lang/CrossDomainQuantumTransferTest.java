package com.servpp.lang;

import com.servpp.lang.models.DomainDeploymentValidator;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrossDomainQuantumTransferTest {
    private static final String FIELDS = "accountCode = account; currency = currency; claim = outstanding;";
    private static final String PLAY = "play ConsumerRole by provider.Position { " + FIELDS + " }";
    private static final String ALTERNATE =
            "play ConsumerRole by alternate.Reserve { accountCode = account; currency = currency; claim = outstanding; }";

    private SppProject project(String plays, String providerType, String providerTable, String mode) throws Exception {
        var project = new SppProject();
        for (String domain : new String[]{"provider", "alternate"}) {
            String type = domain.equals("provider") ? "Position" : "Reserve";
            parse(project, ".spp", """
                    domain %s;
                    Entity %s { String account; String currency; %s outstanding; }
                    """.formatted(domain, type, providerType));
            String keys = domain.equals("provider") ? "Position.account, Position.currency" : "Reserve.currency, Reserve.account";
            parse(project, ".app", """
                    domain %s;
                    tables entities { table %s(%s) { primarykey(%s); } }
                    """.formatted(domain, type.toUpperCase(java.util.Locale.ROOT), type, keys));
        }
        parse(project, ".spp", """
                domain consumer;
                Role Funds { String wallet; quantum decimal amount; }
                Role ConsumerRole { String accountCode; String currency; quantum decimal claim; }
                quantum update moveFunds(Funds consumerFunds, ConsumerRole played) {
                    consumerFunds.amount -> played.claim : 1.125;
                }
                """);
        parse(project, ".app", """
                domain consumer;
                application transfer { environment develop {
                    deploy consumer; deploy provider; deploy alternate;
                    transactions { mode: %s; resource: records; }
                } }
                tables roles { table FUNDS(Funds) { primarykey(Funds.wallet); } }
                scope { local { moveFunds; } }
                %s
                """.formatted(mode, plays));
        parse(project, ".phy", physical("consumer", "unifiedStore", "positions_value"));
        parse(project, ".phy", physical("provider", "unifiedStore", providerTable));
        parse(project, ".phy", physical("alternate", "unifiedStore", "positions_value"));
        return project;
    }

    private SppProject project(String plays, String providerType) throws Exception {
        return project(plays, providerType, "positions_value", "LOCAL");
    }

    private String physical(String domain, String store, String table) {
        return """
                domain %s;
                phy {
                    repository data {
                        adapter jdbcObjectRepository;
                        config default = true;
                        config appDdlTables = true;
                        config datasourceUrl = "jdbc:h2:mem:cross_domain";
                        config driverClassName = "org.h2.Driver";
                    }
                    backend database {
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:cross_domain";
                        config driverClassName = "org.h2.Driver";
                    }
                    quantumStore %s {
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:cross_domain";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "positions_journal";
                        config valueTable = "%s";
                    }
                }
                """.formatted(domain, store, table);
    }

    private void parse(SppProject project, String extension, String content) throws Exception {
        var source = Files.createTempFile("cross-domain-quantum", extension);
        try {
            Files.writeString(source, content);
            if (extension.equals(".spp")) new SppCompiler(source.toFile(), project).compile();
            else new AppCompiler(source.toFile(), project).compile();
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test public void resolvesAlternativesToCanonicalAttributesAndOrderedKeys() throws Exception {
        var project = project(PLAY + "\n" + ALTERNATE, "quantum decimal");
        assertEquals(2, project.getDomain("consumer").getRuleBlock().getRolePlays().size());
        assertNull(project.getDomain("consumer").getSppClass("Position"));
        var service = compile(project);
        assertEquals("LOCAL", service.getTransactionModeName());
        assertTrue(service.getQuantumServiceMetadataMethod().contains("\"Position.outstanding\""));
        assertTrue(service.getQuantumServiceMetadataMethod().contains("\"Reserve.outstanding\""));
        assertTrue(service.getQuantumConservationParticipantMethod().contains(
                "PlayEndpoint(\"Position.outstanding\", \"accountCode\", \"currency\")"));
        assertTrue(service.getQuantumConservationParticipantMethod().contains(
                "PlayEndpoint(\"Reserve.outstanding\", \"currency\", \"accountCode\")"));
    }

    @Test public void changesProviderThroughAppAlone() throws Exception {
        var first = compile(project(PLAY, "quantum decimal")).getQuantumServiceMetadataMethod();
        var second = compile(project(ALTERNATE, "quantum decimal")).getQuantumServiceMetadataMethod();
        assertTrue(first.contains("Position.outstanding"));
        assertFalse(first.contains("Reserve.outstanding"));
        assertTrue(second.contains("Reserve.outstanding"));
        assertFalse(second.contains("Position.outstanding"));
    }

    @Test public void validatesEachAlternativeAndBothDirectionsOfQuantumMismatch() throws Exception {
        rejects(project(PLAY, "decimal"), "quantum/non-quantum");
        rejects(project(PLAY + "\n" + ALTERNATE.replace("accountCode = account", "accountCode = outstanding")
                .replace("claim = outstanding", "claim = account"), "quantum decimal"), "quantum/non-quantum");
        rejects(project(PLAY, "quantum long"), "type mismatch");
        rejects(project(PLAY, "quantum decimal(12,2)"), "scale mismatch");
        rejects(project(PLAY.replace("currency = currency;", ""), "quantum decimal"), "unmapped provider primary-key field");
        rejects(project(PLAY.replace("provider.Position", "missing.Position"), "quantum decimal"), "unknown provider domain");
        rejects(project(PLAY.replace("provider.Position", "provider.Missing"), "quantum decimal"), "unknown provider type");
        rejects(project(PLAY.replace("claim = outstanding;", ""), "quantum decimal"), "unmapped consumer quantum field");
    }

    @Test public void rejectsDistributedStoresOrBackendWithoutLocalEnlistment() throws Exception {
        String pending = "distributed cross-domain quantum transfer not yet supported (APP TCC pending)";
        rejects(project(PLAY, "quantum decimal", "other_value", "LOCAL"), pending);
        rejects(project(PLAY, "quantum decimal", "positions_value", "SAGA"), pending);
        var project = project(PLAY, "quantum decimal");
        project.getDomain("consumer").getRuleBlock().getAppPhysicalBackends().get(0)
                .putConfiguration("supportedTransactions", "saga,quantum");
        rejects(project, pending);
    }

    @Test public void rejectsProviderSideQuantumTake() throws Exception {
        var project = project("", "quantum decimal");
        parse(project, ".spp", """
                domain provider;
                Reference ConsumerRole from consumer.ConsumerRole {
                    String accountCode; String currency; quantum decimal claim;
                }
                take Position as ConsumerRole {
                    String accountCode = account; mapsame; decimal claim = outstanding;
                }
                """);
        rejects(project, "cross-domain quantum roles require APP play");
    }

    private SppService compile(SppProject project) {
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(), errors.isEmpty());
        var domain = project.getDomain("consumer");
        errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        return (SppService) domain.getSppClass("MoveFunds");
    }

    private void rejects(SppProject project, String diagnostic) {
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(diagnostic)));
    }
}
