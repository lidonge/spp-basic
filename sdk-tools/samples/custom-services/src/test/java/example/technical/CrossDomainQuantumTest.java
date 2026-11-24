package example.technical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.ExampleJdbcFaults;
import com.servpp.service.repository.CompositeObjectId;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.DefaultTransactionManager;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import com.servpp.service.transaction.TransactionMode;
import example.generated.ledger.model.ReadClaimByRequest;
import example.generated.ledger.model.ReadClaimRequest;
import example.generated.ledger.model.TransferFundsRequest;
import example.generated.positions.model.Position;
import example.generated.reserves.model.Reserve;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrossDomainQuantumTest {
    private static final String FUNDS = "Funds.amount";
    private static final String CLAIM = "Position.outstanding";
    private static final String PROVIDER_KEY = CompositeObjectId.encode(List.of("P-1", "UNIT"));

    @Test public void appPlayCommitsRollsBackAndReversesProviderPosition() throws Exception {
        String consumerDdl = java.nio.file.Files.readString(java.nio.file.Path.of(
                "target/spp/src/main/resources/sql/ledger/ddl.sql"));
        assertFalse(consumerDdl.replace("_", "").toLowerCase(java.util.Locale.ROOT).contains("providerplayedrole"));
        String reserveKey = CompositeObjectId.encode(List.of("UNIT", "R-1"));
        String sharedReserveKey = CompositeObjectId.encode(List.of("UNIT", "BOTH"));
        String property = "spp.basic-ledger.datasource-url";
        String previous = System.getProperty(property);
        String url = "jdbc:h2:mem:cross_domain_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        System.setProperty(property, url);
        var source = new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null);
        var container = DefaultServiceContainer.getInstance();
        container.clear();
        try {
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            runtime.enableExampleFaults();
            GeneratedApplicationDeployment.install(runtime, "basicLedger");
            var repository = runtime.getRepository();
            DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() -> {
                repository.insert("Position", PROVIDER_KEY,
                        new Position().account("P-1").currency("UNIT").outstanding(new BigDecimal("-2.375")));
                repository.insert("Position", CompositeObjectId.encode(List.of("BOTH", "UNIT")),
                        new Position().account("BOTH").currency("UNIT").outstanding(new BigDecimal("-3.000")));
                repository.insert("Reserve", reserveKey,
                        new Reserve().referenceCode("R-1").denomination("UNIT").available(new BigDecimal("-4.000")));
                repository.insert("Reserve", sharedReserveKey,
                        new Reserve().referenceCode("BOTH").denomination("UNIT").available(new BigDecimal("-5.000")));
            }));
            try (var connection = source.getConnection();
                    var tables = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) {
                while (tables.next()) assertFalse(tables.getString("TABLE_NAME").contains("PROVIDERPLAYEDROLE")
                        || tables.getString("TABLE_NAME").contains("PROVIDER_PLAYED_ROLE"));
            }
            var store = runtime.getQuantumJournalStore();
            store.seedCurrentValue("$state", FUNDS, "F-1", new BigDecimal("5.250"));
            // This datasource opens its own connections for durable-state assertions.
            var observer = new JdbcQuantumJournalStore(
                    new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null),
                    "transfer_journal", "transfer_values");
            var read = readClaimBy(runtime, mapper);
            assertEquals("P-1", read.getPlayed().getAccountCode());
            assertEquals("UNIT", read.getPlayed().getCurrency());
            assertEquals(0, new BigDecimal("-2.375").compareTo(read.getPlayed().getClaim()));
            java.util.function.BiConsumer<String, String> transfer = (account, amount) -> runtime.executeGeneratedService(
                    "ledger.transferFunds", mapper.convertValue(Map.of(
                            "consumerFunds", Map.of("wallet", "F-1"),
                            "played", Map.of("accountCode", account, "currency", "UNIT"),
                            "transfer", Map.of("amount", new BigDecimal(amount))),
                            TransferFundsRequest.class));

            var transferReadRole = mapper.convertValue(Map.of(
                    "consumerFunds", Map.of("wallet", "F-1"),
                    "transfer", Map.of("amount", new BigDecimal("1.125"))), TransferFundsRequest.class);
            transferReadRole.setPlayed(read.getPlayed());
            runtime.executeGeneratedService("ledger.transferFunds", transferReadRole);
            var manager = DefaultTransactionManager.getInstance();
            String committed = manager.committedQuantumBoundaryPayload().orElseThrow();
            assertPositions(observer, "4.125", "-1.250");
            assertEquals(0, new BigDecimal("-1.250").compareTo(readClaimBy(runtime, mapper).getPlayed().getClaim()));
            assertEquals(2, observer.findAll().size());
            assertTrue(observer.findAll().stream().anyMatch(row ->
                    CLAIM.equals(row.getAttributeId()) && PROVIDER_KEY.equals(row.getObjectId())));
            assertTrue(observer.findCurrentValue("ProviderPlayedRole.claim", PROVIDER_KEY).isEmpty());
            var booked = observer.findCurrentValues();
            var ambiguousRead = assertThrows(IllegalStateException.class, () -> readClaim(runtime, mapper, "BOTH"));
            assertTrue(ambiguousRead.getMessage().startsWith("QUANTUM_PLAY_AMBIGUOUS"));
            var ambiguousTransfer = assertThrows(IllegalStateException.class, () -> transfer.accept("BOTH", "1.125"));
            assertTrue(ambiguousTransfer.getMessage().startsWith("QUANTUM_PLAY_AMBIGUOUS"));
            assertEquals(booked, observer.findCurrentValues());
            assertEquals(2, observer.findAll().size());

            try (var fault = ExampleJdbcFaults.activate("ledger.moveFunds", "beforeCommit")) {
                var failure = assertThrows(IllegalStateException.class, () -> transfer.accept("P-1", "1.125"));
                assertEquals("EXAMPLE_JDBC_BEFORE_COMMIT", failure.getMessage());
                fault.verifyTriggered();
            }
            assertEquals(booked, observer.findCurrentValues());
            assertEquals(2, observer.findAll().size());

            transfer.accept("P-1", "2");
            assertEquals("QUANTUM_BOUND_REJECTED", BusinessChecks.scenarioResult().code());
            assertAbsentClaim(runtime, mapper, "missing");
            var wrongKey = assertThrows(IllegalStateException.class, () -> transfer.accept("missing", "1.125"));
            assertEquals("QUANTUM_PLAY_POSITION_NOT_FOUND: ProviderPlayedRole.claim", wrongKey.getMessage());
            assertEquals(booked, observer.findCurrentValues());
            assertEquals(2, observer.findAll().size());

            manager.reverseCommittedQuantumBoundary(committed);
            assertPositions(observer, "5.250", "-2.375");
            assertEquals(4, observer.findAll().size());
            manager.reverseCommittedQuantumBoundary(committed);
            assertEquals(4, observer.findAll().size());

            // Deleting a performer leaves its ledger history. That history cannot play the role.
            DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() ->
                    repository.delete("Position", PROVIDER_KEY)));
            assertAbsentClaim(runtime, mapper, "P-1");
            var absent = readClaimBy(runtime, mapper).getPlayed();
            assertEquals("P-1", absent.getAccountCode());
            assertEquals("UNIT", absent.getCurrency());
            assertEquals(0, BigDecimal.ZERO.compareTo(absent.getClaim()));
            transferReadRole.setPlayed(absent);
            var deleted = assertThrows(IllegalStateException.class, () ->
                    runtime.executeGeneratedService("ledger.transferFunds", transferReadRole));
            assertEquals("QUANTUM_PLAY_POSITION_NOT_FOUND: ProviderPlayedRole.claim", deleted.getMessage());
            assertPositions(observer, "5.250", "-2.375");
            assertEquals(4, observer.findAll().size());

            assertEquals(0, new BigDecimal("-4.000").compareTo(readClaim(runtime, mapper, "R-1").getPlayed().getClaim()));
            transfer.accept("R-1", "1.125");
            assertEquals(0, new BigDecimal("-2.875").compareTo(
                    observer.findCurrentValue("Reserve.available", reserveKey).orElseThrow()));
            assertPositions(observer, "4.125", "-2.375");
            assertEquals(6, observer.findAll().size());
            assertTrue(observer.findAll().stream().anyMatch(row ->
                    "Reserve.available".equals(row.getAttributeId()) && reserveKey.equals(row.getObjectId())));

            // The consumer's code-free unit becomes the provider's composite key on booking.
            var settlement = mapper.convertValue(Map.of("consumerFunds", Map.of("wallet", "F-1"),
                    "position", Map.of("accountCode", "BOTH", "unit", "UNLISTED"),
                    "transfer", Map.of("amount", BigDecimal.ONE)), example.generated.ledger.model.SettlePositionRequest.class);
            var unmapped = assertThrows(IllegalArgumentException.class,
                    () -> runtime.executeGeneratedService("ledger.settlePosition", settlement));
            assertTrue(unmapped.getMessage(), unmapped.getMessage().contains("No provider code for UNLISTED"));
            settlement.getPosition().setUnit(example.generated.ledger.model.SettlementUnit.LOCAL_UNIT);
            runtime.executeGeneratedService("ledger.settlePosition", settlement);
            assertEquals(0, new BigDecimal("-2.000").compareTo(observer.findCurrentValue(CLAIM,
                    CompositeObjectId.encode(List.of("BOTH", "UNIT"))).orElseThrow()));
        } finally {
            container.clear();
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private ReadClaimByRequest readClaimBy(LocalGeneratedServiceRuntime runtime, ObjectMapper mapper) {
        var request = mapper.convertValue(Map.of("played", Map.of(
                "accountCode", "wrong", "currency", "wrong", "claim", new BigDecimal("99"))), ReadClaimByRequest.class);
        runtime.executeGeneratedService("ledger.readClaimBy", request);
        return request;
    }

    private void assertAbsentClaim(LocalGeneratedServiceRuntime runtime, ObjectMapper mapper, String account) {
        var request = readClaim(runtime, mapper, account);
        assertEquals(0, BigDecimal.ZERO.compareTo(request.getPlayed().getClaim()));
        assertEquals(account, request.getPlayed().getAccountCode());
    }

    private ReadClaimRequest readClaim(LocalGeneratedServiceRuntime runtime, ObjectMapper mapper, String account) {
        var request = mapper.convertValue(Map.of("played", Map.of(
                "accountCode", account, "currency", "UNIT", "claim", new BigDecimal("99"))), ReadClaimRequest.class);
        runtime.executeGeneratedService("ledger.readClaim", request);
        return request;
    }

    private void assertPositions(JdbcQuantumJournalStore store, String funds, String claim) {
        assertEquals(0, new BigDecimal(funds).compareTo(store.findCurrentValue(FUNDS, "F-1").orElseThrow()));
        assertEquals(0, new BigDecimal(claim).compareTo(store.findCurrentValue(CLAIM, PROVIDER_KEY).orElseThrow()));
    }
}
