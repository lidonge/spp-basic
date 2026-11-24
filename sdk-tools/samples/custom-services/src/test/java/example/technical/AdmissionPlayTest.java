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
import example.generated.positions.model.Admission;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdmissionPlayTest {
    private final boolean alternate = Boolean.getBoolean("sample.alternate.admission");

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

        provider.setReturnCode(alternate ? "200" : "");
        mapper.entityToRole(performers, role);
        assertSame(ProviderOutcome.SUCCESS, role.getCode());
        mapper.roleToEntity(role, performers);
        assertEquals(alternate ? "OK" : "", provider.getReturnCode());

        if (alternate) {
            provider.setReturnCode("");
            mapper.entityToRole(performers, role);
            assertTrue(ProviderOutcome.sameMember(ProviderOutcome.UNRECOGNIZED, role.getCode()));
            mapper.roleToEntity(role, performers);
            assertEquals("", provider.getReturnCode());
        }
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
            String[] codes = alternate ? new String[]{"OK", "404", "MISS", "E9999", null, "200", "OLD"}
                    : new String[]{"", "E1214", "OLD", "E9999", null, "", "OLD"};
            String[] expected = {"", "E1214", "E1214", "E9999", "UNRECOGNIZED", "", alternate ? "OLD" : "E1214"};
            for (int i = 0; i < codes.length; i++) {
                String account = alternate && i == 1 ? "K1" : "A" + i;
                var admission = new Admission().account(account).returnCode(codes[i]);
                DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() ->
                        repository.insert("Admission", account, admission)));
                var request = mapper.convertValue(Map.of("result", Map.of("accountNumber", account)), VerifyAdmissionRequest.class);
                runtime.executeGeneratedService("ledger.verifyAdmission", request);
                assertEquals(new BusinessChecks.Result(i == 0 || i == 5, expected[i]), BusinessChecks.scenarioResult());
                assertEquals(expected[i], request.getResult().getCode().getValue());
                if (alternate && i == 6)
                    assertTrue(ProviderOutcome.sameMember(ProviderOutcome.UNRECOGNIZED, request.getResult().getCode()));
            }
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
