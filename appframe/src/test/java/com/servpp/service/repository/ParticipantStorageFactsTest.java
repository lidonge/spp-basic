package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.runtime.AppDdlCatalog;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantStorageFactsTest {
    public static class Buyer { public String membershipNumber; public String name;
        public String getMembershipNumber() { return membershipNumber; } public String getName() { return name; } }
    public static class Goods { public String sku; public String name;
        public String getSku() { return sku; } public String getName() { return name; } }
    public static class CartLine { public Buyer buyer; public Goods goods; public int quantity;
        public Buyer getBuyer() { return buyer; } public Goods getGoods() { return goods; } public int getQuantity() { return quantity; } }
    public static class Cart { public Buyer buyer; public List<CartLine> lines;
        public Buyer getBuyer() { return buyer; } public List<CartLine> getLines() { return lines; } }
    private AppDdlCatalog catalog() {
        var properties = new Properties(); properties.setProperty("tables","BUYERS,GOODS,CART_LINES,CARTS");
        String[][] tables = {{"BUYERS","Buyer","membershipNumber"},{"GOODS","Goods","sku"},
                {"CART_LINES","CartLine","buyer_membershipnumber,goods_sku"},{"CARTS","Cart","buyer_membershipnumber"}};
        for (var row : tables) {
            String prefix = "table."+row[0]+".";
            properties.setProperty(prefix+"category","roles"); properties.setProperty(prefix+"businessType",row[1]);
            properties.setProperty(prefix+"primaryKeys",row[2]); properties.setProperty(prefix+"columns",row[2]);
            properties.setProperty(prefix+"queryService","shop.find"+row[1]);
            for (String key : row[2].split(",")) properties.setProperty(prefix+"column."+key+".type","VARCHAR");
        }
        properties.setProperty("table.BUYERS.excludedFacts","name"); return AppDdlCatalog.fromProperties(properties);
    }
    @SuppressWarnings("unchecked")
    @Test public void parentStoresMemberKeysAndChildStoresOnlyOwnFacts() {
        var buyer = new Buyer(); buyer.membershipNumber="B1"; buyer.name="Buyer name";
        var goods = new Goods(); goods.sku="CAT1"; goods.name="Cat";
        var line = new CartLine(); line.buyer=buyer; line.goods=goods; line.quantity=2;
        var cart = new Cart(); cart.buyer=buyer; cart.lines=List.of(line);
        var mapper = new ObjectMapper();
        var parent = (Map<String,Object>)ParticipantStorageFacts.project(cart,cart,catalog(),mapper);
        var storedLine = (Map<String,Object>)((List<?>)parent.get("lines")).get(0);
        assertFalse(storedLine.containsKey("quantity"));
        assertEquals(Map.of("membershipNumber","B1"),storedLine.get("buyer"));
        assertEquals(Map.of("sku","CAT1"),storedLine.get("goods"));
        var child = (Map<String,Object>)ParticipantStorageFacts.project(line,line,catalog(),mapper);
        assertEquals(2,child.get("quantity")); assertFalse(((Map<?,?>)child.get("buyer")).containsKey("name"));
    }
}
