package com.servpp.generator.db;

import com.servpp.lang.AppCompiler;
import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.app.AppTables;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.JDBCType;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DDLGeneratorTest {
    @Test public void associationsToIndependentRolesUseThePrivatePlatformKey() throws Exception {
        var domain=compileDomain("domain catalog; Role Account { String email; } Contract Agreement { String receipt; Account holder; }",
                "domain catalog; tables roles { table ACCOUNTS(Account) { primarykey String accountRowId; identity independent; index contact(Account.email); } } "
                + "tables contracts { table AGREEMENTS(Agreement) { primarykey(Agreement.receipt); foreignkey(Agreement.holder) references Account; } }");
        var generator=new DDLGenerator(domain,null);var accounts=generator.getTableList().get("ACCOUNTS");var agreements=generator.getTableList().get("AGREEMENTS");
        assertTrue(accounts.getPrimaryKeys().containsKey("accountRowId"));
        assertTrue(agreements.getColumns().getArrayList().stream().map(DbColumn::getName).toList().toString(),agreements.getColumns().containsKey("holder_accountrowid"));
        assertTrue(!agreements.getColumns().containsKey("holder_email"));
        assertTrue(((SppClass)domain.getSppClass("Account")).getField("accountRowId")==null);
    }
    @Test public void participantSnapshotsUseWholeJsonClobsIncludingCollections() throws Exception {
        var domain = compileDomain("domain catalog; Contract Receipt { String receiptCode; String reason; } "
                + "Role FailureRecord { String handlingCode; Receipt original; Receipt[] attempts; }",
                "domain catalog; tables roles { table FAILURES(FailureRecord) { primarykey(FailureRecord.handlingCode); "
                + "snapshot(FailureRecord.original, FailureRecord.attempts); } }");
        var generator = new DDLGenerator(domain,null);
        var table = generator.getTableList().get("FAILURES");
        assertEquals(JDBCType.CLOB,table.getColumns().get("original").getJdbcType());
        assertEquals(JDBCType.CLOB,table.getColumns().get("attempts").getJdbcType());
        assertTrue(table.getColumns().get("original_reason") == null);
    }
    @Test
    public void exactPhysicalNumbersKeepLogicalTypesAndGenerateMappedTextColumns() throws Exception {
        String spp = """
                domain catalog;
                Role Evidence { String id; double value; int count; }
                """;
        String app = """
                domain catalog;
                tables roles { table ITEMS(Evidence) { primarykey(Evidence.id); } }
                map ITEMS { value = String(4096) value_text; count = String(100) count_text; }
                """;
        SppDomain domain = compileDomain(spp, app);
        domain.getRuleBlock().getNumericBindings().add(
                new com.servpp.lang.models.app.NumericBinding("Evidence.value", "BigDecimal"));
        domain.getRuleBlock().getNumericBindings().add(
                new com.servpp.lang.models.app.NumericBinding("int", "BigInteger"));
        DDLGenerator generator = new DDLGenerator(domain, null);
        assertTrue(generator.validateMapperDeclarations().toString(), generator.validateMapperDeclarations().isEmpty());
        DbTable table = generator.getTableList().get("ITEMS");
        assertEquals(JDBCType.VARCHAR, table.getColumns().get("value_text").getJdbcType());
        assertEquals(4096, table.getColumns().get("value_text").getPrecision());
        assertEquals(JDBCType.VARCHAR, table.getColumns().get("count_text").getJdbcType());
        assertEquals(100, table.getColumns().get("count_text").getPrecision());
        assertEquals("double", ((SppClass) domain.getSppClass("Evidence")).getField("value").getType().getName());
        assertTrue(table.getColumns().get("value") == null);
        assertTrue(table.getColumns().get("count") == null);

        DDLGenerator unbound = new DDLGenerator(compileDomain(spp, app), null);
        assertEquals(2, unbound.validateMapperDeclarations().stream()
                .filter(error -> error.contains("declares type String but generated table column")).count());
    }

    @Test
    public void ddlGeneratorKeepsAppTableCategoryAndMapsEnumAsVarchar() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Enum GoodsStatus{ OnSale,OffSale }
                Role Goods{
                    String skuId;
                    GoodsStatus status;
                }
                """, """
                domain catalog;
                @column(varchar_precision="80", nullable="true",
                    decimal_precision="19", decimal_scale="2",
                    type_boolean="INTEGER", type_String="VARCHAR")
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                """);

        SppClass goodsBusinessType = (SppClass) domain.getSppClass("Goods");
        assertTrue(goodsBusinessType.getField("rowId") == null);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(goodsBusinessType.getField("rowId") == null);
        assertTrue(goods.getAppTable().getEntity().getField("rowId") != null);
        assertEquals(AppTables.Category.roles, goods.getCategory());
        assertEquals("roles", goods.getCategoryName());
        assertEquals(JDBCType.VARCHAR, goods.getColumns().get("status").getJdbcType());
        assertEquals(80, goods.getColumns().get("status").getPrecision());
        assertEquals(JDBCType.VARCHAR, goods.getPrimaryKeys().get("rowId").getJdbcType());
        assertEquals(80, goods.getPrimaryKeys().get("rowId").getPrecision());
    }

    @Test
    public void ddlGeneratorSkipsTablesWithInvalidCategoryOrDuplicateMappings() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Entity Store{ String storeId; }
                Entity Warehouse{ String warehouseId; }
                Entity Supplier{ String supplierId; }
                Role Goods{ String skuId; }
                Contract SalesOrder{ String orderNo; }
                """, """
                domain catalog;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table PRODUCT_DUP(Product){ primarykey String productId; }
                    table GOODS_AS_ENTITY(Goods){ primarykey String skuId; }
                    table DUPLICATE_NAME(Warehouse){ primarykey String warehouseId; }
                    table STORE_AND_WAREHOUSE(Store, Warehouse){ primarykey(Store.storeId); }
                    table SUPPLIER(Supplier){ primarykey String supplierId; }
                }
                tables roles{
                    table DUPLICATE_NAME(Goods){ primarykey String skuId; }
                }
                tables unknown{
                    table SALES_ORDER(SalesOrder){ primarykey String orderNo; }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("SUPPLIER") != null);
        assertTrue(ddlGenerator.getTableList().get("PRODUCT") == null);
        assertTrue(ddlGenerator.getTableList().get("PRODUCT_DUP") == null);
        assertTrue(ddlGenerator.getTableList().get("GOODS_AS_ENTITY") == null);
        assertTrue(ddlGenerator.getTableList().get("STORE_AND_WAREHOUSE") == null);
        assertTrue(ddlGenerator.getTableList().get("DUPLICATE_NAME") == null);
        assertTrue(ddlGenerator.getTableList().get("SALES_ORDER") == null);
    }

    @Test
    public void ddlGeneratorAppliesAndFiltersExplicitColumnPrecisionScale() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{ String skuId; }
                Role Inventory{ String inventoryId; }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                        notnull String(120) displayName;
                        notnull double(12, 3) saleAmount;
                    }
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                        notnull int(10) quantity;
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(goods != null);
        assertEquals(120, goods.getColumns().get("displayName").getPrecision());
        assertEquals(12, goods.getColumns().get("saleAmount").getPrecision());
        assertEquals(3, goods.getColumns().get("saleAmount").getScale());
        assertTrue(ddlGenerator.getTableList().get("INVENTORY") == null);
    }

    @Test
    public void ddlGeneratorKeepsQuantumFieldsOutOfObjectTables() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity CreditAccount{
                    String accountNo;
                    quantum double availableCredit;
                    double usedCredit;
                }
                Role OrderAmount{
                    String amountId;
                    quantum double occupiedAmount;
                    double payableAmount;
                }
                """, """
                domain catalog;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey(CreditAccount.accountNo);
                    }
                }
                tables roles{
                    table ORDER_AMOUNT(OrderAmount){
                        primarykey String orderAmountId;
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable creditAccount = ddlGenerator.getTableList().get("CREDIT_ACCOUNT");
        DbTable orderAmount = ddlGenerator.getTableList().get("ORDER_AMOUNT");

        assertTrue(creditAccount != null);
        assertTrue(orderAmount != null);
        assertTrue(creditAccount.getColumns().get("accountNo") != null);
        assertTrue(creditAccount.getColumns().get("usedCredit") != null);
        assertTrue(creditAccount.getColumns().get("availableCredit") == null);
        assertTrue(orderAmount.getColumns().get("orderAmountId") != null);
        assertTrue(orderAmount.getColumns().get("payableAmount") != null);
        assertTrue(orderAmount.getColumns().get("occupiedAmount") == null);
    }

    @Test
    public void ddlGeneratorKeepsJointRolePerformerIdentityAsAppDdlPlatformKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Store{ String name; }
                Entity Product{ String productId; }
                Role Goods{
                    String skuId;
                    String title;
                }
                take Product as Goods;
                Role Inventory{
                    quantum int availableQty;
                    int safeQty;
                }
                take Store, Goods as Inventory;
                """, """
                domain catalog;
                @insideEntity(namingType="snake_case")
                tables entities{
                    table STORE(Store){ primarykey(Store.name); }
                    table PRODUCT(Product){ primarykey(Product.productId); }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey(Goods.skuId);
                        null String product_productid;
                    }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_skuid;
                        null int lockedQty;
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable inventory = ddlGenerator.getTableList().get("INVENTORY");

        assertTrue(inventory != null);
        assertEquals(AppTables.Category.roles, inventory.getCategory());
        assertTrue(inventory.getPrimaryKeys().get("store_name") != null);
        assertTrue(inventory.getPrimaryKeys().get("goods_skuid") != null);
        assertEquals(JDBCType.VARCHAR, inventory.getPrimaryKeys().get("store_name").getJdbcType());
        assertEquals(JDBCType.VARCHAR, inventory.getPrimaryKeys().get("goods_skuid").getJdbcType());
        assertTrue(inventory.getColumns().get("store_name") != null);
        assertTrue(inventory.getColumns().get("goods_skuid") != null);
        assertTrue(inventory.getColumns().get("safeQty") != null);
        assertTrue(inventory.getColumns().get("lockedQty") != null);
        assertTrue(inventory.getColumns().get("availableQty") == null);
        assertTrue(inventory.getColumns().get("store") == null);
        assertTrue(inventory.getColumns().get("goods") == null);
    }

    @Test
    public void ddlGeneratorRejectsQuantumFieldsAsTableKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity CreditAccount{
                    quantum double availableCredit;
                    double usedCredit;
                }
                Role OrderAmount{
                    String amountId;
                    quantum double occupiedAmount;
                }
                """, """
                domain catalog;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey(CreditAccount.availableCredit);
                    }
                }
                tables roles{
                    table ORDER_AMOUNT(OrderAmount){
                        primarykey String orderAmountId;
                        foreignkey(OrderAmount.occupiedAmount);
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("CREDIT_ACCOUNT") == null);
        assertTrue(ddlGenerator.getTableList().get("ORDER_AMOUNT") == null);
    }

    @Test
    public void ddlGeneratorSkipsTablesWithAmbiguousExplicitColumnModifiers() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{ String skuId; }
                Role Inventory{ String inventoryId; }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                        notnull String displayName;
                    }
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                        null notnull String externalCode;
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("GOODS") != null);
        assertTrue(ddlGenerator.getTableList().get("INVENTORY") == null);
    }

    @Test
    public void ddlGeneratorSkipsTablesWithUnstableExplicitColumns() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Role Goods{ String skuId; Product product; }
                Role Inventory{ String inventoryId; }
                Role PriceTag{ String priceTagId; }
                """, """
                domain catalog;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                    }
                    table GOODS(Goods){
                        primarykey String skuId;
                        notnull String skuId;
                        notnull String product;
                    }
                    table PRICE_TAG(PriceTag){
                        primarykey String priceTagId;
                        notnull int priceTagId;
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("INVENTORY") != null);
        assertTrue(ddlGenerator.getTableList().get("GOODS") == null);
        assertTrue(ddlGenerator.getTableList().get("PRICE_TAG") == null);
    }

    @Test
    public void ddlGeneratorSkipsTablesWithUnstableImplicitObjectOutputColumns() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Role Goods{ String skuId; String productProductId; Product product; }
                Role Category{ String categoryId; Category parent; }
                Role CatalogEntry{ String entryId; Category category; }
                Role Inventory{ String inventoryId; }
                """, """
                domain catalog;
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                    }
                    table CATALOG_ENTRY(CatalogEntry){
                        primarykey String entryId;
                    }
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("GOODS") == null);
        assertTrue(ddlGenerator.getTableList().get("CATALOG_ENTRY") == null);
        assertTrue(ddlGenerator.getTableList().get("INVENTORY") != null);
    }

    @Test
    public void ddlGeneratorValidatesMapperColumnsAgainstGeneratedTable() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{
                    String skuId;
                    String name;
                }
                """, """
                domain catalog;
                @column(varchar_precision="80", nullable="true",
                    decimal_precision="19", decimal_scale="2",
                    type_boolean="INTEGER", type_String="VARCHAR")
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                map GOODS{
                    skuId = String name;
                    missingColumn = String missing_column;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        List<String> errors = ddlGenerator.validateMapperDeclarations();

        assertTrue(errors.contains(
                "Mapper GOODS column missingColumn is not defined in generated table GOODS."));
        assertTrue(errors.contains(
                "Mapper GOODS output column name is produced by multiple generated columns."));
    }

    @Test
    public void ddlGeneratorRejectsDuplicateMapperBlocks() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{
                    String skuId;
                    String name;
                }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                map GOODS{
                    skuId = String sku_id;
                }
                map GOODS{
                    name = String goods_name;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        List<String> errors = ddlGenerator.validateMapperDeclarations();

        assertTrue(errors.toString(), errors.contains(
                "APP mapper GOODS is declared multiple times."));
    }

    @Test
    public void ddlGeneratorRejectsUnsupportedMapperPrecisionScale() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{
                    String skuId;
                    int quantity;
                    double saleAmount;
                }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                map GOODS{
                    skuId = String(120) sku_id;
                    quantity = int(10) quantity;
                    saleAmount = double(12, 3) sale_amount;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        List<String> errors = ddlGenerator.validateMapperDeclarations();
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(errors.toString(), errors.contains(
                "Mapper GOODS column quantity with type int cannot declare precision."));
        assertEquals("skuId", goods.getColumns().get("skuId").getName());
        assertEquals("saleAmount", goods.getColumns().get("saleAmount").getName());
    }

    @Test
    public void ddlGeneratorRejectsMapperColumnTypeDrift() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{
                    String skuId;
                    int availableQty;
                }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                map GOODS{
                    skuId = int sku_id;
                    availableQty = String available_qty;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        List<String> errors = ddlGenerator.validateMapperDeclarations();
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column skuId declares type int but generated table column skuId has type String")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column availableQty declares type String but generated table column availableQty has type int")));
        assertEquals("skuId", goods.getColumns().get("skuId").getName());
        assertEquals("availableQty", goods.getColumns().get("availableQty").getName());
    }

    @Test
    public void ddlGeneratorRejectsUnsupportedMapperParameters() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{
                    String skuId;
                    String name;
                }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                map GOODS(readModel){
                    skuId = String sku_id;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        List<String> errors = ddlGenerator.validateMapperDeclarations();
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(errors.toString(), errors.contains(
                "Mapper GOODS declares unsupported parameters: readModel."));
        assertEquals("skuId", goods.getColumns().get("skuId").getName());
    }

    @Test
    public void ddlGeneratorRejectsDuplicateMapperSourcesEvenWhenColumnIsMissing() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{
                    String skuId;
                    String name;
                }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                map GOODS{
                    missingColumn = String missing_column;
                    missingColumn = String missing_column_2;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        List<String> errors = ddlGenerator.validateMapperDeclarations();

        assertTrue(errors.toString(), errors.contains(
                "Mapper GOODS declares duplicate source column missingColumn."));
        assertEquals(errors.toString(), 2, errors.stream()
                .filter(error -> error.equals(
                        "Mapper GOODS column missingColumn is not defined in generated table GOODS."))
                .count());
    }

    @Test
    public void ddlGeneratorExpandsNestedObjectPrimaryKeysIndependentOfTableOrder() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Region{ String regionId; }
                Entity Store{ Region region; }
                Entity Product{ Store store; String name; }
                Role Goods{ String skuId; Product product; }
                """, """
                domain catalog;
                tables entities{
                    table PRODUCT(Product){ primarykey(Product.store); }
                    table STORE(Store){ primarykey(Store.region); }
                    table REGION(Region){ primarykey String regionId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                map GOODS{
                    product_store_region_regionid = String product_store_region_id;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable product = ddlGenerator.getTableList().get("PRODUCT");
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(product.getPrimaryKeys().get("store_region_regionid") != null);
        assertTrue(goods.getDbForeigns().get(0).getForeignKeys()
                .get("product_store_region_id") != null);
        assertTrue(goods.getDbForeigns().get(0).getForeignKeys()
                .get("product_store_region_regionid") == null);
        assertTrue(ddlGenerator.validateMapperDeclarations().isEmpty());
    }

    @Test
    public void ddlGeneratorExpandsForeignKeysIndependentOfTableBlockOrder() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Role Goods{ String skuId; Product product; }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                map GOODS{
                    product_productid = String product_id;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertEquals(1, goods.getDbForeigns().size());
        assertTrue(goods.getDbForeigns().get(0).getForeignKeys().get("product_id") != null);
        assertTrue(goods.getDbForeigns().get(0).getForeignKeys().get("product_productid") == null);
        assertTrue(ddlGenerator.validateMapperDeclarations().toString(),
                ddlGenerator.validateMapperDeclarations().isEmpty());
    }

    @Test
    public void ddlGeneratorCreatesIndexesFromAppDdlIndexes() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Role Goods{ String skuId; Product product; }
                Role Inventory{ String inventoryId; Goods goods; }
                """, """
                domain catalog;
                @insideEntity(namingType="snake_case")
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                        index inventoryByGoods(Inventory.goods.skuId);
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable inventory = ddlGenerator.getTableList().get("INVENTORY");

        assertEquals(1, inventory.getIndexes().size());
        assertEquals("idx_INVENTORY_inventoryByGoods", inventory.getIndexes().get(0).getName());
        assertEquals("goods_skuid", inventory.getIndexes().get(0).getColumns().getArrayList().get(0).getName());
    }

    @Test
    public void ddlGeneratorCreatesIndexesFromAppDdlPlatformColumns() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Contract SalesOrder{ String orderNo; String status; }
                """, """
                domain catalog;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey String salesOrderId;
                        notnull String orderNo;
                        index salesOrderByOrderNo(orderNo);
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable salesOrder = ddlGenerator.getTableList().get("SALES_ORDER");

        assertEquals(1, salesOrder.getIndexes().size());
        assertEquals("idx_SALES_ORDER_salesOrderByOrderNo", salesOrder.getIndexes().get(0).getName());
        assertEquals("orderNo", salesOrder.getIndexes().get(0).getColumns().getArrayList().get(0).getName());
    }

    @Test
    public void ddlGeneratorIndexesFollowMappedColumnNames() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Role Goods{ String skuId; Product product; }
                Role Inventory{ String inventoryId; Goods goods; }
                """, """
                domain catalog;
                @insideEntity(namingType="snake_case")
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                        index(Inventory.goods.skuId);
                    }
                }
                map INVENTORY{
                    goods_skuid = String goods_id;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable inventory = ddlGenerator.getTableList().get("INVENTORY");

        assertTrue(ddlGenerator.validateMapperDeclarations().toString(),
                ddlGenerator.validateMapperDeclarations().isEmpty());
        assertEquals("goods_id", inventory.getIndexes().get(0).getColumns().getArrayList().get(0).getName());
        assertTrue(inventory.getColumns().get("goods_id") != null);
        assertTrue(inventory.getColumns().get("goods_skuid") == null);
    }

    @Test
    public void ddlGeneratorInheritsRootDupNamingTypeForRepeatedObjectFields() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Address{ String addressId; }
                Role Delivery{ String deliveryId; Address shipAddress; Address billAddress; }
                """, """
                domain catalog;
                tables entities{
                    table ADDRESS(Address){ primarykey String addressId; }
                }
                @insideEntity(namingType="snake_case", dupNamingType="pascallowercase")
                tables roles{
                    table DELIVERY(Delivery){
                        primarykey String deliveryId;
                        foreignkey(Delivery.shipAddress);
                        foreignkey(Delivery.billAddress);
                    }
                }
                map DELIVERY{
                    shipaddressaddressid = String ship_address_id;
                    billaddressaddressid = String bill_address_id;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable delivery = ddlGenerator.getTableList().get("DELIVERY");

        assertTrue(delivery.getDbForeigns().get(0).getForeignKeys()
                .get("ship_address_id") != null);
        assertTrue(delivery.getDbForeigns().get(0).getForeignKeys()
                .get("shipaddressaddressid") == null);
        assertTrue(delivery.getDbForeigns().get(1).getForeignKeys()
                .get("bill_address_id") != null);
        assertTrue(delivery.getDbForeigns().get(1).getForeignKeys()
                .get("billaddressaddressid") == null);
        assertTrue(ddlGenerator.validateMapperDeclarations().toString(),
                ddlGenerator.validateMapperDeclarations().isEmpty());
    }

    @Test
    public void ddlGeneratorAcceptsMapperForImplicitExpandedObjectColumn() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Role Goods{ String skuId; Product product; }
                """, """
                domain catalog;
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                    }
                }
                map GOODS{
                    productProductId = String product_id;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(goods.getColumns().get("product_id") != null);
        assertTrue(goods.getColumns().get("productProductId") == null);
        assertTrue(ddlGenerator.validateMapperDeclarations().toString(),
                ddlGenerator.validateMapperDeclarations().isEmpty());
    }

    @Test
    public void ddlGeneratorKeepsMybatisColumnNameWhenMapperOnlyOverridesSqlType() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{
                    String skuId;
                    String name;
                }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }
                map GOODS{
                    skuId = String(120);
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(ddlGenerator.validateMapperDeclarations().toString(),
                ddlGenerator.validateMapperDeclarations().isEmpty());
        assertEquals("skuId", goods.getColumns().get("skuId").getName());
        assertEquals(120, goods.getColumns().get("skuId").getPrecision());
        assertEquals("skuId", goods.getMybatisClass().getFields().stream()
                .filter(field -> "skuId".equals(field.getProperty()))
                .findFirst()
                .orElseThrow()
                .getColumn());
    }

    @Test
    public void ddlGeneratorSkipsInvalidAppTablesInsteadOfThrowing() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Role Goods{ String skuId; }
                """, """
                domain catalog;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table MISSING(MissingType){ primarykey String id; }
                }
                map MISSING{
                    id = String missing_id;
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("GOODS") != null);
        assertTrue(ddlGenerator.getTableList().get("MISSING") == null);
        assertTrue(ddlGenerator.validateMapperDeclarations().contains(
                "Mapper table MISSING is not defined."));
    }

    @Test
    public void ddlGeneratorSkipsAppTablesWithArrayPrimaryOrForeignKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Role Goods{ String skuId; Product[] alternatives; }
                """, """
                domain catalog;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey(Goods.alternatives);
                        foreignkey(Goods.alternatives);
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("PRODUCT") != null);
        assertTrue(ddlGenerator.getTableList().get("GOODS") == null);
    }

    @Test
    public void ddlGeneratorSkipsTablesWithUnstableForeignKeyReferences() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Enum GoodsStatus{ OnSale,OffSale }
                Entity Product{ String productId; }
                Entity Store{ String storeId; }
                Role Goods{ String skuId; Product product; }
                Role WrongReference{ String skuId; Product product; }
                Role MissingReference{ String skuId; Product product; }
                Role EnumReference{ String skuId; Product product; }
                """, """
                domain catalog;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table STORE(Store){ primarykey String storeId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product) references Product;
                    }
                    table WRONG_REFERENCE(WrongReference){
                        primarykey String skuId;
                        foreignkey(WrongReference.product) references Store;
                    }
                    table MISSING_REFERENCE(MissingReference){
                        primarykey String skuId;
                        foreignkey(MissingReference.product) references MissingProduct;
                    }
                    table ENUM_REFERENCE(EnumReference){
                        primarykey String skuId;
                        foreignkey(EnumReference.product) references GoodsStatus;
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("GOODS") != null);
        assertTrue(ddlGenerator.getTableList().get("WRONG_REFERENCE") == null);
        assertTrue(ddlGenerator.getTableList().get("MISSING_REFERENCE") == null);
        assertTrue(ddlGenerator.getTableList().get("ENUM_REFERENCE") == null);
    }

    @Test
    public void ddlGeneratorSkipsTablesWithoutResolvablePrimaryKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Entity Store{ String storeId; }
                Role Goods{ String skuId; Store store; }
                """, """
                domain catalog;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table STORE(Store){ }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.store);
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("PRODUCT") != null);
        assertTrue(ddlGenerator.getTableList().get("STORE") == null);
        assertTrue(ddlGenerator.getTableList().get("GOODS") == null);
    }

    @Test
    public void ddlGeneratorSkipsTablesReferencingNonGeneratableForeignKeyTargets() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Store{ String storeId; }
                Role Goods{ String skuId; Store store; }
                Role Shipment{ String shipmentId; }
                """, """
                domain catalog;
                tables roles{
                    table STORE(Store){ primarykey String storeId; }
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.store);
                    }
                    table SHIPMENT(Shipment){ primarykey String shipmentId; }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("STORE") == null);
        assertTrue(ddlGenerator.getTableList().get("GOODS") == null);
        assertTrue(ddlGenerator.getTableList().get("SHIPMENT") != null);
    }

    @Test
    public void ddlGeneratorSkipsTablesWithMixedTargetForeignKeyDeclarations() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Product{ String productId; }
                Entity Store{ String storeId; }
                Role Goods{ String skuId; Product product; Store store; }
                """, """
                domain catalog;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table STORE(Store){ primarykey String storeId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product, Goods.store);
                    }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);

        assertTrue(ddlGenerator.getTableList().get("PRODUCT") != null);
        assertTrue(ddlGenerator.getTableList().get("STORE") != null);
        assertTrue(ddlGenerator.getTableList().get("GOODS") == null);
    }

    @Test
    public void ddlGeneratorSkipsRecursiveImplicitObjectExpansion() throws Exception {
        SppDomain domain = compileDomain("""
                domain catalog;
                Entity Category{ String categoryId; Category parent; }
                Role Goods{ String skuId; Category category; }
                """, """
                domain catalog;
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                }
                """);

        DDLGenerator ddlGenerator = new DDLGenerator(domain, null);
        DbTable goods = ddlGenerator.getTableList().get("GOODS");

        assertTrue(goods == null);
    }

    private SppDomain compileDomain(String sppSource, String appSource) throws Exception {
        File dir = Files.createTempDirectory("spp-ddl-generator-test").toFile();
        File sppFile = write(new File(dir, "catalog.spp"), sppSource);
        File appFile = write(new File(dir, "catalog.app"), appSource);
        SppProject project = new SppProject();
        new SppCompiler(sppFile, project).compile();
        new AppCompiler(appFile, project).compile();
        return project.getDomain("catalog");
    }

    private File write(File file, String source) throws IOException {
        Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
        return file;
    }
}
