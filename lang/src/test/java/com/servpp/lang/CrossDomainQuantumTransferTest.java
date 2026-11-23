package com.servpp.lang;

import com.servpp.lang.models.DomainDeploymentValidator;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrossDomainQuantumTransferTest {
    private SppProject project(String providerTable, String mapping, String positionModifier) throws Exception {
        return project(providerTable, mapping, positionModifier, false);
    }

    private SppProject project(String providerTable, String mapping, String positionModifier, boolean providerSide) throws Exception {
        var project = new SppProject();
        String take = "take Position as ProviderPlayedRole { String accountCode = account; mapsame; " + mapping + " }";
        parse(project, ".spp", """
                domain provider;
                Entity Position { String account; String currency; %s decimal outstanding; }
                """.formatted(positionModifier) + (providerSide ? """
                Reference ProviderPlayedRole from consumer.ProviderPlayedRole {
                    String accountCode; String currency; quantum decimal claim;
                }
                """ + take : ""));
        parse(project, ".spp", """
                domain consumer;
                Role Funds { String wallet; quantum decimal amount; }
                Role ProviderPlayedRole { String accountCode; String currency; quantum decimal claim; }
                %s
                quantum update moveFunds(Funds consumerFunds, ProviderPlayedRole played) {
                    consumerFunds.amount -> played.claim : 1.125;
                }
                """.formatted(providerSide ? "" : """
                Reference Position from provider.Position { String account; String currency; %s decimal outstanding; }
                """.formatted(positionModifier) + take));
        parse(project, ".app", """
                domain provider;
                tables entities { table POSITIONS(Position) { primarykey(Position.account, Position.currency); } }
                """);
        parse(project, ".app", """
                domain consumer;
                application transfer { environment develop {
                    deploy consumer; deploy provider;
                    transactions { mode: LOCAL; resource: records; }
                } }
                tables roles { table FUNDS(Funds) { primarykey(Funds.wallet); } }
                scope { local { moveFunds; } }
                """);
        parse(project, ".phy", physical("consumer", "unifiedStore", "positions_value"));
        parse(project, ".phy", physical("provider", "unifiedStore", providerTable));
        return project;
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

    @Test public void resolvesProviderAttributeAndOrderedCompositePlayKeyInLocalDeployment() throws Exception {
        assertCanonicalEndpoint(project("positions_value", "decimal claim = outstanding;", "quantum"));
    }

    @Test public void resolvesProviderSideTakeWithoutConsumerReference() throws Exception {
        var project = project("positions_value", "decimal claim = outstanding;", "quantum", true);
        assertNull(project.getDomain("consumer").getSppClass("Position"));
        assertCanonicalEndpoint(project);
    }

    @Test public void selectsTheDeployedProviderSideTake() throws Exception {
        var project = project("positions_value", "decimal claim = outstanding;", "quantum", true);
        parse(project, ".spp", """
                domain alternate;
                Entity Alternate { String account; String currency; quantum decimal outstanding; }
                Reference ProviderPlayedRole from consumer.ProviderPlayedRole {
                    String accountCode; String currency; quantum decimal claim;
                }
                take Alternate as ProviderPlayedRole {
                    String accountCode = account; mapsame; decimal claim = outstanding;
                }
                """);
        parse(project, ".app", """
                domain alternate;
                tables entities { table ALTERNATE(Alternate) { primarykey(Alternate.account, Alternate.currency); } }
                """);
        parse(project, ".phy", physical("alternate", "alternateStore", "alternate_value"));
        assertCanonicalEndpoint(project);
    }

    private void assertCanonicalEndpoint(SppProject project) {
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(), errors.isEmpty());
        var domain = project.getDomain("consumer");
        errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var service = (SppService) domain.getSppClass("MoveFunds");
        assertEquals("LOCAL", service.getTransactionModeName());
        assertTrue(service.getQuantumServiceMetadataMethod().contains("\"Position.outstanding\""));
        assertTrue(service.getQuantumConservationParticipantMethod().contains(
                "\"Position.outstanding\", \"claim\", \"accountCode\", \"currency\""));
    }

    @Test public void rejectsSplitStoresOnTheSameJdbcResource() throws Exception {
        for (boolean providerSide : new boolean[]{false, true}) {
            var errors = DomainDeploymentValidator.validate(
                    project("other_value", "decimal claim = outstanding;", "quantum", providerSide));
            assertTrue(errors.toString(), errors.stream().anyMatch(error ->
                    error.contains("distributed cross-domain quantum transfer not yet supported (APP TCC pending)")));
        }
    }

    @Test public void rejectsBackendThatCannotEnlistInLocal() throws Exception {
        var project = project("positions_value", "decimal claim = outstanding;", "quantum");
        project.getDomain("consumer").getRuleBlock().getAppPhysicalBackends().get(0)
                .putConfiguration("supportedTransactions", "saga,quantum");
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(), errors.stream().anyMatch(error ->
                error.contains("requires a unified quantum store enlisted in LOCAL")));
    }

    @Test public void rejectsUnmappedAndNonQuantumProviderEndpoints() throws Exception {
        for (var project : new SppProject[] {
                project("positions_value", "", "quantum"),
                project("positions_value", "decimal claim = outstanding;", "")
        }) {
            var errors = DomainDeploymentValidator.validate(project);
            assertTrue(errors.toString(), errors.stream().anyMatch(error ->
                    error.contains("Unmapped quantum transfer endpoint ProviderPlayedRole.claim")));
        }
    }
}
