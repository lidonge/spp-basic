package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.SourceScenarioService;
import com.servpp.service.transaction.CompensationRuleProvider;
import com.servpp.service.transaction.TransactionMode;
import com.servpp.service.transaction.TransactionalService;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScenarioRecoveryCatalogTest {
    public static class Flow extends SourceScenarioService implements ScopeMetadataProvider,
            SagaTransactionCallProvider, GeneratedServiceKindProvider, CompensationRuleProvider {
        private final String name; private final List<String> calls; private final List<String> recovery;
        Flow(String name, List<String> calls, List<String> recovery) { this.name=name; this.calls=calls; this.recovery=recovery; }
        public String getName() { return name; }
        public Object execute(Object... args) { return null; }
        public String startTransaction() { return null; }
        public void endTransaction(String id) { }
        public boolean isLocalScope() { return true; }
        public String getInputHeaderName() { return null; }
        public String getOutputHeaderName() { return null; }
        public GeneratedServiceKind getGeneratedServiceKind() { return GeneratedServiceKind.SCENARIO; }
        public List<String> getSagaTransactionCallServiceNames() { return calls; }
        public List<String> getCompensationServiceNames() { return recovery; }
    }
    public static class Store implements Service, TransactionalService, ScopeMetadataProvider, GeneratedServiceKindProvider {
        public Object execute(Object... args) { return null; }
        public String getName() { return "store"; }
        public String getTransactionResourceId() { return "records"; }
        public TransactionMode getTransactionMode() { return TransactionMode.QUANTUM; }
        public boolean isLocalScope() { return true; }
        public String getInputHeaderName() { return null; }
        public String getOutputHeaderName() { return null; }
        public GeneratedServiceKind getGeneratedServiceKind() { return GeneratedServiceKind.ATOMIC_UPDATE; }
    }
    private SagaTransactionCallPlan plan(boolean boundary, boolean registeredRecovery) {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, new ObjectMapper(), ignored -> null);
        var workflow = new Flow("workflow", List.of("node"), List.of());
        var node = new Flow("node", boundary ? List.of("store") : List.of(), List.of("recover"));
        var services = new java.util.ArrayList<Service>(List.of(workflow,node,new Store()));
        if (registeredRecovery) services.add(new Flow("recover",List.of("store"),List.of()));
        return runtime.catalogGeneratedServices(services).sagaTransactionCallPlans().stream()
                .filter(p -> p.sagaServiceName().equals("workflow") && p.targetServiceName().equals("node")).findFirst().orElseThrow();
    }
    @Test public void declaredScenarioRecoveryIsBusinessOwnedAndDoesNotRequireObjectSnapshots() {
        var plan = plan(true,true);
        assertTrue(plan.sourceScenarioTransaction()); assertFalse(plan.localAtomicUpdateTransaction());
        assertFalse(plan.objectSnapshotRequired()); assertFalse(plan.quantumBoundaryRecoveryEvidence());
        assertEquals("BUSINESS",plan.recoveryOwner()); assertEquals("RECOVERY_CLOSED",plan.recoveryReadiness());
        assertTrue(plan.recoveryReadyReasons().contains("sourceDeclaredLocalTransactionTarget"));
        assertTrue(plan.recoveryReadyReasons().contains("declaredBusinessCompensation"));
        assertTrue(plan.recoveryContract().contains("DECLARED_BUSINESS_COMPENSATION"));
        assertFalse(plan.recoveryMissingReasons().contains("localAtomicUpdateTarget"));
    }
    @Test public void missingBusinessRecoveryStaysOpenWithoutSuggestingAnInvisibleSnapshotWrapper() {
        var plan = plan(true,false);
        assertTrue(plan.sourceScenarioTransaction()); assertEquals("RECOVERY_OPEN",plan.recoveryReadiness());
        assertEquals(List.of("declaredBusinessCompensation"),plan.recoveryMissingReasons());
        assertEquals(List.of("bindLocalBusinessCompensation"),plan.recoveryActions());
    }
    @Test public void scenarioWithoutDeclaredLocalCallEvidenceDoesNotClaimAClosedRecovery() {
        var plan = plan(false,true);
        assertFalse(plan.sourceScenarioTransaction()); assertEquals("RECOVERY_OPEN",plan.recoveryReadiness());
        assertTrue(plan.recoveryMissingReasons().contains("localAtomicUpdateTarget"));
    }
}
