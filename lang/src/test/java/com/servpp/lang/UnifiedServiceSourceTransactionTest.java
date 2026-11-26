package com.servpp.lang;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

/**
 * A source {@code transaction { }} block with quantum updates needs a quantum boundary that can
 * be settled: a centralized JDBC store (LOCAL) or the settlement service (UNIFIED_SERVICE).
 */
public class UnifiedServiceSourceTransactionTest {
    private static final String REJECTION = "requires a centralized JDBC quantum store";

    private SppDomain compile(String quantumPhy, String system) throws Exception {
        File dir = Files.createTempDirectory("spp-unified-service-transaction").toFile();
        Files.writeString(new File(dir, "ledger.spp").toPath(), """
                domain ledger;
                Role Account { String accountNo; quantum BigDecimal(19,2) available; }
                Role Hold { String holdNo; BigDecimal(19,2) amount; quantum BigDecimal(19,2) reserved; }
                quantum update reserve(Account account, Hold hold) { account.available -> hold.reserved : hold.amount; }
                Scenario serial reserveOrder(Account account, Hold hold) { transaction { reserve; } }
                """);
        Files.writeString(new File(dir, "ledger.app").toPath(), """
                domain ledger;
                transactions { default at resource records; }
                quantumStore fixtureLedger {
                    attribute Account.available;
                    attribute Hold.reserved;
                }
                scope { local { reserveOrder; } }
                """);
        Files.writeString(new File(dir, "ledger.phy").toPath(), "domain ledger;\n" + quantumPhy);
        SppProject project = new SppProject();
        new SppCompiler(new File(dir, "ledger.spp"), project).compile();
        project.getSppDomainMap().values().forEach(SppDomain::dealEntityToRoleMaps);
        new AppCompiler(new File(dir, "ledger.app"), project).compile();
        new AppCompiler(new File(dir, "ledger.phy"), project).compile();
        SppDomain domain = project.getDomain("ledger");
        if (system != null) {
            domain.getRuleBlock().selectDeployment("develop", "SAGA", system, "fixtureLedger");
        }
        return domain;
    }

    private static final String QSERVICE_PHY = """
            phy {
                quantumStore fixtureLedger {
                    adapter quantumService;
                    config endpoint = "https://quantum.example.test";
                    config serviceProtocol = "LOGICAL_QSERVICE";
                    config namespace = "ledger";
                    config reversalMode = "SERVICE_REVERSAL";
                    quantum "Account.available" { numberType LONG; minValue "0"; maxValue "100000000000"; scale 2; }
                    quantum "Hold.reserved" { numberType LONG; minValue "0"; maxValue "100000000000"; scale 2; }
                }
            }
            """;

    private static boolean rejectsQuantumTransaction(List<String> errors) {
        return errors.stream().anyMatch(message -> message.contains(REJECTION));
    }

    @Test
    public void unifiedServiceAcceptsASourceTransactionWithQuantumUpdates() throws Exception {
        List<String> errors = compile(QSERVICE_PHY, "UNIFIED_SERVICE").validatePhysicalRuntimeDeclarations();
        assertFalse(errors.toString(), rejectsQuantumTransaction(errors));
    }

    @Test
    public void withoutUnifiedServiceTheQuantumServiceStoreStillCannotSettleASourceTransaction() throws Exception {
        List<String> noSystem = compile(QSERVICE_PHY, null).validatePhysicalRuntimeDeclarations();
        assertTrue(noSystem.toString(), rejectsQuantumTransaction(noSystem));
        List<String> unifiedStore = compile(QSERVICE_PHY, "UNIFIED_STORE").validatePhysicalRuntimeDeclarations();
        assertTrue(unifiedStore.toString(), rejectsQuantumTransaction(unifiedStore));
    }
}
