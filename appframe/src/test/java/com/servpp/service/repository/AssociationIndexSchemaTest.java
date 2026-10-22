package com.servpp.service.repository;

import com.servpp.service.runtime.AppDdlCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class AssociationIndexSchemaTest {
    @Test public void associationIndexesExpandCompositeIdentityWithoutMatchingSiblingFacts() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var columns = List.of(
                new AppDdlCatalog.Column("buyer_number", "VARCHAR", 32, 0, true, true),
                new AppDdlCatalog.Column("buyer_region", "VARCHAR", 32, 0, true, true),
                new AppDdlCatalog.Column("buyerName", "VARCHAR", 32, 0, true, true));
        var table = new AppDdlCatalog.Table("ORDERS", "contracts", "Order", List.of(),
                List.of("Order.buyer"), List.of("byBuyer"), List.of(),
                Map.of("byBuyer", List.of("Order.buyer", "Order.buyer_number")), columns,
                List.of(), List.of(), List.of(), List.of(), List.of());
        // Keep the in-memory schema alive while the manager opens its connection.
        try (var connection = source.getConnection()) {
            new JdbcAppDdlSchemaManager(source).ensureTables(new AppDdlCatalog(List.of(table)));
            var indexes = new java.util.LinkedHashMap<String, java.util.List<String>>();
            try (var result = connection.getMetaData().getIndexInfo(null, null, "ORDERS", false, false)) {
                while (result.next()) indexes.computeIfAbsent(result.getString("INDEX_NAME"),
                        ignored -> new java.util.ArrayList<>()).add(result.getString("COLUMN_NAME"));
            }
            assertEquals(2, indexes.size());
            for (var indexed : indexes.values()) assertEquals(List.of("BUYER_NUMBER", "BUYER_REGION"), indexed);
        }
    }
}
