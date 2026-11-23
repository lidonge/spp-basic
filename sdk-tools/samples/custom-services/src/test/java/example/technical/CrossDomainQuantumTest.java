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
import example.generated.ledger.model.ReadClaimRequest;
import example.generated.ledger.model.TransferFundsRequest;
import example.generated.positions.model.Position;
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

    @Test public void providerSideTakeCommitsRollsBackAndReversesProviderPosition() throws Exception {
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
            DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() ->
                    repository.insert("Position", PROVIDER_KEY,
                            new Position().account("P-1").currency("UNIT").outstanding(new BigDecimal("-2.375")))));
            var store = runtime.getQuantumJournalStore();
            store.seedCurrentValue("$state", FUNDS, "F-1", new BigDecimal("5.250"));
            // This datasource opens its own connections for durable-state assertions.
            var observer = new JdbcQuantumJournalStore(
                    new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null),
                    "transfer_journal", "transfer_values");
            var played = Map.of("accountCode", "P-1", "currency", "UNIT");
            var read = mapper.convertValue(Map.of("played", played), ReadClaimRequest.class);
            runtime.executeGeneratedService("ledger.readClaim", read);
            assertEquals(0, new BigDecimal("-2.375").compareTo(read.getPlayed().getClaim()));
            java.util.function.BiConsumer<String, String> transfer = (account, amount) -> runtime.executeGeneratedService(
                    "ledger.transferFunds", mapper.convertValue(Map.of(
                            "consumerFunds", Map.of("wallet", "F-1"),
                            "played", Map.of("accountCode", account, "currency", "UNIT"),
                            "transfer", Map.of("amount", new BigDecimal(amount))),
                            TransferFundsRequest.class));

            transfer.accept("P-1", "1.125");
            var manager = DefaultTransactionManager.getInstance();
            String committed = manager.committedQuantumBoundaryPayload().orElseThrow();
            assertPositions(observer, "4.125", "-1.250");
            assertEquals(2, observer.findAll().size());
            assertTrue(observer.findAll().stream().anyMatch(row ->
                    CLAIM.equals(row.getAttributeId()) && PROVIDER_KEY.equals(row.getObjectId())));
            assertTrue(observer.findCurrentValue("ProviderPlayedRole.claim", PROVIDER_KEY).isEmpty());
            var booked = observer.findCurrentValues();

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
            assertEquals("QUANTUM_PLAY_POSITION_NOT_FOUND: Position.outstanding", wrongKey.getMessage());
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
            var deleted = assertThrows(IllegalStateException.class, () -> transfer.accept("P-1", "1.125"));
            assertEquals("QUANTUM_PLAY_POSITION_NOT_FOUND: Position.outstanding", deleted.getMessage());
            assertPositions(observer, "5.250", "-2.375");
            assertEquals(4, observer.findAll().size());
        } finally {
            container.clear();
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private void assertAbsentClaim(LocalGeneratedServiceRuntime runtime, ObjectMapper mapper, String account) {
        var request = mapper.convertValue(Map.of("played", Map.of(
                "accountCode", account, "currency", "UNIT", "claim", new BigDecimal("99"))), ReadClaimRequest.class);
        runtime.executeGeneratedService("ledger.readClaim", request);
        assertEquals(0, BigDecimal.ZERO.compareTo(request.getPlayed().getClaim()));
        assertEquals(account, request.getPlayed().getAccountCode());
    }

    private void assertPositions(JdbcQuantumJournalStore store, String funds, String claim) {
        assertEquals(0, new BigDecimal(funds).compareTo(store.findCurrentValue(FUNDS, "F-1").orElseThrow()));
        assertEquals(0, new BigDecimal(claim).compareTo(store.findCurrentValue(CLAIM, PROVIDER_KEY).orElseThrow()));
    }
}
