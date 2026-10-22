package com.servpp.service.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import com.servpp.service.Service;
import com.servpp.service.transaction.DefaultTransactionManager;
import com.servpp.service.transaction.QuantumConservationRecord;
import com.servpp.service.transaction.QuantumEntanglementBoundary;
import com.servpp.service.transaction.LogicalQuantumProvisioningResponse;
import com.servpp.service.transaction.SagaExecutionScope;
import com.servpp.service.transaction.SagaRecoveryScanResult;
import com.servpp.service.transaction.SagaTransactionGroupCoordinator;
import com.servpp.service.transaction.SagaTransactionGroupManager;
import com.servpp.service.transaction.TccObjectStoreQuantumAccessAdapter;
import com.servpp.service.transaction.TccObjectStoreQuantumCoordinator;
import com.servpp.service.transaction.TransactionContext;
import com.servpp.service.transaction.TransactionMode;
import com.servpp.service.transaction.TransactionalService;

public class LocalGeneratedServiceRuntimeTest {
    @Test
    public void physicalCatalogSelectsConfiguredNativeSagaCoordinator() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        FakeSagaCoordinator.tenantId = null;
        FakeSagaCoordinator.executed = false;
        try {
            runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                    "backends=sagaTx\n"
                            + "backend.sagaTx.adapter=seata\n"
                            + "backend.sagaTx.bindings=saga:*\n"
                            + "backend.sagaTx.config.applicationId=credit-commerce\n"
                            + "backend.sagaTx.config.transactionServiceGroup=credit-commerce-tx-group\n"
                            + "backend.sagaTx.config.sagaImplementation=SEATA_SAGA\n"
                            + "backend.sagaTx.config.sagaCoordinatorClass="
                            + FakeSagaCoordinator.class.getName() + "\n")
                    .getBytes(StandardCharsets.UTF_8)));

            SagaTransactionGroupManager.execute(List.of());

            assertEquals("credit-commerce", FakeSagaCoordinator.tenantId);
            assertTrue(FakeSagaCoordinator.executed);
        } finally {
            SagaTransactionGroupManager.resetCoordinator();
        }
    }

    @Test
    public void nativeSagaParticipatesInUnifiedRecoveryWorkItemsAndPlans() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(
                        h2DataSource(), new ObjectMapper(), type -> null);
        FakeSagaCoordinator.recoveryLimit = 0;
        try {
            runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                    "backends=sagaTx\n"
                            + "backend.sagaTx.adapter=seata\n"
                            + "backend.sagaTx.bindings=saga:*\n"
                            + "backend.sagaTx.config.applicationId=credit-commerce\n"
                            + "backend.sagaTx.config.sagaImplementation=SEATA_SAGA\n"
                            + "backend.sagaTx.config.sagaCoordinatorClass="
                            + FakeSagaCoordinator.class.getName() + "\n"
                            + "backend.sagaTx.config.sagaStateDatasourceUrl="
                            + "jdbc:h2:mem:native-saga-work-item\n"
                            + "backend.sagaTx.config.sagaStateDriverClassName=org.h2.Driver\n"
                            + "backend.sagaTx.config.sagaStateTablePrefix=saga_work_\n"
                            + "backend.sagaTx.config.sagaRecoveryLeaseTable=spp_saga_lease\n"
                            + "backend.sagaTx.config.sagaRecoveryBatchSize=25\n")
                    .getBytes(StandardCharsets.UTF_8)));

            GeneratedServiceCatalog catalog =
                    runtime.catalogGeneratedServices(List.of(new FakeSagaService()));
            RecoveryWorkItemPlan plan = catalog.recoveryWorkItemPlansByKind()
                    .get("NATIVE_SAGA");
            assertEquals(List.of("submitOrder"), plan.sourceServiceNames());
            assertEquals(List.of("SCAN", "SKIP"), plan.supportedDecisions());
            assertEquals("saga_work_state_machine_inst",
                    plan.nativeSagaStateMachineInstanceTableName());
            assertEquals("saga_work_state_inst",
                    plan.nativeSagaStateInstanceTableName());
            assertEquals("spp_saga_lease", plan.recoveryLeaseTableName());
            assertEquals("RECOVERY_WORK_ITEM_READY", plan.readiness());
            assertTrue(plan.physicalEvidencePaths().contains(
                    "NATIVE_SAGA:sagaTx:SEATA_STATE_MACHINE_INSTANCE:"
                            + "saga_work_state_machine_inst"));
            assertTrue(plan.physicalEvidencePaths().contains(
                    "NATIVE_SAGA:sagaTx:SAGA_RECOVERY_LEASE:spp_saga_lease"));
            RecoveryDeploymentPlan deployment = catalog
                    .recoveryDeploymentPlansByWorkItemKind()
                    .get("NATIVE_SAGA")
                    .get(0);
            assertEquals("nativeSagaRecovery:sagaTx", deployment.planName());
            assertEquals("jdbc:h2:mem:native-saga-work-item",
                    deployment.backendDatasourceUrl());
            assertEquals(List.of(
                            "SEATA_STATE_MACHINE_INSTANCE",
                            "SEATA_STATE_INSTANCE",
                            "SAGA_RECOVERY_LEASE"),
                    deployment.evidenceSources());
            assertEquals("RECOVERY_DEPLOYMENT_READY", deployment.readiness());

            RecoveryWorkItem item = runtime.findRecoveryWorkItems().stream()
                    .filter(candidate -> "NATIVE_SAGA".equals(candidate.kind()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("native-saga:sagaTx", item.workId());
            assertEquals("SCAN", item.suggestedAction());

            List<RecoveryWorkItemScanResult> results =
                    runtime.recoverRecoveryWorkItems(
                            candidate -> RecoveryWorkItemDecision.SCAN, 7);
            RecoveryWorkItemScanResult result = results.stream()
                    .filter(candidate -> "NATIVE_SAGA".equals(
                            candidate.workItem().kind()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(7, FakeSagaCoordinator.recoveryLimit);
            assertEquals(new SagaRecoveryScanResult(
                            2, 1, 1, List.of("retry later")),
                    result.nativeSagaRecoveryResult());
        } finally {
            SagaTransactionGroupManager.resetCoordinator();
        }
    }

    @Test
    public void physicalCatalogRejectsNativeSagaWithoutCoordinatorClass() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                        "backends=sagaTx\n"
                                + "backend.sagaTx.adapter=seata\n"
                                + "backend.sagaTx.bindings=saga:*\n"
                                + "backend.sagaTx.config.sagaImplementation=SEATA_SAGA\n")
                        .getBytes(StandardCharsets.UTF_8))));

        assertTrue(failure.getMessage().contains("does not configure sagaCoordinatorClass"));
    }

    @Test
    public void quantumConservationCatalogTreatsThreeAttributesAsOneClosureGroup() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        GeneratedServiceCatalog catalog =
                runtime.catalogGeneratedServices(List.of(new ThreeAttributeQuantumService()));
        QuantumConservationPlan plan = catalog.quantumConservationPlansByService().get("rebalance").get(0);

        assertEquals("ledgerAccounting", plan.resourceId());
        assertEquals(List.of("Ledger.available", "Ledger.reserved", "Ledger.captured"), plan.attributeIds());
        assertEquals(3, plan.attributeCount());
        assertFalse(plan.pairDeclared());
        assertTrue(plan.conservationClosureCandidate());
        assertEquals("CONSERVATION_DECLARATION_READY", plan.readiness());
        assertTrue(plan.missingReasons().isEmpty());
        assertEquals(List.of("NO_ACTION_REQUIRED"), plan.actions());
        assertTrue(catalog.quantumConservationPlansByAttributeSet()
                .get("Ledger.available+Ledger.reserved+Ledger.captured")
                .contains(plan));
        assertEquals("rebalance:ledgerAccounting"
                        + "->Ledger.available+Ledger.reserved+Ledger.captured"
                        + ":CONSERVATION_DECLARATION_READY",
                plan.conservationContract());
    }

    @Test
    public void roleParticipantCatalogAndIdentityResolutionKeepAlternativeRolePerformers() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadRoleParticipantPlans(new ByteArrayInputStream(("roleParticipant.count=2\n"
                + "roleParticipant.0.roleType=Inventory\n"
                + "roleParticipant.0.performerTypes=Store,Goods\n"
                + "roleParticipant.0.performerKeyPaths=Store.name,Goods.skuId\n"
                + "roleParticipant.0.platformColumns=store_name,goods_skuid\n"
                + "roleParticipant.0.complete=true\n"
                + "roleParticipant.1.roleType=Inventory\n"
                + "roleParticipant.1.performerTypes=Warehouse,Goods\n"
                + "roleParticipant.1.performerKeyPaths=Warehouse.code,Goods.skuId\n"
                + "roleParticipant.1.platformColumns=warehouse_code,goods_skuid\n"
                + "roleParticipant.1.complete=true\n")
                .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog =
                runtime.catalogGeneratedServices(List.of(new PolymorphicInventoryService()));

        assertEquals(2, catalog.roleParticipantPlansByRole().get("Inventory").size());
        assertEquals(2, catalog.roleBusinessModelClosurePlansByRole().get("Inventory").size());
        assertTrue(catalog.roleBusinessModelClosurePlansByPerformerTypeSet()
                .containsKey("Store+Goods"));
        assertTrue(catalog.roleBusinessModelClosurePlansByPerformerTypeSet()
                .containsKey("Warehouse+Goods"));
        assertTrue(catalog.roleBusinessModelClosurePlansByRoleModelContract()
                .containsKey("Inventory:JOINT_PERFORMER:Store+Goods:ROLE_MODEL_CLOSED"));
        assertTrue(catalog.roleBusinessModelClosurePlansByRoleModelContract()
                .containsKey("Inventory:JOINT_PERFORMER:Warehouse+Goods:ROLE_MODEL_CLOSED"));
        assertTrue(catalog.roleParticipantRequestBindingPlansByRole().get("Inventory").stream()
                .anyMatch(plan -> plan.performerTypes().equals(List.of("Store", "Goods"))
                        && plan.sidecarFieldNames().equals(List.of("inventoryStore", "inventoryGoods"))));
        assertTrue(catalog.roleParticipantRequestBindingPlansByRole().get("Inventory").stream()
                .anyMatch(plan -> plan.performerTypes().equals(List.of("Warehouse", "Goods"))
                        && plan.sidecarFieldNames().equals(List.of("inventoryWarehouse", "inventoryGoods"))));

        RoleParticipantIdentity identity = runtime.resolveRoleParticipantIdentityFromPerformers("Inventory",
                new Warehouse("wh-a"), new Goods("sku-a")).orElseThrow();
        assertEquals(List.of("Warehouse", "Goods"), identity.performerTypes());
        assertEquals(List.of("warehouse_code", "goods_skuid"), identity.platformColumns());
        assertEquals(List.of("wh-a", "sku-a"), identity.keyValues());
    }

    @Test
    public void roleParticipantCatalogRejectsPerformerCountMismatch() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadRoleParticipantPlans(new ByteArrayInputStream(("roleParticipant.count=1\n"
                        + "roleParticipant.0.roleType=Inventory\n"
                        + "roleParticipant.0.performerTypes=Store,Goods\n"
                        + "roleParticipant.0.performerCount=1\n"
                        + "roleParticipant.0.jointPerformer=true\n")
                        .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog roleParticipant.0.performerCount"
                        + " must match performerTypes size 2, but was 1"));
    }

    @Test
    public void roleParticipantCatalogRejectsInvalidJointPerformerFlag() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadRoleParticipantPlans(new ByteArrayInputStream(("roleParticipant.count=1\n"
                        + "roleParticipant.0.roleType=Inventory\n"
                        + "roleParticipant.0.performerTypes=Store,Goods\n"
                        + "roleParticipant.0.performerCount=2\n"
                        + "roleParticipant.0.jointPerformer=maybe\n")
                        .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "roleParticipant.0.jointPerformer must be true or false, but was maybe"));
    }

    @Test
    public void tccObjectStoreQuantumPhysicalRuntimeUsesConfiguredCoordinator() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        AtomicReference<QuantumEntanglementBoundary> reversedBoundary = new AtomicReference<>();
        TccObjectStoreQuantumCoordinator coordinator = new TccObjectStoreQuantumCoordinator() {
            @Override
            public void commit(String storeName, TransactionContext context, QuantumEntanglementBoundary boundary) {
            }

            @Override
            public void reverse(String storeName, TransactionContext context,
                                QuantumEntanglementBoundary reversalBoundary) {
                reversedBoundary.set(reversalBoundary);
            }
        };
        runtime.getPhysicalRuntime().setTccObjectStoreQuantumCoordinator(coordinator);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                "backends=objectTx\n"
                        + "backend.objectTx.adapter=localJdbc\n"
                        + "backend.objectTx.bindings=quantum:*\n"
                        + "backend.objectTx.quantumStore=objectQuantum\n"
                        + "backend.objectTx.config.datasourceUrl=jdbc:h2:mem:spp-tcc-quantum-runtime;"
                        + "DB_CLOSE_DELAY=-1\n"
                        + "backend.objectTx.config.driverClassName=org.h2.Driver\n"
                        + "quantumStores=objectQuantum\n"
                        + "quantumStore.objectQuantum.adapter=tccObjectStore\n"
                        + "quantumStore.objectQuantum.attributes=Inventory.availableQty,SalesItem.orderQty\n")
                .getBytes(StandardCharsets.UTF_8)));
        TccObjectStoreQuantumAccessAdapter adapter = (TccObjectStoreQuantumAccessAdapter)
                DefaultTransactionManager.getInstance().getQuantumAccessAdapter();
        TransactionContext context = new TransactionContext(Map.of("inventoryAccounting", TransactionMode.QUANTUM));
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary(
                "inventoryAccounting", List.of("inventoryAccounting"));
        boundary.addRecord(new QuantumConservationRecord(
                "inventoryAccounting", "Inventory.availableQty", "MAIN|SKU-1",
                BigDecimal.valueOf(20), BigDecimal.valueOf(-2), BigDecimal.valueOf(18)));

        adapter.reverse(context, boundary);

        assertEquals("objectQuantum", adapter.getStoreName());
        assertEquals(coordinator, adapter.getCoordinator());
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK, boundary.getStatus());
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK, reversedBoundary.get().getStatus());
        assertEquals(BigDecimal.valueOf(2), reversedBoundary.get().getRecords().get(0).getDeltaValue());
        assertEquals(BigDecimal.valueOf(20), reversedBoundary.get().getRecords().get(0).getAfterValue());
    }

    @Test
    public void logicalQuantumServicePhysicalRuntimeProvisionsObjectThroughConfiguredEndpoint()
            throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/logical/provisions", exchange -> {
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            com.fasterxml.jackson.databind.JsonNode request =
                    new ObjectMapper().readTree(requestBody.get());
            String body = """
                    {
                      "logicalKey":%s,
                      "quantumID":23,
                      "definition":%s,
                      "physicalInitialValue":"1234"
                    }
                    """.formatted(request.get("logicalKey"), request.get("definition"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            LocalGeneratedServiceRuntime runtime =
                    LocalGeneratedServiceRuntime.jsonSnapshots(
                            h2DataSource(), new ObjectMapper(), type -> null);
            runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                    "quantumStores=globalQuantum\n"
                            + "quantumStore.globalQuantum.adapter=quantumService\n"
                            + "quantumStore.globalQuantum.attributes=Account.balance\n"
                            + "quantumStore.globalQuantum.config.endpoint=http://127.0.0.1:"
                            + server.getAddress().getPort() + "\n"
                            + "quantumStore.globalQuantum.config.serviceProtocol=LOGICAL_QSERVICE\n"
                            + "quantumStore.globalQuantum.config.namespace=commerce\n"
                            + "quantumStore.globalQuantum.config.reversalMode=SERVICE_REVERSAL\n"
                            + "quantumStore.globalQuantum.definitionCount=1\n"
                            + "quantumStore.globalQuantum.definition.0.attributeId=Account.balance\n"
                            + "quantumStore.globalQuantum.definition.0.numberType=LONG\n"
                            + "quantumStore.globalQuantum.definition.0.minValue=0\n"
                            + "quantumStore.globalQuantum.definition.0.maxValue=1000000\n"
                            + "quantumStore.globalQuantum.definition.0.scale=2\n")
                    .getBytes(StandardCharsets.UTF_8)));

            GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
            PhysicalQuantumPlan plan = catalog.physicalQuantumPlansByStore().get("globalQuantum");
            assertEquals("LOGICAL_QSERVICE", plan.serviceProtocol());
            assertEquals("SERVICE_REVERSAL", plan.reversalMode());
            assertFalse(plan.missingReasons().contains("reversalModeMismatch"));
            assertEquals(List.of(plan),
                    catalog.physicalQuantumPlansByServiceProtocol().get("LOGICAL_QSERVICE"));
            assertFalse(catalog.quantumStoreValidationErrors().stream()
                    .anyMatch(error -> error.contains("config reversalMode must be NEGATIVE_DELTA")));
            List<LogicalQuantumProvisioningResponse> responses =
                    runtime.provisionQuantumObject(
                            "ACCOUNT-1", Map.of("Account.balance", "12.34"));

            assertEquals(1, responses.size());
            assertEquals(23, responses.get(0).quantumID());
            assertTrue(requestBody.get(),
                    requestBody.get().contains("\"objectId\":\"ACCOUNT-1\""));
            assertTrue(requestBody.get(),
                    requestBody.get().contains("\"initialValue\":\"12.34\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void quantumServicePhysicalRuntimeRejectsUnsupportedProtocol() throws Exception {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(
                        h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.attributes=Account.balance\n"
                        + "quantumStore.globalQuantum.config.endpoint=https://quantum.example.test\n"
                        + "quantumStore.globalQuantum.config.serviceProtocol=GRPC\n"
                        + "quantumStore.globalQuantum.config.reversalMode=NEGATIVE_DELTA\n")
                .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalQuantumPlan plan = catalog.physicalQuantumPlansByStore().get("globalQuantum");

        assertEquals("GRPC", plan.serviceProtocol());
        assertTrue(plan.missingReasons().contains("serviceProtocol"));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "config serviceProtocol declares unsupported protocol GRPC")));

        LocalGeneratedServiceRuntime blankProtocolRuntime =
                LocalGeneratedServiceRuntime.jsonSnapshots(
                        h2DataSource(), new ObjectMapper(), type -> null);
        blankProtocolRuntime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.attributes=Account.balance\n"
                        + "quantumStore.globalQuantum.config.endpoint=https://quantum.example.test\n"
                        + "quantumStore.globalQuantum.config.serviceProtocol=\n"
                        + "quantumStore.globalQuantum.config.reversalMode=NEGATIVE_DELTA\n")
                .getBytes(StandardCharsets.UTF_8)));

        List<String> blankProtocolErrors =
                blankProtocolRuntime.catalogGeneratedServices().quantumStoreValidationErrors();
        assertTrue(blankProtocolErrors.toString(), blankProtocolErrors.stream()
                .anyMatch(error -> error.contains("serviceProtocol must not be blank when declared")));

        LocalGeneratedServiceRuntime mixedProtocolRuntime =
                LocalGeneratedServiceRuntime.jsonSnapshots(
                        h2DataSource(), new ObjectMapper(), type -> null);
        mixedProtocolRuntime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream((
                "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.attributes=Account.balance\n"
                        + "quantumStore.globalQuantum.config.endpoint=https://quantum.example.test\n"
                        + "quantumStore.globalQuantum.config.serviceProtocol=HTTP_JSON\n"
                        + "quantumStore.globalQuantum.config.namespace=commerce\n"
                        + "quantumStore.globalQuantum.config.reversalMode=NEGATIVE_DELTA\n"
                        + "quantumStore.globalQuantum.definitionCount=1\n"
                        + "quantumStore.globalQuantum.definition.0.attributeId=Account.balance\n"
                        + "quantumStore.globalQuantum.definition.0.numberType=LONG\n"
                        + "quantumStore.globalQuantum.definition.0.minValue=0\n"
                        + "quantumStore.globalQuantum.definition.0.maxValue=1000000\n"
                        + "quantumStore.globalQuantum.definition.0.scale=2\n")
                .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog mixedProtocolCatalog =
                mixedProtocolRuntime.catalogGeneratedServices();
        List<String> mixedProtocolErrors = mixedProtocolCatalog.quantumStoreValidationErrors();
        assertTrue(mixedProtocolErrors.toString(), mixedProtocolErrors.stream().anyMatch(error -> error.contains(
                "config namespace is only supported when serviceProtocol is LOGICAL_QSERVICE")));
        assertTrue(mixedProtocolErrors.toString(), mixedProtocolErrors.stream().anyMatch(error -> error.contains(
                "quantum definitions are only supported when serviceProtocol is LOGICAL_QSERVICE")));
        Map<String, String> mixedProtocolMetadata = mixedProtocolCatalog.deploymentValidationPlansByCategory()
                .get("QUANTUM_STORE").metadata();
        assertEquals("0/1", mixedProtocolMetadata.get("quantumServiceProtocolBoundaryCoverage"));
        assertEquals("globalQuantum",
                mixedProtocolMetadata.get("invalidQuantumServiceProtocolBoundaryStoreNames"));
        assertTrue(mixedProtocolMetadata.get("quantumServiceProtocolBoundaryBindings")
                .contains(":applicationNamespaceDeclared=true"
                        + ":applicationDefinitionCount=1"
                        + ":compliant=false"
                        + ":action=removeLogicalQServiceProvisioningFromHttpProfile"));
    }

    @Test
    public void tccReservationMissingBindingActionsDescribePhysicalClosureSteps() {
        assertEquals("bindReservationSourceQuantumStore",
                LocalGeneratedServiceRuntime.tccReservationMissingBindingActions("sourcePhysicalQuantumStore"));
        assertEquals("bindReservationTargetAppDdlRepository",
                LocalGeneratedServiceRuntime.tccReservationMissingBindingActions("targetAppDdlRepository"));
        assertEquals("promoteReservationTargetToAppDdlPlatformColumn",
                LocalGeneratedServiceRuntime.tccReservationMissingBindingActions("targetAppDdlPlatformColumn"));
        assertEquals("closeQuantumToAppDdlPlatformColumnReservation",
                LocalGeneratedServiceRuntime.tccReservationMissingBindingActions(
                        "quantumToAppDdlPlatformColumnReservation"));
        assertEquals("resolveTccReservationPhysicalBinding",
                LocalGeneratedServiceRuntime.tccReservationMissingBindingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.tccReservationMissingBindingActions(""));
    }

    @Test
    public void objectRepositoryMissingActionsDescribeAppDdlClosureSteps() {
        assertEquals("bindObjectRepositoryToAppDdl",
                LocalGeneratedServiceRuntime.objectRepositoryMissingActions("appDdlRepository"));
        assertEquals("pushObjectRepositoryKeyColumnsToAppDdl",
                LocalGeneratedServiceRuntime.objectRepositoryMissingActions("appDdlColumnPushdown"));
        assertEquals("resolveObjectRepositoryPhysicalBinding",
                LocalGeneratedServiceRuntime.objectRepositoryMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.objectRepositoryMissingActions(""));
    }

    public static final class FakeSagaCoordinator implements SagaTransactionGroupCoordinator {
        private static String tenantId;
        private static boolean executed;
        private static int recoveryLimit;

        public FakeSagaCoordinator(String tenantId) {
            FakeSagaCoordinator.tenantId = tenantId;
        }

        @Override
        public void execute(SagaExecutionScope scope,
                            List<com.servpp.service.scenario.IExecutor> executors) {
            executed = true;
        }

        @Override
        public SagaRecoveryScanResult recoverPending(int limit) {
            recoveryLimit = limit;
            return new SagaRecoveryScanResult(
                    2, 1, 1, List.of("retry later"));
        }
    }

    private static final class FakeSagaService
            implements Service, TransactionalService {
        @Override
        public Object execute(Object... role) {
            return null;
        }

        @Override
        public String getName() {
            return "submitOrder";
        }

        @Override
        public String getTransactionResourceId() {
            return "salesOrder";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.SAGA;
        }
    }

    @Test
    public void transactionRetentionMissingActionsDescribeRetentionClosureSteps() {
        assertEquals("configureTransactionRetentionMillisByService",
                LocalGeneratedServiceRuntime.transactionRetentionMissingActions("serviceRetentionMillis"));
        assertEquals("configureTransactionRetentionMillisByResource",
                LocalGeneratedServiceRuntime.transactionRetentionMissingActions("resourceRetentionMillis"));
        assertEquals("configureTransactionArchiveRetentionMillisByResource",
                LocalGeneratedServiceRuntime.transactionRetentionMissingActions("resourceArchiveRetentionMillis"));
        assertEquals("createTransactionRetentionExecutionPlan",
                LocalGeneratedServiceRuntime.transactionRetentionMissingActions("execution"));
        assertEquals("createTransactionArchiveRetentionExecutionPlan",
                LocalGeneratedServiceRuntime.transactionRetentionMissingActions("archiveExecution"));
        assertEquals("configureTransactionSnapshotRetentionRepository",
                LocalGeneratedServiceRuntime.transactionRetentionMissingActions("retentionPhysicalConfig"));
        assertEquals("resolveTransactionSnapshotRetentionBinding",
                LocalGeneratedServiceRuntime.transactionRetentionMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.transactionRetentionMissingActions(""));
    }

    @Test
    public void appDdlTablePlatformMissingActionsDescribeTableClosureSteps() {
        assertEquals("promoteAppDdlPrimaryKeyToPlatformColumn",
                LocalGeneratedServiceRuntime.appDdlTablePlatformMissingActions("platformPrimaryKey"));
        assertEquals("promoteAppDdlIndexToPlatformColumn",
                LocalGeneratedServiceRuntime.appDdlTablePlatformMissingActions("platformIndex"));
        assertEquals("declareAppDdlPlatformColumn",
                LocalGeneratedServiceRuntime.appDdlTablePlatformMissingActions("platformColumn"));
        assertEquals("resolveAppDdlPlatformBinding",
                LocalGeneratedServiceRuntime.appDdlTablePlatformMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.appDdlTablePlatformMissingActions(""));
    }

    @Test
    public void physicalBackendMissingActionsDescribeAssignmentClosureSteps() {
        assertEquals("bindServiceToPhysicalBackend",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("assignmentResolution"));
        assertEquals("registerPhysicalBackendAdapter",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("adapterAvailability"));
        assertEquals("enableBackendTransactionSupport",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("transactionSupport"));
        assertEquals("bindServiceToPhysicalBackend",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("unresolved"));
        assertEquals("registerPhysicalBackendAdapter",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("adapterUnavailable"));
        assertEquals("enableBackendTransactionSupport",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("unsupportedTransaction"));
        assertEquals("configureLocalJdbcDatasource",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("localJdbcDatasource"));
        assertEquals("configureSeataApplicationId",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("seataApplicationId"));
        assertEquals("resolvePhysicalBackendBinding",
                LocalGeneratedServiceRuntime.physicalBackendMissingActions("physicalBackendBinding"));
        assertEquals("-", LocalGeneratedServiceRuntime.physicalBackendMissingActions(""));
    }

    @Test
    public void repositoryAdapterMissingActionsDescribePhysicalRepositorySteps() {
        assertEquals("configureJdbcObjectRepositoryAdapter",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("jdbcObjectRepository"));
        assertEquals("configureJsonSnapshotRepositoryAdapter",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("jsonSnapshotStore"));
        assertEquals("configureReferenceRepositoryAdapter",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("referenceStore"));
        assertEquals("enableRepositoryAppDdlTables",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("appDdlRepository"));
        assertEquals("configureRepositoryStorageTable",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("storageTable"));
        assertEquals("configureRepositoryTransactionHistoryTable",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("transactionHistoryTable"));
        assertEquals("configureRepositoryTransactionArchiveTable",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("transactionArchiveTable"));
        assertEquals("configureRepositoryDatasource",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("datasource"));
        assertEquals("configureRepositoryDriver",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("driver"));
        assertEquals("resolveRepositoryAdapterPhysicalBinding",
                LocalGeneratedServiceRuntime.repositoryAdapterMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.repositoryAdapterMissingActions(""));
    }

    @Test
    public void repositoryQueryMissingActionsDescribeAppDdlQueryClosureSteps() {
        assertEquals("declareRepositoryQueryAppDdlIndex",
                LocalGeneratedServiceRuntime.repositoryQueryMissingActions("appDdlIndexCoverage"));
        assertEquals("deriveRepositoryForeignKeyQueryFromAppDdl",
                LocalGeneratedServiceRuntime.repositoryQueryMissingActions("appDdlForeignKeyQuery"));
        assertEquals("deriveRepositoryFromAppDdl+pushRepositoryColumnsToAppDdl+clearRepositoryEscapeHatch",
                LocalGeneratedServiceRuntime.repositoryQueryMissingActions("replacementReady"));
        assertEquals("closeRepositoryQueryAppDdlSemantics",
                LocalGeneratedServiceRuntime.repositoryQueryMissingActions("appDdlRepositoryQueryClosure"));
        assertEquals("resolveRepositoryQuerySemanticBinding",
                LocalGeneratedServiceRuntime.repositoryQueryMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.repositoryQueryMissingActions(""));
    }

    @Test
    public void idempotencyMissingActionsDescribeRuntimeClosureSteps() {
        assertEquals("declareIdempotencyKeyInAppHeader",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("key"));
        assertEquals("bindIdempotentServiceToPhysicalBackend",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("physicalBinding"));
        assertEquals("registerIdempotencyPhysicalAdapter",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("adapterUnavailable"));
        assertEquals("enableAdapterTransactionSupport",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("unsupportedTransaction"));
        assertEquals("configureIdempotencyStoreName",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("storeName"));
        assertEquals("configureIdempotencyTableName",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("tableName"));
        assertEquals("configureIdempotencyDatasource",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("datasource"));
        assertEquals("configureIdempotencyDriver",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("driver"));
        assertEquals("completeServiceInternalIdempotencyContract",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("serviceInternalIdempotency"));
        assertEquals("configureFrameworkIdempotencyRuntimeStore",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("runtimeStore"));
        assertEquals("resolveIdempotencyRuntimeBinding",
                LocalGeneratedServiceRuntime.idempotencyMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.idempotencyMissingActions(""));
    }

    @Test
    public void quantumStoreMissingActionsDescribePhysicalQuantumSteps() {
        assertEquals("bindQuantumAttributesToPhysicalStore",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("attributeBacked"));
        assertEquals("configureJdbcQuantumStoreAdapter",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("jdbcQuantum"));
        assertEquals("configureQuantumStoreDatasource",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("datasource"));
        assertEquals("configureQuantumStoreDriver",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("driver"));
        assertEquals("configureQuantumJournalStore",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("journalStore"));
        assertEquals("configureQuantumJournalTable",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("journalTable"));
        assertEquals("configureQuantumValueTable",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("valueTable"));
        assertEquals("bindQuantumStoreToPhysicalBackend",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("backendBinding"));
        assertEquals("resolveQuantumStorePhysicalBinding",
                LocalGeneratedServiceRuntime.quantumStoreMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.quantumStoreMissingActions(""));
    }

    @Test
    public void currentContextMissingActionsDescribeSagaCurrentClosureSteps() {
        assertEquals("bindCurrentContextToSingleSagaParameter",
                LocalGeneratedServiceRuntime.currentContextMissingActions("resolvedFromSagaParameter"));
        assertEquals("disambiguateCurrentContextSagaParameter",
                LocalGeneratedServiceRuntime.currentContextMissingActions("ambiguousSagaParameter"));
        assertEquals("deriveCurrentContextKeyFromAppDdl",
                LocalGeneratedServiceRuntime.currentContextMissingActions("appDdlDerived"));
        assertEquals("pushCurrentContextObjectKeyToAppDdl",
                LocalGeneratedServiceRuntime.currentContextMissingActions("appDdlObjectKeyPushdown"));
        assertEquals("resolveCurrentSnapshotContextBinding",
                LocalGeneratedServiceRuntime.currentContextMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.currentContextMissingActions(""));
    }

    @Test
    public void sagaTransactionCallMissingActionsDescribeRecoveryClosureSteps() {
        assertEquals("registerSagaTargetService",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("registeredTarget"));
        assertEquals("makeTargetLocalAtomicUpdate",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("localAtomicUpdateTarget"));
        assertEquals("declareSnapshotArgumentRecovery",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("argumentSnapshot"));
        assertEquals("persistArgumentRecoveryEvidence",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("argumentRecoveryEvidence"));
        assertEquals("persistRollbackRecoveryEvidence",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("rollbackRecoveryEvidence"));
        assertEquals("closeSagaRecoveryEvidence",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("recoveryEvidenceClosure"));
        assertEquals("declareSnapshotArgumentRecovery",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("currentOnlyRecoveryEvidence"));
        assertEquals("declareSnapshotArgumentRecovery",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions(
                        "currentSnapshotCannotReplaceArgumentRecovery"));
        assertEquals("resolveSagaTransactionCallBinding",
                LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.sagaTransactionCallMissingActions(""));
    }

    @Test
    public void compensationEvidenceMissingActionsDescribeObjectRecoveryClosureSteps() {
        assertEquals("declareCompensationArgumentRecoveryEvidence",
                LocalGeneratedServiceRuntime.compensationEvidenceMissingActions("targetRecoveryEvidence"));
        assertEquals("deriveCompensationEvidenceFromAppDdl",
                LocalGeneratedServiceRuntime.compensationEvidenceMissingActions("appDdlDerivedEvidence"));
        assertEquals("pushCompensationEvidenceObjectKeyToAppDdl",
                LocalGeneratedServiceRuntime.compensationEvidenceMissingActions("appDdlObjectKeyPushdown"));
        assertEquals("resolveCompensationEvidenceBinding",
                LocalGeneratedServiceRuntime.compensationEvidenceMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.compensationEvidenceMissingActions(""));
    }

    @Test
    public void snapshotMissingActionsDescribeObjectKeyPushdownSteps() {
        assertEquals("pushSnapshotKeyToAppDdl",
                LocalGeneratedServiceRuntime.snapshotMissingActions("businessObjectAppDdlKeyPushdown"));
        assertEquals("pushSnapshotKeyToAppDdl",
                LocalGeneratedServiceRuntime.snapshotMissingActions("recoveryEvidenceAppDdlKeyPushdown"));
        assertEquals("resolveSnapshotBinding",
                LocalGeneratedServiceRuntime.snapshotMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.snapshotMissingActions(""));
    }

    @Test
    public void recoveryWorkItemMissingActionsDescribeOperationalClosureSteps() {
        assertEquals("declareSupportedRecoveryDecisions",
                LocalGeneratedServiceRuntime.recoveryWorkItemMissingActions("supportedDecisions"));
        assertEquals("exposeManualRecoveryEndpoint",
                LocalGeneratedServiceRuntime.recoveryWorkItemMissingActions("manualRecoveryEndpoint"));
        assertEquals("configureRecoveryScheduler",
                LocalGeneratedServiceRuntime.recoveryWorkItemMissingActions("schedulerConfiguration"));
        assertEquals("resolveRecoveryWorkItemBinding",
                LocalGeneratedServiceRuntime.recoveryWorkItemMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.recoveryWorkItemMissingActions(""));
    }

    @Test
    public void recoveryDeploymentMissingActionsDescribeRecoveryPlanClosureSteps() {
        assertEquals("enableTccRecoveryDeployment",
                LocalGeneratedServiceRuntime.recoveryDeploymentMissingActions("TCC"));
        assertEquals("enableRemoteCompensationRecoveryDeployment",
                LocalGeneratedServiceRuntime.recoveryDeploymentMissingActions("REMOTE_COMPENSATION"));
        assertEquals("resolveRecoveryDeploymentBinding",
                LocalGeneratedServiceRuntime.recoveryDeploymentMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.recoveryDeploymentMissingActions(""));
    }

    @Test
    public void remoteServiceMissingActionsDescribeReferenceAndExternalClosureSteps() {
        assertEquals("declarePhysicalRemoteAdapter",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("physicalRemote"));
        assertEquals("bindObjectRepositoryRemoteAdapter",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("referenceRemote"));
        assertEquals("declareReferenceRemoteObjectKey",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("referenceObjectKey"));
        assertEquals("bindReferenceRemoteObjectRepository",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("referenceRepositoryBinding"));
        assertEquals("enableReferenceRemoteAppDdlRepository",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("referenceRepositoryAppDdl"));
        assertEquals("configureReferenceRemoteStorageTable",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("referenceRepositoryStorage"));
        assertEquals("configureReferenceRemoteDatasource",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("referenceRepositoryDatasource"));
        assertEquals("configureReferenceRemoteDriver",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("referenceRepositoryDriver"));
        assertEquals("replaceReferenceRemoteAdapter+bindExternalDomainAdapter",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("realExternalRemote"));
        assertEquals("resolveRemoteServicePhysicalBinding",
                LocalGeneratedServiceRuntime.remoteServiceMissingActions("unknown"));
        assertEquals("", LocalGeneratedServiceRuntime.remoteServiceMissingActions(""));
    }

    private static JdbcDataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:local-runtime-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static class ThreeAttributeQuantumService implements Service, TransactionalService,
            QuantumServiceMetadataProvider, GeneratedServiceKindProvider {
        public Ledger rebalance(Ledger ledger) {
            return ledger;
        }

        @Override
        public Object execute(Object... role) {
            return role == null || role.length == 0 ? null : role[0];
        }

        @Override
        public String getName() {
            return "rebalance";
        }

        @Override
        public String getTransactionResourceId() {
            return "ledgerAccounting";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.QUANTUM;
        }

        @Override
        public List<String> getQuantumAttributeIds() {
            return List.of("Ledger.available", "Ledger.reserved", "Ledger.captured");
        }

        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_UPDATE;
        }
    }

    private static class PolymorphicInventoryService implements Service, ScopeMetadataProvider {
        public Inventory submit(Inventory inventory) {
            return inventory;
        }

        @Override
        public Object execute(Object... role) {
            return role == null || role.length == 0 ? null : role[0];
        }

        @Override
        public String getName() {
            return "submit";
        }

        @Override
        public boolean isLocalScope() {
            return true;
        }

        @Override
        public String getInputHeaderName() {
            return null;
        }

        @Override
        public String getOutputHeaderName() {
            return null;
        }

        @Override
        public List<String> getServiceParameterNames() {
            return List.of("inventory");
        }
    }

    private static class Inventory {
    }

    private static class Ledger {
    }

    private record Store(String name) {
        public String getName() {
            return name;
        }
    }

    private record Warehouse(String code) {
        public String getCode() {
            return code;
        }
    }

    private record Goods(String skuId) {
        public String getSkuId() {
            return skuId;
        }
    }
}
