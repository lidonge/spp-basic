package com.servpp.service.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class AppDdlCatalogTest {
    @Test
    public void parsesGeneratedAppDdlProperties() {
        AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                ("tables=PRODUCT,GOODS\n"
                        + "table.PRODUCT.category=entities\n"
                        + "table.PRODUCT.businessType=Product\n"
                        + "table.PRODUCT.columns=product_id,name\n"
                        + "table.PRODUCT.primaryKeys=product_id\n"
                        + "table.PRODUCT.indexes=name\n"
                        + "table.PRODUCT.indexNames=nameLookup\n"
                        + "table.PRODUCT.index.nameLookup.paths=name\n"
                        + "table.PRODUCT.column.product_id.type=VARCHAR\n"
                        + "table.PRODUCT.column.product_id.precision=64\n"
                        + "table.PRODUCT.column.product_id.scale=0\n"
                        + "table.PRODUCT.column.product_id.nullable=false\n"
                        + "table.PRODUCT.column.name.type=VARCHAR\n"
                        + "table.PRODUCT.column.name.precision=255\n"
                        + "table.PRODUCT.column.name.scale=0\n"
                        + "table.PRODUCT.column.name.nullable=true\n"
                        + "table.PRODUCT.column.name.objectBacked=false\n"
                        + "table.GOODS.category=roles\n"
                        + "table.GOODS.businessType=Goods\n"
                        + "table.GOODS.columns=sku_id,product_productid\n"
                        + "table.GOODS.primaryKeys=sku_id\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, catalog.tables().size());
        AppDdlCatalog.Table product = catalog.table("PRODUCT").orElseThrow();
        assertEquals("entities", product.category());
        assertEquals("Product", product.businessType());
        assertEquals(List.of("product_id"), product.primaryKeys());
        assertEquals(List.of("name"), product.indexes());
        assertEquals(List.of("nameLookup"), product.indexNames());
        assertEquals(List.of("name"), product.indexPathsByName().get("nameLookup"));
        assertEquals(List.of("product_id"), product.objectBackedPrimaryKeys());
        assertEquals(List.of(), product.platformPrimaryKeys());
        assertEquals(List.of(), product.objectBackedIndexPaths());
        assertEquals(List.of("name"), product.platformIndexPaths());
        assertEquals(List.of("name"), product.platformColumns());
        assertEquals(2, product.columns().size());
        assertEquals("product_id", product.columns().get(0).name());
        assertEquals("VARCHAR", product.columns().get(0).type());
        assertEquals(64, product.columns().get(0).precision());
        assertTrue(!product.columns().get(0).nullable());
        assertTrue(product.columns().get(0).objectBacked());
        assertTrue(!product.columns().get(1).objectBacked());
        AppDdlCatalog.Table goods = catalog.tableForBusinessType("Goods").orElseThrow();
        assertEquals("GOODS", goods.name());
        assertEquals(List.of(), goods.platformIndexPaths());
    }

    @Test
    public void mapsQualifiedIndexPathToPlatformColumnBacking() {
        AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                ("tables=GOODS\n"
                        + "table.GOODS.category=roles\n"
                        + "table.GOODS.businessType=Goods\n"
                        + "table.GOODS.columns=skuId,product_productid\n"
                        + "table.GOODS.primaryKeys=skuId\n"
                        + "table.GOODS.indexes=Goods.product.productId\n"
                        + "table.GOODS.indexNames=goodsByProduct\n"
                        + "table.GOODS.index.goodsByProduct.paths=Goods.product.productId\n"
                        + "table.GOODS.column.skuId.type=VARCHAR\n"
                        + "table.GOODS.column.skuId.objectBacked=false\n"
                        + "table.GOODS.column.product_productid.type=VARCHAR\n"
                        + "table.GOODS.column.product_productid.objectBacked=false\n")
                        .getBytes(StandardCharsets.UTF_8)));

        AppDdlCatalog.Table goods = catalog.table("GOODS").orElseThrow();
        assertEquals(List.of(), goods.objectBackedIndexPaths());
        assertEquals(List.of("Goods.product.productId"), goods.platformIndexPaths());
    }

    @Test
    public void mapsQualifiedIndexPathWithRootLocaleIndependentColumnMatching() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                    ("tables=INVENTORY\n"
                            + "table.INVENTORY.category=roles\n"
                            + "table.INVENTORY.businessType=Inventory\n"
                            + "table.INVENTORY.columns=inventory_itemid\n"
                            + "table.INVENTORY.indexes=Inventory.itemId\n"
                            + "table.INVENTORY.indexNames=inventoryByItem\n"
                            + "table.INVENTORY.index.inventoryByItem.paths=Inventory.itemId\n"
                            + "table.INVENTORY.column.inventory_itemid.type=VARCHAR\n"
                            + "table.INVENTORY.column.inventory_itemid.objectBacked=false\n")
                            .getBytes(StandardCharsets.UTF_8)));

            AppDdlCatalog.Table inventory = catalog.table("INVENTORY").orElseThrow();
            assertEquals(List.of(), inventory.objectBackedIndexPaths());
            assertEquals(List.of("Inventory.itemId"), inventory.platformIndexPaths());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void acceptsReferenceTableCategory() {
        AppDdlCatalog catalog = AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                ("tables=CUSTOMER\n"
                        + "table.CUSTOMER.category=references\n"
                        + "table.CUSTOMER.businessType=Customer\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("references", catalog.table("CUSTOMER").orElseThrow().category());
    }

    @Test
    public void rejectsDuplicateTableNames() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS,GOODS\n"
                                + "table.GOODS.category=roles\n"
                                + "table.GOODS.businessType=Goods\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains("duplicate table GOODS"));
    }

    @Test
    public void rejectsUnsupportedTableCategory() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.category=role\n"
                                + "table.GOODS.businessType=Goods\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS category must be one of entities, roles, contracts, references"
                        + ", but was role"));
    }

    @Test
    public void rejectsBlankTableNameEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        "tables=GOODS,\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL catalog table contains blank value"));
    }

    @Test
    public void rejectsDuplicateBusinessTypeMappings() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS,GOODS_VIEW\n"
                                + "table.GOODS.category=roles\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS_VIEW.category=roles\n"
                                + "table.GOODS_VIEW.businessType=Goods\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("business type Goods to multiple tables: GOODS, GOODS_VIEW"));
    }

    @Test
    public void rejectsBlankColumnNameEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.columns=skuId,\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS column contains blank value"));
    }

    @Test
    public void rejectsDuplicateColumnNames() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.columns=skuId,skuId\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS column declares duplicate value skuId"));
    }

    @Test
    public void rejectsBlankPrimaryKeyPathEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.primaryKeys=skuId,,storeName\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS primary key contains blank value"));
    }

    @Test
    public void rejectsDuplicatePrimaryKeyPaths() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.primaryKeys=skuId,skuId\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS primary key declares duplicate value skuId"));
    }

    @Test
    public void rejectsDuplicateNamedIndexes() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.indexNames=goodsByProduct,goodsByProduct\n"
                                + "table.GOODS.index.goodsByProduct.paths=product.productId\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS named index declares duplicate value goodsByProduct"));
    }

    @Test
    public void rejectsBlankNamedIndexPathEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.indexNames=goodsByProduct\n"
                                + "table.GOODS.index.goodsByProduct.paths=product.productId,\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS named index goodsByProduct path contains blank value"));
    }

    @Test
    public void rejectsDuplicateNamedIndexPathEntries() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.indexNames=goodsByProduct\n"
                                + "table.GOODS.index.goodsByProduct.paths=product.productId,product.productId\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("APP DDL table GOODS named index goodsByProduct path"
                        + " declares duplicate value product.productId"));
    }

    @Test
    public void rejectsInvalidColumnPrecision() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.columns=skuId\n"
                                + "table.GOODS.column.skuId.precision=wide\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("table.GOODS.column.skuId.precision must be an integer, but was wide"));
    }

    @Test
    public void rejectsInvalidColumnBoolean() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties(new ByteArrayInputStream(
                        ("tables=GOODS\n"
                                + "table.GOODS.businessType=Goods\n"
                                + "table.GOODS.columns=skuId\n"
                                + "table.GOODS.column.skuId.objectBacked=yes\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("table.GOODS.column.skuId.objectBacked must be true or false, but was yes"));
    }

    @Test
    public void rejectsNullInputStreamWithStartupCatalogMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties((java.io.InputStream) null));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Cannot load generated APP DDL catalog: input stream is null"));
    }

    @Test
    public void rejectsNullPropertiesWithStartupCatalogMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromProperties((java.util.Properties) null));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Cannot load generated APP DDL catalog: properties are null"));
    }

    @Test
    public void rejectsBlankResourceNameWithStartupCatalogMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppDdlCatalog.fromResource(" "));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Cannot find generated APP DDL catalog: resource name is required"));
    }
}
