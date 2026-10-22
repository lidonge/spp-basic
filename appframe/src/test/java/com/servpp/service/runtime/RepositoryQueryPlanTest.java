package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.servpp.service.repository.RepositoryHydratingServiceProxy.HydrationRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RepositoryQueryPlanTest {
    @Test
    public void executableContractIncludesRuntimeRepositoryIdentityAndColumns() {
        RepositoryQueryPlan plan = queryPlan(List.of("store_name", "goods_skuid"));

        assertTrue(plan.executable());
        assertEquals("REPOSITORY_QUERY_EXECUTABLE", plan.executionReadiness());
        assertEquals("service=queryInventoryByStoreGoods:method=queryInventoryByStoreGoods:object=Inventory"
                        + ":repository=objectStore:adapter=jdbcObjectRepository"
                        + ":lookup=COLLECTION_BY_COMPOSITE_KEY:arguments=name+skuId"
                        + ":paths=store_name+goods_skuid:columns=store_name+goods_skuid"
                        + ":identitySources=ROLE_PARTICIPANT_IDENTITY"
                        + ":relation=MULTI_EXTERNAL_AGGREGATE"
                        + ":source=APP_DDL_ROLE_TAKE_PRIMARY_KEY:REPOSITORY_QUERY_EXECUTABLE",
                plan.executionContract());
    }

    @Test
    public void missingAppDdlColumnPreventsExecutableContract() {
        RepositoryQueryPlan plan = queryPlan(List.of("store_name"));

        assertFalse(plan.executable());
        assertEquals("REPOSITORY_QUERY_NOT_EXECUTABLE", plan.executionReadiness());
        assertTrue(plan.executionContract().endsWith(":REPOSITORY_QUERY_NOT_EXECUTABLE"));
    }

    private RepositoryQueryPlan queryPlan(List<String> repositoryColumns) {
        HydrationRule rule = new HydrationRule(
                "queryInventoryByStoreGoods",
                "Inventory",
                List.of(
                        new HydrationRule.KeyTarget(0, "Inventory", "name", "store_name"),
                        new HydrationRule.KeyTarget(0, "Inventory", "skuId", "goods_skuid")),
                "setInventory",
                "objectStore",
                true,
                "app.repository");
        List<RepositoryQueryKeyPlan> keyPlans = List.of(
                new RepositoryQueryKeyPlan(0, "Inventory", "name", "store_name", "store",
                        "ROLE_PARTICIPANT_IDENTITY", "name", "store_name", true, true),
                new RepositoryQueryKeyPlan(0, "Inventory", "skuId", "goods_skuid", "goods",
                        "ROLE_PARTICIPANT_IDENTITY", "skuId", "goods_skuid", true, true));
        return new RepositoryQueryPlan(
                "queryInventoryByStoreGoods",
                "queryInventoryByStoreGoods",
                "Inventory",
                "setInventory",
                true,
                "LIST",
                "Inventory",
                null,
                "objectStore",
                "jdbcObjectRepository",
                true,
                Map.of("appDdlTables", "true"),
                "APP_DDL_ROLE_TAKE_PRIMARY_KEY",
                "INVENTORY",
                null,
                List.of("store_name", "goods_skuid"),
                List.of("store_name", "goods_skuid"),
                true,
                true,
                false,
                List.of("name", "skuId"),
                List.of("store_name", "goods_skuid"),
                repositoryColumns,
                true,
                "COLLECTION_BY_COMPOSITE_KEY",
                2,
                true,
                true,
                2,
                List.of("store", "goods"),
                "MULTI_EXTERNAL_AGGREGATE",
                keyPlans,
                List.of(rule));
    }
}
