package com.example.cv1;

import com.example.cv1.client.model.Accepted;
import com.example.cv1.client.model.ConfirmRequest;
import com.example.cv1.client.model.Handover;
import com.example.cv1.client.model.Intake;
import com.example.cv1.client.model.Receipt;
import com.example.cv1.client.model.Till;
import com.example.cv1.provider.model.CustodyTill;
import com.example.cv1.provider.model.OpenTillRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.repository.RepositoryWriteRuleProvider;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import static org.junit.Assert.*;

public class ComposeTest {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:mem:cv1";
        var source = new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null);
        var services = DefaultServiceContainer.getInstance();
        services.clear();
        try (var db = DriverManager.getConnection(url)) {
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source,
                    new ObjectMapper().findAndRegisterModules(), ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "compose");
            var seed = (RepositoryWriteRuleProvider) services.getService("provider.openTill");
            DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> seed.executeInRepositoryScope(() ->
                    runtime.executeGeneratedService("provider.openTill", new OpenTillRequest()
                            .custodyTill(new CustodyTill().receiptNumber("R").cash(new BigDecimal("20"))))));

            try (var sql = db.createStatement()) {
                sql.execute("ALTER TABLE ACCEPTED ADD CONSTRAINT REJECT_R CHECK (receiptNumber <> 'R')");
                var failure = assertThrows(RuntimeException.class, () -> confirm(runtime));
                assertTrue("Failure must come from the final ACCEPTED insert: " + failure,
                        java.util.stream.Stream.iterate((Throwable) failure, java.util.Objects::nonNull, Throwable::getCause)
                                .anyMatch(cause -> cause.getMessage() != null && cause.getMessage().contains("REJECT_R")));
                assertDatabase(db, false);
                sql.execute("ALTER TABLE ACCEPTED DROP CONSTRAINT REJECT_R");
            }

            if ("serial".equals(args[0])) verifyMissingPerformer(runtime, db);

            confirm(runtime);
            assertEquals("", BusinessChecks.scenarioResult().code());
            assertDatabase(db, true);
        } finally {
            services.clear();
        }
    }

    private static void confirm(LocalGeneratedServiceRuntime runtime) {
        runtime.executeGeneratedService("client.confirm", request());
    }

    private static ConfirmRequest request() {
        return new ConfirmRequest()
                .receipt(new Receipt().receiptNumber("R").amount(new BigDecimal("10")))
                .handover(new Handover()).till(new Till()).intake(new Intake()).accepted(new Accepted());
    }

    private static void verifyMissingPerformer(LocalGeneratedServiceRuntime runtime, Connection db) throws Exception {
        var mapper = new ObjectMapper();
        // The additional serial entry points use the same participants as confirm.
        com.fasterxml.jackson.databind.JsonNode json = mapper.valueToTree(request());
        var absentRequest = mapper.treeToValue(json, Class.forName("com.example.cv1.client.model.ConfirmAbsentRequest"));
        var absent = assertThrows(RuntimeException.class,
                () -> runtime.executeGeneratedService("client.confirmAbsent", absentRequest));
        assertTrue(absent.toString(), absent.getMessage().contains("QUANTUM_PLAY_POSITION_NOT_FOUND"));
        assertDatabase(db, false);

        var skippedRequest = mapper.treeToValue(json, Class.forName("com.example.cv1.client.model.ConfirmSkippedRequest"));
        runtime.executeGeneratedService("client.confirmSkipped", skippedRequest);
        assertEquals("PARTICIPANT_NOT_IDENTIFIED", BusinessChecks.scenarioResult().code());
        assertDatabase(db, false);

        var noMovementRequest = mapper.treeToValue(json, Class.forName("com.example.cv1.client.model.ConfirmNoMovementRequest"));
        var noMovement = assertThrows(RuntimeException.class,
                () -> runtime.executeGeneratedService("client.confirmNoMovement", noMovementRequest));
        assertEquals("QUANTUM_TRANSFER_NOT_APPLIED", noMovement.getMessage());
        assertDatabase(db, false);
    }

    private static void assertDatabase(Connection db, boolean committed) throws Exception {
        assertEquals(committed ? 1 : 0, count(db, "SELECT COUNT(*) FROM HANDOVERS"));
        assertEquals(committed ? 1 : 0, count(db, "SELECT COUNT(*) FROM ACCEPTED"));
        assertEquals(committed ? 2 : 0, count(db, "SELECT COUNT(*) FROM CV1_JOURNAL WHERE group_id = 'custody'"));
        try (var sql = db.createStatement();
                var rows = sql.executeQuery("SELECT attribute_id, current_value FROM CV1_VALUE"
                        + " WHERE attribute_id LIKE 'Custody%' ORDER BY attribute_id")) {
            if (committed) {
                assertTrue(rows.next());
                assertEquals("CustodyHandover.cash", rows.getString(1));
                assertEquals(0, rows.getBigDecimal(2).compareTo(BigDecimal.ZERO));
            }
            assertTrue(rows.next());
            assertEquals("CustodyTill.cash", rows.getString(1));
            assertEquals(0, rows.getBigDecimal(2).compareTo(new BigDecimal(committed ? "30" : "20")));
            assertFalse(rows.next());
        }
    }

    private static int count(Connection db, String query) throws Exception {
        try (var sql = db.createStatement(); var rows = sql.executeQuery(query)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
