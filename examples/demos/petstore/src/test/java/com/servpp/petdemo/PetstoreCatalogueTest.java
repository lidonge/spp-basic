package com.servpp.petdemo;

import org.junit.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class PetstoreCatalogueTest {
    @Test public void openingStoreMakesAllProductsAvailableThroughGeneratedQueries() throws Exception {
        try (var shop = new PetstoreTestSupport("jdbc:h2:mem:catalogue_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")) {
            var opening = shop.call("initializeStore", Map.of("opening", Map.of()));
            var products = shop.call("browseShopProducts", Map.of("selection", Map.of(), "products", java.util.List.of()));
            assertEquals("Opening: " + opening + "; products: " + products, 4, products.path("products").size());
            assertTrue(products.toString(), products.path("products").toString().contains("British Shorthair"));
            var inventory = shop.call("readShopInventory", Map.of("key", java.util.List.of()));
            assertEquals(inventory.toString(), 4, inventory.path("key").size());
            for (var product : products.path("products")) {
                assertTrue(product.toString(), product.path("active").asBoolean());
                assertFalse(product.toString(), product.path("name").asText().isEmpty());
            }
        }
    }
}
