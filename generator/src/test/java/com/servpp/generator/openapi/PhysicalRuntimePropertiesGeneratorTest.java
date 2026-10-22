package com.servpp.generator.openapi;

import com.servpp.lang.AppCompiler;
import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PhysicalRuntimePropertiesGeneratorTest {
    @Test
    public void physicalRuntimePropertiesRoundTripPhyLayerBindingsAndAdapters() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;

                Contract SalesOrder{
                    String orderNo;
                    quantum double amount;
                }

                atomic query queryCustomer(SalesOrder order);
                atomic update openSalesOrder(SalesOrder order);
                Scenario serial submitOrder(SalesOrder order){
                    transaction{ openSalesOrder(order); }
                }
                """, """
                domain commerce;

                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                quantumStore globalQuantum{
                    attribute SalesOrder.amount;
                }

                scope{
                    local (in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                    remote (in baseIn, out baseOut){
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;

                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyBusinessModel = false;
                    backend creditTx{
                        adapter seata;
                        config applicationId = " credit-commerce";
                        config transactionServiceGroup = "credit-commerce-tx-group";
                        config transactionBridgeClass = "com.servpp.creditcommerce.SeataBridge";
                        at salesOrder;
                        saga submitOrder;
                    }
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/spp";
                        config compensationMaxAttempts = 3;
                        config compensationBackoffMillis = 50;
                        config compensationManualInterventionRequired = true;
                    }
                    repository objectStore{
                        adapter objectSnapshot;
                        config table = "spp_object_snapshot";
                    }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config implementationStrategy = "GLOBAL_QUANTUM_STORE";
                        config accessMode = "GLOBAL_STORE_ADAPTER";
                        config idempotencyMode = "FRAMEWORK_IDEMPOTENCY";
                        config reversalMode = "NEGATIVE_DELTA";
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                        config decimalStorage = "text";
                    }
                }
                """);
        Path genRoot = Files.createTempDirectory("spp-physical-properties")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createPhysicalRuntimeProperties(domain, genRoot.toFile());

        Path physicalProperties = genRoot.getParent()
                .resolve("resources/META-INF/spp/physical.properties");
        String propertiesText = Files.readString(physicalProperties);
        assertTrue(propertiesText, propertiesText.contains("backends=creditTx"));
        assertTrue(propertiesText, propertiesText.contains("config.strictAppMigration=true"));
        assertTrue(propertiesText, propertiesText.contains("config.strictRoleOnlyBusinessModel=false"));
        assertTrue(propertiesText, propertiesText.contains("backend.creditTx.adapter=seata"));
        assertTrue(propertiesText, propertiesText.contains(
                "backend.creditTx.bindings=at:salesOrder,saga:submitOrder"));
        assertTrue(propertiesText, propertiesText.contains(
                "backend.creditTx.config.applicationId=\\ credit-commerce"));
        assertTrue(propertiesText, propertiesText.contains(
                "backend.creditTx.config.transactionBridgeClass=com.servpp.creditcommerce.SeataBridge"));
        assertTrue(propertiesText, propertiesText.contains("remoteServices=queryCustomer"));
        assertTrue(propertiesText, propertiesText.contains("remote.queryCustomer.adapter=httpJson"));
        assertTrue(propertiesText, propertiesText.contains("remote.queryCustomer.config.compensationMaxAttempts=3"));
        assertTrue(propertiesText, propertiesText.contains("repositories=objectStore"));
        assertTrue(propertiesText, propertiesText.contains("repository.objectStore.adapter=objectSnapshot"));
        assertTrue(propertiesText, propertiesText.contains("quantumStores=globalQuantum"));
        assertTrue(propertiesText, propertiesText.contains("quantumStore.globalQuantum.config.decimalStorage=text"));
        assertTrue(propertiesText, propertiesText.contains("quantumStore.globalQuantum.attributes=SalesOrder.amount"));
        assertTrue(propertiesText, propertiesText.contains("quantumStore.globalQuantum.adapter=jdbcQuantum"));
        assertTrue(propertiesText, propertiesText.contains(
                "quantumStore.globalQuantum.config.implementationStrategy=GLOBAL_QUANTUM_STORE"));
        assertTrue(propertiesText, propertiesText.contains(
                "quantumStore.globalQuantum.config.accessMode=GLOBAL_STORE_ADAPTER"));
        assertTrue(propertiesText, propertiesText.contains(
                "quantumStore.globalQuantum.config.idempotencyMode=FRAMEWORK_IDEMPOTENCY"));
        assertTrue(propertiesText, propertiesText.contains(
                "quantumStore.globalQuantum.config.reversalMode=NEGATIVE_DELTA"));
        assertTrue(propertiesText, propertiesText.contains(
                "quantumStore.globalQuantum.config.valueTable=spp_quantum_attribute_value"));

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(physicalProperties)) {
            properties.load(input);
        }
        assertEquals("creditTx", properties.getProperty("backends"));
        assertEquals("true", properties.getProperty("config.strictAppMigration"));
        assertEquals("false", properties.getProperty("config.strictRoleOnlyBusinessModel"));
        assertEquals("seata", properties.getProperty("backend.creditTx.adapter"));
        assertEquals("at:salesOrder,saga:submitOrder",
                properties.getProperty("backend.creditTx.bindings"));
        assertEquals(" credit-commerce",
                properties.getProperty("backend.creditTx.config.applicationId"));
        assertEquals("credit-commerce-tx-group",
                properties.getProperty("backend.creditTx.config.transactionServiceGroup"));
        assertEquals("com.servpp.creditcommerce.SeataBridge",
                properties.getProperty("backend.creditTx.config.transactionBridgeClass"));
        assertEquals("queryCustomer", properties.getProperty("remoteServices"));
        assertEquals("httpJson", properties.getProperty("remote.queryCustomer.adapter"));
        assertEquals("https://customer.example.test/spp",
                properties.getProperty("remote.queryCustomer.config.endpoint"));
        assertEquals("3", properties.getProperty("remote.queryCustomer.config.compensationMaxAttempts"));
        assertEquals("50", properties.getProperty("remote.queryCustomer.config.compensationBackoffMillis"));
        assertEquals("true",
                properties.getProperty("remote.queryCustomer.config.compensationManualInterventionRequired"));
        assertEquals("objectStore", properties.getProperty("repositories"));
        assertEquals("objectSnapshot", properties.getProperty("repository.objectStore.adapter"));
        assertEquals("spp_object_snapshot",
                properties.getProperty("repository.objectStore.config.table"));
        assertEquals("globalQuantum", properties.getProperty("quantumStores"));
        assertEquals("SalesOrder.amount", properties.getProperty("quantumStore.globalQuantum.attributes"));
        assertEquals("jdbcQuantum", properties.getProperty("quantumStore.globalQuantum.adapter"));
        assertEquals("GLOBAL_QUANTUM_STORE",
                properties.getProperty("quantumStore.globalQuantum.config.implementationStrategy"));
        assertEquals("GLOBAL_STORE_ADAPTER",
                properties.getProperty("quantumStore.globalQuantum.config.accessMode"));
        assertEquals("FRAMEWORK_IDEMPOTENCY",
                properties.getProperty("quantumStore.globalQuantum.config.idempotencyMode"));
        assertEquals("NEGATIVE_DELTA",
                properties.getProperty("quantumStore.globalQuantum.config.reversalMode"));
        assertEquals("jdbc:h2:mem:spp-commerce-quantum;DB_CLOSE_DELAY=-1",
                properties.getProperty("quantumStore.globalQuantum.config.datasourceUrl"));
        assertEquals("com.servpp.service.transaction.JdbcQuantumJournalStore",
                properties.getProperty("quantumStore.globalQuantum.config.journalStore"));
        assertEquals("spp_quantum_attribute_value",
                properties.getProperty("quantumStore.globalQuantum.config.valueTable"));
    }

    @Test
    public void physicalRuntimePropertiesRejectInvalidPhysicalCatalogWhenCalledDirectly() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;

                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }

                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                """, """
                domain commerce;

                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config quantumStore = "globalQuantum";
                    }
                }
                """);
        Path genRoot = Files.createTempDirectory("spp-invalid-physical-properties")
                .resolve("generated-sources/java");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpenApiGenerator().createPhysicalRuntimeProperties(domain, genRoot.toFile()));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "S++ physical runtime catalog validation failed"));
        assertTrue(error.getMessage(), error.getMessage().contains(
                "PHY backend localJdbc quantumStore globalQuantum"
                        + " has no PHY quantumStore adapter definition"));
        assertTrue(error.getMessage(), error.getMessage().contains(
                "PHY quantum store adapter globalQuantum must define the physical implementation"
                        + " for APP quantum store globalQuantum before generating physical runtime catalog"));
    }

    @Test
    public void physicalRuntimePropertiesAcceptQuantumServiceAccessAdapter() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;

                Entity CreditAccount{ quantum double availableCredit; }
                """, """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                """, """
                domain commerce;

                phy{
                    quantumStore globalQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                        config serviceProtocol = "LOGICAL_QSERVICE";
                        config namespace = "commerce";
                        config reversalMode = "SERVICE_REVERSAL";
                        quantum "CreditAccount.availableCredit" {
                            numberType LONG;
                            minValue "0";
                            maxValue "100000000";
                            scale 2;
                        }
                    }
                }
                """);
        Path genRoot = Files.createTempDirectory("spp-quantum-service-physical-properties")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createPhysicalRuntimeProperties(domain, genRoot.toFile());

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(genRoot.getParent()
                .resolve("resources/META-INF/spp/physical.properties"))) {
            properties.load(input);
        }
        assertEquals("globalQuantum", properties.getProperty("quantumStores"));
        assertEquals("quantumService", properties.getProperty("quantumStore.globalQuantum.adapter"));
        assertEquals("https://quantum.example.test",
                properties.getProperty("quantumStore.globalQuantum.config.endpoint"));
        assertEquals("1", properties.getProperty("quantumStore.globalQuantum.definitionCount"));
        assertEquals("CreditAccount.availableCredit",
                properties.getProperty("quantumStore.globalQuantum.definition.0.attributeId"));
        assertEquals("LONG", properties.getProperty("quantumStore.globalQuantum.definition.0.numberType"));
        assertEquals("0", properties.getProperty("quantumStore.globalQuantum.definition.0.minValue"));
        assertEquals("100000000", properties.getProperty("quantumStore.globalQuantum.definition.0.maxValue"));
        assertEquals("2", properties.getProperty("quantumStore.globalQuantum.definition.0.scale"));
    }

    @Test
    public void physicalRuntimePropertiesAcceptTccObjectStoreQuantumAccessAdapter() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;

                Entity CreditAccount{ quantum double availableCredit; }
                """, """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                """, """
                domain commerce;

                phy{
                    backend localTcc{
                        adapter localJdbc;
                        quantumStore globalQuantum;
                        config quantumSupported = "true";
                        config supportedTransactions = "tcc,quantum";
                    }

                    quantumStore globalQuantum{
                        adapter tccObjectStore;
                        config repository = "objectStore";
                    }
                }
                """);
        Path genRoot = Files.createTempDirectory("spp-tcc-object-quantum-physical-properties")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createPhysicalRuntimeProperties(domain, genRoot.toFile());

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(genRoot.getParent()
                .resolve("resources/META-INF/spp/physical.properties"))) {
            properties.load(input);
        }
        assertEquals("globalQuantum", properties.getProperty("quantumStores"));
        assertEquals("tccObjectStore", properties.getProperty("quantumStore.globalQuantum.adapter"));
        assertEquals("objectStore", properties.getProperty("quantumStore.globalQuantum.config.repository"));
        assertFalse(properties.containsKey("quantumStore.globalQuantum.config.datasourceUrl"));
    }

    @Test
    public void physicalRuntimePropertiesKeepSingleBackendBindingsEmptyForDefaultAdapter() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;

                Contract SalesOrder{ String orderNo; String status; }
                atomic update openSalesOrder(SalesOrder order);
                Scenario serial submitOrder(SalesOrder order){
                    transaction{ openSalesOrder(order); }
                }
                """, """
                domain commerce;

                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    local (in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                }
                """, """
                domain commerce;

                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:commerce-local;DB_CLOSE_DELAY=-1";
                    }
                }
                """);
        Path genRoot = Files.createTempDirectory("spp-physical-default-backend")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createPhysicalRuntimeProperties(domain, genRoot.toFile());

        Path physicalProperties = genRoot.getParent()
                .resolve("resources/META-INF/spp/physical.properties");
        String propertiesText = Files.readString(physicalProperties);
        assertTrue(propertiesText, propertiesText.contains("backends=localJdbc"));
        assertTrue(propertiesText, propertiesText.contains("backend.localJdbc.adapter=localJdbc"));
        assertTrue(propertiesText, propertiesText.contains("backend.localJdbc.bindings=\n"));
        assertFalse(propertiesText, propertiesText.contains("at:salesOrder"));
        assertFalse(propertiesText, propertiesText.contains("saga:submitOrder"));
    }

    @Test
    public void physicalRuntimePropertiesKeepLocalJdbcAndQuantumStoreDatasourcesSeparate() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;

                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;

                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }

                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                """, """
                domain commerce;

                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-local;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        quantumStore globalQuantum;
                        quantum creditAccounting;
                    }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-global-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);
        Path genRoot = Files.createTempDirectory("spp-physical-separate-quantum")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createPhysicalRuntimeProperties(domain, genRoot.toFile());

        Path physicalProperties = genRoot.getParent()
                .resolve("resources/META-INF/spp/physical.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(physicalProperties)) {
            properties.load(input);
        }
        assertEquals("jdbc:h2:mem:spp-commerce-local;DB_CLOSE_DELAY=-1",
                properties.getProperty("backend.localJdbc.config.datasourceUrl"));
        assertEquals("globalQuantum",
                properties.getProperty("backend.localJdbc.quantumStore"));
        assertFalse(properties.containsKey("backend.localJdbc.config.quantumStore"));
        assertEquals("jdbc:h2:mem:spp-commerce-global-quantum;DB_CLOSE_DELAY=-1",
                properties.getProperty("quantumStore.globalQuantum.config.datasourceUrl"));
        assertEquals("jdbcQuantum",
                properties.getProperty("quantumStore.globalQuantum.adapter"));
        assertEquals("quantum:creditAccounting",
                properties.getProperty("backend.localJdbc.bindings"));
    }

    @Test
    public void physicalRuntimePropertiesKeepJdbcObjectRepositoryAdapter() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;

                Contract SalesOrder{ String orderNo; }
                atomic query queryOrder(SalesOrder order);
                """, """
                domain commerce;

                scope{
                    local{
                        repository(argument(0, SalesOrder.orderNo), order) queryOrder;
                    }
                }
                """, """
                domain commerce;

                phy{
                    repository objectStore{
                        adapter jdbcObjectRepository;
                        config datasourceUrl = "jdbc:h2:mem:spp-commerce-objects;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config table = "spp_business_object";
                        config appDdlTables = true;
                    }
                }
                """);
        Path genRoot = Files.createTempDirectory("spp-physical-jdbc-object-repository")
                .resolve("generated-sources/java");

        new OpenApiGenerator().createPhysicalRuntimeProperties(domain, genRoot.toFile());

        Path physicalProperties = genRoot.getParent()
                .resolve("resources/META-INF/spp/physical.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(physicalProperties)) {
            properties.load(input);
        }
        assertEquals("objectStore", properties.getProperty("repositories"));
        assertEquals("jdbcObjectRepository", properties.getProperty("repository.objectStore.adapter"));
        assertEquals("jdbc:h2:mem:spp-commerce-objects;DB_CLOSE_DELAY=-1",
                properties.getProperty("repository.objectStore.config.datasourceUrl"));
        assertEquals("spp_business_object", properties.getProperty("repository.objectStore.config.table"));
        assertEquals("true", properties.getProperty("repository.objectStore.config.appDdlTables"));
    }

    private SppDomain compileDomain(String sppSource, String appSource, String phySource) throws Exception {
        File dir = Files.createTempDirectory("spp-physical-properties").toFile();
        File sppFile = write(new File(dir, "commerce.spp"), sppSource);
        File appFile = write(new File(dir, "commerce.app"), appSource);
        File phyFile = write(new File(dir, "commerce.phy"), phySource);
        SppProject project = new SppProject();
        new SppCompiler(sppFile, project).compile();
        new AppCompiler(appFile, project).compile();
        new AppCompiler(phyFile, project).compile();
        return project.getDomain("commerce");
    }

    private File write(File file, String source) throws Exception {
        Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
        return file;
    }
}
