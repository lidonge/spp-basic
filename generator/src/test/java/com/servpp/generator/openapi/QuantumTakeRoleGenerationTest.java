package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class QuantumTakeRoleGenerationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void siblingRolesUseOneConservedPlayerAttribute() throws Exception {
        var source = temporary.newFolder("source").toPath();
        Files.writeString(source.resolve("wallets.spp"), """
                domain wallets;
                Entity Wallet { String account; quantum decimal funds; }
                Role DebitWallet short debit;
                Role CreditWallet short credit;
                Role RefundWallet short refund;
                take Wallet as DebitWallet { String debitAccount = account; }
                take Wallet as CreditWallet;
                take Wallet as RefundWallet;
                quantum update pay(DebitWallet, CreditWallet) { debit.funds -> credit.funds : 2; }
                quantum update refundPayment(CreditWallet, RefundWallet) { credit.funds -> refund.funds : 2; }
                """);
        Files.writeString(source.resolve("wallets.app"), """
                domain wallets;
                tables entities { table WALLETS(Wallet) { primarykey(Wallet.account); } }
                quantumStore values { attribute Wallet.funds; }
                scope { local {
                    quantum resource(movement, Wallet.funds) pay;
                    quantum resource(movement, Wallet.funds) refundPayment;
                } }
                phy {
                    repository records {
                        adapter jdbcObjectRepository;
                        config default = true;
                        config datasourceUrl = "jdbc:h2:mem:role_quantum";
                        config driverClassName = "org.h2.Driver";
                    }
                    backend database {
                        adapter localJdbc;
                        quantum movement;
                        config datasourceUrl = "jdbc:h2:mem:role_quantum";
                        config driverClassName = "org.h2.Driver";
                    }
                    quantumStore values {
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:role_quantum";
                        config driverClassName = "org.h2.Driver";
                        config journalTable = "role_quantum_journal";
                        config valueTable = "role_quantum_value";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                    }
                }
                """);
        var root = temporary.newFolder("output").toPath();
        var java = root.resolve("java");
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), java.toFile(), "generated");
        for (String service : new String[]{"Pay", "RefundPayment"}) {
            var generated = Files.readString(java.resolve("generated/wallets/service/Simple" + service + ".java"));
            assertTrue(generated, generated.contains("return List.of(\"Wallet.funds\");"));
            assertEquals(2, generated.split("\\.conservePlayedArgument\\(", -1).length - 1);
            assertTrue(generated, generated.contains("PlayEndpoint(\"Wallet.funds\""));
            assertTrue(generated, generated.contains("\"movement\""));
        }
        var pay = Files.readString(java.resolve("generated/wallets/service/SimplePay.java"));
        assertTrue(pay, pay.contains("\"debitAccount\""));
    }
}
