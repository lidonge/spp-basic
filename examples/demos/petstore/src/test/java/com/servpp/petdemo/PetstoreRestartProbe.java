package com.servpp.petdemo;

import java.math.BigDecimal;

/** A test-only child JVM proves persistence without a generated test service. */
public final class PetstoreRestartProbe {
    private PetstoreRestartProbe() { }
    public static void main(String[] args) {
        if (args.length != 2 || !(args[1].equals("setup") || args[1].equals("resume"))) {
            throw new IllegalArgumentException("Expected test JDBC URL and setup/resume stage");
        }
        try (var shop = new PetstoreTestSupport(args[0])) {
            PetstoreTestSupport.Session session;
            if (args[1].equals("setup")) {
                session = shop.openStore("RESTART", false);
                shop.setQuantity(session, "FOOD", "2", "CART"); shop.place(session, "ORDER");
                shop.act(session, "payShopOrder", "Pay", "PAY");
            } else {
                session = new PetstoreTestSupport.Session("RESTART", "RESTART-CUSTOMER");
            }
            equal(shop.customer(session).path("wallet").asText(), "4932.20");
            equal(shop.merchant(session).path("cash").asText(), "67.80");
            equal(shop.inventory(session).path("available").asText(), "38");
            equal(shop.inventory(session).path("reserved").asText(), "2");
            var orders = shop.orders(session);
            if (orders.size() != 1 || !"PAID".equals(orders.get(0).path("status").asText())) {
                throw new AssertionError("Expected exactly one persisted paid order");
            }
        }
    }
    private static void equal(String actual, String expected) {
        if (new BigDecimal(actual).compareTo(new BigDecimal(expected)) != 0) {
            throw new AssertionError(expected + " != " + actual);
        }
    }
}
