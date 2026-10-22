package com.servpp.generator.openapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.Test;

import com.servpp.generator.db.DDLGenerator;
import com.servpp.lang.AppCompiler;
import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.service.runtime.AppDdlCatalog;

public class AppDdlPropertiesGeneratorTest {
    @Test
    public void appDdlPropertiesExposeGeneratedTableCatalog() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; String name; }
                Role Goods{ String skuId; Product product; double salePrice; }
                """, """
                domain catalog;
                @insideEntity(namingType="snake_case")
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                @insideEntity(namingType="snake_case")
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                        index goodsByProduct(Goods.product.productId);
                    }
                }
                """);
        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        Path genRoot = Files.createTempDirectory("spp-app-ddl-properties")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createAppDdlProperties(ddlGenerator, genRoot.toFile());

        Path ddlProperties = genRoot.getParent()
                .resolve("resources/META-INF/spp/app-ddl.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(ddlProperties)) {
            properties.load(input);
        }
        assertEquals("PRODUCT,GOODS", properties.getProperty("tables"));
        assertEquals("entities", properties.getProperty("table.PRODUCT.category"));
        assertEquals("Product", properties.getProperty("table.PRODUCT.businessType"));
        assertTrue(properties.getProperty("table.PRODUCT.columns").contains("productId"));
        assertTrue(properties.getProperty("table.PRODUCT.primaryKeys").contains("productId"));
        assertEquals("roles", properties.getProperty("table.GOODS.category"));
        assertEquals("Goods", properties.getProperty("table.GOODS.businessType"));
        assertTrue(properties.getProperty("table.GOODS.columns").contains("skuId"));
        assertTrue(properties.getProperty("table.GOODS.columns").contains("product_productid"));
        assertEquals("true", properties.getProperty("table.GOODS.column.product_productid.nullable"));
        assertEquals("false", properties.getProperty("table.PRODUCT.column.productId.nullable"));
        assertEquals("Goods.product.productId", properties.getProperty("table.GOODS.indexes"));
        assertEquals("goodsByProduct", properties.getProperty("table.GOODS.indexNames"));
        assertEquals("Goods.product.productId",
                properties.getProperty("table.GOODS.index.goodsByProduct.paths"));
    }

    @Test
    public void appDdlPropertiesDeduplicateLegacyIndexPathCatalog() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Store{ String name; }
                Entity Product{ String productId; }
                Role Goods{ String skuId; }
                take Product as Goods;
                Role Inventory{ int availableQty; }
                take Store, Goods as Inventory;
                """, """
                domain catalog;
                tables entities{
                    table STORE(Store){ primarykey(Store.name); }
                    table PRODUCT(Product){ primarykey(Product.productId); }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_skuid;
                        index inventoryByGoods(goods_skuid);
                        index inventoryByStoreGoods(store_name, goods_skuid);
                    }
                }
                """);
        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        Path genRoot = Files.createTempDirectory("spp-app-ddl-properties")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createAppDdlProperties(ddlGenerator, genRoot.toFile());

        Path ddlProperties = genRoot.getParent()
                .resolve("resources/META-INF/spp/app-ddl.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(ddlProperties)) {
            properties.load(input);
        }
        assertEquals("goods_skuid,store_name", properties.getProperty("table.INVENTORY.indexes"));
        assertEquals("inventoryByGoods,inventoryByStoreGoods",
                properties.getProperty("table.INVENTORY.indexNames"));
        assertEquals("goods_skuid",
                properties.getProperty("table.INVENTORY.index.inventoryByGoods.paths"));
        assertEquals("store_name,goods_skuid",
                properties.getProperty("table.INVENTORY.index.inventoryByStoreGoods.paths"));
        AppDdlCatalog catalog;
        try (InputStream input = Files.newInputStream(ddlProperties)) {
            catalog = AppDdlCatalog.fromProperties(input);
        }
        assertEquals("INVENTORY", catalog.tableForBusinessType("Inventory").orElseThrow().name());
    }

    private SppDomain compileDomain(String sppSource, String appSource) throws Exception {
        File dir = Files.createTempDirectory("spp-app-ddl-properties").toFile();
        File sppFile = write(new File(dir, "catalog.spp"), sppSource);
        File appFile = write(new File(dir, "catalog.app"), appSource);
        SppProject project = new SppProject();
        new SppCompiler(sppFile, project).compile();
        new AppCompiler(appFile, project).compile();
        return project.getDomain("catalog");
    }

    private File write(File file, String source) throws Exception {
        Files.writeString(file.toPath(), source);
        return file;
    }
}
