package com.servpp.petdemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.servpp.petdemo.PetstoreTestSupport.Session;
import com.servpp.service.runtime.ExampleJdbcFaults;
import com.servpp.service.runtime.ExampleServiceFaults;
import com.servpp.service.transaction.TransactionOutcomes;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Java composes independent generated scenes; SPP contains no test orchestration. */
public class PetstoreScenarioTest {
    private PetstoreTestSupport shop;

    @Before public void startRuntime() {
        shop = new PetstoreTestSupport("jdbc:h2:mem:petstore_test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @After public void stopRuntime() throws Exception {
        if (shop == null) return;
        try { shop.close(); }
        finally {
            try (var connection = DriverManager.getConnection(shop.jdbcUrl); var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    @Test public void cartRoleUsesPrivateIndependentIdentityAndCleansRelationshipRows() throws Exception {
        Session session = shop.openStore("ROLE-IDENTITY",false);
        shop.setQuantity(session,"FOOD","2","CREATE-LINE");
        String identity;
        try (var connection = DriverManager.getConnection(shop.jdbcUrl);var statement = connection.createStatement();
             var rows = statement.executeQuery("select cartLineId from SHOP_CART_LINES")) {
            assertTrue(rows.next()); identity = rows.getString(1); assertFalse(identity.isBlank()); assertFalse(rows.next());
        }
        JsonNode cart = shop.cart(session);
        assertFalse(cart.toString(),cart.toString().contains("cartLineId"));
        assertEquals(1,cart.path("lines").size());
        try (var connection = DriverManager.getConnection(shop.jdbcUrl);var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from SHOP_CART_LINE_PLAYERS where role_id='" + identity + "'")) {
            rows.next(); assertEquals(2,rows.getInt(1));
        }
        shop.setQuantity(session,"FOOD","3","REPLACE-LINE");
        try (var connection = DriverManager.getConnection(shop.jdbcUrl);var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from SHOP_CART_LINE_PLAYERS")) {
            rows.next(); assertEquals(2,rows.getInt(1));
        }
        shop.command("clearShopCart",session.command("Clear cart","CLEAR-ROLE"));
        try (var connection = DriverManager.getConnection(shop.jdbcUrl);var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from SHOP_CART_LINE_PLAYERS")) {
            rows.next(); assertEquals(0,rows.getInt(1));
        }
    }

    private Session order(String store, String quantity, boolean vip) {
        Session session = shop.openStore(store, vip);
        shop.setQuantity(session, "FOOD", quantity, "CART");
        shop.place(session, "ORDER");
        return session;
    }

    private void pay(Session session) { shop.act(session, "payShopOrder", "Pay", "PAY"); }
    private void ship(Session session) { shop.act(session, "shipShopOrder", "Ship", "SHIP"); }
    private void deliver(Session session) { shop.act(session, "deliverShopOrder", "Confirm delivery", "DELIVER"); }

    private static void amount(JsonNode value, String expected) {
        assertTrue("Missing numeric result", value.isNumber() || value.isTextual());
        assertEquals(expected + " != " + value, 0, new BigDecimal(expected).compareTo(new BigDecimal(value.asText())));
    }

    private void stock(Session session, String available, String reserved, String sold) {
        JsonNode facts = shop.inventory(session);
        amount(facts.path("available"), available); amount(facts.path("reserved"), reserved); amount(facts.path("sold"), sold);
    }

    private void money(Session session, String wallet, String merchant) {
        amount(shop.customer(session).path("wallet"), wallet);
        amount(shop.merchant(session).path("cash"), merchant);
    }

    private JsonNode storedOrder(Session session) {
        for (JsonNode value : shop.orders(session)) if (session.orderId.equals(value.path("orderNumber").asText())) return value;
        throw new AssertionError("Missing order " + session.orderId);
    }

    private static Throwable reject(String code, Runnable action) {
        Throwable failure = assertThrows(RuntimeException.class, action::run);
        assertCode(code, failure);
        return failure;
    }

    private static void assertCode(String code, Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (code.equals(current.getMessage())) return;
        }
        throw new AssertionError("Expected " + code + ", received " + failure, failure);
    }

    private static void assertOneOfCodes(Throwable failure, String... codes) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (String code : codes) if (code.equals(current.getMessage())) return;
        }
        throw new AssertionError("Unexpected concurrent rejection: " + failure, failure);
    }

    @Test public void orderKeepsAgreedNameAndPriceWhenGoodsChange() {
        Session session = order("HISTORY", "2", false);
        JsonNode before = storedOrder(session).path("lines").get(0);
        String originalName = before.path("name").asText();
        amount(before.path("unitPrice"), "29.90");
        shop.callProvider("product", "reviseProduct", Map.of(
                "Product", Map.of("productCode", "FOOD-001"),
                "ProductRevisionAgreement", Map.of("name", "Renamed food", "category", "Food",
                        "description", "Revised catalogue", "cost", 15)));
        shop.call("reviseShopGoodsPrice", Map.of("Goods", Map.of("sku", "FOOD-001"),
                "GoodsPriceRevisionAgreement", Map.of("unitPrice", "39.90")));
        JsonNode current = shop.call("findGoods", Map.of("Goods", Map.of("sku", "FOOD-001"))).path("Goods");
        assertEquals("Renamed food", current.path("name").asText());
        amount(current.path("price"), "39.90");
        JsonNode after = storedOrder(session).path("lines").get(0);
        assertEquals(originalName, after.path("name").asText());
        amount(after.path("unitPrice"), "29.90");
        amount(after.path("amount"), "59.80");
        JsonNode detail = shop.call("findOrderLine", Map.of("OrderLine",
                Map.of("lineNumber", before.path("lineNumber").asText()))).path("OrderLine");
        assertEquals(originalName, detail.path("name").asText());
        amount(detail.path("unitPrice"), "29.90");
        assertEquals("Renamed food", detail.path("goods").path("name").asText());
        pay(session);
        JsonNode paid = storedOrder(session).path("lines").get(0);
        assertEquals(originalName, paid.path("name").asText());
        amount(paid.path("unitPrice"), "29.90");
        shop.setQuantity(session, "FOOD", "1", "NEW-CART");
        shop.place(session, "NEW-ORDER");
        JsonNode newer = storedOrder(session).path("lines").get(0);
        assertEquals("Renamed food", newer.path("name").asText());
        amount(newer.path("unitPrice"), "39.90");
    }

    @Test public void zeroOpeningAndExternalTransfersAreConserved() throws Exception {
        shop.call("initializeStore", Map.of());
        Session session = new Session("ZERO", "ZERO-CUSTOMER");
        shop.act(session, "registerShopCustomer", "Register", "ZERO-REGISTER");
        money(session, "0", "0"); stock(session, "0", "0", "0");
        shop.fund(session, "5000"); shop.receive("FOOD-001", 10);
        money(session, "5000", "0"); stock(session, "10", "0", "0");
        try (var connection = DriverManager.getConnection(shop.jdbcUrl); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select attribute_id, current_value from petstore_quantum_value where attribute_id in ('SettlementAccount.balance', 'StockSupply.balance')")) {
                int supplies = 0;
                while (rows.next()) {
                    if (rows.getString(1).equals("SettlementAccount.balance")) assertEquals(0, new BigDecimal("-5000").compareTo(new BigDecimal(rows.getString(2))));
                    else { assertTrue(new BigDecimal(rows.getString(2)).signum() <= 0); supplies++; }
                }
                assertEquals(4, supplies);
            }
            try (var rows = statement.executeQuery("select transaction_id, delta_value from petstore_quantum_journal")) {
                Map<String, BigDecimal> totals = new java.util.HashMap<>();
                while (rows.next()) totals.merge(rows.getString(1), new BigDecimal(rows.getString(2)), BigDecimal::add);
                assertFalse(totals.isEmpty());
                for (BigDecimal total : totals.values()) assertEquals(0, total.signum());
            }
        }
        reject("INVALID_FUNDING_AMOUNT", () -> shop.fund(session, "-1"));
        reject("INVALID_QUANTITY", () -> shop.receive("FOOD-001", 0));
        money(session, "5000", "0"); stock(session, "10", "0", "0");
    }

    @Test public void happyPathExample() {
        Session session = order("HAPPY", "2", false);
        amount(storedOrder(session).path("total"), "67.80");
        assertEquals("UNPAID", storedOrder(session).path("status").asText());
        pay(session); money(session, "4932.20", "67.80");
        ship(session); deliver(session);
        assertEquals("DELIVERED", storedOrder(session).path("status").asText());
        stock(session, "38", "0", "2");
    }

    @Test public void vipPricingExample() {
        Session session = order("VIP", "4", true);
        amount(storedOrder(session).path("discount"), "11.96");
        amount(storedOrder(session).path("total"), "107.64");
        pay(session); money(session, "4892.36", "107.64");
    }

    @Test public void unpaidCancellationExample() {
        Session session = order("CANCEL", "2", false);
        shop.act(session, "cancelUnpaidShopOrder", "Cancel", "CANCEL");
        assertEquals("CANCELLED", storedOrder(session).path("status").asText());
        stock(session, "40", "0", "0"); money(session, "5000", "0");
    }

    @Test public void paidCancellationExample() {
        Session session = order("REFUND", "2", false); pay(session);
        shop.act(session, "cancelPaidShopOrder", "Cancel", "CANCEL");
        assertEquals("CANCELLED", storedOrder(session).path("status").asText());
        stock(session, "40", "0", "0"); money(session, "5000", "0");
    }

    @Test public void returnRefundExample() {
        Session session = order("RETURN", "2", false); pay(session); ship(session); deliver(session);
        shop.act(session, "requestShopReturn", "Request return", "RETURN");
        assertEquals("RETURN_REQUESTED", storedOrder(session).path("status").asText());
        shop.act(session, "approveShopReturn", "Approve return", "APPROVE");
        assertEquals("RETURNED", storedOrder(session).path("status").asText());
        stock(session, "40", "0", "0"); money(session, "5000", "0");
    }

    @Test public void multiItemExample() {
        Session session = shop.openStore("MULTI", false);
        shop.setQuantity(session, "FOOD", "2", "FOOD-CART");
        shop.setQuantity(session, "TOY", "1", "TOY-CART"); shop.place(session, "ORDER");
        assertEquals(2, storedOrder(session).path("lines").size());
        amount(storedOrder(session).path("subtotal"), "72.15"); amount(storedOrder(session).path("total"), "80.15");
        stock(session, "37", "3", "0");
    }

    @Test public void cartAdditionExample() {
        Session session = shop.openStore("CART-ADD", false);
        shop.setQuantity(session, "FOOD", "2", "CART");
        Map<String, Object> command = session.command("Add goods", "ADD"); command.put("quantity", "3");
        shop.command("addCartItem", command);
        assertEquals(1, shop.cart(session).path("lines").size());
        amount(shop.cart(session).path("lines").get(0).path("quantity"), "5");
        shop.place(session, "ORDER"); amount(storedOrder(session).path("total"), "149.50");
    }

    @Test public void cartRemovalExample() {
        Session session = shop.openStore("REMOVE", false); shop.setQuantity(session, "FOOD", "2", "CART");
        shop.act(session, "removeCartItem", "Remove goods", "REMOVE");
        assertEquals(0, shop.cart(session).path("lines").size());
        shop.setQuantity(session, "FOOD", "1", "REFILL");
        shop.act(session, "clearShopCart", "Clear cart", "CLEAR");
        assertEquals(0, shop.cart(session).path("lines").size()); stock(session, "40", "0", "0");
    }

    @Test public void quoteWithoutCommitExample() {
        Session session = shop.openStore("QUOTE", false); shop.setQuantity(session, "FOOD", "2", "CART");
        JsonNode quote = shop.act(session, "previewShopOrder", "Quote", "QUOTE");
        amount(quote.path("order").path("total"), "67.80");
        assertEquals(0, shop.orders(session).size()); assertEquals(1, shop.cart(session).path("lines").size());
        stock(session, "40", "0", "0");
    }

    @Test public void insufficientFundsExample() {
        Session session = shop.openStore("UNFUNDED", false);
        shop.setQuantity(session, "DOG", "3", "CART"); shop.place(session, "ORDER");
        reject("INSUFFICIENT_FUNDS", () -> pay(session));
        money(session, "5000", "0"); stock(session, "37", "3", "0");
        assertEquals("UNPAID", storedOrder(session).path("status").asText());
    }

    @Test public void shippedCancellationRejectedExample() {
        Session session = order("STATE", "2", false); pay(session); ship(session);
        reject("ORDER_NOT_CANCELLABLE", () -> shop.act(session, "cancelPaidShopOrder", "Cancel", "CANCEL"));
        stock(session, "38", "0", "2"); money(session, "4932.20", "67.80");
    }

    @Test public void laterOrderPreservedExample() {
        Session session = order("LATER", "2", false); String first = session.orderId;
        shop.setQuantity(session, "FOOD", "1", "LATER-CART"); shop.place(session, "LATER-ORDER");
        String later = session.orderId; session.orderId = first;
        shop.act(session, "cancelUnpaidShopOrder", "Cancel", "CANCEL");
        assertEquals("CANCELLED", storedOrder(session).path("status").asText()); session.orderId = later;
        assertEquals("UNPAID", storedOrder(session).path("status").asText()); stock(session, "39", "1", "0");
    }

    @Test public void duplicateCustomerExample() {
        Session session = shop.openStore("DUPLICATE", false);
        reject("CUSTOMER_EXISTS", () -> shop.act(session, "registerShopCustomer", "Register", "REGISTER-AGAIN"));
        assertEquals("Alex Taylor", shop.customer(session).path("name").asText()); money(session, "5000", "0");
    }

    @Test public void profileValidationScopeExample() {
        Session session = shop.openStore("PROFILE", false);
        Map<String, Object> command = session.command("Update profile", "UPDATE"); command.put("name", "");
        reject("NAME_REQUIRED", () -> shop.command("updateShopCustomer", command));
        assertEquals("Alex Taylor", shop.customer(session).path("name").asText());
        command.put("requestId", "");
        Throwable failure = reject("REQUEST_SERIAL_NUMBER_REQUIRED", () -> shop.command("updateShopCustomer", command));
        assertEquals(TransactionOutcomes.Outcome.UNREPORTED, TransactionOutcomes.outcome(failure));
    }

    @Test public void oversellExample() {
        Session session = shop.openStore("OVERSELL", false); shop.setQuantity(session, "FOOD", "11", "CART");
        reject("INSUFFICIENT_STOCK", () -> shop.place(session, "ORDER"));
        assertEquals(0, shop.orders(session).size()); stock(session, "40", "0", "0");
    }

    @Test public void multiItemAtomicRejectionExample() {
        Session session = shop.openStore("MULTI-STOCK", false);
        shop.setQuantity(session, "FOOD", "2", "CART"); shop.setQuantity(session, "TOY", "11", "SECOND");
        reject("INSUFFICIENT_STOCK", () -> shop.place(session, "ORDER"));
        assertEquals(0, shop.orders(session).size()); stock(session, "40", "0", "0");
    }

    @Test public void emptyCartExample() {
        Session session = shop.openStore("EMPTY", false);
        reject("EMPTY_CART", () -> shop.place(session, "ORDER"));
        assertEquals(0, shop.orders(session).size()); stock(session, "40", "0", "0");
    }

    @Test public void fractionalQuantityExample() {
        Session session = shop.openStore("FRACTIONAL", false);
        var failure = assertThrows(IllegalArgumentException.class, () -> shop.setQuantity(session, "FOOD", "1.5", "CART"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("1.5"));
        assertEquals(0, shop.cart(session).path("lines").size()); stock(session, "40", "0", "0");
    }

    @Test public void idempotencyExample() {
        Session session = order("REPLAY", "2", false);
        Map<String, Object> command = session.command("Pay", "PAY");
        JsonNode first = shop.command("payShopOrder", command);
        if (com.servpp.service.idempotency.BusinessIdempotencyManager.isAvailable()) {
            JsonNode replay = shop.command("payShopOrder", command);
            assertEquals(first.path("receipt"), replay.path("receipt"));
            assertTrue(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
        } else {
            reject("ORDER_NOT_PAYABLE", () -> shop.command("payShopOrder", command));
            assertFalse(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
        }
        money(session, "4932.20", "67.80");
    }

    @Test public void uiDuplicateGuardExample() {
        Session session = order("UI-GUARD", "2", false);
        JsonNode first = com.servpp.service.runtime.InvocationContext.remote(
                com.servpp.service.runtime.InvocationContext.Origin.USER, "browser", "click-one",
                () -> shop.command("payShopOrder", session.command("Pay", "PAY-ONE")));
        if (!com.servpp.service.idempotency.BusinessIdempotencyManager.isAvailable()) {
            reject("ORDER_NOT_PAYABLE", () -> com.servpp.service.runtime.InvocationContext.remote(
                    com.servpp.service.runtime.InvocationContext.Origin.USER, "browser", "click-two",
                    () -> shop.command("payShopOrder", session.command("Pay", "PAY-TWO"))));
            assertFalse(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
            money(session, "4932.20", "67.80");
            return;
        }
        JsonNode replay = com.servpp.service.runtime.InvocationContext.remote(
                com.servpp.service.runtime.InvocationContext.Origin.USER, "browser", "click-two",
                () -> shop.command("payShopOrder", session.command("Pay", "PAY-TWO")));
        assertEquals(first.path("receipt"), replay.path("receipt"));
        assertTrue(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
        money(session, "4932.20", "67.80");
    }

    @Test public void requestCollisionExample() {
        Session session = shop.openStore("COLLISION", false); shop.setQuantity(session, "FOOD", "2", "CART");
        if (com.servpp.service.idempotency.BusinessIdempotencyManager.isAvailable()) {
            reject("REQUEST_ID_REUSED", () -> shop.setQuantity(session, "FOOD", "3", "CART"));
            amount(shop.cart(session).path("lines").get(0).path("quantity"), "2");
        } else {
            shop.setQuantity(session, "FOOD", "3", "CART");
            assertFalse(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
            amount(shop.cart(session).path("lines").get(0).path("quantity"), "3");
        }
    }

    @Test public void rollbackRetryExample() {
        Session session = shop.openStore("FAULT", false); shop.setQuantity(session, "FOOD", "2", "CART");
        try (var fault = ExampleServiceFaults.activate("petshop.insertShopOrder", "after")) {
            Throwable failure = reject("EXAMPLE_SERVICE_FAULT", () -> shop.place(session, "ORDER"));
            assertEquals(TransactionOutcomes.Outcome.ROLLED_BACK, TransactionOutcomes.outcome(failure)); fault.verifyTriggered();
        }
        assertEquals(0, shop.orders(session).size()); stock(session, "40", "0", "0");
        shop.place(session, "ORDER"); stock(session, "38", "2", "0");
    }

    @Test public void paymentRollbackRetryExample() {
        Session session = order("MONEY-FAULT", "2", false);
        try (var fault = ExampleServiceFaults.activate("petshop.payOrderFunds", "after")) {
            Throwable failure = reject("EXAMPLE_SERVICE_FAULT", () -> pay(session));
            assertEquals(TransactionOutcomes.Outcome.ROLLED_BACK, TransactionOutcomes.outcome(failure)); fault.verifyTriggered();
        }
        money(session, "5000", "0"); assertEquals("UNPAID", storedOrder(session).path("status").asText());
        pay(session); money(session, "4932.20", "67.80");
    }

    @Test public void uncertainCommitQueryExample() {
        Session session = shop.openStore("UNKNOWN", false); shop.setQuantity(session, "FOOD", "2", "CART");
        try (var fault = ExampleJdbcFaults.activate("petshop.insertShopOrder", "commitAfter")) {
            Throwable failure = assertThrows(RuntimeException.class, () -> shop.place(session, "ORDER"));
            assertEquals(TransactionOutcomes.Outcome.UNKNOWN, TransactionOutcomes.outcome(failure)); fault.verifyTriggered();
        }
        JsonNode orders = shop.orders(session);
        assertEquals(1, orders.size()); assertEquals("UNPAID", orders.get(0).path("status").asText()); stock(session, "38", "2", "0");
        reject(com.servpp.service.idempotency.BusinessIdempotencyManager.isAvailable()
                ? "REQUEST_RECOVERY_REQUIRED" : "EMPTY_CART", () -> shop.place(session, "ORDER"));
        assertEquals(1, shop.orders(session).size());
        // A durable business query resolves the fact; a technical recovery workflow is still required
        // before replaying an attempt whose response was not atomically recorded with its last commit.
    }

    @Test(timeout = 60000) public void concurrentCheckoutExample() throws Exception {
        Session first = shop.openStore("CONCURRENT", false);
        Session second = new Session(first.store, first.store + "-SECOND");
        shop.act(second, "registerShopCustomer", "Register", "REGISTER-SECOND");
        shop.setQuantity(first, "FOOD", "7", "CART"); shop.setQuantity(second, "FOOD", "7", "CART-SECOND");
        var executor = Executors.newFixedThreadPool(2);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try {
            // Materialize generated requests on the test thread; calls execute concurrently.
            Object firstRequest = shop.purchaseRequest("placeShopOrder", first.command("Place order", "ORDER"));
            Object secondRequest = shop.purchaseRequest("placeShopOrder", second.command("Place order", "ORDER-SECOND"));
            List<Callable<Throwable>> tasks = List.of(() -> attemptFirstOrder(firstRequest, barrier), () -> attemptSecondOrder(secondRequest, barrier));
            var futures = new ArrayList<java.util.concurrent.Future<Throwable>>();
            for (Callable<Throwable> task : tasks) futures.add(executor.submit(task));
            int failures = 0;
            for (var future : futures) {
                Throwable failure = future.get(25, TimeUnit.SECONDS);
                if (failure != null) { assertOneOfCodes(failure, "INSUFFICIENT_STOCK", "QUANTUM_BOUND_REJECTED"); failures++; }
            }
            assertEquals(1, failures);
        } finally { executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
        assertEquals(1, shop.orders(first).size() + shop.orders(second).size()); stock(first, "33", "7", "0");
    }

    private Throwable attemptFirstOrder(Object request, java.util.concurrent.CyclicBarrier barrier) throws Exception { return attemptOrder(request, barrier); }
    private Throwable attemptSecondOrder(Object request, java.util.concurrent.CyclicBarrier barrier) throws Exception { return attemptOrder(request, barrier); }
    private Throwable attemptOrder(Object request, java.util.concurrent.CyclicBarrier barrier) throws Exception {
        barrier.await(20, TimeUnit.SECONDS);
        try { shop.execute("placeShopOrder", request); return null; }
        catch (RuntimeException failure) { return failure; }
    }

    @Test public void quantumLedgerExample() throws Exception {
        Session session = order("QUANTUM", "2", false); pay(session);
        try (var connection = DriverManager.getConnection(shop.jdbcUrl)) {
            try (var statement = connection.prepareStatement("select status, delta_value from petstore_quantum_journal where object_id in (?, ?, ?) and transaction_id not in (select transaction_id from petstore_quantum_journal where attribute_id in ('StockSupply.balance', 'SettlementAccount.balance'))")) {
                statement.setString(1, "MERCHANT-001"); statement.setString(2, session.customerId); statement.setString(3, "FOOD-001");
                BigDecimal delta = BigDecimal.ZERO; int count = 0;
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) { assertEquals("COMMITTED", rows.getString(1)); delta = delta.add(new BigDecimal(rows.getString(2))); count++; }
                }
                assertEquals(4, count); assertEquals(0, delta.compareTo(BigDecimal.ZERO));
            }
            try (var statement = connection.prepareStatement("select attribute_id, object_id, current_value from petstore_quantum_value where object_id in (?, ?, ?) and attribute_id <> 'StockSupply.balance'")) {
                statement.setString(1, "MERCHANT-001"); statement.setString(2, session.customerId); statement.setString(3, "FOOD-001");
                Map<String, String> expected = Map.of("StockLine.available", "8", "StockLine.reserved", "2", "StockLine.sold", "0", "Merchant.cash", "67.80", "Buyer.wallet", "4932.20");
                java.util.Set<String> seen = new java.util.HashSet<>();
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String attribute = rows.getString(1); assertTrue(expected.containsKey(attribute)); assertTrue(seen.add(attribute));
                        assertEquals(attribute.equals("Buyer.wallet") ? session.customerId : attribute.startsWith("StockLine.") ? "FOOD-001" : "MERCHANT-001", rows.getString(2));
                        assertEquals(0, new BigDecimal(expected.get(attribute)).compareTo(new BigDecimal(rows.getString(3))));
                    }
                }
                assertEquals(expected.keySet(), seen);
            }
        }
    }

    @Test public void pricingBoundaryExample() {
        quoteBoundary("100", "NORMAL", "100", "0");
        quoteBoundary("99.99", "NORMAL", "107.99", "8");
        quoteBoundary("111.11", "VIP", "100", "0");
        quoteBoundary("12.35", "VIP", "19.11", "8");
        assertThrows(ArithmeticException.class, () -> quoteBoundary("12.345", "VIP", "19.11", "8"));
    }

    private void quoteBoundary(String price, String tier, String total, String shipping) {
        JsonNode quote = shop.call("quoteOrder", Map.of(
                "customer", Map.of("membershipNumber", "PRICE-CUSTOMER", "tier", tier, "address", "Test address"),
                "cart", Map.of("lines", List.of(Map.of("goods", Map.of("sku", "PRICE-ITEM"), "quantity", "1"))),
                "products", List.of(Map.of("sku", "PRICE-ITEM", "name", "Price boundary input", "price", price, "active", true)),
                "order", Map.of()));
        amount(quote.path("order").path("total"), total); amount(quote.path("order").path("shipping"), shipping);
    }

    @Test public void clientPriceIgnoredExample() {
        Session session = shop.openStore("FORGED", false); shop.setQuantity(session, "FOOD", "2", "CART");
        JsonNode result = shop.call("placeShopOrder", Map.of("AppHeader", Map.of("globalSerialNumber", "FORGED-ORDER"),
                "order", Map.of("total", "0"), "customer", Map.of("membershipNumber", session.customerId, "tier", "VIP"), "products", List.of()));
        session.orderId = result.path("order").path("orderNumber").asText();
        amount(storedOrder(session).path("total"), "67.80"); pay(session); money(session, "4932.20", "67.80");
    }

    @Test(timeout = 60000) public void persistenceSetupExample() throws Exception {
        Path directory = Files.createTempDirectory("spp-petstore-java-restart-");
        try {
            String url = "jdbc:h2:file:" + directory.resolve("shop") + ";DB_CLOSE_DELAY=-1";
            restartStage(url, "setup", directory.resolve("setup.log"));
            restartStage(url, "resume", directory.resolve("resume.log"));
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void restartStage(String url, String stage, Path log) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(executable, "-cp", classpath, PetstoreRestartProbe.class.getName(), url, stage)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue("Restart stage timed out: " + stage, process.waitFor(25, TimeUnit.SECONDS));
            assertEquals(Files.readString(log), 0, process.exitValue());
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
    @Test public void reservationCommitsAcceptanceBeforeRejectedInventory() {
        Session session = shop.openStore("RESERVATION-REJECT", false);
        Object request = shop.request("reserveShopPet", Map.of(
                "customer", Map.of("membershipNumber", session.customerId),
                "goods", Map.of("sku", "FOOD-001"), "agreement", Map.of("quantity", 11)));
        Object retry = shop.mapper.convertValue(request, request.getClass());
        shop.execute("reserveShopPet", request);
        JsonNode agreement = shop.mapper.valueToTree(request).path("PetReservationAgreement");
        String number = agreement.path("reservationNumber").asText();
        assertFalse("Acceptance must have a committed business credential", number.isBlank());
        JsonNode saved = shop.call("readShopReservation", Map.of("agreement", Map.of("reservationNumber", number)))
                .path("agreement");
        assertEquals("REJECTED", saved.path("status").asText());
        assertEquals("INSUFFICIENT_STOCK", saved.path("rejectionReason").asText());
        JsonNode resultInfo = agreement.path("resultInfo");
        assertEquals("FAILED", resultInfo.path("status").asText());
        assertTrue(resultInfo.path("handled").asBoolean());
        assertEquals("QUANTUM_BOUND_REJECTED", resultInfo.path("failure").path("code").asText());
        assertEquals("QUANTUM_SETTLEMENT", resultInfo.path("failure").path("phase").asText());
        assertTrue(resultInfo.path("failure").path("attribute").asText().endsWith("StockLine.available"));
        assertEquals("-1", resultInfo.path("failure").path("actualValue").asText());
        assertEquals("0", resultInfo.path("failure").path("minimum").asText());
        assertTrue(resultInfo.path("failure").path("maximum").isNull());
        assertTrue(resultInfo.path("failure").path("reason").asText().contains("-1"));
        assertEquals("ROLLED_BACK", resultInfo.path("failure").path("recoveryStatus").asText());
        assertTrue(resultInfo.toPrettyString(), resultInfo.path("failure").path("serviceName").asText().contains("reserveRequestedInventory"));
        amount(saved.path("quantity"), "11");
        JsonNode inventory = shop.inventory(session).path("items");
        for (JsonNode item : inventory) if (item.path("goods").path("sku").asText().equals("FOOD-001")) {
            amount(item.path("available"), "10"); amount(item.path("reserved"), "0");
        }
        JsonNode replay = shop.execute("reserveShopPet", retry);
        if (com.servpp.service.idempotency.BusinessIdempotencyManager.isAvailable()) {
            assertTrue(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
            assertEquals(number, replay.path("agreement").path("reservationNumber").asText());
        } else {
            assertFalse(com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
            assertNotEquals(number, replay.path("agreement").path("reservationNumber").asText());
            assertEquals("REJECTED", replay.path("agreement").path("status").asText());
        }
        assertEquals(resultInfo, replay.path("agreement").path("resultInfo"));
    }

    @Test public void reservationWriteFailureRestoresInventoryBeforeBusinessDisposition() {
        Session session = shop.openStore("RESERVATION-WRITE-FAIL", false);
        JsonNode result;
        try (var fault = ExampleServiceFaults.activate("petshop.updatePetReservationAgreement", "after")) {
            result = shop.call("reserveShopPet", Map.of("customer", Map.of("membershipNumber", session.customerId),
                    "goods", Map.of("sku", "FOOD-001"), "agreement", Map.of("quantity", 2)));
            fault.verifyTriggered();
        }
        JsonNode agreement = result.path("agreement");
        assertEquals("REJECTED", agreement.path("status").asText());
        assertEquals("RESERVATION_NOT_COMPLETED", agreement.path("rejectionReason").asText());
        assertEquals("EXAMPLE_SERVICE_FAULT", agreement.path("resultInfo").path("failure").path("code").asText());
        JsonNode saved = shop.call("readShopReservation", Map.of("agreement",
                Map.of("reservationNumber", agreement.path("reservationNumber").asText()))).path("agreement");
        assertEquals("REJECTED", saved.path("status").asText());
        assertEquals("RESERVATION_NOT_COMPLETED", saved.path("rejectionReason").asText());
        for (JsonNode item : shop.inventory(session).path("items"))
            if (item.path("goods").path("sku").asText().equals("FOOD-001")) {
                amount(item.path("available"), "10"); amount(item.path("reserved"), "0");
            }
    }

    @Test public void reservationSecondCommitConfirmsTheAcceptedAgreement() {
        Session session = shop.openStore("RESERVATION-SUCCESS", false);
        JsonNode result = shop.call("reserveShopPet", Map.of(
                "customer", Map.of("membershipNumber", session.customerId),
                "goods", Map.of("sku", "FOOD-001"), "agreement", Map.of("quantity", 2)));
        JsonNode agreement = result.path("agreement");
        assertEquals("RESERVED", agreement.path("status").asText());
        assertEquals("SUCCEEDED", agreement.path("resultInfo").path("status").asText());
        assertTrue(agreement.path("resultInfo").path("failure").isNull());
        JsonNode saved = shop.call("readShopReservation", Map.of("agreement",
                Map.of("reservationNumber", agreement.path("reservationNumber").asText()))).path("agreement");
        assertEquals("RESERVED", saved.path("status").asText());
        for (JsonNode item : shop.inventory(session).path("items"))
            if (item.path("goods").path("sku").asText().equals("FOOD-001")) {
                amount(item.path("available"), "8"); amount(item.path("reserved"), "2");
            }
    }

    @Test public void quantumPaymentDoesNotChangeBuyersOrdinaryRevision() throws Exception {
        Session session = order("SEPARATE-FACTS", "2", false);
        String before = ordinaryRevision("Buyer", session.customerId);
        pay(session);
        assertEquals(before, ordinaryRevision("Buyer", session.customerId));
        amount(shop.customer(session).path("wallet"), "4932.20");
        try (var connection = DriverManager.getConnection(shop.jdbcUrl);
             var statement = connection.prepareStatement("select payload_json from petstore_records where object_type=? and object_id=?")) {
            statement.setString(1, "Buyer"); statement.setString(2, session.customerId);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next()); assertFalse(shop.mapper.readTree(rows.getString(1)).has("wallet"));
            }
        }
    }

    private String ordinaryRevision(String type, String id) throws Exception {
        try (var connection = DriverManager.getConnection(shop.jdbcUrl);
             var statement = connection.prepareStatement("select revision_token from petstore_records where object_type=? and object_id=?")) {
            statement.setString(1, type); statement.setString(2, id);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }

}
