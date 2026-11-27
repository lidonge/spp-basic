package example.technical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.repository.CompositeObjectId;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.ExampleJdbcFaults;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import com.servpp.service.transaction.TransactionMode;
import example.generated.ledger.model.ReadWalletRolesRequest;
import example.generated.ledger.model.TransferPositionRequest;
import example.generated.ledger.model.TransferWalletRequest;
import example.generated.ledger.model.Funds;
import example.generated.ledger.model.LocalWallet;
import example.generated.positions.model.Position;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class SameFieldTransferTest {
    @Test public void twoPlayedRolesBookOneProviderFieldByCompositeKey() throws Exception {
        exercise(true);
    }

    @Test public void twoLocalWalletsBookOneTakenRoleFieldByKey() throws Exception {
        exercise(false);
    }

    @Test public void sameDomainRolesAreHydratedFromTheirOwnKeysOfOneEntity() throws Exception {
        String url = "jdbc:h2:mem:same_domain_roles_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        String property = "spp.basic-ledger.datasource-url";
        String previous = System.setProperty(property, url);
        var source = new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null);
        var container = DefaultServiceContainer.getInstance();
        container.clear();
        try {
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "basicLedger");
            var repository = runtime.getRepository();
            DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() -> {
                // LocalWallet is a stored role over Funds: the provider read needs the role row and its performer row.
                for (String wallet : List.of("A", "B")) {
                    repository.insert("LocalWallet", wallet, new LocalWallet().wallet(wallet).owner("owner of " + wallet));
                    repository.insert("Funds", wallet, new Funds().wallet(wallet).owner("owner of " + wallet));
                }
            }));
            var request = mapper.convertValue(Map.of("origin", Map.of("wallet", "A", "owner", "stale"),
                    "destination", Map.of("wallet", "B", "owner", "stale")), ReadWalletRolesRequest.class);
            runtime.executeGeneratedService("ledger.readWalletRoles", request);
            assertEquals("A", request.getOrigin().getWallet());
            assertEquals("B", request.getDestination().getWallet());
            // Each role is read through its own key, and facts the caller supplied are replaced.
            assertEquals("owner of A", request.getOrigin().getOwner());
            assertEquals("owner of B", request.getDestination().getOwner());
            // An absent entity row leaves no hydrated fact.
            var absent = mapper.convertValue(Map.of("origin", Map.of("wallet", "A"),
                    "destination", Map.of("wallet", "MISSING", "owner", "stale")), ReadWalletRolesRequest.class);
            runtime.executeGeneratedService("ledger.readWalletRoles", absent);
            assertEquals("owner of A", absent.getOrigin().getOwner());
            assertNotEquals("owner of MISSING", absent.getDestination().getOwner());
        } finally {
            container.clear();
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private void exercise(boolean played) throws Exception {
        String url = "jdbc:h2:mem:same_field_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        String property = "spp.basic-ledger.datasource-url";
        String previous = System.setProperty(property, url);
        var source = new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null);
        var container = DefaultServiceContainer.getInstance();
        container.clear();
        try {
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            runtime.enableExampleFaults();
            GeneratedApplicationDeployment.install(runtime, "basicLedger");
            String attribute = played ? "Position.outstanding" : "LocalWallet.amount";
            String a = played ? CompositeObjectId.encode(List.of("A", "UNIT")) : "A";
            String b = played ? CompositeObjectId.encode(List.of("B", "UNIT")) : "B";
            if (played) {
                var repository = runtime.getRepository();
                DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() -> {
                    repository.insert("Position", a, new Position().account("A").currency("UNIT").outstanding(BigDecimal.TEN));
                    repository.insert("Position", CompositeObjectId.encode(List.of("C", "UNIT")),
                            new Position().account("C").currency("UNIT").outstanding(BigDecimal.ZERO));
                    repository.insert("Position", b, new Position().account("B").currency("UNIT").outstanding(BigDecimal.ONE));
                }));
            } else {
                // The two same-domain roles are played by LocalWallet, whose rows must exist like any provider row.
                var repository = runtime.getRepository();
                DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() -> {
                    for (String wallet : List.of(a, "C", b))
                        repository.insert("LocalWallet", wallet, new LocalWallet().wallet(wallet));
                }));
                runtime.getQuantumJournalStore().seedCurrentValue("$state", attribute, a, BigDecimal.TEN);
                runtime.getQuantumJournalStore().seedCurrentValue("$state", attribute, "C", BigDecimal.ZERO);
                runtime.getQuantumJournalStore().seedCurrentValue("$state", attribute, b, BigDecimal.ONE);
            }
            var observer = new JdbcQuantumJournalStore(source, "transfer_journal", "transfer_values");
            java.util.function.BiConsumer<String, Integer> move = (destination, amount) -> {
                var input = Map.of(played ? "played" : "origin", played ? Map.of("accountCode", "A", "currency", "UNIT") : Map.of("wallet", "A"),
                        played ? "position" : "destination", played ? Map.of("accountCode", destination, "unit", "LOCAL_UNIT") : Map.of("wallet", destination),
                        "transfer", Map.of("amount", amount));
                if (played) runtime.executeGeneratedService("ledger.transferPosition", mapper.convertValue(input, TransferPositionRequest.class));
                else runtime.executeGeneratedService("ledger.transferWallet", mapper.convertValue(input, TransferWalletRequest.class));
            };
            move.accept("B", 3);
            assertEquals(0, BigDecimal.valueOf(7).compareTo(observer.findCurrentValue(attribute, a).orElseThrow()));
            assertEquals(0, BigDecimal.valueOf(4).compareTo(observer.findCurrentValue(attribute, b).orElseThrow()));
            var rows = observer.findAll();
            assertEquals(2, rows.size());
            for (var row : rows) assertEquals(attribute, row.getAttributeId());
            assertEquals(0, BigDecimal.valueOf(-3).compareTo(rows.stream().filter(row -> a.equals(row.getObjectId()))
                    .findFirst().orElseThrow().getDeltaValue()));
            assertEquals(0, BigDecimal.valueOf(3).compareTo(rows.stream().filter(row -> b.equals(row.getObjectId()))
                    .findFirst().orElseThrow().getDeltaValue()));
            var committed = observer.findCurrentValues();
            move.accept("B", 7); // Destination would reach 11. Source would reach its allowed minimum.
            assertEquals("QUANTUM_BOUND_REJECTED", BusinessChecks.scenarioResult().code());
            move.accept("C", 8); // Source would reach -1. Destination would remain within its interval.
            assertEquals("QUANTUM_BOUND_REJECTED", BusinessChecks.scenarioResult().code());
            var self = assertThrows(IllegalStateException.class, () -> move.accept("A", 0));
            assertTrue(self.getMessage(), self.getMessage().contains("QUANTUM_SELF_TRANSFER"));
            try (var fault = ExampleJdbcFaults.activate(played ? "ledger.movePosition" : "ledger.moveWallet", "beforeCommit")) {
                assertEquals("EXAMPLE_JDBC_BEFORE_COMMIT", assertThrows(IllegalStateException.class,
                        () -> move.accept("B", 1)).getMessage());
                fault.verifyTriggered();
            }
            assertEquals(committed, observer.findCurrentValues());
            assertEquals(2, observer.findAll().size());
        } finally {
            container.clear();
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }
}
