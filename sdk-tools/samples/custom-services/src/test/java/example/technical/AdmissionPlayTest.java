package example.technical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import example.generated.ledger.model.MapperAdmissionToStoredResult;
import example.generated.ledger.model.ProviderOutcome;
import example.generated.ledger.model.ReadAdmissionRequest;
import example.generated.ledger.model.StoredResult;
import example.generated.ledger.model.VerifyAdmissionRequest;
import example.generated.ledger.model.VerifyAdmissionTextRequest;
import example.generated.ledger.model.VerifyBlankAccountRequest;
import example.generated.positions.model.Admission;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdmissionPlayTest {
    @Test public void storedPlayMapperRoundTripsTheUnknownProviderCode() {
        var mapper = new MapperAdmissionToStoredResult();
        var provider = new example.generated.ledger.model.Admission().returnCode("E9999");
        var performers = Map.<String, Object>of("Admission", provider);
        var role = new StoredResult();
        mapper.entityToRole(performers, role);
        assertTrue(ProviderOutcome.sameMember(ProviderOutcome.UNRECOGNIZED, role.getCode()));
        provider.setReturnCode("OLD");
        mapper.roleToEntity(role, performers);
        assertEquals("E9999", provider.getReturnCode());

        provider.setReturnCode("OLD");
        mapper.entityToRole(performers, role);
        mapper.roleToEntity(role, performers);
        assertEquals("E1214", provider.getReturnCode());
    }

    @Test public void providerCodesReachTheCallerThroughGeneratedPlayAndCheck() throws Exception {
        String property = "spp.basic-ledger.datasource-url";
        String url = "jdbc:h2:mem:admission_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        String previous = System.setProperty(property, url);
        var container = DefaultServiceContainer.getInstance();
        container.clear();
        try {
            var source = new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "basicLedger");
            var repository = runtime.getRepository();
            String[] codes = {"", "E1214", "OLD", "E9999", null};
            String[] expected = {"", "E1214", "E1214", "E9999", "UNRECOGNIZED"};
            for (int i = 0; i < codes.length; i++) {
                String account = "A" + i;
                var admission = new Admission().account(account).returnCode(codes[i]);
                DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() ->
                        repository.insert("Admission", account, admission)));
                var request = mapper.convertValue(Map.of("result", Map.of("accountNumber", account)), VerifyAdmissionRequest.class);
                runtime.executeGeneratedService("ledger.verifyAdmission", request);
                assertEquals(new BusinessChecks.Result(i == 0, expected[i]), BusinessChecks.scenarioResult());
                assertEquals(expected[i], request.getResult().getCode().getValue());
            }
            var padded = new Admission().account("Pad").returnCode("   ");
            DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() ->
                    repository.insert("Admission", "Pad", padded)));
            var text = mapper.convertValue(Map.of("result", Map.of("accountNumber", "Pad")), VerifyAdmissionTextRequest.class);
            runtime.executeGeneratedService("ledger.verifyAdmissionText", text);
            assertTrue(BusinessChecks.scenarioResult().toString(), BusinessChecks.scenarioResult().passed());
            assertEquals("   ", text.getResult().getText());
            try (var connection = source.getConnection();
                 var insert = connection.prepareStatement("INSERT INTO ADMISSIONS (account, returnCode) VALUES (?, ?)")) {
                insert.setString(1, " ".repeat(28));
                insert.setString(2, "   ");
                assertEquals(1, insert.executeUpdate());
            }
            var blankKey = mapper.convertValue(Map.of("result", Map.of("accountNumber", "stale")), VerifyBlankAccountRequest.class);
            runtime.executeGeneratedService("ledger.verifyBlankAccount", blankKey);
            assertTrue(BusinessChecks.scenarioResult().toString(), BusinessChecks.scenarioResult().passed());
            assertEquals("   ", blankKey.getResult().getText());
            var lookup = mapper.convertValue(Map.of("lookup", Map.of("keyCode", "A1")), ReadAdmissionRequest.class);
            runtime.executeGeneratedService("ledger.readAdmission", lookup);
            assertEquals("E1214", lookup.getLookup().getCode().getValue());
            var unknown = mapper.convertValue(Map.of("lookup", Map.of("keyCode", "A3")), ReadAdmissionRequest.class);
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> runtime.executeGeneratedService("ledger.readAdmission", unknown));
            assertTrue(failure.getMessage(), failure.getMessage().contains("Unexpected value 'E9999'"));
        } finally {
            container.clear();
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }
}
