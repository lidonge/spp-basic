package com.servpp.xboddemo;

import java.nio.file.Path;
import java.util.Map;
import static org.junit.Assert.*;

/** Test-only child JVM. Bank operations are exclusively generated SPP services. */
public final class XbodRestartProbe {
    private XbodRestartProbe() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Expected owner URL, ledger URL, state file and stage");
        try (var demo = new XbodTestSupport(args[0],args[1])) {
            Path state = Path.of(args[2]);
            switch (args[3]) {
                case "setup":
                case "setupCrash": {
                    XbodTransactionTest.open(demo);
                    var paid = demo.call("depositCash",XbodTransactionTest.deposit(100));
                    assertEquals(paid.toString(),"SUCCEEDED",paid.path("cashDepositAgreement").path("resultInfo").path("status").asText());
                    demo.mapper.writeValue(state.toFile(),Map.of("depositNumber",paid.path("cashDepositAgreement").path("depositNumber").asText()));
                    if (args[3].equals("setupCrash")) Runtime.getRuntime().halt(73);
                    break;
                }
                case "reverse": {
                    var saved = demo.mapper.readTree(state.toFile());
                    var original = demo.call("readCashDeposit",Map.of("cashDepositAgreement",Map.of("depositNumber",saved.path("depositNumber").asText())));
                    assertFalse(original.path("cashDepositAgreement").path("reversed").asBoolean());
                    XbodTransactionTest.amount("100", original.path("cashDepositAgreement").path("amount"));
                    var before = demo.readAccounts();
                    XbodTransactionTest.amount("100",before.path("depositAccount").path("interestBase"));
                    var reversed = demo.call("reverseCash",XbodTransactionTest.reversal(original.path("cashDepositAgreement"),20261008,false));
                    assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("cashReversalAgreement").path("resultInfo").path("status").asText());
                    demo.mapper.writeValue(state.toFile(),Map.of("depositNumber",saved.path("depositNumber").asText(),"reversalNumber",reversed.path("cashReversalAgreement").path("reversalNumber").asText()));
                    break;
                }
                case "verify": {
                    var saved = demo.mapper.readTree(state.toFile());
                    var original = demo.call("readCashDeposit",Map.of("cashDepositAgreement",Map.of("depositNumber",saved.path("depositNumber").asText())));
                    assertTrue(original.path("cashDepositAgreement").path("reversed").asBoolean());
                    var reversed = demo.call("readCashReversal",Map.of("cashReversalAgreement",Map.of("reversalNumber",saved.path("reversalNumber").asText())));
                    assertEquals(8,reversed.path("cashReversalPostingLines").size());
                    var account = demo.readAccounts();
                    XbodTransactionTest.amount("0",account.path("depositAccount").path("interestBase"));
                    var cash = demo.readAccounts();
                    XbodTransactionTest.amount("0",cash.path("cashSettlementAccount").path("balance"));
                    break;
                }
                default: throw new IllegalArgumentException("Unknown probe stage");
            }
        }
    }
}
