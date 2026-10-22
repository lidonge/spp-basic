package com.servpp.service.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import com.servpp.service.AtomicService;
import com.servpp.service.DelegatingService;
import com.servpp.service.QuantumService;
import com.servpp.service.Scenario;
import com.servpp.service.Service;
import com.servpp.service.idempotency.BusinessIdempotencyManager;
import com.servpp.service.idempotency.JdbcBusinessIdempotencyStore;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.repository.RepositoryHydratingServiceProxy.HydrationRule;
import com.servpp.service.runtime.AppDdlCatalog;
import com.servpp.service.runtime.AppSemanticMigrationPlan;
import com.servpp.service.runtime.DeploymentValidationPlan;
import com.servpp.service.runtime.GeneratedServiceCatalog;
import com.servpp.service.runtime.GeneratedServiceKind;
import com.servpp.service.runtime.GeneratedServiceKindProvider;
import com.servpp.service.runtime.HttpJsonRemoteServiceAdapter;
import com.servpp.service.runtime.LocalJdbcPhysicalBackendAdapter;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.runtime.LocalPhysicalRuntime;
import com.servpp.service.runtime.ObjectRepositoryRemoteServiceAdapter;
import com.servpp.service.runtime.ObjectSnapshotPlan;
import com.servpp.service.runtime.PhysicalBackendAdapter;
import com.servpp.service.runtime.PhysicalBackendAssignment;
import com.servpp.service.runtime.PhysicalBackendPlan;
import com.servpp.service.runtime.PhysicalQuantumPlan;
import com.servpp.service.runtime.PhysicalRuntimeCatalog;
import com.servpp.service.runtime.QuantumAccessPlan;
import com.servpp.service.runtime.QuantumServiceMetadataProvider;
import com.servpp.service.runtime.RecoveryWorkItem;
import com.servpp.service.runtime.RecoveryWorkItemDecision;
import com.servpp.service.runtime.RecoveryWorkItemScanResult;
import com.servpp.service.runtime.RemoteCompensationRecoveryDecision;
import com.servpp.service.runtime.RemoteCompensationRuleProvider;
import com.servpp.service.runtime.RemoteCompensationPolicyProvider;
import com.servpp.service.runtime.RemoteGeneratedService;
import com.servpp.service.runtime.RemoteServiceAdapter;
import com.servpp.service.runtime.RepositoryQueryPlan;
import com.servpp.service.runtime.ReflectiveSeataGlobalTransactionBridge;
import com.servpp.service.runtime.RoleIdentityContext;
import com.servpp.service.runtime.RoleParticipantPlan;
import com.servpp.service.runtime.SagaTransactionCallProvider;
import com.servpp.service.runtime.ScopeMetadataProvider;
import com.servpp.service.runtime.SeataPhysicalBackendAdapter;
import com.servpp.service.runtime.SeataTransactionBridge;
import com.servpp.service.runtime.TccRecoveryCandidate;
import com.servpp.service.runtime.TccRecoveryDecision;
import com.servpp.service.runtime.TccRecoveryScanResult;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.transaction.CompensationRuleProvider;
import com.servpp.service.transaction.ArgumentAwareTransactionParticipant;
import com.servpp.service.transaction.BusinessTraceContext;
import com.servpp.service.transaction.DefaultTccTransactionParticipant;
import com.servpp.service.transaction.DefaultTransactionManager;
import com.servpp.service.transaction.InMemorySagaLogStore;
import com.servpp.service.transaction.JdbcSagaLogStore;
import com.servpp.service.transaction.JdbcTccBranchStore;
import com.servpp.service.transaction.JdbcTccReservationStore;
import com.servpp.service.transaction.SagaLogManager;
import com.servpp.service.transaction.TccBranchRecord;
import com.servpp.service.transaction.TccBranchStatus;
import com.servpp.service.transaction.TccLifecycleRuleProvider;
import com.servpp.service.transaction.TccReservationParticipant;
import com.servpp.service.transaction.TccReservationRecord;
import com.servpp.service.transaction.TccRecoveryPayloadCodec;
import com.servpp.service.transaction.TccReservationRule;
import com.servpp.service.transaction.TccReservationRuleProvider;
import com.servpp.service.transaction.TccReservationStatus;
import com.servpp.service.transaction.TransactionContext;
import com.servpp.service.transaction.TransactionMode;
import com.servpp.service.transaction.TransactionParticipant;
import com.servpp.service.transaction.TransactionalService;

public class JdbcObjectSnapshotStoreTest {
    @Test
    public void savesAndListsObjectSnapshots() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> "json:" + value);

        store.save("SalesOrder", "SO-1", "paid");
        store.save("CreditAccount", "C-1", "available");

        assertEquals(2, store.findAll().size());
        assertEquals("CreditAccount", store.findAll().get(0).objectType());
        assertEquals("C-1", store.findAll().get(0).objectId());
        assertEquals("json:available", store.findAll().get(0).payloadJson());
        assertEquals("SalesOrder", store.findAll().get(1).objectType());
    }

    @Test
    public void findsObjectSnapshotByTypeAndId() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> "json:" + value);

        store.save("SalesOrder", "SO-1", "paid");

        ObjectSnapshot snapshot = store.find("SalesOrder", "SO-1").orElseThrow();
        assertEquals("SalesOrder", snapshot.objectType());
        assertEquals("SO-1", snapshot.objectId());
        assertEquals("json:paid", snapshot.payloadJson());
        assertEquals(0, store.find("SalesOrder", "SO-2").stream().count());
    }

    @Test
    public void findsObjectSnapshotsByType() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> "json:" + value);

        store.save("AuditSnapshot", "REQ-2", "second");
        store.save("SalesOrder", "SO-1", "paid");
        store.save("AuditSnapshot", "REQ-1", "first");

        List<ObjectSnapshot> snapshots = store.findByType("AuditSnapshot");
        assertEquals(2, snapshots.size());
        assertEquals("REQ-1", snapshots.get(0).objectId());
        assertEquals("json:first", snapshots.get(0).payloadJson());
        assertEquals("REQ-2", snapshots.get(1).objectId());
        assertEquals(0, store.findByType("MissingSnapshot").size());
    }

    @Test
    public void transactionSnapshotHistoryKeepsMultipleEntriesForSameTrace() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> "json:" + value);

        store.save("TransactionSnapshot", "REQ-1", "reserveCredit");
        store.save("TransactionSnapshot", "REQ-1", "markOrderPaid");

        assertEquals("json:markOrderPaid", store.find("TransactionSnapshot", "REQ-1").orElseThrow().payloadJson());
        List<ObjectSnapshot> history = store.findByType("TransactionSnapshot");
        assertEquals(2, history.size());
        assertEquals("REQ-1", history.get(0).objectId());
        assertEquals("json:reserveCredit", history.get(0).payloadJson());
        assertEquals("REQ-1", history.get(1).objectId());
        assertEquals("json:markOrderPaid", history.get(1).payloadJson());
    }

    @Test
    public void localRuntimeFiltersTransactionSnapshotsByResourceAndPhase() {
        LocalGeneratedServiceRuntime runtime = LocalGeneratedServiceRuntime.jsonSnapshots(
                h2DataSource(), new ObjectMapper(), type -> null);

        runtime.saveObjectSnapshot("TransactionSnapshot", "REQ-1", Map.of(
                "traceId", "REQ-1",
                "resourceId", "salesOrder",
                "phase", "COMMIT"));
        runtime.saveObjectSnapshot("TransactionSnapshot", "REQ-2", Map.of(
                "traceId", "REQ-2",
                "resourceId", "creditAccounting",
                "phase", "AFTER"));
        runtime.saveObjectSnapshot("SalesOrder", "SO-1", Map.of("orderNo", "SO-1"));

        assertEquals(2, runtime.findTransactionSnapshots().size());
        assertEquals("REQ-1", runtime.findTransactionSnapshotsByResource("salesOrder").get(0).objectId());
        assertEquals("REQ-2", runtime.findTransactionSnapshotsByPhase("AFTER").get(0).objectId());
        assertEquals("REQ-1", runtime.findTransactionSnapshots("salesOrder", "COMMIT").get(0).objectId());
        assertEquals(0, runtime.findTransactionSnapshots("salesOrder", "AFTER").size());
    }

    @Test
    public void transactionSnapshotHistoryCanBeQueriedByStructuredIndexes() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        store.save("TransactionSnapshot", "REQ-1", Map.of(
                "traceId", "REQ-1",
                "transactionId", "TX-1",
                "resourceId", "salesOrder",
                "phase", "COMMIT"));
        store.save("TransactionSnapshot", "REQ-1", Map.of(
                "traceId", "REQ-1",
                "transactionId", "TX-2",
                "resourceId", "reserveCredit",
                "phase", "AFTER"));
        store.save("TransactionSnapshot", "REQ-2", Map.of(
                "traceId", "REQ-2",
                "transactionId", "TX-3",
                "resourceId", "salesOrder",
                "phase", "AFTER"));

        assertEquals(2, store.findTransactionHistory("REQ-1", null, null).size());
        assertEquals("REQ-2", store.findTransactionHistory(null, "salesOrder", "AFTER").get(0).objectId());
        assertEquals("REQ-1", store.findTransactionHistory("REQ-1", "salesOrder", "COMMIT").get(0).objectId());
        assertEquals(0, store.findTransactionHistory("REQ-2", "reserveCredit", null).size());
    }

    @Test
    public void transactionSnapshotHistoryCanBePrunedWithoutRemovingCurrentSnapshot() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> "json:" + value);

        store.save("TransactionSnapshot", "REQ-1", "reserveCredit");
        store.save("TransactionSnapshot", "REQ-1", "markOrderPaid");
        store.save("SalesOrder", "SO-1", "paid");

        assertEquals(2, store.findTransactionHistory().size());
        assertEquals(2, store.pruneTransactionHistoryBefore(Instant.now().plusSeconds(1)));

        assertEquals(0, store.findTransactionHistory().size());
        assertEquals("json:markOrderPaid", store.find("TransactionSnapshot", "REQ-1").orElseThrow().payloadJson());
        assertTrue(store.find("SalesOrder", "SO-1").isPresent());
    }

    @Test
    public void transactionSnapshotHistoryCanBeArchivedBeforePruning() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        store.save("TransactionSnapshot", "REQ-1", Map.of(
                "traceId", "REQ-1",
                "transactionId", "TX-1",
                "resourceId", "salesOrder",
                "phase", "COMMIT"));
        store.save("TransactionSnapshot", "REQ-1", Map.of(
                "traceId", "REQ-1",
                "transactionId", "TX-2",
                "resourceId", "reserveCredit",
                "phase", "AFTER"));
        store.save("TransactionSnapshot", "REQ-2", Map.of(
                "traceId", "REQ-2",
                "transactionId", "TX-3",
                "resourceId", "salesOrder",
                "phase", "AFTER"));

        assertEquals(3, store.archiveTransactionHistoryBefore(Instant.now().plusSeconds(1)));

        assertEquals(0, store.findTransactionHistory().size());
        assertEquals(3, store.findArchivedTransactionHistory().size());
        assertEquals(1, store.findArchivedTransactionHistory("REQ-1", "salesOrder", "COMMIT").size());
        assertEquals("REQ-2", store.findArchivedTransactionHistory(null, "salesOrder", "AFTER")
                .get(0).objectId());
        assertEquals("TransactionSnapshot", store.find("TransactionSnapshot", "REQ-1")
                .orElseThrow().objectType());
    }

    @Test
    public void transactionSnapshotHistoryCanBeArchivedByResourceBeforePruning() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        store.save("TransactionSnapshot", "REQ-1", Map.of(
                "traceId", "REQ-1",
                "transactionId", "TX-1",
                "resourceId", "salesOrder",
                "phase", "COMMIT"));
        store.save("TransactionSnapshot", "REQ-2", Map.of(
                "traceId", "REQ-2",
                "transactionId", "TX-2",
                "resourceId", "reserveCredit",
                "phase", "AFTER"));
        store.save("TransactionSnapshot", "REQ-3", Map.of(
                "traceId", "REQ-3",
                "transactionId", "TX-3",
                "resourceId", "salesOrder",
                "phase", "AFTER"));

        assertEquals(2, store.archiveTransactionHistoryBefore("salesOrder", Instant.now().plusSeconds(1)));

        assertEquals(1, store.findTransactionHistory().size());
        assertEquals("REQ-2", store.findTransactionHistory(null, "reserveCredit", null).get(0).objectId());
        assertEquals(2, store.findArchivedTransactionHistory(null, "salesOrder", null).size());
        assertEquals(0, store.findArchivedTransactionHistory(null, "reserveCredit", null).size());
        assertTrue(store.find("TransactionSnapshot", "REQ-1").isPresent());
    }

    @Test
    public void transactionSnapshotHistoryCanRetainLatestEntriesForTrace() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> "json:" + value);

        store.save("TransactionSnapshot", "REQ-1", "reserveCredit");
        store.save("TransactionSnapshot", "REQ-1", "markOrderPaid");
        store.save("TransactionSnapshot", "REQ-1", "emitEvent");
        store.save("TransactionSnapshot", "REQ-2", "otherTrace");

        assertEquals(1, store.retainLatestTransactionHistory("REQ-1", 2));

        List<ObjectSnapshot> req1History = store.findTransactionHistory("REQ-1", null, null);
        assertEquals(2, req1History.size());
        assertEquals("json:markOrderPaid", req1History.get(0).payloadJson());
        assertEquals("json:emitEvent", req1History.get(1).payloadJson());
        assertEquals(1, store.findTransactionHistory("REQ-2", null, null).size());
        assertEquals("json:emitEvent", store.find("TransactionSnapshot", "REQ-1").orElseThrow().payloadJson());
    }

    @Test
    public void localRuntimeCanApplyTransactionSnapshotRetention() {
        LocalGeneratedServiceRuntime runtime = LocalGeneratedServiceRuntime.jsonSnapshots(
                h2DataSource(), new ObjectMapper(), type -> null);

        runtime.saveObjectSnapshot("TransactionSnapshot", "REQ-1", Map.of("traceId", "REQ-1", "phase", "AFTER"));
        runtime.saveObjectSnapshot("TransactionSnapshot", "REQ-1", Map.of("traceId", "REQ-1", "phase", "COMMIT"));
        runtime.saveObjectSnapshot("TransactionSnapshot", "REQ-2", Map.of("traceId", "REQ-2", "phase", "AFTER"));

        assertEquals(1, runtime.retainLatestTransactionSnapshots("REQ-1", 1));
        assertEquals(1, runtime.findTransactionSnapshots(null, "COMMIT").size());
        assertEquals(0, runtime.pruneTransactionSnapshotsBefore(Instant.EPOCH));
        assertEquals(2, runtime.findTransactionSnapshots().size());
        assertTrue(runtime.findTransactionSnapshots().stream()
                .anyMatch(snapshot -> "REQ-1".equals(snapshot.objectId())));
    }

    @Test
    public void localRuntimeCanArchiveTransactionSnapshots() {
        LocalGeneratedServiceRuntime runtime = LocalGeneratedServiceRuntime.jsonSnapshots(
                h2DataSource(), new ObjectMapper(), type -> null);

        runtime.saveObjectSnapshot("TransactionSnapshot", "REQ-1", Map.of(
                "traceId", "REQ-1",
                "resourceId", "salesOrder",
                "phase", "COMMIT"));
        runtime.saveObjectSnapshot("TransactionSnapshot", "REQ-2", Map.of(
                "traceId", "REQ-2",
                "resourceId", "reserveCredit",
                "phase", "AFTER"));

        assertEquals(1, runtime.archiveTransactionSnapshotsBefore("salesOrder", Instant.now().plusSeconds(1)));

        assertEquals(1, runtime.findTransactionSnapshots().size());
        assertEquals(1, runtime.findArchivedTransactionSnapshots().size());
        assertEquals(1, runtime.findArchivedTransactionSnapshotsByTrace("REQ-1").size());
        assertEquals("REQ-1", runtime.findArchivedTransactionSnapshots("salesOrder", "COMMIT")
                .get(0).objectId());
        assertEquals(0, runtime.pruneArchivedTransactionSnapshotsBefore(Instant.EPOCH));
        assertEquals(1, runtime.pruneArchivedTransactionSnapshotsBefore("salesOrder", Instant.now().plusSeconds(1)));
        assertEquals(0, runtime.findArchivedTransactionSnapshots().size());
        assertEquals("REQ-2", runtime.findTransactionSnapshotsByPhase("AFTER")
                .get(0).objectId());
        assertTrue(runtime.findTransactionSnapshot("REQ-1").isPresent());
    }

    @Test
    public void compositeObjectIdEncodesMultipleStableKeyValues() {
        String objectId = CompositeObjectId.encode(List.of("Main Store", "SKU.100", "P/100"));

        assertTrue(objectId.startsWith("spp-composite:"));
        assertEquals(List.of("Main Store", "SKU.100", "P/100"),
                CompositeObjectId.decode(objectId, 3));
        assertTrue(CompositeObjectId.decode(objectId, 2).isEmpty());
        assertTrue(CompositeObjectId.decode("plain-id", 3).isEmpty());
    }

    @Test
    public void jsonRepositoryReadsAndHydratesSnapshots() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);

        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));

        SnapshotObject loaded = repository.require("SnapshotObject", "C-1", SnapshotObject.class);
        assertEquals("Alice", loaded.name());

        MutableSnapshotObject target = new MutableSnapshotObject();
        target.id = "C-1";
        repository.hydrate("SnapshotObject", "C-1", target);
        assertEquals("C-1", target.id);
        assertEquals("Alice", target.name);
    }

    @Test
    public void repositoryHydratingProxyReadsSnapshotBeforeInvokingQueryMethod() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        TestQueryService delegate = new TestQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, repository,
                List.of(new HydrationRule("querySnapshot", 0,
                        "SnapshotObject", "id", "snapshotObject")));
        assertSame(delegate, ((DelegatingService) service).getDelegateService());

        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertEquals("Alice", delegate.seenName);
        assertEquals("Alice", query.name);
        assertEquals("Alice", response.snapshotObject.name);
    }

    @Test
    public void repositoryHydratingProxyReadsNestedObjectIdPath() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        repository.save("SnapshotObject", "C-NESTED", new SnapshotObject("C-NESTED", "Nested Alice"));
        TestQueryService delegate = new TestQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, repository,
                List.of(new HydrationRule("querySnapshot", 0,
                        "SnapshotObject", "customer.id", "snapshotObject")));

        NestedMutableSnapshotObject query = new NestedMutableSnapshotObject();
        query.customer = new SnapshotObject("C-NESTED", "lookup");
        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertEquals("C-NESTED", query.id);
        assertEquals("Nested Alice", delegate.seenName);
        assertEquals("Nested Alice", response.snapshotObject.name);
        assertEquals("C-NESTED", query.customer.id());
    }

    @Test
    public void repositoryHydratingProxyReadsCompositeObjectIdForMultiKeyHydrate() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        String objectId = CompositeObjectId.encode(List.of("MAIN", "SKU-100"));
        MutableSnapshotObject stored = new MutableSnapshotObject();
        stored.id = "MAIN";
        stored.name = "SKU-100";
        repository.save("SnapshotObject", objectId, stored);
        TestQueryService delegate = new TestQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, repository,
                List.of(new HydrationRule("querySnapshot", List.of(
                        new HydrationRule.KeyTarget(0, "SnapshotObject", "id"),
                        new HydrationRule.KeyTarget(0, "SnapshotObject", "name")),
                        "snapshotObject", null)));

        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "MAIN";
        query.name = "SKU-100";
        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertEquals("SKU-100", delegate.seenName);
        assertEquals("SKU-100", response.snapshotObject.name);
    }

    @Test
    public void repositoryHydratingProxyWritesCollectionResponseFromFilteredRepositoryQuery() {
        AppDdlProduct product = new AppDdlProduct();
        product.productId = "P-100";
        AppDdlGoods probe = new AppDdlGoods();
        probe.product = product;
        AppDdlGoods firstGoods = new AppDdlGoods();
        firstGoods.skuId = "SKU-100";
        AppDdlGoods secondGoods = new AppDdlGoods();
        secondGoods.skuId = "SKU-200";
        CollectionQueryObjectRepository repository =
                new CollectionQueryObjectRepository(List.of(firstGoods, secondGoods));
        GoodsCollectionQueryService delegate = new GoodsCollectionQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, repository,
                List.of(new HydrationRule("queryGoods", 0,
                        "AppDdlGoods", "product.productId", "goods")));

        GoodsCollectionResponse response = ((GoodsCollectionQuery) service).queryGoods(probe);

        assertSame(probe, delegate.seenProbe);
        assertEquals("AppDdlGoods", repository.seenObjectType);
        assertEquals("product.productId", repository.seenKeyPath);
        assertEquals("P-100", repository.seenKeyValue);
        assertEquals(2, response.goods.length);
        assertEquals("SKU-100", response.goods[0].skuId);
        assertEquals("SKU-200", response.goods[1].skuId);
    }

    @Test
    public void repositoryHydratingProxyCanQueryCollectionFromForeignArgumentKey() {
        AppDdlProduct product = new AppDdlProduct();
        product.productId = "P-100";
        AppDdlGoods firstGoods = new AppDdlGoods();
        firstGoods.skuId = "SKU-100";
        AppDdlGoods secondGoods = new AppDdlGoods();
        secondGoods.skuId = "SKU-200";
        CollectionQueryObjectRepository repository =
                new CollectionQueryObjectRepository(List.of(firstGoods, secondGoods));
        ProductGoodsCollectionQueryService delegate = new ProductGoodsCollectionQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, repository,
                List.of(new HydrationRule("queryGoods", "AppDdlGoods", List.of(
                        new HydrationRule.KeyTarget(0, "AppDdlProduct", "productId", "product.productId")),
                        "goods", null)));

        GoodsCollectionResponse response = ((ProductGoodsCollectionQuery) service).queryGoods(product);

        assertSame(product, delegate.seenProduct);
        assertEquals("AppDdlGoods", repository.seenObjectType);
        assertEquals("product.productId", repository.seenKeyPath);
        assertEquals("P-100", repository.seenKeyValue);
        assertEquals(2, response.goods.length);
        assertEquals("SKU-100", response.goods[0].skuId);
        assertEquals("SKU-200", response.goods[1].skuId);
    }

    @Test
    public void repositoryHydratingProxyFiltersCollectionResponseWithCompositeKeys() {
        AppDdlStore main = new AppDdlStore();
        main.name = "MAIN";
        AppDdlGoods firstGoods = new AppDdlGoods();
        firstGoods.skuId = "SKU-100";
        AppDdlGoods secondGoods = new AppDdlGoods();
        secondGoods.skuId = "SKU-200";
        AppDdlInventory matching = new AppDdlInventory();
        matching.store = main;
        matching.goods = firstGoods;
        matching.safeQty = 3;
        AppDdlInventory otherSku = new AppDdlInventory();
        otherSku.store = main;
        otherSku.goods = secondGoods;
        otherSku.safeQty = 7;
        AppDdlInventory probe = new AppDdlInventory();
        probe.store = main;
        probe.goods = firstGoods;

        InventoryCollectionQueryObjectRepository repository =
                new InventoryCollectionQueryObjectRepository(List.of(matching, otherSku));
        InventoryCollectionQueryService delegate = new InventoryCollectionQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, repository,
                List.of(new HydrationRule("queryInventory", List.of(
                        new HydrationRule.KeyTarget(0, "AppDdlInventory", "store.name"),
                        new HydrationRule.KeyTarget(0, "AppDdlInventory", "goods.skuId")),
                        "inventory", null)));

        InventoryCollectionResponse response = ((InventoryCollectionQuery) service).queryInventory(probe);

        assertSame(probe, delegate.seenProbe);
        assertEquals("AppDdlInventory", repository.seenObjectType);
        assertEquals(List.of("store.name", "goods.skuId"), repository.seenKeyPaths);
        assertEquals(List.of("MAIN", "SKU-100"), repository.seenKeyValues);
        assertEquals(1, response.inventory.size());
        assertEquals("SKU-100", response.inventory.get(0).goods.skuId);
        assertEquals(Integer.valueOf(3), response.inventory.get(0).safeQty);
    }

    @Test
    public void repositoryHydratingProxyResolvesRoleIdentityFromRuntimePerformers() {
        AppDdlStore main = new AppDdlStore();
        main.name = "MAIN";
        AppDdlGoods firstGoods = new AppDdlGoods();
        firstGoods.skuId = "SKU-100";
        AppDdlGoods secondGoods = new AppDdlGoods();
        secondGoods.skuId = "SKU-200";
        AppDdlInventory matching = new AppDdlInventory();
        matching.store = main;
        matching.goods = firstGoods;
        matching.safeQty = 3;
        AppDdlInventory otherSku = new AppDdlInventory();
        otherSku.store = main;
        otherSku.goods = secondGoods;
        otherSku.safeQty = 7;

        InventoryCollectionQueryObjectRepository repository =
                new InventoryCollectionQueryObjectRepository(List.of(matching, otherSku));
        StoreGoodsInventoryCollectionQueryService delegate = new StoreGoodsInventoryCollectionQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, () -> repository,
                List.of(new HydrationRule("queryInventory", "AppDdlInventory", List.of(
                        new HydrationRule.KeyTarget(0, "AppDdlStore", "store.name"),
                        new HydrationRule.KeyTarget(1, "AppDdlGoods", "goods.skuId")),
                        "inventory", null, true, "APP_DDL_ROLE_TAKE")),
                (roleType, arguments) -> "AppDdlInventory".equals(roleType)
                        ? Optional.of(new RepositoryHydratingServiceProxy.RoleIdentity(
                                roleType,
                                List.of("store.name", "goods.skuId"),
                                List.of(((AppDdlStore) arguments[0]).name, ((AppDdlGoods) arguments[1]).skuId),
                                List.of("store_name", "goods_skuid"),
                                "PERFORMER",
                                true))
                        : Optional.empty());

        InventoryCollectionResponse response =
                ((StoreGoodsInventoryCollectionQuery) service).queryInventory(main, firstGoods);

        assertSame(main, delegate.seenStore);
        assertSame(firstGoods, delegate.seenGoods);
        assertEquals("AppDdlInventory", repository.seenObjectType);
        assertEquals(List.of("store.name", "goods.skuId"), repository.seenKeyPaths);
        assertEquals(List.of("MAIN", "SKU-100"), repository.seenKeyValues);
        assertEquals(1, response.inventory.size());
        assertEquals("SKU-100", response.inventory.get(0).goods.skuId);
        assertEquals(Integer.valueOf(3), response.inventory.get(0).safeQty);
    }

    @Test
    public void repositoryHydratingProxyIgnoresRoleIdentityForAppDdlPrimaryKeyQueries() {
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        repository.save("SnapshotObject", "WRONG", new SnapshotObject("WRONG", "Wrong"));
        TestQueryService delegate = new TestQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, () -> repository,
                List.of(new HydrationRule("querySnapshot", 0, "SnapshotObject", "id",
                        "snapshotObject", null, true, "APP_DDL_PRIMARY_KEY")),
                (roleType, arguments) -> "SnapshotObject".equals(roleType)
                        ? Optional.of(new RepositoryHydratingServiceProxy.RoleIdentity(
                                roleType,
                                List.of("performer.id"),
                                List.of("WRONG"),
                                List.of("performer_id"),
                                "ROLE_CONTEXT",
                                true))
                        : Optional.empty());

        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertEquals("Alice", query.name);
        assertEquals("Alice", response.snapshotObject.name);
    }

    @Test
    public void repositoryHydratingProxyUsesRoleIdentityForAppDdlRoleTakePrimaryKeyQueries() {
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        AppDdlInventory stored = new AppDdlInventory();
        stored.availableQty = 20;
        stored.safeQty = 3;
        repository.save("AppDdlInventory", CompositeObjectId.encode(List.of("MAIN", "SKU-100")), stored);
        InventoryCollectionQueryService delegate = new InventoryCollectionQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, () -> repository,
                List.of(new HydrationRule("queryInventory", "AppDdlInventory", List.of(
                        new HydrationRule.KeyTarget(0, "AppDdlInventory", "store.name"),
                        new HydrationRule.KeyTarget(0, "AppDdlInventory", "goods.skuId")),
                        null, null, true, "APP_DDL_ROLE_TAKE_PRIMARY_KEY")),
                (roleType, arguments) -> "AppDdlInventory".equals(roleType)
                        ? Optional.of(new RepositoryHydratingServiceProxy.RoleIdentity(
                                roleType,
                                List.of("store.name", "goods.skuId"),
                                List.of("MAIN", "SKU-100"),
                                List.of("store_name", "goods_skuid"),
                                "ROLE_CONTEXT",
                                true))
                        : Optional.empty());

        AppDdlInventory query = new AppDdlInventory();
        ((InventoryCollectionQuery) service).queryInventory(query);

        assertSame(query, delegate.seenProbe);
        assertEquals(Integer.valueOf(20), query.availableQty);
        assertEquals(Integer.valueOf(3), query.safeQty);
    }

    @Test
    public void repositoryHydratingProxyRejectsNullResponseWhenWritingTarget() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        TestQueryService delegate = new NullResponseQueryService();
        Service service = RepositoryHydratingServiceProxy.decorate(delegate, repository,
                List.of(new HydrationRule("querySnapshot", 0,
                        "SnapshotObject", "id", "snapshotObject")));

        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ((TestQuery) service).querySnapshot(query));
        assertTrue(error.getMessage().contains(
                "Repository query querySnapshot returned null response for target snapshotObject"));
        assertEquals("Alice", query.name);
    }

    @Test
    public void repositoryHydratingProxyWrapsMissingSnapshotLookup() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        Service service = RepositoryHydratingServiceProxy.decorate(new TestQueryService(),
                repository, List.of(new HydrationRule("querySnapshot", 0,
                        "SnapshotObject", "id", "snapshotObject")));

        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-404";
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ((TestQuery) service).querySnapshot(query));
        assertTrue(error.getMessage().contains(
                "Repository query querySnapshot cannot hydrate SnapshotObject:C-404"));
        assertTrue(error.getCause().getMessage().contains("SnapshotObject not found: C-404"));
    }

    @Test
    public void repositoryHydratingProxyWrapsIncompatibleResponseWriter() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        Service service = RepositoryHydratingServiceProxy.decorate(new IncompatibleResponseQueryService(),
                repository, List.of(new HydrationRule("querySnapshot", 0,
                        "SnapshotObject", "id", "snapshotObject")));

        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ((IncompatibleResponseQuery) service).querySnapshot(query));
        assertTrue(error.getMessage().contains(
                "Cannot write repository query response with snapshotObject"));
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertEquals("Alice", query.name);
    }

    @Test
    public void generatedRuntimeDecoratesRepositoryHydrationAndObjectSnapshots() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store, repository, type -> null);

        Service service = runtime.decorate(new SnapshotQueryService());
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";

        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);
        assertEquals("Alice", query.name);
        assertEquals("Alice", response.snapshotObject.name);

        ArgumentAwareTransactionParticipant participant =
                (ArgumentAwareTransactionParticipant) ((TransactionalService) service).getTransactionParticipant();
        participant.setArguments(new Object[] { query });
        participant.afterExecute(null);

        ObjectSnapshot snapshot = store.find("SnapshotObject", "C-1").orElseThrow();
        assertEquals("{\"id\":\"C-1\",\"name\":\"Alice\"}", snapshot.payloadJson());
    }

    @Test
    public void generatedRuntimeCanHydrateQueriesThroughGenericObjectRepository() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store, repository, type -> null);

        Service service = runtime.decorate(new SnapshotQueryService());
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";

        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertSame(repository, runtime.getRepository());
        assertEquals("Alice", query.name);
        assertEquals("Alice", response.snapshotObject.name);
    }

    @Test
    public void decoratedRepositoryHydrationUsesLatestInstalledRepository() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store, repository, type -> null);
        Service service = runtime.decorate(new SnapshotQueryService());

        JdbcObjectSnapshotStore installedStore = new JdbcObjectSnapshotStore(h2DataSource(),
                value -> writeJson(value), "spp_object_snapshot_runtime");
        runtime.installJsonSnapshotRepository(installedStore, new ObjectMapper());
        runtime.getRepository().save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";

        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertEquals("Alice", query.name);
        assertEquals("Alice", response.snapshotObject.name);
        assertTrue(repository.find("SnapshotObject", "C-1").isEmpty());
    }

    @Test
    public void generatedRuntimePersistsSnapshotsThroughGenericObjectRepository() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store, repository, type -> null);

        Service service = runtime.decorate(new SnapshotQueryService());
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-2";
        query.name = "Bob";
        ArgumentAwareTransactionParticipant participant =
                (ArgumentAwareTransactionParticipant) ((TransactionalService) service).getTransactionParticipant();

        participant.setArguments(new Object[] { query });
        participant.afterExecute(null);

        assertSame(repository, runtime.getRepository());
        assertTrue(store.findAll().isEmpty());
        assertSame(query, repository.find("SnapshotObject", "C-2").orElseThrow());
        assertSame(query, runtime.getRepository().require("SnapshotObject", "C-2", MutableSnapshotObject.class));
    }

    @Test
    public void decoratedObjectSnapshotsUseLatestInstalledRepository() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store, repository, type -> null);
        Service service = runtime.decorate(new SnapshotQueryService());
        JdbcObjectSnapshotStore installedStore = new JdbcObjectSnapshotStore(h2DataSource(),
                value -> writeJson(value), "spp_object_snapshot_runtime");
        runtime.installJsonSnapshotRepository(installedStore, new ObjectMapper());
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-2";
        query.name = "Bob";
        ArgumentAwareTransactionParticipant participant =
                (ArgumentAwareTransactionParticipant) ((TransactionalService) service).getTransactionParticipant();

        participant.setArguments(new Object[] { query });
        participant.afterExecute(null);

        assertTrue(repository.find("SnapshotObject", "C-2").isEmpty());
        assertEquals("Bob", runtime.getRepository()
                .require("SnapshotObject", "C-2", MutableSnapshotObject.class).name);
    }

    @Test
    public void decoratedObjectSnapshotsPersistRestoredArgumentsAfterRollback() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> writeJson(value));
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, new ObjectMapper());
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store, repository, type -> null);
        Service mutating = runtime.decorate(new MutatingSnapshotService());
        MutableSnapshotObject object = new MutableSnapshotObject();
        object.id = "C-ROLLBACK";
        object.name = "original";

        assertThrows(IllegalStateException.class, () -> DefaultTransactionManager.getInstance().execute(List.of(
                executor((AtomicService) mutating, object),
                executor(new FailingSnapshotService(), object)
        )));

        assertEquals("original", object.name);
        ObjectSnapshot snapshot = store.find("SnapshotObject", "C-ROLLBACK").orElseThrow();
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("\"name\":\"original\""));
    }

    @Test
    public void localGeneratedRuntimeValidatesDeclaredObjectSnapshotRepositoryAdapter() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.physicalRuntime().repositoryAdapters().size());
        RepositoryQueryPlan queryPlan = catalog.repositoryQueryPlans().stream()
                .filter(plan -> plan.serviceName().equals("querySnapshot"))
                .findFirst().orElseThrow();
        assertEquals("EXPLICIT_ARGUMENT", queryPlan.source());
        assertFalse(queryPlan.appDdlDerived());
        assertTrue(queryPlan.escapeHatch());
        assertEquals(null, queryPlan.repositoryName());
        assertEquals("objectStore", queryPlan.resolvedRepositoryName());
        assertEquals("objectSnapshot", queryPlan.repositoryAdapterName());
        assertFalse(queryPlan.repositoryAppDdlTables());
        assertFalse(queryPlan.appDdlColumnPushdown());
        assertTrue(catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void explicitRepositoryArgumentRemainsEscapeHatchWhenItMatchesAppDdlKey() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=SNAPSHOT_OBJECT\n"
                        + "table.SNAPSHOT_OBJECT.category=entities\n"
                        + "table.SNAPSHOT_OBJECT.businessType=SnapshotObject\n"
                        + "table.SNAPSHOT_OBJECT.columns=id\n"
                        + "table.SNAPSHOT_OBJECT.primaryKeys=id\n"
                        + "table.SNAPSHOT_OBJECT.column.id.type=VARCHAR\n"
                        + "table.SNAPSHOT_OBJECT.column.id.precision=64\n"
                        + "table.SNAPSHOT_OBJECT.column.id.scale=0\n"
                        + "table.SNAPSHOT_OBJECT.column.id.nullable=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-explicit-repository-appddl;DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        RepositoryQueryPlan queryPlan = catalog.repositoryQueryPlans().stream()
                .filter(plan -> plan.serviceName().equals("querySnapshot"))
                .findFirst().orElseThrow();

        assertEquals("EXPLICIT_ARGUMENT", queryPlan.source());
        assertFalse(queryPlan.appDdlDerived());
        assertTrue(queryPlan.escapeHatch());
        assertEquals(List.of("id"), queryPlan.repositoryColumns());
        assertFalse(queryPlan.appDdlColumnPushdown());
        assertTrue(catalog.repositoryValidationErrors().toString(), catalog.repositoryValidationErrors().isEmpty());
        assertTrue(catalog.appSemanticMigrationWarnings().toString(),
                catalog.appSemanticMigrationWarnings().stream().anyMatch(warning -> warning.contains(
                        "Repository query querySnapshot still uses explicit repository argument paths id")));
    }

    @Test
    public void appDdlSchemaDeploymentPlanExposesMissingPhysicalRepositoryBindings() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=SNAPSHOT_OBJECT\n"
                        + "table.SNAPSHOT_OBJECT.category=entities\n"
                        + "table.SNAPSHOT_OBJECT.businessType=SnapshotObject\n"
                        + "table.SNAPSHOT_OBJECT.columns=id,status\n"
                        + "table.SNAPSHOT_OBJECT.primaryKeys=id\n"
                        + "table.SNAPSHOT_OBJECT.indexNames=snapshotByStatus\n"
                        + "table.SNAPSHOT_OBJECT.index.snapshotByStatus.paths=status\n"
                        + "table.SNAPSHOT_OBJECT.column.id.type=VARCHAR\n"
                        + "table.SNAPSHOT_OBJECT.column.id.precision=64\n"
                        + "table.SNAPSHOT_OBJECT.column.id.scale=0\n"
                        + "table.SNAPSHOT_OBJECT.column.id.nullable=false\n"
                        + "table.SNAPSHOT_OBJECT.column.id.objectBacked=false\n"
                        + "table.SNAPSHOT_OBJECT.column.status.type=VARCHAR\n"
                        + "table.SNAPSHOT_OBJECT.column.status.precision=32\n"
                        + "table.SNAPSHOT_OBJECT.column.status.scale=0\n"
                        + "table.SNAPSHOT_OBJECT.column.status.nullable=true\n"
                        + "table.SNAPSHOT_OBJECT.column.status.objectBacked=false\n")
                        .getBytes(StandardCharsets.UTF_8)));

        DeploymentValidationPlan plan = runtime.catalogGeneratedServices().deploymentValidationPlans().stream()
                .filter(item -> "APP_DDL_SCHEMA".equals(item.category()))
                .findFirst()
                .orElseThrow();

        assertFalse(plan.passed());
        assertEquals(1, plan.errorCount());
        assertTrue(plan.errors().toString(), plan.errors().get(0).contains(
                "APP DDL table SNAPSHOT_OBJECT for SnapshotObject is not physicalized"));
        assertTrue(plan.errors().toString(), plan.errors().get(0).contains(
                "actions=bindAppDdlTableToPhysicalRepository+enableRepositoryAppDdlTables"));
        assertEquals("1", plan.metadata().get("missingPhysicalizedTableCount"));
        assertEquals("SNAPSHOT_OBJECT", plan.metadata().get("missingPhysicalizedTableNames"));
        assertEquals("0/1", plan.metadata().get("physicalizedTableCoverage"));
        assertTrue(plan.metadata().get("missingTablePhysicalRepositoryBindings")
                .contains("SNAPSHOT_OBJECT:category=entities:businessType=SnapshotObject"
                        + ":physicalized=false"));
        assertTrue(plan.metadata().get("missingTablePhysicalRepositoryBindings")
                .contains(":primaryKeys=id:indexes=-:namedIndexes=snapshotByStatus"));
        assertTrue(plan.metadata().get("missingTablePhysicalRepositoryBindings")
                .contains(":platformPrimaryKeys=id:objectBackedPrimaryKeys=-"));
        assertTrue(plan.metadata().get("missingTablePhysicalRepositoryBindings")
                .contains(":platformIndexPaths=status:objectBackedIndexPaths=-"
                        + ":platformColumns=id+status"));
        assertTrue(plan.metadata().get("missingTablePhysicalRepositoryBindings")
                .contains(":objectBackedPrimaryKeys=-:platformIndexPaths=status"
                        + ":objectBackedIndexPaths=-"));
        assertTrue(plan.metadata().get("missingTablePhysicalRepositoryBindings")
                .contains(":repository=-:adapter=-:storage=-:history=-:archive=-:datasource=-:driver=-"));
    }

    @Test
    public void generatedAppSemanticMigrationWarningsAreMergedAndDeduplicated() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=SNAPSHOT_OBJECT\n"
                        + "table.SNAPSHOT_OBJECT.category=entities\n"
                        + "table.SNAPSHOT_OBJECT.businessType=SnapshotObject\n"
                        + "table.SNAPSHOT_OBJECT.columns=id\n"
                        + "table.SNAPSHOT_OBJECT.primaryKeys=id\n"
                        + "table.SNAPSHOT_OBJECT.column.id.type=VARCHAR\n"
                        + "table.SNAPSHOT_OBJECT.column.id.precision=64\n"
                        + "table.SNAPSHOT_OBJECT.column.id.scale=0\n"
                        + "table.SNAPSHOT_OBJECT.column.id.nullable=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String warning = "APP DDL table SNAPSHOT_OBJECT primary key paths id are still object-backed;"
                + " keep them only when they are business facts,"
                + " otherwise migrate technical identity to platform primary keys.";
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("warning.count=2\n"
                        + "warning.0=" + warning + "\n"
                        + "warning.1=Generated-only warning\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(List.of(warning, "Generated-only warning"), runtime.getGeneratedAppSemanticMigrationWarnings());
        assertEquals(1, catalog.appSemanticMigrationWarnings().stream()
                .filter(warning::equals)
                .count());
        assertTrue(catalog.appSemanticMigrationWarnings().toString(),
                catalog.appSemanticMigrationWarnings().contains("Generated-only warning"));
    }

    @Test
    public void legacyContractSignerMigrationPlansAreSuppressed() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=CONTRACT_SIGNER_FIELD\n"
                        + "plan.0.targetKind=CONTRACT\n"
                        + "plan.0.targetName=SalesOrder\n"
                        + "plan.0.tableName=SALES_ORDER\n"
                        + "plan.0.objectType=SalesOrder\n"
                        + "plan.0.paths=SalesOrder.buyer:Customer\n"
                        + "plan.0.participantCategories=references\n"
                        + "plan.0.recommendation=Legacy contract signer field.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        DeploymentValidationPlan migrationPlan = catalog.deploymentValidationPlans().stream()
                .filter(plan -> "APP_SEMANTIC_MIGRATION".equals(plan.category()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, runtime.getGeneratedAppSemanticMigrationPlans().size());
        assertEquals("CONTRACT_SIGNER_FIELD",
                runtime.getGeneratedAppSemanticMigrationPlans().get(0).migrationType());
        assertTrue(catalog.contractSignerPlans().isEmpty());
        assertEquals("1", migrationPlan.metadata().get("suppressedGeneratedMigrationPlanCount"));
        assertEquals("CONTRACT_SIGNER_FIELD",
                migrationPlan.metadata().get("suppressedGeneratedMigrationTypes"));
        assertEquals("LEGACY_CONTRACT_FIELD",
                migrationPlan.metadata().get("suppressedGeneratedMigrationReasons"));
        assertTrue(migrationPlan.metadata().get("suppressedGeneratedMigrationReasonBindings")
                .contains("CONTRACT_SIGNER_FIELD:target=CONTRACT/SalesOrder"));
        assertEquals("0", migrationPlan.metadata().get("activeGeneratedContractSignerFieldMigrationCount"));
        assertEquals("0", migrationPlan.metadata().get("contractSignerPlanCount"));
        assertEquals("0", migrationPlan.metadata().get("contractSignerRoleOnlyErrorCount"));
    }

    @Test
    public void legacyAsyncModifierMigrationPlanUsesCanonicalAction() {
        AppSemanticMigrationPlan plan = new AppSemanticMigrationPlan("LEGACY_ASYNC_MODIFIER", "SCOPE_SERVICE",
                "releaseInventory", null, "releaseInventory", null,
                null, null, false, false, null, null, false, false, false, false,
                null, null, false, false, List.of(), List.of(), false, List.of(), List.of("asycn"),
                "Replace legacy asycn modifier with async.");
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=LEGACY_ASYNC_MODIFIER\n"
                        + "plan.0.targetKind=SCOPE_SERVICE\n"
                        + "plan.0.targetName=releaseInventory\n"
                        + "plan.0.serviceName=releaseInventory\n"
                        + "plan.0.paths=asycn\n"
                        + "plan.0.recommendation=Replace legacy asycn modifier with async.\n")
                        .getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals("replaceLegacyAsycnWithAsync", plan.migrationAction());
        assertEquals("replaceLegacyAsycnWithAsync",
                runtime.getGeneratedAppSemanticMigrationPlans().get(0).migrationAction());
        assertTrue(catalog.appSemanticMigrationWarnings().toString(),
                catalog.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("Scope service releaseInventory")
                                && warning.contains("legacy async modifier spelling asycn")));
    }

    @Test
    public void generatedRoleFieldMigrationPlansExposeRuntimeWarnings() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=2\n"
                        + "plan.0.migrationType=ROLE_PERFORMER_FIELD\n"
                        + "plan.0.targetKind=ROLE\n"
                        + "plan.0.targetName=Goods\n"
                        + "plan.0.tableName=GOODS\n"
                        + "plan.0.objectType=Goods\n"
                        + "plan.0.paths=Goods.product\n"
                        + "plan.0.recommendation=Role fields duplicate take performers.\n"
                        + "plan.1.migrationType=ROLE_OBJECT_FIELD\n"
                        + "plan.1.targetKind=ROLE\n"
                        + "plan.1.targetName=SalesItem\n"
                        + "plan.1.tableName=SALES_ITEM\n"
                        + "plan.1.objectType=SalesItem\n"
                        + "plan.1.paths=SalesItem.goods:Goods\n"
                        + "plan.1.recommendation=Role fields should describe role facts only.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(runtime.getGeneratedAppSemanticMigrationPlans().stream()
                .anyMatch(plan -> "ROLE_PERFORMER_FIELD".equals(plan.migrationType())
                        && "removeRolePerformerField+useTakePerformerIdentity".equals(plan.migrationAction())));
        assertTrue(runtime.getGeneratedAppSemanticMigrationPlans().stream()
                .anyMatch(plan -> "ROLE_OBJECT_FIELD".equals(plan.migrationType())
                        && "removeRoleObjectField+useRuntimeRoleParticipantIdentity".equals(plan.migrationAction())));
        assertTrue(catalog.appSemanticMigrationWarnings().toString(),
                catalog.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains(
                                "Role Goods still declares performer fields Goods.product")
                                && warning.contains("take(...) runtime bindings")));
        assertTrue(catalog.appSemanticMigrationWarnings().toString(),
                catalog.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains(
                                "Role SalesItem still declares object participant fields SalesItem.goods:Goods")
                                && warning.contains("role fields should describe role facts only")));
    }

    @Test
    public void generatedQuantumServiceInternalProtocolPlanExposesRuntimeWarning() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=QUANTUM_SERVICE_INTERNAL_PROTOCOL\n"
                        + "plan.0.targetKind=QUANTUM_ACCESS\n"
                        + "plan.0.targetName=reserveCredit\n"
                        + "plan.0.serviceName=reserveCredit\n"
                        + "plan.0.adapterName=quantumService\n"
                        + "plan.0.adapterRole=QUANTUM_SERVICE\n"
                        + "plan.0.paths=CreditAccount.availableCredit,SalesOrder.occupiedAmount\n"
                        + "plan.0.participantCategories=objectSnapshot,objectCompensation,frameworkIdempotency\n"
                        + "plan.0.snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE\n"
                        + "plan.0.recoveryRole=QUANTUM_SERVICE_INTERNAL\n"
                        + "plan.0.recommendation=Move object recovery expectations to the quantum service.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals("idempotencyBoundary=QUANTUM_SERVICE_INTERNAL+reversalExecution=INPUT_NEGATIVE_DELTA"
                        + "+snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE",
                runtime.getGeneratedAppSemanticMigrationPlans().get(0).migrationAction());
        assertTrue(catalog.appSemanticMigrationWarnings().toString(),
                catalog.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("Quantum service reserveCredit")
                                && warning.contains("pure quantum service adapter quantumService")
                                && warning.contains("quantum service internal protocol")));
    }

    @Test
    public void strictRoleOnlyServiceBoundaryRejectsGeneratedNonRoleParticipantAtDeploymentTime() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("config.strictRoleOnlyServiceBoundary=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=SERVICE_NON_ROLE_PARTICIPANT\n"
                        + "plan.0.targetKind=SERVICE_PARTICIPANT\n"
                        + "plan.0.serviceName=openSalesOrder\n"
                        + "plan.0.paths=openSalesOrder.order:SalesOrder\n"
                        + "plan.0.participantCategories=contracts\n"
                        + "plan.0.recommendation=Move Contract context behind Role participants.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        DeploymentValidationPlan migrationPlan = catalog.deploymentValidationPlans().stream()
                .filter(plan -> "APP_SEMANTIC_MIGRATION".equals(plan.category()))
                .findFirst()
                .orElseThrow();

        assertTrue(migrationPlan.metadata().get("strictAppMigrationEffectiveEnabledKeys"),
                migrationPlan.metadata().get("strictAppMigrationEffectiveEnabledKeys")
                        .contains("strictRoleOnlyServiceBoundary"));
        assertEquals("1", migrationPlan.metadata().get("serviceParticipantRoleOnlyErrorCount"));
        assertTrue(migrationPlan.metadata().get("serviceParticipantRoleOnlyErrors"),
                migrationPlan.metadata().get("serviceParticipantRoleOnlyErrors").contains(
                        "Service/scenario openSalesOrder participant openSalesOrder.order:SalesOrder"
                                + " (SalesOrder) must be a Role participant"));
        assertTrue(migrationPlan.metadata().get("serviceParticipantRoleOnlyMigrationActionBindings"),
                migrationPlan.metadata().get("serviceParticipantRoleOnlyMigrationActionBindings").contains(
                        "openSalesOrder:SalesOrder:category=contracts"
                                + ":action=CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO"));
        assertTrue(migrationPlan.metadata().get("serviceParticipantRoleOnlyBlockedReasons"),
                migrationPlan.metadata().get("serviceParticipantRoleOnlyBlockedReasons").contains(
                        "MISSING_ROLE_PARTICIPANT"));
        assertEquals(migrationPlan.errors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("must be a Role participant"));
        assertTrue(error.getMessage().contains("openSalesOrder.order:SalesOrder"));
    }

    @Test
    public void strictAppMigrationEnablesRoleOnlyBusinessModelUnlessLeafIsDisabled() {
        LocalGeneratedServiceRuntime strictRuntime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        strictRuntime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("config.strictAppMigration= true \n")
                        .getBytes(StandardCharsets.UTF_8)));
        strictRuntime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=ROLE_OBJECT_FIELD\n"
                        + "plan.0.targetKind=ROLE\n"
                        + "plan.0.targetName=Goods\n"
                        + "plan.0.tableName=GOODS\n"
                        + "plan.0.objectType=Goods\n"
                        + "plan.0.paths=Goods.product:Product\n"
                        + "plan.0.recommendation=Role fields should describe role facts only.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog strictCatalog = strictRuntime.catalogGeneratedServices();
        DeploymentValidationPlan strictPlan = strictCatalog.deploymentValidationPlans().stream()
                .filter(plan -> "APP_SEMANTIC_MIGRATION".equals(plan.category()))
                .findFirst()
                .orElseThrow();

        assertTrue(strictPlan.metadata().get("strictAppMigrationEffectiveEnabledKeys"),
                strictPlan.metadata().get("strictAppMigrationEffectiveEnabledKeys")
                        .contains("strictRoleOnlyBusinessModel"));
        assertTrue(strictPlan.metadata().get("strictAppMigrationConfigBindings"),
                strictPlan.metadata().get("strictAppMigrationConfigBindings")
                        .contains("strictAppMigration=true"));
        assertEquals("1", strictPlan.metadata().get("roleObjectFieldMigrationCount"));
        assertEquals("1", strictPlan.metadata().get("appSemanticMigrationErrorCount"));
        assertTrue(strictPlan.metadata().get("appSemanticMigrationErrors"),
                strictPlan.metadata().get("appSemanticMigrationErrors").contains(
                        "Role Goods declares object participant fields Goods.product:Product"));
        assertEquals(strictPlan.errors(), strictCatalog.deploymentValidationErrors());
        IllegalStateException strictError = assertThrows(IllegalStateException.class,
                strictRuntime::requireValidGeneratedServiceDeployment);
        assertTrue(strictError.getMessage().contains("Role Goods declares object participant fields"));

        LocalGeneratedServiceRuntime overrideRuntime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        overrideRuntime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("config.strictAppMigration=true\n"
                        + "config.strictRoleOnlyBusinessModel=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        overrideRuntime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=ROLE_OBJECT_FIELD\n"
                        + "plan.0.targetKind=ROLE\n"
                        + "plan.0.targetName=Goods\n"
                        + "plan.0.tableName=GOODS\n"
                        + "plan.0.objectType=Goods\n"
                        + "plan.0.paths=Goods.product:Product\n"
                        + "plan.0.recommendation=Role fields should describe role facts only.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog overrideCatalog = overrideRuntime.catalogGeneratedServices();
        DeploymentValidationPlan overridePlan = overrideCatalog.deploymentValidationPlans().stream()
                .filter(plan -> "APP_SEMANTIC_MIGRATION".equals(plan.category()))
                .findFirst()
                .orElseThrow();

        assertTrue(overridePlan.metadata().get("strictAppMigrationEffectiveDisabledKeys"),
                overridePlan.metadata().get("strictAppMigrationEffectiveDisabledKeys")
                        .contains("strictRoleOnlyBusinessModel"));
        assertEquals("1", overridePlan.metadata().get("roleObjectFieldMigrationCount"));
        assertEquals("0", overridePlan.metadata().get("appSemanticMigrationErrorCount"));
        assertTrue(overrideCatalog.deploymentValidationErrors().isEmpty());
        overrideRuntime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void strictAppMigrationRuntimeUsesPlaceholderDefaults() {
        LocalGeneratedServiceRuntime strictRuntime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        strictRuntime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("config.strictRoleOnlyBusinessModel=${spp.strict.role.model:true}\n")
                        .getBytes(StandardCharsets.UTF_8)));
        strictRuntime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=ROLE_OBJECT_FIELD\n"
                        + "plan.0.targetKind=ROLE\n"
                        + "plan.0.targetName=Goods\n"
                        + "plan.0.tableName=GOODS\n"
                        + "plan.0.objectType=Goods\n"
                        + "plan.0.paths=Goods.product:Product\n"
                        + "plan.0.recommendation=Role fields should describe role facts only.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog strictCatalog = strictRuntime.catalogGeneratedServices();
        DeploymentValidationPlan strictPlan = strictCatalog.deploymentValidationPlans().stream()
                .filter(plan -> "APP_SEMANTIC_MIGRATION".equals(plan.category()))
                .findFirst()
                .orElseThrow();

        assertTrue(strictPlan.metadata().get("strictAppMigrationEffectiveEnabledKeys"),
                strictPlan.metadata().get("strictAppMigrationEffectiveEnabledKeys")
                        .contains("strictRoleOnlyBusinessModel"));
        assertTrue(strictPlan.metadata().get("strictAppMigrationConfigBindings"),
                strictPlan.metadata().get("strictAppMigrationConfigBindings")
                        .contains("strictRoleOnlyBusinessModel=true"));
        assertEquals("1", strictPlan.metadata().get("appSemanticMigrationErrorCount"));
        IllegalStateException strictError = assertThrows(IllegalStateException.class,
                strictRuntime::requireValidGeneratedServiceDeployment);
        assertTrue(strictError.getMessage().contains("Role Goods declares object participant fields"));

        LocalGeneratedServiceRuntime overrideRuntime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        overrideRuntime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("config.strictAppMigration=${spp.strict.app:true}\n"
                        + "config.strictRoleOnlyBusinessModel=${spp.strict.role.model:false}\n")
                        .getBytes(StandardCharsets.UTF_8)));
        overrideRuntime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=ROLE_OBJECT_FIELD\n"
                        + "plan.0.targetKind=ROLE\n"
                        + "plan.0.targetName=Goods\n"
                        + "plan.0.tableName=GOODS\n"
                        + "plan.0.objectType=Goods\n"
                        + "plan.0.paths=Goods.product:Product\n"
                        + "plan.0.recommendation=Role fields should describe role facts only.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog overrideCatalog = overrideRuntime.catalogGeneratedServices();
        DeploymentValidationPlan overridePlan = overrideCatalog.deploymentValidationPlans().stream()
                .filter(plan -> "APP_SEMANTIC_MIGRATION".equals(plan.category()))
                .findFirst()
                .orElseThrow();

        assertTrue(overridePlan.metadata().get("strictAppMigrationEffectiveDisabledKeys"),
                overridePlan.metadata().get("strictAppMigrationEffectiveDisabledKeys")
                        .contains("strictRoleOnlyBusinessModel"));
        assertTrue(overridePlan.metadata().get("strictAppMigrationConfigBindings"),
                overridePlan.metadata().get("strictAppMigrationConfigBindings")
                        .contains("strictRoleOnlyBusinessModel=false"));
        assertEquals("0", overridePlan.metadata().get("appSemanticMigrationErrorCount"));
        assertTrue(overrideCatalog.deploymentValidationErrors().isEmpty());
        overrideRuntime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void strictQuantumServiceExternalImplementationRejectsLocalhostEndpoint() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("config.strictQuantumServiceExternalImplementation=true\n"
                        + "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.config.implementationStrategy=QUANTUM_SERVICE\n"
                        + "quantumStore.globalQuantum.config.accessMode=QUANTUM_SERVICE_ADAPTER\n"
                        + "quantumStore.globalQuantum.config.idempotencyMode=SERVICE_IDEMPOTENCY\n"
                        + "quantumStore.globalQuantum.config.reversalMode=NEGATIVE_DELTA\n"
                        + "quantumStore.globalQuantum.config.endpoint=http://localhost:8080/quantum\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        DeploymentValidationPlan migrationPlan = catalog.deploymentValidationPlans().stream()
                .filter(plan -> "APP_SEMANTIC_MIGRATION".equals(plan.category()))
                .findFirst()
                .orElseThrow();

        assertEquals("1", migrationPlan.metadata().get("quantumServiceContractOnlyCount"));
        assertEquals("globalQuantum", migrationPlan.metadata().get("quantumServiceContractOnlyStoreNames"));
        assertEquals("developmentEndpoint", migrationPlan.metadata().get("quantumServiceContractOnlyReasons"));
        assertEquals("1", migrationPlan.metadata().get("quantumServiceDevelopmentEndpointCount"));
        assertEquals("globalQuantum", migrationPlan.metadata().get("quantumServiceDevelopmentEndpointStoreNames"));
        assertEquals("0", migrationPlan.metadata().get("quantumServicePlaceholderEndpointCount"));
        assertEquals("", migrationPlan.metadata().get("quantumServicePlaceholderEndpointStoreNames"));
        assertEquals("0/1", migrationPlan.metadata()
                .get("quantumServiceExternalImplementationReadinessCoverage"));
        assertTrue(migrationPlan.metadata().get("quantumServiceExternalImplementationReadinessBindings"),
                migrationPlan.metadata().get("quantumServiceExternalImplementationReadinessBindings")
                        .contains("globalQuantum:externalImplementationReady=false:readiness=CONTRACT_ONLY"
                                + ":reason=developmentEndpoint"));
        assertEquals("1", migrationPlan.metadata()
                .get("quantumServiceExternalImplementationStrictErrorCount"));
        assertTrue(migrationPlan.metadata().get("quantumServiceExternalImplementationStrictErrors"),
                migrationPlan.metadata().get("quantumServiceExternalImplementationStrictErrors").contains(
                        "Quantum service store globalQuantum external implementation"
                                + " http://localhost:8080/quantum is not production ready"));
        assertTrue(migrationPlan.metadata().get("quantumServiceExternalImplementationStrictErrors"),
                migrationPlan.metadata().get("quantumServiceExternalImplementationStrictErrors")
                        .contains("replaceDevelopmentQuantumServiceEndpoint+bindProductionQuantumService"));
        assertEquals(migrationPlan.errors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains(
                "strictQuantumServiceExternalImplementation"));
        assertTrue(error.getMessage(), error.getMessage().contains("developmentEndpoint"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void deploymentValidationErrorsMergeEveryDeploymentPlanCategory() throws Exception {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        Method mergeValidationErrors = LocalGeneratedServiceRuntime.class.getDeclaredMethod(
                "mergeValidationErrors",
                List.class, List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class);
        mergeValidationErrors.setAccessible(true);

        List<String> errors = (List<String>) mergeValidationErrors.invoke(runtime,
                List.of(new DeploymentValidationPlan("SCOPE_HEADER", false, 1,
                        List.of("scope header deployment error"))),
                List.of("physical profile error"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("scope header deployment error"));

        assertEquals(List.of("physical profile error", "scope header deployment error"), errors);
    }

    @Test
    public void generatedAppSemanticMigrationBooleanPropertiesFailFastOnInvalidValues() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                        ("plan.count=1\n"
                                + "plan.0.migrationType=REMOTE_REFERENCE_ADAPTER\n"
                                + "plan.0.serviceName=queryCustomer\n"
                                + "plan.0.referenceImplementation=maybe\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog plan.0.referenceImplementation"
                        + " must be true or false, but was maybe"));
    }

    @Test
    public void generatedAppSemanticMigrationActionMustMatchRuntimeActionWhenPresent() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("plan.count=1\n"
                        + "plan.0.migrationType=LEGACY_ASYNC_MODIFIER\n"
                        + "plan.0.targetKind=SCOPE_SERVICE\n"
                        + "plan.0.targetName=releaseInventory\n"
                        + "plan.0.serviceName=releaseInventory\n"
                        + "plan.0.paths=asycn\n"
                        + "plan.0.migrationAction=replaceLegacyAsycnWithAsync\n"
                        + "plan.0.recommendation=Replace legacy asycn modifier with async.\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("replaceLegacyAsycnWithAsync",
                runtime.getGeneratedAppSemanticMigrationPlans().get(0).migrationAction());
    }

    @Test
    public void generatedAppSemanticMigrationActionMismatchFailsFast() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                        ("plan.count=1\n"
                                + "plan.0.migrationType=LEGACY_ASYNC_MODIFIER\n"
                                + "plan.0.targetKind=SCOPE_SERVICE\n"
                                + "plan.0.targetName=releaseInventory\n"
                                + "plan.0.serviceName=releaseInventory\n"
                                + "plan.0.paths=asycn\n"
                                + "plan.0.migrationAction=wrongAction\n"
                                + "plan.0.recommendation=Replace legacy asycn modifier with async.\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog plan.0.migrationAction"
                        + " must match runtime action replaceLegacyAsycnWithAsync, but was wrongAction"));
    }

    @Test
    public void generatedAppSemanticMigrationCountsFailFastOnNegativeValues() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                        "warning.count=-1\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog warning.count must be non-negative, but was -1"));
    }

    @Test
    public void generatedRepositoryReplacementSummaryCountsMustBeConsistent() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                        ("summary.repositoryQueryPlanCount=2\n"
                                + "summary.repositoryReplacementReadyCount=2\n"
                                + "summary.missingRepositoryReplacementReadyCount=1\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog repository replacement summary counts"
                        + " must satisfy ready + missing = total, but ready=2, missing=1, total=2"));
    }

    @Test
    public void generatedRoleParticipantBooleanPropertiesFailFastOnInvalidValues() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadRoleParticipantPlans(new ByteArrayInputStream(
                        ("roleParticipant.count=1\n"
                                + "roleParticipant.0.roleType=Inventory\n"
                                + "roleParticipant.0.performerTypes=Store,Goods\n"
                                + "roleParticipant.0.complete=maybe\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog roleParticipant.0.complete"
                        + " must be true or false, but was maybe"));
    }

    @Test
    public void generatedRoleParticipantCountsFailFastOnNegativeValues() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadRoleParticipantPlans(new ByteArrayInputStream(
                        "roleParticipant.count=-1\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog roleParticipant.count"
                        + " must be non-negative, but was -1"));
    }

    @Test
    public void generatedRoleParticipantPerformerCountMustMatchTypes() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadRoleParticipantPlans(new ByteArrayInputStream(
                        ("roleParticipant.count=1\n"
                                + "roleParticipant.0.roleType=Inventory\n"
                                + "roleParticipant.0.performerTypes=Store,Goods\n"
                                + "roleParticipant.0.performerCount=1\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog roleParticipant.0.performerCount"
                        + " must match performerTypes size 2, but was 1"));
    }

    @Test
    public void generatedRoleParticipantJointFlagMustMatchPerformerCount() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadRoleParticipantPlans(new ByteArrayInputStream(
                        ("roleParticipant.count=1\n"
                                + "roleParticipant.0.roleType=Inventory\n"
                                + "roleParticipant.0.performerTypes=Store,Goods\n"
                                + "roleParticipant.0.performerCount=2\n"
                                + "roleParticipant.0.jointPerformer=false\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated APP semantic migration catalog roleParticipant.0.jointPerformer"
                        + " must be true when performerCount is 2, but was false"));
    }

    @Test
    public void localGeneratedRuntimeRequiresTccReservationTargetPlatformColumn() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("reserveInventory="
                        + ReserveInventoryWithReservationService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.tccLifecycleValidationErrors().toString(),
                catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC reservation target attribute Inventory.lockedQty on service reserveInventory")
                        && error.contains("APP DDL platform column")));
        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC reservation target attribute Inventory.lockedQty on service reserveInventory")
                        && error.contains("APP DDL platform column")));
    }

    @Test
    public void localGeneratedRuntimeRejectsObjectBackedTccReservationTargetColumn() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=INVENTORY\n"
                        + "table.INVENTORY.category=roles\n"
                        + "table.INVENTORY.businessType=Inventory\n"
                        + "table.INVENTORY.columns=availableQty,lockedQty\n"
                        + "table.INVENTORY.primaryKeys=availableQty\n"
                        + "table.INVENTORY.column.availableQty.type=INTEGER\n"
                        + "table.INVENTORY.column.availableQty.precision=0\n"
                        + "table.INVENTORY.column.availableQty.scale=0\n"
                        + "table.INVENTORY.column.availableQty.nullable=true\n"
                        + "table.INVENTORY.column.lockedQty.type=INTEGER\n"
                        + "table.INVENTORY.column.lockedQty.precision=0\n"
                        + "table.INVENTORY.column.lockedQty.scale=0\n"
                        + "table.INVENTORY.column.lockedQty.nullable=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("reserveInventory="
                        + ReserveInventoryWithReservationService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.tccLifecycleValidationErrors().toString(),
                catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC reservation target attribute Inventory.lockedQty on service reserveInventory")
                        && error.contains("column lockedQty is object-backed")));
    }

    @Test
    public void localGeneratedRuntimeRequiresTccReservationSourceQuantumStore() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                inventoryAppDdlCatalogProperties().getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.table=spp_business_object\n"
                        + "repository.objectStore.config.appDdlTables=true\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:tcc-source-missing-quantum;"
                        + "DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("reserveInventory="
                        + ReserveInventoryWithReservationService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.tccLifecycleValidationErrors().toString(),
                catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC reservation source attribute Inventory.availableQty on service reserveInventory")
                        && error.contains("must be assigned to a PHY quantumStore")));
        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC reservation source attribute Inventory.availableQty on service reserveInventory")
                        && error.contains("must be assigned to a PHY quantumStore")));
        assertEquals(1, catalog.tccReservationPlans().size());
        assertFalse(catalog.tccReservationPlans().get(0).sourceQuantumAttribute());
        assertEquals("", catalog.tccReservationPlans().get(0).sourceQuantumStoreName());
        assertTrue(catalog.tccReservationPlans().get(0).targetAppDdlColumnPushdown());
    }

    @Test
    public void repositoryAdapterCanBeUsedOnlyByObjectSnapshots() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.physicalRuntime().repositoryAdapters().size());
        assertTrue(catalog.repositoryHydrationRules().isEmpty());
        assertEquals(1, catalog.objectSnapshotRules().get("releaseSnapshot").size());
        ObjectSnapshotPlan snapshotPlan = catalog.objectSnapshotPlans().stream()
                .filter(plan -> plan.serviceName().equals("releaseSnapshot"))
                .findFirst().orElseThrow();
        assertEquals("objectStore", snapshotPlan.resolvedRepositoryName());
        assertEquals("objectSnapshot", snapshotPlan.repositoryAdapterName());
        assertFalse(snapshotPlan.repositoryAppDdlTables());
        assertTrue(catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void objectSnapshotRepositoryAdapterRequiresJsonSnapshotRepositoryRuntime() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.repositoryAdapterValidationErrors().size());
        assertTrue(catalog.repositoryAdapterValidationErrors().get(0).contains(
                "adapter objectSnapshot requires JsonObjectSnapshotRepository"));
        assertEquals(catalog.repositoryAdapterValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("adapter objectSnapshot requires JsonObjectSnapshotRepository"));
    }

    @Test
    public void objectSnapshotRepositoryAdapterRejectsBlankDeclaredJdbcConfig() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n"
                        + "repository.objectStore.config.datasourceUrl=\n"
                        + "repository.objectStore.config.driverClassName=\n"
                        + "repository.objectStore.config.table=\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(3, catalog.repositoryAdapterValidationErrors().size());
        for (String key : List.of("datasourceUrl", "driverClassName", "table")) {
            assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                    catalog.repositoryAdapterValidationErrors().stream().anyMatch(error -> error.contains(
                            "Repository objectStore objectSnapshot adapter config " + key
                                    + " must not be blank when declared")));
        }
        assertEquals(catalog.repositoryAdapterValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("objectSnapshot adapter config datasourceUrl must not be blank"));
    }

    @Test
    public void objectSnapshotRepositoryAdapterRequiresDriverWhenDatasourceIsDeclared() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:spp-object;DB_CLOSE_DELAY=-1\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.repositoryAdapterValidationErrors().size());
        assertTrue(catalog.repositoryAdapterValidationErrors().get(0).contains(
                "Repository objectStore objectSnapshot adapter config driverClassName"
                        + " must be declared when datasourceUrl is declared"));
        assertEquals(catalog.repositoryAdapterValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("driverClassName must be declared"));
        assertTrue(!(runtime.getSnapshotStore().getDataSource() instanceof DriverManagerJdbcDataSource));
    }

    @Test
    public void jdbcObjectRepositoryAdapterValidatesTransactionRetentionConfig() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.transactionRetentionMode=ASYNC_ARCHIVE\n"
                        + "repository.objectStore.config.transactionRetentionMillisByResource=salesOrder:60000,\n"
                        + "repository.objectStore.config.transactionArchiveRetentionMillisByResource=:60000\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().stream().anyMatch(error -> error.contains(
                        "Repository objectStore jdbcObjectRepository adapter config transactionRetentionMode"
                                + " must be MANUAL_API or PHYSICAL_SCHEDULED_ARCHIVE")));
        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().stream().anyMatch(error -> error.contains(
                        "Repository objectStore jdbcObjectRepository adapter config"
                                + " transactionRetentionMillisByResource entries must use"
                                + " resourceName:positiveMillis")));
        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().stream().anyMatch(error -> error.contains(
                        "Repository objectStore jdbcObjectRepository adapter config"
                                + " transactionArchiveRetentionMillisByResource entries must use"
                                + " resourceName:positiveMillis")));
        assertEquals(catalog.repositoryAdapterValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void objectRepositoryAdapterAcceptsGenericObjectRepositoryRuntime() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.physicalRuntime().repositoryAdapters().size());
        assertEquals("objectRepository", catalog.physicalRuntime().repositoryAdapters().get(0).adapterName());
        assertTrue(catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void repositoryHydrationRuleCanBindDeclaredRepositoryAdapter() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + BoundRepositorySnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals("objectStore", catalog.repositoryHydrationRules()
                .get("querySnapshot").get(0).repositoryName());
        assertTrue(catalog.repositoryValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
    }

    @Test
    public void repositoryHydrationRuleRejectsMissingRepositoryAdapterBinding() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + MissingRepositoryBindingQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.repositoryValidationErrors().size());
        assertTrue(catalog.repositoryValidationErrors().get(0).contains(
                "references undefined repository adapter missingStore"));
        assertEquals(catalog.repositoryValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void repositoryHydrationRuleMustBindRepositoryWhenMultipleAdaptersExist() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore,customerStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "repository.customerStore.adapter=objectRepository\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.repositoryValidationErrors().size());
        assertTrue(catalog.repositoryValidationErrors().get(0).contains(
                "must declare repositoryName when no unique default repository adapter exists"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.repositoryValidationErrors()));
    }

    @Test
    public void repositoryHydrationRuleCanResolveDefaultRepositoryAmongMultipleAdapters() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore,customerStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "repository.objectStore.config.default=true\n"
                        + "repository.customerStore.adapter=objectRepository\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.repositoryValidationErrors().toString(), catalog.repositoryValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().toString(), catalog.deploymentValidationErrors().isEmpty());
        assertEquals(null, catalog.repositoryQueryPlans().get(0).repositoryName());
        assertEquals("objectStore", catalog.repositoryQueryPlans().get(0).resolvedRepositoryName());
    }

    @Test
    public void repositoryHydrationRuleMustMatchAppDdlBusinessTypeWhenTypedTablesAreEnabled() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=PRODUCT\n"
                        + "table.PRODUCT.category=entities\n"
                        + "table.PRODUCT.businessType=Product\n"
                        + "table.PRODUCT.columns=productId\n"
                        + "table.PRODUCT.primaryKeys=productId\n"
                        + "table.PRODUCT.column.productId.type=VARCHAR\n"
                        + "table.PRODUCT.column.productId.precision=64\n"
                        + "table.PRODUCT.column.productId.scale=0\n"
                        + "table.PRODUCT.column.productId.nullable=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-repo-appddl-missing-type;DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot="
                        + BoundRepositorySnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.repositoryValidationErrors().size());
        assertTrue(catalog.repositoryValidationErrors().get(0).contains(
                "uses object type SnapshotObject but repository objectStore enables appDdlTables"
                        + " without an APP DDL table for that business type"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.repositoryValidationErrors()));
    }

    @Test
    public void objectSnapshotRuleMustResolveRepositoryWhenMultipleAdaptersExist() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore,auditStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n"
                        + "repository.auditStore.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.snapshotValidationErrors().get(0).contains(
                "Object snapshot service releaseSnapshot rule for object type SnapshotObject"
                        + " must declare repositoryName"
                        + " when no unique default repository adapter exists"));
        assertEquals(null, catalog.objectSnapshotPlans().get(0).resolvedRepositoryName());
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.snapshotValidationErrors()));
    }

    @Test
    public void objectSnapshotRuleCanResolveDefaultRepositoryAmongMultipleAdapters() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore,auditStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n"
                        + "repository.objectStore.config.default=true\n"
                        + "repository.auditStore.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.snapshotValidationErrors().toString(), catalog.snapshotValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().toString(), catalog.deploymentValidationErrors().isEmpty());
        assertEquals(null, catalog.objectSnapshotPlans().get(0).repositoryName());
        assertEquals("objectStore", catalog.objectSnapshotPlans().get(0).resolvedRepositoryName());
    }

    @Test
    public void objectSnapshotRuleCanBindRepositoryWhenMultipleAdaptersExist() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore,auditStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n"
                        + "repository.auditStore.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("releaseSnapshot=" + BoundRepositorySnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.snapshotValidationErrors().toString(), catalog.snapshotValidationErrors().isEmpty());
        ObjectSnapshotPlan plan = catalog.objectSnapshotPlans().stream()
                .filter(item -> item.serviceName().equals("releaseSnapshot"))
                .findFirst().orElseThrow();
        assertEquals("objectStore", plan.repositoryName());
        assertEquals("objectStore", plan.resolvedRepositoryName());
        assertEquals("objectSnapshot", plan.repositoryAdapterName());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
    }

    @Test
    public void objectSnapshotRuleRejectsMissingRepositoryBinding() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("releaseSnapshot="
                        + MissingRepositoryBindingSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.snapshotValidationErrors().get(0).contains(
                "Object snapshot service releaseSnapshot rule for object type SnapshotObject"
                        + " references undefined repository adapter missingStore"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.snapshotValidationErrors()));
    }

    @Test
    public void objectSnapshotRuleMustMatchAppDdlBusinessTypeWhenTypedTablesAreEnabled() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=PRODUCT\n"
                        + "table.PRODUCT.category=entities\n"
                        + "table.PRODUCT.businessType=Product\n"
                        + "table.PRODUCT.columns=productId\n"
                        + "table.PRODUCT.primaryKeys=productId\n"
                        + "table.PRODUCT.column.productId.type=VARCHAR\n"
                        + "table.PRODUCT.column.productId.precision=64\n"
                        + "table.PRODUCT.column.productId.scale=0\n"
                        + "table.PRODUCT.column.productId.nullable=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-snapshot-appddl-missing-type;DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.snapshotValidationErrors().get(0).contains(
                "rule for object type SnapshotObject uses repository objectStore with appDdlTables"
                        + " but no APP DDL table exists for that business type"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.snapshotValidationErrors()));
    }

    @Test
    public void transactionSnapshotRuleDoesNotRequireAppDdlBusinessTable() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=PRODUCT\n"
                        + "table.PRODUCT.category=entities\n"
                        + "table.PRODUCT.businessType=Product\n"
                        + "table.PRODUCT.columns=productId\n"
                        + "table.PRODUCT.primaryKeys=productId\n"
                        + "table.PRODUCT.column.productId.type=VARCHAR\n"
                        + "table.PRODUCT.column.productId.precision=64\n"
                        + "table.PRODUCT.column.productId.scale=0\n"
                        + "table.PRODUCT.column.productId.nullable=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-tx-snapshot-appddl;DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("transactionSnapshot="
                        + TransactionSnapshotOnlyService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.snapshotValidationErrors().toString(), catalog.snapshotValidationErrors().isEmpty());
        ObjectSnapshotPlan plan = catalog.objectSnapshotPlans().stream()
                .filter(item -> item.serviceName().equals("transactionSnapshot"))
                .findFirst().orElseThrow();
        assertEquals("TransactionSnapshot", plan.objectType());
        assertEquals("objectStore", plan.resolvedRepositoryName());
        assertFalse(plan.appDdlObjectKeyPushdown());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
    }

    @Test
    public void transactionSnapshotEvidenceUsesTransactionResourceId() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        Service service = runtime.getGeneratedServiceRuntime().decorate(new TransactionSnapshotOnlyService());

        BusinessTraceContext.execute("REQ-TX-RESOURCE", () -> {
            new DefaultTransactionManager().execute(List.of(new IExecutor() {
                @Override
                public Service getService() {
                    return service;
                }

                @Override
                public void execute() {
                    MutableSnapshotObject value = new MutableSnapshotObject();
                    value.id = "SNAP-1";
                    value.name = "resource";
                    service.execute(value);
                }
            }));
            return null;
        });

        List<ObjectSnapshot> resourceHistory =
                runtime.findTransactionSnapshots("snapshotRelease", ObjectSnapshotRule.Timing.COMMIT.name());
        assertEquals(1, resourceHistory.size());
        assertEquals("REQ-TX-RESOURCE", resourceHistory.get(0).objectId());
        assertTrue(resourceHistory.get(0).payloadJson().contains("\"resourceId\":\"snapshotRelease\""));
        assertEquals(0, runtime.findTransactionSnapshots("transactionSnapshot",
                ObjectSnapshotRule.Timing.COMMIT.name()).size());
    }

    @Test
    public void explicitSnapshotArgumentRemainsEscapeHatchWhenItMatchesAppDdlKey() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=SNAPSHOT_OBJECT\n"
                        + "table.SNAPSHOT_OBJECT.category=entities\n"
                        + "table.SNAPSHOT_OBJECT.businessType=SnapshotObject\n"
                        + "table.SNAPSHOT_OBJECT.columns=id\n"
                        + "table.SNAPSHOT_OBJECT.primaryKeys=id\n"
                        + "table.SNAPSHOT_OBJECT.column.id.type=VARCHAR\n"
                        + "table.SNAPSHOT_OBJECT.column.id.precision=64\n"
                        + "table.SNAPSHOT_OBJECT.column.id.scale=0\n"
                        + "table.SNAPSHOT_OBJECT.column.id.nullable=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-explicit-snapshot-appddl;DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        ObjectSnapshotPlan plan = catalog.objectSnapshotPlans().stream()
                .filter(item -> item.serviceName().equals("releaseSnapshot"))
                .findFirst().orElseThrow();

        assertEquals("EXPLICIT_ARGUMENT", plan.source());
        assertFalse(plan.appDdlDerived());
        assertTrue(plan.escapeHatch());
        assertEquals("APP_DDL_PRIMARY_KEY", plan.objectKeySource());
        assertTrue(plan.objectKeyAppDdlDerived());
        assertFalse(plan.objectKeyEscapeHatch());
        assertEquals(List.of("id"), plan.objectKeyColumns());
        assertTrue(plan.appDdlObjectKeyPushdown());
        assertTrue(catalog.snapshotValidationErrors().toString(), catalog.snapshotValidationErrors().isEmpty());
        assertTrue(catalog.appSemanticMigrationWarnings().toString(),
                catalog.appSemanticMigrationWarnings().stream().anyMatch(warning -> warning.contains(
                        "Snapshot rule for service releaseSnapshot object SnapshotObject"
                                + " still uses explicit object key paths id")));
    }

    @Test
    public void appDdlSnapshotPlanKeepsAggregateMemberPathSeparateFromObjectKeyPath() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=CREDIT_ACCOUNT\n"
                        + "table.CREDIT_ACCOUNT.category=entities\n"
                        + "table.CREDIT_ACCOUNT.businessType=CreditAccount\n"
                        + "table.CREDIT_ACCOUNT.columns=creditAccountId,customer_id\n"
                        + "table.CREDIT_ACCOUNT.primaryKeys=creditAccountId\n"
                        + "table.CREDIT_ACCOUNT.indexNames=creditAccountByCustomer\n"
                        + "table.CREDIT_ACCOUNT.index.creditAccountByCustomer.paths=CreditAccount.customer.id\n"
                        + "table.CREDIT_ACCOUNT.column.creditAccountId.type=VARCHAR\n"
                        + "table.CREDIT_ACCOUNT.column.creditAccountId.precision=64\n"
                        + "table.CREDIT_ACCOUNT.column.creditAccountId.scale=0\n"
                        + "table.CREDIT_ACCOUNT.column.creditAccountId.nullable=false\n"
                        + "table.CREDIT_ACCOUNT.column.creditAccountId.objectBacked=false\n"
                        + "table.CREDIT_ACCOUNT.column.customer_id.type=VARCHAR\n"
                        + "table.CREDIT_ACCOUNT.column.customer_id.precision=64\n"
                        + "table.CREDIT_ACCOUNT.column.customer_id.scale=0\n"
                        + "table.CREDIT_ACCOUNT.column.customer_id.nullable=false\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-app-ddl-member-snapshot;DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("reserveCredit="
                        + AggregateMemberSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        ObjectSnapshotPlan plan = catalog.objectSnapshotPlans().stream()
                .filter(item -> item.serviceName().equals("reserveCredit"))
                .findFirst().orElseThrow();

        assertEquals("APP_DDL_NAMED_INDEX", plan.source());
        assertTrue(plan.appDdlDerived());
        assertFalse(plan.escapeHatch());
        assertEquals("creditAccountByCustomer", plan.appDdlIndexName());
        assertEquals(List.of("account.customer.id"), plan.objectIdPaths());
        assertEquals(List.of("customer.id"), plan.appDdlObjectKeyPaths());
        assertEquals("APP_DDL_NAMED_INDEX", plan.objectKeySource());
        assertEquals("creditAccountByCustomer", plan.objectKeyAppDdlIndexName());
        assertEquals(List.of("customer_id"), plan.objectKeyColumns());
        assertTrue(plan.appDdlObjectKeyPushdown());
        assertTrue(catalog.snapshotValidationErrors().toString(), catalog.snapshotValidationErrors().isEmpty());
    }

    @Test
    public void objectRepositoryAdapterCanRequireInjectedRepositoryImplementationClass() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "repository.objectStore.config.implementationClass="
                        + InMemoryObjectRepository.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void objectRepositoryAdapterRejectsMismatchedImplementationClass() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "repository.objectStore.config.implementationClass="
                        + JsonObjectSnapshotRepository.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.repositoryAdapterValidationErrors().size());
        assertTrue(catalog.repositoryAdapterValidationErrors().get(0).contains(
                "adapter objectRepository requires implementationClass "
                        + JsonObjectSnapshotRepository.class.getName()));
        assertEquals(catalog.repositoryAdapterValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("requires implementationClass"));
    }

    @Test
    public void objectRepositoryAdapterRejectsMissingImplementationClass() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        GeneratedServiceRuntime generatedRuntime = new GeneratedServiceRuntime(store, repository, type -> null);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(LocalPhysicalRuntime.jdbc(h2DataSource()),
                generatedRuntime);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "repository.objectStore.config.implementationClass=example.MissingRepository\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("querySnapshot=" + SnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.repositoryAdapterValidationErrors().size());
        assertTrue(catalog.repositoryAdapterValidationErrors().get(0).contains(
                "implementationClass example.MissingRepository is not available"));
        assertEquals(catalog.repositoryAdapterValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void repositoryAdapterValidationRejectsDuplicateRepositoryNames() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                        ("repositories=objectStore,objectStore\n"
                                + "repository.objectStore.adapter=objectSnapshot\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated physical runtime catalog repository declares duplicate value objectStore"));
    }

    @Test
    public void generatedRuntimeFactoryCreatesJsonSnapshotRuntime() {
        GeneratedServiceRuntime runtime =
                GeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.getObjectRepository().save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));

        Service service = runtime.decorate(new SnapshotQueryService());
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertEquals("Alice", response.snapshotObject.name);
        assertEquals("{\"id\":\"C-1\",\"name\":\"Alice\"}",
                runtime.getSnapshotStore().find("SnapshotObject", "C-1").orElseThrow().payloadJson());
    }

    @Test
    public void localGeneratedRuntimeInstallsJdbcReferenceStores() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.install();
        LocalPhysicalRuntime physicalRuntime = runtime.getPhysicalRuntime();

        assertSame(physicalRuntime.getQuantumJournalStore(), runtime.getQuantumJournalStore());
        assertSame(physicalRuntime.getIdempotencyStore(), runtime.getIdempotencyStore());
        assertSame(physicalRuntime.getSagaLogStore(), runtime.getSagaLogStore());
        assertSame(physicalRuntime.getTccBranchStore(), runtime.getTccBranchStore());
        assertSame(physicalRuntime.getTccReservationStore(), runtime.getTccReservationStore());
        assertSame(runtime.getQuantumJournalStore(), DefaultTransactionManager.getInstance().getQuantumJournalStore());
        assertSame(runtime.getIdempotencyStore(), BusinessIdempotencyManager.getStore());
        assertSame(runtime.getSagaLogStore(), SagaLogManager.getStore());
        assertSame(runtime.getTccBranchStore(), DefaultTccTransactionParticipant.getBranchStore());

        runtime.saveObjectSnapshot("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        runtime.seedCurrentQuantumValue("snapshotGroup", "SnapshotObject.balance", BigDecimal.TEN);
        assertEquals(BigDecimal.TEN.setScale(12), runtime.findCurrentQuantumValues().get(0).currentValue());
        assertEquals(1, runtime.findObjectSnapshots().size());
        assertEquals(0, runtime.findSagaLogs().size());
        assertEquals(0, runtime.findTccBranches().size());
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-TRY", "REQ-TRY", "inventory", "lockInventory",
                TccBranchStatus.TRYING, "inventory locked", "2026-06-27T00:00:00Z"));
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-DONE", "REQ-DONE", "inventory", "lockInventory",
                TccBranchStatus.CONFIRMED, "inventory confirmed", "2026-06-27T00:00:01Z"));
        assertEquals(1, runtime.findTryingTccBranches().size());
        assertEquals("BR-TRY", runtime.findTryingTccBranches().get(0).branchId());
        runtime.getTccReservationStore().save(new TccReservationRecord("RSV-TRY", "BR-TRY",
                "REQ-TRY", "TX-TRY", "inventory", "lockInventory",
                "Inventory.availableQty", "Inventory.lockedQty", BigDecimal.ONE,
                TccReservationStatus.TRYING, "reserved", "2026-06-27T00:00:00Z",
                "2026-06-27T00:00:01Z"));
        assertEquals(1, runtime.findExpiredTccReservations(Instant.parse("2026-06-27T00:00:02Z")).size());
        assertThrows(IllegalStateException.class, () -> BusinessIdempotencyManager.getStore().execute("snapshot:C-1", () -> "OK"));

        runtime.clear();

        assertEquals(0, runtime.findObjectSnapshots().size());
        assertEquals(0, runtime.findTccReservations().size());
        assertEquals(false, BusinessIdempotencyManager.getStore().contains("snapshot:C-1"));
    }

    @Test
    public void basicRuntimeExposesTraceDiagnosticsWithoutAnInstalledRequestImplementation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        HeaderRequest request = new HeaderRequest(new TestHeader("REQ-LOCAL-IDEMPOTENCY", ""));

        assertEquals("snapshotScenario:globalSerialNumber:REQ-LOCAL-IDEMPOTENCY",
                runtime.idempotencyKey("snapshotScenario", request).orElseThrow());
        assertEquals(false, runtime.hasIdempotentResponse("snapshotScenario", request));

        assertThrows(IllegalStateException.class, () -> runtime.getIdempotencyStore().execute(
                "snapshotScenario:globalSerialNumber:REQ-LOCAL-IDEMPOTENCY", () -> new TestQueryResponse()));
        assertEquals(false, runtime.hasIdempotentResponse("snapshotScenario", request));
    }

    @Test
    public void localGeneratedRuntimeRegistersGeneratedServices() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.getObjectRepository().save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        String properties = "querySnapshot=" + SnapshotQueryService.class.getName() + "\n";

        List<Service> services = runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, services.size());
        assertSame(runtime.getIdempotencyStore(), BusinessIdempotencyManager.getStore());
        Service service = DefaultServiceContainer.getInstance().getService("querySnapshot");
        assertTrue(service instanceof DelegatingService);
        assertTrue(originalService(service) instanceof SnapshotQueryService);
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);
        assertEquals("Alice", response.snapshotObject.name);
    }

    @Test
    public void localGeneratedRuntimeCatalogsRegisteredServicesByModeResourceAndCompensation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "querySnapshot=" + SnapshotQueryService.class.getName() + "\n"
                + "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n"
                + "trySnapshot=" + TrySnapshotService.class.getName() + "\n"
                + "snapshotScenario=" + ScenarioDependingOnQuery.class.getName() + "\n";

        List<Service> services = runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(services, runtime.getRegisteredGeneratedServices());
        assertEquals(5, catalog.services().size());
        assertTrue(catalog.servicesByMode().get("AT").contains("querySnapshot"));
        assertTrue(catalog.servicesByMode().get("AT").contains("releaseSnapshot"));
        assertTrue(catalog.servicesByMode().get("QUANTUM").contains("reserveSnapshot"));
        assertTrue(catalog.servicesByMode().get("TCC").contains("trySnapshot"));
        assertTrue(catalog.servicesByMode().get("SAGA").contains("snapshotScenario"));
        assertTrue(catalog.servicesByResource().get("snapshotLedger").contains("reserveSnapshot"));
        assertTrue(catalog.servicesByResource().get("snapshotRelease").contains("releaseSnapshot"));
        assertTrue(catalog.servicesByResource().get("snapshotTcc").contains("trySnapshot"));
        assertEquals(List.of("releaseSnapshot"), catalog.compensationRules().get("reserveSnapshot"));
        assertEquals(1, catalog.objectSnapshotRules().get("reserveSnapshot").size());
        assertEquals(ObjectSnapshotRule.Source.ARGUMENT,
                catalog.objectSnapshotRules().get("reserveSnapshot").get(0).source());
        ObjectSnapshotPlan reserveSnapshotPlan = catalog.objectSnapshotPlans().stream()
                .filter(plan -> plan.serviceName().equals("reserveSnapshot"))
                .findFirst().orElseThrow();
        assertEquals("EXPLICIT_ARGUMENT", reserveSnapshotPlan.source());
        assertFalse(reserveSnapshotPlan.appDdlDerived());
        assertTrue(reserveSnapshotPlan.escapeHatch());
        assertEquals(List.of("id"), reserveSnapshotPlan.objectIdPaths());
        assertEquals(null, reserveSnapshotPlan.resolvedRepositoryName());
        assertEquals(null, reserveSnapshotPlan.repositoryAdapterName());
        assertFalse(reserveSnapshotPlan.repositoryAppDdlTables());
        assertEquals(List.of(), reserveSnapshotPlan.objectKeyColumns());
        assertFalse(reserveSnapshotPlan.appDdlObjectKeyPushdown());
        assertEquals("EXPLICIT_ARGUMENT", reserveSnapshotPlan.objectKeySource());
        assertTrue(reserveSnapshotPlan.objectKeyEscapeHatch());
        assertEquals(1, catalog.objectSnapshotRules().get("querySnapshot").size());
        assertEquals("SnapshotObject", catalog.objectSnapshotRules().get("querySnapshot").get(0).objectType());
        assertEquals(1, catalog.objectSnapshotRules().get("releaseSnapshot").size());
        assertEquals(ObjectSnapshotRule.Source.ARGUMENT,
                catalog.objectSnapshotRules().get("releaseSnapshot").get(0).source());
        assertEquals(1, catalog.repositoryHydrationRules().get("querySnapshot").size());
        assertEquals("snapshotObject", catalog.repositoryHydrationRules()
                .get("querySnapshot").get(0).responseMethodName());
        assertTrue(catalog.physicalRuntime().backends().isEmpty());
        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());
        assertTrue(catalog.snapshotValidationErrors().isEmpty());
        assertTrue(catalog.repositoryValidationErrors().isEmpty());
        assertTrue(catalog.compensationValidationErrors().isEmpty());
        assertTrue(catalog.services().stream()
                .anyMatch(metadata -> "reserveSnapshot".equals(metadata.serviceName())
                        && ReserveSnapshotService.class.getName().equals(metadata.serviceClassName())));
        assertTrue(catalog.services().stream()
                .anyMatch(metadata -> "querySnapshot".equals(metadata.serviceName())
                        && metadata.scopeLocal()
                        && metadata.async()
                        && "baseIn".equals(metadata.inputHeaderName())
                        && "baseOut".equals(metadata.outputHeaderName())
                        && metadata.objectSnapshotRules().size() == 1
                        && metadata.repositoryHydrationRules().size() == 1));

        Service refreshed = runtime.registerGeneratedService(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)), "reserveSnapshot");
        assertEquals(5, runtime.getRegisteredGeneratedServices().size());
        assertSame(refreshed, runtime.getRegisteredGeneratedServices().stream()
                .filter(service -> "reserveSnapshot".equals(service.getName()))
                .findFirst()
                .orElseThrow());
    }

    @Test
    public void generatedDeploymentCatalogsSagaTransactionCalls() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "snapshotScenario=" + ScenarioWithTransactionCall.class.getName() + "\n"
                + "reserveSnapshot=" + SagaTransactionTargetService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.snapshotValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        assertTrue(catalog.services().stream()
                .anyMatch(metadata -> "snapshotScenario".equals(metadata.serviceName())
                        && metadata.sagaTransactionCallServiceNames().equals(List.of("reserveSnapshot"))));
    }

    @Test
    public void generatedDeploymentRejectsSagaTransactionCallWithoutArgumentSnapshot() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "snapshotScenario=" + ScenarioWithTransactionCall.class.getName() + "\n"
                + "reserveSnapshot=" + SagaTransactionTargetWithoutEvidenceService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.snapshotValidationErrors()));
        assertTrue(catalog.snapshotValidationErrors().get(0).contains(
                "Saga service snapshotScenario transaction block call reserveSnapshot"
                        + " must declare argument object snapshot rules"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("transaction block call reserveSnapshot"));
    }

    @Test
    public void generatedDeploymentRequiresCompensatedAtomicServiceArgumentSnapshot() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotWithoutEvidenceService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.compensationValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensated service reserveSnapshot"
                        + " must declare argument object snapshot rules"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("Compensated service reserveSnapshot"));
    }

    @Test
    public void generatedDeploymentRequiresRegisteredCompensationTarget() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.compensationValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " is not registered as a generated service"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("releaseSnapshot referenced by reserveSnapshot"));
    }

    @Test
    public void generatedDeploymentRejectsKnownNonUpdateCompensationKinds() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotQueryKindService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotQueryKindService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.compensationValidationErrors().toString(),
                catalog.compensationValidationErrors().stream().anyMatch(error -> error.contains(
                        "Compensated service reserveSnapshot"
                                + " must be an atomic update service or saga scenario service")));
        assertTrue(catalog.compensationValidationErrors().toString(),
                catalog.compensationValidationErrors().stream().anyMatch(error -> error.contains(
                        "Compensation service releaseSnapshot referenced by reserveSnapshot"
                                + " must be an atomic update service")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.compensationValidationErrors()));
    }

    @Test
    public void generatedDeploymentRequiresCompensationArgumentSnapshots() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotWithoutEvidenceService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.compensationValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " must declare argument object snapshot rules"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("argument object snapshot rules"));
    }

    @Test
    public void generatedDeploymentRequiresCompensationRollbackObservableSnapshots() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotBeforeOnlyEvidenceService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.compensationValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " must declare after-execution argument object snapshot rules"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("rollback evidence"));
    }

    @Test
    public void generatedDeploymentRejectsCommitOnlyCompensationRollbackEvidence() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotCommitOnlyEvidenceService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.compensationValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " must declare after-execution argument object snapshot rules"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("rollback evidence"));
    }

    @Test
    public void generatedDeploymentRequiresMatchingCompensationArgumentSnapshots() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotMismatchedEvidenceService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.compensationValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " must declare matching argument object snapshot rule SnapshotObject.id"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("matching argument object snapshot rule SnapshotObject.id"));
    }

    @Test
    public void generatedDeploymentRejectsNestedCompensationTargets() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotWithNestedCompensationService.class.getName() + "\n"
                + "auditSnapshot=" + AuditSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.compensationValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " cannot declare nested compensation services"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("nested compensation services"));
    }

    @Test
    public void generatedDeploymentRejectsDuplicateCompensationTargets() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotWithDuplicateCompensationService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.compensationValidationErrors().stream().anyMatch(error -> error.contains(
                "Compensation rule on service reserveSnapshot"
                        + " declares duplicate compensation service releaseSnapshot")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.compensationValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("duplicate compensation service releaseSnapshot"));
    }

    @Test
    public void generatedDeploymentRejectsSelfCompensationTargets() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotSelfCompensationService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.compensationValidationErrors().stream().anyMatch(error -> error.contains(
                "Compensation service reserveSnapshot cannot compensate itself")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.compensationValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("cannot compensate itself"));
    }

    @Test
    public void generatedDeploymentRejectsCompensationParameterMismatch() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotWithBusinessMethodService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotWithWrongParameterService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.compensationValidationErrors().stream().anyMatch(error -> error.contains(
                "Compensation service releaseSnapshot for reserveSnapshot must accept the same parameter list")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.compensationValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("releaseSnapshot for reserveSnapshot"));
    }

    @Test
    public void generatedDeploymentRequiresTransactionalCompensationTarget() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + NonTransactionalReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.snapshotValidationErrors().size());
        assertEquals(1, catalog.compensationValidationErrors().size());
        assertEquals(2, catalog.deploymentValidationErrors().size());
        assertTrue(catalog.compensationValidationErrors().get(0).contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " must declare a transaction mode"));
        assertTrue(catalog.snapshotValidationErrors().get(0).contains(
                "Object snapshot service releaseSnapshot must declare a transaction mode"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("must declare a transaction mode"));
    }

    @Test
    public void generatedDeploymentRejectsNonLocalCompensatedService() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + NonLocalReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.compensationValidationErrors().stream().anyMatch(error -> error.contains(
                "Compensated service reserveSnapshot must be declared in local APP scope")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.compensationValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("reserveSnapshot must be declared in local APP scope"));
    }

    @Test
    public void generatedDeploymentRejectsNonLocalCompensationTarget() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + NonLocalReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.compensationValidationErrors().stream().anyMatch(error -> error.contains(
                "Compensation service releaseSnapshot referenced by reserveSnapshot"
                        + " must be declared in local APP scope")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.compensationValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("releaseSnapshot referenced by reserveSnapshot"
                + " must be declared in local APP scope"));
    }

    @Test
    public void generatedDeploymentRejectsRemoteObjectSnapshotRules() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteReleaseSnapshot\n"
                        + "remote.remoteReleaseSnapshot.adapter=httpJson\n"
                        + "remote.remoteReleaseSnapshot.config.endpoint=https://snapshot.example.test/release\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "remoteReleaseSnapshot=" + RemoteReleaseSnapshotService.class.getName() + "\n";
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReleaseSnapshot");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return null;
            }
        });

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertEquals(catalog.snapshotValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(2, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.snapshotValidationErrors().stream().anyMatch(error -> error.contains(
                "Object snapshot service remoteReleaseSnapshot cannot be a remote generated service")));
        assertTrue(catalog.snapshotValidationErrors().stream().anyMatch(error -> error.contains(
                "Object snapshot service remoteReleaseSnapshot must be declared in local APP scope")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("cannot be a remote generated service"));
    }

    @Test
    public void generatedDeploymentRejectsSagaObjectSnapshotRules() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "submitSnapshotSaga=" + PhysicalSagaWithSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.snapshotValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.snapshotValidationErrors().get(0).contains(
                "Object snapshot service submitSnapshotSaga cannot declare SAGA transaction mode"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("cannot declare SAGA transaction mode"));
    }

    @Test
    public void generatedDeploymentRejectsKnownNonUpdateObjectSnapshotKind() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + ObjectSnapshotQueryKindService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.snapshotValidationErrors().toString(),
                catalog.snapshotValidationErrors().stream().anyMatch(error -> error.contains(
                        "Object snapshot service reserveSnapshot must be an atomic update service")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.snapshotValidationErrors()));
    }

    @Test
    public void generatedDeploymentRejectsObjectSnapshotInvalidArgumentPath() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "reserveSnapshot=" + InvalidObjectSnapshotArgumentService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.snapshotValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(2, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.snapshotValidationErrors().stream().anyMatch(error -> error.contains(
                "Object snapshot service reserveSnapshot method reserveSnapshot"
                        + " argument index 1 is outside parameter list")));
        assertTrue(catalog.snapshotValidationErrors().stream().anyMatch(error -> error.contains(
                "Object snapshot service reserveSnapshot method reserveSnapshot"
                        + " object id path MutableSnapshotObject.missingId is not readable")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("argument index 1"));
        assertTrue(error.getMessage().contains("missingId"));
    }

    @Test
    public void generatedDeploymentCatalogsTccLifecycleRules() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryLifecycleService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelLifecycleService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(List.of("confirmLifecycle", "cancelLifecycle"),
                catalog.tccLifecycleRules().get("tryLifecycle"));
        assertTrue(catalog.tccLifecycleValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void generatedDeploymentRejectsKnownNonUpdateTccLifecycleKinds() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryQueryKindLifecycleService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmQueryKindLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelQueryKindLifecycleService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.tccLifecycleValidationErrors().toString(),
                catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC lifecycle service tryLifecycle must be an atomic update service")));
        assertTrue(catalog.tccLifecycleValidationErrors().toString(),
                catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC confirm service confirmLifecycle referenced by tryLifecycle"
                                + " must be an atomic update service")));
        assertTrue(catalog.tccLifecycleValidationErrors().toString(),
                catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                        "TCC cancel service cancelLifecycle referenced by tryLifecycle"
                                + " must be an atomic update service")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.tccLifecycleValidationErrors()));
    }

    @Test
    public void localGeneratedRuntimeExposesTccRecoveryCandidatesWithLifecycleMetadata() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryLifecycleService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelLifecycleService.class.getName() + "\n";
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-TCC-1", "REQ-TCC-1", "inventoryTcc",
                "tryLifecycle", TccBranchStatus.TRYING, "try completed", "2026-06-27T00:00:00Z"));
        runtime.getTccReservationStore().save(new TccReservationRecord("RSV-TCC-1", "BR-TCC-1",
                "REQ-TCC-1", "TX-TCC-1", "inventoryTcc", "tryLifecycle",
                "Inventory.availableQty", "Inventory.lockedQty", BigDecimal.ONE,
                TccReservationStatus.TRYING, "reserved", "2026-06-27T00:00:00Z",
                "2026-06-27T00:00:01Z"));

        List<TccRecoveryCandidate> candidates =
                runtime.findTccRecoveryCandidates(Instant.parse("2026-06-27T00:00:02Z"));

        assertEquals(1, candidates.size());
        TccRecoveryCandidate candidate = candidates.get(0);
        assertEquals("BR-TCC-1", candidate.branch().branchId());
        assertEquals("tryLifecycle", candidate.tryServiceName());
        assertEquals("confirmLifecycle", candidate.confirmServiceName());
        assertEquals("cancelLifecycle", candidate.cancelServiceName());
        assertEquals(1, candidate.objectSnapshotRules().size());
        assertEquals("SnapshotObject", candidate.objectSnapshotRules().get(0).objectType());
        assertEquals(1, candidate.argumentSnapshotRuleCount());
        assertEquals(List.of("AFTER", "COMMIT"), candidate.argumentSnapshotSavePhases());
        assertEquals(List.of("AFTER", "COMMIT"), candidate.rollbackRecoverySavePhases());
        assertEquals(true, candidate.lifecycleAvailable());
        assertEquals(false, candidate.recoveryPayloadAvailable());
        assertEquals(1, candidate.reservations().size());
        assertEquals("RSV-TCC-1", candidate.reservations().get(0).reservationId());
        assertEquals(true, candidate.expiredReservationAvailable());
        List<RecoveryWorkItem> recoveryWorkItems =
                runtime.findRecoveryWorkItems(Instant.parse("2026-06-27T00:00:02Z"));
        assertEquals(1, recoveryWorkItems.size());
        RecoveryWorkItem workItem = recoveryWorkItems.get(0);
        assertEquals("TCC", workItem.kind());
        assertEquals("BR-TCC-1", workItem.workId());
        assertEquals("tryLifecycle", workItem.serviceName());
        assertEquals("TRYING", workItem.status());
        assertEquals("MANUAL", workItem.suggestedAction());
        assertEquals("TCC recovery payload is not available.", workItem.actionReason());
        assertEquals(false, workItem.recoveryPayloadAvailable());
        assertEquals(true, workItem.manualInterventionRequired());
        assertEquals(true, workItem.expired());
        assertEquals(1, workItem.argumentSnapshotRuleCount());
        assertEquals(List.of("AFTER", "COMMIT"), workItem.argumentSnapshotSavePhases());
        assertEquals(List.of("AFTER", "COMMIT"), workItem.rollbackRecoverySavePhases());
        assertEquals(candidate, workItem.tccCandidate());
        assertEquals(List.of("AFTER", "COMMIT"), workItem.tccCandidate().rollbackRecoverySavePhases());
        assertEquals(null, workItem.remoteCompensationCandidate());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.confirmTccRecovery("BR-TCC-1"));
        assertTrue(error.getMessage().contains("does not have recovery payload"));
    }

    @Test
    public void localGeneratedRuntimeConfirmsTccRecoveryCandidateWithPayload() {
        TccConfirmLifecycleService.lastLifecycle.set(null);
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryLifecycleService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelLifecycleService.class.getName() + "\n";
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        MutableSnapshotObject snapshot = new MutableSnapshotObject();
        snapshot.id = "INV-1";
        snapshot.name = "reserved";
        String payload = TccRecoveryPayloadCodec.encode(new Object[] { snapshot });
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-TCC-2", "REQ-TCC-2", "inventoryTcc",
                "tryLifecycle", TccBranchStatus.TRYING, "try completed", "2026-06-27T00:00:00Z", payload));

        List<TccRecoveryCandidate> candidates = runtime.findTccRecoveryCandidates();
        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).recoveryPayloadAvailable());

        runtime.confirmTccRecovery("BR-TCC-2");

        TccBranchRecord branch = runtime.getTccBranchStore().find("BR-TCC-2").orElseThrow();
        assertEquals(TccBranchStatus.CONFIRMED, branch.status());
        assertEquals(payload, branch.recoveryPayload());
        assertEquals("confirm:INV-1:reserved", TccConfirmLifecycleService.lastLifecycle.get());
        assertTrue(runtime.findTccRecoveryCandidates().isEmpty());
    }

    @Test
    public void localGeneratedRuntimeReleasesPersistedReservationDuringTccRecovery() throws Exception {
        String repositoryUrl = "jdbc:h2:mem:runtime-tcc-reservation-recovery-"
                + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        JdbcDataSource dataSource = h2DataSource();
        dataSource.setURL(repositoryUrl);
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore snapshotStore = new JdbcObjectSnapshotStore(dataSource, value -> writeJson(value));
        JdbcObjectRepository repository = new JdbcObjectRepository(snapshotStore, objectMapper);
        LocalGeneratedServiceRuntime runtime = new LocalGeneratedServiceRuntime(
                LocalPhysicalRuntime.jdbc(dataSource),
                new GeneratedServiceRuntime(snapshotStore, repository, type -> null));
        AppDdlCatalog appDdlCatalog = runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                inventoryAppDdlCatalogProperties().getBytes(StandardCharsets.UTF_8)));
        new JdbcAppDdlSchemaManager(dataSource).ensureTables(appDdlCatalog);
        repository.useAppDdlCatalog(appDdlCatalog);
        String properties = "tryInventoryRecovery=" + TccInventoryTryRecoveryService.class.getName() + "\n"
                + "confirmInventoryRecovery=" + TccInventoryConfirmRecoveryService.class.getName() + "\n"
                + "cancelInventoryRecovery=" + TccInventoryCancelRecoveryService.class.getName() + "\n";
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

        AppDdlStore store = new AppDdlStore();
        store.name = "Main Store";
        AppDdlGoods goods = new AppDdlGoods();
        goods.skuId = "SKU-RECOVERY";
        AppDdlInventory inventory = new AppDdlInventory();
        inventory.store = store;
        inventory.goods = goods;
        inventory.availableQty = 17;
        inventory.safeQty = 3;
        String objectId = CompositeObjectId.encode(List.of("Main Store", "SKU-RECOVERY"));
        runtime.getRepository().save("Inventory", objectId, inventory);
        repository.writeAppDdlPlatformColumn("Inventory", objectId, inventory, "lockedQty", 2);

        AppDdlInventory recoveryInventory = new AppDdlInventory();
        recoveryInventory.store = store;
        recoveryInventory.goods = goods;
        recoveryInventory.availableQty = 18;
        recoveryInventory.safeQty = 3;
        String payload = TccRecoveryPayloadCodec.encode(new Object[] {recoveryInventory});
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-TCC-RECOVERY", "REQ-TCC-RECOVERY",
                "inventoryTcc", "tryInventoryRecovery", TccBranchStatus.TRYING,
                "try completed", "2026-06-27T00:00:00Z", payload));
        runtime.getTccReservationStore().save(new TccReservationRecord(
                "RSV-TCC-RECOVERY", "BR-TCC-RECOVERY", "REQ-TCC-RECOVERY", "TX-TCC-RECOVERY",
                "inventoryTcc", "tryInventoryRecovery", "Inventory.availableQty", "Inventory.lockedQty",
                BigDecimal.valueOf(2), TccReservationStatus.TRYING, "reserved",
                "2026-06-27T00:00:00Z", "2026-06-27T00:05:00Z", objectId));

        runtime.confirmTccRecovery("BR-TCC-RECOVERY");

        assertEquals(0, queryInt(repositoryUrl,
                "select lockedQty from INVENTORY where goods_skuid = 'SKU-RECOVERY'"));
        assertEquals(18, queryInt(repositoryUrl,
                "select availableQty from INVENTORY where goods_skuid = 'SKU-RECOVERY'"));
        assertEquals(TccBranchStatus.CONFIRMED,
                runtime.getTccBranchStore().find("BR-TCC-RECOVERY").orElseThrow().status());
        TccReservationRecord reservation =
                runtime.getTccReservationStore().find("RSV-TCC-RECOVERY").orElseThrow();
        assertEquals(TccReservationStatus.CONFIRMED, reservation.status());
        assertEquals(objectId, reservation.objectId());
    }

    @Test
    public void localGeneratedRuntimeScansTccRecoveryCandidatesWithResolver() {
        TccConfirmLifecycleService.lastLifecycle.set(null);
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryLifecycleService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelLifecycleService.class.getName() + "\n";
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        MutableSnapshotObject confirmSnapshot = new MutableSnapshotObject();
        confirmSnapshot.id = "INV-CONFIRM";
        confirmSnapshot.name = "reserved";
        MutableSnapshotObject skipSnapshot = new MutableSnapshotObject();
        skipSnapshot.id = "INV-SKIP";
        skipSnapshot.name = "reserved";
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-CONFIRM", "REQ-CONFIRM", "inventoryTcc",
                "tryLifecycle", TccBranchStatus.TRYING, "try completed", "2026-06-27T00:00:00Z",
                TccRecoveryPayloadCodec.encode(new Object[] { confirmSnapshot })));
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-SKIP", "REQ-SKIP", "inventoryTcc",
                "tryLifecycle", TccBranchStatus.TRYING, "try completed", "2026-06-27T00:00:01Z",
                TccRecoveryPayloadCodec.encode(new Object[] { skipSnapshot })));

        List<TccRecoveryScanResult> results = runtime.recoverTccCandidates(candidate ->
                "BR-CONFIRM".equals(candidate.branch().branchId())
                        ? TccRecoveryDecision.CONFIRM
                        : TccRecoveryDecision.SKIP);

        assertEquals(2, results.size());
        TccRecoveryScanResult confirmResult = results.stream()
                .filter(result -> "BR-CONFIRM".equals(result.candidate().branch().branchId()))
                .findFirst()
                .orElseThrow();
        TccRecoveryScanResult skipResult = results.stream()
                .filter(result -> "BR-SKIP".equals(result.candidate().branch().branchId()))
                .findFirst()
                .orElseThrow();
        assertEquals(TccRecoveryDecision.CONFIRM, confirmResult.decision());
        assertEquals(TccBranchStatus.CONFIRMED, confirmResult.recoveredBranch().status());
        assertEquals(null, confirmResult.errorMessage());
        assertEquals(TccRecoveryDecision.SKIP, skipResult.decision());
        assertEquals(null, skipResult.recoveredBranch());
        assertEquals(null, skipResult.errorMessage());
        assertEquals(TccBranchStatus.CONFIRMED,
                runtime.getTccBranchStore().find("BR-CONFIRM").orElseThrow().status());
        assertEquals(TccBranchStatus.TRYING,
                runtime.getTccBranchStore().find("BR-SKIP").orElseThrow().status());
        assertEquals("confirm:INV-CONFIRM:reserved", TccConfirmLifecycleService.lastLifecycle.get());
    }

    @Test
    public void localGeneratedRuntimeRecoveryScanRecordsErrorsAndContinues() {
        TccConfirmLifecycleService.lastLifecycle.set(null);
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryLifecycleService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelLifecycleService.class.getName() + "\n";
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        MutableSnapshotObject snapshot = new MutableSnapshotObject();
        snapshot.id = "INV-OK";
        snapshot.name = "reserved";
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-OLD", "REQ-OLD", "inventoryTcc",
                "tryLifecycle", TccBranchStatus.TRYING, "try completed", "2026-06-27T00:00:00Z"));
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-OK", "REQ-OK", "inventoryTcc",
                "tryLifecycle", TccBranchStatus.TRYING, "try completed", "2026-06-27T00:00:01Z",
                TccRecoveryPayloadCodec.encode(new Object[] { snapshot })));

        List<TccRecoveryScanResult> results = runtime.recoverTccCandidates(candidate ->
                TccRecoveryDecision.CONFIRM);

        assertEquals(2, results.size());
        TccRecoveryScanResult oldResult = results.stream()
                .filter(result -> "BR-OLD".equals(result.candidate().branch().branchId()))
                .findFirst()
                .orElseThrow();
        assertEquals(TccRecoveryDecision.CONFIRM, oldResult.decision());
        assertEquals(null, oldResult.recoveredBranch());
        assertTrue(oldResult.errorMessage().contains("does not have recovery payload"));
        assertEquals(TccBranchStatus.TRYING,
                runtime.getTccBranchStore().find("BR-OLD").orElseThrow().status());
        assertEquals(TccBranchStatus.CONFIRMED,
                runtime.getTccBranchStore().find("BR-OK").orElseThrow().status());
        assertEquals("confirm:INV-OK:reserved", TccConfirmLifecycleService.lastLifecycle.get());
    }

    @Test
    public void localGeneratedRuntimeRecoversUnifiedWorkItems() {
        TccConfirmLifecycleService.lastLifecycle.set(null);
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryLifecycleService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelLifecycleService.class.getName() + "\n"
                + "remoteReservePricing=" + RemoteCompensatedPricingService.class.getName() + "\n"
                + "remoteReleasePricing=" + RemoteReleasePricingService.class.getName() + "\n";
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        MutableSnapshotObject snapshot = new MutableSnapshotObject();
        snapshot.id = "INV-WORK";
        snapshot.name = "reserved";
        runtime.getTccBranchStore().save(new TccBranchRecord("BR-WORK", "REQ-WORK", "inventoryTcc",
                "tryLifecycle", TccBranchStatus.TRYING, "try completed", "2026-06-27T00:00:00Z",
                TccRecoveryPayloadCodec.encode(new Object[] { snapshot })));
        List<String> adapterCalls = new ArrayList<>();
        boolean[] recoveryEnabled = {false};
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                adapterCalls.add(serviceName + ":" + request.id());
                if ("remoteReleasePricing".equals(serviceName) && !recoveryEnabled[0]) {
                    throw new IllegalStateException("remote pricing release failed");
                }
                return null;
            }
        });
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");
        assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-WORK", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-WORK", "fail"))
                )));
        assertEquals(2, runtime.findRecoveryWorkItems().size());

        recoveryEnabled[0] = true;
        List<RecoveryWorkItemScanResult> results = runtime.recoverRecoveryWorkItems(workItem -> {
            if ("TCC".equals(workItem.kind())) {
                return RecoveryWorkItemDecision.CONFIRM;
            }
            if ("REMOTE_COMPENSATION".equals(workItem.kind())) {
                return RecoveryWorkItemDecision.RETRY;
            }
            return RecoveryWorkItemDecision.SKIP;
        });

        assertEquals(2, results.size());
        RecoveryWorkItemScanResult tccResult = results.stream()
                .filter(result -> "TCC".equals(result.workItem().kind()))
                .findFirst()
                .orElseThrow();
        RecoveryWorkItemScanResult remoteResult = results.stream()
                .filter(result -> "REMOTE_COMPENSATION".equals(result.workItem().kind()))
                .findFirst()
                .orElseThrow();
        assertEquals(RecoveryWorkItemDecision.CONFIRM, tccResult.decision());
        assertEquals(TccBranchStatus.CONFIRMED, tccResult.recoveredBranch().status());
        assertEquals(null, tccResult.recoveredLog());
        assertEquals(null, tccResult.errorMessage());
        assertEquals(RecoveryWorkItemDecision.RETRY, remoteResult.decision());
        assertEquals(null, remoteResult.recoveredBranch());
        assertEquals("REMOTE_COMPENSATION_RECOVERED", remoteResult.recoveredLog().status());
        assertEquals(null, remoteResult.errorMessage());
        assertEquals("confirm:INV-WORK:reserved", TccConfirmLifecycleService.lastLifecycle.get());
        assertEquals(List.of("remoteReservePricing:PRICE-WORK",
                "remoteReleasePricing:PRICE-WORK",
                "remoteReleasePricing:PRICE-WORK"), adapterCalls);
        assertTrue(runtime.findRecoveryWorkItems().isEmpty());
    }

    @Test
    public void generatedDeploymentValidatesTccLifecycleTargets() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + MissingConfirmTccTryLifecycleService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelWithoutSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.tccLifecycleValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(2, catalog.tccLifecycleValidationErrors().size());
        assertTrue(catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                "TCC confirm service missingConfirm referenced by tryLifecycle"
                        + " is not registered as a generated service")));
        assertTrue(catalog.tccLifecycleValidationErrors().stream().anyMatch(error -> error.contains(
                "TCC cancel service cancelLifecycle referenced by tryLifecycle"
                        + " must declare argument object snapshot rules")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("missingConfirm referenced by tryLifecycle"));
    }

    @Test
    public void generatedDeploymentRejectsTccLifecycleParameterMismatch() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "tryLifecycle=" + TccTryWithBusinessMethodService.class.getName() + "\n"
                + "confirmLifecycle=" + TccConfirmWithBusinessMethodService.class.getName() + "\n"
                + "cancelLifecycle=" + TccCancelWithWrongParameterService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.snapshotValidationErrors()));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.tccLifecycleValidationErrors()));
        assertEquals(1, catalog.tccLifecycleValidationErrors().size());
        assertTrue(catalog.tccLifecycleValidationErrors().get(0).contains(
                "TCC cancel service cancelLifecycle for tryLifecycle must accept the same parameter list"));
        assertEquals(1, catalog.snapshotValidationErrors().size());
        assertTrue(catalog.snapshotValidationErrors().get(0).contains(
                "Object snapshot service cancelLifecycle method cancelLifecycle"
                        + " object id path String.id is not readable"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("cancelLifecycle for tryLifecycle"));
    }

    @Test
    public void generatedDeploymentRejectsRemoteRepositoryHydrationRules() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteRepositorySnapshot\n"
                        + "remote.remoteRepositorySnapshot.adapter=httpJson\n"
                        + "remote.remoteRepositorySnapshot.config.endpoint=https://snapshot.example.test/repository\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "remoteRepositorySnapshot=" + RemoteRepositorySnapshotQueryService.class.getName() + "\n";
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteRepositorySnapshot");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return new SnapshotObject("C-REMOTE", "remote");
            }
        });

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertEquals(catalog.repositoryValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(2, catalog.repositoryValidationErrors().size());
        assertTrue(catalog.repositoryValidationErrors().stream().anyMatch(error -> error.contains(
                "Repository hydration service remoteRepositorySnapshot cannot be a remote generated service")));
        assertTrue(catalog.repositoryValidationErrors().stream().anyMatch(error -> error.contains(
                "Repository hydration service remoteRepositorySnapshot must be declared in local APP scope")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("cannot be a remote generated service"));
    }

    @Test
    public void generatedDeploymentRejectsKnownNonQueryRepositoryHydrationKind() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "querySnapshot=" + RepositoryHydrationUpdateKindService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.repositoryValidationErrors().toString(),
                catalog.repositoryValidationErrors().stream().anyMatch(error -> error.contains(
                        "Repository hydration service querySnapshot must be an atomic query service")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.repositoryValidationErrors()));
    }

    @Test
    public void generatedDeploymentRejectsRepositoryHydrationMissingResponseWriter() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "querySnapshot=" + MissingResponseWriterRepositoryService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.repositoryValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.repositoryValidationErrors().size());
        assertTrue(catalog.repositoryValidationErrors().get(0).contains(
                "Repository hydration service querySnapshot response type TestQueryResponse"
                        + " must declare write method missingSnapshot(...)"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("missingSnapshot"));
    }

    @Test
    public void generatedDeploymentRejectsRepositoryHydrationInvalidArgumentPath() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "querySnapshot=" + InvalidRepositoryArgumentService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.repositoryValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(2, catalog.repositoryValidationErrors().size());
        assertTrue(catalog.repositoryValidationErrors().stream().anyMatch(error -> error.contains(
                "Repository hydration service querySnapshot method querySnapshot"
                        + " argument index 1 is outside parameter list")));
        assertTrue(catalog.repositoryValidationErrors().stream().anyMatch(error -> error.contains(
                "Repository hydration service querySnapshot method querySnapshot"
                        + " object id path MutableSnapshotObject.missingId is not readable")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("argument index 1"));
        assertTrue(error.getMessage().contains("missingId"));
    }

    @Test
    public void generatedDeploymentRejectsRepositoryHydrationDuplicateResponseTargets() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "querySnapshot=" + DuplicateRepositoryTargetService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(catalog.repositoryValidationErrors(), catalog.deploymentValidationErrors());
        assertEquals(1, catalog.repositoryValidationErrors().size());
        assertTrue(catalog.repositoryValidationErrors().get(0).contains(
                "Repository hydration service querySnapshot declares duplicate response target snapshotObject"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("duplicate response target snapshotObject"));
    }

    @Test
    public void localGeneratedRuntimeLoadsPhysicalRuntimeCatalog() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String physical = "backends=localJdbc,seata\n"
                + "backend.localJdbc.bindings=\n"
                + "backend.seata.bindings=at:salesOrder,tcc:inventoryTcc,saga:submitOrder\n"
                + "backend.seata.config.applicationId=credit-commerce\n"
                + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                + "backend.seata.config.quantumSupported= true \n"
                + "remoteServices=remoteSnapshotScenario\n"
                + "remote.remoteSnapshotScenario.adapter=httpJson\n"
                + "remote.remoteSnapshotScenario.config.endpoint=https://snapshot.example.test/spp\n"
                + "repositories=objectStore\n"
                + "repository.objectStore.adapter=objectSnapshot\n"
                + "repository.objectStore.config.table=spp_object_snapshot\n";

        PhysicalRuntimeCatalog catalog = runtime.loadPhysicalRuntimeCatalog(
                new ByteArrayInputStream(physical.getBytes(StandardCharsets.UTF_8)));

        assertEquals(catalog, runtime.getPhysicalRuntimeCatalog());
        assertEquals(catalog, runtime.catalogGeneratedServices().physicalRuntime());
        assertEquals(2, catalog.backends().size());
        assertEquals("localJdbc", catalog.backends().get(0).name());
        assertEquals("seata", catalog.backends().get(1).name());
        assertEquals("at", catalog.backends().get(1).bindings().get(0).kind());
        assertEquals("salesOrder", catalog.backends().get(1).bindings().get(0).targetName());
        assertEquals("credit-commerce", catalog.backends().get(1).configuration().get("applicationId"));
        assertEquals("credit-commerce-tx-group",
                catalog.backends().get(1).configuration().get("transactionServiceGroup"));
        assertEquals("true", catalog.backends().get(1).configuration().get("quantumSupported").trim());
        assertEquals("seata", catalog.backendFor("at", "salesOrder").orElseThrow().name());
        assertEquals(1, catalog.bindingsFor("saga").size());
        assertTrue(catalog.backendFor("at", "missingResource").isEmpty());
        assertEquals(1, catalog.remoteAdapters().size());
        assertEquals("remoteSnapshotScenario", catalog.remoteAdapters().get(0).serviceName());
        assertEquals("httpJson", catalog.remoteAdapters().get(0).adapterName());
        assertEquals("https://snapshot.example.test/spp",
                catalog.remoteAdapters().get(0).configuration().get("endpoint"));
        assertEquals(1, catalog.repositoryAdapters().size());
        assertEquals("objectStore", catalog.repositoryAdapters().get(0).repositoryName());
        assertEquals("objectSnapshot", catalog.repositoryAdapters().get(0).adapterName());
        assertEquals("spp_object_snapshot", catalog.repositoryAdapters().get(0).configuration().get("table"));

        SeataPhysicalBackendAdapter adapter = SeataPhysicalBackendAdapter.from(catalog.backends().get(1));
        assertEquals("seata-at-tcc-saga-quantum", adapter.implementationType());
        assertTrue(adapter.supportsTransaction("quantum", "creditAccounting"));
        assertTrue(runtime.catalogGeneratedServices().physicalBackendPlans().stream()
                .anyMatch(plan -> "seata".equals(plan.backendName())
                        && plan.seataQuantumSupported()
                        && plan.supportedTransactions().contains("quantum")));
        assertEquals("credit-commerce", adapter.configuration().get("applicationId"));
        assertEquals("credit-commerce-tx-group", adapter.configuration().get("transactionServiceGroup"));
    }

    @Test
    public void physicalBackendOutputModelsNormalizeBoundaryValues() {
        List<String> supportedTransactions = new ArrayList<>();
        supportedTransactions.add(" at ");
        supportedTransactions.add(null);
        supportedTransactions.add(" quantum ");
        PhysicalBackendPlan plan = new PhysicalBackendPlan(" seata ", " seata ", true,
                " seata-at-tcc-saga-quantum ", supportedTransactions,
                " credit-commerce ", " credit-commerce-tx-group ", " bridge ",
                " com.servpp.SeataBridge ", " 5000 ", true,
                " jdbc:h2:mem:physical-plan ", " org.h2.Driver ", " globalQuantum ",
                " jdbc:h2:mem:quantum ", true, true, " QUANTUM_DATASOURCE_COORDINATED ",
                " NO_MISSING_REASON ", " NO_ACTION_REQUIRED ",
                " seata:seata:datasource=jdbc:h2:mem:physical-plan:quantumStore=globalQuantum ",
                " quantumJournal ", " spp_quantum_journal ",
                " spp_quantum_value ", " transactionCoordinator ", " idempotencyStore ",
                " spp_idempotency ", " sagaLogStore ", " spp_saga_log ", " tccBranchStore ",
                " spp_tcc_branch ", " tccReservationStore ", " spp_tcc_reservation ", " 1500 ");

        assertEquals("seata", plan.backendName());
        assertEquals("seata", plan.adapterName());
        assertEquals("seata-at-tcc-saga-quantum", plan.adapterImplementationType());
        assertEquals(List.of("at", "", "quantum"), plan.supportedTransactions());
        assertEquals("credit-commerce", plan.applicationId());
        assertEquals("credit-commerce-tx-group", plan.transactionServiceGroup());
        assertEquals("globalQuantum", plan.quantumStoreName());
        assertTrue(plan.quantumDatasourceSeparate());
        assertTrue(plan.quantumDatasourceCoordinated());
        assertEquals("QUANTUM_DATASOURCE_COORDINATED", plan.quantumDatasourceCoordinationReadiness());
        assertEquals("NO_MISSING_REASON", plan.quantumDatasourceCoordinationMissingReason());
        assertEquals("NO_ACTION_REQUIRED", plan.quantumDatasourceCoordinationAction());
        assertEquals("spp_tcc_reservation", plan.tccReservationTableName());
        assertEquals("1500", plan.tccReservationTtlMillis());
        assertEquals("PHYSICAL_BACKEND_READY", plan.readiness());
        assertEquals(List.of("NO_MISSING_REASON"), plan.missingReasons());
        assertEquals(List.of("NO_ACTION_REQUIRED"), plan.actions());
        assertEquals("seata:seata:seata-at-tcc-saga-quantum:at+UNASSIGNED+quantum:PHYSICAL_BACKEND_READY",
                plan.backendContract());
        assertEquals("seata->seata-at-tcc-saga-quantum"
                        + "+datasource=jdbc:h2:mem:physical-plan"
                        + "+quantumStore=globalQuantum"
                        + "+quantumDatasource=jdbc:h2:mem:quantum"
                        + ":PHYSICAL_BACKEND_READY",
                plan.runtimeContract());

        PhysicalBackendAssignment assignment = new PhysicalBackendAssignment(" reserveSnapshot ", " quantum ",
                " snapshotLedger ", " seata ", " seata ", true, " EXACT ", " quantum:snapshotLedger ",
                true, true, true, " seata-at-tcc-saga-quantum ",
                Map.of("applicationId", " credit-commerce "));

        assertEquals("reserveSnapshot", assignment.serviceName());
        assertEquals("quantum", assignment.transactionKind());
        assertEquals("snapshotLedger", assignment.targetName());
        assertEquals("seata", assignment.backendName());
        assertEquals("seata", assignment.adapterName());
        assertEquals("EXACT", assignment.bindingScope());
        assertEquals("quantum:snapshotLedger", assignment.bindingKey());
        assertEquals("seata-at-tcc-saga-quantum", assignment.adapterImplementationType());
        assertEquals(" credit-commerce ", assignment.adapterConfiguration().get("applicationId"));
    }

    @Test
    public void semanticOutputModelsNormalizeBoundaryValues() {
        List<String> attributeIds = new ArrayList<>();
        attributeIds.add(" Inventory.availableQty ");
        attributeIds.add(null);
        List<String> missingReasons = new ArrayList<>();
        missingReasons.add(" endpoint ");
        missingReasons.add(null);
        QuantumAccessPlan accessPlan = new QuantumAccessPlan(" reserveInventory ", TransactionMode.QUANTUM,
                " inventoryTcc ", attributeIds,
                List.of(" inventoryQuantum "), List.of(" quantumService "),
                List.of(" QUANTUM_SERVICE "), true, false,
                List.of(" QUANTUM_SERVICE_ADAPTER "), List.of(" QUANTUM_SERVICE "),
                List.of(" SERVICE_CALL_BRIDGE "), " SERVICE_IDEMPOTENCY ", " QUANTUM_SERVICE ",
                " QUANTUM_SERVICE_INTERNAL ", true, List.of(" NEGATIVE_DELTA "),
                List.of(" QUANTUM_SERVICE "), List.of(" INPUT_NEGATIVE_DELTA "),
                " QUANTUM_SERVICE_AUDIT_EVIDENCE ", " QUANTUM_SERVICE ",
                false, false, false, true, true, true, true, true, true, false,
                List.of(" https://quantum.example.test/commit "), List.of(" 3000 "),
                List.of(" jdbc:h2:mem:quantum "),
                false, missingReasons);

        assertEquals("reserveInventory", accessPlan.serviceName());
        assertEquals("inventoryTcc", accessPlan.transactionResourceId());
        assertEquals(List.of("Inventory.availableQty", ""), accessPlan.attributeIds());
        assertEquals(List.of("inventoryQuantum"), accessPlan.storeNames());
        assertEquals(List.of("QUANTUM_SERVICE"), accessPlan.implementationStrategies());
        assertEquals("SERVICE_IDEMPOTENCY", accessPlan.idempotencyMode());
        assertEquals("QUANTUM_SERVICE_INTERNAL", accessPlan.idempotencyBoundary());
        assertEquals(List.of("NEGATIVE_DELTA"), accessPlan.reversalModes());
        assertEquals("QUANTUM_SERVICE_AUDIT_EVIDENCE", accessPlan.snapshotBoundary());
        assertEquals(List.of("https://quantum.example.test/commit"), accessPlan.endpoints());
        assertEquals(List.of("3000"), accessPlan.timeoutMillisValues());
        assertEquals(List.of("endpoint", ""), accessPlan.missingContractReasons());
        assertEquals("RESPONSIBILITY_INCOMPLETE", accessPlan.responsibilityReadiness());
        assertEquals(List.of("accessContract"), accessPlan.responsibilityMissingReasons());
        assertEquals(List.of("fixQuantumAccessContract"), accessPlan.responsibilityActions());
        assertEquals("QUANTUM_SERVICE", accessPlan.implementationStrategySet());
        assertEquals("QUANTUM_SERVICE+QUANTUM_SERVICE+QUANTUM_SERVICE_AUDIT_EVIDENCE+QUANTUM_SERVICE",
                accessPlan.responsibilityBoundary());
        assertEquals("reserveInventory:Inventory.availableQty->QUANTUM_SERVICE+QUANTUM_SERVICE"
                        + "+QUANTUM_SERVICE+ACCESS_CONTRACT_MISSING",
                accessPlan.accessContract());
        assertEquals("reserveInventory:SERVICE_IDEMPOTENCY+SERVICE_SNAPSHOT+NEGATIVE_DELTA_REVERSAL"
                        + "+SERVICE_REVERSAL_INTERNAL+ACCESS_CONTRACT_MISSING",
                accessPlan.serviceInternalProtocolContract());

        List<String> compatRoleKeyPaths = new ArrayList<>();
        compatRoleKeyPaths.add(" legacyStore ");
        compatRoleKeyPaths.add(null);
        RoleParticipantPlan rolePlan = new RoleParticipantPlan(" Inventory ", " INVENTORY ",
                " InventoryMapper ", List.of(" Store ", " Goods "), List.of(" store.id ", " goods.skuId "),
                List.of(" inventoryId "), List.of(" storeId ", " goodsId "),
                compatRoleKeyPaths, List.of(" available_qty "), true, true, true,
                0, false, " JOINT_PERFORMER ", " Inventory:Store+Goods ");

        assertEquals("Inventory", rolePlan.roleType());
        assertEquals("INVENTORY", rolePlan.roleTableName());
        assertEquals("InventoryMapper", rolePlan.mapperName());
        assertEquals(List.of("Store", "Goods"), rolePlan.performerTypes());
        assertEquals(List.of("store.id", "goods.skuId"), rolePlan.performerKeyPaths());
        assertEquals(List.of("inventoryId"), rolePlan.roleKeyPaths());
        assertEquals(List.of("legacyStore", ""), rolePlan.compatRoleKeyPaths());
        assertEquals(List.of("available_qty"), rolePlan.platformColumns());
        assertEquals(2, rolePlan.performerCount());
        assertTrue(rolePlan.jointPerformer());
        assertEquals("JOINT_PERFORMER", rolePlan.participationKind());
        assertEquals("Inventory:Store+Goods", rolePlan.binding());
    }

    @Test
    public void localGeneratedRuntimeLoadsTccReservationStoreFromPhysicalBackend() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String physical = "backends=localJdbc\n"
                + "backend.localJdbc.bindings=\n"
                + "backend.localJdbc.config.datasourceUrl=jdbc:h2:mem:runtime-physical-tcc-reservation;"
                + "DB_CLOSE_DELAY=-1\n"
                + "backend.localJdbc.config.driverClassName=org.h2.Driver\n"
                + "backend.localJdbc.config.tccReservationTable=spp_tcc_reservation\n"
                + "backend.localJdbc.config.tccReservationTtlMillis=1500\n";

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(physical.getBytes(StandardCharsets.UTF_8)));

        assertEquals("spp_tcc_reservation", runtime.getTccReservationStore().getTableName());
        assertEquals(1500L, runtime.getPhysicalRuntime().getTccReservationTtlMillis());
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        Map<String, String> configuration = catalog.physicalBackendAssignments().stream()
                .filter(assignment -> "localJdbc".equals(assignment.backendName()))
                .findFirst()
                .map(PhysicalBackendAssignment::adapterConfiguration)
                .orElseGet(() -> new LocalJdbcPhysicalBackendAdapter(runtime.getPhysicalRuntime())
                        .configuration());
        assertEquals("spp_tcc_reservation", configuration.get("tccReservationTable"));
        assertEquals("1500", configuration.get("tccReservationTtlMillis"));
    }

    @Test
    public void physicalRuntimeCatalogResolvesDeploymentConfigPlaceholders() {
        String previousEndpoint = System.getProperty("spp.test.customer.endpoint");
        String previousJdbcUrl = System.getProperty("spp.test.local.jdbc.url");
        try {
            System.setProperty("spp.test.customer.endpoint", "https://customer.override.test/query");
            System.setProperty("spp.test.local.jdbc.url", "jdbc:h2:mem:spp-placeholder-local");
            PhysicalRuntimeCatalog catalog = PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                    ("backends=localJdbc\n"
                            + "backend.localJdbc.bindings=\n"
                            + "backend.localJdbc.config.datasourceUrl=${spp.test.local.jdbc.url}\n"
                            + "backend.localJdbc.config.driverClassName=${spp.test.missing.driver:org.h2.Driver}\n"
                            + "remoteServices=queryCustomer\n"
                            + "remote.queryCustomer.adapter=httpJson\n"
                            + "remote.queryCustomer.config.endpoint=${spp.test.customer.endpoint}\n"
                            + "remote.queryCustomer.config.timeoutMillis=${spp.test.missing.timeout:2500}\n"
                            + "repositories=objectStore\n"
                            + "repository.objectStore.adapter=jdbcObjectRepository\n"
                            + "repository.objectStore.config.datasourceUrl=${spp.test.object.url:jdbc:h2:mem:spp-object}\n"
                            + "quantumStores=globalQuantum\n"
                            + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                            + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                            + "quantumStore.globalQuantum.config.datasourceUrl=${spp.test.quantum.url:jdbc:h2:mem:spp-quantum}\n")
                            .getBytes(StandardCharsets.UTF_8)));

            assertEquals("jdbc:h2:mem:spp-placeholder-local",
                    catalog.backends().get(0).configuration().get("datasourceUrl"));
            assertEquals("org.h2.Driver",
                    catalog.backends().get(0).configuration().get("driverClassName"));
            assertEquals("https://customer.override.test/query",
                    catalog.remoteAdapters().get(0).configuration().get("endpoint"));
            assertEquals("2500", catalog.remoteAdapters().get(0).configuration().get("timeoutMillis"));
            assertEquals("jdbc:h2:mem:spp-object",
                    catalog.repositoryAdapters().get(0).configuration().get("datasourceUrl"));
            assertEquals("jdbc:h2:mem:spp-quantum",
                    catalog.quantumStores().get(0).configuration().get("datasourceUrl"));
        } finally {
            restoreSystemProperty("spp.test.customer.endpoint", previousEndpoint);
            restoreSystemProperty("spp.test.local.jdbc.url", previousJdbcUrl);
        }
    }

    @Test
    public void physicalRuntimeCatalogRejectsMalformedDeploymentConfigPlaceholders() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.localJdbc.config.datasourceUrl=${spp.test.missing.url\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.deploymentValidationPlans().stream()
                .filter(plan -> "PHYSICAL_PROFILE".equals(plan.category()))
                .flatMap(plan -> plan.errors().stream())
                .anyMatch(error -> error.contains(
                        "Physical runtime config backend.localJdbc.config.datasourceUrl"
                                + " contains unresolved or malformed placeholder")));
        assertTrue(catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                "Physical runtime config backend.localJdbc.config.datasourceUrl"
                        + " contains unresolved or malformed placeholder")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("contains unresolved or malformed placeholder"));
    }

    @Test
    public void malformedQuantumServicePlaceholdersDoNotCreateClientBeforeDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.config.implementationStrategy=QUANTUM_SERVICE\n"
                        + "quantumStore.globalQuantum.config.accessMode=QUANTUM_SERVICE_ADAPTER\n"
                        + "quantumStore.globalQuantum.config.idempotencyMode=SERVICE_IDEMPOTENCY\n"
                        + "quantumStore.globalQuantum.config.reversalMode=NEGATIVE_DELTA\n"
                        + "quantumStore.globalQuantum.config.endpoint=${spp.test.quantum.endpoint\n"
                        + "quantumStore.globalQuantum.config.timeoutMillis=${spp.test.quantum.timeout\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                        "Physical runtime config quantumStore.globalQuantum.config.endpoint"
                                + " contains unresolved or malformed placeholder")));
        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                        "Physical runtime config quantumStore.globalQuantum.config.timeoutMillis"
                                + " contains unresolved or malformed placeholder")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("quantumStore.globalQuantum.config.endpoint"));
    }

    @Test
    public void malformedHttpJsonRemotePlaceholdersAreDeploymentValidationErrors() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=httpJson\n"
                        + "remote.remoteSnapshotQuery.config.endpoint=${spp.test.remote.endpoint\n"
                        + "remote.remoteSnapshotQuery.config.timeoutMillis=${spp.test.remote.timeout\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                        "Physical runtime config remote.remoteSnapshotQuery.config.endpoint"
                                + " contains unresolved or malformed placeholder")));
        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                        "Physical runtime config remote.remoteSnapshotQuery.config.timeoutMillis"
                                + " contains unresolved or malformed placeholder")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("remote.remoteSnapshotQuery.config.endpoint"));
    }

    @Test
    public void singleUnboundPhysicalBackendIsDefaultForAppSemantics() {
        PhysicalRuntimeCatalog catalog = PhysicalRuntimeCatalog.fromProperties(
                new ByteArrayInputStream("backends=localJdbc\nbackend.localJdbc.bindings=\n"
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("localJdbc", catalog.findBackend("localJdbc").orElseThrow().name());
        assertTrue(catalog.findBackend("missingBackend").isEmpty());
        assertEquals("localJdbc", catalog.backendFor("at", "salesOrder").orElseThrow().name());
        assertEquals("localJdbc", catalog.backendFor("tcc", "inventoryTcc").orElseThrow().name());
        assertEquals("localJdbc", catalog.backendFor("saga", "submitOrder").orElseThrow().name());
    }

    @Test
    public void localGeneratedRuntimeResolvesPhysicalBackendAssignments() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                "backends=localJdbc\nbackend.localJdbc.bindings=\n".getBytes(StandardCharsets.UTF_8)));
        String properties = "querySnapshot=" + SnapshotQueryService.class.getName() + "\n"
                + "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n"
                + "trySnapshot=" + TrySnapshotService.class.getName() + "\n"
                + "snapshotScenario=" + ScenarioDependingOnQuery.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(5, catalog.physicalBackendAssignments().size());
        PhysicalBackendAssignment query = catalog.physicalBackendAssignments().stream()
                .filter(item -> "querySnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("at", query.transactionKind());
        assertEquals("querySnapshot", query.targetName());
        assertEquals("localJdbc", query.backendName());
        assertFalse(query.explicitBinding());
        assertEquals("DEFAULT_BACKEND", query.bindingScope());
        assertEquals("", query.bindingKey());
        assertTrue(query.resolved());
        assertTrue(query.adapterAvailable());
        assertTrue(runtime.getPhysicalBackendRegistry().adapterFor("local-jdbc").isPresent());
        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());
        assertTrue(runtime.validatePhysicalBackendAssignments().isEmpty());
    }

    @Test
    public void physicalBackendAssignmentsExposeExplicitAndUnresolvedBindings() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc,seata\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.seata.bindings=quantum:snapshotLedger,saga:snapshotScenario\n"
                        + "backend.seata.config.transactionServiceGroup=snapshot-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "querySnapshot=" + SnapshotQueryService.class.getName() + "\n"
                + "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n"
                + "snapshotScenario=" + ScenarioDependingOnQuery.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        PhysicalBackendAssignment reserve = catalog.physicalBackendAssignments().stream()
                .filter(item -> "reserveSnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("quantum", reserve.transactionKind());
        assertEquals("snapshotLedger", reserve.targetName());
        assertEquals("seata", reserve.backendName());
        assertTrue(reserve.explicitBinding());
        assertEquals("EXACT_TARGET", reserve.bindingScope());
        assertEquals("quantum:snapshotLedger", reserve.bindingKey());
        assertTrue(reserve.resolved());
        assertTrue(reserve.adapterAvailable());
        assertTrue(!reserve.adapterSupportsTransaction());
        assertEquals("seata-at-tcc-saga", reserve.adapterImplementationType());
        assertEquals("at,tcc,saga", reserve.adapterConfiguration().get("supportedTransactions"));

        PhysicalBackendAssignment query = catalog.physicalBackendAssignments().stream()
                .filter(item -> "querySnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("at", query.transactionKind());
        assertEquals("querySnapshot", query.targetName());
        assertEquals(null, query.backendName());
        assertEquals("UNRESOLVED", query.bindingScope());
        assertEquals("", query.bindingKey());
        assertTrue(!query.resolved());
        assertTrue(!query.adapterAvailable());
        assertTrue(!query.adapterSupportsTransaction());
        assertEquals(null, query.adapterImplementationType());
        assertTrue(query.adapterConfiguration().isEmpty());
        assertEquals(3, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().stream()
                .anyMatch(error -> error.contains("Physical backend seata adapter seata-at-tcc-saga"
                        + " does not support quantum snapshotLedger")));
        assertTrue(catalog.physicalBackendValidationErrors().stream()
                .anyMatch(error -> error.contains("Physical backend is not resolved for at querySnapshot")));
        assertTrue(catalog.physicalBackendValidationErrors().stream()
                .anyMatch(error -> error.contains("Physical backend is not resolved for at snapshotRelease")));
        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().containsAll(catalog.physicalBackendValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidPhysicalBackendAssignments);
        assertTrue(error.getMessage().contains("Invalid physical backend assignments"));
        error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("does not support quantum snapshotLedger"));
    }

    @Test
    public void physicalBackendAssignmentsResolveWholeTransactionKindBindings() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=at:*,quantum:*,saga:*\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "querySnapshot=" + SnapshotQueryService.class.getName() + "\n"
                + "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n"
                + "snapshotScenario=" + ScenarioDependingOnQuery.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(4, catalog.physicalBackendAssignments().size());
        PhysicalBackendAssignment query = catalog.physicalBackendAssignments().stream()
                .filter(item -> "querySnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("at", query.transactionKind());
        assertEquals("querySnapshot", query.targetName());
        assertEquals("localJdbc", query.backendName());
        assertTrue(query.explicitBinding());
        assertEquals("TRANSACTION_KIND", query.bindingScope());
        assertEquals("at:*", query.bindingKey());
        assertTrue(query.resolved());
        assertTrue(query.adapterAvailable());
        PhysicalBackendAssignment reserve = catalog.physicalBackendAssignments().stream()
                .filter(item -> "reserveSnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("quantum", reserve.transactionKind());
        assertEquals("snapshotLedger", reserve.targetName());
        assertEquals("localJdbc", reserve.backendName());
        assertTrue(reserve.explicitBinding());
        assertEquals("TRANSACTION_KIND", reserve.bindingScope());
        assertEquals("quantum:*", reserve.bindingKey());
        PhysicalBackendAssignment scenario = catalog.physicalBackendAssignments().stream()
                .filter(item -> "snapshotScenario".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("saga", scenario.transactionKind());
        assertEquals("snapshotScenario", scenario.targetName());
        assertEquals("localJdbc", scenario.backendName());
        assertTrue(scenario.explicitBinding());
        assertEquals("TRANSACTION_KIND", scenario.bindingScope());
        assertEquals("saga:*", scenario.bindingKey());
        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());
        runtime.requireValidPhysicalBackendAssignments();
    }

    @Test
    public void physicalBackendAssignmentsExposeRegisteredAdapterAvailability() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder\n"
                        + "backend.seata.config.applicationId=credit-commerce\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        PhysicalBackendAssignment current = runtime.catalogGeneratedServices()
                .physicalBackendAssignments().get(0);

        assertEquals("seata", current.backendName());
        assertEquals("EXACT_TARGET", current.bindingScope());
        assertEquals("at:currentOrder", current.bindingKey());
        assertTrue(current.resolved());
        assertTrue(current.adapterAvailable());
        assertTrue(current.adapterSupportsTransaction());
        assertEquals("seata-at-tcc-saga", current.adapterImplementationType());
        assertEquals("credit-commerce",
                current.adapterConfiguration().get("applicationId"));
        assertEquals("credit-commerce-tx-group",
                current.adapterConfiguration().get("transactionServiceGroup"));
        assertEquals("at,tcc,saga",
                current.adapterConfiguration().get("supportedTransactions"));
        assertTrue(runtime.catalogGeneratedServices().physicalBackendValidationErrors().isEmpty());
        assertTrue(runtime.validatePhysicalBackendAssignments().isEmpty());
        runtime.requireValidPhysicalBackendAssignments();
    }

    @Test
    public void physicalBackendCanUseDeploymentNameWithExplicitAdapter() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=creditTx\n"
                        + "backend.creditTx.adapter=seata\n"
                        + "backend.creditTx.bindings=at:currentOrder\n"
                        + "backend.creditTx.config.applicationId=credit-commerce\n"
                        + "backend.creditTx.config.transactionServiceGroup=credit-commerce-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        PhysicalBackendAssignment current = runtime.catalogGeneratedServices()
                .physicalBackendAssignments().get(0);

        assertEquals("creditTx", current.backendName());
        assertEquals("seata", current.adapterName());
        assertEquals("EXACT_TARGET", current.bindingScope());
        assertEquals("at:currentOrder", current.bindingKey());
        assertTrue(current.resolved());
        assertTrue(current.adapterAvailable());
        assertTrue(current.adapterSupportsTransaction());
        assertEquals("seata-at-tcc-saga", current.adapterImplementationType());
        assertEquals("credit-commerce",
                current.adapterConfiguration().get("applicationId"));
        assertEquals("credit-commerce-tx-group",
                current.adapterConfiguration().get("transactionServiceGroup"));
        assertTrue(runtime.catalogGeneratedServices().physicalBackendValidationErrors().isEmpty());
        runtime.requireValidPhysicalBackendAssignments();
    }

    @Test
    public void physicalBackendValidationRejectsMultipleRegisteredAdaptersForSameBackendAdapter() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=creditTx\n"
                        + "backend.creditTx.adapter=seata\n"
                        + "backend.creditTx.bindings=at:currentOrder\n"
                        + "backend.creditTx.config.applicationId=credit-commerce\n"
                        + "backend.creditTx.config.transactionServiceGroup=credit-commerce-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerPhysicalBackendAdapter(new PhysicalBackendAdapter() {
            @Override
            public Set<String> backendNames() {
                return Set.of("seata");
            }

            @Override
            public String implementationType() {
                return "seata-primary";
            }
        });
        runtime.registerPhysicalBackendAdapter(new PhysicalBackendAdapter() {
            @Override
            public Set<String> backendNames() {
                return Set.of("seata");
            }

            @Override
            public String implementationType() {
                return "seata-backup";
            }
        });
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.physicalBackendAssignments().size());
        assertTrue(catalog.physicalBackendAssignments().get(0).adapterAvailable());
        assertTrue(catalog.physicalBackendAssignments().get(0).adapterSupportsTransaction());
        assertEquals("seata-backup",
                catalog.physicalBackendAssignments().get(0).adapterImplementationType());
        assertEquals(1, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().get(0).contains(
                "Physical backend adapter seata has multiple registered adapters: seata-primary, seata-backup"));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("multiple registered adapters"));
    }

    @Test
    public void physicalBackendValidationRejectsUnusedExplicitBindings() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder,at:missingOrder\n"
                        + "backend.seata.config.applicationId=credit-commerce\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.physicalBackendAssignments().size());
        assertEquals(1, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().get(0).contains(
                "Physical backend seata declares unused binding at missingOrder"));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("unused binding at missingOrder"));
    }

    @Test
    public void physicalBackendValidationRejectsDuplicateExplicitBindings() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata,localJdbc\n"
                        + "backend.seata.bindings=at:currentOrder\n"
                        + "backend.seata.config.applicationId=credit-commerce\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                        + "backend.localJdbc.bindings=at:currentOrder\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.physicalBackendAssignments().size());
        assertTrue(catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                "Physical binding at:currentOrder is assigned to multiple backends: seata and localJdbc")));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("at:currentOrder is assigned to multiple backends"));
    }

    @Test
    public void physicalBackendValidationRejectsDuplicateBindingsInSameBackend() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                        ("backends=seata\n"
                                + "backend.seata.bindings=at:currentOrder,at:currentOrder\n"
                                + "backend.seata.config.applicationId=credit-commerce\n"
                                + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated physical runtime catalog backend binding declares duplicate value at:currentOrder"));
    }

    @Test
    public void physicalBackendValidationRejectsDuplicateBackendNames() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                        ("backends=localJdbc,localJdbc\n"
                                + "backend.localJdbc.bindings=\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated physical runtime catalog backend declares duplicate value localJdbc"));
    }

    @Test
    public void generatedRuntimeValidatesIdempotencyMetadataForAppTransactionalServices() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=at:currentOrder\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("saveCurrentOrder="
                        + AppScopedPhysicalAtWithoutIdempotencyMetadata.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());
        assertEquals(2, catalog.idempotencyValidationErrors().size());
        assertTrue(catalog.idempotencyValidationErrors().stream().anyMatch(error -> error.contains(
                "saveCurrentOrder must expose idempotencyKeyRequired=true")));
        assertTrue(catalog.idempotencyValidationErrors().stream().anyMatch(error -> error.contains(
                "saveCurrentOrder must expose a unique String idempotency key field on input header baseIn")));
        DeploymentValidationPlan scopeHeaderPlan = catalog.deploymentValidationPlans().stream()
                .filter(plan -> "SCOPE_HEADER".equals(plan.category()))
                .findFirst()
                .orElseThrow();
        assertTrue(scopeHeaderPlan.metadata().get("missingIdempotencyKeyBindings"),
                scopeHeaderPlan.metadata().get("missingIdempotencyKeyBindings")
                        .contains(":missing=idempotencyKey:actions=declareUniqueIdempotencyHeaderField"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.idempotencyValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("idempotencyKeyRequired=true"));
    }

    @Test
    public void generatedRuntimeValidatesIdempotencyMetadataFieldName() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=at:currentOrder\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("saveCurrentOrder="
                        + AppScopedPhysicalAtWithInvalidIdempotencyField.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());
        assertEquals(1, catalog.idempotencyValidationErrors().size());
        assertTrue(catalog.idempotencyValidationErrors().get(0), catalog.idempotencyValidationErrors().get(0).contains(
                "idempotency key field must be globalSerialNumber or messageId, but was traceId"));
        assertEquals(catalog.idempotencyValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void generatedRuntimeValidatesIdempotencyRequestHeaderAccessor() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=at:currentOrder\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("saveCurrentOrder="
                        + AppScopedPhysicalAtWithMissingHeaderRequest.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.idempotencyValidationErrors().size());
        assertTrue(catalog.idempotencyValidationErrors().get(0),
                catalog.idempotencyValidationErrors().get(0).contains(
                        "request type " + MissingHeaderRequest.class.getName()
                                + " must expose getAppHeader() for idempotency"));
        assertEquals(catalog.idempotencyValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void generatedRuntimeValidatesIdempotencyHeaderGetterType() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=at:currentOrder\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("saveCurrentOrder="
                        + AppScopedPhysicalAtWithNumericIdempotencyHeader.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.idempotencyValidationErrors().size());
        assertTrue(catalog.idempotencyValidationErrors().get(0),
                catalog.idempotencyValidationErrors().get(0).contains(
                        "idempotency getter getGlobalSerialNumber() must return String, but was java.lang.Integer"));
        assertEquals(catalog.idempotencyValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void physicalBackendAdapterReceivesTransactionLifecycleCallbacks() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder,tcc:snapshotTcc,saga:submitSnapshotSaga\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        RecordingPhysicalBackendAdapter adapter = new RecordingPhysicalBackendAdapter();
        runtime.registerPhysicalBackendAdapter(adapter);
        String properties = "saveCurrentOrder=" + PhysicalAtService.class.getName() + "\n"
                + "trySnapshot=" + TrySnapshotService.class.getName() + "\n"
                + "submitSnapshotSaga=" + PhysicalSagaService.class.getName() + "\n"
                + "failCurrentOrder=" + FailingPhysicalAtService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        Service saveCurrentOrder = DefaultServiceContainer.getInstance().getService("saveCurrentOrder");
        Service trySnapshot = DefaultServiceContainer.getInstance().getService("trySnapshot");
        MutableSnapshotObject order = new MutableSnapshotObject();
        order.id = "SO-1";
        order.name = "Created";

        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return saveCurrentOrder;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                saveCurrentOrder.execute(order);
            }
        }));

        assertEquals(List.of(
                "begin:saveCurrentOrder:at:currentOrder:1",
                "beforeExecute:saveCurrentOrder:at:currentOrder:1",
                "afterExecute:saveCurrentOrder:at:currentOrder:1",
                "commit:saveCurrentOrder:at:currentOrder:1"), adapter.events);

        adapter.events.clear();
        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return trySnapshot;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                trySnapshot.execute(order);
            }
        }));

        assertEquals(List.of(
                "begin:trySnapshot:tcc:snapshotTcc:1",
                "beforeTry:trySnapshot:tcc:snapshotTcc:1",
                "afterTry:trySnapshot:tcc:snapshotTcc:1",
                "confirm:trySnapshot:tcc:snapshotTcc:1"), adapter.events);

        adapter.events.clear();
        Service submitSnapshotSaga = DefaultServiceContainer.getInstance().getService("submitSnapshotSaga");
        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return submitSnapshotSaga;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                submitSnapshotSaga.execute(order);
            }
        }));

        assertEquals(List.of(
                "begin:submitSnapshotSaga:saga:submitSnapshotSaga:1",
                "beforeExecute:submitSnapshotSaga:saga:submitSnapshotSaga:1",
                "afterExecute:submitSnapshotSaga:saga:submitSnapshotSaga:1",
                "commit:submitSnapshotSaga:saga:submitSnapshotSaga:1"), adapter.events);

        adapter.events.clear();
        Service failCurrentOrder = DefaultServiceContainer.getInstance().getService("failCurrentOrder");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
                    @Override
                    public Service getService() {
                        return trySnapshot;
                    }

                    @Override
                    public Object[] getArguments() {
                        return new Object[] { order };
                    }

                    @Override
                    public void execute() {
                        trySnapshot.execute(order);
                    }
                }, new IExecutor() {
                    @Override
                    public Service getService() {
                        return failCurrentOrder;
                    }

                    @Override
                    public Object[] getArguments() {
                        return new Object[] { order };
                    }

                    @Override
                    public void execute() {
                        failCurrentOrder.execute(order);
                    }
                })));

        assertEquals("simulated physical AT failure", failure.getMessage());
        assertEquals(List.of(
                "begin:trySnapshot:tcc:snapshotTcc:1",
                "begin:failCurrentOrder:at:currentOrder:1",
                "beforeTry:trySnapshot:tcc:snapshotTcc:1",
                "afterTry:trySnapshot:tcc:snapshotTcc:1",
                "beforeExecute:failCurrentOrder:at:currentOrder:1",
                "cancel:trySnapshot:tcc:snapshotTcc:1"), adapter.events);
    }

    @Test
    public void seataPhysicalBackendDelegatesLifecycleToConfiguredTransactionBridge() {
        RecordingSeataTransactionBridge.events.clear();
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder,tcc:snapshotTcc,saga:submitSnapshotSaga\n"
                        + "backend.seata.config.applicationId= credit-commerce \n"
                        + "backend.seata.config.transactionServiceGroup= credit-commerce-tx-group \n"
                        + "backend.seata.config.transactionBridgeClass="
                        + RecordingSeataTransactionBridge.class.getName() + " \n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "saveCurrentOrder=" + PhysicalAtService.class.getName() + "\n"
                + "trySnapshot=" + TrySnapshotService.class.getName() + "\n"
                + "submitSnapshotSaga=" + PhysicalSagaService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalBackendAssignment assignment = catalog.physicalBackendAssignments().stream()
                .filter(item -> "saveCurrentOrder".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("seata-at-tcc-saga", assignment.adapterImplementationType());
        assertEquals("credit-commerce", assignment.adapterConfiguration().get("applicationId"));
        assertEquals("credit-commerce-tx-group",
                assignment.adapterConfiguration().get("transactionServiceGroup"));
        assertEquals("recording-seata-bridge", assignment.adapterConfiguration().get("transactionBridge"));
        assertEquals(RecordingSeataTransactionBridge.class.getName(),
                assignment.adapterConfiguration().get("transactionBridgeClass"));
        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());

        MutableSnapshotObject order = new MutableSnapshotObject();
        order.id = "SO-1";
        order.name = "Created";
        Service saveCurrentOrder = DefaultServiceContainer.getInstance().getService("saveCurrentOrder");
        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return saveCurrentOrder;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                saveCurrentOrder.execute(order);
            }
        }));

        Service trySnapshot = DefaultServiceContainer.getInstance().getService("trySnapshot");
        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return trySnapshot;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                trySnapshot.execute(order);
            }
        }));

        Service submitSnapshotSaga = DefaultServiceContainer.getInstance().getService("submitSnapshotSaga");
        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return submitSnapshotSaga;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                submitSnapshotSaga.execute(order);
            }
        }));

        assertEquals(List.of(
                "begin:credit-commerce:credit-commerce-tx-group:saveCurrentOrder:at:currentOrder:1",
                "beforeExecute:credit-commerce:credit-commerce-tx-group:saveCurrentOrder:at:currentOrder:1",
                "afterExecute:credit-commerce:credit-commerce-tx-group:saveCurrentOrder:at:currentOrder:1",
                "commit:credit-commerce:credit-commerce-tx-group:saveCurrentOrder:at:currentOrder:1",
                "begin:credit-commerce:credit-commerce-tx-group:trySnapshot:tcc:snapshotTcc:1",
                "beforeTry:credit-commerce:credit-commerce-tx-group:trySnapshot:tcc:snapshotTcc:1",
                "afterTry:credit-commerce:credit-commerce-tx-group:trySnapshot:tcc:snapshotTcc:1",
                "confirm:credit-commerce:credit-commerce-tx-group:trySnapshot:tcc:snapshotTcc:1",
                "begin:credit-commerce:credit-commerce-tx-group:submitSnapshotSaga:saga:submitSnapshotSaga:1",
                "beforeExecute:credit-commerce:credit-commerce-tx-group:submitSnapshotSaga:saga:submitSnapshotSaga:1",
                "afterExecute:credit-commerce:credit-commerce-tx-group:submitSnapshotSaga:saga:submitSnapshotSaga:1",
                "commit:credit-commerce:credit-commerce-tx-group:submitSnapshotSaga:saga:submitSnapshotSaga:1"),
                RecordingSeataTransactionBridge.events);
    }

    @Test
    public void seataReflectiveBridgeUsesGlobalTransactionAcrossPhysicalParticipants() {
        io.seata.tm.api.GlobalTransactionContext.reset();
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder,saga:submitSnapshotSaga\n"
                        + "backend.seata.config.applicationId=credit-commerce\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                        + "backend.seata.config.transactionBridgeClass="
                        + ReflectiveSeataGlobalTransactionBridge.class.getName() + " \n"
                        + "backend.seata.config.seataTransactionTimeoutMillis= 1500 \n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "saveCurrentOrder=" + PhysicalAtService.class.getName() + "\n"
                + "submitSnapshotSaga=" + PhysicalSagaService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalBackendAssignment assignment = catalog.physicalBackendAssignments().stream()
                .filter(item -> "saveCurrentOrder".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("seata-reflective-global-transaction",
                assignment.adapterConfiguration().get("transactionBridge"));
        assertEquals("1500",
                assignment.adapterConfiguration().get(
                        "transactionBridge.seataTransactionTimeoutMillis"));
        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());

        MutableSnapshotObject order = new MutableSnapshotObject();
        order.id = "SO-1";
        order.name = "Created";
        Service saveCurrentOrder = DefaultServiceContainer.getInstance().getService("saveCurrentOrder");
        Service submitSnapshotSaga = DefaultServiceContainer.getInstance().getService("submitSnapshotSaga");
        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return saveCurrentOrder;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                saveCurrentOrder.execute(order);
            }
        }, new IExecutor() {
            @Override
            public Service getService() {
                return submitSnapshotSaga;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                submitSnapshotSaga.execute(order);
            }
        }));

        assertEquals(List.of(
                "begin:1500:credit-commerce:credit-commerce-tx-group:"
                        + DefaultTransactionManager.getLastContext().getTraceId(),
                "commit"), io.seata.tm.api.GlobalTransactionContext.current().events());
    }

    @Test
    public void physicalBackendAdapterSkipsServicesWithoutRequiredBinding() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        RecordingPhysicalBackendAdapter adapter = new RecordingPhysicalBackendAdapter();
        runtime.registerPhysicalBackendAdapter(adapter);
        String properties = "localCheck=" + NonPhysicalAtService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());

        Service localCheck = DefaultServiceContainer.getInstance().getService("localCheck");
        MutableSnapshotObject order = new MutableSnapshotObject();
        order.id = "SO-1";
        DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
            @Override
            public Service getService() {
                return localCheck;
            }

            @Override
            public Object[] getArguments() {
                return new Object[] { order };
            }

            @Override
            public void execute() {
                localCheck.execute(order);
            }
        }));

        assertTrue(adapter.events.isEmpty());
    }

    @Test
    public void invalidPhysicalBackendAssignmentFailsGeneratedServiceExecution() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=externalTx\n"
                        + "backend.externalTx.bindings=at:currentOrder\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "saveCurrentOrder=" + PhysicalAtService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        Service saveCurrentOrder = DefaultServiceContainer.getInstance().getService("saveCurrentOrder");
        MutableSnapshotObject order = new MutableSnapshotObject();
        order.id = "SO-1";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
                    @Override
                    public Service getService() {
                        return saveCurrentOrder;
                    }

                    @Override
                    public Object[] getArguments() {
                        return new Object[] { order };
                    }

                    @Override
                    public void execute() {
                        saveCurrentOrder.execute(order);
                    }
                })));

        assertTrue(error.getMessage().contains(
                "Physical backend externalTx has no registered adapter for at currentOrder"));
    }

    @Test
    public void seataPhysicalBackendRequiresTransactionServiceGroupForDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder\n"
                        + "backend.seata.config.quantumSupported=maybe\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalBackendAssignment current = catalog.physicalBackendAssignments().get(0);

        assertEquals("seata", current.backendName());
        assertTrue(current.adapterAvailable());
        assertTrue(current.adapterSupportsTransaction());
        assertEquals("seata-at-tcc-saga", current.adapterImplementationType());
        assertEquals(2, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().get(0).contains(
                "Physical backend seata Seata adapter must configure transactionServiceGroup"));
        assertTrue(catalog.physicalBackendValidationErrors().get(1).contains(
                "Physical backend seata Seata adapter quantumSupported must be true or false"));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("Seata adapter must configure transactionServiceGroup"));
        assertTrue(error.getMessage().contains("quantumSupported must be true or false"));
    }

    @Test
    public void declaredSeataPhysicalBackendConfigurationIsValidatedWithoutServiceAssignments() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=\n"
                        + "backend.seata.config.quantumSupported=maybe\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertEquals(2, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().get(0).contains(
                "Physical backend seata Seata adapter must configure transactionServiceGroup"));
        assertTrue(catalog.physicalBackendValidationErrors().get(1).contains(
                "Physical backend seata Seata adapter quantumSupported must be true or false"));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("Seata adapter must configure transactionServiceGroup"));
        assertTrue(error.getMessage().contains("quantumSupported must be true or false"));
    }

    @Test
    public void seataPhysicalBackendRejectsMissingTransactionBridgeClass() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                        + "backend.seata.config.transactionBridgeClass=missing.SeataBridge\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().get(0).contains(
                "Physical backend seata Seata adapter transactionBridgeClass missing.SeataBridge cannot be loaded"));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("transactionBridgeClass missing.SeataBridge cannot be loaded"));
    }

    @Test
    public void seataReflectiveBridgeValidatesSeataSdkClassesAtDeploymentTime() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=at:currentOrder\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                        + "backend.seata.config.transactionBridgeClass="
                        + ReflectiveSeataGlobalTransactionBridge.class.getName() + "\n"
                        + "backend.seata.config.seataGlobalTransactionContextClass=missing.GlobalTransactionContext\n"
                        + "backend.seata.config.seataTransactionTimeoutMillis=0\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "currentSnapshot=" + CurrentSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendValidationErrors().toString(),
                catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                        "Seata reflective bridge config seataGlobalTransactionContextClass"
                                + " class missing.GlobalTransactionContext cannot be loaded")));
        assertTrue(catalog.physicalBackendValidationErrors().toString(),
                catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                        "Seata reflective bridge config seataTransactionTimeoutMillis must be positive")));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("missing.GlobalTransactionContext cannot be loaded"));
    }

    @Test
    public void declaredLocalJdbcPhysicalBackendConfigurationIsValidatedWithoutServiceAssignments() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.localJdbc.config.datasourceUrl=\n"
                        + "backend.localJdbc.config.driverClassName=\n"
                        + "backend.localJdbc.config.schema=\n"
                        + "backend.localJdbc.config.transactionCoordinator=\n"
                        + "backend.localJdbc.config.idempotencyStore=\n"
                        + "backend.localJdbc.config.sagaLogStore=\n"
                        + "backend.localJdbc.config.tccBranchStore=\n"
                        + "backend.localJdbc.config.idempotencyTable=\n"
                        + "backend.localJdbc.config.sagaLogTable=\n"
                        + "backend.localJdbc.config.tccBranchTable=\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertEquals(10, catalog.physicalBackendValidationErrors().size());
        for (String key : List.of("datasourceUrl", "driverClassName", "schema",
                "transactionCoordinator", "idempotencyStore", "sagaLogStore", "tccBranchStore",
                "idempotencyTable", "sagaLogTable", "tccBranchTable")) {
            assertTrue(catalog.physicalBackendValidationErrors().toString(),
                    catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                            "Physical backend localJdbc localJdbc adapter config " + key + " must not be blank")));
        }
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertFalse(error.getMessage().contains("localJdbc adapter config quantumStore must not be blank"));
    }

    @Test
    public void declaredLocalJdbcPhysicalBackendRejectsInvalidTccReservationTtl() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.localJdbc.config.tccReservationTtlMillis=abc\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertEquals(1, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().toString(),
                catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                        "Physical backend localJdbc localJdbc adapter config tccReservationTtlMillis"
                                + " must be a non-negative integer.")));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("tccReservationTtlMillis must be a non-negative integer"));
    }

    @Test
    public void declaredLocalJdbcPhysicalBackendRejectsUnsupportedImplementationClasses() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.localJdbc.config.transactionCoordinator=com.example.CustomTransactionManager\n"
                        + "backend.localJdbc.config.idempotencyStore=com.example.CustomIdempotencyStore\n"
                        + "backend.localJdbc.config.sagaLogStore=com.example.CustomSagaLogStore\n"
                        + "backend.localJdbc.config.tccBranchStore=com.example.CustomTccBranchStore\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertEquals(4, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                "Physical backend localJdbc localJdbc adapter config transactionCoordinator"
                        + " declares unsupported implementation com.example.CustomTransactionManager"
                        + "; supported implementation is " + DefaultTransactionManager.class.getName())));
        assertTrue(catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                "Physical backend localJdbc localJdbc adapter config idempotencyStore"
                        + " declares unsupported implementation com.example.CustomIdempotencyStore"
                        + "; supported implementation is " + JdbcBusinessIdempotencyStore.class.getName())));
        assertTrue(catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                "Physical backend localJdbc localJdbc adapter config sagaLogStore"
                        + " declares unsupported implementation com.example.CustomSagaLogStore"
                        + "; supported implementation is " + JdbcSagaLogStore.class.getName())));
        assertTrue(catalog.physicalBackendValidationErrors().stream().anyMatch(error -> error.contains(
                "Physical backend localJdbc localJdbc adapter config tccBranchStore"
                        + " declares unsupported implementation com.example.CustomTccBranchStore"
                        + "; supported implementation is " + JdbcTccBranchStore.class.getName())));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("CustomTransactionManager"));
    }

    @Test
    public void declaredLocalJdbcPhysicalBackendAcceptsSupportedImplementationClasses() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.localJdbc.config.transactionCoordinator="
                        + DefaultTransactionManager.class.getName() + "\n"
                        + "backend.localJdbc.config.idempotencyStore="
                        + JdbcBusinessIdempotencyStore.class.getName() + "\n"
                        + "backend.localJdbc.config.sagaLogStore=" + JdbcSagaLogStore.class.getName() + "\n"
                        + "backend.localJdbc.config.tccBranchStore=" + JdbcTccBranchStore.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertTrue(catalog.physicalBackendValidationErrors().toString(),
                catalog.physicalBackendValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void declaredPhysicalBackendAdapterMustBeRegisteredWithoutServiceAssignments() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=externalTx\n"
                        + "backend.externalTx.adapter=missingAdapter\n"
                        + "backend.externalTx.bindings=\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertEquals(1, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().get(0).contains(
                "Physical backend externalTx declares adapter missingAdapter"
                        + " but no registered adapter is available"));
        assertEquals(catalog.physicalBackendValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("declares adapter missingAdapter"));
    }

    @Test
    public void physicalBackendAdapterMustSupportAssignedTransactionKind() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=quantum:snapshotLedger,at:snapshotRelease\n"
                        + "backend.seata.config.transactionServiceGroup=snapshot-tx-group\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalBackendAssignment reserve = catalog.physicalBackendAssignments().stream()
                .filter(item -> "reserveSnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();

        assertEquals("seata", reserve.backendName());
        assertTrue(reserve.adapterAvailable());
        assertTrue(!reserve.adapterSupportsTransaction());
        assertEquals(1, catalog.physicalBackendValidationErrors().size());
        assertTrue(catalog.physicalBackendValidationErrors().get(0).contains(
                "adapter seata-at-tcc-saga does not support quantum snapshotLedger"));
        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().containsAll(catalog.physicalBackendValidationErrors()));
        IllegalStateException physicalError = assertThrows(IllegalStateException.class,
                runtime::requireValidPhysicalBackendAssignments);
        assertTrue(physicalError.getMessage().contains("does not support quantum snapshotLedger"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("does not support quantum snapshotLedger"));
    }

    @Test
    public void seataPhysicalBackendAdapterCanExplicitlyExposeQuantumSupport() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=quantum:snapshotLedger,at:snapshotRelease\n"
                        + "backend.seata.config.applicationId=credit-commerce\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                        + "backend.seata.config.quantumSupported=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        PhysicalBackendAssignment reserve = runtime.catalogGeneratedServices()
                .physicalBackendAssignments().stream()
                .filter(item -> "reserveSnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();

        assertEquals("seata", reserve.backendName());
        assertTrue(reserve.adapterAvailable());
        assertTrue(reserve.adapterSupportsTransaction());
        assertEquals("seata-at-tcc-saga-quantum", reserve.adapterImplementationType());
        assertEquals("credit-commerce", reserve.adapterConfiguration().get("applicationId"));
        assertEquals("credit-commerce-tx-group",
                reserve.adapterConfiguration().get("transactionServiceGroup"));
        assertEquals("at,tcc,saga,quantum",
                reserve.adapterConfiguration().get("supportedTransactions"));
        assertTrue(runtime.catalogGeneratedServices().physicalBackendValidationErrors().isEmpty());
        runtime.requireValidPhysicalBackendAssignments();
    }

    @Test
    public void explicitSupportedTransactionsRestrictSeataQuantumSupport() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=quantum:snapshotLedger,at:snapshotRelease\n"
                        + "backend.seata.config.applicationId=credit-commerce\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                        + "backend.seata.config.quantumSupported=true\n"
                        + "backend.seata.config.supportedTransactions=at,tcc,saga\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n"
                + "releaseSnapshot=" + ReleaseSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalBackendAssignment reserve = catalog.physicalBackendAssignments().stream()
                .filter(item -> "reserveSnapshot".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();

        assertEquals("seata", reserve.backendName());
        assertTrue(reserve.adapterAvailable());
        assertTrue(!reserve.adapterSupportsTransaction());
        assertEquals("at,tcc,saga", reserve.adapterConfiguration().get("supportedTransactions"));
        assertFalse(catalog.physicalBackendPlans().stream()
                .filter(plan -> "seata".equals(plan.backendName()))
                .findFirst()
                .orElseThrow()
                .supportedTransactions()
                .contains("quantum"));
        assertTrue(catalog.physicalBackendValidationErrors().stream()
                .anyMatch(error -> error.contains("does not support quantum snapshotLedger")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains("does not support quantum snapshotLedger"));
    }

    @Test
    public void localJdbcDefaultPhysicalAdapterExposesImplementationDetails() {
        String localUrl = "jdbc:h2:mem:spp-local-physical-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.localJdbc.config.datasourceUrl=" + localUrl + "\n"
                        + "backend.localJdbc.config.driverClassName=org.h2.Driver\n"
                        + "backend.localJdbc.config.schema=PUBLIC\n"
                        + "backend.localJdbc.config.idempotencyTable=spp_idempotency_runtime\n"
                        + "backend.localJdbc.config.sagaLogTable=spp_saga_runtime\n"
                        + "backend.localJdbc.config.tccBranchTable=spp_tcc_runtime\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        PhysicalBackendAssignment reserve = runtime.catalogGeneratedServices()
                .physicalBackendAssignments().get(0);

        assertEquals("localJdbc", reserve.backendName());
        assertTrue(reserve.adapterAvailable());
        assertTrue(reserve.adapterSupportsTransaction());
        assertEquals("appframe-local-jdbc", reserve.adapterImplementationType());
        assertEquals(localUrl, reserve.adapterConfiguration().get("datasourceUrl"));
        assertEquals(localUrl, reserve.adapterConfiguration().get("localJdbcDatasourceUrl"));
        assertEquals("org.h2.Driver", reserve.adapterConfiguration().get("driverClassName"));
        assertEquals("PUBLIC", reserve.adapterConfiguration().get("schema"));
        assertTrue(reserve.adapterConfiguration().get("quantumJournalStore").contains("JdbcQuantumJournalStore"));
        assertTrue(reserve.adapterConfiguration().get("idempotencyStore").contains("JdbcBusinessIdempotencyStore"));
        assertEquals("spp_idempotency_runtime", reserve.adapterConfiguration().get("idempotencyTable"));
        assertTrue(reserve.adapterConfiguration().get("sagaLogStore").contains("JdbcSagaLogStore"));
        assertEquals("spp_saga_runtime", reserve.adapterConfiguration().get("sagaLogTable"));
        assertTrue(reserve.adapterConfiguration().get("tccBranchStore").contains("JdbcTccBranchStore"));
        assertEquals("spp_tcc_runtime", reserve.adapterConfiguration().get("tccBranchTable"));
        assertEquals("spp_idempotency_runtime", runtime.getPhysicalRuntime().getIdempotencyStore().getTableName());
        assertEquals("spp_saga_runtime", runtime.getPhysicalRuntime().getSagaLogStore().getTableName());
        assertEquals("spp_tcc_runtime", runtime.getPhysicalRuntime().getTccBranchStore().getTableName());
        assertEquals(localUrl, ((DriverManagerJdbcDataSource) runtime.getPhysicalRuntime()
                .getIdempotencyStore().getDataSource()).getUrl());
        runtime.requireValidPhysicalBackendAssignments();
    }

    @Test
    public void jdbcQuantumStoreConfigurationIsValidatedAtDeploymentTime() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.globalQuantum.config.datasourceUrl=jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.globalQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.globalQuantum.config.journalTable=spp_quantum_journal\n"
                        + "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.quantumStoreValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void jdbcQuantumStoreRejectsUnsupportedJournalStoreImplementation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.globalQuantum.config.datasourceUrl=jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.globalQuantum.config.journalStore=com.example.CustomQuantumJournalStore\n"
                        + "quantumStore.globalQuantum.config.journalTable=spp_quantum_journal\n"
                        + "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.quantumStoreValidationErrors().size());
        assertTrue(catalog.quantumStoreValidationErrors().get(0).contains(
                "Quantum store globalQuantum jdbcQuantum adapter config journalStore"
                        + " declares unsupported implementation com.example.CustomQuantumJournalStore"
                        + "; supported implementation is com.servpp.service.transaction.JdbcQuantumJournalStore"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.quantumStoreValidationErrors()));
        assertTrue(catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                "Quantum access contract is incomplete: globalQuantum")
                && error.contains("journalStoreMismatch")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("CustomQuantumJournalStore"));
        assertTrue(!(runtime.getQuantumJournalStore().getDataSource() instanceof DriverManagerJdbcDataSource));
    }

    @Test
    public void physicalJdbcQuantumStoreInstallsConfiguredRuntimeStore() throws Exception {
        String localUrl = "jdbc:h2:mem:runtime-local-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String quantumUrl = "jdbc:h2:mem:runtime-quantum-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "backend.localJdbc.config.datasourceUrl=" + localUrl + "\n"
                        + "backend.localJdbc.config.driverClassName=org.h2.Driver\n"
                        + "backend.localJdbc.quantumStore=globalQuantum\n"
                        + "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.globalQuantum.config.datasourceUrl=" + quantumUrl + "\n"
                        + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.globalQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.globalQuantum.config.journalTable=spp_quantum_journal\n"
                        + "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("spp_quantum_journal", runtime.getQuantumJournalStore().getJournalTable());
        assertEquals("spp_quantum_attribute_value", runtime.getQuantumJournalStore().getValueTable());
        DriverManagerJdbcDataSource dataSource =
                (DriverManagerJdbcDataSource) runtime.getQuantumJournalStore().getDataSource();
        assertEquals(quantumUrl, dataSource.getUrl());
        assertEquals(localUrl, ((DriverManagerJdbcDataSource) runtime.getPhysicalRuntime()
                .getIdempotencyStore().getDataSource()).getUrl());
        assertTrue(!localUrl.equals(dataSource.getUrl()));

        runtime.seedCurrentQuantumValue("creditAccounting", "CreditAccount.availableCredit", BigDecimal.valueOf(100));
        assertEquals(BigDecimal.valueOf(100).setScale(12),
                runtime.findCurrentQuantumValues().get(0).currentValue());
        assertTrue(tableExists(quantumUrl, "spp_quantum_journal"));
        assertTrue(tableExists(quantumUrl, "spp_quantum_attribute_value"));
        assertTrue(!tableExists(localUrl, "spp_quantum_journal"));
        assertTrue(!tableExists(localUrl, "spp_quantum_attribute_value"));
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(new ReserveSnapshotService()));
        PhysicalBackendAssignment assignment = catalog.physicalBackendAssignments().get(0);
        assertEquals("globalQuantum", assignment.adapterConfiguration().get("quantumStore"));
        assertEquals(localUrl, assignment.adapterConfiguration().get("localJdbcDatasourceUrl"));
        assertEquals(quantumUrl, assignment.adapterConfiguration().get("quantumDatasourceUrl"));
        assertTrue(!assignment.adapterConfiguration().get("localJdbcDatasourceUrl")
                .equals(assignment.adapterConfiguration().get("quantumDatasourceUrl")));
        assertEquals("spp_quantum_journal", assignment.adapterConfiguration().get("quantumJournalTable"));
        assertEquals("spp_quantum_attribute_value", assignment.adapterConfiguration().get("quantumValueTable"));
        assertTrue(catalog.deploymentValidationPlans().stream()
                .anyMatch(plan -> "QUANTUM_STORE".equals(plan.category())
                        && "1".equals(plan.metadata().get("backendQuantumStoreBindingCount"))
                        && plan.metadata().get("backendQuantumStoreBindings")
                                .contains("localJdbc:adapter=localJdbc:impl=appframe-local-jdbc")
                        && plan.metadata().get("backendQuantumStoreBindings")
                                .contains(":datasource=" + localUrl + ":driver=org.h2.Driver")
                        && plan.metadata().get("backendQuantumStoreBindings")
                                .contains(":quantumStore=globalQuantum:quantumResolved=true"
                                        + ":quantumAdapter=jdbcQuantum:quantumRole=JDBC_QUANTUM_STORE")
                        && plan.metadata().get("backendQuantumStoreBindings")
                                .contains(":quantumDatasource=" + quantumUrl
                                        + ":quantumDriver=org.h2.Driver")
                        && plan.metadata().get("backendQuantumStoreBindings")
                                .contains(":quantumJournalTable=spp_quantum_journal"
                                        + ":quantumValueTable=spp_quantum_attribute_value"
                                        + ":separateDatasource=true")));
    }

    @Test
    public void generatedQuantumServiceMetadataExposesConservedAttributes() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(new QuantumAccountingService()));

        assertEquals(List.of("CreditAccount.availableCredit", "OrderAmount.occupiedAmount"),
                catalog.services().get(0).quantumAttributeIds());
    }

    @Test
    public void runtimeQuantumStoreMustCoverGeneratedQuantumServiceAttributes() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.globalQuantum.config.datasourceUrl=jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.globalQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.globalQuantum.config.journalTable=spp_quantum_journal\n"
                        + "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(new QuantumAccountingService()));

        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum service reserveCredit resource creditAccounting attribute"
                                + " OrderAmount.occupiedAmount"
                                + " is not assigned to any runtime quantum store")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.quantumStoreValidationErrors()));
    }

    @Test
    public void localJdbcQuantumStoreSelectionMustReferenceDeclaredStoreAtDeploymentTime() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=quantum:creditAccounting\n"
                        + "backend.localJdbc.config.quantumStore=missingQuantum\n"
                        + "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.globalQuantum.config.datasourceUrl=jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.globalQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.globalQuantum.config.journalTable=spp_quantum_journal\n"
                        + "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals("missingQuantum", runtime.getPhysicalRuntimeCatalog().backends().get(0).quantumStoreName());
        assertFalse(runtime.getPhysicalRuntimeCatalog().backends().get(0).configuration()
                .containsKey("quantumStore"));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Physical backend localJdbc quantumStore"
                                + " references undefined quantum store missingQuantum")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.quantumStoreValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains("missingQuantum"));
    }

    @Test
    public void localJdbcQuantumBackendMustSelectStoreWhenMultipleRuntimeQuantumStoresExist() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=quantum:creditAccounting\n"
                        + "quantumStores=creditQuantum,inventoryQuantum\n"
                        + "quantumStore.creditQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.creditQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.creditQuantum.config.datasourceUrl=jdbc:h2:mem:spp-credit-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.creditQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.creditQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.creditQuantum.config.journalTable=spp_credit_quantum_journal\n"
                        + "quantumStore.creditQuantum.config.valueTable=spp_credit_quantum_value\n"
                        + "quantumStore.inventoryQuantum.attributes=Inventory.availableQty\n"
                        + "quantumStore.inventoryQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.inventoryQuantum.config.datasourceUrl=jdbc:h2:mem:spp-inventory-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.inventoryQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.inventoryQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.inventoryQuantum.config.journalTable=spp_inventory_quantum_journal\n"
                        + "quantumStore.inventoryQuantum.config.valueTable=spp_inventory_quantum_value\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Physical backend localJdbc localJdbc adapter must declare quantumStore"
                                + " when multiple quantum stores are declared")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.quantumStoreValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains("multiple quantum stores"));
    }

    @Test
    public void physicalObjectSnapshotRepositoryInstallsConfiguredRuntimeStore() {
        String repositoryUrl = "jdbc:h2:mem:runtime-object-store-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectSnapshot\n"
                        + "repository.objectStore.config.datasourceUrl=" + repositoryUrl + "\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.table=spp_object_snapshot_runtime\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("spp_object_snapshot_runtime", runtime.getSnapshotStore().getTableName());
        DriverManagerJdbcDataSource dataSource =
                (DriverManagerJdbcDataSource) runtime.getSnapshotStore().getDataSource();
        assertEquals(repositoryUrl, dataSource.getUrl());

        MutableSnapshotObject snapshot = new MutableSnapshotObject();
        snapshot.id = "C-9";
        snapshot.name = "configured";
        runtime.getRepository().save("SnapshotObject", "C-9", snapshot);

        MutableSnapshotObject hydrated =
                runtime.getObjectRepository().require("SnapshotObject", "C-9", MutableSnapshotObject.class);
        assertEquals("configured", hydrated.name);
    }

    @Test
    public void physicalJdbcObjectRepositoryInstallsConfiguredRuntimeRepository() throws Exception {
        String repositoryUrl = "jdbc:h2:mem:runtime-entity-object-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=" + repositoryUrl + "\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.table=spp_business_object\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(new ReserveSnapshotService()));

        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(runtime.getRepository() instanceof JdbcObjectRepository);
        JdbcObjectRepository repository = (JdbcObjectRepository) runtime.getRepository();
        assertEquals("spp_business_object", repository.getStore().getTableName());
        assertEquals(repositoryUrl, ((DriverManagerJdbcDataSource) repository.getStore().getDataSource()).getUrl());

        MutableSnapshotObject snapshot = new MutableSnapshotObject();
        snapshot.id = "C-11";
        snapshot.name = "entity";
        runtime.getRepository().save("SnapshotObject", "C-11", snapshot);
        assertTrue(tableExists(repositoryUrl, "spp_business_object"));

        MutableSnapshotObject hydrated = new MutableSnapshotObject();
        hydrated.id = "C-11";
        runtime.getRepository().hydrate("SnapshotObject", "C-11", hydrated);

        assertEquals("C-11", hydrated.id);
        assertEquals("entity", hydrated.name);
    }

    @Test
    public void physicalJdbcObjectRepositoryCreatesAppDdlTablesWhenEnabled() throws Exception {
        String repositoryUrl = "jdbc:h2:mem:runtime-app-ddl-object-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=" + repositoryUrl + "\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.table=spp_business_object\n"
                        + "repository.objectStore.config.appDdlTables=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=PRODUCT,GOODS,INVENTORY,ORDER_HEADER\n"
                        + "table.PRODUCT.category=entities\n"
                        + "table.PRODUCT.businessType=Product\n"
                        + "table.PRODUCT.columns=productId,name\n"
                        + "table.PRODUCT.primaryKeys=productId\n"
                        + "table.PRODUCT.indexes=name\n"
                        + "table.PRODUCT.column.productId.type=VARCHAR\n"
                        + "table.PRODUCT.column.productId.precision=64\n"
                        + "table.PRODUCT.column.productId.scale=0\n"
                        + "table.PRODUCT.column.productId.nullable=false\n"
                        + "table.PRODUCT.column.name.type=VARCHAR\n"
                        + "table.PRODUCT.column.name.precision=255\n"
                        + "table.PRODUCT.column.name.scale=0\n"
                        + "table.PRODUCT.column.name.nullable=true\n"
                        + "table.GOODS.category=roles\n"
                        + "table.GOODS.businessType=Goods\n"
                        + "table.GOODS.columns=skuId,product_productid,salePrice\n"
                        + "table.GOODS.primaryKeys=skuId\n"
                        + "table.GOODS.indexNames=goodsByProduct\n"
                        + "table.GOODS.index.goodsByProduct.paths=Goods.product.productId\n"
                        + "table.GOODS.column.skuId.type=VARCHAR\n"
                        + "table.GOODS.column.skuId.precision=64\n"
                        + "table.GOODS.column.skuId.scale=0\n"
                        + "table.GOODS.column.skuId.nullable=false\n"
                        + "table.GOODS.column.product_productid.type=VARCHAR\n"
                        + "table.GOODS.column.product_productid.precision=64\n"
                        + "table.GOODS.column.product_productid.scale=0\n"
                        + "table.GOODS.column.product_productid.nullable=false\n"
                        + "table.GOODS.column.salePrice.type=DOUBLE\n"
                        + "table.GOODS.column.salePrice.precision=0\n"
                        + "table.GOODS.column.salePrice.scale=0\n"
                        + "table.GOODS.column.salePrice.nullable=true\n"
                        + "table.INVENTORY.category=roles\n"
                        + "table.INVENTORY.businessType=Inventory\n"
                        + "table.INVENTORY.columns=store_name,goods_skuid,availableQty,safeQty,lockedQty\n"
                        + "table.INVENTORY.primaryKeys=store_name,goods_skuid\n"
                        + "table.INVENTORY.indexNames=inventoryByGoods,inventoryByStoreGoods\n"
                        + "table.INVENTORY.index.inventoryByGoods.paths=Inventory.goods.skuId\n"
                        + "table.INVENTORY.index.inventoryByStoreGoods.paths=Inventory.store.name,Inventory.goods.skuId\n"
                        + "table.INVENTORY.column.store_name.type=VARCHAR\n"
                        + "table.INVENTORY.column.store_name.precision=64\n"
                        + "table.INVENTORY.column.store_name.scale=0\n"
                        + "table.INVENTORY.column.store_name.nullable=false\n"
                        + "table.INVENTORY.column.goods_skuid.type=VARCHAR\n"
                        + "table.INVENTORY.column.goods_skuid.precision=64\n"
                        + "table.INVENTORY.column.goods_skuid.scale=0\n"
                        + "table.INVENTORY.column.goods_skuid.nullable=false\n"
                        + "table.INVENTORY.column.availableQty.type=INTEGER\n"
                        + "table.INVENTORY.column.availableQty.precision=0\n"
                        + "table.INVENTORY.column.availableQty.scale=0\n"
                        + "table.INVENTORY.column.availableQty.nullable=true\n"
                        + "table.INVENTORY.column.safeQty.type=INTEGER\n"
                        + "table.INVENTORY.column.safeQty.precision=0\n"
                        + "table.INVENTORY.column.safeQty.scale=0\n"
                        + "table.INVENTORY.column.safeQty.nullable=true\n"
                        + "table.INVENTORY.column.lockedQty.type=INTEGER\n"
                        + "table.INVENTORY.column.lockedQty.precision=0\n"
                        + "table.INVENTORY.column.lockedQty.scale=0\n"
                        + "table.INVENTORY.column.lockedQty.nullable=true\n"
                        + "table.INVENTORY.column.lockedQty.objectBacked=false\n"
                        + "table.ORDER_HEADER.category=contracts\n"
                        + "table.ORDER_HEADER.businessType=OrderHeader\n"
                        + "table.ORDER_HEADER.columns=orderId,orderNo,status\n"
                        + "table.ORDER_HEADER.primaryKeys=orderId\n"
                        + "table.ORDER_HEADER.indexNames=orderByOrderNo\n"
                        + "table.ORDER_HEADER.index.orderByOrderNo.paths=orderNo\n"
                        + "table.ORDER_HEADER.column.orderId.type=VARCHAR\n"
                        + "table.ORDER_HEADER.column.orderId.precision=64\n"
                        + "table.ORDER_HEADER.column.orderId.scale=0\n"
                        + "table.ORDER_HEADER.column.orderId.nullable=false\n"
                        + "table.ORDER_HEADER.column.orderId.objectBacked=false\n"
                        + "table.ORDER_HEADER.column.orderNo.type=VARCHAR\n"
                        + "table.ORDER_HEADER.column.orderNo.precision=64\n"
                        + "table.ORDER_HEADER.column.orderNo.scale=0\n"
                        + "table.ORDER_HEADER.column.orderNo.nullable=false\n"
                        + "table.ORDER_HEADER.column.orderNo.objectBacked=false\n"
                        + "table.ORDER_HEADER.column.status.type=VARCHAR\n"
                        + "table.ORDER_HEADER.column.status.precision=32\n"
                        + "table.ORDER_HEADER.column.status.scale=0\n"
                        + "table.ORDER_HEADER.column.status.nullable=true\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(new ReserveSnapshotService()));

        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(tableExists(repositoryUrl, "PRODUCT"));
        assertTrue(tableExists(repositoryUrl, "GOODS"));
        assertTrue(tableExists(repositoryUrl, "INVENTORY"));
        assertTrue(tableExists(repositoryUrl, "ORDER_HEADER"));
        assertTrue(tableExists(repositoryUrl, "spp_business_object"));
        assertTrue(columnExists(repositoryUrl, "PRODUCT", "productId"));
        assertTrue(columnExists(repositoryUrl, "GOODS", "product_productid"));
        assertTrue(columnExists(repositoryUrl, "INVENTORY", "goods_skuid"));
        assertTrue(columnExists(repositoryUrl, "INVENTORY", "lockedQty"));
        assertTrue(columnExists(repositoryUrl, "ORDER_HEADER", "orderId"));
        assertTrue(indexExists(repositoryUrl, "GOODS", "GOODS_GOODSBYPRODUCT"));
        assertTrue(indexExists(repositoryUrl, "INVENTORY", "INVENTORY_INVENTORYBYGOODS"));
        assertTrue(indexExists(repositoryUrl, "INVENTORY", "INVENTORY_INVENTORYBYSTOREGOODS"));
        assertTrue(indexExists(repositoryUrl, "ORDER_HEADER", "ORDER_HEADER_ORDERBYORDERNO"));
        assertTrue(indexExists(repositoryUrl, "PRODUCT", "PRODUCT_IDX_NAME"));

        JdbcObjectRepository repository = (JdbcObjectRepository) runtime.getRepository();
        assertTrue(repository.isAppDdlCatalogEnabled());

        AppDdlProduct product = new AppDdlProduct();
        product.productId = "P-100";
        product.name = "Demo Product";
        repository.save("Product", product.productId, product);

        AppDdlGoods goods = new AppDdlGoods();
        goods.skuId = "SKU-100";
        goods.product = product;
        goods.salePrice = 19.5D;
        repository.save("Goods", goods.skuId, goods);

        AppDdlGoods secondGoods = new AppDdlGoods();
        secondGoods.skuId = "SKU-200";
        secondGoods.product = product;
        secondGoods.salePrice = 29.5D;
        repository.save("Goods", secondGoods.skuId, secondGoods);

        AppDdlInventory inventory = new AppDdlInventory();
        inventory.store = new AppDdlStore();
        inventory.store.name = "Main Store";
        inventory.goods = goods;
        inventory.availableQty = 20;
        inventory.safeQty = 3;
        repository.save("Inventory", goods.skuId, inventory);

        AppDdlOrderHeader order = new AppDdlOrderHeader();
        order.orderNo = "SO-100";
        order.status = "Created";
        repository.save("OrderHeader", "ORDER-100", order);

        assertEquals("Demo Product",
                queryString(repositoryUrl, "select name from PRODUCT where productId = 'P-100'"));
        assertEquals("P-100",
                queryString(repositoryUrl, "select product_productid from GOODS where skuId = 'SKU-100'"));
        assertEquals("Main Store",
                queryString(repositoryUrl, "select store_name from INVENTORY where goods_skuid = 'SKU-100'"));
        assertEquals("ORDER-100",
                queryString(repositoryUrl, "select orderId from ORDER_HEADER where orderNo = 'SO-100'"));
        repository.adjustAppDdlPlatformColumn("Inventory", inventory, "lockedQty", BigDecimal.valueOf(2));
        assertEquals(2,
                queryInt(repositoryUrl, "select lockedQty from INVENTORY where goods_skuid = 'SKU-100'"));
        assertEquals(2, ((Number) repository.readAppDdlPlatformColumn("Inventory", inventory, "lockedQty")
                .orElseThrow()).intValue());
        repository.save("Inventory", goods.skuId, inventory);
        assertEquals(2,
                queryInt(repositoryUrl, "select lockedQty from INVENTORY where goods_skuid = 'SKU-100'"));
        assertEquals(2, ((Number) repository.readAppDdlPlatformColumn("Inventory", inventory, "lockedQty")
                .orElseThrow()).intValue());

        List<AppDdlGoods> listedGoods = repository.findAll("Goods", AppDdlGoods.class);
        assertEquals(2, listedGoods.size());
        assertEquals("SKU-100", listedGoods.get(0).skuId);
        assertEquals("SKU-200", listedGoods.get(1).skuId);

        List<AppDdlGoods> listedGoodsByProduct =
                repository.findBy("Goods", "product.productId", "P-100", AppDdlGoods.class);
        assertEquals(2, listedGoodsByProduct.size());
        assertEquals("SKU-100", listedGoodsByProduct.get(0).skuId);
        assertEquals("SKU-200", listedGoodsByProduct.get(1).skuId);

        List<AppDdlGoods> listedGoodsBySku =
                repository.findBy("Goods", "skuId", "SKU-100", AppDdlGoods.class);
        assertEquals(1, listedGoodsBySku.size());
        assertEquals("SKU-100", listedGoodsBySku.get(0).skuId);

        List<AppDdlInventory> listedInventoryByStoreGoods = repository.findBy("Inventory",
                List.of("store.name", "goods.skuId"), List.of("Main Store", "SKU-100"), AppDdlInventory.class);
        assertEquals(1, listedInventoryByStoreGoods.size());
        assertEquals("Main Store", listedInventoryByStoreGoods.get(0).store.name);
        assertEquals("SKU-100", listedInventoryByStoreGoods.get(0).goods.skuId);
        assertEquals(Integer.valueOf(3), listedInventoryByStoreGoods.get(0).safeQty);

        executeUpdate(repositoryUrl, "update PRODUCT set name = 'Typed Product' where productId = 'P-100'");
        AppDdlProduct foundProductWithSnapshot = repository.find("Product", "P-100", AppDdlProduct.class)
                .orElseThrow();
        assertEquals("P-100", foundProductWithSnapshot.productId);
        // Revision-bearing snapshots are authoritative while present; native rows are the fallback.
        assertEquals("Demo Product", foundProductWithSnapshot.name);

        runtime.getSnapshotStore().clear();

        AppDdlProduct foundProduct = repository.find("Product", "P-100", AppDdlProduct.class).orElseThrow();
        assertEquals("P-100", foundProduct.productId);
        assertEquals("Typed Product", foundProduct.name);

        AppDdlOrderHeader foundOrder = repository.require("OrderHeader", "ORDER-100", AppDdlOrderHeader.class);
        assertEquals("SO-100", foundOrder.orderNo);
        assertEquals("Created", foundOrder.status);
        assertEquals("ORDER-100",
                queryString(repositoryUrl, "select orderId from ORDER_HEADER where orderNo = 'SO-100'"));

        AppDdlProduct hydratedProduct = new AppDdlProduct();
        repository.hydrate("Product", "P-100", hydratedProduct);
        assertEquals("P-100", hydratedProduct.productId);
        assertEquals("Typed Product", hydratedProduct.name);

        AppDdlGoods hydratedGoods = new AppDdlGoods();
        repository.hydrate("Goods", "SKU-100", hydratedGoods);
        assertEquals("SKU-100", hydratedGoods.skuId);
        assertEquals("P-100", hydratedGoods.product.productId);
        assertEquals(19.5D, hydratedGoods.salePrice, 0.001D);

        AppDdlInventory hydratedInventory = new AppDdlInventory();
        hydratedInventory.goods = new AppDdlGoods();
        hydratedInventory.goods.skuId = "SKU-100";
        repository.hydrate("Inventory", "SKU-100", hydratedInventory);
        assertEquals("Main Store", hydratedInventory.store.name);
        assertEquals("SKU-100", hydratedInventory.goods.skuId);
        assertEquals(3, hydratedInventory.safeQty.intValue());

        AppDdlInventory branchInventory = new AppDdlInventory();
        branchInventory.store = new AppDdlStore();
        branchInventory.store.name = "Branch Store";
        branchInventory.goods = goods;
        branchInventory.availableQty = 9;
        branchInventory.safeQty = 1;
        repository.save("Inventory", CompositeObjectId.encode(List.of("Branch Store", goods.skuId)),
                branchInventory);

        AppDdlInventory ambiguousInventory = new AppDdlInventory();
        ambiguousInventory.goods = new AppDdlGoods();
        ambiguousInventory.goods.skuId = "SKU-100";
        IllegalStateException ambiguous = assertThrows(IllegalStateException.class,
                () -> repository.hydrate("Inventory", "SKU-100", ambiguousInventory));
        assertTrue(ambiguous.getMessage(), ambiguous.getMessage()
                .contains("APP DDL table INVENTORY has multiple rows for [goods_skuid]."));

        AppDdlInventory hydratedByCompositeId = new AppDdlInventory();
        repository.hydrate("Inventory",
                CompositeObjectId.encode(List.of("Main Store", "SKU-100")),
                hydratedByCompositeId);
        assertEquals("Main Store", hydratedByCompositeId.store.name);
        assertEquals("SKU-100", hydratedByCompositeId.goods.skuId);
        assertEquals(3, hydratedByCompositeId.safeQty.intValue());

        AppDdlOrderHeader hydratedOrder = new AppDdlOrderHeader();
        repository.hydrate("OrderHeader", "ORDER-100", hydratedOrder);
        assertEquals("SO-100", hydratedOrder.orderNo);
        assertEquals("Created", hydratedOrder.status);
    }

    @Test
    public void physicalJdbcObjectRepositoryKeepsRolePerformersInAppDdlPlatformColumns() throws Exception {
        String repositoryUrl = "jdbc:h2:mem:runtime-role-performer-app-ddl-"
                + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                rolePerformerAppDdlCatalogProperties().getBytes(StandardCharsets.UTF_8)));
        JdbcDataSource dataSource = h2DataSource();
        dataSource.setURL(repositoryUrl);
        new JdbcAppDdlSchemaManager(dataSource).ensureTables(catalog);
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore snapshotStore = new JdbcObjectSnapshotStore(dataSource,
                value -> writeJson(value));
        JdbcObjectRepository repository = new JdbcObjectRepository(snapshotStore, objectMapper)
                .useAppDdlCatalog(catalog);

        AppDdlRoleOnlyGoods goods = new AppDdlRoleOnlyGoods();
        goods.skuId = "SKU-ROLE-100";
        goods.title = "Role Only Goods";
        goods.salePrice = 19.5D;
        repository.save("Goods", goods.skuId, goods);
        repository.writeAppDdlPlatformColumn("Goods", goods, "product_productid", "P-ROLE-100");

        AppDdlRoleOnlyInventory inventory = new AppDdlRoleOnlyInventory();
        inventory.availableQty = 20;
        inventory.safeQty = 3;
        String inventoryObjectId = CompositeObjectId.encode(List.of("MAIN", goods.skuId));
        repository.save("Inventory", inventoryObjectId, inventory);
        repository.adjustAppDdlPlatformColumn("Inventory", inventoryObjectId, inventory,
                "lockedQty", BigDecimal.valueOf(2));
        repository.writeAppDdlPlatformColumn("Inventory", inventoryObjectId, inventory,
                "lockedQty", 5);
        repository.writeAppDdlPlatformColumn("Inventory", inventoryObjectId, null,
                "lockedQty", 7);
        repository.adjustAppDdlPlatformColumn("Inventory", inventoryObjectId, null,
                "lockedQty", BigDecimal.valueOf(2));

        assertEquals("P-ROLE-100",
                queryString(repositoryUrl,
                        "select product_productid from GOODS where skuId = 'SKU-ROLE-100'"));
        assertEquals("MAIN",
                queryString(repositoryUrl,
                        "select store_name from INVENTORY where goods_skuid = 'SKU-ROLE-100'"));
        assertEquals(9,
                queryInt(repositoryUrl,
                        "select lockedQty from INVENTORY where goods_skuid = 'SKU-ROLE-100'"));
        assertEquals(9, ((Number) repository.readAppDdlPlatformColumnByObjectId("Inventory",
                inventoryObjectId, "lockedQty").orElseThrow()).intValue());

        repository.save("Goods", goods.skuId, goods);
        repository.save("Inventory", inventoryObjectId, inventory);
        assertEquals("P-ROLE-100",
                queryString(repositoryUrl,
                        "select product_productid from GOODS where skuId = 'SKU-ROLE-100'"));
        assertEquals(9,
                queryInt(repositoryUrl,
                        "select lockedQty from INVENTORY where goods_skuid = 'SKU-ROLE-100'"));

        List<AppDdlRoleOnlyGoods> goodsByProduct = repository.findBy("Goods",
                "product_productid", "P-ROLE-100", AppDdlRoleOnlyGoods.class);
        assertEquals(1, goodsByProduct.size());
        assertEquals("SKU-ROLE-100", goodsByProduct.get(0).skuId);
        assertEquals("Role Only Goods", goodsByProduct.get(0).title);

        List<AppDdlRoleOnlyInventory> inventoryByStoreGoods = repository.findBy("Inventory",
                List.of("store_name", "goods_skuid"), List.of("MAIN", "SKU-ROLE-100"),
                AppDdlRoleOnlyInventory.class);
        assertEquals(1, inventoryByStoreGoods.size());
        assertEquals(Integer.valueOf(20), inventoryByStoreGoods.get(0).availableQty);
        assertEquals(Integer.valueOf(3), inventoryByStoreGoods.get(0).safeQty);

        snapshotStore.clear();
        AppDdlRoleOnlyInventory hydratedInventory = repository
                .find("Inventory", inventoryObjectId, AppDdlRoleOnlyInventory.class)
                .orElseThrow();
        assertEquals(Integer.valueOf(20), hydratedInventory.availableQty);
        assertEquals(Integer.valueOf(3), hydratedInventory.safeQty);
    }

    @Test
    public void generatedTccReservationRulesMaintainAppDdlPlatformColumns() throws Exception {
        String repositoryUrl = "jdbc:h2:mem:runtime-tcc-reservation-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                inventoryAppDdlCatalogProperties().getBytes(StandardCharsets.UTF_8)));
        JdbcDataSource dataSource = h2DataSource();
        dataSource.setURL(repositoryUrl);
        new JdbcAppDdlSchemaManager(dataSource).ensureTables(catalog);
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore snapshotStore = new JdbcObjectSnapshotStore(dataSource,
                value -> writeJson(value));
        JdbcObjectRepository repository = new JdbcObjectRepository(snapshotStore, objectMapper)
                .useAppDdlCatalog(catalog);
        JdbcTccReservationStore reservationStore = new JdbcTccReservationStore(dataSource);
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(snapshotStore, repository, type -> null)
                .useTccReservationStore(() -> reservationStore)
                .useTccReservationTtlMillis(() -> 60_000L);

        AppDdlStore store = new AppDdlStore();
        store.name = "Main Store";
        AppDdlGoods goods = new AppDdlGoods();
        goods.skuId = "SKU-100";
        AppDdlInventory inventory = new AppDdlInventory();
        inventory.store = store;
        inventory.goods = goods;
        inventory.availableQty = 20;
        inventory.safeQty = 3;
        Service decorated = runtime.decorate(new ReserveInventoryWithReservationService());

        new DefaultTransactionManager().execute(List.of(executor((AtomicService) decorated, inventory, 2)));

        assertEquals(18, inventory.availableQty.intValue());
        assertEquals(0,
                queryInt(repositoryUrl, "select lockedQty from INVENTORY where goods_skuid = 'SKU-100'"));
        assertEquals(18,
                queryInt(repositoryUrl, "select availableQty from INVENTORY where goods_skuid = 'SKU-100'"));
        List<TccReservationRecord> reservations = reservationStore.findAll();
        assertEquals(1, reservations.size());
        assertEquals("inventoryTcc", reservations.get(0).resourceId());
        assertEquals("reserveInventory", reservations.get(0).actionName());
        assertTrue(reservations.get(0).branchId().endsWith(":inventoryTcc:reserveInventory"));
        assertTrue(reservations.get(0).expiresAt() != null && !reservations.get(0).expiresAt().isBlank());
        assertEquals(TccReservationStatus.CONFIRMED, reservations.get(0).status());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(reservations.get(0).quantity()));

        AppDdlGoods rollbackGoods = new AppDdlGoods();
        rollbackGoods.skuId = "SKU-200";
        AppDdlInventory rollbackInventory = new AppDdlInventory();
        rollbackInventory.store = store;
        rollbackInventory.goods = rollbackGoods;
        rollbackInventory.availableQty = 20;
        rollbackInventory.safeQty = 3;
        Service rollbackReserve = runtime.decorate(new ReserveInventoryWithReservationService());
        assertThrows(IllegalStateException.class, () -> new DefaultTransactionManager().execute(List.of(
                executor((AtomicService) rollbackReserve, rollbackInventory, 2),
                executor(new FailingNoopAtomicService(), rollbackInventory)
        )));

        assertEquals(20, rollbackInventory.availableQty.intValue());
        assertEquals(0,
                queryInt(repositoryUrl, "select lockedQty from INVENTORY where goods_skuid = 'SKU-200'"));
        assertEquals(20,
                queryInt(repositoryUrl, "select availableQty from INVENTORY where goods_skuid = 'SKU-200'"));
        reservations = reservationStore.findAll();
        assertEquals(2, reservations.size());
        assertTrue(reservations.stream().anyMatch(record ->
                TccReservationStatus.CANCELLED == record.status()
                        && "inventoryTcc".equals(record.resourceId())
                        && "reserveInventory".equals(record.actionName())
                        && record.branchId() != null
                        && record.branchId().endsWith(":inventoryTcc:reserveInventory")
                        && record.expiresAt() != null
                        && BigDecimal.valueOf(2).compareTo(record.quantity()) == 0));
    }

    @Test
    public void tccReservationParticipantExposesTryingPlatformColumnBeforeConfirm() throws Exception {
        String repositoryUrl = "jdbc:h2:mem:runtime-tcc-reservation-trying-"
                + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                inventoryAppDdlCatalogProperties().getBytes(StandardCharsets.UTF_8)));
        JdbcDataSource dataSource = h2DataSource();
        dataSource.setURL(repositoryUrl);
        new JdbcAppDdlSchemaManager(dataSource).ensureTables(catalog);
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore snapshotStore = new JdbcObjectSnapshotStore(dataSource,
                value -> writeJson(value));
        JdbcObjectRepository repository = new JdbcObjectRepository(snapshotStore, objectMapper)
                .useAppDdlCatalog(catalog);
        JdbcTccReservationStore reservationStore = new JdbcTccReservationStore(dataSource);

        AppDdlStore store = new AppDdlStore();
        store.name = "Main Store";
        AppDdlGoods goods = new AppDdlGoods();
        goods.skuId = "SKU-TRYING";
        AppDdlInventory inventory = new AppDdlInventory();
        inventory.store = store;
        inventory.goods = goods;
        inventory.availableQty = 20;
        inventory.safeQty = 3;

        TccReservationParticipant participant = new TccReservationParticipant("inventoryTcc", "reserveInventory",
                List.of(new TccReservationRule("Inventory.availableQty", "Inventory.lockedQty")),
                () -> repository, () -> reservationStore, () -> 60_000L)
                .resolveRoleIdentities((roleType, values) -> Optional.of(
                        new RepositoryHydratingServiceProxy.RoleIdentity("Inventory",
                                List.of("Store.name", "Goods.skuId"),
                                List.of("Main Store", "SKU-TRYING"),
                                List.of("store_name", "goods_skuid"), "PERFORMER", true)));
        Object[] arguments = new Object[] {inventory, 2};
        participant.setArguments(arguments);
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC));

        participant.beforeTry(context);
        new ReserveInventoryWithReservationService().execute(arguments);
        participant.afterTry(context);

        assertEquals(18, inventory.availableQty.intValue());
        assertEquals(2,
                queryInt(repositoryUrl, "select lockedQty from INVENTORY where goods_skuid = 'SKU-TRYING'"));
        List<TccReservationRecord> reservations = reservationStore.findAll();
        assertEquals(1, reservations.size());
        assertEquals(TccReservationStatus.TRYING, reservations.get(0).status());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(reservations.get(0).quantity()));
        assertEquals(CompositeObjectId.encode(List.of("Main Store", "SKU-TRYING")),
                reservations.get(0).objectId());
        assertTrue(reservations.get(0).expiresAt() != null && !reservations.get(0).expiresAt().isBlank());

        participant.confirm(context);

        assertEquals(0,
                queryInt(repositoryUrl, "select lockedQty from INVENTORY where goods_skuid = 'SKU-TRYING'"));
        reservations = reservationStore.findAll();
        assertEquals(1, reservations.size());
        assertEquals(TccReservationStatus.CONFIRMED, reservations.get(0).status());
    }

    @Test
    public void generatedTccReservationRulesUseRuntimeRoleIdentityForAppDdlPlatformColumns() throws Exception {
        String repositoryUrl = "jdbc:h2:mem:runtime-tcc-reservation-role-identity-"
                + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                inventoryAppDdlCatalogProperties().getBytes(StandardCharsets.UTF_8)));
        JdbcDataSource dataSource = h2DataSource();
        dataSource.setURL(repositoryUrl);
        new JdbcAppDdlSchemaManager(dataSource).ensureTables(catalog);
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore snapshotStore = new JdbcObjectSnapshotStore(dataSource,
                value -> writeJson(value));
        JdbcObjectRepository repository = new JdbcObjectRepository(snapshotStore, objectMapper)
                .useAppDdlCatalog(catalog);
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(snapshotStore, repository, type -> null)
                .resolveRepositoryRoleIdentities((roleType, arguments) -> {
                    if (!"Inventory".equals(roleType)) {
                        return Optional.empty();
                    }
                    return Optional.of(new RepositoryHydratingServiceProxy.RoleIdentity("Inventory",
                            List.of("Store.name", "Goods.skuId"), List.of("Main Store", "SKU-RUNTIME"),
                            List.of("store_name", "goods_skuid"), "PERFORMER", true));
                });

        AppDdlInventory inventory = new AppDdlInventory();
        inventory.availableQty = 20;
        inventory.safeQty = 3;
        Service decorated = runtime.decorate(new ReserveInventoryWithReservationService());

        new DefaultTransactionManager().execute(List.of(executor((AtomicService) decorated, inventory, 2)));

        assertEquals(18, inventory.availableQty.intValue());
        assertEquals(0,
                queryInt(repositoryUrl, "select lockedQty from INVENTORY where goods_skuid = 'SKU-RUNTIME'"));
        assertEquals(18,
                queryInt(repositoryUrl, "select availableQty from INVENTORY where goods_skuid = 'SKU-RUNTIME'"));
    }

    @Test
    public void jdbcTccReservationStoreFindsExpiredTryingReservations() {
        JdbcTccReservationStore store = new JdbcTccReservationStore(h2DataSource());
        store.save(new TccReservationRecord("RSV-OLD", "BR-OLD", "REQ-OLD", "TX-OLD",
                "inventoryTcc", "reserveInventory", "Inventory.availableQty",
                "Inventory.lockedQty", BigDecimal.valueOf(2), TccReservationStatus.TRYING,
                "reserved", "2026-06-27T00:00:00Z", "2026-06-27T00:00:01Z", "STORE-1:SKU-1"));
        store.save(new TccReservationRecord("RSV-NEW", "BR-NEW", "REQ-NEW", "TX-NEW",
                "inventoryTcc", "reserveInventory", "Inventory.availableQty",
                "Inventory.lockedQty", BigDecimal.valueOf(3), TccReservationStatus.TRYING,
                "reserved", "2026-06-27T00:00:00Z", "2026-06-27T00:01:00Z"));
        store.save(new TccReservationRecord("RSV-DONE", "BR-DONE", "REQ-DONE", "TX-DONE",
                "inventoryTcc", "reserveInventory", "Inventory.availableQty",
                "Inventory.lockedQty", BigDecimal.valueOf(4), TccReservationStatus.CONFIRMED,
                "confirmed", "2026-06-27T00:00:00Z", "2026-06-27T00:00:01Z"));

        List<TccReservationRecord> expired = store.findExpiredTrying("2026-06-27T00:00:30Z");

        assertEquals(1, expired.size());
        assertEquals("RSV-OLD", expired.get(0).reservationId());
        assertEquals("BR-OLD", expired.get(0).branchId());
        assertEquals("STORE-1:SKU-1", expired.get(0).objectId());
    }

    @Test
    public void physicalJdbcObjectRepositoryRejectsInvalidAppDdlTablesFlag() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);

        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-invalid-appddl;DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables=yes\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(new ReserveSnapshotService()));

        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().stream().anyMatch(error -> error.contains(
                        "Repository objectStore jdbcObjectRepository adapter config appDdlTables"
                                + " must be true or false")));
    }

    @Test
    public void physicalJdbcObjectRepositoryAcceptsTrimmedBooleanFlags() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=SNAPSHOT_OBJECT\n"
                        + "table.SNAPSHOT_OBJECT.category=roles\n"
                        + "table.SNAPSHOT_OBJECT.businessType=SnapshotObject\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=jdbc:h2:mem:runtime-trimmed-appddl;"
                        + "DB_CLOSE_DELAY=-1\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.appDdlTables= true \n"
                        + "repository.objectStore.config.default= true \n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(new ReserveSnapshotService()));

        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(catalog.physicalRepositoryPlans().stream()
                .anyMatch(plan -> "objectStore".equals(plan.repositoryName())
                        && plan.appDdlTables()));
        assertTrue(runtime.getPhysicalRuntimeCatalog().defaultRepositoryAdapter().isPresent());
        assertEquals("objectStore",
                runtime.getPhysicalRuntimeCatalog().defaultRepositoryAdapter().get().repositoryName());
    }

    @Test
    public void jdbcTransactionScopeAllowsNestedDifferentDataSources() throws Exception {
        JdbcDataSource localDataSource = h2DataSource();
        JdbcDataSource quantumDataSource = h2DataSource();
        AtomicReference<Boolean> nestedLocalConnectionVisible = new AtomicReference<>(false);
        AtomicReference<Boolean> nestedQuantumConnectionVisible = new AtomicReference<>(false);

        JdbcTransactionScope.execute(localDataSource, localConnection -> {
            assertTrue(JdbcTransactionScope.currentConnection(localDataSource).isPresent());
            JdbcTransactionScope.execute(quantumDataSource, quantumConnection -> {
                nestedLocalConnectionVisible.set(JdbcTransactionScope.currentConnection(localDataSource).isPresent());
                nestedQuantumConnectionVisible.set(
                        JdbcTransactionScope.currentConnection(quantumDataSource).isPresent());
                return null;
            });
            assertTrue(JdbcTransactionScope.currentConnection(localDataSource).isPresent());
            return null;
        });

        assertTrue(nestedLocalConnectionVisible.get());
        assertTrue(nestedQuantumConnectionVisible.get());
    }

    @Test
    public void invalidJdbcQuantumStoreConfigurationFailsDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum,missingAdapter,unsupported\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.globalQuantum.config.datasourceUrl=\n"
                        + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.missingAdapter.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.unsupported.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.unsupported.adapter=objectSnapshot\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store globalQuantum jdbcQuantum adapter config datasourceUrl must not be blank")));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store globalQuantum jdbcQuantum adapter config journalStore must not be blank")));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store missingAdapter must declare a PHY adapter")));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store unsupported declares unsupported adapter objectSnapshot")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.quantumStoreValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage(), error.getMessage().contains("Quantum store missingAdapter"));
    }

    @Test
    public void runtimeRejectsIncompleteLogicalQuantumServiceContract() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.config.implementationStrategy=QUANTUM_SERVICE\n"
                        + "quantumStore.globalQuantum.config.accessMode=QUANTUM_SERVICE_ADAPTER\n"
                        + "quantumStore.globalQuantum.config.idempotencyMode=SERVICE_IDEMPOTENCY\n"
                        + "quantumStore.globalQuantum.config.serviceProtocol=LOGICAL_QSERVICE\n"
                        + "quantumStore.globalQuantum.config.reversalMode=SERVICE_REVERSAL\n"
                        + "quantumStore.globalQuantum.config.endpoint=quantum.example.test\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store globalQuantum quantumService adapter config namespace"
                                + " must not be blank")));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store globalQuantum quantumService adapter config endpoint"
                                + " must be an absolute http or https URI")));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store globalQuantum quantumService has no binding definition"
                                + " for CreditAccount.availableCredit")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.quantumStoreValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage(), error.getMessage().contains(
                "Quantum store globalQuantum quantumService adapter config namespace must not be blank"));
        assertTrue(error.getMessage(), error.getMessage().contains(
                "Quantum store globalQuantum quantumService adapter config endpoint"
                        + " must be an absolute http or https URI"));
        assertTrue(error.getMessage(), error.getMessage().contains(
                "Quantum store globalQuantum quantumService has no binding definition"
                        + " for CreditAccount.availableCredit"));
    }

    @Test
    public void runtimeAcceptsTrimmedQuantumContractValues() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.config.implementationStrategy= QUANTUM_SERVICE \n"
                        + "quantumStore.globalQuantum.config.accessMode= QUANTUM_SERVICE_ADAPTER \n"
                        + "quantumStore.globalQuantum.config.idempotencyMode= SERVICE_IDEMPOTENCY \n"
                        + "quantumStore.globalQuantum.config.reversalMode= NEGATIVE_DELTA \n"
                        + "quantumStore.globalQuantum.config.endpoint= https://quantum.example.test/commit \n"
                        + "quantumStore.globalQuantum.config.timeoutMillis= 3000 \n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalQuantumPlan plan = catalog.physicalQuantumPlans().get(0);

        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().isEmpty());
        assertEquals("QUANTUM_SERVICE", plan.implementationStrategy());
        assertEquals("QUANTUM_SERVICE_ADAPTER", plan.accessMode());
        assertEquals("SERVICE_IDEMPOTENCY", plan.idempotencyMode());
        assertEquals("NEGATIVE_DELTA", plan.reversalMode());
        assertEquals("https://quantum.example.test/commit", plan.endpoint());
        assertEquals("3000", plan.timeoutMillis());
    }

    @Test
    public void tccObjectStoreQuantumContractRequiresBackendQuantumSupport() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=quantum:*\n"
                        + "backend.seata.quantumStore=globalQuantum\n"
                        + "backend.seata.config.applicationId=credit-commerce\n"
                        + "backend.seata.config.transactionServiceGroup=credit-commerce-tx-group\n"
                        + "backend.seata.config.quantumSupported=true\n"
                        + "backend.seata.config.supportedTransactions=tcc\n"
                        + "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=tccObjectStore\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        PhysicalQuantumPlan plan = catalog.physicalQuantumPlans().get(0);
        DeploymentValidationPlan quantumPlan = catalog.deploymentValidationPlans().stream()
                .filter(item -> "QUANTUM_STORE".equals(item.category()))
                .findFirst()
                .orElseThrow();

        assertEquals("TCC_OBJECT_STORE", plan.implementationStrategy());
        assertEquals(List.of("tcc"), plan.backendSupportedTransactions());
        assertTrue(quantumPlan.errors().toString(),
                quantumPlan.errors().stream().anyMatch(error -> error.contains(
                        "Quantum access contract is incomplete: globalQuantum"
                                + ":contractAvailable=false:missing=quantumBackend")));
        assertTrue(catalog.deploymentValidationErrors().toString(),
                catalog.deploymentValidationErrors().containsAll(quantumPlan.errors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains("quantumBackend"));
    }

    @Test
    public void runtimeRejectsInvalidQuantumServiceTimeout() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=globalQuantum,inventoryQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.config.implementationStrategy=QUANTUM_SERVICE\n"
                        + "quantumStore.globalQuantum.config.accessMode=QUANTUM_SERVICE_ADAPTER\n"
                        + "quantumStore.globalQuantum.config.idempotencyMode=SERVICE_IDEMPOTENCY\n"
                        + "quantumStore.globalQuantum.config.reversalMode=NEGATIVE_DELTA\n"
                        + "quantumStore.globalQuantum.config.endpoint=https://quantum.example.test/commit\n"
                        + "quantumStore.globalQuantum.config.timeoutMillis=0\n"
                        + "quantumStore.inventoryQuantum.attributes=Inventory.availableQty\n"
                        + "quantumStore.inventoryQuantum.adapter=quantumService\n"
                        + "quantumStore.inventoryQuantum.config.implementationStrategy=QUANTUM_SERVICE\n"
                        + "quantumStore.inventoryQuantum.config.accessMode=QUANTUM_SERVICE_ADAPTER\n"
                        + "quantumStore.inventoryQuantum.config.idempotencyMode=SERVICE_IDEMPOTENCY\n"
                        + "quantumStore.inventoryQuantum.config.reversalMode=NEGATIVE_DELTA\n"
                        + "quantumStore.inventoryQuantum.config.endpoint=https://quantum.example.test/commit\n"
                        + "quantumStore.inventoryQuantum.config.timeoutMillis=slow\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store globalQuantum quantumService adapter config timeoutMillis"
                                + " must be positive")));
        assertTrue(catalog.quantumStoreValidationErrors().toString(),
                catalog.quantumStoreValidationErrors().stream().anyMatch(error -> error.contains(
                        "Quantum store inventoryQuantum quantumService adapter config timeoutMillis"
                                + " must be a number")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.quantumStoreValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage(), error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage(), error.getMessage().contains("timeoutMillis must be positive"));
        assertTrue(error.getMessage(), error.getMessage().contains("timeoutMillis must be a number"));
    }

    @Test
    public void quantumStoreValidationRejectsDuplicateStoreNames() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                        ("quantumStores=globalQuantum,globalQuantum\n"
                                + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit\n"
                                + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                                + "quantumStore.globalQuantum.config.datasourceUrl=jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1\n"
                                + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                                + "quantumStore.globalQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                                + "quantumStore.globalQuantum.config.journalTable=spp_quantum_journal\n"
                                + "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated physical runtime catalog quantum store declares duplicate value globalQuantum"));
    }

    @Test
    public void quantumStoreValidationRejectsDuplicateStoreAttributes() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                        ("quantumStores=globalQuantum\n"
                                + "quantumStore.globalQuantum.attributes="
                                + "CreditAccount.availableCredit,CreditAccount.availableCredit\n"
                                + "quantumStore.globalQuantum.adapter=jdbcQuantum\n"
                                + "quantumStore.globalQuantum.config.datasourceUrl=jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1\n"
                                + "quantumStore.globalQuantum.config.driverClassName=org.h2.Driver\n"
                                + "quantumStore.globalQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                                + "quantumStore.globalQuantum.config.journalTable=spp_quantum_journal\n"
                                + "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage().contains(
                "Generated physical runtime catalog quantum store attribute declares duplicate value "
                        + "CreditAccount.availableCredit"));
    }

    @Test
    public void quantumStoreValidationRejectsAttributesAssignedToMultipleStores() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("quantumStores=creditQuantum,orderQuantum\n"
                        + "quantumStore.creditQuantum.attributes=CreditAccount.availableCredit\n"
                        + "quantumStore.creditQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.creditQuantum.config.datasourceUrl=jdbc:h2:mem:spp-credit-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.creditQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.creditQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.creditQuantum.config.journalTable=spp_credit_quantum_journal\n"
                        + "quantumStore.creditQuantum.config.valueTable=spp_credit_quantum_value\n"
                        + "quantumStore.orderQuantum.attributes=CreditAccount.availableCredit,OrderAmount.occupiedAmount\n"
                        + "quantumStore.orderQuantum.adapter=jdbcQuantum\n"
                        + "quantumStore.orderQuantum.config.datasourceUrl=jdbc:h2:mem:spp-order-quantum;DB_CLOSE_DELAY=-1\n"
                        + "quantumStore.orderQuantum.config.driverClassName=org.h2.Driver\n"
                        + "quantumStore.orderQuantum.config.journalStore=com.servpp.service.transaction.JdbcQuantumJournalStore\n"
                        + "quantumStore.orderQuantum.config.journalTable=spp_order_quantum_journal\n"
                        + "quantumStore.orderQuantum.config.valueTable=spp_order_quantum_value\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.quantumStoreValidationErrors().size());
        assertTrue(catalog.quantumStoreValidationErrors().get(0).contains(
                "Quantum attribute CreditAccount.availableCredit"
                        + " is assigned to multiple runtime quantum stores: creditQuantum and orderQuantum"));
        assertEquals(catalog.quantumStoreValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("multiple runtime quantum stores"));
    }

    @Test
    public void physicalBackendAdaptersSurvivePhysicalCatalogReload() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerPhysicalBackendAdapter(() -> Set.of("seata"));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=seata\n"
                        + "backend.seata.bindings=quantum:snapshotLedger\n")
                        .getBytes(StandardCharsets.UTF_8)));
        String properties = "reserveSnapshot=" + ReserveSnapshotService.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        PhysicalBackendAssignment reserve = runtime.catalogGeneratedServices()
                .physicalBackendAssignments().get(0);

        assertEquals("seata", reserve.backendName());
        assertTrue(reserve.resolved());
        assertTrue(reserve.adapterAvailable());
        assertTrue(reserve.adapterSupportsTransaction());
        assertTrue(runtime.catalogGeneratedServices().physicalBackendValidationErrors().isEmpty());
        runtime.requireValidPhysicalBackendAssignments();
    }

    @Test
    public void remoteGeneratedServicesAreMetadataOnlyForPhysicalAssignments() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("backends=localJdbc\n"
                        + "backend.localJdbc.bindings=\n"
                        + "remoteServices=remoteSnapshotScenario\n"
                        + "remote.remoteSnapshotScenario.adapter=httpJson\n"
                        + "remote.remoteSnapshotScenario.config.endpoint=https://snapshot.example.test/scenario\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotScenario=" + RemoteSnapshotScenario.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.services().size());
        assertEquals("remoteSnapshotScenario", catalog.services().get(0).serviceName());
        assertTrue(catalog.services().get(0).remote());
        assertEquals(TransactionMode.SAGA, catalog.services().get(0).transactionMode());
        assertTrue(catalog.physicalBackendAssignments().isEmpty());
        assertTrue(catalog.physicalBackendValidationErrors().isEmpty());
        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertEquals("remoteSnapshotScenario", catalog.remoteServiceAssignments().get(0).serviceName());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals("httpJson", catalog.remoteServiceAssignments().get(0).adapterImplementationType());
        assertEquals("https://snapshot.example.test/scenario",
                catalog.remoteServiceAssignments().get(0).adapterConfiguration().get("endpoint"));
        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidRemoteServices();
        runtime.requireValidGeneratedServiceDeployment();

        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteSnapshotScenario");
            }

            @Override
            public String implementationType() {
                return "http-json";
            }

            @Override
            public Map<String, String> configuration() {
                return Map.of("endpoint", "https://customer.example.test/spp");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                return new SnapshotObject(request.id(), "remote-" + request.name());
            }
        });
        catalog = runtime.catalogGeneratedServices(List.of(new RemoteSnapshotScenario()));
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals("http-json", catalog.remoteServiceAssignments().get(0).adapterImplementationType());
        assertEquals("https://customer.example.test/spp",
                catalog.remoteServiceAssignments().get(0).adapterConfiguration().get("endpoint"));
        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidRemoteServices();
        runtime.requireValidGeneratedServiceDeployment();

        SnapshotObject response = (SnapshotObject) runtime.executeGeneratedService(
                "remoteSnapshotScenario", new SnapshotObject("C-REMOTE", "Alice"));
        assertEquals("C-REMOTE", response.id());
        assertEquals("remote-Alice", response.name());
    }

    @Test
    public void remoteGeneratedServiceMetadataKeepsRemoteCompensationRules() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));

        Service registered = DefaultServiceContainer.getInstance().getService("remoteReservePricing");
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(registered instanceof RemoteCompensationRuleProvider);
        assertEquals(List.of("remoteReleasePricing"),
                ((RemoteCompensationRuleProvider) registered).getRemoteCompensationServiceNames());
        assertEquals(List.of("remoteReleasePricing"),
                catalog.services().get(0).remoteCompensationServiceNames());
        assertTrue(catalog.services().get(0).compensationServiceNames().isEmpty());
        assertTrue(catalog.services().get(0).remote());
        assertEquals(1, catalog.recoveryDeploymentPlans().size());
        assertEquals("generatedRuntimeRecovery", catalog.recoveryDeploymentPlans().get(0).planName());
        assertTrue(catalog.recoveryDeploymentPlans().get(0).unifiedWorkItemView());
        assertEquals(List.of("REMOTE_COMPENSATION"), catalog.recoveryDeploymentPlans().get(0).workItemKinds());
        assertFalse(catalog.recoveryDeploymentPlans().get(0).tccRecoveryAvailable());
        assertTrue(catalog.recoveryDeploymentPlans().get(0).remoteCompensationRecoveryAvailable());
        assertEquals(List.of("remoteReservePricing"),
                catalog.recoveryDeploymentPlans().get(0).remoteCompensationSourceServiceNames());
    }

    @Test
    public void remoteCompensationTargetMustBeRegisteredForDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteReservePricing\n"
                        + "remote.remoteReservePricing.adapter=httpJson\n"
                        + "remote.remoteReservePricing.config.endpoint=https://pricing.example.test/reserve\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return null;
            }
        });

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(List.of("remoteReleasePricing"),
                catalog.remoteCompensationRules().get("remoteReservePricing"));
        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertEquals(1, catalog.remoteCompensationValidationErrors().size());
        assertTrue(catalog.remoteCompensationValidationErrors().get(0).contains(
                "Remote compensation service remoteReleasePricing referenced by remoteReservePricing"
                        + " is not registered as a generated service"));
        assertTrue(catalog.deploymentValidationPlans().stream()
                .anyMatch(plan -> "REMOTE_COMPENSATION".equals(plan.category())
                        && !plan.passed()
                        && plan.errors().containsAll(catalog.remoteCompensationValidationErrors())
                        && plan.errors().stream().anyMatch(error -> error.contains(
                                "Remote compensation target remoteReleasePricing is missing recovery policy"))));
        assertTrue(catalog.deploymentValidationPlans().stream()
                .anyMatch(plan -> "REMOTE_SERVICE".equals(plan.category())
                        && plan.passed()
                        && plan.errors().isEmpty()));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteCompensationValidationErrors()));
        assertTrue(catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote compensation target remoteReleasePricing is missing recovery policy")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("remoteReleasePricing"));
    }

    @Test
    public void remoteCompensationTargetMustHaveExecutableAdapterForDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return null;
            }
        });

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(2, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteReleasePricing has no registered adapter")));
        assertEquals(1, catalog.remoteCompensationValidationErrors().size());
        assertTrue(catalog.remoteCompensationValidationErrors().get(0).contains(
                "Remote compensation service remoteReleasePricing referenced by remoteReservePricing"
                        + " has no registered adapter"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteServiceValidationErrors()));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteCompensationValidationErrors()));
    }

    @Test
    public void remoteCompensationTargetPassesDeploymentValidationWhenAdapterIsExecutable() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteReservePricing,remoteReleasePricing\n"
                        + "remote.remoteReservePricing.adapter=httpJson\n"
                        + "remote.remoteReservePricing.config.endpoint=https://pricing.example.test/reserve\n"
                        + "remote.remoteReleasePricing.adapter=httpJson\n"
                        + "remote.remoteReleasePricing.config.endpoint=https://pricing.example.test/release\n"
                        + "remote.remoteReleasePricing.config.compensationMaxAttempts=2\n"
                        + "remote.remoteReleasePricing.config.compensationBackoffMillis=0\n"
                        + "remote.remoteReleasePricing.config.compensationManualInterventionRequired=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public String implementationType() {
                return "pricing-http-json";
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return null;
            }
        });

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(List.of("remoteReleasePricing"),
                catalog.remoteCompensationRules().get("remoteReservePricing"));
        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertTrue(catalog.remoteCompensationValidationErrors().isEmpty());
        DeploymentValidationPlan plan = catalog.deploymentValidationPlans().stream()
                .filter(item -> "REMOTE_COMPENSATION".equals(item.category()))
                .findFirst()
                .orElseThrow();
        assertTrue(plan.toString(), plan.passed());
        assertEquals("1", plan.metadata().get("ruleCount"));
        assertEquals("remoteReservePricing", plan.metadata().get("sourceServiceNames"));
        assertEquals("remoteReleasePricing", plan.metadata().get("targetServiceNames"));
        assertEquals("pricing-http-json", plan.metadata().get("targetAdapterImplementationTypes"));
        assertEquals("CATALOG_REMOTE", plan.metadata().get("targetAdapterRoles"));
        assertEquals("1", plan.metadata().get("executableTargetCount"));
        assertTrue(plan.metadata().toString(), plan.metadata().get("executableTargetBindings")
                .contains("remoteReleasePricing:physicalAdapter=httpJson"
                        + ":physicalRole=HTTP_JSON_EXTERNAL"));
        assertTrue(plan.metadata().toString(), plan.metadata().get("executableTargetBindings")
                .contains(":runtimeAdapter=pricing-http-json"
                        + ":runtimeRole=CATALOG_REMOTE:available=true:executable=true"));
        assertTrue(plan.metadata().toString(), plan.metadata().get("executableTargetBindings")
                .contains(":policy=true:attempts=2:backoff=0:manual=true"));
        assertEquals("0", plan.metadata().get("adapterUnavailableTargetCount"));
        assertEquals("0", plan.metadata().get("nonExecutableTargetCount"));
        assertEquals("1", plan.metadata().get("policyConfiguredTargetCount"));
        assertTrue(plan.metadata().toString(), plan.metadata().get("remoteCompensationPolicyBindings")
                .contains("remoteReleasePricing:configured=true"
                        + ":attempts=2:backoff=0:manual=true"));
        assertTrue(plan.metadata().toString(), plan.metadata().get("remoteCompensationPolicyBindings")
                .contains(":runtimeAdapter=pricing-http-json"
                        + ":runtimeRole=CATALOG_REMOTE:available=true:executable=true"));
        assertTrue(plan.metadata().toString(), plan.metadata().get("remoteCompensationPolicyMissingBindings").isBlank());
        assertTrue(catalog.deploymentValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void remoteCompensationSourceAndTargetMustExposeUpdateOrScenarioKindsForDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing="
                        + RemoteCompensatedQueryPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleaseQueryPricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return null;
            }
        });

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertEquals(List.of("remoteReleasePricing"),
                catalog.remoteCompensationRules().get("remoteReservePricing"));
        assertTrue(catalog.remoteCompensationValidationErrors().toString(),
                catalog.remoteCompensationValidationErrors().stream().anyMatch(error -> error.contains(
                        "Remote compensation source remoteReservePricing"
                                + " must be a remote atomic update service or remote scenario service")));
        assertTrue(catalog.remoteCompensationValidationErrors().toString(),
                catalog.remoteCompensationValidationErrors().stream().anyMatch(error -> error.contains(
                        "Remote compensation service remoteReleasePricing referenced by remoteReservePricing"
                                + " must be a remote atomic update service")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteCompensationValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("remote atomic update"));
    }

    @Test
    public void remoteCompensationTargetIsCalledThroughAdapterOnSagaRollback() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        List<String> adapterCalls = new ArrayList<>();
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public String implementationType() {
                return "pricing-http-json";
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                adapterCalls.add(serviceName + ":" + request.id());
                return null;
            }
        });
        InMemorySagaLogStore sagaLogStore = new InMemorySagaLogStore();
        SagaLogManager.setStore(sagaLogStore);
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-1", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-1", "fail"))
                )));

        assertEquals("failingNoop failed", error.getMessage());
        assertEquals(List.of("remoteReservePricing:PRICE-1", "remoteReleasePricing:PRICE-1"), adapterCalls);
        assertEquals(1, sagaLogStore.findAll().size());
        assertEquals("remoteReleasePricing", sagaLogStore.findAll().get(0).serviceName());
        assertEquals("REMOTE_COMPENSATION_DONE", sagaLogStore.findAll().get(0).status());
        assertEquals("remote compensated remoteReservePricing", sagaLogStore.findAll().get(0).message());
    }

    @Test
    public void remoteCompensationFailureKeepsSuppressedErrorAndSagaEvidence() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        List<String> adapterCalls = new ArrayList<>();
        boolean[] recoveryEnabled = {false};
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                adapterCalls.add(serviceName + ":" + request.id());
                if ("remoteReleasePricing".equals(serviceName) && !recoveryEnabled[0]) {
                    throw new IllegalStateException("remote pricing release failed");
                }
                return null;
            }
        });
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-FAIL", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-FAIL", "fail"))
                )));

        assertEquals("failingNoop failed", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
        assertTrue(error.getSuppressed()[0].getMessage().contains("remote pricing release failed"));
        assertEquals(1, runtime.findSagaLogs().size());
        assertEquals("remoteReleasePricing", runtime.findSagaLogs().get(0).serviceName());
        assertEquals("REMOTE_COMPENSATION_FAILED", runtime.findSagaLogs().get(0).status());
        assertEquals("remote compensation failed for remoteReservePricing: remote pricing release failed",
                runtime.findSagaLogs().get(0).message());
        assertEquals(1, runtime.findRemoteCompensationRecoveryCandidates().size());
        assertEquals("remoteReleasePricing",
                runtime.findRemoteCompensationRecoveryCandidates().get(0).serviceName());
        assertEquals("REMOTE_COMPENSATION_FAILED",
                runtime.findRemoteCompensationRecoveryCandidates().get(0).status());
        assertEquals(true,
                runtime.findRemoteCompensationRecoveryCandidates().get(0).recoveryPayloadAvailable());
        assertEquals(false,
                runtime.findRemoteCompensationRecoveryCandidates().get(0).manualInterventionRequired());
        List<RecoveryWorkItem> recoveryWorkItems = runtime.findRecoveryWorkItems();
        assertEquals(1, recoveryWorkItems.size());
        RecoveryWorkItem workItem = recoveryWorkItems.get(0);
        assertEquals("REMOTE_COMPENSATION", workItem.kind());
        assertEquals("remoteReleasePricing", workItem.serviceName());
        assertEquals("REMOTE_COMPENSATION_FAILED", workItem.status());
        assertEquals("RETRY", workItem.suggestedAction());
        assertEquals("Remote compensation failed and replay payload is available.", workItem.actionReason());
        assertEquals(true, workItem.recoveryPayloadAvailable());
        assertEquals(false, workItem.manualInterventionRequired());
        assertEquals(false, workItem.expired());
        assertEquals(null, workItem.tccCandidate());
        assertEquals("remoteReleasePricing", workItem.remoteCompensationCandidate().serviceName());
        assertEquals(List.of("remoteReservePricing:PRICE-FAIL", "remoteReleasePricing:PRICE-FAIL"), adapterCalls);

        recoveryEnabled[0] = true;
        String sagaId = runtime.findRemoteCompensationRecoveryCandidates().get(0).sagaId();
        var recovered = runtime.retryRemoteCompensationRecovery(sagaId);

        assertEquals("REMOTE_COMPENSATION_RECOVERED", recovered.status());
        assertEquals("remoteReleasePricing", recovered.serviceName());
        assertEquals(2, runtime.findSagaLogs().size());
        assertEquals("REMOTE_COMPENSATION_RECOVERED", runtime.findSagaLogs().get(1).status());
        assertEquals(true, runtime.findSagaLogs().get(1).recoveryPayload() != null
                && !runtime.findSagaLogs().get(1).recoveryPayload().isBlank());
        assertEquals(List.of("remoteReservePricing:PRICE-FAIL",
                "remoteReleasePricing:PRICE-FAIL",
                "remoteReleasePricing:PRICE-FAIL"), adapterCalls);
    }

    @Test
    public void remoteCompensationRecoveryScanRetriesSelectedCandidates() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        List<String> adapterCalls = new ArrayList<>();
        boolean[] recoveryEnabled = {false};
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                adapterCalls.add(serviceName + ":" + request.id());
                if ("remoteReleasePricing".equals(serviceName) && !recoveryEnabled[0]) {
                    throw new IllegalStateException("remote pricing release failed");
                }
                return null;
            }
        });
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");
        assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-SCAN", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-SCAN", "fail"))
                )));

        var skipped = runtime.recoverRemoteCompensationCandidates(candidate ->
                RemoteCompensationRecoveryDecision.SKIP);

        assertEquals(1, skipped.size());
        assertEquals(RemoteCompensationRecoveryDecision.SKIP, skipped.get(0).decision());
        assertEquals(null, skipped.get(0).recoveredLog());
        assertEquals(1, runtime.findSagaLogs().size());

        recoveryEnabled[0] = true;
        var retried = runtime.recoverRemoteCompensationCandidates(candidate ->
                RemoteCompensationRecoveryDecision.RETRY);

        assertEquals(1, retried.size());
        assertEquals(RemoteCompensationRecoveryDecision.RETRY, retried.get(0).decision());
        assertEquals("REMOTE_COMPENSATION_RECOVERED", retried.get(0).recoveredLog().status());
        assertEquals(null, retried.get(0).errorMessage());
        assertEquals(2, runtime.findSagaLogs().size());
        assertEquals(List.of("remoteReservePricing:PRICE-SCAN",
                "remoteReleasePricing:PRICE-SCAN",
                "remoteReleasePricing:PRICE-SCAN"), adapterCalls);
    }

    @Test
    public void remoteCompensationParticipantAttemptsAllTargetsWhenOneFails() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing="
                        + MultiRemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\nremoteAuditPricing=" + RemoteAuditPricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        List<String> adapterCalls = new ArrayList<>();
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing", "remoteAuditPricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                adapterCalls.add(serviceName + ":" + request.id());
                if ("remoteReleasePricing".equals(serviceName)) {
                    throw new IllegalStateException("remote pricing release failed");
                }
                return null;
            }
        });
        InMemorySagaLogStore sagaLogStore = new InMemorySagaLogStore();
        SagaLogManager.setStore(sagaLogStore);
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-MULTI", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-MULTI", "fail"))
                )));

        assertEquals("failingNoop failed", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
        assertEquals("remote pricing release failed", error.getSuppressed()[0].getMessage());
        assertEquals(List.of("remoteReservePricing:PRICE-MULTI",
                "remoteReleasePricing:PRICE-MULTI",
                "remoteAuditPricing:PRICE-MULTI"), adapterCalls);
        assertEquals(2, sagaLogStore.findAll().size());
        assertEquals("REMOTE_COMPENSATION_FAILED", sagaLogStore.findAll().get(0).status());
        assertEquals("REMOTE_COMPENSATION_DONE", sagaLogStore.findAll().get(1).status());
        assertEquals("remoteAuditPricing", sagaLogStore.findAll().get(1).serviceName());
    }

    @Test
    public void remoteCompensationRetriesTransientFailureBeforeSagaFailureEscalation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RetryableRemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        List<String> adapterCalls = new ArrayList<>();
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                adapterCalls.add(serviceName + ":" + request.id());
                if ("remoteReleasePricing".equals(serviceName)
                        && adapterCalls.stream().filter(call -> call.startsWith("remoteReleasePricing:")).count() == 1) {
                    throw new IllegalStateException("remote pricing release retryable failure");
                }
                return null;
            }
        });
        InMemorySagaLogStore sagaLogStore = new InMemorySagaLogStore();
        SagaLogManager.setStore(sagaLogStore);
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-RETRY", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-RETRY", "fail"))
                )));

        assertEquals("failingNoop failed", error.getMessage());
        assertEquals(0, error.getSuppressed().length);
        assertEquals(List.of("remoteReservePricing:PRICE-RETRY",
                "remoteReleasePricing:PRICE-RETRY",
                "remoteReleasePricing:PRICE-RETRY"), adapterCalls);
        assertEquals(2, sagaLogStore.findAll().size());
        assertEquals("REMOTE_COMPENSATION_RETRY", sagaLogStore.findAll().get(0).status());
        assertEquals("REMOTE_COMPENSATION_DONE", sagaLogStore.findAll().get(1).status());
    }

    @Test
    public void remoteCompensationPolicyCanComeFromPhysicalRemoteAdapterConfig() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteReleasePricing\n"
                        + "remote.remoteReleasePricing.adapter=httpJson\n"
                        + "remote.remoteReleasePricing.config.endpoint=https://pricing.example.test/release\n"
                        + "remote.remoteReleasePricing.config.compensationMaxAttempts=2\n"
                        + "remote.remoteReleasePricing.config.compensationBackoffMillis=0\n"
                        + "remote.remoteReleasePricing.config.compensationManualInterventionRequired=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        List<String> adapterCalls = new ArrayList<>();
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                adapterCalls.add(serviceName + ":" + request.id());
                if ("remoteReleasePricing".equals(serviceName)
                        && adapterCalls.stream().filter(call -> call.startsWith("remoteReleasePricing:")).count() == 1) {
                    throw new IllegalStateException("remote pricing release configured retry");
                }
                return null;
            }
        });
        InMemorySagaLogStore sagaLogStore = new InMemorySagaLogStore();
        SagaLogManager.setStore(sagaLogStore);
        AtomicService release = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReleasePricing");
        assertTrue(release instanceof RemoteCompensationPolicyProvider);
        RemoteCompensationPolicyProvider policy = (RemoteCompensationPolicyProvider) release;
        assertEquals(2, policy.getRemoteCompensationMaxAttempts());
        assertEquals(0L, policy.getRemoteCompensationBackoffMillis());
        assertTrue(policy.isRemoteCompensationManualInterventionRequired());
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-PHY", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-PHY", "fail"))
                )));

        assertEquals("failingNoop failed", error.getMessage());
        assertEquals(List.of("remoteReservePricing:PRICE-PHY",
                "remoteReleasePricing:PRICE-PHY",
                "remoteReleasePricing:PRICE-PHY"), adapterCalls);
        assertEquals(2, sagaLogStore.findAll().size());
        assertEquals("REMOTE_COMPENSATION_RETRY", sagaLogStore.findAll().get(0).status());
        assertEquals("REMOTE_COMPENSATION_DONE", sagaLogStore.findAll().get(1).status());
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        assertTrue(catalog.deploymentValidationPlans().stream()
                .anyMatch(plan -> "REMOTE_COMPENSATION".equals(plan.category())
                        && plan.passed()
                        && "1".equals(plan.metadata().get("ruleCount"))
                        && "remoteReservePricing".equals(plan.metadata().get("sourceServiceNames"))
                        && "remoteReleasePricing".equals(plan.metadata().get("targetServiceNames"))
                        && "httpJson".equals(plan.metadata().get("targetAdapterNames"))
                        && "HTTP_JSON_EXTERNAL".equals(plan.metadata().get("targetPhysicalAdapterRoles"))
                        && "CATALOG_REMOTE".equals(plan.metadata().get("targetAdapterRoles"))
                        && "1".equals(plan.metadata().get("executableTargetCount"))
                        && plan.metadata().get("executableTargetBindings")
                                .contains("remoteReleasePricing:physicalAdapter=httpJson"
                                        + ":physicalRole=HTTP_JSON_EXTERNAL")
                        && plan.metadata().get("executableTargetBindings")
                                .contains(":runtimeRole=CATALOG_REMOTE:available=true:executable=true")
                        && plan.metadata().get("executableTargetBindings")
                                .contains(":endpoint=https://pricing.example.test/release:timeout=-")
                        && plan.metadata().get("executableTargetBindings")
                                .contains(":policy=true:attempts=2:backoff=0:manual=true")
                        && "1".equals(plan.metadata().get("policyConfiguredTargetCount"))
                        && "1/1".equals(plan.metadata().get("policyConfiguredCoverage"))
                        && "2".equals(plan.metadata().get("compensationMaxAttempts"))
                        && "0".equals(plan.metadata().get("compensationBackoffMillis"))
                        && "true".equals(plan.metadata().get("manualInterventionRequiredValues"))
                        && plan.metadata().get("remoteCompensationBindings")
                                .contains("remoteReservePricing->remoteReleasePricing"
                                        + ":physicalAdapter=httpJson:physicalRole=HTTP_JSON_EXTERNAL")
                        && plan.metadata().get("remoteCompensationBindings")
                                .contains(":runtimeRole=CATALOG_REMOTE:available=true:executable=true")
                        && plan.metadata().get("remoteCompensationBindings")
                                .contains(":endpoint=https://pricing.example.test/release:timeout=-")
                        && plan.metadata().get("remoteCompensationBindings")
                                .contains(":repository=-:repositoryAdapter=-:repositoryRole=-")
                        && plan.metadata().get("remoteCompensationBindings")
                                .contains(":datasource=-:repositoryDriver=-:policy=true"
                                        + ":attempts=2:backoff=0:manual=true")
                        && plan.metadata().get("remoteCompensationPolicyBindings")
                                .contains("remoteReleasePricing:configured=true"
                                        + ":attempts=2:backoff=0:manual=true")
                        && plan.metadata().get("remoteCompensationPolicyBindings")
                                .contains(":physicalAdapter=httpJson:physicalRole=HTTP_JSON_EXTERNAL")
                        && plan.metadata().get("remoteCompensationPolicyBindings")
                                .contains(":runtimeRole=CATALOG_REMOTE:available=true:executable=true")
                        && plan.metadata().get("remoteCompensationPolicyMissingBindings").isEmpty()));
    }

    @Test
    public void remoteCompensationPhysicalPolicyConfigIsValidated() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteReleasePricing\n"
                        + "remote.remoteReleasePricing.adapter=httpJson\n"
                        + "remote.remoteReleasePricing.config.endpoint=https://pricing.example.test/release\n"
                        + "remote.remoteReleasePricing.config.compensationMaxAttempts=0\n"
                        + "remote.remoteReleasePricing.config.compensationBackoffMillis=-1\n"
                        + "remote.remoteReleasePricing.config.compensationManualInterventionRequired=maybe\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return null;
            }
        });

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceValidationErrors().toString(),
                catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                        "Remote service remoteReleasePricing compensationMaxAttempts"
                                + " must be a positive integer")));
        assertTrue(catalog.remoteServiceValidationErrors().toString(),
                catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                        "Remote service remoteReleasePricing compensationBackoffMillis"
                                + " must be a non-negative integer")));
        assertTrue(catalog.remoteServiceValidationErrors().toString(),
                catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                        "Remote service remoteReleasePricing compensationManualInterventionRequired"
                                + " must be true or false")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteServiceValidationErrors()));
    }

    @Test
    public void remoteCompensationPolicyRejectsInvalidManualInterventionFlagAtRuntime() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteReleasePricing\n"
                        + "remote.remoteReleasePricing.adapter=httpJson\n"
                        + "remote.remoteReleasePricing.config.endpoint=https://pricing.example.test/release\n"
                        + "remote.remoteReleasePricing.config.compensationMaxAttempts=2\n"
                        + "remote.remoteReleasePricing.config.compensationBackoffMillis=0\n"
                        + "remote.remoteReleasePricing.config.compensationManualInterventionRequired=maybe\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReleasePricing=" + RemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        AtomicService release = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReleasePricing");
        RemoteCompensationPolicyProvider policy = (RemoteCompensationPolicyProvider) release;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                policy::isRemoteCompensationManualInterventionRequired);

        assertEquals("Remote service remoteReleasePricing compensationManualInterventionRequired"
                + " must be true or false, but was maybe.", error.getMessage());
    }

    @Test
    public void remoteCompensationFailureMarksManualInterventionWhenPolicyRequiresIt() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteReservePricing=" + RemoteCompensatedPricingService.class.getName()
                        + "\nremoteReleasePricing=" + ManualRemoteReleasePricingService.class.getName()
                        + "\n").getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteReservePricing", "remoteReleasePricing");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                if ("remoteReleasePricing".equals(serviceName)) {
                    throw new IllegalStateException("remote pricing release requires operator");
                }
                return null;
            }
        });
        AtomicService reserve = (AtomicService) DefaultServiceContainer.getInstance()
                .getService("remoteReservePricing");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(
                        executor(reserve, new SnapshotObject("PRICE-MANUAL", "reserve")),
                        executor(new FailingNoopAtomicService(), new SnapshotObject("PRICE-MANUAL", "fail"))
                )));

        assertEquals("failingNoop failed", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
        assertEquals(4, runtime.findSagaLogs().size());
        assertEquals("REMOTE_COMPENSATION_RETRY", runtime.findSagaLogs().get(0).status());
        assertEquals("REMOTE_COMPENSATION_RETRY", runtime.findSagaLogs().get(1).status());
        assertEquals("REMOTE_COMPENSATION_FAILED", runtime.findSagaLogs().get(2).status());
        assertEquals("REMOTE_COMPENSATION_MANUAL_REQUIRED", runtime.findSagaLogs().get(3).status());
        assertTrue(runtime.findSagaLogs().get(3).message().contains("requires manual intervention"));
        assertEquals(2, runtime.findRemoteCompensationRecoveryCandidates().size());
        assertEquals("REMOTE_COMPENSATION_FAILED",
                runtime.findRemoteCompensationRecoveryCandidates().get(0).status());
        assertEquals(true,
                runtime.findRemoteCompensationRecoveryCandidates().get(0).recoveryPayloadAvailable());
        assertEquals("REMOTE_COMPENSATION_MANUAL_REQUIRED",
                runtime.findRemoteCompensationRecoveryCandidates().get(1).status());
        assertEquals(true,
                runtime.findRemoteCompensationRecoveryCandidates().get(1).recoveryPayloadAvailable());
        assertEquals(true,
                runtime.findRemoteCompensationRecoveryCandidates().get(1).manualInterventionRequired());
        List<RecoveryWorkItem> recoveryWorkItems = runtime.findRecoveryWorkItems();
        assertEquals(2, recoveryWorkItems.size());
        assertEquals("RETRY", recoveryWorkItems.get(0).suggestedAction());
        assertEquals("MANUAL", recoveryWorkItems.get(1).suggestedAction());
        assertEquals("Remote compensation policy requires manual intervention.",
                recoveryWorkItems.get(1).actionReason());
    }

    @Test
    public void remoteServiceAdapterMustImplementExecuteForDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotScenario\n"
                        + "remote.remoteSnapshotScenario.adapter=httpJson\n"
                        + "remote.remoteSnapshotScenario.config.endpoint=https://snapshot.example.test/scenario\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotScenario=" + RemoteSnapshotScenario.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(() -> Set.of("remoteSnapshotScenario"));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(!catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "Remote service remoteSnapshotScenario has registered adapter"));
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "but the adapter is not executable"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteServiceValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("but the adapter is not executable"));
    }

    @Test
    public void remoteServiceValidationRejectsMultipleRegisteredAdaptersForSameService() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotScenario\n"
                        + "remote.remoteSnapshotScenario.adapter=httpJson\n"
                        + "remote.remoteSnapshotScenario.config.endpoint=https://snapshot.example.test/scenario\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotScenario=" + RemoteSnapshotScenario.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteSnapshotScenario");
            }

            @Override
            public String implementationType() {
                return "httpJsonPrimary";
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return arguments[0];
            }
        });
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteSnapshotScenario");
            }

            @Override
            public String implementationType() {
                return "httpJsonBackup";
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                return arguments[0];
            }
        });

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals("httpJsonPrimary",
                catalog.remoteServiceAssignments().get(0).adapterImplementationType());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "Remote service remoteSnapshotScenario has multiple registered adapters:"
                        + " httpJsonPrimary, httpJsonBackup"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteServiceValidationErrors()));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("multiple registered adapters"));
    }

    @Test
    public void physicalCatalogRemoteAdapterExposesConfigurationButRequiresExecutableOverride() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotScenario\n"
                        + "remote.remoteSnapshotScenario.adapter=metadataOnly\n"
                        + "remote.remoteSnapshotScenario.config.endpoint=https://snapshot.example.test/spp\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotScenario=" + RemoteSnapshotScenario.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(!catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals("metadataOnly", catalog.remoteServiceAssignments().get(0).adapterImplementationType());
        assertEquals("https://snapshot.example.test/spp",
                catalog.remoteServiceAssignments().get(0).adapterConfiguration().get("endpoint"));
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "but the adapter is not executable"));

        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteSnapshotScenario");
            }

            @Override
            public String implementationType() {
                return "httpJson";
            }

            @Override
            public Map<String, String> configuration() {
                return Map.of("endpoint", "https://override.example.test/spp");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                return new SnapshotObject(request.id(), "override-" + request.name());
            }
        });
        catalog = runtime.catalogGeneratedServices();
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals("https://override.example.test/spp",
                catalog.remoteServiceAssignments().get(0).adapterConfiguration().get("endpoint"));
        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();
    }

    @Test
    public void physicalCatalogHttpJsonRemoteAdapterRequiresEndpointForDeploymentValidation() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=httpJson\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals("httpJson", catalog.remoteServiceAssignments().get(0).adapterImplementationType());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "Remote service remoteSnapshotQuery httpJson adapter must configure endpoint"));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("httpJson adapter must configure endpoint"));
    }

    @Test
    public void remoteServiceValidationRejectsDuplicatePhysicalAdapters() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                        ("remoteServices=remoteSnapshotQuery,remoteSnapshotQuery\n"
                                + "remote.remoteSnapshotQuery.adapter=httpJson\n"
                                + "remote.remoteSnapshotQuery.config.endpoint=https://snapshot.example.test/spp\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated physical runtime catalog remote service declares duplicate value remoteSnapshotQuery"));
    }

    @Test
    public void physicalCatalogHttpJsonRemoteAdapterRejectsInvalidEndpoint() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=httpJson\n"
                        + "remote.remoteSnapshotQuery.config.endpoint=://bad-endpoint\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "Remote service remoteSnapshotQuery httpJson adapter endpoint is invalid"));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("endpoint is invalid"));
    }

    @Test
    public void physicalCatalogHttpJsonRemoteAdapterRequiresAbsoluteHttpEndpoint() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery,remoteSnapshotScenario\n"
                        + "remote.remoteSnapshotQuery.adapter=httpJson\n"
                        + "remote.remoteSnapshotQuery.config.endpoint=/relative/spp\n"
                        + "remote.remoteSnapshotScenario.adapter=httpJson\n"
                        + "remote.remoteSnapshotScenario.config.endpoint=ftp://snapshot.example.test/spp\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n"
                        + "remoteSnapshotScenario=" + RemoteSnapshotScenario.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(2, catalog.remoteServiceAssignments().size());
        assertEquals(2, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotQuery httpJson adapter endpoint is invalid: /relative/spp")));
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotScenario httpJson adapter endpoint is invalid:"
                        + " ftp://snapshot.example.test/spp")));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("endpoint is invalid: /relative/spp"));
        assertTrue(error.getMessage().contains("endpoint is invalid: ftp://snapshot.example.test/spp"));
    }

    @Test
    public void physicalCatalogHttpJsonRemoteAdapterValidatesTimeoutMillis() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery,remoteSnapshotScenario\n"
                        + "remote.remoteSnapshotQuery.adapter=httpJson\n"
                        + "remote.remoteSnapshotQuery.config.endpoint=https://snapshot.example.test/spp\n"
                        + "remote.remoteSnapshotQuery.config.timeoutMillis=0\n"
                        + "remote.remoteSnapshotScenario.adapter=httpJson\n"
                        + "remote.remoteSnapshotScenario.config.endpoint=https://snapshot.example.test/scenario\n"
                        + "remote.remoteSnapshotScenario.config.timeoutMillis=slow\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n"
                        + "remoteSnapshotScenario=" + RemoteSnapshotScenario.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(2, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().stream()
                .allMatch(item -> item.adapterExecutable()));
        assertEquals(2, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotQuery httpJson adapter timeoutMillis must be positive")));
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotScenario httpJson adapter timeoutMillis must be a number")));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("timeoutMillis must be positive"));
        assertTrue(error.getMessage().contains("timeoutMillis must be a number"));
    }

    @Test
    public void httpJsonRemoteAdapterRejectsInvalidTimeoutAtExecution() {
        HttpJsonRemoteServiceAdapter adapter = new HttpJsonRemoteServiceAdapter(
                "remoteSnapshotQuery",
                Map.of("endpoint", "https://snapshot.example.test/spp",
                        "timeoutMillis", "slow"),
                new ObjectMapper(),
                java.net.http.HttpClient.newHttpClient());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.execute("remoteSnapshotQuery", Map.of("objectId", "SO-1")));

        assertTrue(error.getMessage().contains(
                "Remote service remoteSnapshotQuery httpJson adapter timeoutMillis must be a number"));
    }

    @Test
    public void httpJsonRemoteAdapterWrapsInvalidEndpointAtExecution() {
        HttpJsonRemoteServiceAdapter adapter = new HttpJsonRemoteServiceAdapter(
                "remoteSnapshotQuery",
                Map.of("endpoint", "${spp.test.remote.endpoint"),
                new ObjectMapper(),
                java.net.http.HttpClient.newHttpClient());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.execute("remoteSnapshotQuery", Map.of("objectId", "SO-1")));

        assertTrue(error.getMessage().contains(
                "Remote service remoteSnapshotQuery httpJson adapter endpoint is invalid"));
    }

    @Test
    public void declaredHttpJsonRemoteAdapterConfigurationIsValidatedWithoutServiceAssignments() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=httpJson\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceAssignments().isEmpty());
        assertEquals(2, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotQuery declares a physical adapter"
                        + " but is not registered as a generated remote service")));
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotQuery httpJson adapter must configure endpoint")));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteServiceValidationErrors()));
        assertTrue(catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                "PHY remote adapter remoteSnapshotQuery does not match any APP remote service")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("httpJson adapter must configure endpoint"));
        assertTrue(error.getMessage().contains("not registered as a generated remote service"));
    }

    @Test
    public void declaredRemoteAdapterMustMatchRegisteredGeneratedRemoteService() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=httpJson\n"
                        + "remote.remoteSnapshotQuery.config.endpoint=https://snapshot.example.test/spp\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceAssignments().isEmpty());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "Remote service remoteSnapshotQuery declares a physical adapter"
                        + " but is not registered as a generated remote service"));
        assertTrue(catalog.deploymentValidationErrors().containsAll(catalog.remoteServiceValidationErrors()));
        assertTrue(catalog.deploymentValidationErrors().stream().anyMatch(error -> error.contains(
                "PHY remote adapter remoteSnapshotQuery does not match any APP remote service")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                runtime::requireValidGeneratedServiceDeployment);
        assertTrue(error.getMessage().contains("Invalid generated service deployment"));
        assertTrue(error.getMessage().contains("not registered as a generated remote service"));
    }

    @Test
    public void physicalCatalogHttpJsonRemoteAdapterExecutesTypedRemoteService() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/spp/query", exchange -> {
            try {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"id\":\"C-REMOTE\",\"name\":\"http-Alice\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            LocalGeneratedServiceRuntime runtime =
                    LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
            String endpoint = "http://localhost:" + server.getAddress().getPort() + "/spp/query";
            runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                    ("remoteServices=remoteSnapshotQuery\n"
                            + "remote.remoteSnapshotQuery.adapter=httpJson\n"
                            + "remote.remoteSnapshotQuery.config.endpoint=" + endpoint + "\n")
                            .getBytes(StandardCharsets.UTF_8)));
            runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                    new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                            .getBytes(StandardCharsets.UTF_8)));

            GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

            assertEquals(1, catalog.remoteServiceAssignments().size());
            assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
            assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
            assertEquals("httpJson", catalog.remoteServiceAssignments().get(0).adapterImplementationType());
            assertEquals(endpoint, catalog.remoteServiceAssignments().get(0).adapterConfiguration().get("endpoint"));
            assertEquals(1, catalog.appSemanticMigrationPlans().stream()
                    .filter(plan -> "REMOTE_EXTERNAL_ADAPTER".equals(plan.migrationType())
                            && "remoteSnapshotQuery".equals(plan.serviceName())
                            && "httpJson".equals(plan.adapterName())
                            && plan.externalImplementation()
                            && !plan.referenceImplementation())
                    .count());
            assertEquals(1, catalog.appSemanticMigrationSummaryPlans().stream()
                    .filter(plan -> "REMOTE_EXTERNAL_ADAPTER".equals(plan.migrationType())
                            && plan.totalCount() == 1
                            && plan.externalImplementationCount() == 1
                            && plan.referenceImplementationCount() == 0
                            && plan.serviceNames().contains("remoteSnapshotQuery")
                            && plan.adapterNames().contains("httpJson"))
                    .count());
            assertTrue(catalog.appSemanticMigrationWarnings().stream()
                    .noneMatch(warning -> warning.contains("external domain adapter")));
            assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
            runtime.requireValidGeneratedServiceDeployment();

            RemoteSnapshotQuery query = (RemoteSnapshotQuery) DefaultServiceContainer.getInstance()
                    .getService("remoteSnapshotQuery");
            SnapshotObject response = query.queryRemote(new SnapshotObject("C-REMOTE", "Alice"));

            assertEquals("C-REMOTE", response.id());
            assertEquals("http-Alice", response.name());
            assertTrue(requestBody.get(), requestBody.get().contains("\"id\":\"C-REMOTE\""));
            assertTrue(requestBody.get(), requestBody.get().contains("\"name\":\"Alice\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void httpJsonRemoteRoleAdapterSendsRolePerformerEnvelope() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/spp/role-query", exchange -> {
            try {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"id\":\"ROLE-1\",\"name\":\"http-role\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://localhost:" + server.getAddress().getPort() + "/spp/role-query";
            HttpJsonRemoteServiceAdapter adapter = new HttpJsonRemoteServiceAdapter(
                    "remoteSnapshotQuery",
                    Map.of("endpoint", endpoint,
                            "roleAdapter", "true",
                            "roleType", "SnapshotObject",
                            "roleAdapterProtocol", "HTTP_JSON"),
                    new ObjectMapper(),
                    java.net.http.HttpClient.newHttpClient());
            SnapshotObject role = new SnapshotObject("ROLE-1", "role");
            SnapshotObject performer = new SnapshotObject("PERFORMER-1", "performer");
            RoleIdentityContext.attachPerformers(role, "SnapshotObject", performer);
            Method method = RemoteSnapshotQuery.class.getMethod("queryRemote", SnapshotObject.class);

            SnapshotObject response = (SnapshotObject) adapter.execute("remoteSnapshotQuery", method, role);

            assertEquals("ROLE-1", response.id());
            assertEquals("http-role", response.name());
            JsonNode body = new ObjectMapper().readTree(requestBody.get());
            assertEquals("SnapshotObject", body.get("roleType").asText());
            assertEquals("ROLE-1", body.get("role").get("id").asText());
            assertEquals("role", body.get("role").get("name").asText());
            assertEquals("PERFORMER-1", body.get("performers").get(0).get("id").asText());
            assertEquals("performer", body.get("performers").get(0).get("name").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterExecutesTypedRemoteService() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String referenceWarning = "Remote service remoteSnapshotQuery"
                + " is still backed by reference adapter objectRepositoryRemote"
                + "; keep it only as a mock/reference implementation until a real external adapter is bound.";
        runtime.loadAppSemanticMigrationWarnings(new ByteArrayInputStream(
                ("warning.count=1\n"
                        + "warning.0=" + referenceWarning + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotQuery.config.repository=objectStore\n"
                        + "remote.remoteSnapshotQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotQuery.config.objectIdPath=id\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.saveObjectSnapshot("SnapshotObject", "C-REMOTE",
                new SnapshotObject("C-REMOTE", "repository-Alice"));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals("objectRepositoryRemote",
                catalog.remoteServiceAssignments().get(0).adapterImplementationType());
        assertEquals("SnapshotObject",
                catalog.remoteServiceAssignments().get(0).adapterConfiguration().get("objectType"));
        assertEquals("objectStore",
                catalog.remoteServiceAssignments().get(0).adapterConfiguration().get("repository"));
        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        assertEquals(1, catalog.appSemanticMigrationWarnings().stream()
                .filter(referenceWarning::equals)
                .count());
        assertEquals(1, catalog.appSemanticMigrationPlans().stream()
                .filter(plan -> "REMOTE_REFERENCE_ADAPTER".equals(plan.migrationType())
                        && "remoteSnapshotQuery".equals(plan.serviceName()))
                .count());
        runtime.requireValidGeneratedServiceDeployment();

        RemoteSnapshotQuery query = (RemoteSnapshotQuery) DefaultServiceContainer.getInstance()
                .getService("remoteSnapshotQuery");
        SnapshotObject response = query.queryRemote(new SnapshotObject("C-REMOTE", "ignored"));

        assertEquals("C-REMOTE", response.id());
        assertEquals("repository-Alice", response.name());
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterReadsAppDdlTables() {
        String repositoryUrl = "jdbc:h2:mem:remote-app-ddl-object-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.datasourceUrl=" + repositoryUrl + "\n"
                        + "repository.objectStore.config.driverClassName=org.h2.Driver\n"
                        + "repository.objectStore.config.table=spp_business_object\n"
                        + "repository.objectStore.config.appDdlTables=true\n"
                        + "remoteServices=remoteMutableSnapshotQuery\n"
                        + "remote.remoteMutableSnapshotQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteMutableSnapshotQuery.config.repository=objectStore\n"
                        + "remote.remoteMutableSnapshotQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteMutableSnapshotQuery.config.objectIdPath=id\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.loadAppDdlCatalog(new ByteArrayInputStream(
                ("tables=SNAPSHOT_OBJECT\n"
                        + "table.SNAPSHOT_OBJECT.category=entities\n"
                        + "table.SNAPSHOT_OBJECT.businessType=SnapshotObject\n"
                        + "table.SNAPSHOT_OBJECT.columns=id,name\n"
                        + "table.SNAPSHOT_OBJECT.primaryKeys=id\n"
                        + "table.SNAPSHOT_OBJECT.column.id.type=VARCHAR\n"
                        + "table.SNAPSHOT_OBJECT.column.id.precision=64\n"
                        + "table.SNAPSHOT_OBJECT.column.id.scale=0\n"
                        + "table.SNAPSHOT_OBJECT.column.id.nullable=false\n"
                        + "table.SNAPSHOT_OBJECT.column.name.type=VARCHAR\n"
                        + "table.SNAPSHOT_OBJECT.column.name.precision=80\n"
                        + "table.SNAPSHOT_OBJECT.column.name.scale=0\n"
                        + "table.SNAPSHOT_OBJECT.column.name.nullable=true\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteMutableSnapshotQuery="
                        + RemoteMutableSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        MutableSnapshotObject stored = new MutableSnapshotObject();
        stored.id = "C-REMOTE-DDL";
        stored.name = "typed-Alice";
        runtime.getRepository().save("SnapshotObject", stored.id, stored);
        runtime.getSnapshotStore().clear();

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.repositoryAdapterValidationErrors().toString(),
                catalog.repositoryAdapterValidationErrors().isEmpty());
        assertTrue(catalog.remoteServiceValidationErrors().toString(),
                catalog.remoteServiceValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();

        RemoteMutableSnapshotQuery query = (RemoteMutableSnapshotQuery) DefaultServiceContainer.getInstance()
                .getService("remoteMutableSnapshotQuery");
        MutableSnapshotObject request = new MutableSnapshotObject();
        request.id = "C-REMOTE-DDL";
        request.name = "ignored";
        MutableSnapshotObject response = query.queryRemote(request);

        assertEquals("C-REMOTE-DDL", response.id);
        assertEquals("typed-Alice", response.name);
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterExecutesCollectionRemoteService() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "remoteServices=remoteSnapshotListQuery\n"
                        + "remote.remoteSnapshotListQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotListQuery.config.repository=objectStore\n"
                        + "remote.remoteSnapshotListQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotListQuery.config.objectIdPath=name\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotListQuery="
                        + RemoteSnapshotListQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.saveObjectSnapshot("SnapshotObject", "C-REMOTE-1",
                new SnapshotObject("C-REMOTE-1", "shared"));
        runtime.saveObjectSnapshot("SnapshotObject", "C-REMOTE-2",
                new SnapshotObject("C-REMOTE-2", "shared"));
        runtime.saveObjectSnapshot("SnapshotObject", "C-REMOTE-3",
                new SnapshotObject("C-REMOTE-3", "other"));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();

        RemoteSnapshotListQuery query = (RemoteSnapshotListQuery) DefaultServiceContainer.getInstance()
                .getService("remoteSnapshotListQuery");
        List<SnapshotObject> response = query.queryRemoteList(new SnapshotObject("ignored", "shared"));

        assertEquals(2, response.size());
        assertTrue(response.stream().anyMatch(item -> "C-REMOTE-1".equals(item.id())));
        assertTrue(response.stream().anyMatch(item -> "C-REMOTE-2".equals(item.id())));
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterExecutesCompositeCollectionLookup() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("repositories=objectStore\n"
                        + "repository.objectStore.adapter=objectRepository\n"
                        + "remoteServices=remoteSnapshotListQuery\n"
                        + "remote.remoteSnapshotListQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotListQuery.config.repository=objectStore\n"
                        + "remote.remoteSnapshotListQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotListQuery.config.objectIdPaths=id,name\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotListQuery="
                        + RemoteSnapshotListQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.saveObjectSnapshot("SnapshotObject", "C-REMOTE-1",
                new SnapshotObject("C-REMOTE-1", "shared"));
        runtime.saveObjectSnapshot("SnapshotObject", "C-REMOTE-2",
                new SnapshotObject("C-REMOTE-2", "shared"));
        runtime.saveObjectSnapshot("SnapshotObject", "C-REMOTE-1-OTHER",
                new SnapshotObject("C-REMOTE-1", "other"));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertTrue(catalog.remoteServiceValidationErrors().toString(),
                catalog.remoteServiceValidationErrors().isEmpty());
        runtime.requireValidGeneratedServiceDeployment();

        RemoteSnapshotListQuery query = (RemoteSnapshotListQuery) DefaultServiceContainer.getInstance()
                .getService("remoteSnapshotListQuery");
        List<SnapshotObject> response = query.queryRemoteList(new SnapshotObject("C-REMOTE-1", "shared"));

        assertEquals(1, response.size());
        assertEquals("C-REMOTE-1", response.get(0).id());
        assertEquals("shared", response.get(0).name());
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterRejectsCompositeSingleObjectLookup() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotQuery.config.objectIdPaths=id,name\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery="
                        + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0),
                catalog.remoteServiceValidationErrors().get(0).contains(
                        "objectRepositoryRemote adapter config objectIdPaths"
                                + " requires the remote method to return List or array"));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterValidatesRepositoryLookupPathAgainstCollectionElement() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotListQuery\n"
                        + "remote.remoteSnapshotListQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotListQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotListQuery.config.objectIdPaths=id,name\n"
                        + "remote.remoteSnapshotListQuery.config.repositoryObjectIdPaths=id,missingName\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotListQuery="
                        + RemoteSnapshotListQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0),
                catalog.remoteServiceValidationErrors().get(0).contains(
                        "objectRepositoryRemote adapter repository object id path"
                                + " SnapshotObject.missingName is not readable"));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterValidatesLookupPathAgainstRequest() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotQuery.config.objectIdPath=missingId\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery="
                        + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0),
                catalog.remoteServiceValidationErrors().get(0).contains(
                        "objectRepositoryRemote adapter object id path SnapshotObject.missingId is not readable"));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterRequiresSingleRequestParameter() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotPairQuery\n"
                        + "remote.remoteSnapshotPairQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotPairQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotPairQuery.config.objectIdPath=id\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotPairQuery="
                        + RemoteSnapshotPairQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0),
                catalog.remoteServiceValidationErrors().get(0).contains(
                        "objectRepositoryRemote adapter requires exactly one request parameter"));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterValidatesLookupConfig() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotQuery.config.objectType=\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterAvailable());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertEquals(2, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotQuery objectRepositoryRemote adapter must configure objectType")));
        assertTrue(catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                "Remote service remoteSnapshotQuery objectRepositoryRemote adapter must configure objectIdPath")));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterRejectsBlankLookupPathSegments() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotQuery.config.objectIdPath=id.\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceAssignments().size());
        assertTrue(catalog.remoteServiceAssignments().get(0).adapterExecutable());
        assertTrue(catalog.remoteServiceValidationErrors().toString(),
                catalog.remoteServiceValidationErrors().stream().anyMatch(error -> error.contains(
                        "objectRepositoryRemote adapter config objectIdPath path id."
                                + " must not contain blank segments")));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void objectRepositoryRemoteAdapterRequiresRepositoryAtExecution() {
        ObjectRepositoryRemoteServiceAdapter adapter = new ObjectRepositoryRemoteServiceAdapter(
                "remoteSnapshotQuery",
                Map.of("objectType", "SnapshotObject", "objectIdPath", "id"),
                () -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.execute("remoteSnapshotQuery", new SnapshotObject("SO-1", "draft")));

        assertTrue(error.getMessage().contains(
                "Remote service remoteSnapshotQuery objectRepositoryRemote adapter requires an ObjectRepository"));
    }

    @Test
    public void objectRepositoryRemoteAdapterReadsRolePerformerIdentityPath() throws Exception {
        InMemoryObjectRepository repository = new InMemoryObjectRepository();
        RemoteCustomer customer = new RemoteCustomer("C-1001", "Ada");
        repository.save("RemoteCustomer", "C-1001", customer);
        RemoteCreditAccount account = new RemoteCreditAccount();
        RoleIdentityContext.attachPerformers(account, "RemoteCreditAccount", customer);
        ObjectRepositoryRemoteServiceAdapter adapter = new ObjectRepositoryRemoteServiceAdapter(
                "queryCustomer",
                Map.of("objectType", "RemoteCustomer", "objectIdPath", "remoteCustomer.customerId"),
                () -> repository);
        Method method = RemoteCustomerQuery.class.getMethod("queryCustomer", RemoteCreditAccount.class);

        Object result = adapter.execute("queryCustomer", method, account);

        assertSame(customer, result);
    }

    @Test
    public void objectRepositoryRemoteAdapterReportsMissingRolePerformerIdentityPath() throws Exception {
        ObjectRepositoryRemoteServiceAdapter adapter = new ObjectRepositoryRemoteServiceAdapter(
                "queryCustomer",
                Map.of("objectType", "RemoteCustomer", "objectIdPath", "remoteCustomer.customerId"),
                InMemoryObjectRepository::new);
        Method method = RemoteCustomerQuery.class.getMethod("queryCustomer", RemoteCreditAccount.class);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.execute("queryCustomer", method, new RemoteCreditAccount()));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Remote service queryCustomer objectRepositoryRemote adapter object id path"
                        + " remoteCustomer.customerId is not readable from"));
        assertTrue(error.getMessage(), error.getMessage().contains(
                "or attached Role performer identity remoteCustomer"));
    }

    @Test
    public void physicalCatalogObjectRepositoryRemoteAdapterRepositoryMustReferenceDeclaredRepository() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.loadPhysicalRuntimeCatalog(new ByteArrayInputStream(
                ("remoteServices=remoteSnapshotQuery\n"
                        + "remote.remoteSnapshotQuery.adapter=objectRepositoryRemote\n"
                        + "remote.remoteSnapshotQuery.config.repository=missingStore\n"
                        + "remote.remoteSnapshotQuery.config.objectType=SnapshotObject\n"
                        + "remote.remoteSnapshotQuery.config.objectIdPath=id\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();

        assertEquals(1, catalog.remoteServiceValidationErrors().size());
        assertTrue(catalog.remoteServiceValidationErrors().get(0).contains(
                "Remote service remoteSnapshotQuery objectRepositoryRemote adapter config repository"
                        + " references undefined repository missingStore"));
        assertEquals(catalog.remoteServiceValidationErrors(), catalog.deploymentValidationErrors());
    }

    @Test
    public void remoteServiceProxyKeepsScopeMetadataLocal() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        int[] adapterCalls = new int[1];
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("asyncRemoteSnapshotQuery="
                        + AsyncRemoteSnapshotQueryService.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("asyncRemoteSnapshotQuery");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                adapterCalls[0]++;
                SnapshotObject request = (SnapshotObject) arguments[0];
                return new SnapshotObject(request.id(), "async-" + request.name());
            }
        });

        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices();
        assertEquals(1, catalog.services().size());
        assertEquals("asyncRemoteSnapshotQuery", catalog.services().get(0).serviceName());
        assertTrue(catalog.services().get(0).remote());
        assertEquals(GeneratedServiceKind.ATOMIC_QUERY, catalog.services().get(0).serviceKind());
        assertTrue(!catalog.services().get(0).scopeLocal());
        assertTrue(catalog.services().get(0).async());
        assertEquals("remoteIn", catalog.services().get(0).inputHeaderName());
        assertEquals("remoteOut", catalog.services().get(0).outputHeaderName());

        Service service = DefaultServiceContainer.getInstance().getService("asyncRemoteSnapshotQuery");
        ScopeMetadataProvider metadata = (ScopeMetadataProvider) service;
        assertTrue(!metadata.isLocalScope());
        assertTrue(metadata.isAsyncScope());
        assertEquals("remoteIn", metadata.getInputHeaderName());
        assertEquals("remoteOut", metadata.getOutputHeaderName());
        assertTrue(!metadata.requiresPhysicalBackendBinding());
        assertEquals(GeneratedServiceKind.ATOMIC_QUERY,
                ((GeneratedServiceKindProvider) service).getGeneratedServiceKind());
        assertEquals(0, adapterCalls[0]);

        SnapshotObject response = ((RemoteSnapshotQuery) service)
                .queryRemote(new SnapshotObject("C-ASYNC", "Alice"));
        assertEquals("C-ASYNC", response.id());
        assertEquals("async-Alice", response.name());
        assertEquals(1, adapterCalls[0]);
    }

    @Test
    public void remoteGeneratedServiceExecutionRequiresRegisteredAdapter() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(("remoteSnapshotScenario=" + RemoteSnapshotScenario.class.getName() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.executeGeneratedService("remoteSnapshotScenario",
                        new SnapshotObject("C-REMOTE", "Alice")));

        assertTrue(error.getMessage().contains(
                "Remote service remoteSnapshotScenario has no registered adapter"));
    }

    @Test
    public void registeredRemoteGeneratedServiceProxyDelegatesContainerCallsToAdapter() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        String properties = "remoteSnapshotQuery=" + RemoteSnapshotQueryService.class.getName() + "\n"
                + "scenarioDependingOnRemoteQuery=" + ScenarioDependingOnRemoteQuery.class.getName() + "\n";

        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        runtime.registerRemoteServiceAdapter(new RemoteServiceAdapter() {
            @Override
            public Set<String> serviceNames() {
                return Set.of("remoteSnapshotQuery");
            }

            @Override
            public Object execute(String serviceName, Object... arguments) {
                SnapshotObject request = (SnapshotObject) arguments[0];
                return new SnapshotObject(request.id(), "adapter-" + request.name());
            }
        });

        SnapshotObject response = (SnapshotObject) runtime.executeGeneratedService(
                "scenarioDependingOnRemoteQuery", new SnapshotObject("C-REMOTE", "Alice"));

        assertEquals("C-REMOTE", response.id());
        assertEquals("adapter-Alice", response.name());
    }

    @Test
    public void currentObjectContextRestoresOuterObjectsAfterNestedExecution() {
        SnapshotObject outer = new SnapshotObject("SO-OUTER", "outer-order");
        SnapshotObject inner = new SnapshotObject("SO-INNER", "inner-order");

        CurrentObjectContext.withObjects(new Object[] { outer }, () -> {
            assertSame(outer, CurrentObjectContext.find("SnapshotObject", "id").orElseThrow());
            CurrentObjectContext.withObjects(new Object[] { inner }, () -> {
                assertSame(inner, CurrentObjectContext.find("SnapshotObject", "id").orElseThrow());
                return null;
            });
            assertSame(outer, CurrentObjectContext.find("SnapshotObject", "id").orElseThrow());
            return null;
        });

        assertTrue(CurrentObjectContext.find("SnapshotObject", "id").isEmpty());
    }

    @Test
    public void localGeneratedRuntimeExecutesRegisteredGeneratedService() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        runtime.getObjectRepository().save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        String properties = "querySnapshot=" + SnapshotQueryService.class.getName() + "\n"
                + "snapshotScenario=" + ScenarioDependingOnQuery.class.getName() + "\n";
        runtime.registerGeneratedServices(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        TestQueryResponse response = (TestQueryResponse) runtime.executeGeneratedService("snapshotScenario", query);

        assertSame(runtime.getIdempotencyStore(), BusinessIdempotencyManager.getStore());
        assertEquals("Alice", query.name);
        assertEquals("Alice", response.snapshotObject.name);
    }

    @Test
    public void localGeneratedRuntimeResolvesCurrentSnapshotFromScenarioRequest() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        DefaultServiceContainer services = DefaultServiceContainer.getInstance();
        services.registerService("saveCurrentOrder",
                runtime.getGeneratedServiceRuntime().decorate(new CurrentSnapshotService()));
        services.registerService("currentSnapshotScenario", new CurrentSnapshotScenario());

        runtime.executeGeneratedService("currentSnapshotScenario",
                new SnapshotScenarioRequest(new SnapshotObject(null, "wrong-order"),
                        new SnapshotObject("SO-CURRENT", "order"),
                        new SnapshotObject(null, "account")));

        ObjectSnapshot snapshot = runtime.getSnapshotStore()
                .find("SnapshotObject", "SO-CURRENT")
                .orElseThrow();
        assertEquals("{\"id\":\"SO-CURRENT\",\"name\":\"order\"}", snapshot.payloadJson());
        assertEquals(1, runtime.getSnapshotStore().findAll().size());
        GeneratedServiceCatalog catalog = runtime.catalogGeneratedServices(List.of(services.getService(
                "saveCurrentOrder")));
        ObjectSnapshotPlan plan = catalog.objectSnapshotPlans().stream()
                .filter(item -> item.serviceName().equals("saveCurrentOrder"))
                .findFirst().orElseThrow();
        assertEquals("CURRENT_OBJECT", plan.source());
        assertEquals("CURRENT_OBJECT_KEY", plan.objectKeySource());
        assertFalse(plan.escapeHatch());
        assertFalse(plan.objectKeyEscapeHatch());
    }

    @Test
    public void localGeneratedRuntimeResolvesCurrentSnapshotFromNestedObjectIdPath() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        DefaultServiceContainer services = DefaultServiceContainer.getInstance();
        services.registerService("saveNestedCurrentOrder",
                runtime.getGeneratedServiceRuntime().decorate(new NestedCurrentSnapshotService()));
        services.registerService("nestedCurrentSnapshotScenario", new NestedCurrentSnapshotScenario());

        runtime.executeGeneratedService("nestedCurrentSnapshotScenario",
                new NestedSnapshotScenarioRequest(
                        new NestedSnapshotObject("SO-CURRENT", new SnapshotObject("C-CURRENT", "customer")),
                        new SnapshotObject(null, "account")));

        ObjectSnapshot snapshot = runtime.getSnapshotStore()
                .find("NestedSnapshotObject", "C-CURRENT")
                .orElseThrow();
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("\"id\":\"SO-CURRENT\""));
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("\"id\":\"C-CURRENT\""));
        assertEquals(1, runtime.getSnapshotStore().findAll().size());
    }

    @Test
    public void localGeneratedRuntimeRejectsAmbiguousCurrentSnapshotContext() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        DefaultServiceContainer services = DefaultServiceContainer.getInstance();
        services.registerService("saveCurrentOrder",
                runtime.getGeneratedServiceRuntime().decorate(new CurrentSnapshotService()));
        services.registerService("currentSnapshotScenario", new CurrentSnapshotScenario());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.executeGeneratedService("currentSnapshotScenario",
                        new SnapshotScenarioRequest(new SnapshotObject("SO-DRAFT", "draft-order"),
                                new SnapshotObject("SO-CURRENT", "order"),
                                new SnapshotObject(null, "account"))));

        assertTrue(error.getMessage().contains(
                "Current object context for SnapshotObject.id is ambiguous"));
        assertTrue(runtime.getSnapshotStore().findAll().isEmpty());
    }

    @Test
    public void localGeneratedRuntimeClearsCurrentSnapshotContextAfterFailure() {
        LocalGeneratedServiceRuntime runtime =
                LocalGeneratedServiceRuntime.jsonSnapshots(h2DataSource(), new ObjectMapper(), type -> null);
        DefaultServiceContainer services = DefaultServiceContainer.getInstance();
        services.registerService("saveCurrentOrder",
                runtime.getGeneratedServiceRuntime().decorate(new CurrentSnapshotService()));
        services.registerService("failingCurrentSnapshotScenario", new FailingCurrentSnapshotScenario());

        SnapshotScenarioRequest request = new SnapshotScenarioRequest(new SnapshotObject(null, "wrong-order"),
                new SnapshotObject("SO-LEAK", "order"),
                new SnapshotObject(null, "account"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runtime.executeGeneratedService("failingCurrentSnapshotScenario", request));
        assertEquals("scenario failed", error.getMessage());

        Service saveCurrentOrder = services.getService("saveCurrentOrder");
        IllegalStateException missingCurrent = assertThrows(IllegalStateException.class,
                () -> DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
                    @Override
                    public Service getService() {
                        return saveCurrentOrder;
                    }

                    @Override
                    public Object[] getArguments() {
                        return new Object[] { request.account() };
                    }

                    @Override
                    public void execute() {
                        saveCurrentOrder.execute(request.account());
                    }
                })));
        assertTrue(missingCurrent.getMessage().contains(
                "Object snapshot SnapshotObject requires a non-null object"));
        assertTrue(runtime.getSnapshotStore().find("SnapshotObject", "SO-LEAK").isEmpty());
    }

    @Test
    public void generatedRuntimeRegistersServicesFromProperties() {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonObjectSnapshotRepository repository = new JsonObjectSnapshotRepository(store, objectMapper);
        repository.save("SnapshotObject", "C-1", new SnapshotObject("C-1", "Alice"));
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store, repository, type -> null);
        String properties = "querySnapshot=" + SnapshotQueryService.class.getName() + "\n";

        List<Service> registered = runtime.registerFromProperties(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, registered.size());
        assertEquals("querySnapshot", registered.get(0).getName());
        Service service = DefaultServiceContainer.getInstance().getService("querySnapshot");
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        TestQueryResponse response = ((TestQuery) service).querySnapshot(query);

        assertEquals("Alice", response.snapshotObject.name);
    }

    @Test
    public void generatedRuntimeInstantiatesScenarioServicesAfterTheirDependencies() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(store,
                new JsonObjectSnapshotRepository(store, new ObjectMapper()), type -> null);
        String properties = "snapshotScenario=" + ScenarioDependingOnQuery.class.getName() + "\n"
                + "querySnapshot=" + TestQueryService.class.getName() + "\n";

        runtime.registerFromProperties(DefaultServiceContainer.getInstance(),
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));

        Service service = DefaultServiceContainer.getInstance().getService("snapshotScenario");
        MutableSnapshotObject query = new MutableSnapshotObject();
        query.id = "C-1";
        query.name = "Alice";

        TestQueryResponse response = (TestQueryResponse) service.execute(query);
        assertEquals("Alice", response.snapshotObject.name);
    }



    @Test
    public void joinsCurrentJdbcTransactionScopeAndRollsBackWithIt() {
        JdbcDataSource dataSource = h2DataSource();
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(dataSource, Object::toString);

        assertThrows(IllegalStateException.class, () -> JdbcTransactionScope.execute(dataSource, connection -> {
            store.save("Inventory", "SKU-1", "changed");
            throw new IllegalStateException("rollback");
        }));

        assertEquals(0, store.findAll().size());
    }

    @Test
    public void participantPersistsArgumentAndResolvedObjectAfterExecute() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        SnapshotObject account = new SnapshotObject("C-1", "account");
        SnapshotObject order = new SnapshotObject("SO-1", "order");
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .argument(0, "CreditAccount", SnapshotObject::id)
                .object("SalesOrder", arguments -> order, SnapshotObject::id);

        participant.setArguments(new Object[] { account });
        participant.afterExecute(null);

        assertEquals(2, store.findAll().size());
        assertEquals("CreditAccount", store.findAll().get(0).objectType());
        assertEquals("C-1", store.findAll().get(0).objectId());
        assertEquals("SalesOrder", store.findAll().get(1).objectType());
        assertEquals("SO-1", store.findAll().get(1).objectId());
    }

    @Test
    public void participantPersistsDeclaredSnapshotRules() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        SnapshotObject account = new SnapshotObject("C-1", "account");
        SnapshotObject order = new SnapshotObject("SO-1", "order");
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(
                        ObjectSnapshotRule.argument(0, "CreditAccount", "id"),
                        ObjectSnapshotRule.current("SalesOrder", "id")),
                        Map.of("SalesOrder", order)::get);

        participant.setArguments(new Object[] { account });
        participant.afterExecute(null);

        assertEquals(2, store.findAll().size());
        assertEquals("CreditAccount", store.findAll().get(0).objectType());
        assertEquals("C-1", store.findAll().get(0).objectId());
        assertEquals("SalesOrder", store.findAll().get(1).objectType());
        assertEquals("SO-1", store.findAll().get(1).objectId());
    }

    @Test
    public void participantPersistsArgumentSnapshotRulesWithNestedObjectIdPath() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> writeJson(value));
        NestedSnapshotObject account =
                new NestedSnapshotObject("CA-1", new SnapshotObject("C-NESTED", "customer"));
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.argument(0, "CreditAccount", "customer.id")),
                        type -> null);

        participant.setArguments(new Object[] { account });
        participant.afterExecute(null);

        ObjectSnapshot snapshot = store.find("CreditAccount", "C-NESTED").orElseThrow();
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("\"id\":\"CA-1\""));
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("\"id\":\"C-NESTED\""));
        assertEquals(1, store.findAll().size());
    }

    @Test
    public void participantPersistsArgumentSnapshotRulesWithCompositeObjectIdPaths() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> writeJson(value));
        AppDdlStore storeRef = new AppDdlStore();
        storeRef.name = "MAIN";
        AppDdlGoods goods = new AppDdlGoods();
        goods.skuId = "SKU-100";
        AppDdlInventory inventory = new AppDdlInventory();
        inventory.store = storeRef;
        inventory.goods = goods;
        inventory.safeQty = 10;
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.argument(0, "AppDdlInventory",
                        List.of("store.name", "goods.skuId"))), type -> null);

        participant.setArguments(new Object[] { inventory });
        participant.afterExecute(null);

        String objectId = CompositeObjectId.encode(List.of("MAIN", "SKU-100"));
        ObjectSnapshot snapshot = store.find("AppDdlInventory", objectId).orElseThrow();
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("\"safeQty\":10"));
        assertEquals(1, store.findAll().size());
    }

    @Test
    public void participantResolvesSnapshotObjectIdFromRuntimeRolePerformers() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), value -> writeJson(value));
        AppDdlStore storeRef = new AppDdlStore();
        storeRef.name = "MAIN";
        AppDdlGoods goods = new AppDdlGoods();
        goods.skuId = "SKU-100";
        AppDdlInventory inventory = new AppDdlInventory();
        inventory.safeQty = 10;
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .resolveRoleIdentities((roleType, arguments) -> "AppDdlInventory".equals(roleType)
                        ? Optional.of(new RepositoryHydratingServiceProxy.RoleIdentity(
                                roleType,
                                List.of("store.name", "goods.skuId"),
                                List.of(((AppDdlStore) arguments[0]).name, ((AppDdlGoods) arguments[1]).skuId),
                                List.of("store_name", "goods_skuid"),
                                "PERFORMER",
                                true))
                        : Optional.empty())
                .rules(List.of(ObjectSnapshotRule.argument(2, "AppDdlInventory",
                        List.of("store.name", "goods.skuId"))), type -> null);

        participant.setArguments(new Object[] { storeRef, goods, inventory });
        participant.afterExecute(null);

        String objectId = CompositeObjectId.encode(List.of("MAIN", "SKU-100"));
        ObjectSnapshot snapshot = store.find("AppDdlInventory", objectId).orElseThrow();
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("\"safeQty\":10"));
        assertEquals(1, store.findAll().size());
    }

    @Test
    public void participantPersistsSnapshotRulesAtDeclaredTiming() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        SnapshotObject account = new SnapshotObject("C-1", "account");
        SnapshotObject order = new SnapshotObject("SO-1", "order");
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(
                        ObjectSnapshotRule.argument(0, "CreditAccount", "id",
                                ObjectSnapshotRule.Timing.BEFORE),
                        ObjectSnapshotRule.current("SalesOrder", "id",
                                ObjectSnapshotRule.Timing.COMMIT)),
                        Map.of("SalesOrder", order)::get);

        participant.setArguments(new Object[] { account });
        participant.beforeExecute(null);

        assertEquals(1, store.findAll().size());
        assertTrue(store.find("CreditAccount", "C-1").isPresent());
        assertTrue(store.find("SalesOrder", "SO-1").isEmpty());

        participant.afterExecute(null);

        assertEquals(1, store.findAll().size());
        assertTrue(store.find("SalesOrder", "SO-1").isEmpty());

        participant.commit(null);

        assertEquals(2, store.findAll().size());
        assertTrue(store.find("SalesOrder", "SO-1").isPresent());
    }

    @Test
    public void participantPersistsTransactionSnapshotRuleWithBusinessTraceId() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.transaction()), type -> null);

        BusinessTraceContext.execute("REQ-TX-100", () -> {
            TransactionContext context = new TransactionContext(Map.of("commerce", TransactionMode.AT));
            participant.afterExecute(context);
            return null;
        });

        ObjectSnapshot snapshot = store.find("TransactionSnapshot", "REQ-TX-100").orElseThrow();
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("REQ-TX-100"));
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("globalSerialNumber=REQ-TX-100"));
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("transactionId="));
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("resourceId=commerce"));
        assertTrue(snapshot.payloadJson(), snapshot.payloadJson().contains("phase=AFTER"));
        assertEquals(1, store.findAll().size());
        assertEquals("REQ-TX-100", store.findTransactionHistory(null, "commerce", "AFTER").get(0).objectId());
    }

    @Test
    public void participantRejectsTransactionSnapshotRuleWithoutCurrentBusinessTraceId() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.transaction()), type -> null);

        IllegalStateException withoutContext = assertThrows(IllegalStateException.class,
                () -> participant.afterExecute(null));
        assertTrue(withoutContext.getMessage().contains(
                "Object snapshot TransactionSnapshot requires a current business trace id"));

        TransactionContext context = new TransactionContext(Map.of("commerce", TransactionMode.AT));
        IllegalStateException withoutContextTrace = assertThrows(IllegalStateException.class,
                () -> participant.afterExecute(context));
        assertTrue(withoutContextTrace.getMessage().contains(
                "Object snapshot TransactionSnapshot requires a current business trace id"));
        assertTrue(store.findAll().isEmpty());
    }

    @Test
    public void participantRejectsTransactionSnapshotRuleWithoutBusinessTraceId() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        ObjectSnapshotParticipant participant = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.transaction()), type -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BusinessTraceContext.execute("", () -> {
                    participant.afterExecute(null);
                    return null;
                }));
        assertTrue(error.getMessage().contains(
                "Object snapshot TransactionSnapshot requires a current business trace id"));
        assertTrue(store.findAll().isEmpty());
    }

    @Test
    public void participantRejectsSnapshotRulesWithoutObjectId() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        ObjectSnapshotParticipant missingId = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.argument(0, "CreditAccount", "id")),
                        type -> null);
        MutableSnapshotObject account = new MutableSnapshotObject();

        missingId.setArguments(new Object[] { account });
        IllegalStateException missingIdError = assertThrows(IllegalStateException.class,
                () -> missingId.afterExecute(null));
        assertTrue(missingIdError.getMessage().contains(
                "Object snapshot CreditAccount requires a non-empty object id"));

        ObjectSnapshotParticipant blankId = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.argument(0, "CreditAccount", "id")),
                        type -> null);
        account.id = " ";
        blankId.setArguments(new Object[] { account });
        IllegalStateException blankIdError = assertThrows(IllegalStateException.class,
                () -> blankId.afterExecute(null));
        assertTrue(blankIdError.getMessage().contains(
                "Object snapshot CreditAccount requires a non-empty object id"));
        assertEquals(0, store.findAll().size());
    }

    @Test
    public void participantRejectsSnapshotRulesWithoutObject() {
        JdbcObjectSnapshotStore store = new JdbcObjectSnapshotStore(h2DataSource(), Object::toString);
        ObjectSnapshotParticipant missingArgument = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.argument(0, "CreditAccount", "id")),
                        type -> null);

        missingArgument.setArguments(new Object[] { null });
        IllegalStateException missingArgumentError = assertThrows(IllegalStateException.class,
                () -> missingArgument.afterExecute(null));
        assertTrue(missingArgumentError.getMessage().contains(
                "Object snapshot CreditAccount requires a non-null object"));

        ObjectSnapshotParticipant missingCurrent = new ObjectSnapshotParticipant("commerce", store)
                .rules(List.of(ObjectSnapshotRule.current("SalesOrder", "id")),
                        type -> null);
        IllegalStateException missingCurrentError = assertThrows(IllegalStateException.class,
                () -> missingCurrent.afterExecute(null));
        assertTrue(missingCurrentError.getMessage().contains(
                "Object snapshot SalesOrder requires a non-null object"));
        assertEquals(0, store.findAll().size());
    }

    private Service originalService(Service service) {
        Service current = service;
        while (current instanceof DelegatingService delegatingService) {
            Service delegate = delegatingService.getDelegateService();
            if (delegate == null || delegate == current) {
                return current;
            }
            current = delegate;
        }
        return current;
    }

    private static String writeJson(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JdbcDataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:object-snapshot-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static boolean tableExists(String jdbcUrl, String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             ResultSet tables = connection.getMetaData()
                     .getTables(null, "PUBLIC", tableName.toUpperCase(), null)) {
            return tables.next();
        }
    }

    private static boolean columnExists(String jdbcUrl, String tableName, String columnName) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             ResultSet columns = connection.getMetaData()
                     .getColumns(null, "PUBLIC", tableName.toUpperCase(), columnName.toUpperCase())) {
            return columns.next();
        }
    }

    private static boolean indexExists(String jdbcUrl, String tableName, String indexName) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             ResultSet indexes = connection.getMetaData()
                     .getIndexInfo(null, "PUBLIC", tableName.toUpperCase(), false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String queryString(String jdbcUrl, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static int queryInt(String jdbcUrl, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static void executeUpdate(String jdbcUrl, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String rolePerformerAppDdlCatalogProperties() {
        return "tables=GOODS,INVENTORY\n"
                + "table.GOODS.category=roles\n"
                + "table.GOODS.businessType=Goods\n"
                + "table.GOODS.columns=skuId,product_productid,title,salePrice\n"
                + "table.GOODS.primaryKeys=skuId\n"
                + "table.GOODS.indexNames=goodsByProduct\n"
                + "table.GOODS.index.goodsByProduct.paths=product_productid\n"
                + "table.GOODS.column.skuId.type=VARCHAR\n"
                + "table.GOODS.column.skuId.precision=64\n"
                + "table.GOODS.column.skuId.scale=0\n"
                + "table.GOODS.column.skuId.nullable=false\n"
                + "table.GOODS.column.product_productid.type=VARCHAR\n"
                + "table.GOODS.column.product_productid.precision=64\n"
                + "table.GOODS.column.product_productid.scale=0\n"
                + "table.GOODS.column.product_productid.nullable=true\n"
                + "table.GOODS.column.product_productid.objectBacked=false\n"
                + "table.GOODS.column.title.type=VARCHAR\n"
                + "table.GOODS.column.title.precision=255\n"
                + "table.GOODS.column.title.scale=0\n"
                + "table.GOODS.column.title.nullable=true\n"
                + "table.GOODS.column.salePrice.type=DOUBLE\n"
                + "table.GOODS.column.salePrice.precision=0\n"
                + "table.GOODS.column.salePrice.scale=0\n"
                + "table.GOODS.column.salePrice.nullable=true\n"
                + "table.INVENTORY.category=roles\n"
                + "table.INVENTORY.businessType=Inventory\n"
                + "table.INVENTORY.columns=store_name,goods_skuid,availableQty,safeQty,lockedQty\n"
                + "table.INVENTORY.primaryKeys=store_name,goods_skuid\n"
                + "table.INVENTORY.indexNames=inventoryByGoods,inventoryByStoreGoods\n"
                + "table.INVENTORY.index.inventoryByGoods.paths=goods_skuid\n"
                + "table.INVENTORY.index.inventoryByStoreGoods.paths=store_name,goods_skuid\n"
                + "table.INVENTORY.column.store_name.type=VARCHAR\n"
                + "table.INVENTORY.column.store_name.precision=64\n"
                + "table.INVENTORY.column.store_name.scale=0\n"
                + "table.INVENTORY.column.store_name.nullable=false\n"
                + "table.INVENTORY.column.store_name.objectBacked=false\n"
                + "table.INVENTORY.column.goods_skuid.type=VARCHAR\n"
                + "table.INVENTORY.column.goods_skuid.precision=64\n"
                + "table.INVENTORY.column.goods_skuid.scale=0\n"
                + "table.INVENTORY.column.goods_skuid.nullable=false\n"
                + "table.INVENTORY.column.goods_skuid.objectBacked=false\n"
                + "table.INVENTORY.column.availableQty.type=INTEGER\n"
                + "table.INVENTORY.column.availableQty.precision=0\n"
                + "table.INVENTORY.column.availableQty.scale=0\n"
                + "table.INVENTORY.column.availableQty.nullable=true\n"
                + "table.INVENTORY.column.safeQty.type=INTEGER\n"
                + "table.INVENTORY.column.safeQty.precision=0\n"
                + "table.INVENTORY.column.safeQty.scale=0\n"
                + "table.INVENTORY.column.safeQty.nullable=true\n"
                + "table.INVENTORY.column.lockedQty.type=INTEGER\n"
                + "table.INVENTORY.column.lockedQty.precision=0\n"
                + "table.INVENTORY.column.lockedQty.scale=0\n"
                + "table.INVENTORY.column.lockedQty.nullable=true\n"
                + "table.INVENTORY.column.lockedQty.objectBacked=false\n";
    }

    private static String inventoryAppDdlCatalogProperties() {
        return "tables=INVENTORY\n"
                + "table.INVENTORY.category=roles\n"
                + "table.INVENTORY.businessType=Inventory\n"
                + "table.INVENTORY.columns=store_name,goods_skuid,availableQty,safeQty,lockedQty\n"
                + "table.INVENTORY.primaryKeys=store_name,goods_skuid\n"
                + "table.INVENTORY.indexNames=inventoryByGoods,inventoryByStoreGoods\n"
                + "table.INVENTORY.index.inventoryByGoods.paths=Inventory.goods.skuId\n"
                + "table.INVENTORY.index.inventoryByStoreGoods.paths=Inventory.store.name,Inventory.goods.skuId\n"
                + "table.INVENTORY.column.store_name.type=VARCHAR\n"
                + "table.INVENTORY.column.store_name.precision=64\n"
                + "table.INVENTORY.column.store_name.scale=0\n"
                + "table.INVENTORY.column.store_name.nullable=false\n"
                + "table.INVENTORY.column.goods_skuid.type=VARCHAR\n"
                + "table.INVENTORY.column.goods_skuid.precision=64\n"
                + "table.INVENTORY.column.goods_skuid.scale=0\n"
                + "table.INVENTORY.column.goods_skuid.nullable=false\n"
                + "table.INVENTORY.column.availableQty.type=INTEGER\n"
                + "table.INVENTORY.column.availableQty.precision=0\n"
                + "table.INVENTORY.column.availableQty.scale=0\n"
                + "table.INVENTORY.column.availableQty.nullable=true\n"
                + "table.INVENTORY.column.safeQty.type=INTEGER\n"
                + "table.INVENTORY.column.safeQty.precision=0\n"
                + "table.INVENTORY.column.safeQty.scale=0\n"
                + "table.INVENTORY.column.safeQty.nullable=true\n"
                + "table.INVENTORY.column.lockedQty.type=INTEGER\n"
                + "table.INVENTORY.column.lockedQty.precision=0\n"
                + "table.INVENTORY.column.lockedQty.scale=0\n"
                + "table.INVENTORY.column.lockedQty.nullable=true\n"
                + "table.INVENTORY.column.lockedQty.objectBacked=false\n";
    }

    private static IExecutor executor(AtomicService service, Object... arguments) {
        return new IExecutor() {
            @Override
            public Service getService() {
                return service;
            }

            @Override
            public Object[] getArguments() {
                return arguments;
            }

            @Override
            public void execute() {
                service.execute(arguments);
            }
        };
    }

    public record SnapshotObject(String id, String name) {
    }

    private record SnapshotScenarioRequest(SnapshotObject draftOrder, SnapshotObject order, SnapshotObject account) {
    }

    private record NestedSnapshotObject(String id, SnapshotObject customer) {
    }

    private record NestedSnapshotScenarioRequest(NestedSnapshotObject order, SnapshotObject account) {
    }

    public static class MutableSnapshotObject {
        public String id;
        public String name;
    }

    public static class NestedMutableSnapshotObject extends MutableSnapshotObject {
        public SnapshotObject customer;
    }

    public static class AppDdlProduct {
        public String productId;
        public String name;
    }

    public static class AppDdlGoods {
        public String skuId;
        public AppDdlProduct product;
        public Double salePrice;
    }

    public static class AppDdlRoleOnlyGoods {
        public String skuId;
        public String title;
        public Double salePrice;
    }

    public static class AppDdlStore {
        public String name;
    }

    public static class AppDdlInventory {
        public AppDdlStore store;
        public AppDdlGoods goods;
        public Integer availableQty;
        public Integer safeQty;
    }

    public static class AppDdlRoleOnlyInventory {
        public Integer availableQty;
        public Integer safeQty;
    }

    public static class AppDdlOrderHeader {
        public String orderNo;
        public String status;
    }

    public static class RemoteCreditAccount {
    }

    public static class RemoteCustomer {
        public String customerId;
        public String name;

        public RemoteCustomer(String customerId, String name) {
            this.customerId = customerId;
            this.name = name;
        }
    }

    public static class RemoteCustomerQuery {
        public RemoteCustomer queryCustomer(RemoteCreditAccount account) {
            return null;
        }
    }

    public static class InMemoryObjectRepository implements ObjectRepository {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public void save(String objectType, String objectId, Object value) {
            values.put(key(objectType, objectId), value);
        }

        @Override
        public Optional<?> find(String objectType, String objectId) {
            return Optional.ofNullable(values.get(key(objectType, objectId)));
        }

        @Override
        public <T> T hydrate(String objectType, String objectId, T target) {
            Object value = values.get(key(objectType, objectId));
            if (value == null) {
                throw new IllegalStateException(objectType + " not found: " + objectId);
            }
            if (value instanceof SnapshotObject snapshot && target instanceof MutableSnapshotObject mutable) {
                mutable.id = snapshot.id();
                mutable.name = snapshot.name();
                return target;
            }
            if (value instanceof AppDdlInventory stored && target instanceof AppDdlInventory inventory) {
                inventory.store = stored.store;
                inventory.goods = stored.goods;
                inventory.availableQty = stored.availableQty;
                inventory.safeQty = stored.safeQty;
                return target;
            }
            throw new IllegalStateException("Cannot hydrate " + objectType + " into "
                    + target.getClass().getName());
        }

        private String key(String objectType, String objectId) {
            return objectType + ":" + objectId;
        }
    }

    public static class CollectionQueryObjectRepository extends InMemoryObjectRepository {
        private final List<AppDdlGoods> goods;
        private String seenObjectType;
        private String seenKeyPath;
        private Object seenKeyValue;

        public CollectionQueryObjectRepository(List<AppDdlGoods> goods) {
            this.goods = goods;
        }

        @Override
        public <T> List<T> findBy(String objectType, String keyPath, Object keyValue, Class<T> type) {
            seenObjectType = objectType;
            seenKeyPath = keyPath;
            seenKeyValue = keyValue;
            return goods.stream()
                    .map(type::cast)
                    .toList();
        }
    }

    public static class InventoryCollectionQueryObjectRepository extends InMemoryObjectRepository {
        private final List<AppDdlInventory> inventory;
        private String seenObjectType;
        private List<String> seenKeyPaths;
        private List<?> seenKeyValues;

        public InventoryCollectionQueryObjectRepository(List<AppDdlInventory> inventory) {
            this.inventory = inventory;
        }

        @Override
        public <T> List<T> findBy(String objectType, List<String> keyPaths, List<?> keyValues, Class<T> type) {
            seenObjectType = objectType;
            seenKeyPaths = keyPaths;
            seenKeyValues = keyValues;
            return inventory.stream()
                    .filter(value -> keyValues.get(0).equals(value.store.name))
                    .filter(value -> keyValues.get(1).equals(value.goods.skuId))
                    .map(type::cast)
                    .toList();
        }
    }

    public interface TestQuery extends AtomicService {
        TestQueryResponse querySnapshot(MutableSnapshotObject value);
    }

    public interface GoodsCollectionQuery extends AtomicService {
        GoodsCollectionResponse queryGoods(AppDdlGoods value);
    }

    public interface ProductGoodsCollectionQuery extends AtomicService {
        GoodsCollectionResponse queryGoods(AppDdlProduct value);
    }

    public interface InventoryCollectionQuery extends AtomicService {
        InventoryCollectionResponse queryInventory(AppDdlInventory value);
    }

    public interface StoreGoodsInventoryCollectionQuery extends AtomicService {
        InventoryCollectionResponse queryInventory(AppDdlStore store, AppDdlGoods goods);
    }

    public interface IncompatibleResponseQuery extends AtomicService {
        IncompatibleQueryResponse querySnapshot(MutableSnapshotObject value);
    }

    public static class TestQueryService implements TestQuery {
        private String seenName;

        @Override
        public TestQueryResponse querySnapshot(MutableSnapshotObject value) {
            seenName = value.name;
            return new TestQueryResponse().snapshotObject(value);
        }

        @Override
        public Object execute(Object... role) {
            return querySnapshot((MutableSnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "querySnapshot";
        }
    }

    public static class GoodsCollectionQueryService implements GoodsCollectionQuery {
        private AppDdlGoods seenProbe;

        @Override
        public GoodsCollectionResponse queryGoods(AppDdlGoods value) {
            seenProbe = value;
            return new GoodsCollectionResponse();
        }

        @Override
        public Object execute(Object... role) {
            return queryGoods((AppDdlGoods) role[0]);
        }

        @Override
        public String getName() {
            return "queryGoods";
        }
    }

    public static class ProductGoodsCollectionQueryService implements ProductGoodsCollectionQuery {
        private AppDdlProduct seenProduct;

        @Override
        public GoodsCollectionResponse queryGoods(AppDdlProduct value) {
            seenProduct = value;
            return new GoodsCollectionResponse();
        }

        @Override
        public Object execute(Object... role) {
            return queryGoods((AppDdlProduct) role[0]);
        }

        @Override
        public String getName() {
            return "queryGoods";
        }
    }

    public static class InventoryCollectionQueryService implements InventoryCollectionQuery {
        private AppDdlInventory seenProbe;

        @Override
        public InventoryCollectionResponse queryInventory(AppDdlInventory value) {
            seenProbe = value;
            return new InventoryCollectionResponse();
        }

        @Override
        public Object execute(Object... role) {
            return queryInventory((AppDdlInventory) role[0]);
        }

        @Override
        public String getName() {
            return "queryInventory";
        }
    }

    public static class StoreGoodsInventoryCollectionQueryService implements StoreGoodsInventoryCollectionQuery {
        private AppDdlStore seenStore;
        private AppDdlGoods seenGoods;

        @Override
        public InventoryCollectionResponse queryInventory(AppDdlStore store, AppDdlGoods goods) {
            seenStore = store;
            seenGoods = goods;
            return new InventoryCollectionResponse();
        }

        @Override
        public Object execute(Object... role) {
            return queryInventory((AppDdlStore) role[0], (AppDdlGoods) role[1]);
        }

        @Override
        public String getName() {
            return "queryInventory";
        }
    }

    public static class SnapshotQueryService extends TestQueryService
            implements RepositoryHydrationRuleProvider, ObjectSnapshotRuleProvider, ScopeMetadataProvider {
        @Override
        public List<HydrationRule> getRepositoryHydrationRules() {
            return List.of(new HydrationRule("querySnapshot", 0,
                    "SnapshotObject", "id", "snapshotObject"));
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id"));
        }

        @Override
        public boolean isLocalScope() {
            return true;
        }

        @Override
        public boolean isAsyncScope() {
            return true;
        }

        @Override
        public String getInputHeaderName() {
            return "baseIn";
        }

        @Override
        public String getOutputHeaderName() {
            return "baseOut";
        }

        @Override
        public boolean requiresPhysicalBackendBinding() {
            return true;
        }
    }

    public static class BoundRepositorySnapshotQueryService extends SnapshotQueryService {
        @Override
        public List<HydrationRule> getRepositoryHydrationRules() {
            return List.of(new HydrationRule("querySnapshot", 0,
                    "SnapshotObject", "id", "snapshotObject", "objectStore"));
        }
    }

    public static class MissingRepositoryBindingQueryService extends SnapshotQueryService {
        @Override
        public List<HydrationRule> getRepositoryHydrationRules() {
            return List.of(new HydrationRule("querySnapshot", 0,
                    "SnapshotObject", "id", "snapshotObject", "missingStore"));
        }
    }

    public static class RepositoryHydrationUpdateKindService extends SnapshotQueryService
            implements GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_UPDATE;
        }
    }

    public static class MissingResponseWriterRepositoryService extends SnapshotQueryService {
        @Override
        public List<HydrationRule> getRepositoryHydrationRules() {
            return List.of(new HydrationRule("querySnapshot", 0,
                    "SnapshotObject", "id", "missingSnapshot"));
        }
    }

    public static class InvalidRepositoryArgumentService extends SnapshotQueryService {
        @Override
        public List<HydrationRule> getRepositoryHydrationRules() {
            return List.of(
                    new HydrationRule("querySnapshot", 1,
                            "SnapshotObject", "id", "snapshotObject"),
                    new HydrationRule("querySnapshot", 0,
                            "SnapshotObject", "missingId", "missingObject"));
        }
    }

    public static class DuplicateRepositoryTargetService extends SnapshotQueryService {
        @Override
        public List<HydrationRule> getRepositoryHydrationRules() {
            return List.of(
                    new HydrationRule("querySnapshot", 0,
                            "SnapshotObject", "id", "snapshotObject"),
                    new HydrationRule("querySnapshot", 0,
                            "SnapshotObject", "name", "snapshotObject"));
        }
    }

    public static class MutatingSnapshotService extends TestQueryService implements ObjectSnapshotRuleProvider {
        @Override
        public TestQueryResponse querySnapshot(MutableSnapshotObject value) {
            value.name = "changed";
            return new TestQueryResponse().snapshotObject(value);
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id"));
        }
    }

    public static class FailingSnapshotService implements AtomicService {
        @Override
        public Object execute(Object... role) {
            throw new IllegalStateException("fail after snapshot");
        }

        @Override
        public String getName() {
            return "failingSnapshot";
        }
    }

    public static class FailingNoopAtomicService implements AtomicService {
        @Override
        public Object execute(Object... role) {
            throw new IllegalStateException("failingNoop failed");
        }

        @Override
        public String getName() {
            return "failingNoop";
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return TransactionParticipant.noop(getTransactionResourceId());
        }
    }

    public static class NullResponseQueryService extends TestQueryService {
        @Override
        public TestQueryResponse querySnapshot(MutableSnapshotObject value) {
            super.querySnapshot(value);
            return null;
        }
    }

    public static class IncompatibleResponseQueryService implements IncompatibleResponseQuery {
        @Override
        public IncompatibleQueryResponse querySnapshot(MutableSnapshotObject value) {
            return new IncompatibleQueryResponse();
        }

        @Override
        public Object execute(Object... role) {
            return querySnapshot((MutableSnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "querySnapshot";
        }
    }

    public static class ScenarioDependingOnQuery implements Scenario {
        private final TestQuery query;

        public ScenarioDependingOnQuery() {
            query = (TestQuery) DefaultServiceContainer.getInstance().getService("querySnapshot");
        }

        @Override
        public Object execute(Object... role) {
            return query.querySnapshot((MutableSnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "snapshotScenario";
        }

        @Override
        public String startTransaction() {
            return null;
        }

        @Override
        public void endTransaction(String id) {
        }
    }

    public static class ScenarioWithTransactionCall
            implements Scenario, SagaTransactionCallProvider, GeneratedServiceKindProvider {
        @Override
        public Object execute(Object... role) {
            return null;
        }

        @Override
        public String getName() {
            return "snapshotScenario";
        }

        @Override
        public String startTransaction() {
            return null;
        }

        @Override
        public void endTransaction(String id) {
        }

        @Override
        public List<String> getSagaTransactionCallServiceNames() {
            return List.of("reserveSnapshot");
        }

        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.SCENARIO;
        }
    }

    public static class CurrentSnapshotScenario implements Scenario {
        private final Service saveCurrentOrder;

        public CurrentSnapshotScenario() {
            saveCurrentOrder = DefaultServiceContainer.getInstance().getService("saveCurrentOrder");
        }

        @Override
        public Object execute(Object... role) {
            SnapshotScenarioRequest request = (SnapshotScenarioRequest) role[0];
            DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
                @Override
                public Service getService() {
                    return saveCurrentOrder;
                }

                @Override
                public Object[] getArguments() {
                    return new Object[] { request.account() };
                }

                @Override
                public void execute() {
                    saveCurrentOrder.execute(request.account());
                }
            }));
            return null;
        }

        @Override
        public String getName() {
            return "currentSnapshotScenario";
        }

        @Override
        public String startTransaction() {
            return null;
        }

        @Override
        public void endTransaction(String id) {
        }
    }

    public static class FailingCurrentSnapshotScenario implements Scenario {
        @Override
        public Object execute(Object... role) {
            throw new IllegalStateException("scenario failed");
        }

        @Override
        public String getName() {
            return "failingCurrentSnapshotScenario";
        }

        @Override
        public String startTransaction() {
            return null;
        }

        @Override
        public void endTransaction(String id) {
        }
    }

    public static class NestedCurrentSnapshotScenario implements Scenario {
        private final Service saveNestedCurrentOrder;

        public NestedCurrentSnapshotScenario() {
            saveNestedCurrentOrder = DefaultServiceContainer.getInstance().getService("saveNestedCurrentOrder");
        }

        @Override
        public Object execute(Object... role) {
            NestedSnapshotScenarioRequest request = (NestedSnapshotScenarioRequest) role[0];
            DefaultTransactionManager.getInstance().execute(List.of(new IExecutor() {
                @Override
                public Service getService() {
                    return saveNestedCurrentOrder;
                }

                @Override
                public Object[] getArguments() {
                    return new Object[] { request.account() };
                }

                @Override
                public void execute() {
                    saveNestedCurrentOrder.execute(request.account());
                }
            }));
            return null;
        }

        @Override
        public String getName() {
            return "nestedCurrentSnapshotScenario";
        }

        @Override
        public String startTransaction() {
            return null;
        }

        @Override
        public void endTransaction(String id) {
        }
    }

    public static class RemoteSnapshotScenario implements Scenario, RemoteGeneratedService {
        @Override
        public Object execute(Object... role) {
            throw new UnsupportedOperationException("Remote scenario remoteSnapshotScenario requires an adapter implementation.");
        }

        @Override
        public String getName() {
            return "remoteSnapshotScenario";
        }

        @Override
        public String startTransaction() {
            return null;
        }

        @Override
        public void endTransaction(String id) {
        }
    }

    public interface RemoteSnapshotQuery extends AtomicService {
        SnapshotObject queryRemote(SnapshotObject request);
    }

    public interface RemoteMutableSnapshotQuery extends AtomicService {
        MutableSnapshotObject queryRemote(MutableSnapshotObject request);
    }

    public interface RemoteSnapshotListQuery extends AtomicService {
        List<SnapshotObject> queryRemoteList(SnapshotObject request);
    }

    public interface RemoteSnapshotPairQuery extends AtomicService {
        SnapshotObject queryRemotePair(SnapshotObject left, SnapshotObject right);
    }

    public static class RemoteSnapshotQueryService implements RemoteSnapshotQuery, RemoteGeneratedService {
        @Override
        public SnapshotObject queryRemote(SnapshotObject request) {
            throw new UnsupportedOperationException("Remote service remoteSnapshotQuery requires an adapter implementation.");
        }

        @Override
        public Object execute(Object... role) {
            return queryRemote((SnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "remoteSnapshotQuery";
        }
    }

    public static class RemoteMutableSnapshotQueryService
            implements RemoteMutableSnapshotQuery, RemoteGeneratedService {
        @Override
        public MutableSnapshotObject queryRemote(MutableSnapshotObject request) {
            throw new UnsupportedOperationException(
                    "Remote service remoteMutableSnapshotQuery requires an adapter implementation.");
        }

        @Override
        public Object execute(Object... role) {
            return queryRemote((MutableSnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "remoteMutableSnapshotQuery";
        }
    }

    public static class RemoteSnapshotListQueryService
            implements RemoteSnapshotListQuery, RemoteGeneratedService {
        @Override
        public List<SnapshotObject> queryRemoteList(SnapshotObject request) {
            throw new UnsupportedOperationException(
                    "Remote service remoteSnapshotListQuery requires an adapter implementation.");
        }

        @Override
        public Object execute(Object... role) {
            return queryRemoteList((SnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "remoteSnapshotListQuery";
        }
    }

    public static class RemoteSnapshotPairQueryService
            implements RemoteSnapshotPairQuery, RemoteGeneratedService {
        @Override
        public SnapshotObject queryRemotePair(SnapshotObject left, SnapshotObject right) {
            throw new UnsupportedOperationException(
                    "Remote service remoteSnapshotPairQuery requires an adapter implementation.");
        }

        @Override
        public Object execute(Object... role) {
            return queryRemotePair((SnapshotObject) role[0], (SnapshotObject) role[1]);
        }

        @Override
        public String getName() {
            return "remoteSnapshotPairQuery";
        }
    }

    public static class RemoteCompensatedPricingService
            implements AtomicService, RemoteGeneratedService, RemoteCompensationRuleProvider,
            GeneratedServiceKindProvider {
        @Override
        public Object execute(Object... role) {
            throw new UnsupportedOperationException(
                    "Remote service remoteReservePricing requires an adapter implementation.");
        }

        @Override
        public String getName() {
            return "remoteReservePricing";
        }

        @Override
        public List<String> getRemoteCompensationServiceNames() {
            return List.of("remoteReleasePricing");
        }

        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_UPDATE;
        }
    }

    public static class RemoteCompensatedQueryPricingService extends RemoteCompensatedPricingService {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class MultiRemoteCompensatedPricingService extends RemoteCompensatedPricingService {
        @Override
        public List<String> getRemoteCompensationServiceNames() {
            return List.of("remoteReleasePricing", "remoteAuditPricing");
        }
    }

    public static class RemoteReleasePricingService
            implements AtomicService, RemoteGeneratedService, GeneratedServiceKindProvider {
        @Override
        public Object execute(Object... role) {
            throw new UnsupportedOperationException(
                    "Remote service remoteReleasePricing requires an adapter implementation.");
        }

        @Override
        public String getName() {
            return "remoteReleasePricing";
        }

        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_UPDATE;
        }
    }

    public static class RemoteAuditPricingService extends RemoteReleasePricingService {
        @Override
        public String getName() {
            return "remoteAuditPricing";
        }
    }

    public static class RemoteReleaseQueryPricingService extends RemoteReleasePricingService {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class RetryableRemoteReleasePricingService extends RemoteReleasePricingService
            implements RemoteCompensationPolicyProvider {
        @Override
        public int getRemoteCompensationMaxAttempts() {
            return 2;
        }
    }

    public static class ManualRemoteReleasePricingService extends RemoteReleasePricingService
            implements RemoteCompensationPolicyProvider {
        @Override
        public int getRemoteCompensationMaxAttempts() {
            return 3;
        }

        @Override
        public boolean isRemoteCompensationManualInterventionRequired() {
            return true;
        }
    }

    public static class AsyncRemoteSnapshotQueryService
            implements RemoteSnapshotQuery, RemoteGeneratedService, ScopeMetadataProvider,
            GeneratedServiceKindProvider {
        @Override
        public SnapshotObject queryRemote(SnapshotObject request) {
            throw new UnsupportedOperationException(
                    "Remote service asyncRemoteSnapshotQuery requires an adapter implementation.");
        }

        @Override
        public Object execute(Object... role) {
            return queryRemote((SnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "asyncRemoteSnapshotQuery";
        }

        @Override
        public boolean isLocalScope() {
            return false;
        }

        @Override
        public boolean isAsyncScope() {
            return true;
        }

        @Override
        public String getInputHeaderName() {
            return "remoteIn";
        }

        @Override
        public String getOutputHeaderName() {
            return "remoteOut";
        }

        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class RemoteRepositorySnapshotQueryService extends SnapshotQueryService
            implements RemoteGeneratedService {
        @Override
        public String getName() {
            return "remoteRepositorySnapshot";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of();
        }

        @Override
        public boolean isLocalScope() {
            return false;
        }
    }

    public static class RemoteReleaseSnapshotService extends ReleaseSnapshotService
            implements RemoteGeneratedService, ScopeMetadataProvider {
        @Override
        public String getName() {
            return "remoteReleaseSnapshot";
        }

        @Override
        public boolean isLocalScope() {
            return false;
        }

        @Override
        public String getInputHeaderName() {
            return "baseIn";
        }

        @Override
        public String getOutputHeaderName() {
            return "baseOut";
        }
    }

    public static class ScenarioDependingOnRemoteQuery implements Scenario {
        private final RemoteSnapshotQuery remoteQuery;

        public ScenarioDependingOnRemoteQuery() {
            remoteQuery = (RemoteSnapshotQuery) DefaultServiceContainer.getInstance()
                    .getService("remoteSnapshotQuery");
        }

        @Override
        public Object execute(Object... role) {
            return remoteQuery.queryRemote((SnapshotObject) role[0]);
        }

        @Override
        public String getName() {
            return "scenarioDependingOnRemoteQuery";
        }

        @Override
        public String startTransaction() {
            return null;
        }

        @Override
        public void endTransaction(String id) {
        }
    }

    public static class CurrentSnapshotService implements AtomicService, ObjectSnapshotRuleProvider {
        @Override
        public Object execute(Object... role) {
            return null;
        }

        @Override
        public String getName() {
            return "saveCurrentOrder";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.AT;
        }

        @Override
        public String getTransactionResourceId() {
            return "currentOrder";
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return TransactionParticipant.noop(getTransactionResourceId());
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.current("SnapshotObject", "id"));
        }
    }

    public static class QuantumAccountingService implements QuantumService, QuantumServiceMetadataProvider {
        @Override
        public Object execute(Object... role) {
            return null;
        }

        @Override
        public String getName() {
            return "reserveCredit";
        }

        @Override
        public String getTransactionResourceId() {
            return "creditAccounting";
        }

        @Override
        public List<String> getQuantumAttributeIds() {
            return List.of("CreditAccount.availableCredit", "OrderAmount.occupiedAmount");
        }
    }

    public static class NestedCurrentSnapshotService implements AtomicService, ObjectSnapshotRuleProvider {
        @Override
        public Object execute(Object... role) {
            return null;
        }

        @Override
        public String getName() {
            return "saveNestedCurrentOrder";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.AT;
        }

        @Override
        public String getTransactionResourceId() {
            return "nestedCurrentOrder";
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return TransactionParticipant.noop(getTransactionResourceId());
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.current("NestedSnapshotObject", "customer.id"));
        }
    }

    public static class PhysicalAtService implements AtomicService {
        @Override
        public Object execute(Object... role) {
            return null;
        }

        @Override
        public String getName() {
            return "saveCurrentOrder";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.AT;
        }

        @Override
        public String getTransactionResourceId() {
            return "currentOrder";
        }
    }

    public static class FailingPhysicalAtService extends PhysicalAtService {
        @Override
        public Object execute(Object... role) {
            throw new IllegalStateException("simulated physical AT failure");
        }

        @Override
        public String getName() {
            return "failCurrentOrder";
        }
    }

    public static class AppScopedPhysicalAtWithoutIdempotencyMetadata extends PhysicalAtService
            implements ScopeMetadataProvider, GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_UPDATE;
        }

        @Override
        public boolean isLocalScope() {
            return true;
        }

        @Override
        public String getInputHeaderName() {
            return "baseIn";
        }

        @Override
        public String getOutputHeaderName() {
            return "baseOut";
        }

        @Override
        public boolean requiresPhysicalBackendBinding() {
            return true;
        }
    }

    public static class AppScopedPhysicalAtWithInvalidIdempotencyField
            extends AppScopedPhysicalAtWithoutIdempotencyMetadata {
        @Override
        public boolean requiresIdempotencyKey() {
            return true;
        }

        @Override
        public String getIdempotencyKeyFieldName() {
            return "traceId";
        }
    }

    public static class AppScopedPhysicalAtWithMissingHeaderRequest
            extends AppScopedPhysicalAtWithoutIdempotencyMetadata {
        public Object saveCurrentOrder(MissingHeaderRequest request) {
            return null;
        }

        @Override
        public boolean requiresIdempotencyKey() {
            return true;
        }

        @Override
        public String getIdempotencyKeyFieldName() {
            return "globalSerialNumber";
        }
    }

    public static class AppScopedPhysicalAtWithNumericIdempotencyHeader
            extends AppScopedPhysicalAtWithoutIdempotencyMetadata {
        public Object saveCurrentOrder(NumericHeaderRequest request) {
            return null;
        }

        @Override
        public boolean requiresIdempotencyKey() {
            return true;
        }

        @Override
        public String getIdempotencyKeyFieldName() {
            return "globalSerialNumber";
        }
    }

    public record MissingHeaderRequest(String value) {
    }

    public record NumericHeaderRequest(NumericHeader appHeader) {
        public NumericHeader getAppHeader() {
            return appHeader;
        }
    }

    public record NumericHeader(Integer globalSerialNumber) {
        public Integer getGlobalSerialNumber() {
            return globalSerialNumber;
        }
    }

    public static class NonPhysicalAtService extends PhysicalAtService implements ScopeMetadataProvider {
        @Override
        public String getName() {
            return "localCheck";
        }

        @Override
        public String getTransactionResourceId() {
            return "localCheck";
        }

        @Override
        public boolean isLocalScope() {
            return true;
        }

        @Override
        public String getInputHeaderName() {
            return "baseIn";
        }

        @Override
        public String getOutputHeaderName() {
            return "baseOut";
        }

        @Override
        public boolean requiresPhysicalBackendBinding() {
            return false;
        }
    }

    public static class PhysicalSagaService extends PhysicalAtService {
        @Override
        public String getName() {
            return "submitSnapshotSaga";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.SAGA;
        }

        @Override
        public String getTransactionResourceId() {
            return getName();
        }
    }

    public static class PhysicalSagaWithSnapshotService extends PhysicalSagaService
            implements ObjectSnapshotRuleProvider {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id"));
        }
    }

    public static class TestQueryResponse {
        public MutableSnapshotObject snapshotObject;

        public TestQueryResponse snapshotObject(MutableSnapshotObject snapshotObject) {
            this.snapshotObject = snapshotObject;
            return this;
        }

        public TestQueryResponse missingObject(MutableSnapshotObject snapshotObject) {
            this.snapshotObject = snapshotObject;
            return this;
        }
    }

    public static class GoodsCollectionResponse {
        public AppDdlGoods[] goods;

        public GoodsCollectionResponse goods(AppDdlGoods[] goods) {
            this.goods = goods;
            return this;
        }
    }

    public static class InventoryCollectionResponse {
        public List<AppDdlInventory> inventory;

        public InventoryCollectionResponse inventory(List<AppDdlInventory> inventory) {
            this.inventory = inventory;
            return this;
        }
    }

    public static class IncompatibleQueryResponse {
        public IncompatibleQueryResponse snapshotObject(String value) {
            return this;
        }
    }

    public static class ReserveSnapshotService extends TestQueryService
            implements CompensationRuleProvider, ObjectSnapshotRuleProvider {
        @Override
        public String getName() {
            return "reserveSnapshot";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.QUANTUM;
        }

        @Override
        public String getTransactionResourceId() {
            return "snapshotLedger";
        }

        @Override
        public List<String> getCompensationServiceNames() {
            return List.of("releaseSnapshot");
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id"));
        }
    }

    public static class ReserveSnapshotWithoutEvidenceService extends ReserveSnapshotService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.current("SnapshotObject", "id"));
        }
    }

    public static class SagaTransactionTargetService extends ReleaseSnapshotService {
        @Override
        public String getName() {
            return "reserveSnapshot";
        }
    }

    public static class SagaTransactionTargetWithoutEvidenceService extends SagaTransactionTargetService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.current("SnapshotObject", "id"));
        }
    }

    public static class ObjectSnapshotQueryKindService extends ReserveSnapshotService
            implements GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class ReserveSnapshotWithDuplicateCompensationService extends ReserveSnapshotService {
        @Override
        public List<String> getCompensationServiceNames() {
            return List.of("releaseSnapshot", "releaseSnapshot");
        }
    }

    public static class ReserveSnapshotSelfCompensationService extends ReserveSnapshotService {
        @Override
        public List<String> getCompensationServiceNames() {
            return List.of("reserveSnapshot");
        }
    }

    public static class ReserveSnapshotQueryKindService extends ReserveSnapshotService
            implements GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class ReserveSnapshotWithBusinessMethodService extends ReserveSnapshotService {
        public TestQueryResponse reserveSnapshot(MutableSnapshotObject value) {
            return querySnapshot(value);
        }
    }

    public static class NonLocalReserveSnapshotService extends ReserveSnapshotService
            implements ScopeMetadataProvider {
        @Override
        public boolean isLocalScope() {
            return false;
        }

        @Override
        public String getInputHeaderName() {
            return "remoteIn";
        }

        @Override
        public String getOutputHeaderName() {
            return "remoteOut";
        }
    }

    public static class ReleaseSnapshotService extends TestQueryService implements ObjectSnapshotRuleProvider {
        @Override
        public String getName() {
            return "releaseSnapshot";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.AT;
        }

        @Override
        public String getTransactionResourceId() {
            return "snapshotRelease";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id"));
        }
    }

    public static class BoundRepositorySnapshotService extends ReleaseSnapshotService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id",
                    ObjectSnapshotRule.Timing.AFTER_COMMIT, "objectStore"));
        }
    }

    public static class MissingRepositoryBindingSnapshotService extends ReleaseSnapshotService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id",
                    ObjectSnapshotRule.Timing.AFTER_COMMIT, "missingStore"));
        }
    }

    public static class TransactionSnapshotOnlyService extends ReleaseSnapshotService {
        @Override
        public String getName() {
            return "transactionSnapshot";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.transaction());
        }
    }

    public static class AggregateMemberSnapshotService extends ReleaseSnapshotService {
        @Override
        public String getName() {
            return "reserveCredit";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.appDdlArgument(0, "CreditAccount",
                    "account.customer.id"));
        }
    }

    public static class InvalidObjectSnapshotArgumentService extends TestQueryService
            implements ObjectSnapshotRuleProvider {
        public TestQueryResponse reserveSnapshot(MutableSnapshotObject value) {
            return querySnapshot(value);
        }

        @Override
        public String getName() {
            return "reserveSnapshot";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.AT;
        }

        @Override
        public String getTransactionResourceId() {
            return "snapshotLedger";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(
                    ObjectSnapshotRule.argument(1, "SnapshotObject", "id"),
                    ObjectSnapshotRule.argument(0, "SnapshotObject", "missingId"));
        }
    }

    public static class ReleaseSnapshotWithoutEvidenceService extends ReleaseSnapshotService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.current("SnapshotObject", "id"));
        }
    }

    public static class ReleaseSnapshotBeforeOnlyEvidenceService extends ReleaseSnapshotService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id",
                    ObjectSnapshotRule.Timing.BEFORE));
        }
    }

    public static class ReleaseSnapshotCommitOnlyEvidenceService extends ReleaseSnapshotService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id",
                    ObjectSnapshotRule.Timing.COMMIT));
        }
    }

    public static class ReleaseSnapshotMismatchedEvidenceService extends ReleaseSnapshotService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "OtherSnapshotObject", "id"));
        }
    }

    public static class ReleaseSnapshotWithWrongParameterService extends ReleaseSnapshotService {
        public TestQueryResponse releaseSnapshot(String value) {
            MutableSnapshotObject snapshot = new MutableSnapshotObject();
            snapshot.id = value;
            return querySnapshot(snapshot);
        }
    }

    public static class ReleaseSnapshotQueryKindService extends ReleaseSnapshotService
            implements GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class NonLocalReleaseSnapshotService extends ReleaseSnapshotService
            implements ScopeMetadataProvider {
        @Override
        public boolean isLocalScope() {
            return false;
        }

        @Override
        public String getInputHeaderName() {
            return "remoteIn";
        }

        @Override
        public String getOutputHeaderName() {
            return "remoteOut";
        }
    }

    public static class ReleaseSnapshotWithNestedCompensationService extends ReleaseSnapshotService
            implements CompensationRuleProvider {
        @Override
        public List<String> getCompensationServiceNames() {
            return List.of("auditSnapshot");
        }
    }

    public static class AuditSnapshotService extends ReleaseSnapshotService {
        @Override
        public String getName() {
            return "auditSnapshot";
        }

        @Override
        public String getTransactionResourceId() {
            return "snapshotAudit";
        }
    }

    public static class NonTransactionalReleaseSnapshotService extends ReleaseSnapshotService {
        @Override
        public TransactionMode getTransactionMode() {
            return null;
        }

        @Override
        public String getTransactionResourceId() {
            return null;
        }
    }

    public static class TrySnapshotService extends TestQueryService {
        @Override
        public String getName() {
            return "trySnapshot";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.TCC;
        }

        @Override
        public String getTransactionResourceId() {
            return "snapshotTcc";
        }
    }

    public static class TccTryLifecycleService extends TestQueryService
            implements ObjectSnapshotRuleProvider, TccLifecycleRuleProvider {
        @Override
        public String getName() {
            return "tryLifecycle";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.TCC;
        }

        @Override
        public String getTransactionResourceId() {
            return "inventoryTcc";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id"));
        }

        @Override
        public String getTccConfirmServiceName() {
            return "confirmLifecycle";
        }

        @Override
        public String getTccCancelServiceName() {
            return "cancelLifecycle";
        }
    }

    public static class MissingConfirmTccTryLifecycleService extends TccTryLifecycleService {
        @Override
        public String getTccConfirmServiceName() {
            return "missingConfirm";
        }
    }

    public static class TccTryQueryKindLifecycleService extends TccTryLifecycleService
            implements GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class TccTryWithBusinessMethodService extends TccTryLifecycleService {
        public TestQueryResponse tryLifecycle(MutableSnapshotObject value) {
            return querySnapshot(value);
        }
    }

    public static class TccConfirmLifecycleService extends TestQueryService implements ObjectSnapshotRuleProvider {
        protected static final AtomicReference<String> lastLifecycle = new AtomicReference<>();

        @Override
        public String getName() {
            return "confirmLifecycle";
        }

        public TestQueryResponse confirmLifecycle(MutableSnapshotObject value) {
            lastLifecycle.set("confirm:" + value.id + ":" + value.name);
            return querySnapshot(value);
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.TCC;
        }

        @Override
        public String getTransactionResourceId() {
            return "inventoryTcc";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "SnapshotObject", "id"));
        }
    }

    public static class TccCancelLifecycleService extends TccConfirmLifecycleService {
        @Override
        public String getName() {
            return "cancelLifecycle";
        }

        public TestQueryResponse cancelLifecycle(MutableSnapshotObject value) {
            lastLifecycle.set("cancel:" + value.id + ":" + value.name);
            return querySnapshot(value);
        }
    }

    public static class TccConfirmWithBusinessMethodService extends TccConfirmLifecycleService {
        public TestQueryResponse confirmLifecycle(MutableSnapshotObject value) {
            return querySnapshot(value);
        }
    }

    public static class TccInventoryTryRecoveryService extends TccTryLifecycleService {
        @Override
        public String getName() {
            return "tryInventoryRecovery";
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "Inventory", "goods.skuId"));
        }

        @Override
        public String getTccConfirmServiceName() {
            return "confirmInventoryRecovery";
        }

        @Override
        public String getTccCancelServiceName() {
            return "cancelInventoryRecovery";
        }
    }

    public static class TccInventoryConfirmRecoveryService extends TccConfirmLifecycleService {
        @Override
        public String getName() {
            return "confirmInventoryRecovery";
        }

        public TestQueryResponse confirmInventoryRecovery(AppDdlInventory value) {
            return new TestQueryResponse();
        }

        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of(ObjectSnapshotRule.argument(0, "Inventory", "goods.skuId"));
        }
    }

    public static class TccInventoryCancelRecoveryService extends TccInventoryConfirmRecoveryService {
        @Override
        public String getName() {
            return "cancelInventoryRecovery";
        }

        public TestQueryResponse cancelInventoryRecovery(AppDdlInventory value) {
            return new TestQueryResponse();
        }
    }

    public static class TccConfirmQueryKindLifecycleService extends TccConfirmLifecycleService
            implements GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class TccCancelQueryKindLifecycleService extends TccCancelLifecycleService
            implements GeneratedServiceKindProvider {
        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_QUERY;
        }
    }

    public static class TccCancelWithWrongParameterService extends TccCancelLifecycleService {
        public TestQueryResponse cancelLifecycle(String value) {
            MutableSnapshotObject snapshot = new MutableSnapshotObject();
            snapshot.id = value;
            return querySnapshot(snapshot);
        }
    }

    public static class TccCancelWithoutSnapshotService extends TccCancelLifecycleService {
        @Override
        public List<ObjectSnapshotRule> getObjectSnapshotRules() {
            return List.of();
        }
    }

    public static class ReserveInventoryWithReservationService
            implements AtomicService, TccReservationRuleProvider, GeneratedServiceKindProvider {
        @Override
        public Object execute(Object... role) {
            AppDdlInventory inventory = (AppDdlInventory) role[0];
            Integer quantity = (Integer) role[1];
            inventory.availableQty -= quantity;
            return null;
        }

        @Override
        public String getName() {
            return "reserveInventory";
        }

        @Override
        public String getTransactionResourceId() {
            return "inventoryTcc";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.TCC;
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return new DefaultTccTransactionParticipant(getTransactionResourceId(), getName());
        }

        @Override
        public List<TccReservationRule> getTccReservationRules() {
            return List.of(new TccReservationRule("Inventory.availableQty", "Inventory.lockedQty"));
        }

        @Override
        public GeneratedServiceKind getGeneratedServiceKind() {
            return GeneratedServiceKind.ATOMIC_UPDATE;
        }
    }

    public static class RecordingPhysicalBackendAdapter implements PhysicalBackendAdapter {
        private final List<String> events = new ArrayList<>();

        @Override
        public Set<String> backendNames() {
            return Set.of("seata");
        }

        @Override
        public String implementationType() {
            return "recording-seata";
        }

        @Override
        public void begin(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("begin", assignment, arguments);
        }

        @Override
        public void beforeExecute(PhysicalBackendAssignment assignment, TransactionContext context,
                                  Object[] arguments) {
            record("beforeExecute", assignment, arguments);
        }

        @Override
        public void afterExecute(PhysicalBackendAssignment assignment, TransactionContext context,
                                 Object[] arguments) {
            record("afterExecute", assignment, arguments);
        }

        @Override
        public void commit(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("commit", assignment, arguments);
        }

        @Override
        public void beforeTry(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("beforeTry", assignment, arguments);
        }

        @Override
        public void afterTry(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("afterTry", assignment, arguments);
        }

        @Override
        public void confirm(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("confirm", assignment, arguments);
        }

        @Override
        public void cancel(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                           Throwable cause) {
            record("cancel", assignment, arguments);
        }

        private void record(String event, PhysicalBackendAssignment assignment, Object[] arguments) {
            events.add(event + ":" + assignment.serviceName()
                    + ":" + assignment.transactionKind()
                    + ":" + assignment.targetName()
                    + ":" + arguments.length);
        }
    }

    public static class RecordingSeataTransactionBridge implements SeataTransactionBridge {
        private static final List<String> events = new ArrayList<>();

        @Override
        public String bridgeType() {
            return "recording-seata-bridge";
        }

        @Override
        public void begin(String applicationId, String transactionServiceGroup,
                          PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("begin", applicationId, transactionServiceGroup, assignment, arguments);
        }

        @Override
        public void beforeExecute(String applicationId, String transactionServiceGroup,
                                  PhysicalBackendAssignment assignment, TransactionContext context,
                                  Object[] arguments) {
            record("beforeExecute", applicationId, transactionServiceGroup, assignment, arguments);
        }

        @Override
        public void afterExecute(String applicationId, String transactionServiceGroup,
                                 PhysicalBackendAssignment assignment, TransactionContext context,
                                 Object[] arguments) {
            record("afterExecute", applicationId, transactionServiceGroup, assignment, arguments);
        }

        @Override
        public void commit(String applicationId, String transactionServiceGroup,
                           PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("commit", applicationId, transactionServiceGroup, assignment, arguments);
        }

        @Override
        public void beforeTry(String applicationId, String transactionServiceGroup,
                              PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("beforeTry", applicationId, transactionServiceGroup, assignment, arguments);
        }

        @Override
        public void afterTry(String applicationId, String transactionServiceGroup,
                             PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("afterTry", applicationId, transactionServiceGroup, assignment, arguments);
        }

        @Override
        public void confirm(String applicationId, String transactionServiceGroup,
                            PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
            record("confirm", applicationId, transactionServiceGroup, assignment, arguments);
        }

        private void record(String event, String applicationId, String transactionServiceGroup,
                            PhysicalBackendAssignment assignment, Object[] arguments) {
            events.add(event + ":" + applicationId
                    + ":" + transactionServiceGroup
                    + ":" + assignment.serviceName()
                    + ":" + assignment.transactionKind()
                    + ":" + assignment.targetName()
                    + ":" + arguments.length);
        }
    }

    public record HeaderRequest(TestHeader appHeader) {
        public TestHeader getAppHeader() {
            return appHeader;
        }
    }

    public record TestHeader(String globalSerialNumber, String messageId) {
        public String getGlobalSerialNumber() {
            return globalSerialNumber;
        }

        public String getMessageId() {
            return messageId;
        }
    }
}
