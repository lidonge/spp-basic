package com.servpp.petdemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Test inputs and invocation of generated services. No business implementation lives here. */
final class PetstoreTestSupport implements AutoCloseable {
    private static final String MODELS = "com.servpp.petdemo.generated.petshop.model.";
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final String jdbcUrl;
    private final String previousUrl;
    private final LocalGeneratedServiceRuntime runtime;

    PetstoreTestSupport(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        previousUrl = System.getProperty("spp.petstore.datasource-url");
        System.setProperty("spp.petstore.datasource-url", jdbcUrl);
        DefaultServiceContainer.getInstance().clear();
        runtime = LocalGeneratedServiceRuntime.jsonSnapshots(
                new DriverManagerJdbcDataSource("org.h2.Driver", jdbcUrl, null, null), mapper, ignored -> null);
        runtime.enableExampleFaults();
        com.servpp.service.runtime.GeneratedApplicationDeployment.install(runtime, "petstore");
        callProvider("customer", "initializeCustomerDomain", Map.of("subject", Map.of(), "agreement", Map.of()));
        callProvider("product", "initializeProductDomain", Map.of("selection", Map.of(), "products", List.of(), "roots", List.of(), "agreement", Map.of()));
    }

    void callProvider(String domain, String service, Map<String, ?> participants) {
        try {
            Class<?> type = Class.forName("com.servpp.petdemo.generated." + domain + ".model." + Character.toUpperCase(service.charAt(0)) + service.substring(1) + "Request");
            Map<String,Object> values = new LinkedHashMap<>(participants);
            participantNames(service).fields().forEachRemaining(entry -> {
                if (values.containsKey(entry.getKey())) values.put(entry.getValue().asText(), values.remove(entry.getKey()));
            });
            values.put("AppHeader", Map.of("globalSerialNumber", UUID.randomUUID().toString()));
            runtime.executeGeneratedService(domain + "." + service, mapper.convertValue(values, type));
        } catch (ClassNotFoundException failure) { throw new IllegalStateException(failure); }
    }

    private JsonNode participantNames(String service) {
        try (var input = getClass().getResourceAsStream("/participant-names.json")) {
            if (input == null) throw new IllegalStateException("Missing participant name fixture");
            return mapper.readTree(input).path(service);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    Object request(String service, Map<String, ?> participants) {
        try {
            Class<?> type = Class.forName(MODELS + Character.toUpperCase(service.charAt(0)) + service.substring(1) + "Request");
            Map<String, Object> arguments = new LinkedHashMap<>(participants);
            var names = participantNames(service);
            names.fields().forEachRemaining(entry -> {
                if (arguments.containsKey(entry.getKey())) arguments.put(entry.getValue().asText(), arguments.remove(entry.getKey()));
            });
            arguments.putIfAbsent("AppHeader", Map.of("globalSerialNumber", UUID.randomUUID().toString()));
            // Metadata retains exact default Role names and explicit aliases used in JSON.
            var metadata = (com.servpp.service.runtime.ScopeMetadataProvider) DefaultServiceContainer.getInstance().getService("petshop", service);
            for (String name : metadata.getServiceParameterNames()) {
                boolean collection = java.util.Arrays.stream(type.getMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("get" + name)
                        && java.util.List.class.isAssignableFrom(method.getReturnType()));
                arguments.putIfAbsent(name, collection ? List.of() : Map.of());
            }
            return mapper.convertValue(arguments, type);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Missing generated request for " + service, failure);
        }
    }

    JsonNode execute(String service, Object request) {
        com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(null);
        String serial = com.servpp.service.idempotency.BusinessIdempotencyManager.businessTraceId(request).orElse("");
        if (com.servpp.service.runtime.InvocationContext.current().mode() == com.servpp.service.runtime.InvocationContext.Mode.REMOTE) {
            com.servpp.service.runtime.TechnicalCallHeaders.apply(request, com.servpp.service.runtime.InvocationContext.current());
            runtime.executeGeneratedService("petshop." + service, request);
        } else {
            runtime.install();
            com.servpp.service.runtime.RemoteServiceIngress.execute(
                    com.servpp.service.server.DefaultServiceContainer.getInstance().getService("petshop." + service),
                    request, "petstore-java-acceptance", serial);
        }
        var checked = com.servpp.service.runtime.BusinessChecks.scenarioResult();
        if (!checked.passed()) throw new IllegalStateException(checked.code());
        var result = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(request);
        participantNames(service).fields().forEachRemaining(entry -> {
            if (result.has(entry.getValue().asText())) result.set(entry.getKey(), result.get(entry.getValue().asText()));
        });
        return result;
    }

    JsonNode call(String service, Map<String, ?> participants) {
        return execute(service, request(service, participants));
    }

    JsonNode command(String service, Map<String, ?> command) {
        if (java.util.Set.of("registerShopCustomer", "updateShopCustomer", "addCartItem", "setCartQuantity", "removeCartItem", "clearShopCart").contains(service)) {
            Map<String, Object> participants = new LinkedHashMap<>();
            participants.put("AppHeader", Map.of("globalSerialNumber", command.get("requestId")));
            Map<String, Object> buyer = new LinkedHashMap<>(); buyer.put("membershipNumber", command.get("customerId"));
            Map<String, Object> agreement = new LinkedHashMap<>();
            if (service.equals("registerShopCustomer")) for (String field : List.of("name", "email", "address", "tier")) buyer.put(field, command.get(field));
            if (service.equals("updateShopCustomer")) for (String field : List.of("name", "email", "address", "tier")) agreement.put("new" + Character.toUpperCase(field.charAt(0)) + field.substring(1), command.get(field));
            if (java.util.Set.of("addCartItem", "setCartQuantity", "removeCartItem").contains(service)) {
                String fixtureItem = command.get("itemId").toString();
                String item = fixtureItem.substring(fixtureItem.lastIndexOf('-') + 1);
                String sku = Map.of("CAT", "CAT-001", "FOOD", "FOOD-001", "TOY", "TOY-001", "DOG", "DOG-001").getOrDefault(item, fixtureItem);
                participants.put("goods", Map.of("sku", sku));
            }
            if (java.util.Set.of("addCartItem", "setCartQuantity").contains(service)) agreement.put("quantity", command.get("quantity"));
            participants.put(service.equals("clearShopCart") ? "Buyer" : "customer", buyer);
            participants.put(service.equals("clearShopCart") ? "CartClearingAgreement" : "agreement", agreement);
            return call(service, participants);
        }
        if (java.util.Set.of("payShopOrder", "shipShopOrder", "deliverShopOrder", "cancelUnpaidShopOrder", "cancelPaidShopOrder", "approveShopReturn").contains(service)) {
            Map<String, Object> participants = new LinkedHashMap<>();
            participants.put("AppHeader", Map.of("globalSerialNumber", command.get("requestId")));
            participants.put("customer", Map.of("membershipNumber", command.get("customerId")));
            participants.put("order", Map.of("orderNumber", command.get("orderId")));
            Map<String, Object> agreement = new LinkedHashMap<>();
            if (service.equals("shipShopOrder")) agreement.put("tracking", command.get("tracking"));
            if (service.startsWith("cancel")) agreement.put("reason", command.get("reason"));
            participants.put("agreement", agreement);
            return call(service, participants);
        }
        if (service.equals("requestShopReturn")) {
            return call(service, Map.of(
                    "AppHeader", Map.of("globalSerialNumber", command.get("requestId")),
                    "Buyer", Map.of("membershipNumber", command.get("customerId")),
                    "ShopOrder", Map.of("orderNumber", command.get("orderId")),
                    "ReturnRequestAgreement", Map.of("reason", command.get("reason"))));
        }
        if (java.util.Set.of("placeShopOrder", "previewShopOrder").contains(service)) return execute(service, purchaseRequest(service, command));
        throw new IllegalArgumentException("No participant fixture mapping for " + service);
    }

    Object purchaseRequest(String service, Map<String, ?> input) {
        return request(service, Map.of("AppHeader", Map.of("globalSerialNumber", input.get("requestId")),
                "customer", Map.of("membershipNumber", input.get("customerId")), "products", List.of()));
    }

    Session openStore(String store, boolean vip) {
        call("initializeStore", Map.of("opening", Map.of("tradingName", "Test merchant", "shopName", "Test pet shop")));
        Session session = new Session(store, store + "-CUSTOMER");
        Map<String, Object> registration = session.command("Register", "REGISTER");
        registration.put("tier", vip ? "VIP" : "NORMAL");
        command("registerShopCustomer", registration);
        fund(session, "5000");
        for (String sku : List.of("CAT-001", "FOOD-001", "TOY-001", "DOG-001")) receive(sku, 10);
        return session;
    }

    JsonNode fund(Session session, String amount) {
        return call("fundShopWallet", Map.of("Buyer", Map.of("membershipNumber", session.customerId),
                "WalletFundingAgreement", Map.of("amount", amount)));
    }

    JsonNode receive(String sku, int quantity) {
        return call("receiveShopStock", Map.of("Goods", Map.of("sku", sku),
                "StockReceiptAgreement", Map.of("quantity", quantity)));
    }

    JsonNode customer(Session session) {
        return call("readShopCustomer", Map.of("key", Map.of("membershipNumber", session.customerId)))
                .path("key");
    }

    JsonNode cart(Session session) {
        return call("readShopCart", Map.of("key", Map.of("buyer", Map.of("membershipNumber", session.customerId))))
                .path("key");
    }

    JsonNode orders(Session session) {
        return call("readShopOrders", Map.of("key", Map.of("membershipNumber", session.customerId), "orders", List.of()))
                .path("orders");
    }

    JsonNode inventory(Session session) {
        JsonNode items = call("readShopInventory", Map.of("key", List.of())).path("key");
        var summary = mapper.createObjectNode(); summary.set("items", items);
        // Derived totals exist only in the test assertion fixture, never in a business participant.
        for (String property : List.of("available", "reserved", "sold")) {
            java.math.BigInteger total = java.math.BigInteger.ZERO;
            for (JsonNode item : items) total = total.add(item.path(property).bigIntegerValue());
            summary.put(property, total);
        }
        return summary;
    }

    JsonNode merchant(Session session) {
        return call("readShopMerchant", Map.of("key", Map.of()))
                .path("key");
    }

    JsonNode setQuantity(Session session, String item, String quantity, String requestId) {
        Map<String, Object> command = session.command("Set quantity", requestId);
        command.put("itemId", session.store + "-" + item);
        command.put("quantity", quantity);
        return command("setCartQuantity", command);
    }

    JsonNode place(Session session, String requestId) {
        JsonNode result = command("placeShopOrder", session.command("Place order", requestId));
        session.orderId = result.path("order").path("orderNumber").asText();
        return result;
    }

    JsonNode act(Session session, String service, String action, String requestId) {
        return command(service, session.command(action, requestId));
    }

    @Override public void close() {
        // Do not clear persistent stores: the restart probe must retain physical evidence.
        com.servpp.service.transaction.DefaultTransactionManager.clearLastContext();
        DefaultServiceContainer.getInstance().clear();
        if (previousUrl == null) System.clearProperty("spp.petstore.datasource-url");
        else System.setProperty("spp.petstore.datasource-url", previousUrl);
    }

    /** A Java test fixture, never a generated business participant or service. */
    static final class Session {
        final String store, customerId;
        String orderId = "";
        Session(String store, String customerId) { this.store = store; this.customerId = customerId; }

        Map<String, Object> command(String action, String requestId) {
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("storeId", store); command.put("customerId", customerId);
            command.put("requestId", requestId); command.put("action", action);
            command.put("name", "Alex Taylor"); command.put("email", "pet@example.test");
            command.put("address", "1 Pet Lane, Demo City"); command.put("tier", "NORMAL");
            command.put("orderId", orderId); command.put("itemId", store + "-FOOD");
            command.put("quantity", "2"); command.put("tracking", "DEMO-TRACKING");
            command.put("reason", "Test cancellation or return");
            return command;
        }
    }
}
