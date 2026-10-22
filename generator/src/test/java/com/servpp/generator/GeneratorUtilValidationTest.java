package com.servpp.generator;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GeneratorUtilValidationTest {
    @Test
    public void explicitSingleDomainApplicationUsesDeploymentCatalogs() throws Exception {
        Path source = Files.createTempDirectory("spp-single-application-source");
        Path output = Files.createTempDirectory("spp-single-application-output");
        Files.writeString(source.resolve("ledger.spp"), """
                domain ledger;
                Role Facts { int amount; }
                atomic calculate twice(Facts) { Facts.amount *= 2; }
                Scenario serial preview(Facts) { twice; }
                """);
        Files.writeString(source.resolve("ledger.app"), """
                domain ledger;
                application single { environment develop { deploy ledger; } }
                scope { local { } }
                """);
        GeneratorUtil.execute(source.toFile(), output.resolve("yaml").toFile(),
                output.resolve("java").toFile(), "com.servpp.single");
        Path resources = output.resolve("resources/META-INF/spp");
        assertTrue(Files.exists(resources.resolve("applications.properties")));
        assertTrue(Files.exists(resources.resolve("domains/ledger/services.properties")));
        String catalog = Files.readString(resources.resolve("domains/ledger/services.properties"));
        assertTrue(catalog, catalog.contains("ledger.preview"));
    }

    @Test public void projectInspectionRunsBeforeWritingArtifacts()throws Exception {
        Path source=Files.createTempDirectory("inspection-source"),output=Files.createTempDirectory("inspection-output");Files.writeString(source.resolve("ledger.spp"),"domain ledger; Role Facts { int amount; } atomic calculate same(Facts facts) { facts.amount = facts.amount; } Scenario serial preview(Facts facts) { same(facts); }");
        try{GeneratorUtil.execute(source.toFile(),output.resolve("yaml").toFile(),output.resolve("java").toFile(),"com.servpp.test",null,project->{assertEquals(1,project.getSppDomainMap().size());throw new IllegalStateException("SYNTHETIC_INSPECTION_REJECTION");});fail("Inspection must reject");}
        catch(IllegalStateException expected){assertEquals("SYNTHETIC_INSPECTION_REJECTION",expected.getMessage());}
        assertFalse(Files.exists(output.resolve("yaml")));assertFalse(Files.exists(output.resolve("java")));
    }

    @Test
    public void generatedSpringMethodTemplateFailsOnSerializationErrors() throws Exception {
        try (InputStream stream = GeneratorUtilValidationTest.class.getResourceAsStream(
                "/JavaSpring/methodBody.mustache")) {
            assertTrue("Expected JavaSpring methodBody.mustache test resource.", stream != null);
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(template, template.contains("printStackTrace()"));
            assertTrue(template, template.contains(
                    "throw new IllegalStateException(\"Failed to serialize response for {{operationId}}.\""));
        }
    }

    @Test
    public void generatorFailsWhenSourcePathIsNotDirectory() throws Exception {
        Path sourceFile = Files.createTempFile("spp-generator-source-file", ".spp");
        Path yamlDir = Files.createTempDirectory("spp-generator-source-file-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-source-file-java");

        try {
            GeneratorUtil.execute(sourceFile.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected non-directory source path to fail generation.");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "S++ source directory is not a readable directory"));
        }
    }

    @Test
    public void generatorFailsFastOnSppSyntaxErrors() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-syntax-error-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-syntax-error-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-syntax-error-java");
        Files.writeString(sourceDir.resolve("broken.spp"), """
                domain broken;

                Entity SalesOrder{
                    String orderNo
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected invalid S++ syntax to fail generation.");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "Failed to compile S++ source file"));
            assertTrue(e.getCause().getMessage(), e.getCause().getMessage().contains(
                    "syntax error"));
        }
    }

    @Test
    public void generatorFailsWhenArtifactOutputCannotBeCreated() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-output-failure-source");
        Path yamlFile = Files.createTempFile("spp-generator-output-failure-yaml", ".txt");
        Path javaDir = Files.createTempDirectory("spp-generator-output-failure-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity Customer{
                    String membershipNumber;
                }

                atomic query queryCustomer(Customer customer);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables entities{
                    table CUSTOMER(Customer){
                        primarykey(Customer.membershipNumber);
                    }
                }

                scope{
                    local{
                        queryCustomer;
                    }
                }
                """);

        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce; phy {
                    repository records { adapter objectSnapshot; config default = true; }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlFile.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected artifact output failure to fail generation.");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "Failed to generate S++ artifacts for domain commerce"));
        }
    }

    @Test
    public void generatorKeepsQuantumFieldsOutOfRenderedDdl() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-quantum-ddl-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-quantum-ddl-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-quantum-ddl-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity CreditAccount{
                    String accountNo;
                    quantum double availableCredit;
                    double usedCredit;
                }

                Role OrderAmount{
                    String amountNumber;
                    quantum double occupiedAmount;
                    double payableAmount;
                }

                atomic query queryCredit(CreditAccount account);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }

                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey(CreditAccount.accountNo);
                    }
                }
                tables roles{
                    table ORDER_AMOUNT(OrderAmount){
                        primarykey String amountNumber;
                    }
                }

                scope{
                    local{
                        queryCredit;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    repository records { adapter objectSnapshot; config default = true; }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        String ddl = Files.readString(yamlDir.resolve("sql/ddl.sql"));
        assertTrue(ddl, ddl.contains("usedCredit"));
        assertTrue(ddl, ddl.contains("payableAmount"));
        assertTrue(ddl, !ddl.contains("availableCredit"));
        assertTrue(ddl, !ddl.contains("occupiedAmount"));
    }

    @Test
    public void generatorKeepsPhysicalStoresSeparateInRenderedCatalog() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-physical-stores-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-physical-stores-yaml");
        Path genRoot = Files.createTempDirectory("spp-generator-physical-stores-gen");
        Path javaDir = genRoot.resolve("java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Reference Customer{ String membershipNumber; }

                Entity CreditAccount{
                    Customer customer;
                    quantum double availableCredit;
                    double usedCredit;
                }

                Role OrderAmount{
                    quantum double occupiedAmount;
                    double payableAmount;
                }

                Contract SalesOrder{
                    String orderNo;
                    CreditAccount account;
                    OrderAmount amount;
                }

                atomic query queryCreditAccount(CreditAccount account);
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update openSalesOrder(SalesOrder order);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }

                scope{
                    local (in baseIn, out baseOut){
                        repository(argument(0, CreditAccount.customer.membershipNumber), account)
                            queryCreditAccount;
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.customer.membershipNumber))
                            reserveCredit;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo))
                            openSalesOrder;
                    }
                }

                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        account.usedCredit += amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                    openSalesOrder{
                        order.orderNo = order.orderNo;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-local;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        quantumStore globalQuantum;
                    }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-global-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                    repository objectStore{
                        adapter objectSnapshot;
                        config default = true;
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-object;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config table = "spp_object_snapshot";
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                genRoot.resolve("resources/META-INF/spp/physical.properties"))) {
            properties.load(input);
        }
        assertEquals("jdbc:h2:mem:spp-commerce-local;DB_CLOSE_DELAY=-1",
                properties.getProperty("backend.localJdbc.config.datasourceUrl"));
        assertEquals("globalQuantum",
                properties.getProperty("backend.localJdbc.quantumStore"));
        assertFalse(properties.containsKey("backend.localJdbc.config.quantumStore"));
        assertEquals("jdbc:h2:mem:spp-commerce-global-quantum;DB_CLOSE_DELAY=-1",
                properties.getProperty("quantumStore.globalQuantum.config.datasourceUrl"));
        assertEquals("jdbc:h2:mem:spp-commerce-object;DB_CLOSE_DELAY=-1",
                properties.getProperty("repository.objectStore.config.datasourceUrl"));
    }

    @Test
    public void generatorWritesStructuredAppSemanticMigrationPlans() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-app-migration-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-app-migration-yaml");
        Path genRoot = Files.createTempDirectory("spp-generator-app-migration-gen");
        Path javaDir = genRoot.resolve("java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Reference Customer{
                    String membershipNumber;
                }

                Entity Product{
                    String productCode;
                    String name;
                }

                Role Goods{
                    String sku;
                }
                take Product as Goods;

                atomic query queryCustomer(Customer customer);
                atomic query queryProduct(Product product);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables entities{
                    table PRODUCT(Product){
                        primarykey(Product.productCode);
                    }
                }

                scope{
                    remote{
                        queryCustomer;
                    }
                    local{
                        repository(argument(0, Product.productCode), product) queryProduct;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    repository records { adapter objectSnapshot; config default = true; }
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config objectType = "Customer";
                        config objectIdPath = "membershipNumber";
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                genRoot.resolve("resources/META-INF/spp/app-semantic-migration.properties"))) {
            properties.load(input);
        }
        assertEquals("6", properties.getProperty("plan.count"));
        assertEquals("2", properties.getProperty("summary.repositoryQueryPlanCount"));
        assertEquals("1", properties.getProperty("summary.repositoryReplacementReadyCount"));
        assertEquals("1", properties.getProperty("summary.missingRepositoryReplacementReadyCount"));
        assertEquals("EXPLICIT_REPOSITORY_ARGUMENT,APP_DDL_INFERRED",
                properties.getProperty("summary.repositoryReplacementSources"));
        assertEquals("findProduct", properties.getProperty("summary.repositoryReplacementReadyServiceNames"));
        assertEquals("queryProduct",
                properties.getProperty("summary.missingRepositoryReplacementReadyServiceNames"));
        String productPrimaryKeyPlan = appSemanticMigrationPlanPrefix(properties,
                "OBJECT_BACKED_PRIMARY_KEY", null, "PRODUCT");
        assertEquals("APP_DDL_TABLE", properties.getProperty(productPrimaryKeyPlan + "targetKind"));
        assertEquals("Product", properties.getProperty(productPrimaryKeyPlan + "objectType"));
        assertEquals("productCode", properties.getProperty(productPrimaryKeyPlan + "paths"));
        String productParticipantPlan = appSemanticMigrationPlanPrefix(properties,
                "SERVICE_NON_ROLE_PARTICIPANT", "queryProduct", null);
        assertEquals("SERVICE_PARTICIPANT", properties.getProperty(productParticipantPlan + "targetKind"));
        assertEquals("queryProduct.product:Product", properties.getProperty(productParticipantPlan + "paths"));
        assertTrue(properties.getProperty(productParticipantPlan + "recommendation"),
                properties.getProperty(productParticipantPlan + "recommendation")
                        .contains("Accept target Role Goods"));
        String customerParticipantPlan = appSemanticMigrationPlanPrefix(properties,
                "SERVICE_NON_ROLE_PARTICIPANT", "queryCustomer", null);
        assertEquals("SERVICE_PARTICIPANT", properties.getProperty(customerParticipantPlan + "targetKind"));
        assertEquals("queryCustomer.customer:Customer", properties.getProperty(customerParticipantPlan + "paths"));
        String repositoryEscapePlan = appSemanticMigrationPlanPrefix(properties,
                "REPOSITORY_ESCAPE_HATCH", "queryProduct", null);
        assertEquals("Product", properties.getProperty(repositoryEscapePlan + "objectType"));
        assertEquals("productCode", properties.getProperty(repositoryEscapePlan + "paths"));
        assertEquals("productCode", properties.getProperty(repositoryEscapePlan + "appDdlObjectKeyPaths"));
        assertEquals("productCode", properties.getProperty(repositoryEscapePlan + "objectKeyColumns"));
        assertEquals("true", properties.getProperty(repositoryEscapePlan + "appDdlObjectKeyPushdown"));
        String remoteReferencePlan = appSemanticMigrationPlanPrefix(properties,
                "REMOTE_REFERENCE_ADAPTER", "queryCustomer", null);
        assertEquals("objectRepositoryRemote", properties.getProperty(remoteReferencePlan + "adapterName"));
        assertEquals("OBJECT_REPOSITORY_REFERENCE", properties.getProperty(remoteReferencePlan + "adapterRole"));
        assertEquals("true", properties.getProperty(remoteReferencePlan + "referenceImplementation"));
        assertEquals("false", properties.getProperty(remoteReferencePlan + "externalImplementation"));
        assertTrue(properties.getProperty("warning.0").contains("PRODUCT primary key paths productCode"));
    }

    private String appSemanticMigrationPlanPrefix(Properties properties, String migrationType,
                                                  String serviceName, String tableName) {
        int count = Integer.parseInt(properties.getProperty("plan.count"));
        for (int index = 0; index < count; index++) {
            String prefix = "plan." + index + ".";
            if (!migrationType.equals(properties.getProperty(prefix + "migrationType"))) {
                continue;
            }
            if (serviceName != null && !serviceName.equals(properties.getProperty(prefix + "serviceName"))) {
                continue;
            }
            if (tableName != null && !tableName.equals(properties.getProperty(prefix + "tableName"))) {
                continue;
            }
            return prefix;
        }
        throw new AssertionError("Missing APP semantic migration plan " + migrationType
                + " service=" + serviceName + " table=" + tableName);
    }

    @Test
    public void generatorRejectsDuplicateAppServiceImplementations() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-validation-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-validation-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-validation-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Role Inventory{
                    int availableQty;
                    int requestedQty;
                }

                atomic update reserveInventory(Inventory inventory);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    local (in baseIn, out baseOut){
                        at resource(inventory) snapshot(argument(0, Inventory.availableQty)) reserveInventory;
                    }
                }

                services{
                    reserveInventory{
                        inventory.availableQty -= inventory.requestedQty;
                    }
                    reserveInventory{
                        inventory.availableQty = 0;
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected duplicate APP service implementation to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "APP service implementation reserveInventory is declared multiple times."));
        }
    }

    @Test
    public void generatorAcceptsLocalTransactionalServiceWithoutIdempotencyHeader() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-idempotency-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-idempotency-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-idempotency-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity SalesOrder{
                    String orderNo;
                    String status;
                }

                atomic update openSalesOrder(SalesOrder order);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    local (in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }

                services{
                    openSalesOrder{
                        order.status = "Created";
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");
        String implementation=Files.readString(javaDir.resolve(
                "com/servpp/openapi/commerce/service/SimpleOpenSalesOrder.java"));
        assertTrue(implementation, implementation.contains("setStatus"));
        assertFalse(implementation, implementation.contains("getGlobalSerialNumber()"));
        assertTrue(implementation, implementation.matches("(?s).*boolean isLocalScope\\(\\)\\s*\\{\\s*return true;.*"));
        assertTrue(implementation, implementation.matches("(?s).*boolean requiresIdempotencyKey\\(\\)\\s*\\{\\s*return false;.*"));
    }

    @Test
    public void generatorRejectsQueryServiceWritingBusinessObjects() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-query-write-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-query-write-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-query-write-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Role Inventory{
                    String sku;
                    int availableQty;
                }

                atomic query queryInventory(Inventory inventory);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    local (in baseIn, out baseOut){
                        queryInventory;
                    }
                }

                services{
                    queryInventory{
                        inventory.availableQty = 100;
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected query service implementation write to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "APP query service implementation queryInventory cannot write business objects"));
        }
    }

    @Test
    public void generatorRejectsCheckServiceWritingBusinessObjects() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-check-write-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-check-write-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-check-write-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Role Inventory{
                    int availableQty;
                    int requestedQty;
                }

                atomic check checkInventory(Inventory inventory);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    local (in baseIn, out baseOut){
                        checkInventory;
                    }
                }

                services{
                    checkInventory{
                        inventory.availableQty -= inventory.requestedQty;
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected check service implementation write to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "APP check service implementation checkInventory cannot write business objects"));
        }
    }

    @Test
    public void generatorRejectsRemoteScopeServiceImplementation() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-remote-implementation-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-remote-implementation-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-remote-implementation-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity OrderAmount{
                    double payableAmount;
                }

                atomic update calculateAmount(OrderAmount amount);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    remote (in baseIn, out baseOut){
                        calculateAmount;
                    }
                }

                services{
                    calculateAmount{
                        amount.payableAmount = 10.0;
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected remote scope service implementation to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "APP service implementation calculateAmount must bind a local scope service."));
        }
    }

    @Test
    public void generatorRejectsMapperColumnTypeDrift() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-mapper-type-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-mapper-type-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-mapper-type-java");
        Files.writeString(sourceDir.resolve("catalog.spp"), """
                domain catalog;

                Role Goods{
                    String skuId;
                    int availableQty;
                }
                """);
        Files.writeString(sourceDir.resolve("catalog.app"), """
                domain catalog;

                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                    }
                }

                map GOODS{
                    skuId = String sku_id;
                    availableQty = String available_qty;
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected mapper column type drift to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "Mapper GOODS column availableQty declares type String but APP table column availableQty has type int."));
        }
    }

    @Test
    public void generatorAcceptsRoleOnlyBusinessModelWithTakeParticipantIdentity() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-role-only-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-role-only-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-role-only-java");
        Path genRoot = javaDir.getParent();
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity Product{ String productCode; }
                Entity Store{ String name; }

                Role Goods{
                    String sku;
                    double salePrice;
                }
                take Product as Goods;

                Role Inventory{
                    quantum int availableQty;
                    int safeQty;
                }
                take Store, Goods as Inventory;

                Role SalesItem{
                    int requestedQty;
                    quantum int orderQty;
                    double unitPrice;
                    double lineAmount;
                }
                take Goods as SalesItem;
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables entities{
                    table PRODUCT(Product){ primarykey String productCode; }
                    table STORE(Store){ primarykey String name; }
                }

                tables roles{
                    table GOODS(Goods){
                        primarykey String sku;
                        notnull String product_productcode;
                    }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_sku;
                    }
                    table SALES_ITEM(SalesItem){
                        primarykey String salesItemId;
                        notnull String goods_sku;
                    }
                }

                quantumStore globalQuantum{
                    attribute Inventory.availableQty;
                    attribute SalesItem.orderQty;
                }

                phy{
                    config strictRoleOnlyBusinessModel = true;
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:role-only-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                genRoot.resolve("resources/META-INF/spp/role-participants.properties"))) {
            properties.load(input);
        }
        assertEquals("3", properties.getProperty("roleParticipant.count"));
        String goodsPlan = roleParticipantPlanPrefix(properties, "Goods");
        assertEquals("Product", properties.getProperty(goodsPlan + "performerTypes"));
        assertEquals("product_productcode", properties.getProperty(goodsPlan + "platformColumns"));
        assertEquals("", properties.getProperty(goodsPlan + "roleKeyPaths"));
        assertEquals("product.productCode", properties.getProperty(goodsPlan + "compatRoleKeyPaths"));
        assertEquals("", properties.getProperty(goodsPlan + "roleSourceKeyPaths"));
        assertEquals("true", properties.getProperty(goodsPlan + "performerKeyComplete"));
        assertEquals("true", properties.getProperty(goodsPlan + "roleKeyComplete"));
        assertEquals("true", properties.getProperty(goodsPlan + "complete"));
        String inventoryPlan = roleParticipantPlanPrefix(properties, "Inventory");
        assertEquals("Store,Goods", properties.getProperty(inventoryPlan + "performerTypes"));
        assertEquals("true", properties.getProperty(inventoryPlan + "jointPerformer"));
        assertEquals("store_name,goods_sku", properties.getProperty(inventoryPlan + "platformColumns"));
        assertEquals("", properties.getProperty(inventoryPlan + "roleKeyPaths"));
        assertEquals("store.name,goods.sku", properties.getProperty(inventoryPlan + "compatRoleKeyPaths"));
        assertEquals("", properties.getProperty(inventoryPlan + "roleSourceKeyPaths"));
        assertEquals("true", properties.getProperty(inventoryPlan + "roleKeyComplete"));
        String salesItemPlan = roleParticipantPlanPrefix(properties, "SalesItem");
        assertEquals("Goods", properties.getProperty(salesItemPlan + "performerTypes"));
        assertEquals("goods_sku", properties.getProperty(salesItemPlan + "platformColumns"));
        assertEquals("", properties.getProperty(salesItemPlan + "roleKeyPaths"));
        assertEquals("goods.sku", properties.getProperty(salesItemPlan + "compatRoleKeyPaths"));
        assertEquals("", properties.getProperty(salesItemPlan + "roleSourceKeyPaths"));
        assertEquals("true", properties.getProperty(salesItemPlan + "roleKeyComplete"));

        Properties migrationProperties = new Properties();
        try (InputStream input = Files.newInputStream(
                genRoot.resolve("resources/META-INF/spp/app-semantic-migration.properties"))) {
            migrationProperties.load(input);
        }
        for (int index = 0; index < Integer.parseInt(migrationProperties.getProperty("plan.count")); index++) {
            String migrationType = migrationProperties.getProperty("plan." + index + ".migrationType");
            assertFalse("ROLE_PERFORMER_FIELD".equals(migrationType));
            assertFalse("ROLE_OBJECT_FIELD".equals(migrationType));
        }
    }

    @Test
    public void generatorKeepsAlternativeRolePerformersAsSeparateParticipantPlans() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-role-polymorphic-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-role-polymorphic-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-role-polymorphic-java");
        Path genRoot = javaDir.getParent();
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity Product{ String productCode; }
                Entity Bundle{ String bundleCode; }

                Role Goods{
                    String sku;
                    double salePrice;
                }
                take Product as Goods;
                take Bundle as Goods;
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables entities{
                    table PRODUCT(Product){ primarykey String productCode; }
                    table BUNDLE(Bundle){ primarykey String bundleCode; }
                }

                tables roles{
                    table GOODS(Goods){
                        primarykey String sku;
                        null String product_productcode;
                        null String bundle_bundlecode;
                    }
                }

                phy{
                    config strictRoleOnlyBusinessModel = true;
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                genRoot.resolve("resources/META-INF/spp/role-participants.properties"))) {
            properties.load(input);
        }
        List<String> goodsPlans = roleParticipantPlanPrefixes(properties, "Goods");
        assertEquals(2, goodsPlans.size());
        assertEquals(java.util.Set.of("Product","Bundle"), goodsPlans.stream()
                .map(prefix -> properties.getProperty(prefix + "performerTypes")).collect(java.util.stream.Collectors.toSet()));
        for (String prefix : goodsPlans) assertEquals("false",properties.getProperty(prefix + "jointPerformer"));
    }

    @Test
    public void generatorAcceptsIntrinsicRoleFieldsUnderStrictRoleOnlyBusinessModel() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-role-object-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-role-object-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-role-object-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity Product{ business("Product registration") String productId; }

                Role Goods{
                    business("Goods number") String skuId;
                    Product product;
                    double salePrice;
                }
                take Product as Goods;
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }

                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        notnull String product_productid;
                    }
                }

                phy{
                    config strictRoleOnlyBusinessModel = true;
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(),yamlDir.toFile(),javaDir.toFile(),"com.servpp.openapi");
        assertTrue(Files.exists(yamlDir.resolve("commerce.spp.yaml")));
    }

    @Test
    public void generatorAcceptsIntrinsicRolesWithoutRepeatedTake() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-role-without-take-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-role-without-take-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-role-without-take-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Reference Customer{ business("Customer registration") String customerId; }

                Role CreditAccount{
                    double creditLimit;
                }
                take Customer as CreditAccount;

                Role OrderAmount{
                    Customer payer;
                    double payableAmount;
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables roles{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String customer_customerid;
                    }
                    table ORDER_AMOUNT(OrderAmount){
                        primarykey String order_amount_id;
                    }
                }

                phy{
                    config strictRoleOnlyBusinessModel = true;
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(),yamlDir.toFile(),javaDir.toFile(),"com.servpp.openapi");
        assertTrue(Files.exists(yamlDir.resolve("commerce.spp.yaml")));
    }

    @Test
    public void generatorDoesNotReportIntrinsicRolesAsMissingTake() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-role-without-take-plan-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-role-without-take-plan-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-role-without-take-plan-java");
        Path genRoot = javaDir.getParent();
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Reference Customer{ String membershipNumber; }

                Role CreditAccount{
                    double creditLimit;
                }
                take Customer as CreditAccount;

                Role OrderAmount{
                    Customer payer;
                    double payableAmount;
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables roles{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String customer_membershipnumber;
                    }
                    table ORDER_AMOUNT(OrderAmount){
                        primarykey String order_amount_id;
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        Properties migrationProperties = new Properties();
        try (InputStream input = Files.newInputStream(
                genRoot.resolve("resources/META-INF/spp/app-semantic-migration.properties"))) {
            migrationProperties.load(input);
        }
        boolean found = false;
        for (int index = 0; index < Integer.parseInt(migrationProperties.getProperty("plan.count")); index++) {
            String prefix = "plan." + index + ".";
            if (!"ROLE_WITHOUT_PERFORMER".equals(migrationProperties.getProperty(prefix + "migrationType"))) {
                continue;
            }
            found = true;
            assertEquals("OrderAmount", migrationProperties.getProperty(prefix + "objectType"));
            assertEquals("OrderAmount", migrationProperties.getProperty(prefix + "paths"));
            assertEquals("declareRoleTakePerformer",
                    migrationProperties.getProperty(prefix + "migrationAction"));
        }
        assertFalse("Intrinsic participants already establish role composition.", found);
    }

    @Test
    public void generatorRejectsRoleOverloadFieldTypeMismatchUnderStrictRoleOnlyBusinessModel() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-role-overload-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-role-overload-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-role-overload-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity Product{
                    String productId;
                    String title;
                }

                Role Goods{
                    String skuId;
                    int title;
                    double salePrice;
                }
                take Product as Goods;
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }

                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        notnull String product_productid;
                    }
                }

                phy{
                    config strictRoleOnlyBusinessModel = true;
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected role overload type mismatch to fail strict role-only business model validation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "Role Goods field title overloads performer Product.title"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "changes type from String to int"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "actions=alignRoleOverloadFieldType"));
        }
    }

    @Test
    public void generatorRejectsHttpJsonRemoteAdapterWithoutEndpoint() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-http-json-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-http-json-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-http-json-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity SalesOrder{
                    String orderNo;
                }

                atomic query queryRemoteOrder(SalesOrder order);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    remote (in baseIn, out baseOut){
                        queryRemoteOrder;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    remote queryRemoteOrder{
                        adapter httpJson;
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected httpJson remote adapter without endpoint to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "PHY remote adapter queryRemoteOrder httpJson config endpoint must not be blank."));
        }
    }

    @Test
    public void generatorRejectsObjectSnapshotRepositoryBlankJdbcConfig() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-object-snapshot-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-object-snapshot-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-object-snapshot-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity SalesOrder{
                    String orderNo;
                }

                atomic query queryOrder(SalesOrder order);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                scope{
                    local{
                        repository(argument(0, SalesOrder.orderNo), order) queryOrder;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    repository objectStore{
                        adapter objectSnapshot;
                        config datasourceUrl = "";
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected objectSnapshot repository adapter blank config to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "PHY repository adapter objectStore objectSnapshot config datasourceUrl must not be blank when declared."));
        }
    }

    @Test
    public void generatorRejectsNonPhysicalDeclarationsInPhysicalFile() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-phy-boundary-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-phy-boundary-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-phy-boundary-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity SalesOrder{
                    String orderNo;
                    String status;
                }

                atomic update openSalesOrder(SalesOrder order);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    local (in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                header deploymentOnly{
                    String forbidden;
                }

                phy{
                    backend localJdbc;
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected non-physical declarations in .phy to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "PHY file commerce.phy can only declare phy blocks."));
        }
    }

    @Test
    public void generatorRejectsAppQuantumStoreWithoutPhysicalAdapter() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-quantum-phy-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-quantum-phy-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-quantum-phy-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity CreditAccount{
                    quantum double availableCredit;
                }

                atomic query queryCredit(CreditAccount account);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }

                scope{
                    local{
                        queryCredit;
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected APP quantum store without PHY adapter to fail physical catalog generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "S++ physical runtime catalog validation failed"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "PHY quantum store adapter globalQuantum"
                            + " must define the physical implementation for APP quantum store globalQuantum"
                            + " before generating physical runtime catalog."));
        }
    }

    @Test
    public void generatorRejectsUnsupportedQuantumAccessContractModes() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-quantum-contract-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-quantum-contract-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-quantum-contract-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity CreditAccount{
                    quantum double availableCredit;
                }

                atomic query queryCredit(CreditAccount account);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }

                scope{
                    local{
                        queryCredit;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config implementationStrategy = "UNKNOWN_STRATEGY";
                        config accessMode = "UNKNOWN_ACCESS";
                        config idempotencyMode = "UNKNOWN_IDEMPOTENCY";
                        config reversalMode = "UNKNOWN_REVERSAL";
                        config datasourceUrl = "jdbc:h2:mem:commerce-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected unsupported quantum access contract modes to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "config implementationStrategy declares unsupported strategy UNKNOWN_STRATEGY"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "config accessMode declares unsupported mode UNKNOWN_ACCESS"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "config idempotencyMode declares unsupported mode UNKNOWN_IDEMPOTENCY"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "config reversalMode declares unsupported mode UNKNOWN_REVERSAL"));
        }
    }

    @Test
    public void generatorRejectsQuantumAccessContractAdapterMismatch() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-quantum-contract-mismatch-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-quantum-contract-mismatch-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-quantum-contract-mismatch-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity CreditAccount{
                    quantum double availableCredit;
                }

                atomic query queryCredit(CreditAccount account);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }

                scope{
                    local{
                        queryCredit;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    quantumStore globalQuantum{
                        adapter quantumService;
                        config implementationStrategy = "GLOBAL_QUANTUM_STORE";
                        config accessMode = "GLOBAL_STORE_ADAPTER";
                        config idempotencyMode = "FRAMEWORK_IDEMPOTENCY";
                        config reversalMode = "SERVICE_REVERSAL";
                        config namespace = "commerce";
                        config endpoint = "quantum.example.test";
                        quantum "CreditAccount.availableCredit" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1.7976931348623157E308";
                            scale 0;
                        }
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected mismatched quantum access contract to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "config implementationStrategy must be QUANTUM_SERVICE when adapter is quantumService"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "config accessMode must be QUANTUM_SERVICE_ADAPTER when adapter is quantumService"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "config idempotencyMode must be SERVICE_IDEMPOTENCY when adapter is quantumService"));
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "quantumService config endpoint must be an absolute http or https URI"));
        }
    }

    @Test
    public void generatorRejectsMixedQuantumImplementationStrategiesInOneProfile() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-quantum-strategy-mix-source");
        Path yamlDir = Files.createTempDirectory("spp-generator-quantum-strategy-mix-yaml");
        Path javaDir = Files.createTempDirectory("spp-generator-quantum-strategy-mix-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity CreditAccount{
                    quantum double availableCredit;
                }

                Entity Inventory{
                    quantum double availableQty;
                }

                atomic query queryCredit(CreditAccount account);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                quantumStore creditQuantum{
                    attribute CreditAccount.availableCredit;
                }

                quantumStore inventoryQuantum{
                    attribute Inventory.availableQty;
                }

                scope{
                    local{
                        queryCredit;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    quantumStore creditQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:credit-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                    quantumStore inventoryQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                    }
                }
                """);

        try {
            GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                    "com.servpp.openapi");
            fail("Expected mixed quantum implementation strategies to fail generation.");
        } catch (MojoFailureException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "PHY quantum implementation strategies are mutually exclusive within one physical profile"));
            assertTrue(e.getMessage(), e.getMessage().contains("GLOBAL_QUANTUM_STORE[creditQuantum=jdbcQuantum]"));
            assertTrue(e.getMessage(), e.getMessage().contains("QUANTUM_SERVICE[inventoryQuantum=quantumService]"));
        }
    }

    @Test
    public void physicalProfileSelectsOnlySharedAndMatchingProfileFiles() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-generator-physical-profile-source");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity CreditAccount{
                    quantum double availableCredit;
                }

                atomic query checkCredit(CreditAccount account);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }

                scope{
                    local{
                        checkCredit;
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.phy"), """
                domain commerce;

                phy{
                    config strictRoleOnlyBusinessModel = false;
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.local.phy"), """
                domain commerce;

                phy{
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config implementationStrategy = "GLOBAL_QUANTUM_STORE";
                        config accessMode = "GLOBAL_STORE_ADAPTER";
                        config idempotencyMode = "FRAMEWORK_IDEMPOTENCY";
                        config reversalMode = "NEGATIVE_DELTA";
                        config datasourceUrl = "jdbc:h2:mem:commerce-local-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);
        Files.writeString(sourceDir.resolve("commerce.quantum.phy"), """
                domain commerce;

                phy{
                    quantumStore globalQuantum{
                        adapter quantumService;
                        config implementationStrategy = "QUANTUM_SERVICE";
                        config accessMode = "QUANTUM_SERVICE_ADAPTER";
                        config idempotencyMode = "SERVICE_IDEMPOTENCY";
                        config reversalMode = "SERVICE_REVERSAL";
                        config serviceProtocol = "LOGICAL_QSERVICE";
                        config namespace = "commerce";
                        config endpoint = "https://quantum.example.test";
                        config timeoutMillis = "3000";
                        quantum "CreditAccount.availableCredit" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1.7976931348623157E308";
                            scale 0;
                        }
                    }
                }
                """);

        Properties localProperties = generatePhysicalPropertiesForProfile(sourceDir, "local");
        assertEquals("local", localProperties.getProperty("physicalProfile"));
        assertEquals("true", localProperties.getProperty("physicalProfileExplicit"));
        assertEquals("commerce.phy,commerce.local.phy", localProperties.getProperty("physicalSourceFiles"));
        assertEquals("jdbcQuantum", localProperties.getProperty("quantumStore.globalQuantum.adapter"));
        assertEquals("GLOBAL_QUANTUM_STORE",
                localProperties.getProperty("quantumStore.globalQuantum.config.implementationStrategy"));
        assertEquals("jdbc:h2:mem:commerce-local-quantum;DB_CLOSE_DELAY=-1",
                localProperties.getProperty("quantumStore.globalQuantum.config.datasourceUrl"));

        Properties quantumProperties = generatePhysicalPropertiesForProfile(sourceDir, "quantum");
        assertEquals("quantum", quantumProperties.getProperty("physicalProfile"));
        assertEquals("true", quantumProperties.getProperty("physicalProfileExplicit"));
        assertEquals("commerce.phy,commerce.quantum.phy", quantumProperties.getProperty("physicalSourceFiles"));
        assertEquals("quantumService", quantumProperties.getProperty("quantumStore.globalQuantum.adapter"));
        assertEquals("QUANTUM_SERVICE",
                quantumProperties.getProperty("quantumStore.globalQuantum.config.implementationStrategy"));
        assertEquals("https://quantum.example.test",
                quantumProperties.getProperty("quantumStore.globalQuantum.config.endpoint"));
        assertEquals("commerce",
                quantumProperties.getProperty("quantumStore.globalQuantum.config.namespace"));
        assertEquals("SERVICE_REVERSAL",
                quantumProperties.getProperty("quantumStore.globalQuantum.config.reversalMode"));
        assertEquals("1",
                quantumProperties.getProperty("quantumStore.globalQuantum.definitionCount"));
        assertFalse(quantumProperties.containsKey("quantumStore.globalQuantum.config.datasourceUrl"));

        // The same project must compile recursively with profile filtering and accurate source paths.
        Path domainDirectory = Files.createDirectories(sourceDir.resolve("domains/commerce"));
        try (var files = Files.list(sourceDir)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                Files.move(file, domainDirectory.resolve(file.getFileName()));
        }
        Properties nestedLocal = generatePhysicalPropertiesForProfile(sourceDir, "local");
        assertEquals("domains/commerce/commerce.phy,domains/commerce/commerce.local.phy",
                nestedLocal.getProperty("physicalSourceFiles"));
        assertEquals(localProperties.getProperty("quantumStore.globalQuantum.config.datasourceUrl"),
                nestedLocal.getProperty("quantumStore.globalQuantum.config.datasourceUrl"));
        Properties nestedQuantum = generatePhysicalPropertiesForProfile(sourceDir, "quantum");
        assertEquals("domains/commerce/commerce.phy,domains/commerce/commerce.quantum.phy",
                nestedQuantum.getProperty("physicalSourceFiles"));
        assertEquals("quantumService", nestedQuantum.getProperty("quantumStore.globalQuantum.adapter"));
        try {
            generatePhysicalPropertiesForProfile(sourceDir, "missing");
            fail("Unknown nested physical profile must fail");
        } catch (MojoFailureException expected) {
            assertTrue(expected.getMessage().contains("does not match any .phy file"));
        }
    }

    private Properties generatePhysicalPropertiesForProfile(Path sourceDir, String physicalProfile) throws Exception {
        Path outputRoot = Files.createTempDirectory("spp-generator-physical-profile-" + physicalProfile);
        Path yamlDir = outputRoot.resolve("yaml");
        Path javaDir = outputRoot.resolve("java");
        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi", physicalProfile);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(outputRoot.resolve(
                "resources/META-INF/spp/physical.properties"))) {
            properties.load(input);
        }
        return properties;
    }

    private String roleParticipantPlanPrefix(Properties properties, String roleType) {
        List<String> prefixes = roleParticipantPlanPrefixes(properties, roleType);
        if (!prefixes.isEmpty()) {
            return prefixes.get(0);
        }
        fail("Expected role participant plan for " + roleType);
        return "";
    }

    private List<String> roleParticipantPlanPrefixes(Properties properties, String roleType) {
        int count = Integer.parseInt(properties.getProperty("roleParticipant.count", "0"));
        List<String> prefixes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "roleParticipant." + index + ".";
            if (roleType.equals(properties.getProperty(prefix + "roleType"))) {
                prefixes.add(prefix);
            }
        }
        return List.copyOf(prefixes);
    }
}
