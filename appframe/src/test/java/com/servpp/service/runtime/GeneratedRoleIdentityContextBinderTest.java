package com.servpp.service.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Properties;

import org.junit.Test;

public class GeneratedRoleIdentityContextBinderTest {
    @Test
    public void parsesAndTrimsGeneratedRoleParticipantPlans() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "1");
        properties.setProperty("roleParticipant.0.roleType", " Inventory ");
        properties.setProperty("roleParticipant.0.performerTypes", " Store, Goods ");
        properties.setProperty("roleParticipant.0.performerCount", "2");
        properties.setProperty("roleParticipant.0.jointPerformer", "true");

        List<GeneratedRoleIdentityContextBinder.RoleBindingPlan> plans =
                GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties);

        assertEquals(1, plans.size());
        assertEquals("Inventory", plans.get(0).roleType());
        assertEquals(List.of("Store", "Goods"), plans.get(0).performerTypes());
    }

    @Test
    public void missingRoleParticipantShapeFieldsStayCompatibleWithOldGeneratedCatalogs() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "1");
        properties.setProperty("roleParticipant.0.roleType", "Inventory");
        properties.setProperty("roleParticipant.0.performerTypes", "Store,Goods");

        List<GeneratedRoleIdentityContextBinder.RoleBindingPlan> plans =
                GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties);

        assertEquals(1, plans.size());
        assertEquals(List.of("Store", "Goods"), plans.get(0).performerTypes());
    }

    @Test
    public void roleParticipantCountFailsFastOnInvalidNumber() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "many");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated role participant catalog roleParticipant.count must be a number"));
    }

    @Test
    public void roleParticipantCountFailsFastOnNegativeNumber() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "-1");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated role participant catalog roleParticipant.count must be non-negative, but was -1"));
    }

    @Test
    public void duplicateRoleTypesRepresentAlternativeRolePerformers() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "2");
        properties.setProperty("roleParticipant.0.roleType", "Inventory");
        properties.setProperty("roleParticipant.0.performerTypes", "Store,Goods");
        properties.setProperty("roleParticipant.1.roleType", "Inventory");
        properties.setProperty("roleParticipant.1.performerTypes", "Warehouse,Goods");

        List<GeneratedRoleIdentityContextBinder.RoleBindingPlan> plans =
                GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties);

        assertEquals(2, plans.size());
        assertEquals("Inventory", plans.get(0).roleType());
        assertEquals(List.of("Store", "Goods"), plans.get(0).performerTypes());
        assertEquals("Inventory", plans.get(1).roleType());
        assertEquals(List.of("Warehouse", "Goods"), plans.get(1).performerTypes());
    }

    @Test
    public void attachSelectsMatchingAlternativeRolePerformerPlan() {
        Goods goods = new Goods();
        Product product = new Product();
        GeneratedRoleIdentityContextBinder.attach(new ProductGoodsRequest(goods, product), List.of(
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods", List.of("Bundle")),
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods", List.of("Product"))));

        assertEquals(List.of(product), RoleIdentityContext.performers(goods, "Goods").orElseThrow());
    }

    @Test public void sidecarsSelectJointClauseInsteadOfFirstCompatibleAlternative() {
        var goods = new Goods(); var product = new Product(); var bundle = new Bundle();
        var alternatives = List.of(new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods",List.of("Product")),
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods",List.of("Bundle")),
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods",List.of("Product","Bundle")));
        GeneratedRoleIdentityContextBinder.attach(new AlternativeGoodsRequest(goods,product,bundle),alternatives);
        assertEquals(List.of(product,bundle),RoleIdentityContext.performers(goods,"Goods").orElseThrow());
        GeneratedRoleIdentityContextBinder.attach(new AlternativeGoodsRequest(goods,null,bundle),alternatives);
        assertEquals(List.of(bundle),RoleIdentityContext.performers(goods,"Goods").orElseThrow());
    }
    @Test public void simultaneousAlternativeSidecarsRequireAnExplicitJointClause() {
        assertThrows(IllegalStateException.class,() -> GeneratedRoleIdentityContextBinder.attach(
                new AlternativeGoodsRequest(new Goods(),new Product(),new Bundle()),List.of(
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods",List.of("Product")),
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods",List.of("Bundle")))));
    }
    private static class Bundle { }
    private record AlternativeGoodsRequest(Goods goods, Product goodsProduct, Bundle goodsBundle) {
        public Goods getGoods() { return goods; }
        public Product getGoodsProduct() { return goodsProduct; }
        public Bundle getGoodsBundle() { return goodsBundle; }
    }
    @Test
    public void attachDoesNotClearRoleIdentityWhenSameObjectAlsoAppearsAsPerformerSidecar() {
        Goods goods = new Goods();
        Product product = new Product();
        Inventory inventory = new Inventory();

        GeneratedRoleIdentityContextBinder.attach(new InventoryGoodsRequest(
                goods, product, inventory, goods), List.of(
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Goods", List.of("Product")),
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Inventory", List.of("Goods"))));

        assertEquals(List.of(product), RoleIdentityContext.performers(goods, "Goods").orElseThrow());
        assertEquals(List.of(goods), RoleIdentityContext.performers(inventory, "Inventory").orElseThrow());
    }

    @Test
    public void attachBindsNestedRolePerformerSidecars() {
        Account account = new Account();
        Customer customer = new Customer();
        Order order = new Order(account);

        GeneratedRoleIdentityContextBinder.attach(new SubmitOrderRequest(order, customer), List.of(
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Account", List.of("Customer"))));

        assertEquals(List.of(customer), RoleIdentityContext.performers(account, "Account").orElseThrow());
    }

    @Test
    public void attachKeepsExistingRolePerformersWhenRequestSidecarValueIsNull() {
        Account account = new Account();
        Customer firstCustomer = new Customer();
        Order firstOrder = new Order(account);
        GeneratedRoleIdentityContextBinder.RoleBindingPlan plan =
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Account", List.of("Customer"));

        GeneratedRoleIdentityContextBinder.attach(new SubmitOrderRequest(firstOrder, firstCustomer), List.of(plan));

        assertEquals(List.of(firstCustomer), RoleIdentityContext.performers(account, "Account").orElseThrow());

        GeneratedRoleIdentityContextBinder.attach(new SubmitOrderRequest(new Order(account), null), List.of(plan));

        assertEquals(List.of(firstCustomer), RoleIdentityContext.performers(account, "Account").orElseThrow());
    }

    @Test
    public void attachKeepsExistingRolePerformersWhenRootHasNoSidecarDeclaration() {
        Account account = new Account();
        Customer customer = new Customer();
        GeneratedRoleIdentityContextBinder.RoleBindingPlan plan =
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Account", List.of("Customer"));

        GeneratedRoleIdentityContextBinder.attach(new SubmitOrderRequest(new Order(account), customer), List.of(plan));

        assertEquals(List.of(customer), RoleIdentityContext.performers(account, "Account").orElseThrow());

        GeneratedRoleIdentityContextBinder.attach(new Order(account), List.of(plan));

        assertEquals(List.of(customer), RoleIdentityContext.performers(account, "Account").orElseThrow());
    }

    @Test
    public void attachBindsNestedArrayRolePerformerSidecarsByIndex() {
        Goods firstGoods = new Goods();
        Goods secondGoods = new Goods();
        Item firstItem = new Item();
        Item secondItem = new Item();
        Order order = new Order(new Item[] {firstItem, secondItem});

        GeneratedRoleIdentityContextBinder.attach(new SubmitOrderItemsRequest(order,
                new Goods[] {firstGoods, secondGoods}), List.of(
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Item", List.of("Goods"))));

        assertEquals(List.of(firstGoods), RoleIdentityContext.performers(firstItem, "Item").orElseThrow());
        assertEquals(List.of(secondGoods), RoleIdentityContext.performers(secondItem, "Item").orElseThrow());
    }

    @Test
    public void attachClearsStaleCollectionRolePerformersWhenSidecarIndexIsMissing() {
        Goods firstGoods = new Goods();
        Goods secondGoods = new Goods();
        Item firstItem = new Item();
        Item secondItem = new Item();
        GeneratedRoleIdentityContextBinder.RoleBindingPlan plan =
                new GeneratedRoleIdentityContextBinder.RoleBindingPlan("Item", List.of("Goods"));

        GeneratedRoleIdentityContextBinder.attach(new SubmitOrderItemsRequest(
                new Order(new Item[] {firstItem, secondItem}),
                new Goods[] {firstGoods, secondGoods}), List.of(plan));

        assertEquals(List.of(firstGoods), RoleIdentityContext.performers(firstItem, "Item").orElseThrow());
        assertEquals(List.of(secondGoods), RoleIdentityContext.performers(secondItem, "Item").orElseThrow());

        GeneratedRoleIdentityContextBinder.attach(new SubmitOrderItemsRequest(
                new Order(new Item[] {firstItem, secondItem}),
                new Goods[] {firstGoods}), List.of(plan));

        assertEquals(List.of(firstGoods), RoleIdentityContext.performers(firstItem, "Item").orElseThrow());
        assertTrue(RoleIdentityContext.performers(secondItem, "Item").isEmpty());
    }

    @Test
    public void duplicatePerformerTypesFailFast() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "1");
        properties.setProperty("roleParticipant.0.roleType", "Inventory");
        properties.setProperty("roleParticipant.0.performerTypes", "Store,Store");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated role participant catalog role Inventory performer type"
                        + " declares duplicate value Store"));
    }

    @Test
    public void performerCountMismatchFailsFast() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "1");
        properties.setProperty("roleParticipant.0.roleType", "Inventory");
        properties.setProperty("roleParticipant.0.performerTypes", "Store,Goods");
        properties.setProperty("roleParticipant.0.performerCount", "1");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated role participant catalog roleParticipant.0.performerCount"
                        + " must match performerTypes size 2, but was 1"));
    }

    @Test
    public void jointPerformerMismatchFailsFast() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "1");
        properties.setProperty("roleParticipant.0.roleType", "Inventory");
        properties.setProperty("roleParticipant.0.performerTypes", "Store,Goods");
        properties.setProperty("roleParticipant.0.performerCount", "2");
        properties.setProperty("roleParticipant.0.jointPerformer", "false");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated role participant catalog roleParticipant.0.jointPerformer"
                        + " must be true when performerCount is 2, but was false"));
    }

    @Test
    public void blankPerformerTypeEntryFailsFast() {
        Properties properties = new Properties();
        properties.setProperty("roleParticipant.count", "1");
        properties.setProperty("roleParticipant.0.roleType", "Inventory");
        properties.setProperty("roleParticipant.0.performerTypes", "Store,");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GeneratedRoleIdentityContextBinder.parseRoleBindingPlans(properties));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated role participant catalog role Inventory performer type contains blank value"));
    }

    private static class Goods {
    }

    private static class Product {
    }

    private static class Order {
        private final Account account;
        private final Item[] items;

        private Order(Account account) {
            this.account = account;
            this.items = null;
        }

        private Order(Item[] items) {
            this.account = null;
            this.items = items;
        }

        public Account getAccount() {
            return account;
        }

        public Item[] getItems() {
            return items;
        }
    }

    private static class Account {
    }

    private static class Item {
    }

    private static class Customer {
    }

    private static class Inventory {
    }

    private record ProductGoodsRequest(Goods goods, Product goodsProduct) {
        public Goods getGoods() {
            return goods;
        }

        public Product getGoodsProduct() {
            return goodsProduct;
        }
    }

    private record SubmitOrderRequest(Order order, Customer orderAccountCustomer) {
        public Order getOrder() {
            return order;
        }

        public Customer getOrderAccountCustomer() {
            return orderAccountCustomer;
        }
    }

    private record SubmitOrderItemsRequest(Order order, Goods[] orderItemsGoods) {
        public Order getOrder() {
            return order;
        }

        public Goods[] getOrderItemsGoods() {
            return orderItemsGoods;
        }
    }

    private record InventoryGoodsRequest(Goods goods, Product goodsProduct,
                                         Inventory inventory, Goods inventoryGoods) {
        public Goods getGoods() {
            return goods;
        }

        public Product getGoodsProduct() {
            return goodsProduct;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public Goods getInventoryGoods() {
            return inventoryGoods;
        }
    }
}
