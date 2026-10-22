package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.service.runtime.AppDdlCatalog;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoleCompositionGenerationTest {
    @Test public void independentRoleAndBothRelationshipLayoutsReachGeneratedCatalogAndDdl() throws Exception {
        Path source = Files.createTempDirectory("role-composition-source"), root = Files.createTempDirectory("role-composition-generated");
        Files.writeString(source.resolve("shop.spp"),"""
                domain shop;
                Entity Product { String code; }
                Entity Package { String code; }
                Role Goods;
                take Product, Package as Goods;
                take Product as Goods;
                Role Buyer { String membershipNumber; }
                Role CartLine { Buyer; Goods; int quantity; }
                """);
        Files.writeString(source.resolve("shop.app"),"""
                domain shop;
                tables entities {
                    table PRODUCTS(Product) { primarykey(Product.code); }
                    table PACKAGES(Package) { primarykey(Package.code); }
                }
                tables roles {
                    table BUYERS(Buyer) { primarykey(Buyer.membershipNumber); }
                    table GOODS(Goods) { performers inline; }
                    table CART_LINES(CartLine) { performers in CART_LINE_PLAYERS; }
                }
                """);
        GeneratorUtil.execute(source.toFile(),root.resolve("yaml").toFile(),root.resolve("java").toFile(),"example");
        var properties = new Properties();
        try (var input = Files.newInputStream(root.resolve("resources/META-INF/spp/app-ddl.properties"))) { properties.load(input); }
        var catalog = AppDdlCatalog.fromProperties(properties);
        assertTrue(catalog.roleStorage().get("CartLine").independent());
        assertEquals(2,catalog.roleStorage().get("CartLine").performers().size());
        assertEquals(Set.of(Set.of("Product","Package"),Set.of("Product")),catalog.roleStorage().get("Goods").variants().stream()
                .map(HashSet::new).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(Set.of("Buyer","Goods")),catalog.roleStorage().get("CartLine").variants().stream()
                .map(HashSet::new).collect(java.util.stream.Collectors.toSet()));
        assertEquals("CART_LINE_PLAYERS",catalog.roleStorage().get("CartLine").performerTable());
        assertEquals(Set.of("Buyer","Goods"),new HashSet<>(catalog.roleStorage().get("CartLine").performers().stream().map(AppDdlCatalog.Performer::type).toList()));
        assertEquals("example.shop.model.Buyer",catalog.roleStorage().get("CartLine").performers().stream().filter(actor -> actor.type().equals("Buyer")).findFirst().orElseThrow().javaType());
        String sql = Files.readString(root.resolve("yaml/sql/ddl.sql"));
        assertTrue(sql,sql.contains("CART_LINE_PLAYERS"));
        assertTrue(sql,sql.contains("spp_performer_Product"));
        assertTrue(sql,sql.contains("roleId"));
        String mapper = Files.readString(root.resolve("java/example/shop/model/MapperIntrinsicToCartLine.java"));
        assertTrue(mapper,mapper.contains("role.setBuyer")); assertTrue(mapper,mapper.contains("role.setGoods"));
        String yaml = Files.readString(root.resolve("yaml/shop.spp.yaml"));
        assertFalse(yaml,yaml.contains("roleId:"));
    }
}
