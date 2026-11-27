package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * A failing plain check ends its enclosing bare block. When that block is the source transaction itself the transaction
 * rolls back (same effect as ,breakTransaction). The bodies of if/else are transparent; an explicit bare serial block
 * exits alone and the transaction continues. ,breakTransaction rolls back from any depth. Outside a transaction a failing check only ends its block (updates need a transaction, so the fixture writes nothing).
 */
public class CheckRollbackSemanticsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String URL = "jdbc:h2:mem:check_rollback;DB_CLOSE_DELAY=-1";

    private void run(String variant) throws Exception {
        Path root = temp.getRoot().toPath();
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("ledger.spp"), """
                domain ledger;
                Contract Voucher short voucher { String voucherCode; boolean rejected; }
                Contract Marker short marker { String markerCode; }
                Enum Fault { REJECTED }
                atomic check guard(Voucher) { require(voucher.rejected == false, Fault.REJECTED); }
                atomic update storeVoucher(Voucher);
                atomic update storeMarker(Marker);
                Scenario serial recordDirect(Voucher) { transaction { storeVoucher; guard; } }
                Scenario serial recordIf(Voucher) { transaction { storeVoucher; if (voucher.rejected) { guard; } } }
                Scenario serial recordNested(Voucher, Marker) { transaction { storeVoucher; serial { guard; } storeMarker; } }
                Scenario serial recordNestedBreak(Voucher, Marker) { transaction { storeVoucher; serial { guard,breakTransaction; } storeMarker; } }
                Scenario serial recordOutside(Voucher) { guard; }
                Scenario serial recordThenFail(Voucher, Marker) { transaction { storeVoucher; storeMarker; } }
                """);
        Files.writeString(source.resolve("ledger.app"), """
                domain ledger;
                application ledgers { deploy ledger; }
                transactions { default at resource records; }
                tables contracts {
                    table VOUCHERS(Voucher) { primarykey(Voucher.voucherCode); }
                    table MARKERS(Marker) { primarykey(Marker.markerCode); }
                }
                scope { local {
                    persist(insert, argument(0, Voucher.voucherCode)) storeVoucher;
                    persist(insert, argument(0, Marker.markerCode)) storeMarker;
                } }
                phy { repository records {
                    adapter jdbcObjectRepository; config default = true;
                    config persistenceMode = LOCAL; config persistenceResource = records; config appDdlTables = true;
                    config datasourceUrl = "%s"; config driverClassName = "org.h2.Driver";
                } }
                """.formatted(URL));
        GeneratedDatabaseFixture.run(root, source, "ledger", Fixture.class, variant);
    }

    @Test public void checkDirectlyInTransactionRollsBack() throws Exception { run("direct"); }
    @Test public void checkInIfBodyRollsBack() throws Exception { run("if"); }
    @Test public void checkInNestedBareBlockExitsOnlyThatBlock() throws Exception { run("nested"); }
    @Test public void breakTransactionInNestedBareBlockRollsBack() throws Exception { run("nestedBreak"); }
    @Test public void checkOutsideTransactionOnlyEndsItsBlock() throws Exception { run("outside"); }
    @Test public void anExceptionRollsTheEarlierRowBack() throws Exception { run("exception"); }

    public static class Fixture {
        public static void main(String[] args) throws Exception {
            var source = new DriverManagerJdbcDataSource("org.h2.Driver", URL, null, null);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "ledgers");
            try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO MARKERS (markerCode) VALUES ('M1')"); }
            String variant = args[0];
            String scenario = switch (variant) { case "direct" -> "recordDirect"; case "if" -> "recordIf"; case "nested" -> "recordNested";
                case "nestedBreak" -> "recordNestedBreak"; case "outside" -> "recordOutside"; default -> "recordThenFail"; };
            var data = new HashMap<String, Object>();
            data.put("voucherCode", "V1"); data.put("rejected", !variant.equals("exception"));
            var body = new HashMap<String, Object>(); body.put("voucher", data);
            if (variant.equals("exception")) body.put("marker", Map.of("markerCode", "M1"));
            if (variant.startsWith("nested")) body.put("marker", Map.of("markerCode", "M2"));
            BusinessChecks.restoreScenarioResult(null);
            boolean threw = false;
            try {
                runtime.executeGeneratedService("ledger." + scenario,
                        mapper.convertValue(body, Class.forName("generated.ledger.model." + Character.toUpperCase(scenario.charAt(0)) + scenario.substring(1) + "Request")));
            } catch (RuntimeException failure) { threw = true; }
            boolean rejected = !BusinessChecks.scenarioResult().passed();
            int rows;
            try (var c = source.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM VOUCHERS")) { r.next(); rows = r.getInt(1); }
            System.out.println(variant + ": threw=" + threw + " rejected=" + rejected + " voucherRows=" + rows);
            int markers;
            try (var c = source.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM MARKERS")) { r.next(); markers = r.getInt(1); }
            switch (variant) {
                case "direct", "if", "nestedBreak", "exception" -> assertEquals(variant + " rolled back", 0, rows);
                case "nested" -> { assertTrue("the check failed", rejected); assertEquals("row stays committed", 1, rows); assertEquals("transaction continued", 2, markers); }
                case "outside" -> { assertTrue("the check failed", rejected); assertFalse("only its block ends", threw); }
                default -> fail(variant);
            }
        }
    }
}
