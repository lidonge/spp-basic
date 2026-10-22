package com.servpp.lang;

import com.servpp.ILogable;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import com.servpp.lang.models.app.AppTable;
import com.servpp.lang.models.app.AppTables;
import com.servpp.lang.models.app.AppColumn;
import com.servpp.lang.models.app.AppHeader;
import com.servpp.lang.models.app.AppPhysicalBackend;
import com.servpp.lang.models.app.AppPhysicalBinding;
import com.servpp.lang.models.app.AppQuantumStore;
import com.servpp.lang.models.app.AppRepositoryAdapter;
import com.servpp.lang.models.app.AppRemoteAdapter;
import com.servpp.lang.models.app.AppSemanticMigrationPlan;
import com.servpp.lang.models.app.ObjectSnapshotRule;
import com.servpp.lang.models.app.RepositoryHydrationRule;
import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.models.app.ScopeDefine;
import com.servpp.lang.models.app.ScopeItem;
import com.servpp.lang.models.app.TccReservationRule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

public class AppSemanticsTest {
    @Test
    public void textStorageRequiresAnExactPhysicalNumericBinding() throws Exception {
        String spp = """
                domain creditcommerce;
                Role Evidence { business("id business credential") String id; double value; int count; boolean active; }
                """;
        String app = """
                domain creditcommerce;
                tables roles { table ITEMS(Evidence) { primarykey(Evidence.id); } }
                map ITEMS { value = String(4096) value_text; count = String(100) count_text; }
                """;
        SppDomain exact = compileDomain(spp, app, """
                domain creditcommerce;
                phy { numeric Evidence.value as BigDecimal; numeric int as BigInteger; }
                """);
        assertTrue(exact.validateAppDeclarations().toString(), exact.validateAppDeclarations().isEmpty());
        SppDomain primitive = compileDomain(spp, app);
        assertEquals(2, primitive.validateAppDeclarations().stream()
                .filter(error -> error.contains("declares type String but APP table column")).count());
        SppDomain overridden = compileDomain(spp, app, """
                domain creditcommerce;
                phy { numeric double as BigDecimal; numeric Evidence.value as double; numeric int as BigInteger; }
                """);
        assertTrue(overridden.validateAppDeclarations().toString(), overridden.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("column value declares type String")));
        SppDomain booleanColumn = compileDomain(spp, app.replace("count = String(100) count_text;",
                "active = String(100) active_text;"), """
                domain creditcommerce;
                phy { numeric double as BigDecimal; }
                """);
        assertTrue(booleanColumn.validateAppDeclarations().toString(), booleanColumn.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("column active declares type String")));
        SppDomain unsupported = compileDomain(spp, app, """
                domain creditcommerce;
                phy { numeric double as Unsupported; numeric int as BigInteger; }
                """);
        assertTrue(unsupported.validateAppDeclarations().toString(), unsupported.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("Unsupported PHY numeric representation Unsupported")));
    }

    @Test
    public void antlrGeneratedArtifactsStayOutOfSourceGrammarDirectory() throws Exception {
        Path grammarSourceRoot = Path.of(System.getProperty("user.dir"), "src", "main", "antlr4");
        try (Stream<Path> paths = Files.walk(grammarSourceRoot)) {
            List<Path> generatedArtifacts = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains(File.separator + ".antlr" + File.separator)
                            || !path.getFileName().toString().endsWith(".g4"))
                    .toList();
            assertTrue("ANTLR source grammar directory must contain only .g4 files; generated artifacts"
                            + " must live under target, not " + grammarSourceRoot
                    + ": " + generatedArtifacts, generatedArtifacts.isEmpty());
        }
        assertTrue(Files.exists(Path.of(System.getProperty("user.dir"), "target", "generated-sources",
                "antlr4", "com", "servpp", "lang", "antlr", "BaseParser.java")));
    }

    @Test
    public void appTablesKeepBusinessCategorySemantics() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                Contract SalesOrder{ String orderNo; Goods goods; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String orderNo; }
                }
                scope{ local{ queryGoods; } }
                """);

        RuleBlock ruleBlock = domain.getRuleBlock();
        assertEquals(3, ruleBlock.getAppTables().size());
        assertTable(ruleBlock.getAppTables().get(0), AppTables.Category.entities,
                "PRODUCT", "Product");
        assertTable(ruleBlock.getAppTables().get(1), AppTables.Category.roles,
                "GOODS", "Goods");
        assertTable(ruleBlock.getAppTables().get(2), AppTables.Category.contracts,
                "SALES_ORDER", "SalesOrder");
        assertTrue(domain.validateAppTableDeclarations().isEmpty());
    }

    @Test
    public void invoiceSampleKeepsContractAndRoleAppSemantics() throws Exception {
        SppDomain domain;
        try (var spp = getClass().getResourceAsStream("/app-semantics/invoice/invoice.spp");
             var app = getClass().getResourceAsStream("/app-semantics/invoice/invoice.app")) {
            assertNotNull(spp); assertNotNull(app);
            domain = compileDomain(new String(spp.readAllBytes(), StandardCharsets.UTF_8),
                    new String(app.readAllBytes(), StandardCharsets.UTF_8));
        }

        assertTrue(domain.validateAppTableDeclarations().toString(),
                domain.validateAppTableDeclarations().isEmpty());
        assertTable(domain.getRuleBlock().getAppTables().stream()
                        .flatMap(tables -> tables.getAppTableList().stream())
                        .filter(table -> "INVOICE".equals(table.getName()))
                        .findFirst()
                        .orElseThrow(),
                AppTables.Category.contracts, "INVOICE", "Invoice");
        assertTable(domain.getRuleBlock().getAppTables().stream()
                        .flatMap(tables -> tables.getAppTableList().stream())
                        .filter(table -> "GOODS".equals(table.getName()))
                        .findFirst()
                        .orElseThrow(),
                AppTables.Category.roles, "GOODS", "Goods");
        assertFalse(domain.appSemanticMigrationPlans().toString(),
                domain.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "ROLE_OBJECT_FIELD".equals(plan.migrationType())
                                && plan.paths().contains("Goods.stock:GoodsStock")));
    }

    @Test
    public void appTableDeclarationsValidateCategoryTargetAndUniqueBusinessMapping() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                Contract SalesOrder{ String orderNo; Goods goods; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table PRODUCT_DUP(Product){ primarykey String productId; }
                    table GOODS_AS_ENTITY(Goods){ primarykey String skuId; }
                }
                tables unknown{
                    table ORDER_UNKNOWN(SalesOrder){ primarykey String orderNo; }
                }
                tables roles{
                    table MISSING_TYPE(MissingType){ primarykey String id; }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Business type Product is mapped by multiple APP tables")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS_AS_ENTITY in tables entities must map to a entity")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Unknown APP table category unknown")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table MISSING_TYPE must map to a defined business type")));
    }

    @Test
    public void appTableNamesMustBeUnique() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table PRODUCT(Goods){ primarykey String skuId; }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "APP table PRODUCT is declared multiple times"));
    }

    @Test
    public void appQuantumStoreDeclaresGlobalQuantumAttributes() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; double usedCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{ local{ reserveCredit; } }
                """);

        AppQuantumStore store = domain.getRuleBlock().getAppQuantumStores().get(0);

        assertEquals("globalQuantum", store.getName());
        assertEquals(List.of("CreditAccount.availableCredit", "OrderAmount.occupiedAmount"),
                store.getAttributeIds());
        assertTrue(domain.validateQuantumStoreDeclarations().isEmpty());
    }

    @Test
    public void appQuantumStoreMustDeclareAtLeastOneAttribute() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                }
                scope{ local{ reserveCredit; } }
                """);

        List<String> errors = domain.validateQuantumStoreDeclarations();

        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum store globalQuantum must declare at least one APP quantum attribute")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum field CreditAccount.availableCredit must be assigned to an APP quantum store")));
    }

    @Test
    public void appQuantumStoreMustReferenceQuantumFields() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; double usedCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute CreditAccount.usedCredit;
                    attribute MissingType.value;
                    attribute CreditAccount.availableCredit;
                }
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """);

        List<String> errors = domain.validateQuantumStoreDeclarations();

        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum store globalQuantum is declared multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "declares invalid attribute CreditAccount.usedCredit: field is not quantum")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "declares invalid attribute MissingType.value: class not found")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "declares duplicate attribute CreditAccount.availableCredit")));
    }

    @Test
    public void appQuantumStoreMustOwnEachQuantumFieldExactlyOnce() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain creditcommerce;
                quantumStore creditQuantum{
                    attribute CreditAccount.availableCredit;
                }
                quantumStore duplicatedQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """);

        List<String> errors = domain.validateQuantumStoreDeclarations();

        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum attribute CreditAccount.availableCredit is assigned to multiple APP quantum stores")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum field OrderAmount.occupiedAmount must be assigned to an APP quantum store")));
    }

    @Test
    public void quantumResourcesMustUseAttributesFromAppQuantumStore() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.occupiedAmount;
                        amount.occupiedAmount += amount.occupiedAmount;
                    }
                }
                """);

        List<String> errors = domain.validateQuantumConservationDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum resource creditAccounting declares attribute OrderAmount.occupiedAmount"
                        + " that is not assigned to any APP quantum store")));
    }

    @Test
    public void phyQuantumStoreConfigMustReferenceAppQuantumStore() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config quantumStore = "missingQuantum";
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter globalQuantum"
                        + " must define the physical implementation for APP quantum store globalQuantum")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc quantumStore references undefined quantum store missingQuantum")));

        SppDomain defaultReference = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config quantumStore = "${credit.quantum.store:globalQuantum}";
                    }
                }
                """);

        List<String> defaultReferenceErrors = defaultReference.validatePhysicalRuntimeDeclarations();
        assertEquals(defaultReferenceErrors.toString(), 1, defaultReferenceErrors.size());
        assertTrue(defaultReferenceErrors.toString(), defaultReferenceErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc quantumStore globalQuantum"
                        + " has no PHY quantumStore adapter definition")));

        SppDomain blankDefaultReference = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config quantumStore = "${credit.quantum.store:}";
                    }
                }
                """);

        List<String> blankDefaultErrors = blankDefaultReference.validatePhysicalRuntimeDeclarations();
        assertTrue(blankDefaultErrors.toString(), blankDefaultErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config quantumStore must not be blank when declared")));
    }

    @Test
    public void phyQuantumStoreAdapterDefinesPhysicalQuantumStore() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        quantumStore globalQuantum;
                    }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        assertTrue(domain.validatePhysicalRuntimeDeclarations().toString(),
                domain.validatePhysicalRuntimeDeclarations().isEmpty());
        assertEquals(1, domain.getRuleBlock().getAppQuantumStoreAdapters().size());
        assertEquals("jdbcQuantum",
                domain.getRuleBlock().getAppQuantumStoreAdapters().get(0).getAdapterName());
        assertEquals("spp_quantum_attribute_value",
                domain.getRuleBlock().getAppQuantumStoreAdapters().get(0)
                        .getConfiguration().get("valueTable"));
    }

    @Test
    public void phyQuantumServiceDefinesEveryLogicalQuantumBinding() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum long availableCredit; }
                Entity Inventory{ quantum double availableQty; }
                atomic update reserveInventory(CreditAccount account, Inventory inventory);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute Inventory.availableQty;
                }
                scope{ local{ reserveInventory; } }
                """, """
                domain creditcommerce;
                phy{
                    quantumStore globalQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                        config serviceProtocol = "LOGICAL_QSERVICE";
                        config namespace = "creditcommerce";
                        config reversalMode = "SERVICE_REVERSAL";
                        quantum "CreditAccount.availableCredit" {
                            numberType LONG;
                            minValue "0";
                            maxValue "9223372036854775807";
                            scale 2;
                        }
                        quantum "Inventory.availableQty" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1000000";
                            scale 0;
                        }
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.isEmpty());
        assertEquals(2, domain.getRuleBlock().getAppQuantumStoreAdapters().get(0)
                .getQuantumDefinitions().size());
        assertEquals("LONG", domain.getRuleBlock().getAppQuantumStoreAdapters().get(0)
                .getQuantumDefinitions().get(0).getNumberType());
        assertEquals("2", domain.getRuleBlock().getAppQuantumStoreAdapters().get(0)
                .getQuantumDefinitions().get(0).getScale());
    }

    @Test
    public void phyQuantumServiceRejectsIncompleteOrInvalidLogicalBindings() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum long availableCredit; }
                Entity Inventory{ quantum double availableQty; }
                atomic update reserveInventory(CreditAccount account, Inventory inventory);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute Inventory.availableQty;
                }
                scope{ local{ reserveInventory; } }
                """, """
                domain creditcommerce;
                phy{
                    quantumStore globalQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                        config serviceProtocol = "LOGICAL_QSERVICE";
                        config namespace = "creditcommerce";
                        config reversalMode = "SERVICE_REVERSAL";
                        quantum "CreditAccount.availableCredit" {
                            numberType LONG;
                            minValue "10";
                            maxValue "1";
                            scale 0;
                        }
                        quantum "CreditAccount.availableCredit" {
                            numberType LONG;
                            minValue "0";
                            maxValue "100";
                            scale 0;
                        }
                        quantum "Unknown.amount" {
                            numberType FLOAT;
                            minValue "0";
                            maxValue "Infinity";
                            scale 2;
                        }
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "declares duplicate quantum definition CreditAccount.availableCredit")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "CreditAccount.availableCredit minValue must not be greater than maxValue")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "defines quantum Unknown.amount which is not an attribute")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Unknown.amount maxValue is not a valid FLOAT value")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Unknown.amount fixed scale requires an integer numberType")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "must define number bounds for APP quantum attribute Inventory.availableQty")));
    }

    @Test
    public void phyLocalBackendAndQuantumStoreCanUseSeparatePhysicalStores() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain creditcommerce;
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
                domain creditcommerce;
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:spp-credit-commerce-local;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        quantumStore globalQuantum;
                    }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-credit-commerce-global-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                        config decimalStorage = "text";
                    }
                }
                """);

        assertTrue(domain.validatePhysicalRuntimeDeclarations().toString(),
                domain.validatePhysicalRuntimeDeclarations().isEmpty());
        assertEquals("globalQuantum",
                domain.getRuleBlock().getAppPhysicalBackends().get(0)
                        .getQuantumStoreName());
        assertEquals("jdbc:h2:mem:spp-credit-commerce-local;DB_CLOSE_DELAY=-1",
                domain.getRuleBlock().getAppPhysicalBackends().get(0)
                        .getConfiguration().get("datasourceUrl"));
        assertEquals("jdbc:h2:mem:spp-credit-commerce-global-quantum;DB_CLOSE_DELAY=-1",
                domain.getRuleBlock().getAppQuantumStoreAdapters().get(0)
                        .getConfiguration().get("datasourceUrl"));
        var storage = domain.getRuleBlock().getAppQuantumStoreAdapters().get(0).getConfiguration();
        assertEquals("text", storage.get("decimalStorage"));
        storage.put("decimalStorage", "float");
        assertTrue(domain.validatePhysicalRuntimeDeclarations().stream().anyMatch(error -> error.contains("unsupported decimalStorage")));
        storage.put("decimalStorage", "decimal");
        assertTrue(domain.validatePhysicalRuntimeDeclarations().toString(), domain.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyDeploymentMustDefinePhysicalAdapterForEveryAppQuantumStore() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:spp-local;DB_CLOSE_DELAY=-1";
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY quantum store adapter globalQuantum"
                        + " must define the physical implementation for APP quantum store globalQuantum"));
    }

    @Test
    public void phyJdbcQuantumStoreAdapterMustDefineRuntimeConfig() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter globalQuantum jdbcQuantum config datasourceUrl must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter globalQuantum jdbcQuantum config driverClassName must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter globalQuantum jdbcQuantum config journalStore must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter globalQuantum jdbcQuantum config journalTable must not be blank")));
    }

    @Test
    public void phyJdbcQuantumStoreAdapterRejectsUnsupportedJournalStoreImplementation() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.example.CustomQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY quantum store adapter globalQuantum jdbcQuantum config journalStore"
                        + " declares unsupported implementation com.example.CustomQuantumJournalStore"
                        + "; supported implementation is com.servpp.service.transaction.JdbcQuantumJournalStore"));
    }

    @Test
    public void phyQuantumStoreAdapterMustReferenceAppStoreAndSupportedAdapter() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain creditcommerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                """, """
                domain creditcommerce;
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        config quantumStore = "globalQuantum";
                    }
                    quantumStore missingQuantum{
                        adapter objectSnapshot;
                        config valueTable = "spp_quantum_attribute_value";
                        config valueTable = "dup";
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc quantumStore globalQuantum"
                        + " has no PHY quantumStore adapter definition")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter missingQuantum must reference an APP quantum store")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Unsupported PHY quantum store adapter objectSnapshot on quantum store missingQuantum")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter missingQuantum declares duplicate config valueTable")));
    }

    @Test
    public void phyLogicalQuantumServiceAdapterRequiresServiceSideReversalContract() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
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
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config serviceProtocol = LOGICAL_QSERVICE;
                        config namespace = "commerce";
                        config reversalMode = SERVICE_REVERSAL;
                        quantum "CreditAccount.availableCredit" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1000000";
                            scale 0;
                        }
                        quantum "OrderAmount.occupiedAmount" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1000000";
                            scale 0;
                        }
                    }
                }
                """);

        List<String> errors = valid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void phyQuantumServiceProtocolSelectsItsReversalContract() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                phy{
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config serviceProtocol = LOGICAL_QSERVICE;
                        config namespace = "commerce";
                        config reversalMode = NEGATIVE_DELTA;
                        quantum "CreditAccount.availableCredit" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1000000";
                            scale 0;
                        }
                        quantum "OrderAmount.occupiedAmount" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1000000";
                            scale 0;
                        }
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config reversalMode must be SERVICE_REVERSAL"
                        + " when adapter is quantumService, but was NEGATIVE_DELTA")));

        SppDomain unsupported = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                phy{
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config serviceProtocol = GRPC;
                    }
                }
                """);

        List<String> unsupportedErrors = unsupported.validatePhysicalRuntimeDeclarations();

        assertTrue(unsupportedErrors.toString(), unsupportedErrors.stream().anyMatch(error -> error.contains(
                "config serviceProtocol declares unsupported protocol GRPC")));

        SppDomain mixedProtocol = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{ local{ reserveCredit; } }
                phy{
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config serviceProtocol = HTTP_JSON;
                        config namespace = "commerce";
                        quantum "CreditAccount.availableCredit" {
                            numberType DOUBLE;
                            minValue "0";
                            maxValue "1000000";
                            scale 0;
                        }
                    }
                }
                """);

        List<String> mixedProtocolErrors = mixedProtocol.validatePhysicalRuntimeDeclarations();

        assertTrue(mixedProtocolErrors.toString(), mixedProtocolErrors.stream().anyMatch(error -> error.contains(
                "config namespace is only supported when serviceProtocol is LOGICAL_QSERVICE")));
        assertTrue(mixedProtocolErrors.toString(), mixedProtocolErrors.stream().anyMatch(error -> error.contains(
                "quantum definitions are only supported when serviceProtocol is LOGICAL_QSERVICE")));
    }

    @Test
    public void phyQuantumStoreContractConfigAcceptsDeploymentPlaceholders() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
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
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config implementationStrategy = "${quantum.strategy:QUANTUM_SERVICE}";
                        config accessMode = "${quantum.access:QUANTUM_SERVICE_ADAPTER}";
                        config idempotencyMode = "${quantum.idempotency:SERVICE_IDEMPOTENCY}";
                        config reversalMode = "${quantum.reversal:NEGATIVE_DELTA}";
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
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
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config implementationStrategy = "${quantum.strategy:GLOBAL_QUANTUM_STORE}";
                        config accessMode = "${quantum.access:GLOBAL_STORE_ADAPTER}";
                        config idempotencyMode = "${quantum.idempotency:FRAMEWORK_IDEMPOTENCY}";
                        config reversalMode = "${quantum.reversal:SERVICE_REVERSAL}";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config implementationStrategy must be QUANTUM_SERVICE"
                        + " when adapter is quantumService, but was GLOBAL_QUANTUM_STORE")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config accessMode must be QUANTUM_SERVICE_ADAPTER"
                        + " when adapter is quantumService, but was GLOBAL_STORE_ADAPTER")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config idempotencyMode must be SERVICE_IDEMPOTENCY"
                        + " when adapter is quantumService, but was FRAMEWORK_IDEMPOTENCY")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config reversalMode must be NEGATIVE_DELTA"
                        + " when adapter is quantumService, but was SERVICE_REVERSAL")));

        SppDomain blankDefaults = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
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
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config implementationStrategy = "${quantum.strategy:}";
                        config accessMode = "${quantum.access:}";
                        config idempotencyMode = "${quantum.idempotency:}";
                        config reversalMode = "${quantum.reversal:}";
                    }
                }
                """);

        List<String> blankDefaultErrors = blankDefaults.validatePhysicalRuntimeDeclarations();

        assertTrue(blankDefaultErrors.toString(), blankDefaultErrors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter serviceQuantum config implementationStrategy"
                        + " must not be blank when declared")));
        assertTrue(blankDefaultErrors.toString(), blankDefaultErrors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter serviceQuantum config accessMode must not be blank when declared")));
        assertTrue(blankDefaultErrors.toString(), blankDefaultErrors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter serviceQuantum config idempotencyMode"
                        + " must not be blank when declared")));
        assertTrue(blankDefaultErrors.toString(), blankDefaultErrors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter serviceQuantum config reversalMode must not be blank when declared")));
    }

    @Test
    public void phyQuantumServiceAdapterValidatesTimeoutMillis() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                Role Inventory{ quantum int availableQty; quantum int lockedQty; }
                Role BlankTimeoutProbe{ quantum int probeQty; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update deductInventory(Inventory inventory);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                quantumStore inventoryQuantum{
                    attribute Inventory.availableQty;
                    attribute Inventory.lockedQty;
                }
                quantumStore blankTimeoutQuantum{
                    attribute BlankTimeoutProbe.probeQty;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                        quantum resource(inventoryAccounting,
                            Inventory.availableQty, Inventory.lockedQty) deductInventory;
                    }
                }
                """, """
                domain commerce;
                phy{
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config timeoutMillis = 0;
                    }
                    quantumStore inventoryQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config timeoutMillis = slow;
                    }
                    quantumStore blankTimeoutQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                        config timeoutMillis = "${commerce.quantum.timeout:}";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter serviceQuantum quantumService config timeoutMillis must be positive")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter inventoryQuantum quantumService config timeoutMillis must be a number")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter blankTimeoutQuantum quantumService config timeoutMillis must not be blank when declared")));
    }

    @Test
    public void phyQuantumServiceAdapterValidatesEndpointPlaceholders() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                quantumStore runtimeQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                quantumStore blankQuantum{
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
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "${commerce.quantum.endpoint:https://quantum.internal.example.org}";
                    }
                    quantumStore runtimeQuantum{
                        adapter quantumService;
                        config endpoint = "${commerce.quantum.runtime.endpoint}";
                    }
                    quantumStore blankQuantum{
                        adapter quantumService;
                        config endpoint = "${commerce.quantum.blank.endpoint:}";
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY quantum store adapter blankQuantum quantumService config endpoint must not be blank"));
    }

    @Test
    public void phyTccObjectStoreMustBindBackendSupportingTccAndQuantum() throws Exception {
        SppDomain unbound = compileDomain("""
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
                    quantumStore globalQuantum{
                        adapter tccObjectStore;
                    }
                }
                """);

        List<String> unboundErrors = unbound.validatePhysicalRuntimeDeclarations();
        assertTrue(unboundErrors.toString(), unboundErrors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter globalQuantum tccObjectStore"
                        + " must be referenced by a PHY backend quantumStore binding")));

        SppDomain unsupportedBackend = compileDomain("""
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
                    backend creditTx{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx";
                        quantumStore globalQuantum;
                    }
                    quantumStore globalQuantum{
                        adapter tccObjectStore;
                    }
                }
                """);

        List<String> unsupportedErrors = unsupportedBackend.validatePhysicalRuntimeDeclarations();
        assertTrue(unsupportedErrors.toString(), unsupportedErrors.stream().anyMatch(error -> error.contains(
                "PHY quantum store adapter globalQuantum tccObjectStore"
                        + " must be referenced by a PHY backend that supports both tcc and quantum")));
        assertTrue(unsupportedErrors.toString(), unsupportedErrors.stream().anyMatch(error -> error.contains(
                "creditTx=at+xa+tcc+saga")));

        SppDomain valid = compileDomain("""
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
                        quantumStore globalQuantum;
                    }
                    quantumStore globalQuantum{
                        adapter tccObjectStore;
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain shorthandBackend = compileDomain("""
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
                        quantumStore globalQuantum;
                    }
                    quantumStore globalQuantum{
                        adapter tccObjectStore;
                    }
                }
                """);

        assertTrue(shorthandBackend.validatePhysicalRuntimeDeclarations().toString(),
                shorthandBackend.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyBackendSupportedTransactionsConfigMustUseKnownTransactionKinds() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        at salesOrder;
                        config supportedTransactions = "at, ,xa,quantum";
                    }
                    backend creditTx{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx";
                        config supportedTransactions = "";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc config supportedTransactions must be a comma-separated list"
                        + " of at, tcc, quantum, and saga; unsupported values: <blank>")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend creditTx config supportedTransactions must not be blank when declared")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        at salesOrder;
                        config supportedTransactions = "${backend.supported:at,tcc,quantum,saga}";
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void appTableMustMapExactlyOneBusinessType() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                Enum GoodsStatus{ OnSale,OffSale }
                atomic query queryProduct(Product product);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product, Store){ primarykey(Product.productId); }
                    table STATUS(GoodsStatus){ primarykey String statusId; }
                }
                scope{ local{ queryProduct; } }
                """);

        List<String> errors = invalid.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table PRODUCT must map to exactly one business type; already mapped to Product, cannot also map Store")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table STATUS must map to a business type, but GoodsStatus is enum")));
        AppTable productTable = invalid.getRuleBlock().getAppTables().get(0).getAppTableList().get(0);
        assertEquals("Product", productTable.getEntity().getName());
    }

    @Test
    public void appTableDeclarationsRejectMultipleBusinessTypesWithoutParseErrors() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                atomic query queryProduct(Product product);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product, Store){ primarykey(Product.productId); }
                }
                scope{ local{ queryProduct; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Table PRODUCT must map to exactly one business type; already mapped to Product, cannot also map Store"));
    }

    @Test
    public void appTableDeclarationsRejectNonBusinessTargetTypes() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Enum GoodsStatus{ OnSale,OffSale }
                atomic query queryStatus();
                """, """
                domain creditcommerce;
                tables entities{
                    table STATUS(GoodsStatus){ primarykey String statusId; }
                }
                scope{ local{ queryStatus; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Table STATUS must map to a business type, but GoodsStatus is enum"));
    }

    @Test
    public void appTableDeclarationsRejectUndefinedBusinessTargets() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                atomic query queryMissing();
                """, """
                domain creditcommerce;
                tables entities{
                    table MISSING_TYPE(MissingType){ primarykey String id; }
                }
                scope{ local{ queryMissing; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Table MISSING_TYPE must map to a defined business type, but MissingType is not defined"));
    }

    @Test
    public void appTableDeclarationsRequireResolvablePrimaryKeys() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.product); }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table PRODUCT must declare at least one primary key")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Goods.product on table GOODS references table PRODUCT without primary keys")));
    }

    @Test
    public void appTableDeclarationsRejectArrayFieldsAsPrimaryOrForeignKeys() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product[] alternatives; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey(Goods.alternatives);
                        foreignkey(Goods.alternatives);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Goods.alternatives on table GOODS must refer to a single field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.alternatives on table GOODS must refer to a single object field")));
    }

    @Test
    public void appTableDeclarationsRejectQuantumFieldsAsPrimaryOrForeignKeys() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity CreditAccount{ quantum double availableCredit; double usedCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                atomic query queryCredit(CreditAccount account);
                """, """
                domain creditcommerce;
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
                scope{ local{ queryCredit; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key CreditAccount.availableCredit on table CREDIT_ACCOUNT"
                        + " must not reference a quantum field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key OrderAmount.occupiedAmount on table ORDER_AMOUNT"
                        + " must not reference a quantum field")));
    }

    @Test
    public void appTableDeclarationsRequireObjectPrimaryKeysToExpandToColumns() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Entity Store{ business("storeId business credential") String storeId; }
                Entity Product{ Store store; String name; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table STORE(Store){ primarykey String storeId; }
                    table PRODUCT(Product){ primarykey(Product.store); }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                map GOODS{
                    product_store_storeid = String product_store_id;
                }
                scope{ local{ queryGoods; } }
                """);
        assertTrue(valid.validateAppTableDeclarations().isEmpty());
        assertTrue(valid.validateAppMapperDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ Store store; String name; }
                Entity Store{ Product product; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey(Product.store); }
                    table STORE(Store){ primarykey(Store.product); }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Product.store on table PRODUCT references table STORE without expandable primary key columns")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Store.product on table STORE references table PRODUCT without expandable primary key columns")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.product on table GOODS references table PRODUCT without expandable primary key columns")));
    }

    @Test
    public void appTableDeclarationsAllowNestedPrimaryKeysOwnedByMappedBusinessType() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ Customer customer; double creditLimit; }
                atomic query queryCreditAccount(CreditAccount account);
                """, """
                domain creditcommerce;
                tables entities{
                    table CUSTOMER(Customer){ primarykey String customerId; }
                    table CREDIT_ACCOUNT(CreditAccount){ primarykey(CreditAccount.customer.customerId); }
                }
                scope{ local{ queryCreditAccount; } }
                """);

        assertTrue(valid.validateAppTableDeclarations().toString(),
                valid.validateAppTableDeclarations().isEmpty());
    }

    @Test
    public void appTableDeclarationsRequirePrimaryKeysToBelongToMappedBusinessType() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ String name; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table STORE(Store){ primarykey String name; }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Store.name); }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Primary key Store.name on table GOODS must belong to Goods"));
    }

    @Test
    public void appTableDeclarationsRejectDuplicatePrimaryAndForeignKeyFields() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey(Goods.skuId, Goods.skuId);
                        foreignkey(Goods.product);
                        foreignkey(Goods.product);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS declares duplicate primary key Goods.skuId")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS declares duplicate foreign key Goods.product")));
    }

    @Test
    public void appTableDeclarationsRejectDuplicatePrimaryKeyColumns() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        primarykey(Goods.skuId);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Table GOODS declares duplicate primary key column skuId"));
    }

    @Test
    public void appTableDeclarationsValidateForeignKeysBeforeDdlGeneration() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                Role Goods{ business("skuId business credential") String skuId; String productCode; Product product; Store store; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table STORE(Store){ }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product) references Store;
                        foreignkey(Goods.productCode);
                        foreignkey(Goods.store);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table STORE must declare at least one primary key")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.product on table GOODS references Store but field type is Product")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.productCode on table GOODS must refer to an object field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.store on table GOODS references table STORE without primary keys")));
    }

    @Test
    public void appTableDeclarationsRejectObjectKeysReferencingInvalidTargetTables() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Store{ business("storeId business credential") String storeId; }
                Entity Product{ Store store; }
                Role Goods{ business("skuId business credential") String skuId; Store store; }
                Role Shipment{ business("shipmentId business credential") String shipmentId; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey(Product.store); }
                }
                tables roles{
                    table STORE(Store){ primarykey String storeId; }
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.store);
                    }
                    table SHIPMENT(Shipment){ primarykey String shipmentId; }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table STORE in tables roles must map to a role, but Store is entity")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Product.store on table PRODUCT references table STORE with invalid APP table target")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.store on table GOODS references table STORE with invalid APP table target")));
    }

    @Test
    public void appTableDeclarationsRejectObjectKeysReferencingDuplicateBusinessMappings() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Bundle{ Product product; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table PRODUCT_DUP(Product){ primarykey String productId; }
                    table BUNDLE(Bundle){ primarykey(Bundle.product); }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Business type Product is mapped by multiple APP tables: PRODUCT and PRODUCT_DUP")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Bundle.product on table BUNDLE references table PRODUCT with invalid APP table target")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.product on table GOODS references table PRODUCT with invalid APP table target")));
    }

    @Test
    public void appTableDeclarationsRejectObjectKeysReferencingDuplicateTableNames() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                Entity Bundle{ Product product; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table PRODUCT(Store){ primarykey String storeId; }
                    table BUNDLE(Bundle){ primarykey(Bundle.product); }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP table PRODUCT is declared multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Bundle.product on table BUNDLE references table PRODUCT with invalid APP table target")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.product on table GOODS references table PRODUCT with invalid APP table target")));
    }

    @Test
    public void appTableDeclarationsRejectObjectKeysReferencingUnstableTargetTableShapes() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; int rating; }
                Entity Bundle{ Product product; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){
                        primarykey String productId;
                        notnull int(10) rating;
                    }
                    table BUNDLE(Bundle){ primarykey(Bundle.product); }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table PRODUCT APP column rating with type int cannot declare precision")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Primary key Bundle.product on table BUNDLE references table PRODUCT with unstable APP table shape")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.product on table GOODS references table PRODUCT with unstable APP table shape")));
    }

    @Test
    public void appTableDeclarationsValidateForeignKeyReferenceTargets() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Enum GoodsStatus{ OnSale,OffSale }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product) references MissingProduct;
                        foreignkey(Goods.product) references GoodsStatus;
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.product on table GOODS references undefined type MissingProduct")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Foreign key Goods.product on table GOODS references GoodsStatus but it is not a business type")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS declares duplicate foreign key Goods.product")));
    }

    @Test
    public void appTableForeignKeyDeclarationMustReferenceOneTargetType() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; Store store; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
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
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Foreign key on table GOODS must reference one target business type per declaration"));
        assertTrue(errors.get(0), errors.get(0).contains("Product and Store"));
    }

    @Test
    public void appTableDeclarationsRejectDuplicateOutputColumns() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; business("productProductId business credential") String productProductId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        notnull String skuId;
                        foreignkey(Goods.product);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS declares duplicate APP column skuId")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS output column productProductId is produced by multiple APP table fields")));
    }

    @Test
    public void appTableDeclarationsRequireExplicitScalarColumnsToMatchBusinessFieldTypes() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; int availableQty; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        notnull int availableQty;
                    }
                }
                scope{ local{ queryGoods; } }
                """);
        assertTrue(valid.validateAppTableDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; int availableQty; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey int skuId;
                        notnull String availableQty;
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column skuId shadows business field Goods.skuId but has type int instead of String")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column availableQty shadows business field Goods.availableQty but has type String instead of int")));
    }

    @Test
    public void appTableDeclarationsValidateExplicitColumnPrecisionAndScale() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                        notnull String(120) displayName;
                        notnull double(12, 3) saleAmount;
                        notnull decimal(19, 4) discountAmount;
                    }
                }
                scope{ local{ queryGoods; } }
                """);
        assertTrue(valid.validateAppTableDeclarations().toString(),
                valid.validateAppTableDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                        notnull String(80, 2) displayName;
                        notnull int(10) quantity;
                        notnull DateTime(6) createdAt;
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column displayName with type String cannot declare scale")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column quantity with type int cannot declare precision")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column createdAt with type DateTime cannot declare precision")));
    }

    @Test
    public void appTableDeclarationsRejectAmbiguousExplicitColumnModifiers() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                        null String displayName;
                        notnull double saleAmount;
                    }
                }
                scope{ local{ queryGoods; } }
                """);
        assertTrue(valid.validateAppTableDeclarations().toString(),
                valid.validateAppTableDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey primarykey String rowId;
                        null null String displayName;
                        notnull notnull double saleAmount;
                        null notnull String externalCode;
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column rowId declares primarykey multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column displayName declares null multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column saleAmount declares notnull multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column externalCode cannot declare both null and notnull")));
    }

    @Test
    public void appTableDeclarationsAllowPlatformColumnsToOverrideExpandedReferences() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        notnull String productProductId;
                        foreignkey(Goods.product);
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = domain.validateAppTableDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        AppColumn productProductId = domain.getRuleBlock().getAppTables().get(1)
                .getAppTableList().get(0).getAppColumns().stream()
                .filter(column -> "productProductId".equals(column.getName()))
                .findFirst()
                .orElseThrow();
        assertFalse(productProductId.isObjectBacked());
    }

    @Test
    public void appTableDeclarationsRejectExplicitColumnsThatShadowNonScalarBusinessFields() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; Product[] alternatives; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey String rowId;
                        notnull String product;
                        notnull String alternatives;
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column product cannot shadow non-scalar business field Goods.product")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Table GOODS APP column alternatives cannot shadow non-scalar business field Goods.alternatives")));
    }

    @Test
    public void appTableDeclarationsRejectRecursiveImplicitObjectExpansion() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Category{ business("categoryId business credential") String categoryId; Category parent; }
                Role Goods{ business("skuId business credential") String skuId; Category category; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppTableDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Table GOODS business field Goods.category cannot be implicitly expanded because Category has recursive object fields"));
    }

    @Test
    public void appMapperDeclarationsMustUseTableColumnsAndUniqueOutputs() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    skuId = String sku_id;
                }
                scope{ local{ queryGoods; } }
                """);
        assertTrue(valid.validateAppMapperDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    skuId = String name;
                    missingColumn = String missing_column;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column missingColumn is not defined in APP table GOODS")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS output column name is produced by multiple APP table columns")));
    }

    @Test
    public void appMapperDeclarationsRejectDuplicateSourceColumns() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    skuId = String sku_id;
                    skuId = String sku_id;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Mapper GOODS declares duplicate source column skuId"));
    }

    @Test
    public void appMapperDeclarationsRejectDuplicateUndefinedSourceColumns() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    missingColumn = String missing_column;
                    missingColumn = String missing_column_2;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS declares duplicate source column missingColumn")));
        assertEquals(errors.toString(), 2, errors.stream()
                .filter(error -> error.contains("Mapper GOODS column missingColumn is not defined in APP table GOODS"))
                .count());
    }

    @Test
    public void appMapperDeclarationsRejectDuplicateMapperBlocks() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    skuId = String sku_id;
                }
                map GOODS{
                    name = String goods_name;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "APP mapper GOODS is declared multiple times"));
    }

    @Test
    public void appMapperDeclarationsRejectUnsupportedParameters() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS(readModel, tenantScoped){
                    skuId = String sku_id;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Mapper GOODS declares unsupported parameters: readModel, tenantScoped"));
    }

    @Test
    public void appMapperDeclarationsMustPreserveColumnTypes() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; int availableQty; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                }
                map GOODS{
                    skuId = int sku_id;
                    availableQty = String available_qty;
                    productProductId = int product_id;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column skuId declares type int but APP table column skuId has type String")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column availableQty declares type String but APP table column availableQty has type int")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column productProductId declares type int but APP table column productProductId has type String")));
    }

    @Test
    public void appMapperDeclarationsValidateSqlTypePrecisionAndScale() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; double saleAmount; decimal discountAmount; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    skuId = String(120) sku_id;
                    saleAmount = double(12, 3) sale_amount;
                    discountAmount = decimal(19, 4) discount_amount;
                }
                scope{ local{ queryGoods; } }
                """);
        assertTrue(valid.validateAppMapperDeclarations().toString(),
                valid.validateAppMapperDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; int quantity; DateTime createdAt; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    skuId = String(80, 2) sku_id;
                    quantity = int(10) quantity;
                    createdAt = DateTime(6) created_at;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column skuId with type String cannot declare scale")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column quantity with type int cannot declare precision")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column createdAt with type DateTime cannot declare precision")));
    }

    @Test
    public void appMapperDeclarationsKeepUndefinedTableMappingsIsolated() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; String name; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){ primarykey String rowId; }
                }
                map GOODS{
                    skuId = String sku_id;
                }
                map MISSING{
                    missingColumn = String missing_column;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> mapperErrors = invalid.validateAppMapperDeclarations();
        assertEquals(mapperErrors.toString(), 1, mapperErrors.size());
        assertTrue(mapperErrors.get(0), mapperErrors.get(0).contains(
                "Mapper table MISSING is not defined"));

        List<String> errors = invalid.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Identifier MISSING not defined")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper table MISSING is not defined")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper GOODS column missingColumn")));
    }

    @Test
    public void appMapperDeclarationsRejectUnstableAppTableTargets() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                Entity Warehouse{ business("warehouseId business credential") String warehouseId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                    table PRODUCT_DUP(Product){ primarykey String productId; }
                    table WAREHOUSE(Warehouse){ primarykey String warehouseId; }
                    table WAREHOUSE(Store){ primarykey String storeId; }
                }
                tables roles{
                    table STORE(Store){ primarykey String storeId; }
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                map PRODUCT{
                    productId = String product_id;
                }
                map GOODS{
                    product_productid = String product_id;
                }
                map STORE{
                    storeId = String store_id;
                }
                map WAREHOUSE{
                    warehouseId = String warehouse_id;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper table PRODUCT does not reference a stable APP table target")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper table GOODS depends on unstable APP table object key targets")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper table STORE does not reference a stable APP table target")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper table WAREHOUSE does not reference a stable APP table target")));
    }

    @Test
    public void appMapperDeclarationsRejectNonGeneratableAppTables() throws Exception {
        SppDomain invalid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product[] alternatives; }
                Role Inventory{ business("inventoryId business credential") String inventoryId; int quantity; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey(Goods.alternatives);
                    }
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                        notnull int(10) quantity;
                    }
                }
                map GOODS{
                    alternatives = String alternative_id;
                }
                map INVENTORY{
                    quantity = int quantity;
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper table GOODS does not reference a generatable APP table")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Mapper table INVENTORY does not reference a generatable APP table")));
    }

    @Test
    public void appMapperDeclarationsUseDupNamingTypeForRepeatedObjectFields() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Address{ business("addressId business credential") String addressId; }
                Role Delivery{ business("deliveryId business credential") String deliveryId; Address shipAddress; Address billAddress; }
                atomic query queryDelivery(Delivery delivery);
                """, """
                domain commerce;
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
                scope{ local{ queryDelivery; } }
                """);

        assertTrue(valid.validateAppTableDeclarations().isEmpty());
        assertTrue(valid.validateAppMapperDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Address{ business("addressId business credential") String addressId; }
                Role Delivery{ business("deliveryId business credential") String deliveryId; Address shipAddress; Address billAddress; }
                atomic query queryDelivery(Delivery delivery);
                """, """
                domain commerce;
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
                    shipaddress_addressid = String ship_address_id;
                }
                scope{ local{ queryDelivery; } }
                """);

        List<String> errors = invalid.validateAppMapperDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Mapper DELIVERY column shipaddress_addressid is not defined in APP table DELIVERY"));
    }

    @Test
    public void appMapperDeclarationsAcceptImplicitExpandedObjectColumns() throws Exception {
        SppDomain valid = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                }
                map GOODS{
                    productProductId = String product_id;
                }
                scope{ local{ queryGoods; } }
                """);

        assertTrue(valid.validateAppTableDeclarations().isEmpty());
        assertTrue(valid.validateAppMapperDeclarations().toString(),
                valid.validateAppMapperDeclarations().isEmpty());
    }

    @Test
    public void appTableDeclarationsAllowPlatformColumnsToOverrideImplicitObjectExpansion() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoods(Goods goods);
                """, """
                domain creditcommerce;
                @insideEntity(namingType="camelCase")
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        notnull String productProductId;
                    }
                }
                scope{ local{ queryGoods; } }
                """);

        List<String> errors = domain.validateAppTableDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        AppColumn productProductId = domain.getRuleBlock().getAppTables().get(0)
                .getAppTableList().get(0).getAppColumns().stream()
                .filter(column -> "productProductId".equals(column.getName()))
                .findFirst()
                .orElseThrow();
        assertFalse(productProductId.isObjectBacked());
    }

    @Test
    public void appTableDeclarationsAllowPlatformPrimaryKeysToOverrideBusinessFields() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Store{ String name; String location; }
                atomic query queryStore(Store store);
                """, """
                domain creditcommerce;
                tables entities{
                    table STORE(Store){
                        primarykey String name;
                    }
                }
                scope{ local{ queryStore; } }
                """);

        List<String> errors = domain.validateAppTableDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        AppColumn name = domain.getRuleBlock().getAppTables().get(0)
                .getAppTableList().get(0).getAppColumns().stream()
                .filter(column -> "name".equals(column.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(name.isPrimaryKey());
        assertFalse(name.isObjectBacked());
    }

    @Test
    public void appScopeSeparatesQuantumAttributesTccLifecycleAndRepositoryRules() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Product{ business("productId business credential") String productId; }
                Reference Customer{ business("customerId business credential") String customerId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role Inventory{ quantum int availableQty; int lockedQty; }
                Entity CreditAccount{ Customer customer; quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                Contract SalesOrder{ String orderNo; Customer buyer; CreditAccount account; OrderAmount amount; }
                atomic query queryCustomer(Customer customer);
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update reserveInventory(Inventory inventory);
                atomic update deductReservedInventory(Inventory inventory);
                atomic update releaseReservedInventory(Inventory inventory);
                """, """
                domain creditcommerce;
                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "creditcommerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.customer.customerId), current(SalesOrder.orderNo))
                            reserveCredit;
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);

        ScopeDefine local = domain.getRuleBlock().getAppScope().getScopelist().get(0);
        List<ScopeItem> items = local.getScopeItems();
        ScopeItem queryCustomer = items.get(0);
        ScopeItem reserveCredit = items.get(1);
        ScopeItem reserveInventory = items.get(2);

        RepositoryHydrationRule repositoryRule = queryCustomer.getRepositoryHydrationRules().get(0);
        assertEquals("Customer", repositoryRule.getObjectType());
        assertEquals("customerId", repositoryRule.getObjectIdPath());
        assertEquals("customer", repositoryRule.responseMethodName());

        assertEquals("creditAccounting", reserveCredit.getResourceId());
        assertEquals(List.of("CreditAccount.availableCredit", "OrderAmount.occupiedAmount"),
                reserveCredit.getQuantumConservationAttributeIds());
        assertTrue(reserveCredit.getResourceServiceNames().isEmpty());
        assertEquals(2, reserveCredit.getObjectSnapshotRules().size());
        ObjectSnapshotRule accountSnapshot = reserveCredit.getObjectSnapshotRules().get(0);
        assertEquals(ObjectSnapshotRule.Source.argument, accountSnapshot.source());
        assertEquals(0, accountSnapshot.argumentIndex());
        assertEquals("CreditAccount", accountSnapshot.getObjectType());
        assertEquals("customer.customerId", accountSnapshot.getObjectIdPath());
        ObjectSnapshotRule orderSnapshot = reserveCredit.getObjectSnapshotRules().get(1);
        assertEquals(ObjectSnapshotRule.Source.current, orderSnapshot.source());
        assertEquals("SalesOrder", orderSnapshot.getObjectType());
        assertEquals("orderNo", orderSnapshot.getObjectIdPath());
        assertEquals(ObjectSnapshotRule.Timing.afterCommit, orderSnapshot.timing());

        assertEquals("inventoryTcc", reserveInventory.getResourceId());
        assertEquals(List.of("deductReservedInventory", "releaseReservedInventory"),
                reserveInventory.getResourceServiceNames());
        assertTrue(reserveInventory.getQuantumConservationAttributeIds().isEmpty());
        assertEquals("deductReservedInventory", reserveInventory.getTccConfirmServiceName());
        assertEquals("releaseReservedInventory", reserveInventory.getTccCancelServiceName());
    }

    @Test
    public void appScopeAcceptsBlockTccResourceReservationSyntax() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Role Goods{ business("skuId business credential") String skuId; }
                Role Inventory{ quantum int availableQty; }
                Contract SalesOrder{ String orderNo; }
                atomic update reserveInventory(Inventory inventory);
                atomic update deductReservedInventory(Inventory inventory);
                atomic update releaseReservedInventory(Inventory inventory);
                """, """
                domain creditcommerce;
                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "creditcommerce"); }
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey notnull String goods_skuid;
                        null int lockedQty;
                    }
                }
                scope{
                    local (in baseIn, out baseOut){
                        tcc resource(inventoryTcc){
                            reserve Inventory.availableQty -> Inventory.lockedQty;
                            confirm deductReservedInventory;
                            cancel releaseReservedInventory;
                            snapshot(before, argument(0, Inventory.goods_skuid));
                            snapshot(transaction);
                        } reserveInventory;
                    }
                }
                """);

        ScopeItem reserveInventory = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);

        assertEquals("inventoryTcc", reserveInventory.getResourceId());
        assertEquals(List.of("deductReservedInventory", "releaseReservedInventory"),
                reserveInventory.getResourceServiceNames());
        assertEquals("deductReservedInventory", reserveInventory.getTccConfirmServiceName());
        assertEquals("releaseReservedInventory", reserveInventory.getTccCancelServiceName());
        assertEquals(1, reserveInventory.getTccReservationRules().size());
        TccReservationRule reservation = reserveInventory.getTccReservationRules().get(0);
        assertEquals("Inventory.availableQty", reservation.sourceAttributeId());
        assertEquals("Inventory.lockedQty", reservation.reservationAttributeId());
        assertEquals(2, reserveInventory.getObjectSnapshotRules().size());
        ObjectSnapshotRule beforeSnapshot = reserveInventory.getObjectSnapshotRules().get(0);
        assertEquals(ObjectSnapshotRule.Source.argument, beforeSnapshot.source());
        assertEquals(ObjectSnapshotRule.Timing.before, beforeSnapshot.timing());
        assertEquals("Inventory", beforeSnapshot.getObjectType());
        assertEquals("goods_skuid", beforeSnapshot.getObjectIdPath());
        assertEquals(ObjectSnapshotRule.Source.transaction,
                reserveInventory.getObjectSnapshotRules().get(1).source());
    }

    @Test
    public void appScopeRejectsDuplicateServiceDeclarations() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                atomic update releaseInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(inventory) reserveInventory;
                        at resource(inventory) releaseInventory;
                    }
                }
                """);
        assertTrue(valid.validateUniqueScopeServiceDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(inventory) reserveInventory;
                        tcc resource(inventoryTcc) reserveInventory;
                    }
                }
                """);
        List<String> errors = invalid.validateUniqueScopeServiceDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Service reserveInventory is declared multiple times in APP scope"));
    }

    @Test
    public void appScopeRejectsUndefinedOrNonServiceReferencesInUnifiedValidation() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        missingService;
                        Customer;
                        queryCustomer;
                    }
                }
                """);

        List<String> scopeErrors = invalid.validateUniqueScopeServiceDeclarations();
        assertEquals(scopeErrors.toString(), 2, scopeErrors.size());
        assertTrue(scopeErrors.toString(), scopeErrors.stream().anyMatch(error -> error.contains(
                "Scope service missingService is not defined or is not a service")));
        assertTrue(scopeErrors.toString(), scopeErrors.stream().anyMatch(error -> error.contains(
                "Scope service Customer is not defined or is not a service")));

        List<String> errors = invalid.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "missingService should be a service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Customer should be a service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service missingService is not defined or is not a service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service Customer is not defined or is not a service")));
    }

    @Test
    public void duplicateScopeDeclarationsMustNotOverwriteExistingServiceBinding() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(inventory) reserveInventory;
                    }
                    remote{
                        reserveInventory;
                    }
                }
                """);

        SppService reserveInventory = (SppService) domain.getSppClass("ReserveInventory");
        assertTrue(reserveInventory.getScopeItem().isLocal());
        assertEquals("inventory", reserveInventory.getScopeItem().getResourceId());
        List<String> errors = domain.validateUniqueScopeServiceDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Service reserveInventory is declared multiple times in APP scope"));
    }

    @Test
    public void appDefaultScopeOverridesImplicitDefaultServiceBinding() throws Exception {
        SppDomain domain = compileDomainWithDefaultServices("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        _default;
                    }
                }
                """);

        SppService createCustomer = (SppService) domain.getSppClass("CreateCustomer");
        assertTrue(createCustomer.getScopeItem().isLocal());
        assertFalse(createCustomer.getScopeItem().isImplicitDefaultBinding());
        assertEquals("baseIn", createCustomer.getScopeItem().getScopeDefine().getIn().getName());
        assertEquals("baseOut", createCustomer.getScopeItem().getScopeDefine().getOut().getName());
    }

    @Test
    public void appDefaultScopeExpandsToIndependentDefaultServiceBindings() throws Exception {
        SppDomain domain = compileDomainWithDefaultServices("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                Contract SalesOrder{ String orderNo; }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        _default;
                    }
                }
                """);

        ScopeDefine local = domain.getRuleBlock().getAppScope().getScopelist().get(0);
        assertEquals(8, local.getScopeItems().size());

        SppService createCustomer = (SppService) domain.getSppClass("CreateCustomer");
        SppService updateCustomer = (SppService) domain.getSppClass("UpdateCustomer");
        SppService getCustomer = (SppService) domain.getSppClass("GetCustomer");
        SppService createSalesOrder = (SppService) domain.getSppClass("CreateSalesOrder");

        assertEquals(createCustomer, createCustomer.getScopeItem().getService());
        assertEquals(updateCustomer, updateCustomer.getScopeItem().getService());
        assertEquals(getCustomer, getCustomer.getScopeItem().getService());
        assertEquals(createSalesOrder, createSalesOrder.getScopeItem().getService());
        assertFalse(createCustomer.getScopeItem() == updateCustomer.getScopeItem());
        assertFalse(createCustomer.getScopeItem() == createSalesOrder.getScopeItem());
        assertTrue(domain.validateUniqueScopeServiceDeclarations().isEmpty());
    }

    @Test
    public void scopeAsyncModifierAcceptsCorrectAndLegacySpellings() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic update reserveInventory(Inventory inventory);
                atomic update releaseInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        async reserveInventory;
                        asycn releaseInventory;
                    }
                }
                """);

        ScopeDefine local = domain.getRuleBlock().getAppScope().getScopelist().get(0);
        assertTrue(local.getScopeItems().get(0).isAsync());
        assertTrue(local.getScopeItems().get(1).isAsync());
        assertTrue(domain.getAppParseErrors().toString(), domain.getAppParseErrors().isEmpty());
        assertTrue(domain.validateUniqueScopeServiceDeclarations().toString(),
                domain.validateUniqueScopeServiceDeclarations().isEmpty());
        assertTrue(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("Scope service releaseInventory")
                                && warning.contains("legacy async modifier spelling asycn")));
        assertTrue(domain.appSemanticMigrationPlans().stream()
                .anyMatch(plan -> "LEGACY_ASYNC_MODIFIER".equals(plan.migrationType())
                        && "SCOPE_SERVICE".equals(plan.targetKind())
                        && "releaseInventory".equals(plan.serviceName())
                        && plan.paths().equals(List.of("asycn"))
                        && "replaceLegacyAsycnWithAsync".equals(plan.migrationAction())));
        assertFalse(domain.appSemanticMigrationPlans().stream()
                .anyMatch(plan -> "LEGACY_ASYNC_MODIFIER".equals(plan.migrationType())
                        && "reserveInventory".equals(plan.serviceName())));

        SppDomain duplicateAsync = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic update reserveInventory(Inventory inventory);
                atomic update releaseInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        async asycn reserveInventory;
                        async async releaseInventory;
                    }
                }
                """);

        List<String> errors = duplicateAsync.validateUniqueScopeServiceDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service reserveInventory declares async multiple times: async, asycn")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service releaseInventory declares async multiple times: async, async")));
    }

    @Test
    public void scopeBodiesCanDeclareRemoteBeforeLocalWithoutChangingOwnership() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                Contract SalesOrder{ String orderNo; Customer buyer; }
                atomic query queryCustomer(Customer customer);
                atomic update openSalesOrder(SalesOrder order);
                Scenario serial submitOrder(SalesOrder order){
                    queryCustomer(order.buyer);
                    transaction{
                        openSalesOrder(order);
                    }
                }
                """, """
                domain commerce;
                header remoteIn{ notnull String caller; }
                header remoteOut{ notnull String appId(Default = "customer"); }
                header localIn{ unique String globalSerialNumber; }
                header localOut{ notnull String appId(Default = "commerce"); }
                scope{
                    remote(in remoteIn, out remoteOut){
                        queryCustomer;
                    }
                    local(in localIn, out localOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                }
                services{
                    openSalesOrder{
                        order.orderNo = order.orderNo;
                    }
                }
                """);

        List<ScopeDefine> scopes = domain.getRuleBlock().getAppScope().getScopelist();
        assertEquals(2, scopes.size());
        assertFalse(scopes.get(0).isLocal());
        assertTrue(scopes.get(1).isLocal());
        assertEquals("remoteIn", scopes.get(0).getIn().getName());
        assertEquals("localIn", scopes.get(1).getIn().getName());

        SppService queryCustomer = (SppService) domain.getSppClass("QueryCustomer");
        SppService openSalesOrder = (SppService) domain.getSppClass("OpenSalesOrder");
        SppService submitOrder = (SppService) domain.getSppClass("SubmitOrder");
        assertFalse(queryCustomer.getScopeItem().isLocal());
        assertTrue(openSalesOrder.getScopeItem().isLocal());
        assertTrue(submitOrder.getScopeItem().isLocal());
        assertEquals("salesOrder", openSalesOrder.getScopeItem().getResourceId());
        assertTrue(domain.getAppParseErrors().toString(), domain.getAppParseErrors().isEmpty());
        assertTrue(domain.validateUniqueScopeServiceDeclarations().toString(),
                domain.validateUniqueScopeServiceDeclarations().isEmpty());
        assertTrue(domain.validateRemoteScopeDeclarations().toString(),
                domain.validateRemoteScopeDeclarations().isEmpty());
    }

    @Test
    public void appServicesMustImplementLocalScopeServices() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity OrderAmount{ double payableAmount; }
                atomic update calculateAmount(OrderAmount amount);
                """, """
                domain commerce;
                scope{
                    local{
                        calculateAmount;
                    }
                }
                services{
                    calculateAmount{
                        amount.payableAmount = 10.0;
                    }
                }
                """);
        assertTrue(valid.validateAppServiceImplementations().isEmpty());

        SppDomain unscopedImplementation = compileDomain("""
                domain commerce;
                Entity OrderAmount{ double payableAmount; }
                atomic update calculateAmount(OrderAmount amount);
                """, """
                domain commerce;
                services{
                    calculateAmount{
                        amount.payableAmount = 10.0;
                    }
                }
                """);
        assertTrue(unscopedImplementation.validateAppServiceImplementations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity OrderAmount{ double payableAmount; }
                atomic update calculateAmount(OrderAmount amount);
                """, """
                domain commerce;
                scope{
                    remote{
                        calculateAmount;
                    }
                }
                services{
                    calculateAmount{
                        amount.payableAmount = 10.0;
                    }
                }
                """);
        List<String> errors = invalid.validateAppServiceImplementations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "APP service implementation calculateAmount must bind a local scope service"));
    }

    @Test
    public void checkServiceImplementationsMustNotWriteBusinessObjects() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int requestedQty; int safeQty; }
                Role SalesItem{ int requestedQty; int lineAmount; }
                atomic check checkInventory(Inventory inventory);
                atomic calculate calculateOrderLine(SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        checkInventory;
                        calculateOrderLine;
                    }
                }
                services{
                    checkInventory{
                        require(inventory.availableQty - inventory.requestedQty >= inventory.safeQty,
                            "inventory is below safe quantity");
                    }
                    calculateOrderLine{
                        item.lineAmount = item.requestedQty * 10;
                    }
                }
                """);
        assertTrue(valid.validateAppServiceImplementations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int requestedQty; int safeQty; }
                atomic check checkInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        checkInventory;
                    }
                }
                services{
                    checkInventory{
                        inventory.availableQty -= inventory.requestedQty;
                    }
                }
                """);
        List<String> errors = invalid.validateAppServiceImplementations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "APP check service implementation checkInventory cannot write business objects"));
        assertTrue(errors.get(0), errors.get(0).contains("inventory.availableQty"));
        assertTrue(errors.get(0), errors.get(0).contains("Move state changes to atomic update services"));
    }

    @Test
    public void checkServiceConditionExpressionsAreNotWrites() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int safeQty; }
                atomic check checkInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        checkInventory;
                    }
                }
                services{
                    checkInventory{
                        inventory.availableQty >= inventory.safeQty;
                    }
                }
                """);

        SppService checkInventory = (SppService) valid.getSppClass("CheckInventory");
        assertFalse(checkInventory.hasWriteOperations());
        assertTrue(valid.validateAppServiceImplementations().isEmpty());
    }

    @Test
    public void duplicateAppServiceImplementationsMustNotOverwriteExistingServiceBody() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int requestedQty; int safeQty; }
                atomic check checkInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        checkInventory;
                    }
                }
                services{
                    checkInventory{
                        require(inventory.availableQty - inventory.requestedQty >= inventory.safeQty,
                            "inventory is below safe quantity");
                    }
                    checkInventory{
                        inventory.availableQty -= inventory.requestedQty;
                    }
                }
                """);

        SppService checkInventory = (SppService) domain.getSppClass("CheckInventory");
        assertFalse(checkInventory.hasWriteOperations());
        List<String> errors = domain.validateAppServiceImplementations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "APP service implementation checkInventory is declared multiple times"));
    }

    @Test
    public void queryServiceImplementationsMustNotWriteBusinessObjects() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; business("skuId business credential") String skuId; }
                atomic query queryInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        queryInventory;
                    }
                }
                services{
                    queryInventory{
                        inventory.availableQty = 100;
                    }
                }
                """);
        List<String> errors = invalid.validateAppServiceImplementations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "APP query service implementation queryInventory cannot write business objects"));
        assertTrue(errors.get(0), errors.get(0).contains("inventory.availableQty"));
        assertTrue(errors.get(0), errors.get(0).contains("Move state changes to atomic update services"));
    }

    @Test
    public void appDeclarationValidationAggregatesRuntimeSemantics() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int requestedQty; int safeQty; }
                atomic check checkInventory(Inventory inventory);
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        checkInventory;
                        at resource(inventory) reserveInventory;
                        tcc resource(inventoryTcc) reserveInventory;
                    }
                }
                services{
                    checkInventory{
                        inventory.availableQty -= inventory.requestedQty;
                    }
                }
                """);

        List<String> errors = invalid.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Service reserveInventory is declared multiple times in APP scope")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP check service implementation checkInventory cannot write business objects")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "inventory.availableQty")));
    }

    @Test
    public void transactionResourcesMustBindLocalAtomicUpdateServices() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                atomic query queryInventory(Inventory inventory);
                atomic update reserveInventory(Inventory inventory);
                atomic update releaseInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        queryInventory;
                        at resource(inventory) reserveInventory;
                        tcc resource(inventoryTcc) releaseInventory;
                    }
                }
                """);
        assertTrue(valid.validateTransactionResourceBindings().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                atomic query queryInventory(Inventory inventory);
                Scenario serial submitOrder(Inventory inventory){ transaction{} }
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(inventoryRead) queryInventory;
                        at resource(orderSaga) submitOrder;
                        tcc queryInventory;
                        quantum submitOrder;
                    }
                }
                """);
        List<String> errors = invalid.validateTransactionResourceBindings();
        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Transaction resource inventoryRead on service queryInventory must bind a local atomic update service"));
        assertTrue(errors.get(1), errors.get(1).contains(
                "Transaction resource orderSaga on service submitOrder must bind a local atomic update service"));
        assertTrue(errors.get(2), errors.get(2).contains(
                "Transaction mode TCC on service queryInventory must bind a local atomic update service"));
        assertTrue(errors.get(3), errors.get(3).contains(
                "Transaction mode QUANTUM on service submitOrder must bind a local atomic update service"));

        SppDomain ambiguousResource = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic update reserveInventory(Inventory inventory);
                atomic update releaseInventory(Inventory inventory);
                atomic update captureInventory(Inventory inventory);
                atomic update restoreInventory(Inventory inventory);
                atomic update auditInventory(Inventory inventory);
                atomic update adjustInventory(Inventory inventory);
                atomic update countInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        resource(inventory) reserveInventory;
                        db resource(inventoryDb) releaseInventory;
                        resource(inventoryLate) at captureInventory;
                        at tcc resource(inventoryMixed) restoreInventory;
                        at resource(inventoryAudit) resource(inventoryAuditBackup) auditInventory;
                        at resource(inventoryAdjust, Inventory.availableQty, Inventory.lockedQty) adjustInventory;
                        db resource(inventoryCount, Inventory.availableQty, Inventory.lockedQty) countInventory;
                    }
                }
                """);
        errors = ambiguousResource.validateTransactionResourceBindings();
        assertEquals(errors.toString(), 8, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventory on service reserveInventory must declare AT, TCC, or QUANTUM mode")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryDb on service releaseInventory must declare AT, TCC, or QUANTUM mode")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryLate on service captureInventory must be declared after AT, TCC, or QUANTUM mode")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service restoreInventory declares multiple transaction modes: AT, TCC")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service auditInventory declares multiple transaction resources: inventoryAudit, inventoryAuditBackup")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryAdjust on service adjustInventory must not declare resource arguments unless it is TCC or QUANTUM: Inventory.availableQty, Inventory.lockedQty")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryCount on service countInventory must not declare resource arguments unless it is TCC or QUANTUM: Inventory.availableQty, Inventory.lockedQty")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryCount on service countInventory must declare AT, TCC, or QUANTUM mode")));
    }

    @Test
    public void explicitDbTransactionModeMustBeOmitted() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic query queryInventory(Inventory inventory);
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        queryInventory;
                        reserveInventory;
                    }
                }
                """);
        assertTrue(valid.validateTransactionResourceBindings().isEmpty());

        SppDomain explicitDb = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic query queryInventory(Inventory inventory);
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        db queryInventory;
                        db reserveInventory;
                    }
                }
                """);
        List<String> errors = explicitDb.validateTransactionResourceBindings();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service queryInventory declares unsupported DB transaction mode; omit the modifier or use AT, TCC, QUANTUM, or SAGA")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Scope service reserveInventory declares unsupported DB transaction mode; omit the modifier or use AT, TCC, QUANTUM, or SAGA")));
    }

    @Test
    public void transactionResourceArgumentsMustFollowExplicitTransactionMode() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ quantum int availableQty; quantum int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                atomic update reserveInventoryTcc(Inventory inventory);
                atomic update confirmInventory(Inventory inventory);
                atomic update cancelInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        resource(inventoryAccounting, Inventory.availableQty, Inventory.lockedQty)
                            quantum reserveInventory;
                        resource(inventoryTcc, confirmInventory, cancelInventory)
                            tcc reserveInventoryTcc;
                    }
                }
                """);

        List<String> errors = invalid.validateTransactionResourceBindings();
        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryAccounting on service reserveInventory must be declared after AT, TCC, or QUANTUM mode")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryAccounting on service reserveInventory must declare AT, TCC, or QUANTUM before resource arguments: Inventory.availableQty, Inventory.lockedQty")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryTcc on service reserveInventoryTcc must be declared after AT, TCC, or QUANTUM mode")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction resource inventoryTcc on service reserveInventoryTcc must declare AT, TCC, or QUANTUM before resource arguments: confirmInventory, cancelInventory")));
    }

    @Test
    public void transactionResourceIdsMustUseStableModesAndQuantumAttributeSets() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                        quantum resource(creditAccounting, OrderAmount.occupiedAmount, CreditAccount.availableCredit)
                            releaseCredit;
                    }
                }
                """);
        assertTrue(valid.validateTransactionResourceModes().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{
                    quantum double occupiedAmount;
                    quantum double capturedAmount;
                }
                Role Inventory{ int availableQty; int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                atomic update releaseInventory(Inventory inventory);
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update captureCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(inventory) reserveInventory;
                        tcc resource(inventory) releaseInventory;
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                        quantum resource(creditAccounting, OrderAmount.occupiedAmount, OrderAmount.capturedAmount)
                            captureCredit;
                    }
                }
                """);
        List<String> errors = invalid.validateTransactionResourceModes();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Resource inventory uses multiple transaction modes: AT in reserveInventory and TCC in releaseInventory"));
        assertTrue(errors.get(1), errors.get(1).contains(
                "Quantum resource creditAccounting uses multiple conserved attribute sets"));
        assertTrue(errors.get(1), errors.get(1).contains("reserveCredit"));
        assertTrue(errors.get(1), errors.get(1).contains("captureCredit"));
        // Deployment defaults select a resource, not a fixed conservation group.
        for (var scope : invalid.getRuleBlock().getAppScope().getScopelist()) {
            for (var item : scope.getScopeItems()) {
                if (item.getTransactionType() == com.servpp.lang.general.IConstance.TransactionType.quantum)
                    item.setQuantumResourceInferred(true);
            }
        }
        assertEquals(invalid.validateTransactionResourceModes().toString(), 1,
                invalid.validateTransactionResourceModes().size());
    }

    @Test
    public void declaredQuantumServicesMustUseQuantumAppMode() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditAccounting) reserveCredit;
                        tcc releaseCredit;
                    }
                }
                """);

        List<String> errors = invalid.validateTransactionResourceBindings();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum service reserveCredit must be declared with QUANTUM transaction mode in APP scope")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum service releaseCredit must be declared with QUANTUM transaction mode in APP scope")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                """);
        assertTrue(valid.validateTransactionResourceBindings().isEmpty());
    }

    @Test
    public void quantumResourceMustBindAtomicUpdateAndWriteDeclaredAttributes() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                """);
        assertTrue(valid.validateQuantumConservationDeclarations().isEmpty());

        SppDomain missingWrite = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                    }
                }
                """);
        List<String> errors = missingWrite.validateQuantumConservationDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Quantum service reserveCredit must both increase and decrease declared quantum attributes"));

        SppDomain noQuantumWrite = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                """);
        errors = noQuantumWrite.validateQuantumConservationDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Quantum service reserveCredit must write declared quantum attributes in resource creditAccounting"));

        SppDomain duplicateAttribute = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double payableAmount; }
                quantum update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, CreditAccount.availableCredit)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= account.payableAmount;
                    }
                }
                """);
        errors = duplicateAttribute.validateQuantumConservationDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum resource creditAccounting must declare at least two distinct quantum attributes")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum resource creditAccounting declares duplicate quantum attribute CreditAccount.availableCredit")));

        SppDomain singleAttribute = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double payableAmount; }
                quantum update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= account.payableAmount;
                    }
                }
                """);
        errors = singleAttribute.validateQuantumConservationDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Quantum resource creditAccounting must declare at least two distinct quantum attributes"));

        SppDomain queryResource = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic query queryCredit(CreditAccount account);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            queryCredit;
                    }
                }
                """);
        errors = queryResource.validateQuantumConservationDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Quantum resource creditAccounting on service queryCredit must bind a local atomic update service"));

        SppDomain inaccessibleAttribute = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double payableAmount; }
                Role OrderAmount{ quantum double occupiedAmount; }
                quantum update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= account.payableAmount;
                    }
                }
                """);
        errors = inaccessibleAttribute.validateQuantumConservationDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum resource creditAccounting declares inaccessible attribute OrderAmount.occupiedAmount on service reserveCredit")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "service parameters do not include a scalar or one-dimensional OrderAmount object")));
    }

    @Test
    public void quantumResourcesMustStayWithinOneAppQuantumStore() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double payableAmount; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore creditQuantum{
                    attribute CreditAccount.availableCredit;
                }
                quantumStore orderQuantum{
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= account.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                """);

        List<String> errors = domain.validateQuantumConservationDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Quantum resource creditAccounting declares attributes across multiple APP quantum stores"));
        assertTrue(errors.get(0), errors.get(0).contains(
                "creditQuantum=[CreditAccount.availableCredit]"));
        assertTrue(errors.get(0), errors.get(0).contains(
                "orderQuantum=[OrderAmount.occupiedAmount]"));
    }

    @Test
    public void quantumResourceAssignmentsMustExposeConservationDirectionsWhenKnown() throws Exception {
        SppDomain sameDirection = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double adjustment; }
                Role OrderAmount{ quantum double occupiedAmount; double adjustment; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit += account.adjustment;
                        amount.occupiedAmount += amount.adjustment;
                    }
                }
                """);
        List<String> errors = sameDirection.validateQuantumConservationDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Quantum service reserveCredit must both increase and decrease declared quantum attributes in resource creditAccounting"));

        SppDomain sameDirectionAssignment = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double adjustment; }
                Role OrderAmount{ quantum double occupiedAmount; double adjustment; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit = account.availableCredit + account.adjustment;
                        amount.occupiedAmount = amount.occupiedAmount + amount.adjustment;
                    }
                }
                """);
        errors = sameDirectionAssignment.validateQuantumConservationDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Quantum service reserveCredit must both increase and decrease declared quantum attributes in resource creditAccounting"));

        SppDomain complexAssignment = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double adjustment; }
                Role OrderAmount{ quantum double occupiedAmount; double adjustment; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit = account.availableCredit - account.adjustment;
                        amount.occupiedAmount = amount.occupiedAmount + amount.adjustment;
                    }
                }
                """);
        assertTrue(complexAssignment.validateQuantumConservationDeclarations().isEmpty());

        SppDomain resetToZero = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; double adjustment; }
                Role OrderAmount{ quantum double occupiedAmount; double adjustment; }
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting, CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            releaseCredit;
                    }
                }
                services{
                    releaseCredit{
                        account.availableCredit += amount.occupiedAmount;
                        amount.occupiedAmount = 0.0;
                    }
                }
                """);
        assertTrue(resetToZero.validateQuantumConservationDeclarations().isEmpty());
    }

    @Test
    public void tccResourceMustReferenceCompatibleLocalLifecycleServices() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Role Inventory{ business("skuId business credential") String skuId; int availableQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update deductReservedInventory(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey(Inventory.skuId);
                        null int lockedQty;
                    }
                }
                scope{
                    local{
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            deductReservedInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            releaseReservedInventory;
                    }
                }
        """);
        assertTrue(valid.validateTccResourceDeclarations().isEmpty());

        SppDomain missingTrySnapshot = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update deductReservedInventory(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            reserveInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            deductReservedInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            releaseReservedInventory;
                    }
                }
                """);
        List<String> errors = missingTrySnapshot.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "TCC try service reserveInventory must declare argument object snapshot rules"));

        SppDomain missingLifecycleSnapshot = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update deductReservedInventory(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                        tcc resource(inventoryTcc) deductReservedInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            releaseReservedInventory;
                    }
                }
                """);
        errors = missingLifecycleSnapshot.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "TCC confirm service deductReservedInventory referenced by reserveInventory must declare argument object snapshot rules"));

        SppDomain mismatchedLifecycleSnapshot = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                Role SalesItem{ int requestedQty; int itemNo; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update deductReservedInventory(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.lockedQty))
                            deductReservedInventory;
                        tcc resource(inventoryTcc) snapshot(argument(1, SalesItem.itemNo))
                            releaseReservedInventory;
                    }
                }
                """);
        errors = mismatchedLifecycleSnapshot.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC confirm service deductReservedInventory referenced by reserveInventory must declare matching argument object snapshot rule argument(0, Inventory.availableQty)")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC cancel service releaseReservedInventory referenced by reserveInventory must declare matching argument object snapshot rule argument(0, Inventory.availableQty)")));

        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update reserveInventoryWithMissingLifecycle(Inventory inventory, SalesItem item);
                atomic update reserveInventoryWithOtherLifecycleResource(Inventory inventory, SalesItem item);
                atomic update confirmOtherResource(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                atomic query queryInventory(Inventory inventory);
                atomic update cancelWithWrongSignature(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc, missingConfirm, releaseReservedInventory)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventoryWithMissingLifecycle;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            releaseReservedInventory;
                        tcc resource(inventoryTcc, confirmOtherResource, releaseReservedInventory)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventoryWithOtherLifecycleResource;
                        tcc resource(otherTcc) confirmOtherResource;
                        tcc resource(inventoryTcc, queryInventory, cancelWithWrongSignature)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                        tcc resource(inventoryTcc) cancelWithWrongSignature;
                    }
                }
                """);
        errors = invalid.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 4, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "TCC confirm service missingConfirm referenced by reserveInventoryWithMissingLifecycle not found"));
        assertTrue(errors.get(1), errors.get(1).contains(
                "TCC confirm service confirmOtherResource referenced by reserveInventoryWithOtherLifecycleResource must be declared on the same TCC resource inventoryTcc"));
        assertTrue(errors.get(2), errors.get(2).contains(
                "TCC confirm service queryInventory referenced by reserveInventory must be a local atomic update service"));
        assertTrue(errors.get(3), errors.get(3).contains(
                "TCC cancel service cancelWithWrongSignature for reserveInventory must accept the same parameter list"));

        SppDomain duplicateLifecycle = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update finalizeInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc, finalizeInventory, finalizeInventory)
                            reserveInventory;
                        tcc resource(inventoryTcc) finalizeInventory;
                    }
                }
                """);
        errors = duplicateLifecycle.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "TCC resource inventoryTcc on service reserveInventory must declare different confirm and cancel services"));

        SppDomain lifecycleFieldPaths = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc, Inventory.availableQty, Inventory.lockedQty)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);
        errors = lifecycleFieldPaths.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC resource inventoryTcc on service reserveInventory lifecycle service Inventory.availableQty must be a service name, not a field path")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC resource inventoryTcc on service reserveInventory lifecycle service Inventory.lockedQty must be a service name, not a field path")));
    }

    @Test
    public void tccReservationRuleBelongsToTccResourceService() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update deductReservedInventory(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey(Inventory.availableQty);
                        null int lockedQty;
                    }
                }
                scope{
                    local{
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            reservation(Inventory.availableQty -> Inventory.lockedQty)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            deductReservedInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            releaseReservedInventory;
                    }
                }
                """);
        ScopeItem reserveInventory = valid.getRuleBlock().getAppScope().getScopelist().get(0)
                .getScopeItems().get(0);
        assertEquals(1, reserveInventory.getTccReservationRules().size());
        TccReservationRule rule = reserveInventory.getTccReservationRules().get(0);
        assertEquals("Inventory.availableQty", rule.sourceAttributeId());
        assertEquals("Inventory.lockedQty", rule.reservationAttributeId());
        assertTrue(valid.validateTccResourceDeclarations().isEmpty());

        SppDomain businessFieldTarget = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc)
                            reservation(Inventory.availableQty -> Inventory.lockedQty)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);
        List<String> errors = businessFieldTarget.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC reservation target attribute Inventory.lockedQty")
                && error.contains("must be declared as an APP DDL platform column")));

        SppDomain appDdlPlatformColumnTarget = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update deductReservedInventory(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey(Inventory.availableQty);
                        null int lockedQty;
                    }
                }
                scope{
                    local{
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            reservation(Inventory.availableQty -> Inventory.lockedQty)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            deductReservedInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            releaseReservedInventory;
                    }
                }
                """);
        errors = appDdlPlatformColumnTarget.validateTccResourceDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());

        SppDomain crossTypeReservation = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                Entity CreditAccount{ double availableCredit; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey(CreditAccount.availableCredit);
                        null double usedCredit;
                    }
                }
                scope{
                    local{
                        tcc resource(inventoryTcc)
                            reservation(Inventory.availableQty -> CreditAccount.usedCredit)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);
        errors = crossTypeReservation.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC reservation Inventory.availableQty -> CreditAccount.usedCredit"
                        + " must keep source and target on the same business type")));

        SppDomain primaryKeyReservationTarget = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                    }
                }
                scope{
                    local{
                        tcc resource(inventoryTcc)
                            reservation(Inventory.availableQty -> Inventory.inventoryId)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);
        errors = primaryKeyReservationTarget.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC reservation target attribute Inventory.inventoryId"
                        + " must not be an APP DDL primary key column")));

        SppDomain mismatchedReservationTypes = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey(Inventory.availableQty);
                        null String lockedQty;
                    }
                }
                scope{
                    local{
                        tcc resource(inventoryTcc)
                            reservation(Inventory.availableQty -> Inventory.lockedQty)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);
        errors = mismatchedReservationTypes.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC reservation Inventory.availableQty -> Inventory.lockedQty"
                        + " must use matching attribute types, but source is int and target is String")));

        SppDomain quantumReservationTarget = compileDomain("""
                domain commerce;
                Role Inventory{ quantum int availableQty; quantum int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc)
                            reservation(Inventory.availableQty -> Inventory.lockedQty)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);
        errors = quantumReservationTarget.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "TCC reservation target attribute Inventory.lockedQty must not refer to a quantum field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "reservation state belongs to APP DDL platform columns or TCC platform state")));

        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey(Inventory.availableQty);
                        null int lockedQty;
                    }
                }
                scope{
                    local{
                        at resource(inventory)
                            reservation(Inventory.availableQty -> Inventory.lockedQty)
                            reserveInventory;
                    }
                }
                """);
        errors = invalid.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "TCC reservation Inventory.availableQty -> Inventory.lockedQty on service reserveInventory must be declared on a TCC resource service"));
    }

    @Test
    public void tccResourceWithoutLifecycleMustBeReferencedByTryService() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                atomic update reserveInventory(Inventory inventory);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                    }
                }
                """);

        List<String> errors = invalid.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "TCC resource inventoryTcc on service reserveInventory must declare confirm and cancel services or be referenced as a TCC lifecycle service"));
    }

    @Test
    public void tccLifecycleServicesMustNotDeclareNestedLifecycleServices() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Role Inventory{ int availableQty; int lockedQty; }
                Role SalesItem{ int requestedQty; }
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                atomic update deductReservedInventory(Inventory inventory, SalesItem item);
                atomic update finalizeDeduction(Inventory inventory, SalesItem item);
                atomic update rollbackDeduction(Inventory inventory, SalesItem item);
                atomic update releaseReservedInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        tcc resource(inventoryTcc, deductReservedInventory, releaseReservedInventory)
                            snapshot(argument(0, Inventory.availableQty))
                            reserveInventory;
                        tcc resource(inventoryTcc, finalizeDeduction, rollbackDeduction)
                            snapshot(argument(0, Inventory.availableQty))
                            deductReservedInventory;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            finalizeDeduction;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            rollbackDeduction;
                        tcc resource(inventoryTcc) snapshot(argument(0, Inventory.availableQty))
                            releaseReservedInventory;
                    }
                }
                """);

        List<String> errors = invalid.validateTccResourceDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "TCC confirm service deductReservedInventory referenced by reserveInventory cannot declare nested TCC lifecycle services"));
    }

    @Test
    public void snapshotRulesCanDeclarePersistenceTiming() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(before, argument(0, CreditAccount.accountNo))
                            snapshot(commit, current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);

        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(2, rules.size());
        assertEquals(ObjectSnapshotRule.Timing.before, rules.get(0).timing());
        assertEquals(ObjectSnapshotRule.Timing.commit, rules.get(1).timing());
    }

    @Test
    public void snapshotRulesCanDeclareTransactionEvidence() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; unique String messageId; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(transaction)
                            snapshot(before, transaction)
                            snapshot(argument(0, CreditAccount.accountNo)) reserveCredit;
                    }
                }
                """);

        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(3, rules.size());
        assertEquals(ObjectSnapshotRule.Source.transaction, rules.get(0).source());
        assertEquals("TransactionSnapshot", rules.get(0).getObjectType());
        assertEquals("globalSerialNumber", rules.get(0).getObjectIdPath());
        assertEquals(ObjectSnapshotRule.Timing.afterCommit, rules.get(0).timing());
        assertEquals(ObjectSnapshotRule.Source.transaction, rules.get(1).source());
        assertEquals(ObjectSnapshotRule.Timing.before, rules.get(1).timing());
        assertFalse(rules.get(2).appDdlInferred());
        assertTrue(domain.validateSnapshotDeclarations().isEmpty());

        String java = reserveCredit.getService().getObjectSnapshotRulesMethod();
        assertTrue(java, java.contains("ObjectSnapshotRule.transaction()"));
        assertTrue(java, java.contains("ObjectSnapshotRule.transaction(ObjectSnapshotRule.Timing.BEFORE)"));
        assertTrue(java, java.contains("ObjectSnapshotRule.argument(0, \"CreditAccount\", \"accountNo\""));
    }

    @Test
    public void transactionSnapshotRequiresTraceHeader() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                header baseIn{ unique String requestId; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(transaction)
                            snapshot(argument(0, CreditAccount.accountNo)) reserveCredit;
                    }
                }
                """);

        List<String> errors = invalid.validateSnapshotDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Transaction snapshot rule on service reserveCredit"
                        + " must use an input header with unique globalSerialNumber or messageId")));

        SppDomain inheritedTrace = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                header traceIn{ unique String messageId; }
                header baseIn extends traceIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(transaction)
                            snapshot(argument(0, CreditAccount.accountNo)) reserveCredit;
                    }
                }
                """);

        assertTrue(inheritedTrace.validateSnapshotDeclarations().toString(),
                inheritedTrace.validateSnapshotDeclarations().isEmpty());
    }

    @Test
    public void remoteScopeKeepsProtocolHeadersAndRejectsLocalRuntimeRules() throws Exception {
        SppDomain remoteOnly = compileDomain("""
                domain pricing;
                Entity Product{ business("productId business credential") String productId; }
                Entity PriceQuote{ business("quoteId business credential") String quoteId; double amount; }
                atomic query queryPricing(Product product, PriceQuote priceQuote);
                """, """
                domain pricing;
                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "pricing"); }
                scope{
                    remote (in baseIn, out baseOut){
                        queryPricing;
                    }
                }
                """);

        ScopeItem remoteItem = remoteOnly.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        assertFalse(remoteItem.isLocal());
        assertEquals("baseIn", remoteItem.getScopeDefine().getIn().getName());
        assertEquals("baseOut", remoteItem.getScopeDefine().getOut().getName());
        assertTrue(remoteOnly.validateRemoteScopeDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain pricing;
                Entity Product{ business("productId business credential") String productId; quantum double availableQty; }
                Entity PriceQuote{ business("quoteId business credential") String quoteId; quantum double amount; }
                Entity PriceBudget{ business("budgetId business credential") String budgetId; quantum double availableAmount; }
                atomic query queryPricing(Product product, PriceQuote priceQuote);
                atomic update auditPricing(PriceQuote quote);
                atomic update updatePricing(PriceQuote quote);
                quantum update reservePricing(PriceQuote quote, PriceBudget budget);
                atomic update notifyPricing(PriceQuote quote);
                atomic update confirmPricing(PriceQuote quote);
                atomic update cancelPricing(PriceQuote quote);
                atomic update refundPricing(PriceQuote quote);
                atomic update releasePricing(PriceQuote quote);
                """, """
                domain pricing;
                scope{
                    remote{
                        repository(argument(0, Product.productId), product) queryPricing;
                        snapshot(argument(0, PriceQuote.quoteId)) auditPricing;
                        at resource(pricingDb) updatePricing;
                        quantum resource(priceAccounting, PriceQuote.amount, PriceBudget.availableAmount)
                            reservePricing;
                        tcc resource(priceTcc, confirmPricing, cancelPricing) notifyPricing;
                        compensate(releasePricing) refundPricing;
                    }
                }
                """);

        List<String> errors = invalid.validateRemoteScopeDeclarations();
        assertEquals(6, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service queryPricing cannot declare repository hydration rules")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service auditPricing cannot declare object snapshot rules")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service refundPricing cannot declare compensation rules")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service updatePricing cannot declare local transaction mode or resource")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service reservePricing cannot declare local transaction mode or resource")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service notifyPricing cannot declare local transaction mode or resource")));
    }

    @Test
    public void duplicateRemoteScopeItemsStillRejectLocalRuntimeRules() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        queryCustomer;
                        updateCustomer;
                    }
                    remote{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                        at resource(customerDb) updateCustomer;
                    }
                }
                """);

        List<String> scopeErrors = invalid.validateUniqueScopeServiceDeclarations();
        assertEquals(scopeErrors.toString(), 2, scopeErrors.size());

        List<String> remoteErrors = invalid.validateRemoteScopeDeclarations();
        assertEquals(remoteErrors.toString(), 2, remoteErrors.size());
        assertTrue(remoteErrors.toString(), remoteErrors.stream().anyMatch(error -> error.contains(
                "Remote service queryCustomer cannot declare repository hydration rules")));
        assertTrue(remoteErrors.toString(), remoteErrors.stream().anyMatch(error -> error.contains(
                "Remote service updateCustomer cannot declare local transaction mode or resource")));
    }

    @Test
    public void remoteCompensationMustUseExplicitRemoteScopeServices() throws Exception {
        SppDomain valid = compileDomain("""
                domain pricing;
                Entity PriceQuote{ business("quoteId business credential") String quoteId; double amount; }
                atomic update reservePricing(PriceQuote quote);
                atomic update releasePricing(PriceQuote quote);
                """, """
                domain pricing;
                scope{
                    remote{
                        remoteCompensate(releasePricing) reservePricing;
                        releasePricing;
                    }
                }
                """);

        ScopeItem reservePricing = valid.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);

        assertTrue(valid.validateRemoteCompensationDeclarations().isEmpty());
        assertEquals(List.of("releasePricing"), reservePricing.getRemoteCompensationServiceNames());
        assertTrue(reservePricing.getCompensationServiceNames().isEmpty());
        SppService reservePricingService = (SppService) valid.getSppClass("ReservePricing");
        assertTrue(reservePricingService.getRemoteCompensationImports()
                .contains("RemoteCompensationRuleProvider"));
        assertEquals(", RemoteCompensationRuleProvider",
                reservePricingService.getRemoteCompensationInterface());
        assertTrue(reservePricingService.getRemoteCompensationServiceNamesMethod()
                .contains("return List.of(\"releasePricing\")"));
        assertFalse(reservePricingService.getRemoteCompensationImports()
                .contains("com.servpp.service.transaction.CompensationRuleProvider"));
        assertEquals("", reservePricingService.getCompensationInterface());
        assertEquals("", reservePricingService.getCompensationServiceNamesMethod());

        SppDomain invalid = compileDomain("""
                domain pricing;
                Entity PriceQuote{ business("quoteId business credential") String quoteId; double amount; }
                atomic query queryPricing(PriceQuote quote);
                atomic update reservePricing(PriceQuote quote);
                atomic update reserveMissingPricing(PriceQuote quote);
                atomic update reserveSelfPricing(PriceQuote quote);
                atomic update reserveLocalPricing(PriceQuote quote);
                atomic update reserveNestedPricing(PriceQuote quote);
                atomic update reserveMismatchedPricing(PriceQuote quote);
                atomic update reserveDuplicatePricing(PriceQuote quote);
                atomic update releasePricing(PriceQuote quote);
                atomic update localReleasePricing(PriceQuote quote);
                atomic update nestedReleasePricing(PriceQuote quote);
                atomic update mismatchedReleasePricing();
                """, """
                domain pricing;
                scope{
                    local{
                        remoteCompensate(releasePricing) localReleasePricing;
                    }
                    remote{
                        remoteCompensate(releasePricing) queryPricing;
                        remoteCompensate(missingReleasePricing) reserveMissingPricing;
                        remoteCompensate(reserveSelfPricing) reserveSelfPricing;
                        remoteCompensate(localReleasePricing) reserveLocalPricing;
                        remoteCompensate(nestedReleasePricing) reserveNestedPricing;
                        remoteCompensate(mismatchedReleasePricing) reserveMismatchedPricing;
                        remoteCompensate(releasePricing, releasePricing) reserveDuplicatePricing;
                        remoteCompensate(releasePricing) nestedReleasePricing;
                        mismatchedReleasePricing;
                        releasePricing;
                    }
                }
                """);

        List<String> errors = invalid.validateRemoteCompensationDeclarations();

        assertEquals(errors.toString(), 8, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation rule on service localReleasePricing must be declared in remote scope")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation rule on service queryPricing must be declared on a remote atomic update service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation service missingReleasePricing referenced by reserveMissingPricing not found")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation service reserveSelfPricing cannot compensate itself")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation service localReleasePricing referenced by reserveLocalPricing must be a remote atomic update service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation service nestedReleasePricing referenced by reserveNestedPricing cannot declare nested remote compensation services")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation service mismatchedReleasePricing for reserveMismatchedPricing must accept the same parameter list")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote compensation rule on service reserveDuplicatePricing declares duplicate remote compensation service releasePricing")));
    }

    @Test
    public void repositoryHydrationMustBindLocalAtomicQueryService() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """);
        assertTrue(valid.validateRepositoryHydrationDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                Scenario serial submitCustomer(Customer customer){ transaction{} }
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) updateCustomer;
                        repository(argument(0, Customer.customerId), customer) submitCustomer;
                    }
                }
                """);
        List<String> errors = invalid.validateRepositoryHydrationDeclarations();
        assertEquals(2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository hydration service updateCustomer must be a local atomic query service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository hydration service submitCustomer must be a local atomic query service")));

        SppDomain remoteRepository = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """);
        errors = remoteRepository.validateRepositoryHydrationDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Repository hydration service queryCustomer must be a local atomic query service"));
    }

    @Test
    public void repositoryHydrationCannotTargetReferenceObjects() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """);

        List<String> errors = invalid.validateRepositoryHydrationDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Repository hydration service queryCustomer targets Reference Customer"));
        assertTrue(errors.get(0), errors.get(0).contains(
                "declare it in remote scope and bind a PHY remote adapter"));

        SppDomain remote = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """);
        assertTrue(remote.validateRepositoryHydrationDeclarations().toString(),
                remote.validateRepositoryHydrationDeclarations().isEmpty());
    }

    @Test
    public void repositoryHydrationCanBindPhysicalRepositoryAdapter() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) using objectStore queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());
        RepositoryHydrationRule rule = valid.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0).getRepositoryHydrationRules().get(0);
        assertEquals("objectStore", rule.repositoryName());
        assertFalse(rule.appDdlInferred());

        SppDomain missing = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) using missingStore queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        List<String> missingErrors = missing.validatePhysicalRuntimeDeclarations();
        assertEquals(missingErrors.toString(), 1, missingErrors.size());
        assertTrue(missingErrors.get(0), missingErrors.get(0).contains(
                "Repository hydration rule for response target customer"
                        + " references undefined PHY repository adapter missingStore"));

        SppDomain ambiguous = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                    repository customerStore{
                        adapter objectRepository;
                    }
                }
                """);
        List<String> ambiguousErrors = ambiguous.validatePhysicalRuntimeDeclarations();
        assertEquals(ambiguousErrors.toString(), 1, ambiguousErrors.size());
        assertTrue(ambiguousErrors.get(0), ambiguousErrors.get(0).contains(
                "Repository hydration rule for response target customer"
                        + " must declare using <repository> when no unique default PHY repository adapter exists"));

        SppDomain defaultRepository = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                        config default = true;
                    }
                    repository auditStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(defaultRepository.validatePhysicalRuntimeDeclarations().toString(),
                defaultRepository.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain defaultRepositoryPlaceholder = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                        config default = "${spp.repository.default:true}";
                    }
                    repository auditStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(defaultRepositoryPlaceholder.validatePhysicalRuntimeDeclarations().toString(),
                defaultRepositoryPlaceholder.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalidDefault = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                        config default = maybe;
                    }
                }
                """);
        List<String> invalidDefaultErrors = invalidDefault.validatePhysicalRuntimeDeclarations();
        assertEquals(invalidDefaultErrors.toString(), 1, invalidDefaultErrors.size());
        assertTrue(invalidDefaultErrors.get(0), invalidDefaultErrors.get(0).contains(
                "PHY repository adapter objectStore objectRepository config default must be true or false"));
    }

    @Test
    public void objectSnapshotCanBindPhysicalRepositoryAdapter() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) using objectStore updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                    repository auditStore{
                        adapter objectRepository;
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());
        ObjectSnapshotRule rule = valid.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0).getObjectSnapshotRules().get(0);
        assertEquals("objectStore", rule.repositoryName());
        String java = ((SppService) valid.getSppClass("UpdateCustomer")).getObjectSnapshotRulesMethod();
        assertTrue(java, java.contains("ObjectSnapshotRule.Timing.AFTER_COMMIT, \"objectStore\""));

        SppDomain missing = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) using missingStore updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        List<String> missingErrors = missing.validatePhysicalRuntimeDeclarations();
        assertEquals(missingErrors.toString(), 1, missingErrors.size());
        assertTrue(missingErrors.get(0), missingErrors.get(0).contains(
                "Object snapshot rule for object type Customer"
                        + " references undefined PHY repository adapter missingStore"));

        SppDomain missingAll = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) using objectStore updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                }
                """);
        List<String> missingAllErrors = missingAll.validatePhysicalRuntimeDeclarations();
        assertEquals(missingAllErrors.toString(), 1, missingAllErrors.size());
        assertTrue(missingAllErrors.get(0), missingAllErrors.get(0).contains(
                "Object snapshot rule for object type Customer"
                        + " references undefined PHY repository adapter objectStore"));

        SppDomain ambiguous = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                    repository auditStore{
                        adapter objectRepository;
                    }
                }
                """);
        List<String> ambiguousErrors = ambiguous.validatePhysicalRuntimeDeclarations();
        assertEquals(ambiguousErrors.toString(), 1, ambiguousErrors.size());
        assertTrue(ambiguousErrors.get(0), ambiguousErrors.get(0).contains(
                "Object snapshot rule for object type Customer"
                        + " must declare using <repository> when no unique default PHY repository adapter exists"));

        SppDomain defaultRepository = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                        config default = true;
                    }
                    repository auditStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(defaultRepository.validatePhysicalRuntimeDeclarations().toString(),
                defaultRepository.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain defaultRepositoryPlaceholder = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                        config default = "${spp.repository.default:true}";
                    }
                    repository auditStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(defaultRepositoryPlaceholder.validatePhysicalRuntimeDeclarations().toString(),
                defaultRepositoryPlaceholder.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain duplicateDefault = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                        config default = true;
                    }
                    repository auditStore{
                        adapter objectRepository;
                        config default = true;
                    }
                }
                """);
        List<String> duplicateDefaultErrors = duplicateDefault.validatePhysicalRuntimeDeclarations();
        assertTrue(duplicateDefaultErrors.toString(), duplicateDefaultErrors.stream().anyMatch(error ->
                error.contains("PHY repository adapters declare multiple defaults: objectStore, auditStore")));

        SppDomain duplicateDefaultPlaceholders = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic update updateCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(customerTx) snapshot(argument(0, Customer.customerId)) updateCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                        config default = "${spp.repository.object.default:true}";
                    }
                    repository auditStore{
                        adapter objectRepository;
                        config default = "${spp.repository.audit.default:true}";
                    }
                }
                """);
        List<String> duplicateDefaultPlaceholderErrors =
                duplicateDefaultPlaceholders.validatePhysicalRuntimeDeclarations();
        assertTrue(duplicateDefaultPlaceholderErrors.toString(),
                duplicateDefaultPlaceholderErrors.stream().anyMatch(error ->
                        error.contains("PHY repository adapters declare multiple defaults: objectStore, auditStore")));
    }

    @Test
    public void repositoryHydrationCanInferAppDdlPrimaryKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity Store{ String name; }
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role Inventory{ quantum int availableQty; int safeQty; }
                take Store, Goods as Inventory;
                Role CreditAccount{ quantum double availableCredit; }
                take Customer as CreditAccount;
                atomic query queryGoods(Goods goods);
                atomic query queryInventory(Inventory inventory);
                atomic query queryCreditAccount(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table STORE(Store){ primarykey(Store.name); }
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_skuid;
                    }
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey notnull String customer_customerid;
                    }
                }
                scope{
                    local{
                        repository(Goods) queryGoods;
                        repository(Inventory) queryInventory;
                        repository(CreditAccount) queryCreditAccount;
                    }
                }
                """);

        List<ScopeItem> items = domain.getRuleBlock().getAppScope().getScopelist().get(0).getScopeItems();
        RepositoryHydrationRule goodsRule = items.get(0).getRepositoryHydrationRules().get(0);
        assertEquals("goods", goodsRule.responseMethodName());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(0, "Goods.skuId")),
                goodsRule.keyTargets());
        assertTrue(goodsRule.appDdlInferred());
        assertEquals("APP_DDL_PRIMARY_KEY", goodsRule.sourceHint());
        assertTrue(items.get(0).getRepositoryHydrationRules().get(0).appDdlInferred());

        RepositoryHydrationRule inventoryRule = items.get(1).getRepositoryHydrationRules().get(0);
        assertEquals("inventory", inventoryRule.responseMethodName());
        assertEquals(List.of(
                        new RepositoryHydrationRule.KeyTarget(0, "Store.name", "Inventory.store_name"),
                        new RepositoryHydrationRule.KeyTarget(0, "Goods.skuId", "Inventory.goods_skuid")),
                inventoryRule.keyTargets());
        assertTrue(inventoryRule.appDdlInferred());
        assertEquals("APP_DDL_ROLE_TAKE_PRIMARY_KEY", inventoryRule.sourceHint());

        RepositoryHydrationRule creditAccountRule = items.get(2).getRepositoryHydrationRules().get(0);
        assertEquals("account", creditAccountRule.responseMethodName());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(0,
                        "Customer.customerId", "CreditAccount.customer_customerid")),
                creditAccountRule.keyTargets());
        assertTrue(creditAccountRule.appDdlInferred());
        assertEquals("APP_DDL_ROLE_TAKE_PRIMARY_KEY", creditAccountRule.sourceHint());
    }

    @Test
    public void snapshotRulesCanInferRoleTakeAppDdlPrimaryKeys() throws Exception {
        SppDomain domain = compileDomainWithRepository("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Role CreditAccount{ quantum double availableCredit; }
                take Customer as CreditAccount;
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables roles{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey notnull String customer_customerid;
                    }
                }
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(creditAccounting)
                            snapshot(CreditAccount)
                            reserveCredit;
                    }
                }
                """, """
                domain commerce;
                phy {
                    quantumStore globalQuantum {
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:role-key-quantum";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        assertTrue(domain.validateAppDeclarations().toString(),
                domain.validateAppDeclarations().isEmpty());
        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("CreditAccount.customerId", rules.get(0).objectPath());
        assertEquals(List.of("customerId"), rules.get(0).objectIdPaths());
        assertTrue(rules.get(0).appDdlInferred());
    }

    @Test
    public void repositoryHydrationCanInferAppDdlForeignKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoodsByProduct(Product product, Goods[] goods);
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                    }
                }
                scope{
                    local{
                        repository(Goods by Product) queryGoodsByProduct;
                    }
                }
                """);

        ScopeItem queryGoodsByProduct = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryGoodsByProduct.getRepositoryHydrationRules().get(0);
        assertEquals("goods", rule.responseMethodName());
        assertEquals("Goods", rule.getObjectType());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(
                        0, "Product.productId", "Goods.product.productId")),
                rule.keyTargets());
        assertEquals("APP_DDL_FOREIGN_KEY", rule.sourceHint());
    }

    @Test
    public void repositoryHydrationCanInferRoleTakeIndexesFromRoleParameter() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; String title; }
                take Product as Goods;
                atomic query queryGoodsByProduct(Goods selection, Goods[] goods);
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){ primarykey String productId; }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        null String product_productid;
                        index goodsByProduct(Goods.product_productid);
                    }
                }
                scope{
                    local{
                        repository(Goods by Product) queryGoodsByProduct;
                    }
                }
                """);

        ScopeItem queryGoodsByProduct = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryGoodsByProduct.getRepositoryHydrationRules().get(0);
        assertTrue(domain.validateRepositoryHydrationDeclarations().toString(),
                domain.validateRepositoryHydrationDeclarations().isEmpty());
        assertEquals("goods", rule.responseMethodName());
        assertEquals("Goods", rule.getObjectType());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(
                        0, "Product.productId", "Goods.product_productid")),
                rule.keyTargets());
        assertTrue(rule.appDdlInferred());
        assertEquals("APP_DDL_ROLE_TAKE", rule.sourceHint());
    }

    @Test
    public void repositoryHydrationCanInferForeignKeysForScalarParticipants() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                atomic query queryGoodsByProduct(Product product, Goods goods);
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){ primarykey(Product.productId); }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey(Goods.skuId);
                        foreignkey(Goods.product);
                    }
                }
                scope{
                    local{
                        repository(Goods by Product) queryGoodsByProduct;
                    }
                }
                """);

        List<String> errors = invalid.validateRepositoryHydrationDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var service = (SppService)invalid.getSppClass("QueryGoodsByProduct");
        var rule = service.getScopeItem().getRepositoryHydrationRules().get(0);
        assertEquals("goods", rule.responseMethodName());
        assertEquals("Goods.product.productId", rule.keyTargets().get(0).repositoryObjectPath());
    }

    @Test
    public void repositoryHydrationCanDisambiguateMultipleForeignKeysWithNamedIndexes() throws Exception {
        SppDomain ambiguous = compileDomain("""
                domain commerce;
                Entity Address{ business("addressId business credential") String addressId; }
                Role Delivery{ business("deliveryId business credential") String deliveryId; Address shipAddress; Address billAddress; }
                atomic query queryDeliveryByAddress(Delivery selection, Delivery[] delivery);
                """, """
                domain commerce;
                tables entities{
                    table ADDRESS(Address){ primarykey(Address.addressId); }
                }
                tables roles{
                    table DELIVERY(Delivery){
                        primarykey(Delivery.deliveryId);
                        foreignkey(Delivery.shipAddress);
                        foreignkey(Delivery.billAddress);
                    }
                }
                scope{
                    local{
                        repository(Delivery by Address) queryDeliveryByAddress;
                    }
                }
                """);
        List<String> errors = ambiguous.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository Delivery by Address is ambiguous; declare a named APP DDL index"
                        + " and use repository(Delivery by index <name>)")));

        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Address{ business("addressId business credential") String addressId; }
                Role Delivery{ business("deliveryId business credential") String deliveryId; Address shipAddress; Address billAddress; }
                atomic query queryDeliveryByAddress(Delivery selection, Delivery[] delivery);
                """, """
                domain commerce;
                tables entities{
                    table ADDRESS(Address){ primarykey(Address.addressId); }
                }
                tables roles{
                    table DELIVERY(Delivery){
                        primarykey(Delivery.deliveryId);
                        foreignkey(Delivery.shipAddress);
                        foreignkey(Delivery.billAddress);
                        index deliveryByShipAddress(Delivery.shipAddress.addressId);
                        index deliveryByBillAddress(Delivery.billAddress.addressId);
                    }
                }
                scope{
                    local{
                        repository(Delivery by index deliveryByShipAddress) queryDeliveryByAddress;
                    }
                }
                """);

        ScopeItem queryDeliveryByAddress = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryDeliveryByAddress.getRepositoryHydrationRules().get(0);
        assertEquals("delivery", rule.responseMethodName());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(0, "Delivery.shipAddress.addressId")),
                rule.keyTargets());
    }

    @Test
    public void repositoryHydrationCanInferAppDdlIndexes() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                atomic query queryCreditAccount(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index(CreditAccount.customer.customerId);
                    }
                }
                scope{
                    local{
                        repository(CreditAccount by Customer) queryCreditAccount;
                    }
                }
                """);

        ScopeItem queryCreditAccount = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryCreditAccount.getRepositoryHydrationRules().get(0);
        assertEquals("account", rule.responseMethodName());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(0, "CreditAccount.customer.customerId")),
                rule.keyTargets());
        assertTrue(rule.appDdlInferred());
    }

    @Test
    public void appDdlIndexesCanBeNamed() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String phoneNo; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomer(CreditAccount.customer.customerId);
                        index creditAccountByCustomerPhone(CreditAccount.customer.phoneNo);
                    }
                }
                """);

        AppTable creditAccount = domain.getRuleBlock().getAppTables().get(0).getAppTableList().get(0);
        assertEquals(List.of("CreditAccount.customer.customerId", "CreditAccount.customer.phoneNo"),
                creditAccount.getIndexes());
        assertEquals(2, creditAccount.getAppIndexes().size());
        assertEquals("creditAccountByCustomer", creditAccount.getAppIndexes().get(0).getName());
        assertEquals(List.of("CreditAccount.customer.customerId"),
                creditAccount.getAppIndexes().get(0).getPaths());
        assertEquals("creditAccountByCustomerPhone", creditAccount.getAppIndexes().get(1).getName());
    }

    @Test
    public void appDdlIndexesCanTargetPlatformColumns() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey String salesOrderId;
                        notnull String orderNo;
                        index salesOrderByOrderNo(orderNo);
                    }
                }
                """);

        AppTable salesOrder = domain.getRuleBlock().getAppTables().get(0).getAppTableList().get(0);
        assertEquals(List.of("orderNo"), salesOrder.getIndexes());
        assertEquals("salesOrderByOrderNo", salesOrder.getAppIndexes().get(0).getName());
        assertEquals(List.of("orderNo"), salesOrder.getAppIndexes().get(0).getPaths());
        assertTrue(domain.validateAppTableDeclarations().toString(),
                domain.validateAppTableDeclarations().isEmpty());
        assertFalse(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("SALES_ORDER index paths orderNo")));
    }

    @Test
    public void appDdlQualifiedIndexesBackedByPlatformColumnsDoNotWarn() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; String name; }
                Role Goods{ Product product; String title; }
                take Product as Goods;
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){
                        primarykey String productId;
                    }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                        foreignkey(Goods.product);
                        index goodsByProduct(Goods.product.productId);
                    }
                }
                """);

        AppTable goods = domain.getRuleBlock().getAppTables().stream()
                .flatMap(tables -> tables.getAppTableList().stream())
                .filter(table -> "GOODS".equals(table.getName()))
                .findFirst().orElseThrow();
        assertEquals(List.of("Goods.product.productId"), goods.getIndexes());
        assertEquals("goodsByProduct", goods.getAppIndexes().get(0).getName());
        assertTrue(domain.validateAppTableDeclarations().toString(),
                domain.validateAppTableDeclarations().isEmpty());
        assertFalse(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("GOODS index paths Goods.product.productId")
                                && warning.contains("object-backed")));
        assertFalse(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("Role Goods still declares performer fields Goods.product")
                                && warning.contains("take(...) runtime bindings")));
        assertFalse(domain.appSemanticMigrationPlans().toString(),
                domain.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "ROLE_PERFORMER_FIELD".equals(plan.migrationType())
                                && "GOODS".equals(plan.tableName())
                                && "Goods".equals(plan.objectType())
                                && List.of("Goods.product").equals(plan.paths())));
    }

    @Test
    public void appSemanticMigrationWarningsExposeNonRoleServiceParticipants() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Role Buyer{ String level; }
                take Customer as Buyer;
                Contract SalesOrder{ String orderNo; Buyer buyer; }
                atomic query queryCustomer(Customer customer);
                atomic update openSalesOrder(SalesOrder order, Buyer buyer);
                Scenario serial submitOrder(SalesOrder order, Buyer buyer){
                    transaction{ openSalesOrder(order, buyer); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                scope{
                    local(in baseIn, out baseOut){
                        queryCustomer;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                }
                """);

        List<AppSemanticMigrationPlan> plans = domain.appSemanticMigrationPlans();
        assertTrue(plans.toString(), plans.stream()
                .anyMatch(plan -> "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                        && "queryCustomer".equals(plan.serviceName())
                        && "SERVICE_PARTICIPANT".equals(plan.targetKind())
                        && List.of("queryCustomer.customer:Customer").equals(plan.paths())
                        && List.of("references").equals(plan.participantCategories())));
        assertFalse(plans.toString(), plans.stream()
                .anyMatch(plan -> "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                        && "openSalesOrder".equals(plan.serviceName())
                        && "SERVICE_PARTICIPANT".equals(plan.targetKind())
                        && List.of("openSalesOrder.order:SalesOrder").equals(plan.paths())
                        && List.of("contracts").equals(plan.participantCategories())
                        && "CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO".equals(plan.migrationAction())));
        assertFalse(plans.toString(), plans.stream()
                .anyMatch(plan -> "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                        && "submitOrder".equals(plan.serviceName())
                        && "SCENARIO_PARTICIPANT".equals(plan.targetKind())
                        && List.of("submitOrder.order:SalesOrder").equals(plan.paths())
                        && "CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO".equals(plan.migrationAction())));
        assertFalse(plans.toString(), plans.stream()
                .filter(plan -> "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType()))
                .flatMap(plan -> plan.paths().stream())
                .anyMatch(path -> path.contains("buyer:Buyer")));
        assertTrue(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains(
                                "Service queryCustomer still accepts non-Role participants queryCustomer.customer:Customer")
                                && warning.contains("target Role Buyer")
                                && warning.contains("remote Role adapter")));
        assertTrue(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains(
                                "Service queryCustomer still accepts non-Role participants queryCustomer.customer:Customer")
                                && warning.contains("external Reference objects")
                                && warning.contains("remote Role adapter")));
    }

    @Test
    public void appSemanticMigrationAcceptsIntrinsicRoleFields() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ Goods goods; int requestedQty; }
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                """);

        assertFalse(domain.appSemanticMigrationPlans().toString(),domain.appSemanticMigrationPlans().stream()
                .anyMatch(plan -> "SalesItem".equals(plan.objectType()) && ("ROLE_OBJECT_FIELD".equals(plan.migrationType()) || "ROLE_PERFORMER_FIELD".equals(plan.migrationType()))));
        assertTrue(domain.prepareRoleCompositions().isEmpty());
    }

    @Test
    public void appSemanticMigrationWarningsClassifyRoleForbiddenObjectFieldCategories() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Reference Customer{ business("customerId business credential") String customerId; }
                Role Buyer{ String level; }
                take Customer as Buyer;
                Contract SalesOrder{ String orderNo; Buyer buyer; }
                Role ReviewContext{
                    Product product;
                    Customer customer;
                    Buyer buyer;
                    SalesOrder order;
                    String status;
                }
                """, """
                domain commerce;
                tables roles{
                    table REVIEW_CONTEXT(ReviewContext){ primarykey String reviewContextId; }
                }
                """);

        var plans = domain.appSemanticMigrationPlans();
        assertTrue(plans.toString(),plans.stream().anyMatch(plan -> "ROLE_OBJECT_FIELD".equals(plan.migrationType())
                && plan.paths().equals(List.of("ReviewContext.order:SalesOrder")) && plan.participantCategories().equals(List.of("contracts"))));
    }

    @Test
    public void appSemanticMigrationWarningsExposeAppDdlPlatformPrimaryKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ String title; }
                take Product as Goods;
                Role SalesItem{ int requestedQty; }
                take Goods as SalesItem;
                Contract SalesOrder{ SalesItem[] lines; }
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                """);

        List<AppSemanticMigrationPlan> plans = domain.appSemanticMigrationPlans();

        assertTrue(plans.toString(), plans.stream()
                .anyMatch(plan -> "APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                        && "SALES_ITEM".equals(plan.tableName())
                        && "SalesItem".equals(plan.objectType())
                        && List.of("SALES_ITEM.salesItemId").equals(plan.paths())
                        && ("replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                        + "+keepRuntimeIdentityOutsideSppRole").equals(plan.migrationAction())));
        assertTrue(plans.toString(), plans.stream()
                .anyMatch(plan -> "APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                        && "SALES_ORDER".equals(plan.tableName())
                        && "SalesOrder".equals(plan.objectType())
                        && List.of("SALES_ORDER.salesOrderId").equals(plan.paths())
                        && ("replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                        + "+keepRuntimeIdentityOutsideSppRole").equals(plan.migrationAction())));
        assertTrue(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains(
                                "APP DDL table SALES_ITEM carries platform primary key paths SALES_ITEM.salesItemId")
                                && warning.contains("identityKind=SIDECAR_PLATFORM_IDENTITY")
                                && warning.contains("runtime identity")
                                && warning.contains("composition identity")));
    }

    @Test
    public void appDdlPlatformPrimaryKeyValidationRejectsSidecarPrimaryKeys() throws Exception {
        SppDomain migrationWindow = compileDomain("""
                domain commerce;
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ SalesItem[] lines; }
                """, """
                domain commerce;
                tables roles{
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                """);
        assertTrue(migrationWindow.validateAppDeclarations().toString(),
                migrationWindow.validateAppDeclarations().isEmpty());

        List<String> directErrors = migrationWindow.validateAppDdlPlatformPrimaryKeyDeclarations();

        assertTrue(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ITEM still carries platform primary key identity SALES_ITEM.salesItemId")));
        assertTrue(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ORDER still carries platform primary key identity SALES_ORDER.salesOrderId")));
        assertTrue(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "replace sidecar primary keys with business or composition identity")));
        assertTrue(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "objectType=SalesItem")
                && error.contains("paths=SALES_ITEM.salesItemId")
                && error.contains("actions=replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                + "+keepRuntimeIdentityOutsideSppRole")
                && error.contains("action=replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                + "+keepRuntimeIdentityOutsideSppRole")));

        SppDomain strict = compileDomain("""
                domain commerce;
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ SalesItem[] lines; }
                """, """
                domain commerce;
                tables roles{
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppDdlPlatformPrimaryKey = true;
                }
                """);

        List<String> strictErrors = strict.validateAppDeclarations();

        assertTrue(strictErrors.toString(), strictErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ITEM still carries platform primary key identity SALES_ITEM.salesItemId")));
        assertTrue(strictErrors.toString(), strictErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ORDER still carries platform primary key identity SALES_ORDER.salesOrderId")));
        assertTrue(strictErrors.toString(), strictErrors.stream().anyMatch(error -> error.contains(
                "objectType=SalesOrder")
                && error.contains("paths=SALES_ORDER.salesOrderId")
                && error.contains("actions=replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                + "+keepRuntimeIdentityOutsideSppRole")
                && error.contains("action=replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                + "+keepRuntimeIdentityOutsideSppRole")));
    }

    @Test
    public void appDdlPlatformPrimaryKeyValidationAllowsRolePerformerIdentityPrimaryKeys() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Role Goods{ business("skuId business credential") String skuId; }
                Role SalesItem{ int requestedQty; double unitPrice; double lineAmount; }
                take Goods as SalesItem;
                Contract SalesOrder{ SalesItem[] lines; }
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table SALES_ITEM(SalesItem){ primarykey String goods_skuid; }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppDdlPlatformPrimaryKey = true;
                }
                """);

        assertTrue(strict.appSemanticMigrationPlans().toString(),
                strict.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                                && "SALES_ITEM".equals(plan.tableName())
                                && plan.paths().contains("SALES_ITEM.goods_skuid")));

        List<String> directErrors = strict.validateAppDdlPlatformPrimaryKeyDeclarations();

        assertFalse(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ITEM still carries platform primary key identity SALES_ITEM.goods_skuid")));
        assertTrue(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ORDER still carries platform primary key identity SALES_ORDER.salesOrderId")));

        List<String> strictErrors = strict.validateAppDeclarations();

        assertFalse(strictErrors.toString(), strictErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ITEM still carries platform primary key identity SALES_ITEM.goods_skuid")));
        assertTrue(strictErrors.toString(), strictErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ORDER still carries platform primary key identity SALES_ORDER.salesOrderId")));
    }

    @Test
    public void appDdlPlatformPrimaryKeyValidationAllowsRoleCompositionIdentityPrimaryKeys() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Role Goods{ business("skuId business credential") String skuId; }
                Role SalesItem{ int requestedQty; double unitPrice; double lineAmount; }
                take Goods as SalesItem;
                Contract SalesOrder{ String orderNo; SalesItem[] lines; }
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table SALES_ITEM(SalesItem){
                        primarykey String order_no;
                        primarykey String goods_skuid;
                    }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppDdlPlatformPrimaryKey = true;
                }
                """);

        List<AppSemanticMigrationPlan> plans = strict.appSemanticMigrationPlans();

        assertTrue(plans.toString(), plans.stream()
                .anyMatch(plan -> "APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                        && "SALES_ITEM".equals(plan.tableName())
                        && plan.paths().contains("SALES_ITEM.order_no")
                        && plan.paths().contains("SALES_ITEM.goods_skuid")
                        && "COMPOSITION_IDENTITY".equals(plan.identityKind())));

        List<String> directErrors = strict.validateAppDdlPlatformPrimaryKeyDeclarations();

        assertFalse(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ITEM still carries platform primary key identity SALES_ITEM.order_no")));
        assertTrue(directErrors.toString(), directErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ORDER still carries platform primary key identity SALES_ORDER.salesOrderId")));

        List<String> strictErrors = strict.validateAppDeclarations();

        assertFalse(strictErrors.toString(), strictErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ITEM still carries platform primary key identity SALES_ITEM.order_no")));
        assertTrue(strictErrors.toString(), strictErrors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ORDER still carries platform primary key identity SALES_ORDER.salesOrderId")));
    }

    @Test
    public void appSemanticMigrationWarningsAllowContractStateFields() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ Customer customer; }
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{
                    String orderNo;
                    Customer buyer;
                    CreditAccount account;
                    SalesItem[] lines;
                    int totalQuantity;
                    double goodsAmount;
                    double discountAmount;
                    quantum double occupiedAmount;
                    quantum double capturedAmount;
                    double payableAmount;
                }
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                tables roles{
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                """);

        List<AppSemanticMigrationPlan> plans = domain.appSemanticMigrationPlans();
        assertFalse(plans.toString(), plans.stream()
                .filter(plan -> "CONTRACT_SIGNER_FIELD".equals(plan.migrationType()))
                .flatMap(plan -> plan.paths().stream())
                .anyMatch(path -> path.contains("buyer")
                        || path.contains("account")
                        || path.contains("lines")));
        assertFalse(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("Contract SalesOrder still declares signer")));
    }

    @Test
    public void appSemanticMigrationWarningsPreserveArrayParticipantTypes() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesBundle{ Goods[] goods; int requestedQty; }
                atomic query queryGoodsByProducts(Product[] products, Goods[] goods);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table SALES_BUNDLE(SalesBundle){ primarykey String bundleId; }
                }
                scope{
                    local(in baseIn, out baseOut){
                        queryGoodsByProducts;
                    }
                }
                """);

        List<AppSemanticMigrationPlan> plans = domain.appSemanticMigrationPlans();
        assertTrue(plans.toString(), plans.stream()
                .anyMatch(plan -> "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                        && "queryGoodsByProducts".equals(plan.serviceName())
                        && List.of("queryGoodsByProducts.products:Product[]").equals(plan.paths())));
        assertTrue(plans.toString(), plans.stream()
                .anyMatch(plan -> "ROLE_OBJECT_FIELD".equals(plan.migrationType())
                        && "SALES_BUNDLE".equals(plan.tableName())
                        && "SalesBundle".equals(plan.objectType())
                        && List.of("SalesBundle.goods:Goods[]").equals(plan.paths())));
    }

    @Test
    public void appSemanticMigrationAcceptsUntabledIntrinsicRoleFields() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ Goods goods; int requestedQty; }
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                }
                """);

        assertFalse(domain.appSemanticMigrationPlans().toString(),domain.appSemanticMigrationPlans().stream()
                .anyMatch(plan -> "SalesItem".equals(plan.objectType()) && "ROLE_OBJECT_FIELD".equals(plan.migrationType())));
        assertTrue(domain.prepareRoleCompositions().isEmpty());
    }

    @Test
    public void roleOnlyBusinessModelAcceptsDeclaredIntrinsicParticipants() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                take Product as Goods;
                Role Inventory{ Store store; Goods goods; int safeQty; }
                take Store, Goods as Inventory;
                Role SalesItem{ Goods goods; int requestedQty; }
                take Product as SalesItem;
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table INVENTORY(Inventory){ primarykey String inventoryId; }
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                """);

        assertTrue(invalid.validateRoleOnlyBusinessModelDeclarations().toString(),invalid.validateRoleOnlyBusinessModelDeclarations().isEmpty());
        assertTrue(invalid.prepareRoleCompositions().isEmpty());
    }

    @Test
    public void roleOnlyBusinessModelValidationRejectsContractRoleTakeDeclarations() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ SalesItem[] lines; }
                take SalesOrder as SalesItem;
                take Product as SalesOrder;
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                """);

        List<String> errors = invalid.validateRoleOnlyBusinessModelDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "uses Contract SalesOrder as a performer; contracts are signed by Role participants"
                        + " and must not play their internal Roles")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "must target a Role; take expresses runtime Role playing, not inheritance or Contract composition")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Entity Store{ business("storeId business credential") String storeId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role Inventory{ int safeQty; }
                take Store, Goods as Inventory;
                Role SalesItem{ int requestedQty; }
                take Goods as SalesItem;
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table INVENTORY(Inventory){ primarykey String inventoryId; }
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                """);

        assertTrue(valid.validateRoleOnlyBusinessModelDeclarations().toString(),
                valid.validateRoleOnlyBusinessModelDeclarations().isEmpty());
    }

    @Test
    public void roleOnlyServiceBoundaryValidationRejectsNonRoleParticipants() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ int requestedQty; }
                take Goods as SalesItem;
                Contract SalesOrder{ String orderNo; SalesItem[] lines; }
                atomic query queryCustomer(Customer customer);
                atomic update openSalesOrder(SalesOrder order, SalesItem item);
                Scenario serial submitOrder(SalesOrder order, SalesItem item){
                    transaction{ openSalesOrder(order, item); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                scope{
                    local(in baseIn, out baseOut){
                        queryCustomer;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateRoleOnlyServiceBoundaryDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Service/scenario queryCustomer declares non-Role participants queryCustomer.customer:Customer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "queryCustomer.customer:Customer -> action=REFERENCE_TO_REMOTE_ROLE_ADAPTER")
                && error.contains("roleOnlyReady=false")
                && error.contains("reasons=MISSING_ROLE_PARTICIPANT+MISSING_TARGET_ROLE"
                + "+REMOTE_ROLE_ADAPTER_REQUIRED")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Service/scenario openSalesOrder declares non-Role participants openSalesOrder.order:SalesOrder")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "openSalesOrder.order:SalesOrder -> action=CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO")
                && error.contains("roleOnlyReady=true")
                && error.contains("targetRoles=SalesItem")
                && error.contains("targetRoleParams=item")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Service/scenario submitOrder declares non-Role participants submitOrder.order:SalesOrder")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "submitOrder.order:SalesOrder -> action=CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO")
                && error.contains("roleOnlyReady=true")
                && error.contains("targetRoles=SalesItem")
                && error.contains("targetRoleParams=item")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains("item:SalesItem")));

        List<String> blockedErrors = invalid.validateBlockedRoleOnlyServiceBoundaryDeclarations();
        assertTrue(blockedErrors.toString(), blockedErrors.stream().anyMatch(error -> error.contains(
                "Service/scenario queryCustomer declares non-Role participants queryCustomer.customer:Customer")));
        assertFalse(blockedErrors.toString(), blockedErrors.stream().anyMatch(error -> error.contains(
                "Service/scenario openSalesOrder declares non-Role participants openSalesOrder.order:SalesOrder")));
        assertFalse(blockedErrors.toString(), blockedErrors.stream().anyMatch(error -> error.contains(
                "Service/scenario submitOrder declares non-Role participants submitOrder.order:SalesOrder")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ int requestedQty; }
                take Goods as SalesItem;
                atomic query queryGoods(Goods goods);
                atomic update reserveSalesItem(SalesItem item, Goods goods);
                Scenario serial submitItem(SalesItem item, Goods goods){
                    transaction{ reserveSalesItem(item, goods); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                scope{
                    local(in baseIn, out baseOut){
                        queryGoods;
                        at resource(salesItem) snapshot(argument(0, SalesItem.requestedQty)) reserveSalesItem;
                        saga submitItem;
                    }
                }
                """);

        assertTrue(valid.validateRoleOnlyServiceBoundaryDeclarations().toString(),
                valid.validateRoleOnlyServiceBoundaryDeclarations().isEmpty());
        assertFalse(valid.appSemanticMigrationPlans().toString(),
                valid.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())));
    }

    @Test
    public void roleOnlyServiceBoundaryValidationExplainsEntityPerformerTargets() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                atomic query queryGoodsByProduct(Product product, Goods goods);
                atomic query queryProductOnly(Product product);
                """, """
                domain commerce;
                scope{
                    local{
                        queryGoodsByProduct;
                        queryProductOnly;
                    }
                }
                """);

        List<String> errors = invalid.validateRoleOnlyServiceBoundaryDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "queryGoodsByProduct.product:Product -> action=ENTITY_TO_ROLE_PERFORMER_OR_REPOSITORY_QUERY")
                && error.contains("roleParticipantPresent=true")
                && error.contains("roleOnlyReady=true")
                && error.contains("targetRoles=Goods")
                && error.contains("targetRoleParams=goods")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "queryProductOnly.product:Product -> action=ENTITY_TO_ROLE_PERFORMER_OR_REPOSITORY_QUERY")
                && error.contains("roleParticipantPresent=false")
                && error.contains("roleOnlyReady=false")
                && error.contains("candidateRoles=Goods")
                && error.contains("reasons=MISSING_ROLE_PARTICIPANT+MISSING_TARGET_ROLE")));

        List<String> blockedErrors = invalid.validateBlockedRoleOnlyServiceBoundaryDeclarations();
        assertFalse(blockedErrors.toString(), blockedErrors.stream().anyMatch(error -> error.contains(
                "Service/scenario queryGoodsByProduct declares non-Role participants")));
        assertTrue(blockedErrors.toString(), blockedErrors.stream().anyMatch(error -> error.contains(
                "Service/scenario queryProductOnly declares non-Role participants"
                        + " queryProductOnly.product:Product")));
    }

    @Test
    public void roleOnlyServiceBoundaryValidationAcceptsExternalRemoteReferenceAdapters() throws Exception {
        SppDomain external = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "http://localhost:8080/queryCustomer";
                    }
                }
                """);

        List<String> boundaryErrors = external.validateRoleOnlyServiceBoundaryDeclarations();
        assertTrue(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error -> error.contains(
                "queryCustomer.customer:Customer -> action=REFERENCE_TO_EXTERNAL_DOMAIN_ADAPTER")
                && error.contains("roleOnlyReady=true")
                && error.contains("reasons=EXTERNAL_REMOTE_ADAPTER_BOUND+REFERENCE_BOUNDARY_EXTERNALIZED")));
        assertTrue(external.validateAppDeclarations().toString(), external.validateAppDeclarations().isEmpty());

        SppDomain reference = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter jdbcObjectRepository;
                        config datasourceUrl = "jdbc:h2:mem:spp-object-store;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config table = "spp_business_object";
                    }
                }
                """);

        List<String> errors = reference.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "queryCustomer.customer:Customer -> action=REFERENCE_TO_REMOTE_ROLE_ADAPTER")
                && error.contains("roleOnlyReady=false")
                && error.contains("reasons=MISSING_ROLE_PARTICIPANT+MISSING_TARGET_ROLE"
                + "+REMOTE_ROLE_ADAPTER_REQUIRED")));
    }

    @Test
    public void roleOnlyServiceBoundaryValidationAcceptsAppDdlRepositoryObjectAdapters() throws Exception {
        SppDomain appDdlRepository = compileDomainWithRepository("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; }
                atomic query queryCreditAccount(CreditAccount account);
                atomic query queryOrder(SalesOrder order);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String accountNo;
                    }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                    }
                }
                scope{
                    local{
                        repository(CreditAccount) queryCreditAccount;
                        repository(SalesOrder) queryOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        List<String> boundaryErrors = appDdlRepository.validateRoleOnlyServiceBoundaryDeclarations();
        assertTrue(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error -> error.contains(
                "queryCreditAccount.account:CreditAccount -> action=ENTITY_TO_OBJECT_REPOSITORY_ADAPTER")
                && error.contains("roleOnlyReady=true")
                && error.contains("reasons=APP_DDL_REPOSITORY_BOUND+OBJECT_REPOSITORY_BOUNDARY")));
        assertFalse(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error ->
                error.contains("queryOrder.order:SalesOrder")));
        assertTrue(appDdlRepository.validateAppDeclarations().toString(),
                appDdlRepository.validateAppDeclarations().isEmpty());

        SppDomain explicitRepository = compileDomainWithRepository("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic query queryCreditAccount(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey(CreditAccount.accountNo);
                    }
                }
                scope{
                    local{
                        repository(argument(0, CreditAccount.accountNo), account) queryCreditAccount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        List<AppSemanticMigrationPlan> migrationPlans = explicitRepository.appSemanticMigrationPlans();
        assertTrue(migrationPlans.toString(), migrationPlans.stream().anyMatch(plan ->
                "REPOSITORY_ESCAPE_HATCH".equals(plan.migrationType())
                        && "queryCreditAccount".equals(plan.serviceName())
                        && plan.objectKeyAppDdlDerived()
                        && plan.objectKeyEscapeHatch()
                        && plan.appDdlObjectKeyPushdown()
                        && "APP_DDL_PRIMARY_KEY".equals(plan.objectKeySource())
                        && List.of("accountNo").equals(plan.appDdlObjectKeyPaths())
                        && List.of("accountNo").equals(plan.objectKeyColumns())));

        List<String> explicitRepositoryBoundaryErrors =
                explicitRepository.validateRoleOnlyServiceBoundaryDeclarations();
        assertTrue(explicitRepositoryBoundaryErrors.toString(),
                explicitRepositoryBoundaryErrors.stream().anyMatch(error -> error.contains(
                        "queryCreditAccount.account:CreditAccount -> action=ENTITY_TO_OBJECT_REPOSITORY_ADAPTER")
                        && error.contains("roleOnlyReady=true")
                        && error.contains("reasons=APP_DDL_REPOSITORY_BOUND+OBJECT_REPOSITORY_BOUNDARY")));
        assertTrue(explicitRepository.validateAppDeclarations().toString(),
                explicitRepository.validateAppDeclarations().isEmpty());
    }

    @Test
    public void roleOnlyServiceBoundaryValidationAcceptsQuantumResourceBoundaries() throws Exception {
        SppDomain quantum = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; quantum double availableCredit; }
                Contract SalesOrder{ String orderNo; quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute SalesOrder.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting, CreditAccount.availableCredit, SalesOrder.occupiedAmount)
                            reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= order.payableAmount;
                        order.occupiedAmount += order.payableAmount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                    quantumStore globalQuantum{
                        adapter quantumService;
                        config implementationStrategy = "QUANTUM_SERVICE";
                        config accessMode = "QUANTUM_SERVICE_ADAPTER";
                        config idempotencyMode = "SERVICE_IDEMPOTENCY";
                        config reversalMode = "NEGATIVE_DELTA";
                        config endpoint = "https://quantum.credit.example.org";
                    }
                }
                """);

        List<String> boundaryErrors = quantum.validateRoleOnlyServiceBoundaryDeclarations();
        assertTrue(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error -> error.contains(
                "reserveCredit.account:CreditAccount -> action=ENTITY_TO_QUANTUM_RESOURCE_ADAPTER")
                && error.contains("roleOnlyReady=true")
                && error.contains("reasons=APP_QUANTUM_RESOURCE_BOUND+QUANTUM_RESOURCE_BOUNDARY"
                + "+IDEMPOTENCY_REVERSAL_INTERNAL")));
        assertFalse(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error ->
                error.contains("reserveCredit.order:SalesOrder")));
        assertTrue(quantum.validateAppDeclarations().toString(), quantum.validateAppDeclarations().isEmpty());

        SppDomain ordinaryObjectUpdate = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double usedCredit; }
                Contract SalesOrder{ String orderNo; double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        reserveCredit;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        List<String> errors = ordinaryObjectUpdate.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "reserveCredit.account:CreditAccount -> action=ENTITY_TO_ROLE_PERFORMER_OR_REPOSITORY_QUERY")
                && error.contains("roleOnlyReady=false")
                && error.contains("reasons=MISSING_ROLE_PARTICIPANT+MISSING_TARGET_ROLE")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "reserveCredit.order:SalesOrder -> action=CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO")
                && error.contains("roleOnlyReady=false")
                && error.contains("reasons=MISSING_ROLE_PARTICIPANT+MISSING_TARGET_ROLE")));
    }

    @Test
    public void roleOnlyServiceBoundaryValidationAcceptsAppDdlObjectTransactionAdapters() throws Exception {
        SppDomain objectTransaction = compileDomainWithRepository("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(SalesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        List<String> boundaryErrors = objectTransaction.validateRoleOnlyServiceBoundaryDeclarations();
        assertFalse(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error ->
                error.contains("openSalesOrder.order:SalesOrder")));
        assertTrue(objectTransaction.validateAppDeclarations().toString(),
                objectTransaction.validateAppDeclarations().isEmpty());

        SppDomain ordinaryObjectUpdate = compileDomainWithRepository("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        List<String> errors = ordinaryObjectUpdate.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Default persistence for openSalesOrder requires PHY repository persistenceMode and persistenceResource")));
    }

    @Test
    public void appDdlReplacementValidationRejectsRepositoryAndSnapshotEscapeHatches() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                atomic query queryOrder(SalesOrder order);
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                        index salesOrderByOrderNo(SalesOrder.orderNo);
                    }
                }
                scope{
                    local{
                        repository(argument(0, SalesOrder.orderNo), order) queryOrder;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateAppDdlReplacementDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository query queryOrder uses explicit argument key paths orderNo")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP DDL APP_DDL_PRIMARY_KEY already covers orderNo")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=replaceRepositoryArgumentEscapeHatch+useRepositoryTypeOrRepositoryByIndex")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=replaceRepositoryArgumentEscapeHatch+useRepositoryTypeOrRepositoryByIndex")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot rule for service openSalesOrder object SalesOrder uses explicit object key paths orderNo")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=declareAppDdlKeyOrIndex+replaceSnapshotArgumentEscapeHatch"
                        + "+useSnapshotTypeOrSnapshotByIndex")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=declareAppDdlKeyOrIndex+replaceSnapshotArgumentEscapeHatch"
                        + "+useSnapshotTypeOrSnapshotByIndex")));

        SppDomain missingRepositoryAppDdlKey = compileDomain("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                atomic query queryOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, SalesOrder.orderNo), order) queryOrder;
                    }
                }
                """);
        List<String> missingRepositoryKeyErrors =
                missingRepositoryAppDdlKey.validateAppDdlReplacementDeclarations();
        assertTrue(missingRepositoryKeyErrors.toString(), missingRepositoryKeyErrors.stream().anyMatch(error ->
                error.contains("Repository query queryOrder uses explicit argument key paths orderNo")
                        && error.contains("actions=declareAppDdlKeyOrIndex+replaceRepositoryArgumentEscapeHatch"
                                + "+useRepositoryTypeOrRepositoryByIndex")
                        && error.contains("action=declareAppDdlKeyOrIndex+replaceRepositoryArgumentEscapeHatch"
                                + "+useRepositoryTypeOrRepositoryByIndex")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                atomic query queryOrder(SalesOrder order);
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                        index salesOrderByOrderNo(SalesOrder.orderNo);
                    }
                }
                scope{
                    local{
                        repository(SalesOrder by index salesOrderByOrderNo) queryOrder;
                        at resource(salesOrder) snapshot(SalesOrder by index salesOrderByOrderNo) openSalesOrder;
                    }
                }
                """);

        assertTrue(valid.validateAppDdlReplacementDeclarations().toString(),
                valid.validateAppDdlReplacementDeclarations().isEmpty());
    }

    @Test
    public void phyStrictAppDdlReplacementConfigEnablesValidationInAppDeclarations() throws Exception {
        SppDomain migrationWindow = compileDomainWithRepository("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                atomic query queryOrder(SalesOrder order);
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        repository(argument(0, SalesOrder.orderNo), order) queryOrder;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """);
        assertTrue(migrationWindow.validateAppDeclarations().toString(),
                migrationWindow.validateAppDeclarations().isEmpty());

        SppDomain strict = compileDomainWithRepository("""
                domain commerce;
                Contract SalesOrder{ String orderNo; String status; }
                atomic query queryOrder(SalesOrder order);
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        repository(argument(0, SalesOrder.orderNo), order) queryOrder;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppDdlReplacement = true;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository query queryOrder uses explicit argument key paths orderNo")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot rule for service openSalesOrder object SalesOrder uses explicit object key paths orderNo")));
    }

    @Test
    public void phyStrictAppDdlReplacementAllowsTransactionSnapshotEvidence() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(credit) snapshot(transaction)
                            snapshot(argument(0, CreditAccount.accountNo)) reserveCredit;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppDdlReplacement = true;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot rule for service reserveCredit object CreditAccount uses explicit object key paths accountNo")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot rule for service reserveCredit object TransactionSnapshot uses explicit object key paths globalSerialNumber")));
        assertFalse(strict.appSemanticMigrationPlans().toString(),
                strict.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "SNAPSHOT_ESCAPE_HATCH".equals(plan.migrationType())
                                && "TransactionSnapshot".equals(plan.objectType())));
        assertFalse(strict.appSemanticMigrationWarnings().toString(),
                strict.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("TransactionSnapshot still uses explicit object key paths")));
    }

    @Test
    public void phyStrictAppMigrationConfigEnablesStrictValidationWithSpecificOverride() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                take Product as Goods;
                atomic query queryGoods(Goods goods);
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){
                        primarykey(Goods.skuId);
                    }
                }
                scope{
                    local{
                        repository(argument(0, Goods.skuId), goods) queryGoods;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyBusinessModel = false;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository query queryGoods uses explicit argument key paths skuId")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Role Goods declares performer fields Goods.product")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Role Goods declares object participant fields Goods.product:Product")));
    }

    @Test
    public void phyStrictAppMigrationConfigRejectsInvalidRoleTakeDeclarations() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ SalesItem[] lines; }
                take SalesOrder as SalesItem;
                take Product as SalesOrder;
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String salesOrderId; }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "uses Contract SalesOrder as a performer; contracts are signed by Role participants"
                        + " and must not play their internal Roles")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "must target a Role; take expresses runtime Role playing, not inheritance or Contract composition")));
    }

    @Test
    public void strictRoleOnlyBusinessModelAcceptsIntrinsicPerformerWithoutTake() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Role CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ Customer payer; double payableAmount; }
                take Customer as CreditAccount;
                """, """
                domain commerce;
                tables roles{
                    table CREDIT_ACCOUNT(CreditAccount){ primarykey String customer_customerid; }
                    table ORDER_AMOUNT(OrderAmount){ primarykey String order_amount_id; }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyBusinessModel = true;
                }
                """);

        var errors = strict.validateAppDeclarations();
        assertFalse(errors.toString(),errors.stream().anyMatch(error -> error.contains("Role OrderAmount has no take performer declaration")));
        assertFalse(strict.appSemanticMigrationPlans().toString(),strict.appSemanticMigrationPlans().stream()
                .anyMatch(plan -> "ROLE_WITHOUT_PERFORMER".equals(plan.migrationType()) && "OrderAmount".equals(plan.objectType())));
    }

    @Test
    public void strictRoleOnlyBusinessModelClassifiesRoleParticipantFieldKinds() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Contract SalesOrder{ String orderNo; }
                Role SalesItem{
                    Customer buyer;
                    Goods goods;
                    SalesOrder order;
                    int requestedQty;
                }
                take Goods as SalesItem;
                """, """
                domain commerce;
                tables roles{
                    table GOODS(Goods){ primarykey String skuId; }
                    table SALES_ITEM(SalesItem){ primarykey String goods_skuid; }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyBusinessModel = true;
                }
                """);

        var errors = strict.validateAppDeclarations();
        assertFalse(errors.toString(),errors.stream().anyMatch(error -> error.contains("declares performer fields")));
        assertTrue(errors.toString(),errors.stream().anyMatch(error -> error.contains("SalesItem.order:SalesOrder") && error.contains("participantCategories=contracts")));
    }

    @Test
    public void phyStrictAppMigrationConfigIncludesRoleOnlyServiceBoundary() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ String orderNo; SalesItem[] lines; }
                atomic query inspectOrder(SalesOrder order, SalesItem item);
                atomic query inspectCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        inspectOrder;
                        inspectCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                }
        """);

        List<String> errors = strict.validateAppDeclarations();
        List<String> boundaryErrors = strict.validateRoleOnlyServiceBoundaryDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Service/scenario inspectCustomer declares non-Role participants inspectCustomer.customer:Customer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "inspectCustomer.customer:Customer -> action=REFERENCE_TO_REMOTE_ROLE_ADAPTER")
                && error.contains("roleOnlyReady=false")
                && error.contains("reasons=MISSING_ROLE_PARTICIPANT+MISSING_TARGET_ROLE"
                + "+REMOTE_ROLE_ADAPTER_REQUIRED")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Service/scenario inspectOrder declares non-Role participants")));
        assertFalse(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error -> error.contains(
                "Service/scenario inspectOrder declares non-Role participants")));
        assertFalse(strict.appSemanticMigrationPlans().toString(),
                strict.appSemanticMigrationPlans().stream().anyMatch(plan ->
                        "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                                && "inspectOrder".equals(plan.serviceName())));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains("inspectOrder.item")));

        SppDomain override = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ String orderNo; SalesItem[] lines; }
                atomic query inspectOrder(SalesOrder order, SalesItem item);
                atomic query inspectCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        inspectOrder;
                        inspectCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                }
                """);

        assertFalse(override.validateAppDeclarations().toString(), override.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains(
                        "Service/scenario inspectOrder declares non-Role participants")));

        SppDomain legacyContractParticipant = compileDomain("""
                domain commerce;
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ String orderNo; }
                atomic query inspectOrder(SalesOrder order, SalesItem item);
                """, """
                domain commerce;
                scope{
                    local{
                        inspectOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        assertFalse(legacyContractParticipant.validateAppDeclarations().toString(),
                legacyContractParticipant.validateAppDeclarations().stream().anyMatch(error -> error.contains(
                        "Service/scenario inspectOrder declares non-Role participants"
                                + " inspectOrder.order:SalesOrder")));
    }

    @Test
    public void obsoleteContextParametersAreRejected() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role SalesItem{ int requestedQty; }
                Contract SalesOrder{ String orderNo; }
                atomic query inspectRole(context SalesItem item);
                atomic query inspectEntity(context Product product);
                atomic query inspectOrders(context SalesOrder[] orders);
                """, """
                domain commerce;
                scope{
                    local{
                        inspectRole;
                        inspectEntity;
                        inspectOrders;
                    }
                }
                """, """
                domain commerce;
                phy{}
                """);

        List<String> errors = domain.validateAppDeclarations();

        for (String service : List.of("inspectRole", "inspectEntity", "inspectOrders")) {
            assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains("Service " + service)
                    && error.contains("entrance requires a participant, not context")));
        }
    }

    @Test
    public void phyStrictAppMigrationConfigIncludesAppDdlPlatformPrimaryKeys() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Role SalesItem{ int requestedQty; }
                """, """
                domain commerce;
                tables roles{
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP DDL table SALES_ITEM still carries platform primary key identity SALES_ITEM.salesItemId")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "objectType=SalesItem")
                && error.contains("actions=replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                + "+keepRuntimeIdentityOutsideSppRole")
                && error.contains("action=replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity"
                + "+keepRuntimeIdentityOutsideSppRole")));

        SppDomain override = compileDomain("""
                domain commerce;
                Role SalesItem{ int requestedQty; }
                """, """
                domain commerce;
                tables roles{
                    table SALES_ITEM(SalesItem){ primarykey String salesItemId; }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictAppDdlPlatformPrimaryKey = false;
                }
                """);

        assertFalse(override.validateAppDeclarations().toString(), override.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains(
                        "APP DDL table SALES_ITEM still carries platform primary key identity")));
    }

    @Test
    public void phyStrictAppMigrationConfigIncludesPlatformColumnBusinessShadow() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; String name; }
                Role Goods{ business("skuId business credential") String skuId; String title; }
                take Product as Goods;
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){
                        primarykey String productRowId;
                        notnull String productId;
                    }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String goodsRowId;
                        notnull String skuId;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP DDL table PRODUCT already carries technical-looking SPP fields Product.productId")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP DDL table GOODS already carries technical-looking SPP fields Goods.skuId")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "objectType=Product")
                && error.contains("paths=Product.productId")
                && error.contains("actions=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")
                && error.contains("action=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "objectType=Goods")
                && error.contains("paths=Goods.skuId")
                && error.contains("actions=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")
                && error.contains("action=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")));
        assertTrue(strict.appSemanticMigrationPlans().toString(), strict.appSemanticMigrationPlans().stream()
                .anyMatch(plan -> "SPP_TECHNICAL_FIELD_PLATFORM_COLUMN".equals(plan.migrationType())
                        && "Product".equals(plan.objectType())
                        && List.of("Product.productId").equals(plan.paths())
                        && ("removeTechnicalIdentityFromSppBusinessType"
                        + "+keepOnlyTrueBusinessCredentials").equals(plan.migrationAction())));

        SppDomain override = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; String name; }
                Role Goods{ business("skuId business credential") String skuId; String title; }
                take Product as Goods;
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){
                        primarykey String productRowId;
                        notnull String productId;
                    }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String goodsRowId;
                        notnull String skuId;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictPlatformColumnBusinessShadow = false;
                }
                """);

        assertFalse(override.validateAppDeclarations().toString(), override.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("technical-looking SPP fields")));
    }

    @Test
    public void phyStrictAppMigrationConfigIncludesRemoteProductionGates() throws Exception {
        SppDomain referenceAdapter = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter jdbcObjectRepository;
                        config datasourceUrl = "jdbc:h2:mem:spp-object-store;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config table = "spp_business_object";
                    }
                }
                """);

        List<String> errors = referenceAdapter.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service queryCustomer is still backed by reference adapter objectRepositoryRemote")));

        SppDomain referenceAdapterOverride = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    config strictExternalRemoteAdapter = false;
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter jdbcObjectRepository;
                        config datasourceUrl = "jdbc:h2:mem:spp-object-store;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config table = "spp_business_object";
                    }
                }
                """);
        assertFalse(referenceAdapterOverride.validateAppDeclarations().toString(),
                referenceAdapterOverride.validateAppDeclarations().stream()
                        .anyMatch(error -> error.contains("reference adapter objectRepositoryRemote")));

        SppDomain developmentEndpoint = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "${credit.customer.endpoint:http://localhost:8080/queryCustomer}";
                    }
                }
                """);

        errors = developmentEndpoint.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service queryCustomer uses non-production external endpoint")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=replaceDevelopmentRemoteEndpoint+bindProductionExternalDomain")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=replaceDevelopmentRemoteEndpoint+bindProductionExternalDomain")));

        SppDomain developmentEndpointOverride = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    config strictRemoteExternalEndpoint = false;
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "${credit.customer.endpoint:http://localhost:8080/queryCustomer}";
                    }
                }
                """);
        assertFalse(developmentEndpointOverride.validateAppDeclarations().toString(),
                developmentEndpointOverride.validateAppDeclarations().stream()
                        .anyMatch(error -> error.contains("non-production external endpoint")));
    }

    @Test
    public void phyStrictAppMigrationConfigIncludesCurrentSnapshotAppDdlKeyGate() throws Exception {
        SppDomain migrationWindow = compileDomainWithRepository("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey String salesOrderId;
                    }
                }
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(creditReservation)
                            snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo))
                            reserveCredit;
                        repository(argument(0, SalesOrder.orderNo), SalesOrder) findSalesOrder;
                        saga submitOrder;
                    }
                }
                """);
        assertTrue(migrationWindow.validateAppDeclarations().toString(),
                migrationWindow.validateAppDeclarations().isEmpty());

        SppDomain strict = compileDomainWithRepository("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey String salesOrderId;
                    }
                }
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(creditReservation)
                            snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo))
                            reserveCredit;
                        repository(argument(0, SalesOrder.orderNo), SalesOrder) findSalesOrder;
                        saga submitOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Current snapshot SalesOrder.orderNo on service reserveCredit must be covered by APP DDL")));

        SppDomain override = compileDomainWithRepository("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey String salesOrderId;
                    }
                }
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(creditReservation)
                            snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo))
                            reserveCredit;
                        repository(argument(0, SalesOrder.orderNo), SalesOrder) findSalesOrder;
                        saga submitOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictCurrentSnapshotAppDdlKey = false;
                }
                """);

        assertFalse(override.validateAppDeclarations().toString(), override.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("Current snapshot SalesOrder.orderNo on service reserveCredit"
                        + " must be covered by APP DDL")));
    }

    @Test
    public void phyStrictRoleConfigsEnableValidationInAppDeclarations() throws Exception {
        SppDomain migrationWindow = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                take Product as Goods;
                Contract SalesOrder{ String orderNo; Goods goods; }
                atomic update openSalesOrder(SalesOrder order, Goods goods);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """);
        assertTrue(migrationWindow.validateAppDeclarations().toString(),
                migrationWindow.validateAppDeclarations().isEmpty());

        SppDomain strict = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                take Product as Goods;
                Contract SalesOrder{ String orderNo; Goods goods; }
                atomic update openSalesOrder(SalesOrder order, Goods goods);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyBusinessModel = true;
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        List<String> errors = strict.validateAppDeclarations();
        List<String> boundaryErrors = strict.validateRoleOnlyServiceBoundaryDeclarations();

        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Role Goods declares performer fields Goods.product")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Role Goods declares object participant fields Goods.product:Product")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Service/scenario openSalesOrder declares non-Role participants openSalesOrder.order:SalesOrder")));
        assertFalse(boundaryErrors.toString(), boundaryErrors.stream().anyMatch(error ->
                error.contains("Service/scenario openSalesOrder declares non-Role participants")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains("openSalesOrder.goods:Goods")));
    }

    @Test
    public void phyStrictAppConfigsUsePlaceholderDefaultsForStaticValidation() throws Exception {
        SppDomain strictPlaceholder = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                take Product as Goods;
                """, """
                domain commerce;
                scope{ local{ _default; } }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyBusinessModel = "${commerce.strict-role-model:true}";
                }
                """);

        List<String> errors = strictPlaceholder.validateAppDeclarations();

        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Role Goods declares performer fields Goods.product")));
        assertFalse(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Role Goods declares object participant fields Goods.product:Product")));

        SppDomain falseOverride = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; Product product; }
                take Product as Goods;
                """, """
                domain commerce;
                scope{ local{ _default; } }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = "${commerce.strict-app:true}";
                    config strictRoleOnlyBusinessModel = "${commerce.strict-role-model:false}";
                }
                """);

        assertFalse(falseOverride.validateAppDeclarations().toString(),
                falseOverride.validateAppDeclarations().stream()
                        .anyMatch(error -> error.contains("Role Goods declares performer fields Goods.product")));

        SppDomain invalidDefault = compileDomain("""
                domain commerce;
                Role Goods{ business("skuId business credential") String skuId; }
                """, """
                domain commerce;
                scope{ local{ _default; } }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyBusinessModel = "${commerce.strict-role-model:maybe}";
                }
                """);

        assertTrue(invalidDefault.validateAppDeclarations().toString(),
                invalidDefault.validateAppDeclarations().stream()
                        .anyMatch(error -> error.contains(
                                "PHY config strictRoleOnlyBusinessModel must be true or false")));
    }

    @Test
    public void allPhyStrictAppConfigLeavesAcceptPlaceholderDefaults() throws Exception {
        SppDomain allLeavesDisabled = compileDomain("""
                domain commerce;
                Role Goods{ business("skuId business credential") String skuId; }
                """, """
                domain commerce;
                scope{ local{ _default; } }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = "${commerce.strict-app:true}";
                    config strictRoleOnlyBusinessModel = "${commerce.strict-role-model:false}";
                    config strictRoleOnlyServiceBoundary = "${commerce.strict-role-boundary:false}";
                    config strictAppDdlReplacement = "${commerce.strict-app-ddl-replacement:false}";
                    config strictQuantumServiceInternalProtocol = "${commerce.strict-quantum-protocol:false}";
                    config strictQuantumServiceExternalImplementation = "${commerce.strict-quantum-external:false}";
                    config strictExternalRemoteAdapter = "${commerce.strict-remote-adapter:false}";
                    config strictRemoteExternalEndpoint = "${commerce.strict-remote-endpoint:false}";
                    config strictPlatformColumnBusinessShadow = "${commerce.strict-platform-shadow:false}";
                    config strictAppDdlPlatformPrimaryKey = "${commerce.strict-platform-key:false}";
                    config strictCurrentSnapshotAppDdlKey = "${commerce.strict-current-key:false}";
                }
                """);

        assertTrue(allLeavesDisabled.validateAppDeclarations().toString(),
                allLeavesDisabled.validateAppDeclarations().isEmpty());
    }

    @Test
    public void phyStrictQuantumServiceConfigEnablesValidationInAppDeclarations() throws Exception {
        SppDomain migrationWindow = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit))
                            compensate(releaseCredit) reserveCredit;
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit)) releaseCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                    releaseCredit{
                        account.availableCredit += amount.payableAmount;
                        amount.occupiedAmount -= amount.payableAmount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                    }
                }
                """);
        assertTrue(migrationWindow.validateAppDeclarations().toString(),
                migrationWindow.validateAppDeclarations().isEmpty());

        SppDomain strict = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit))
                            compensate(releaseCredit) reserveCredit;
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit)) releaseCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                    releaseCredit{
                        account.availableCredit += amount.payableAmount;
                        amount.occupiedAmount -= amount.payableAmount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictQuantumServiceInternalProtocol = true;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                    }
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum service reserveCredit still declares APP object recovery protocol")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "objectSnapshot, objectCompensation, frameworkIdempotency")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "reason=objectRecoveryProtocol")));
    }

    @Test
    public void phyStrictAppMigrationConfigIncludesQuantumServiceInternalProtocolGate() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit))
                            compensate(releaseCredit) reserveCredit;
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit)) releaseCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                    releaseCredit{
                        account.availableCredit += amount.payableAmount;
                        amount.occupiedAmount -= amount.payableAmount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    config strictAppDdlReplacement = false;
                    config strictQuantumServiceExternalImplementation = false;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                    }
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum service reserveCredit still declares APP object recovery protocol")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE+objectSnapshotManaged=false"
                        + "+reversalExecution=INPUT_NEGATIVE_DELTA+objectCompensation=false"
                        + "+idempotencyBoundary=QUANTUM_SERVICE_INTERNAL+objectRecoveryRequired=false")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE+objectSnapshotManaged=false"
                        + "+reversalExecution=INPUT_NEGATIVE_DELTA+objectCompensation=false"
                        + "+idempotencyBoundary=QUANTUM_SERVICE_INTERNAL+objectRecoveryRequired=false")));

        SppDomain override = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit))
                            compensate(releaseCredit) reserveCredit;
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit)) releaseCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                    releaseCredit{
                        account.availableCredit += amount.payableAmount;
                        amount.occupiedAmount -= amount.payableAmount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    config strictAppDdlReplacement = false;
                    config strictQuantumServiceInternalProtocol = false;
                    config strictQuantumServiceExternalImplementation = false;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.internal.example.org";
                    }
                }
                """);

        assertFalse(override.validateAppDeclarations().toString(), override.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains(
                        "Quantum service reserveCredit still declares APP object recovery protocol")));
    }

    @Test
    public void phyStrictAppMigrationConfigIncludesQuantumServiceExternalImplementationGate() throws Exception {
        SppDomain strict = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "${quantum.endpoint:https://quantum.example.test}";
                    }
                }
                """);

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum service store serviceQuantum is only a contract boundary")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "reason=placeholderEndpoint")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=replaceQuantumServiceEndpointPlaceholder+bindProductionQuantumService")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=replaceQuantumServiceEndpointPlaceholder+bindProductionQuantumService")));

        SppDomain override = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictAppMigration = true;
                    config strictRoleOnlyServiceBoundary = false;
                    config strictQuantumServiceExternalImplementation = false;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "${quantum.endpoint:https://quantum.example.test}";
                    }
                }
                """);

        assertFalse(override.validateAppDeclarations().toString(), override.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains(
                        "Quantum service store serviceQuantum is only a contract boundary")));
    }

    @Test
    public void phyStrictConfigRejectsUnknownDuplicateOrNonBooleanKeys() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                atomic query ping();
                """, """
                domain commerce;
                scope{ local{ ping; } }
                """, """
                domain commerce;
                phy{
                    config strictAppDdlReplacement = maybe;
                    config strictExternalRemoteAdapter = true;
                    config strictExternalRemoteAdapter = false;
                    config unknownStrict = true;
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error ->
                error.contains("PHY config strictExternalRemoteAdapter is declared multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error ->
                error.contains("PHY config strictAppDdlReplacement must be true or false")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error ->
                error.contains("Unsupported PHY config unknownStrict")));
    }

    @Test
    public void quantumServiceExternalImplementationValidationRejectsContractOnlyEndpoint() throws Exception {
        SppDomain contractOnly = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                phy{
                    config strictQuantumServiceExternalImplementation = true;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "${quantum.endpoint:https://quantum.example.test}";
                    }
                }
                """);

        List<String> errors = contractOnly.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum service store serviceQuantum is only a contract boundary")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "strict production profiles must bind a real absolute http(s) quantum service endpoint")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "reason=placeholderEndpoint")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=replaceQuantumServiceEndpointPlaceholder+bindProductionQuantumService")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=replaceQuantumServiceEndpointPlaceholder+bindProductionQuantumService")));

        SppDomain developmentEndpoint = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                phy{
                    config strictQuantumServiceExternalImplementation = true;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "${quantum.endpoint:http://localhost:8080/quantum}";
                    }
                }
                """);

        List<String> developmentEndpointErrors = developmentEndpoint.validateAppDeclarations();

        assertTrue(developmentEndpointErrors.toString(), developmentEndpointErrors.stream()
                .anyMatch(error -> error.contains("reason=developmentEndpoint")));
        assertTrue(developmentEndpointErrors.toString(), developmentEndpointErrors.stream()
                .anyMatch(error -> error.contains(
                        "actions=replaceDevelopmentQuantumServiceEndpoint+bindProductionQuantumService")));
        assertTrue(developmentEndpointErrors.toString(), developmentEndpointErrors.stream()
                .anyMatch(error -> error.contains(
                        "action=replaceDevelopmentQuantumServiceEndpoint+bindProductionQuantumService")));

        SppDomain missingEndpoint = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                phy{
                    config strictQuantumServiceExternalImplementation = true;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                    }
                }
                """);

        List<String> missingEndpointErrors = missingEndpoint.validateAppDeclarations();

        assertTrue(missingEndpointErrors.toString(), missingEndpointErrors.stream().anyMatch(error -> error.contains(
                "reason=endpoint")));
        assertTrue(missingEndpointErrors.toString(), missingEndpointErrors.stream().anyMatch(error -> error.contains(
                "actions=configureQuantumServiceEndpoint+bindProductionQuantumService")));
        assertTrue(missingEndpointErrors.toString(), missingEndpointErrors.stream().anyMatch(error -> error.contains(
                "action=configureQuantumServiceEndpoint+bindProductionQuantumService")));

        SppDomain invalidEndpoint = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                phy{
                    config strictQuantumServiceExternalImplementation = true;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "/quantum/commit";
                    }
                }
                """);

        List<String> invalidEndpointErrors = invalidEndpoint.validateAppDeclarations();

        assertTrue(invalidEndpointErrors.toString(), invalidEndpointErrors.stream().anyMatch(error -> error.contains(
                "reason=endpointInvalid")));
        assertTrue(invalidEndpointErrors.toString(), invalidEndpointErrors.stream().anyMatch(error -> error.contains(
                "actions=configureAbsoluteHttpQuantumServiceEndpoint+bindProductionQuantumService")));
        assertTrue(invalidEndpointErrors.toString(), invalidEndpointErrors.stream().anyMatch(error -> error.contains(
                "action=configureAbsoluteHttpQuantumServiceEndpoint+bindProductionQuantumService")));

        String previousQuantumEndpoint = System.getProperty("quantum.endpoint");
        try {
            System.setProperty("quantum.endpoint", "https://quantum.internal.example.org/commit");
            SppDomain systemPropertyEndpoint = compileDomain("""
                    domain commerce;
                    Entity CreditAccount{ quantum double availableCredit; }
                    Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                    quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                    """, """
                    domain commerce;
                    header baseIn{ unique String globalSerialNumber; }
                    header baseOut{ notnull String appId; }
                    quantumStore serviceQuantum{
                        attribute CreditAccount.availableCredit;
                        attribute OrderAmount.occupiedAmount;
                    }
                    scope{
                        local(in baseIn, out baseOut){
                            quantum resource(creditAccounting,
                                CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                        }
                    }
                    services{
                        reserveCredit{
                            account.availableCredit -= amount.payableAmount;
                            amount.occupiedAmount += amount.payableAmount;
                        }
                    }
                    phy{
                        config strictQuantumServiceExternalImplementation = true;
                        quantumStore serviceQuantum{
                            adapter quantumService;
                            config endpoint = "${quantum.endpoint}";
                        }
                    }
                    """);

            assertTrue(systemPropertyEndpoint.validateAppDeclarations().toString(),
                    systemPropertyEndpoint.validateAppDeclarations().isEmpty());
        } finally {
            if (previousQuantumEndpoint == null) {
                System.clearProperty("quantum.endpoint");
            } else {
                System.setProperty("quantum.endpoint", previousQuantumEndpoint);
            }
        }

        SppDomain ready = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; double payableAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local(in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                services{
                    reserveCredit{
                        account.availableCredit -= amount.payableAmount;
                        amount.occupiedAmount += amount.payableAmount;
                    }
                }
                phy{
                    config strictQuantumServiceExternalImplementation = true;
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "${quantum.endpoint:https://quantum.internal.example.org}";
                    }
                }
                """);

        assertTrue(ready.validateAppDeclarations().toString(), ready.validateAppDeclarations().isEmpty());
    }

    @Test
    public void quantumServiceInternalProtocolValidationRejectsObjectRecoveryProtocol() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update releaseCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount)
                            snapshot(argument(0, CreditAccount.availableCredit))
                            compensate(releaseCredit) reserveCredit;
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) releaseCredit;
                    }
                }
                phy{
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                    }
                }
                """);

        List<String> errors = invalid.validateQuantumServiceInternalProtocolDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Quantum service reserveCredit still declares APP object recovery protocol")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "objectSnapshot, objectCompensation, frameworkIdempotency")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "pure quantum service profiles must keep idempotency, entanglement audit and reversal inside the quantum service protocol")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE+objectSnapshotManaged=false"
                        + "+reversalExecution=INPUT_NEGATIVE_DELTA+objectCompensation=false"
                        + "+idempotencyBoundary=QUANTUM_SERVICE_INTERNAL+objectRecoveryRequired=false")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE+objectSnapshotManaged=false"
                        + "+reversalExecution=INPUT_NEGATIVE_DELTA+objectCompensation=false"
                        + "+idempotencyBoundary=QUANTUM_SERVICE_INTERNAL+objectRecoveryRequired=false")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore serviceQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                phy{
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                    }
                }
                """);

        assertTrue(valid.validateQuantumServiceInternalProtocolDeclarations().toString(),
                valid.validateQuantumServiceInternalProtocolDeclarations().isEmpty());
    }

    @Test
    public void externalRemoteAdapterValidationRejectsReferenceAdapters() throws Exception {
        SppDomain reference = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter jdbcObjectRepository;
                        config datasourceUrl = "jdbc:h2:mem:spp-object-store;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config table = "spp_business_object";
                    }
                }
                """);

        List<String> errors = reference.validateExternalRemoteAdapterDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service queryCustomer is still backed by reference adapter objectRepositoryRemote")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "bind a real external domain adapter in PHY for production profiles")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "target=REMOTE_SERVICE/queryCustomer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=replaceReferenceRemoteAdapter+bindExternalDomainAdapter")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=replaceReferenceRemoteAdapter+bindExternalDomainAdapter")));

        SppDomain external = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "http://localhost:8080/queryCustomer";
                    }
                }
                """);

        assertTrue(external.validateExternalRemoteAdapterDeclarations().toString(),
                external.validateExternalRemoteAdapterDeclarations().isEmpty());
    }

    @Test
    public void remoteExternalEndpointValidationRejectsDevelopmentEndpoints() throws Exception {
        SppDomain development = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRemoteExternalEndpoint = true;
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "${credit.customer.endpoint:http://localhost:8080/queryCustomer}";
                    }
                }
                """);

        List<String> errors = development.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Remote service queryCustomer uses non-production external endpoint")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "reason=developmentEndpoint")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "actions=replaceDevelopmentRemoteEndpoint+bindProductionExternalDomain")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "action=replaceDevelopmentRemoteEndpoint+bindProductionExternalDomain")));

        SppDomain missingEndpoint = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRemoteExternalEndpoint = true;
                    remote queryCustomer{
                        adapter httpJson;
                    }
                }
                """);

        List<String> missingEndpointErrors = missingEndpoint.validateAppDeclarations();

        assertTrue(missingEndpointErrors.toString(), missingEndpointErrors.stream().anyMatch(error -> error.contains(
                "reason=endpoint")));
        assertTrue(missingEndpointErrors.toString(), missingEndpointErrors.stream().anyMatch(error -> error.contains(
                "actions=configureRemoteEndpoint+bindProductionExternalDomain")));
        assertTrue(missingEndpointErrors.toString(), missingEndpointErrors.stream().anyMatch(error -> error.contains(
                "action=configureRemoteEndpoint+bindProductionExternalDomain")));

        SppDomain invalidEndpoint = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRemoteExternalEndpoint = true;
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "/queryCustomer";
                    }
                }
                """);

        List<String> invalidEndpointErrors = invalidEndpoint.validateAppDeclarations();

        assertTrue(invalidEndpointErrors.toString(), invalidEndpointErrors.stream().anyMatch(error -> error.contains(
                "reason=endpointInvalid")));
        assertTrue(invalidEndpointErrors.toString(), invalidEndpointErrors.stream().anyMatch(error -> error.contains(
                "actions=configureAbsoluteHttpRemoteEndpoint+bindProductionExternalDomain")));
        assertTrue(invalidEndpointErrors.toString(), invalidEndpointErrors.stream().anyMatch(error -> error.contains(
                "action=configureAbsoluteHttpRemoteEndpoint+bindProductionExternalDomain")));

        SppDomain production = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRemoteExternalEndpoint = true;
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "${credit.customer.endpoint:https://customer.internal.example.org/queryCustomer}";
                    }
                }
                """);

        assertTrue(production.validateAppDeclarations().toString(),
                production.validateAppDeclarations().isEmpty());
    }

    @Test
    public void platformColumnBusinessShadowValidationRejectsTechnicalSppFields() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; String name; }
                Role Goods{ business("skuId business credential") String skuId; String title; }
                take Product as Goods;
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){
                        primarykey String productRowId;
                        notnull String productId;
                    }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String goodsRowId;
                        notnull String skuId;
                    }
                }
                """);

        List<String> errors = invalid.validatePlatformColumnBusinessShadowDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP DDL table PRODUCT already carries technical-looking SPP fields Product.productId")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP DDL table GOODS already carries technical-looking SPP fields Goods.skuId")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "remove them from the SPP business type unless they are true business credentials")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "objectType=Product")
                && error.contains("paths=Product.productId")
                && error.contains("actions=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")
                && error.contains("action=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "objectType=Goods")
                && error.contains("paths=Goods.skuId")
                && error.contains("actions=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")
                && error.contains("action=removeTechnicalIdentityFromSppBusinessType"
                + "+keepOnlyTrueBusinessCredentials")));

        SppDomain businessCredential = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; String name; }
                Role Goods{ business("skuId business credential") String skuId; String title; }
                take Product as Goods;
                """, """
                domain commerce;
                tables entities{
                    @businessCredential(fields="productId")
                    table PRODUCT(Product){
                        primarykey String productId;
                    }
                }
                tables roles{
                    @businessCredential(field="skuId")
                    table GOODS(Goods){
                        primarykey String skuId;
                    }
                }
                """);

        assertTrue(businessCredential.validatePlatformColumnBusinessShadowDeclarations().toString(),
                businessCredential.validatePlatformColumnBusinessShadowDeclarations().isEmpty());
        assertFalse(businessCredential.appSemanticMigrationPlans().toString(),
                businessCredential.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "SPP_TECHNICAL_FIELD_PLATFORM_COLUMN".equals(plan.migrationType())));
        assertTrue(businessCredential.appSemanticMigrationPlans().toString(),
                businessCredential.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                                && "PRODUCT".equals(plan.tableName())
                                && plan.paths().contains("PRODUCT.productId")));
        assertTrue(businessCredential.appSemanticMigrationPlans().toString(),
                businessCredential.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                                && "GOODS".equals(plan.tableName())
                                && plan.paths().contains("GOODS.skuId")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Product{ String name; }
                Role Goods{ String title; }
                take Product as Goods;
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){
                        primarykey String productId;
                    }
                }
                tables roles{
                    table GOODS(Goods){
                        primarykey String skuId;
                    }
                }
                """);

        assertTrue(valid.validatePlatformColumnBusinessShadowDeclarations().toString(),
                valid.validatePlatformColumnBusinessShadowDeclarations().isEmpty());
    }

    @Test
    public void appSemanticMigrationWarningsIgnoreImplicitDefaultCrudParticipants() throws Exception {
        SppDomain domain = compileDomainWithDefaultServices("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                """, """
                domain commerce;
                scope{
                    local{
                        _default;
                    }
                }
                """);

        assertFalse(domain.appSemanticMigrationPlans().toString(),
                domain.appSemanticMigrationPlans().stream()
                        .anyMatch(plan -> "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())));
        assertFalse(domain.appSemanticMigrationWarnings().toString(),
                domain.appSemanticMigrationWarnings().stream()
                        .anyMatch(warning -> warning.contains("non-Role participants")));
    }

    @Test
    public void appSemanticMigrationWarningsExposeObjectBackedKeysAndReferenceAdapters() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Contract SalesOrder{ String orderNo; String status; }
                atomic query queryOrder(SalesOrder order);
                atomic query queryCustomer(Customer customer);
                atomic update openSalesOrder(SalesOrder order);
                saga submitOrder(SalesOrder order){
                    transaction{ openSalesOrder(order); }
                }
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey(SalesOrder.orderNo);
                        index orderByStatus(SalesOrder.status);
                    }
                }
                scope{
                    local{
                        repository(argument(0, SalesOrder.orderNo), order) queryOrder;
                        saga submitOrder;
                        at resource(salesOrder)
                            snapshot(argument(0, SalesOrder.orderNo))
                            snapshot(current(SalesOrder.orderNo)) openSalesOrder;
                    }
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectIdPath = "customerId";
                    }
                }
                """);

        List<String> warnings = domain.appSemanticMigrationWarnings();

        assertTrue(warnings.toString(), warnings.stream()
                .anyMatch(warning -> warning.contains("APP DDL table SALES_ORDER primary key paths orderNo")
                        && warning.contains("object-backed")));
        assertTrue(warnings.toString(), warnings.stream()
                .anyMatch(warning -> warning.contains("APP DDL table SALES_ORDER index paths SalesOrder.status")
                        && warning.contains("object-backed")));
        assertTrue(warnings.toString(), warnings.stream()
                .anyMatch(warning -> warning.contains("Repository query queryOrder")
                        && warning.contains("explicit repository argument paths orderNo")));
        assertTrue(warnings.toString(), warnings.stream()
                .anyMatch(warning -> warning.contains("Snapshot rule for service openSalesOrder object SalesOrder")
                        && warning.contains("explicit object key paths orderNo")));
        assertFalse(warnings.toString(), warnings.stream()
                .anyMatch(warning -> warning.contains(
                                "Service openSalesOrder still accepts non-Role participants openSalesOrder.order:SalesOrder")
                        && warning.contains("Move Contract values out of the participant list")));
        assertTrue(warnings.toString(), warnings.stream()
                .anyMatch(warning -> warning.contains(
                                "Service queryCustomer still accepts non-Role participants queryCustomer.customer:Customer")
                        && warning.contains("remote Role adapter")));
        assertTrue(domain.appSemanticMigrationPlans().toString(), domain.appSemanticMigrationPlans().stream()
                .anyMatch(plan -> "REMOTE_REFERENCE_ADAPTER".equals(plan.migrationType())
                        && "queryCustomer".equals(plan.serviceName())
                        && "objectRepositoryRemote".equals(plan.adapterName())
                        && "replaceReferenceRemoteAdapter+bindExternalDomainAdapter"
                        .equals(plan.migrationAction())));
    }

    @Test
    public void repositoryHydrationCanInferNamedAppDdlIndexes() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String phoneNo; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                atomic query queryCreditAccount(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomerId(CreditAccount.customer.customerId);
                        index creditAccountByCustomerPhone(CreditAccount.customer.phoneNo);
                    }
                }
                scope{
                    local{
                        repository(CreditAccount by index creditAccountByCustomerId) queryCreditAccount;
                    }
                }
                """);

        ScopeItem queryCreditAccount = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryCreditAccount.getRepositoryHydrationRules().get(0);
        assertEquals("account", rule.responseMethodName());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(0, "CreditAccount.customer.customerId")),
                rule.keyTargets());
        assertTrue(rule.appDdlInferred());
    }

    @Test
    public void repositoryHydrationCanInferCompositeNamedAppDdlIndexes() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Store{ String name; }
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role Inventory{ int availableQty; }
                take Store, Goods as Inventory;
                atomic query queryInventoryByStoreGoods(Inventory selection, Inventory[] inventory);
                """, """
                domain commerce;
                tables entities{
                    table STORE(Store){ primarykey(Store.name); }
                    table PRODUCT(Product){ primarykey(Product.productId); }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_skuid;
                        index inventoryByStoreGoods(store_name, goods_skuid);
                    }
                }
                scope{
                    local{
                        repository(Inventory by index inventoryByStoreGoods) queryInventoryByStoreGoods;
                    }
                }
                """);

        ScopeItem queryInventoryByStoreGoods = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryInventoryByStoreGoods.getRepositoryHydrationRules().get(0);
        assertEquals("inventory", rule.responseMethodName());
        assertEquals(List.of(
                        new RepositoryHydrationRule.KeyTarget(0, "Store.name", "Inventory.store_name"),
                        new RepositoryHydrationRule.KeyTarget(0, "Goods.skuId", "Inventory.goods_skuid")),
                rule.keyTargets());
        assertEquals("APP_DDL_ROLE_TAKE", rule.sourceHint());
    }

    @Test
    public void repositoryHydrationCanInferNamedAppDdlIndexesFromForeignArgumentType() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Store{ String name; }
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role Inventory{ int availableQty; }
                take Store, Goods as Inventory;
                atomic query queryInventoryByGoods(Goods goods, Inventory[] inventory);
                """, """
                domain commerce;
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
                    }
                }
                scope{
                    local{
                        repository(Inventory by index inventoryByGoods) queryInventoryByGoods;
                    }
                }
                """);

        ScopeItem queryInventoryByGoods = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryInventoryByGoods.getRepositoryHydrationRules().get(0);
        assertEquals("inventory", rule.responseMethodName());
        assertEquals("Inventory", rule.getObjectType());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(
                        0, "Goods.skuId", "Inventory.goods_skuid")),
                rule.keyTargets());
        assertEquals("APP_DDL_ROLE_TAKE", rule.sourceHint());
    }

    @Test
    public void repositoryHydrationCanInferCompositeNamedAppDdlIndexesFromMultipleArgumentTypes() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Store{ String name; }
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role Inventory{ int availableQty; }
                take Store, Goods as Inventory;
                atomic query queryInventoryByStoreGoods(Store store, Goods goods, Inventory[] inventory);
                """, """
                domain commerce;
                tables entities{
                    table STORE(Store){ primarykey(Store.name); }
                    table PRODUCT(Product){ primarykey(Product.productId); }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_skuid;
                        index inventoryByStoreGoods(store_name, goods_skuid);
                    }
                }
                scope{
                    local{
                        repository(Inventory by index inventoryByStoreGoods) queryInventoryByStoreGoods;
                    }
                }
                """);

        ScopeItem queryInventoryByStoreGoods = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = queryInventoryByStoreGoods.getRepositoryHydrationRules().get(0);
        assertEquals("inventory", rule.responseMethodName());
        assertEquals(List.of(
                        new RepositoryHydrationRule.KeyTarget(0, "Store.name", "Inventory.store_name"),
                        new RepositoryHydrationRule.KeyTarget(1, "Goods.skuId", "Inventory.goods_skuid")),
                rule.keyTargets());
        assertEquals("APP_DDL_ROLE_TAKE", rule.sourceHint());
    }

    @Test
    public void repositoryHydrationCanInferContractIndexedRoleCollections() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ int requestedQty; }
                take Goods as SalesItem;
                Contract SalesOrder{ String orderNo; SalesItem[] lines; }
                atomic query querySalesItemsByOrder(SalesOrder order, SalesItem[] salesItem);
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){ primarykey(Product.productId); }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table SALES_ITEM(SalesItem){
                        primarykey notnull String order_no;
                        primarykey notnull String goods_skuid;
                        index salesItemByOrder(order_no);
                    }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey(SalesOrder.orderNo); }
                }
                scope{
                    local{
                        repository(SalesItem by index salesItemByOrder) querySalesItemsByOrder;
                    }
                }
                """);

        ScopeItem querySalesItemsByOrder = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = querySalesItemsByOrder.getRepositoryHydrationRules().get(0);
        assertEquals("salesItem", rule.responseMethodName());
        assertEquals("SalesItem", rule.getObjectType());
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(
                        0, "SalesOrder.orderNo", "SalesItem.order_no")),
                rule.keyTargets());
        assertEquals("APP_DDL_NAMED_INDEX", rule.sourceHint());
        assertTrue(rule.appDdlInferred());
    }

    @Test
    public void strictRoleOnlyBoundaryAcceptsContractQueryKeysAndRoleResults() throws Exception {
        SppDomain strict = compileDomainWithRepository("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role SalesItem{ int requestedQty; }
                take Goods as SalesItem;
                Contract SalesOrder{ String orderNo; SalesItem[] lines; }
                atomic query querySalesItemsByOrder(SalesOrder order, SalesItem[] salesItem);
                """, """
                domain commerce;
                tables entities{
                    table PRODUCT(Product){ primarykey(Product.productId); }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table SALES_ITEM(SalesItem){
                        primarykey notnull String order_no;
                        primarykey notnull String goods_skuid;
                        index salesItemByOrder(order_no);
                    }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey(SalesOrder.orderNo); }
                }
                scope{
                    local{
                        repository(SalesItem by index salesItemByOrder) querySalesItemsByOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    config strictRoleOnlyServiceBoundary = true;
                }
                """);

        ScopeItem querySalesItemsByOrder = strict.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        RepositoryHydrationRule rule = querySalesItemsByOrder.getRepositoryHydrationRules().get(0);
        assertEquals(List.of(new RepositoryHydrationRule.KeyTarget(
                        0, "SalesOrder.orderNo", "SalesItem.order_no")),
                rule.keyTargets());

        List<String> errors = strict.validateAppDeclarations();

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void repositoryHydrationCannotInferTechnicalPrimaryKeysAsObjectKeys() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ double availableCredit; }
                atomic query queryCreditAccount(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                    }
                }
                scope{
                    local{
                        repository(CreditAccount) queryCreditAccount;
                    }
                }
                """);

        List<String> errors = invalid.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository CreditAccount cannot infer APP DDL primary key creditAccountId"
                        + " because it is not a business object field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "declare a business primarykey(...), APP DDL index, or use repository(argument(index, path))"
                        + " as a compatibility escape hatch")));
    }

    @Test
    public void snapshotRulesCanInferNamedAppDdlIndexes() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String phoneNo; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomerId(CreditAccount.customer.customerId);
                        index creditAccountByCustomerPhone(CreditAccount.customer.phoneNo);
                    }
                }
                scope{
                    local{
                        at resource(creditAccounting)
                            snapshot(CreditAccount by index creditAccountByCustomerId)
                            reserveCredit;
                    }
                }
                """);

        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("CreditAccount.customer.customerId", rules.get(0).objectPath());
        assertTrue(rules.get(0).appDdlInferred());
        assertTrue(reserveCredit.getService().getObjectSnapshotRulesMethod()
                .contains("ObjectSnapshotRule.appDdlArgument(0, \"CreditAccount\", \"customer.customerId\""));
    }

    @Test
    public void snapshotRulesCanInferNamedCompositeAppDdlIndexes() throws Exception {
        SppDomain domain = compileDomainWithRepository("""
                domain commerce;
                Entity Store{ String name; }
                Role Goods{ business("skuId business credential") String skuId; }
                Role Inventory{ int availableQty; }
                take Store, Goods as Inventory;
                atomic update deductInventory(Inventory inventory);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                        notnull String store_name;
                        notnull String goods_skuid;
                        index inventoryByStoreGoods(store_name, goods_skuid);
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(inventoryAccounting)
                            snapshot(Inventory by index inventoryByStoreGoods)
                            deductInventory;
                    }
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        ScopeItem deductInventory = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = deductInventory.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("Inventory.store_name", rules.get(0).objectPath());
        assertEquals(List.of("store_name", "goods_skuid"), rules.get(0).objectIdPaths());
        assertTrue(rules.get(0).appDdlInferred());
    }

    @Test
    public void snapshotRulesCanInferAppDdlKeysFromAggregateMembers() throws Exception {
        SppDomain domain = compileDomainWithRepository("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomer(CreditAccount.customer.customerId);
                    }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey(SalesOrder.orderNo); }
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(creditAccounting)
                            snapshot(CreditAccount by index creditAccountByCustomer)
                            reserveCredit;
                    }
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("CreditAccount.account.customer.customerId", rules.get(0).objectPath());
        assertEquals(List.of("account.customer.customerId"), rules.get(0).objectIdPaths());
        assertTrue(rules.get(0).appDdlInferred());
        assertTrue(reserveCredit.getService().getObjectSnapshotRulesMethod()
                .contains("ObjectSnapshotRule.appDdlArgument(0, \"CreditAccount\", "
                        + "\"account.customer.customerId\""));
    }

    @Test
    public void snapshotRulesPreferDirectArgumentsBeforeAggregateMembers() throws Exception {
        SppDomain domain = compileDomainWithRepository("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account, SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomer(CreditAccount.customer.customerId);
                    }
                }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey(SalesOrder.orderNo); }
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(creditAccounting)
                            snapshot(CreditAccount by index creditAccountByCustomer)
                            reserveCredit;
                    }
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("CreditAccount.customer.customerId", rules.get(0).objectPath());
        assertEquals(List.of("customer.customerId"), rules.get(0).objectIdPaths());
        assertTrue(rules.get(0).appDdlInferred());
    }

    @Test
    public void snapshotRulesRejectAmbiguousAggregateMembers() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                Contract SalesOrder{ CreditAccount debitAccount; CreditAccount creditAccount; }
                atomic update reserveCredit(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomer(CreditAccount.customer.customerId);
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(creditAccounting)
                            snapshot(CreditAccount by index creditAccountByCustomer)
                            reserveCredit;
                    }
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot CreditAccount is ambiguous on service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "declare a named APP DDL index or use snapshot(argument(index, path))"
                        + " as a compatibility escape hatch")));
    }

    @Test
    public void snapshotRulesCanDisambiguateMultipleIndexesByName() throws Exception {
        SppDomain ambiguous = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String phoneNo; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomerId(CreditAccount.customer.customerId);
                        index creditAccountByCustomerPhone(CreditAccount.customer.phoneNo);
                    }
                }
                scope{
                    local{
                        at resource(creditAccounting)
                            snapshot(CreditAccount by Customer)
                            reserveCredit;
                    }
                }
                """);

        List<String> errors = ambiguous.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot CreditAccount by Customer APP DDL index must resolve to exactly one object id path")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "use snapshot(CreditAccount by index <name>)")));

        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String phoneNo; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index creditAccountByCustomerId(CreditAccount.customer.customerId);
                        index creditAccountByCustomerPhone(CreditAccount.customer.phoneNo);
                    }
                }
                scope{
                    local{
                        at resource(creditAccounting)
                            snapshot(CreditAccount by index creditAccountByCustomerPhone)
                            reserveCredit;
                    }
                }
                """);

        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("CreditAccount.customer.phoneNo", rules.get(0).objectPath());
    }

    @Test
    public void snapshotRulesCannotInferTechnicalPrimaryKeysAsObjectKeys() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                    }
                }
                scope{
                    local{
                        at resource(creditAccounting) snapshot(CreditAccount) reserveCredit;
                    }
                }
                """);

        List<String> errors = invalid.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot CreditAccount cannot infer APP DDL primary key creditAccountId"
                        + " because it is not a business object field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "declare a business primarykey(...), APP DDL index, or use snapshot(argument(index, path))"
                        + " as a compatibility escape hatch")));
    }

    @Test
    public void snapshotRulesCanInferCompositeAppDdlPrimaryKeys() throws Exception {
        SppDomain domain = compileDomainWithRepository("""
                domain commerce;
                Entity Store{ String name; }
                Role Goods{ business("skuId business credential") String skuId; }
                Role Inventory{ int availableQty; }
                take Store, Goods as Inventory;
                atomic update deductInventory(Inventory inventory);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_skuid;
                        index inventoryByStoreGoods(store_name, goods_skuid);
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(inventoryAccounting)
                            snapshot(Inventory by index inventoryByStoreGoods)
                            deductInventory;
                    }
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        ScopeItem deductInventory = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = deductInventory.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("Inventory.store_name", rules.get(0).objectPath());
        assertEquals(List.of("store_name", "goods_skuid"), rules.get(0).objectIdPaths());
        assertTrue(rules.get(0).appDdlInferred());
        var write = deductInventory.getPersistenceRules().get(0);
        assertEquals("Inventory", write.objectType());
        SppService query = (SppService) domain.getMapsOfClass().get("FindInventory");
        assertEquals("APP_DDL_ROLE_TAKE_PRIMARY_KEY", query.getScopeItem().getRepositoryHydrationRules().get(0).sourceHint());
        String generated = deductInventory.getService().getPersistenceRuleMethods();
        assertTrue(generated, generated.contains("\"Inventory\", java.util.List.of(\"@performer:Store.name\", \"@performer:Goods.skuId\")"));
    }

    @Test
    public void snapshotRulesCanInferRoleTakePlatformPrimaryKeys() throws Exception {
        SppDomain domain = compileDomainWithRepository("""
                domain commerce;
                Entity Store{ String name; }
                Role Goods{ business("skuId business credential") String skuId; }
                Role Inventory{ int availableQty; }
                take Store, Goods as Inventory;
                atomic update deductInventory(Inventory inventory);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId; }
                tables entities{
                    table STORE(Store){ primarykey(Store.name); }
                }
                tables roles{
                    table GOODS(Goods){ primarykey(Goods.skuId); }
                    table INVENTORY(Inventory){
                        primarykey notnull String store_name;
                        primarykey notnull String goods_skuid;
                    }
                }
                scope{
                    local(in baseIn, out baseOut){
                        at resource(inventoryAccounting)
                            snapshot(Inventory)
                            deductInventory;
                    }
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        ScopeItem deductInventory = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = deductInventory.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("Inventory.name", rules.get(0).objectPath());
        assertEquals(List.of("name", "skuId"), rules.get(0).objectIdPaths());
        assertTrue(rules.get(0).appDdlInferred());
        assertTrue(deductInventory.getService().getObjectSnapshotRulesMethod()
                .contains("ObjectSnapshotRule.appDdlArgument(0, \"Inventory\", "
                        + "List.of(\"name\", \"skuId\")"));
    }

    @Test
    public void snapshotRulesCanInferAppDdlPrimaryKeys() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Contract SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey(SalesOrder.orderNo); }
                }
                scope{
                    local{
                        at resource(salesOrder) snapshot(SalesOrder) openSalesOrder;
                    }
                }
                """);

        ScopeItem openSalesOrder = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = openSalesOrder.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("SalesOrder.orderNo", rules.get(0).objectPath());
        assertEquals(ObjectSnapshotRule.Timing.afterCommit, rules.get(0).timing());
        assertTrue(rules.get(0).appDdlInferred());
    }

    @Test
    public void snapshotRulesCanInferAppDdlIndexes() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ Customer customer; double availableCredit; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                tables entities{
                    table CREDIT_ACCOUNT(CreditAccount){
                        primarykey String creditAccountId;
                        index(CreditAccount.customer.customerId);
                    }
                }
                scope{
                    local{
                        at resource(creditAccounting)
                            snapshot(CreditAccount by Customer, current(CreditAccount.customer.customerId))
                            reserveCredit;
                    }
                }
                """);

        ScopeItem reserveCredit = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = reserveCredit.getObjectSnapshotRules();
        assertEquals(2, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("CreditAccount.customer.customerId", rules.get(0).objectPath());
        assertEquals(ObjectSnapshotRule.Source.current, rules.get(1).source());
    }

    @Test
    public void snapshotRulesCanUseAppDdlRolePlatformColumnsWithoutRolePerformerFields() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity Product{ business("productId business credential") String productId; }
                Role Goods{ business("skuId business credential") String skuId; }
                take Product as Goods;
                Role Inventory{ int availableQty; }
                take Goods as Inventory;
                atomic update deductInventory(Inventory inventory);
                """, """
                domain commerce;
                tables roles{
                    table INVENTORY(Inventory){
                        primarykey String inventoryId;
                        notnull String goods_skuid;
                        index inventoryByGoods(goods_skuid);
                    }
                }
                scope{
                    local{
                        at resource(inventoryAccounting)
                            snapshot(argument(0, Inventory.goods_skuid))
                            deductInventory;
                    }
                }
                """);

        ScopeItem deductInventory = domain.getRuleBlock().getAppScope()
                .getScopelist().get(0).getScopeItems().get(0);
        List<ObjectSnapshotRule> rules = deductInventory.getObjectSnapshotRules();
        assertEquals(1, rules.size());
        assertEquals(ObjectSnapshotRule.Source.argument, rules.get(0).source());
        assertEquals(0, rules.get(0).argumentIndex());
        assertEquals("Inventory.goods_skuid", rules.get(0).objectPath());
        assertEquals("goods_skuid", rules.get(0).getObjectIdPath());
    }

    @Test
    public void repositoryHydrationMustNotRepeatResponseTargets() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String phoneNo; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer)
                            repository(argument(0, Customer.phoneNo), customer) queryCustomer;
                    }
                }
                """);

        List<String> errors = invalid.validateRepositoryHydrationDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Repository hydration service queryCustomer declares duplicate response target customer"));
    }

    @Test
    public void repositoryHydrationMustTargetQueryResponseField() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """);
        assertTrue(valid.validateRepositoryHydrationDeclarations().isEmpty());

        SppDomain missingResponseField = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), buyer) queryCustomer;
                    }
                }
                """);
        List<String> missingErrors = missingResponseField.validateRepositoryHydrationDeclarations();
        assertEquals(1, missingErrors.size());
        assertTrue(missingErrors.get(0), missingErrors.get(0).contains(
                "Repository query queryCustomer must hydrate a declared participant or its collection: buyer"));

        SppDomain wrongType = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                Entity Goods{ business("skuId business credential") String skuId; String title; }
                atomic query queryCustomer(Customer customer, Goods goods);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), goods) queryCustomer;
                    }
                }
                """);
        List<String> typeErrors = wrongType.validateRepositoryHydrationDeclarations();
        assertEquals(1, typeErrors.size());
        assertTrue(typeErrors.get(0), typeErrors.get(0).contains(
                "Repository query target goods must have participant type Customer"));

        SppDomain arrayResponse = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer selection, Customer[] customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """);
        List<String> arrayErrors = arrayResponse.validateRepositoryHydrationDeclarations();
        assertTrue(arrayErrors.toString(), arrayErrors.isEmpty());

        SppDomain nestedArrayResponse = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer selection, Customer[][] customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), customer) queryCustomer;
                    }
                }
                """);
        List<String> nestedArrayErrors = nestedArrayResponse.validateRepositoryHydrationDeclarations();
        assertEquals(1, nestedArrayErrors.size());
        assertTrue(nestedArrayErrors.get(0), nestedArrayErrors.get(0).contains(
                "Repository queries support scalar participants or one-dimensional participant collections."));


        SppDomain multipleTargetErrors = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; String name; }
                Entity Goods{ business("skuId business credential") String skuId; String title; }
                atomic query queryCustomer(Customer customer, Goods goods);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(0, Customer.customerId), buyer)
                            repository(argument(0, Customer.customerId), goods) queryCustomer;
                    }
                }
                """);
        List<String> multipleErrors = multipleTargetErrors.validateRepositoryHydrationDeclarations();
        assertEquals(multipleErrors.toString(), 2, multipleErrors.size());
        assertTrue(multipleErrors.toString(), multipleErrors.stream().anyMatch(error -> error.contains(
                "Repository query queryCustomer must hydrate a declared participant or its collection: buyer")));
        assertTrue(multipleErrors.toString(), multipleErrors.stream().anyMatch(error -> error.contains(
                "Repository query target goods must have participant type Customer")));

    }

    @Test
    public void repositoryHydrationMustResolveArgumentObjectPath() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Address{ business("addressId business credential") String addressId; }
                Entity Customer{ business("customerId business credential") String customerId; String name; Address address; Address[] addresses; }
                Entity Goods{ business("skuId business credential") String skuId; String title; }
                atomic query queryCustomer(Customer customer);
                atomic query queryCustomerByGoods(Goods goods, Customer customer);
                atomic query queryCustomerWithoutId(Customer customer);
                atomic query queryCustomerBatch(Customer[] customers, Customer customer);
                atomic query queryCustomerByAddress(Customer customer);
                atomic query queryCustomerByAddressArray(Customer customer);
                """, """
                domain commerce;
                scope{
                    local{
                        repository(argument(1, Customer.customerId), customer) queryCustomer;
                        repository(argument(0, Customer.customerId), customer) queryCustomerByGoods;
                        repository(argument(0, Customer.missingId), customer) queryCustomerWithoutId;
                        repository(argument(0, Customer.customerId), customer) queryCustomerBatch;
                        repository(argument(0, Customer.address), customer) queryCustomerByAddress;
                        repository(argument(0, Customer.addresses.addressId), customer) queryCustomerByAddressArray;
                    }
                }
                """);

        List<String> errors = invalid.validateRepositoryHydrationDeclarations();
        assertEquals(errors.toString(), 6, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository argument index 1 is outside service queryCustomer parameter list")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository argument(0, Customer.customerId) object type must match service parameter type Goods")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository object id path Customer.missingId not found")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository query queryCustomerBatch collection keys must hydrate the same participant collection")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository object id path Customer.address must resolve to a scalar field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Repository object id path Customer.addresses.addressId must not traverse array field Customer.addresses")));
    }

    @Test
    public void compensationRulesMustReferenceCompatibleLocalAtomicUpdateServices() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Role OrderAmount{ double payableAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update releaseCredit(CreditAccount account, OrderAmount amount);
                Scenario serial submitOrder(CreditAccount account, OrderAmount amount){ transaction{} }
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditReservation) compensate(releaseCredit) reserveCredit;
                        at resource(creditRelease) releaseCredit;
                        saga compensate(releaseCredit) submitOrder;
                    }
                }
                """);
        assertTrue(valid.validateCompensationDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Role OrderAmount{ double payableAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic query queryCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditReservation) compensate(missingCredit, queryCredit, releaseCredit) reserveCredit;
                        queryCredit;
                        at resource(creditRelease) releaseCredit;
                    }
                }
                """);
        List<String> errors = invalid.validateCompensationDeclarations();
        assertEquals(3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Compensation service missingCredit referenced by reserveCredit not found")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Compensation service queryCredit referenced by reserveCredit must be a local atomic update service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Compensation service releaseCredit for reserveCredit must accept the same parameter list")));

        SppDomain remoteCompensation = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        releaseCredit;
                    }
                    remote{
                        compensate(releaseCredit) reserveCredit;
                    }
                }
                """);
        List<String> remoteErrors = remoteCompensation.validateCompensationDeclarations();
        assertEquals(1, remoteErrors.size());
        assertTrue(remoteErrors.get(0), remoteErrors.get(0).contains(
                "Compensation rule on service reserveCredit must be declared in local scope"));

        SppDomain unboundCompensation = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        compensate(releaseCredit) reserveCredit;
                        releaseCredit;
                    }
                }
                """);
        List<String> unboundErrors = unboundCompensation.validateCompensationDeclarations();
        assertEquals(unboundErrors.toString(), 2, unboundErrors.size());
        assertTrue(unboundErrors.toString(), unboundErrors.stream().anyMatch(error -> error.contains(
                "Compensated service reserveCredit must declare a local transaction mode or resource")));
        assertTrue(unboundErrors.toString(), unboundErrors.stream().anyMatch(error -> error.contains(
                "Compensation service releaseCredit referenced by reserveCredit must declare a local transaction mode or resource")));

        SppDomain invalidSource = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; }
                atomic query queryCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account){ transaction{} }
                """, """
                domain commerce;
                scope{
                    local{
                        compensate(releaseCredit) queryCredit;
                        at resource(creditRelease) releaseCredit;
                        compensate(releaseCredit) submitOrder;
                    }
                }
                """);
        List<String> invalidSourceErrors = invalidSource.validateCompensationDeclarations();
        assertEquals(invalidSourceErrors.toString(), 2, invalidSourceErrors.size());
        assertTrue(invalidSourceErrors.toString(), invalidSourceErrors.stream().anyMatch(error -> error.contains(
                "Compensation rule on service queryCredit must be declared on a local saga scenario or local atomic update transaction service")));
        assertTrue(invalidSourceErrors.toString(), invalidSourceErrors.stream().anyMatch(error -> error.contains(
                "Compensation rule on service submitOrder must be declared on a local saga scenario or local atomic update transaction service")));

        SppDomain duplicateCompensation = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditReservation) compensate(releaseCredit, releaseCredit) reserveCredit;
                        at resource(creditRelease) releaseCredit;
                    }
                }
                """);
        List<String> duplicateCompensationErrors = duplicateCompensation.validateCompensationDeclarations();
        assertEquals(duplicateCompensationErrors.toString(), 1, duplicateCompensationErrors.size());
        assertTrue(duplicateCompensationErrors.get(0), duplicateCompensationErrors.get(0).contains(
                "Compensation rule on service reserveCredit declares duplicate compensation service releaseCredit"));

        SppDomain selfCompensation = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditReservation) compensate(reserveCredit) reserveCredit;
                    }
                }
                """);
        List<String> selfCompensationErrors = selfCompensation.validateCompensationDeclarations();
        assertEquals(selfCompensationErrors.toString(), 1, selfCompensationErrors.size());
        assertTrue(selfCompensationErrors.get(0), selfCompensationErrors.get(0).contains(
                "Compensation service reserveCredit cannot compensate itself"));
    }

    @Test
    public void compensationTargetsMustNotDeclareNestedCompensationServices() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                atomic update auditReleaseFailure(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditReservation) compensate(releaseCredit) reserveCredit;
                        at resource(creditRelease) compensate(auditReleaseFailure) releaseCredit;
                        at resource(creditAudit) auditReleaseFailure;
                    }
                }
                """);

        List<String> errors = invalid.validateCompensationDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensation service releaseCredit referenced by reserveCredit cannot declare nested compensation services"));
    }

    @Test
    public void snapshotRulesMustBindLocalAtomicUpdateServices() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.accountNo)) reserveCredit;
                    }
                }
                """);
        assertTrue(valid.validateSnapshotDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic query queryCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account){ transaction{} }
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.accountNo)) queryCredit;
                        snapshot(argument(0, CreditAccount.accountNo)) submitOrder;
                    }
                }
                """);
        List<String> errors = invalid.validateSnapshotDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Snapshot rule on service queryCredit must bind a local atomic update service"));
        assertTrue(errors.get(1), errors.get(1).contains(
                "Snapshot rule on service submitOrder must bind a local atomic update service"));
    }

    @Test
    public void snapshotRulesMustResolveArgumentObjectPath() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Entity Customer{ business("customerId business credential") String customerId; }
                Entity SalesOrder{ String orderNo; Customer[] customers; }
                atomic update reserveCredit(CreditAccount account);
                atomic update reserveCreditForOrder(SalesOrder order);
                atomic update reserveCreditWithoutId(CreditAccount account);
                atomic update reserveCreditBatch(CreditAccount[] accounts);
                atomic update reserveCreditForOrderCustomer(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(1, CreditAccount.accountNo)) reserveCredit;
                        snapshot(argument(0, CreditAccount.accountNo)) reserveCreditForOrder;
                        snapshot(argument(0, CreditAccount.missingAccountNo)) reserveCreditWithoutId;
                        snapshot(argument(0, CreditAccount.accountNo)) reserveCreditBatch;
                        snapshot(argument(0, SalesOrder.customers.customerId)) reserveCreditForOrderCustomer;
                    }
                }
                """);

        List<String> errors = invalid.validateSnapshotDeclarations();
        assertEquals(errors.toString(), 5, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot argument index 1 is outside service reserveCredit parameter list")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot argument(0, CreditAccount.accountNo) object type must match service parameter type SalesOrder")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot object id path CreditAccount.missingAccountNo not found")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot argument(0, CreditAccount.accountNo) must refer to a single object parameter, but service parameter type is CreditAccount[]")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot object id path SalesOrder.customers.customerId must not traverse array field SalesOrder.customers")));
    }

    @Test
    public void snapshotRulesRejectDuplicatePersistencePhases() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Entity SalesOrder{ String orderNo; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo))
                            snapshot(after, argument(0, CreditAccount.accountNo))
                            snapshot(commit, current(SalesOrder.orderNo))
                            snapshot(commit, current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateSnapshotDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot rule on service reserveCredit declares duplicate after snapshot for argument(0, CreditAccount.accountNo)")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot rule on service reserveCredit declares duplicate commit snapshot for current(SalesOrder.orderNo)")));
    }

    @Test
    public void snapshotCurrentRulesMustResolveObjectPath() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; CreditAccount[] accounts; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(current(SalesOrder)) reserveCredit;
                        snapshot(current(SalesOrder.missingOrderNo)) reserveCredit;
                        snapshot(current(MissingOrder.orderNo)) reserveCredit;
                        snapshot(current(SalesOrder.account)) reserveCredit;
                        snapshot(current(SalesOrder.accounts.accountNo)) reserveCredit;
                    }
                }
                """);

        List<String> errors = invalid.validateSnapshotDeclarations();
        assertEquals(errors.toString(), 5, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot SalesOrder must include an object id field path")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot object id path SalesOrder.missingOrderNo not found")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot object type MissingOrder not defined")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot object id path SalesOrder.account must resolve to a scalar field")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Snapshot object id path SalesOrder.accounts.accountNo must not traverse array field SalesOrder.accounts")));
    }

    @Test
    public void snapshotArgumentRulesMustResolveScalarObjectIdPath() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ String accountNo; Customer customer; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.customer)) reserveCredit;
                    }
                }
                """);

        List<String> errors = invalid.validateSnapshotDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Snapshot object id path CreditAccount.customer must resolve to a scalar field"));
    }

    @Test
    public void compensatedAtomicServicesMustDeclareRecoverySnapshots() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.accountNo))
                            compensate(releaseCredit) reserveCredit;
                        snapshot(argument(0, CreditAccount.accountNo)) releaseCredit;
                    }
                }
                """);
        assertTrue(valid.validateCompensationSnapshotDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        compensate(releaseCredit) reserveCredit;
                        snapshot(argument(0, CreditAccount.accountNo)) releaseCredit;
                    }
                }
                """);
        List<String> errors = invalid.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensated service reserveCredit must declare argument object snapshot rules"));

        SppDomain currentOnly = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(current(SalesOrder.orderNo))
                            compensate(releaseCredit) reserveCredit;
                        snapshot(argument(0, CreditAccount.accountNo)) releaseCredit;
                    }
                }
                """);
        errors = currentOnly.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensated service reserveCredit must declare argument object snapshot rules"));

        SppDomain sourceBeforeOnly = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(before, argument(0, CreditAccount.accountNo))
                            compensate(releaseCredit) reserveCredit;
                        snapshot(argument(0, CreditAccount.accountNo)) releaseCredit;
                    }
                }
                """);
        errors = sourceBeforeOnly.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensated service reserveCredit must declare after-execution argument object snapshot rules"));

        SppDomain sourceCommitOnly = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(commit, argument(0, CreditAccount.accountNo))
                            compensate(releaseCredit) reserveCredit;
                        snapshot(argument(0, CreditAccount.accountNo)) releaseCredit;
                    }
                }
                """);
        errors = sourceCommitOnly.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensated service reserveCredit must declare after-execution argument object snapshot rules"));

        SppDomain missingTargetSnapshot = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.accountNo))
                            compensate(releaseCredit) reserveCredit;
                        releaseCredit;
                    }
                }
                """);
        errors = missingTargetSnapshot.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensation service releaseCredit referenced by reserveCredit must declare argument object snapshot rules"));

        SppDomain targetBeforeOnly = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.accountNo))
                            compensate(releaseCredit) reserveCredit;
                        snapshot(before, argument(0, CreditAccount.accountNo)) releaseCredit;
                    }
                }
                """);
        errors = targetBeforeOnly.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensation service releaseCredit referenced by reserveCredit"
                        + " must declare after-execution argument object snapshot rules"));

        SppDomain targetCommitOnly = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.accountNo))
                            compensate(releaseCredit) reserveCredit;
                        snapshot(commit, argument(0, CreditAccount.accountNo)) releaseCredit;
                    }
                }
                """);
        errors = targetCommitOnly.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensation service releaseCredit referenced by reserveCredit"
                        + " must declare after-execution argument object snapshot rules"));

        SppDomain mismatchedTargetSnapshot = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; String ledgerNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                """, """
                domain commerce;
                scope{
                    local{
                        snapshot(argument(0, CreditAccount.accountNo))
                            compensate(releaseCredit) reserveCredit;
                        snapshot(argument(0, CreditAccount.ledgerNo)) releaseCredit;
                    }
                }
                """);
        errors = mismatchedTargetSnapshot.validateCompensationSnapshotDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensation service releaseCredit referenced by reserveCredit must declare matching argument object snapshot rule argument(0, CreditAccount.accountNo)"));

        SppDomain sagaTargetMissingSnapshot = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                atomic update releaseCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditReservation) snapshot(argument(0, CreditAccount.accountNo)) reserveCredit;
                        at resource(creditRelease) releaseCredit;
                        saga compensate(releaseCredit) submitOrder;
                    }
                }
                """);
        errors = sagaTargetMissingSnapshot.validateCompensationSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensation service releaseCredit referenced by submitOrder must declare argument object snapshot rules"));

        SppDomain sagaTargetPartialSnapshot = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Role OrderAmount{ business("orderAmountId business credential") String orderAmountId; double payableAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update releaseCredit(CreditAccount account, OrderAmount amount);
                Scenario serial submitOrder(CreditAccount account, OrderAmount amount){
                    transaction{ reserveCredit(account, amount); }
                }
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(creditReservation) snapshot(argument(0, CreditAccount.accountNo)) reserveCredit;
                        at resource(creditRelease) snapshot(argument(0, CreditAccount.accountNo)) releaseCredit;
                        saga compensate(releaseCredit) submitOrder;
                    }
                }
                """);
        errors = sagaTargetPartialSnapshot.validateCompensationSnapshotDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Compensation service releaseCredit referenced by saga submitOrder must declare argument object snapshot rule for saga parameter 1 OrderAmount"));
    }

    @Test
    public void appHeaderMarksUniqueIdempotencyFieldsAcrossInheritance() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header traceIn{
                    unique String globalSerialNumber;
                }
                header baseIn extends traceIn{
                    notnull String caller;
                }
                header baseOut{
                    unique String messageId;
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);

        AppHeader baseIn = domain.getRuleBlock().searchAppHeader(namedHeader("baseIn"));
        AppHeader baseOut = domain.getRuleBlock().searchAppHeader(namedHeader("baseOut"));
        assertTrue(baseIn.hasUniqueIdempotencyField());
        assertTrue(baseOut.hasUniqueIdempotencyField());
        assertEquals("globalSerialNumber", baseIn.findField("globalSerialNumber").getName());
        ScopeDefine local = domain.getRuleBlock().getAppScope().getScopelist().get(0);
        assertTrue(local.getIn().hasUniqueIdempotencyField());
        assertTrue(local.getOut().hasUniqueIdempotencyField());
    }

    @Test
    public void headerDeclarationsResolveForwardReferencesAndRejectMissingOrCycles() throws Exception {
        SppDomain forward = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                header traceIn{
                    unique String globalSerialNumber;
                }
                header baseIn extends traceIn{
                    notnull String caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                """);
        assertTrue(forward.validateHeaderDeclarations().isEmpty());
        ScopeDefine forwardScope = forward.getRuleBlock().getAppScope().getScopelist().get(0);
        assertFalse(forwardScope.getIn().isTemp());
        assertEquals("globalSerialNumber", forwardScope.getIn().findField("globalSerialNumber").getName());

        SppDomain missingScopeHeader = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local (in missingIn, out missingOut){
                        submitOrder;
                    }
                }
                """);
        List<String> errors = missingScopeHeader.validateHeaderDeclarations();
        assertEquals(2, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Scope input header missingIn is referenced but not defined"));
        assertTrue(errors.get(1), errors.get(1).contains(
                "Scope output header missingOut is referenced but not defined"));

        SppDomain missingExtendsHeader = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn extends missingTrace{
                    notnull String caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);
        errors = missingExtendsHeader.validateHeaderDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Header baseIn extends undefined header missingTrace"));

        SppDomain cyclicHeaders = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn extends traceIn{
                    notnull String caller;
                }
                header traceIn extends baseIn{
                    unique String globalSerialNumber;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);
        errors = cyclicHeaders.validateHeaderDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains("Header inheritance cycle detected"));

        SppDomain wrongIdempotencyType = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{
                    unique int messageId;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);
        errors = wrongIdempotencyType.validateHeaderDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Header baseIn idempotency field messageId must have type String"));
        assertFalse(wrongIdempotencyType.getRuleBlock().getAppScope()
                .getScopelist().get(0).getIn().hasUniqueIdempotencyField());
    }

    @Test
    public void headerFieldOverridesMustTargetInheritedFieldsWithSameType() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{
                    notnull String caller;
                }
                header channelIn extends baseIn{
                    notnull String channelId = caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in channelIn, out baseOut){
                        submitOrder;
                    }
                }
                """);
        assertTrue(valid.validateHeaderDeclarations().isEmpty());
        AppHeader channelIn = valid.getRuleBlock().searchAppHeader(namedHeader("channelIn"));
        assertEquals("caller", channelIn.findField("channelId").getOverrideSuper());

        SppDomain explicitSameNameMapping = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{
                    notnull String caller;
                }
                header channelIn extends baseIn{
                    notnull String caller = caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in channelIn, out baseOut){
                        submitOrder;
                    }
                }
                """);
        assertTrue(explicitSameNameMapping.validateHeaderDeclarations().toString(),
                explicitSameNameMapping.validateHeaderDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{
                    notnull String caller;
                    notnull int traceNo;
                }
                header channelIn extends baseIn{
                    notnull String channelId = missingCaller;
                    notnull String traceId = traceNo;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in channelIn, out baseOut){
                        submitOrder;
                    }
                }
                """);
        List<String> errors = invalid.validateHeaderDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Header channelIn field channelId overrides undefined inherited field missingCaller")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Header channelIn field traceId override target traceNo must have the same type")));
    }

    @Test
    public void headerInheritanceMustNotProvideAmbiguousInheritedFields() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header gatewayIn{
                    notnull String caller;
                }
                header channelIn{
                    notnull String caller;
                }
                header baseIn extends gatewayIn, channelIn{
                    unique String messageId;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateHeaderDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Header baseIn inherits field caller from multiple headers: gatewayIn and channelIn"));
    }

    @Test
    public void ambiguousInheritedHeadersRemainInvalidWithoutLocalIdempotencyRequirements() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header gatewayIn{
                    unique String messageId;
                }
                header channelIn{
                    unique String messageId;
                }
                header baseIn extends gatewayIn, channelIn{
                    notnull String caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(orderResource) submitOrder;
                    }
                }
                """);

        AppHeader baseIn = invalid.getRuleBlock().searchAppHeader(namedHeader("baseIn"));
        assertFalse(baseIn.hasUniqueIdempotencyField());

        List<String> headerErrors = invalid.validateHeaderDeclarations();
        assertEquals(headerErrors.toString(), 1, headerErrors.size());
        assertTrue(headerErrors.get(0), headerErrors.get(0).contains(
                "Header baseIn inherits field messageId from multiple headers: gatewayIn and channelIn"));

        // Header ambiguity remains invalid, but local transactions do not require request idempotency.
        assertTrue(invalid.validateTransactionalIdempotencyHeaders().isEmpty());
    }

    @Test
    public void sharedAncestorIdempotencyHeaderCanSatisfyTransactionHeaders() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header traceIn{
                    unique String messageId;
                }
                header gatewayIn extends traceIn{
                    notnull String gatewayId;
                }
                header channelIn extends traceIn{
                    notnull String channelId;
                }
                header baseIn extends gatewayIn, channelIn{
                    notnull String caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(orderResource) submitOrder;
                    }
                }
                """);

        AppHeader baseIn = valid.getRuleBlock().searchAppHeader(namedHeader("baseIn"));
        assertTrue(baseIn.hasUniqueIdempotencyField());
        assertEquals("messageId", baseIn.getPreferredUniqueIdempotencyFieldName());
        assertTrue(valid.validateHeaderDeclarations().toString(),
                valid.validateHeaderDeclarations().isEmpty());
        assertTrue(valid.validateTransactionalIdempotencyHeaders().toString(),
                valid.validateTransactionalIdempotencyHeaders().isEmpty());
    }

    @Test
    public void headerInheritanceMustNotRepeatParentHeaders() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header traceIn{
                    unique String messageId;
                }
                header baseIn extends traceIn, traceIn{
                    notnull String caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateHeaderDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Header baseIn extends header traceIn multiple times"));
    }

    @Test
    public void headerFieldModifiersMustNotRepeat() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{
                    notnull String caller;
                    unique String messageId;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);
        assertTrue(valid.validateHeaderDeclarations().toString(),
                valid.validateHeaderDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{
                    notnull notnull String caller;
                    unique unique String messageId;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateHeaderDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Header baseIn field caller declares notnull multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Header baseIn field messageId declares unique multiple times")));
    }

    @Test
    public void headerDeclaredFieldsMustNotShadowInheritedFields() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header traceIn{
                    notnull String caller;
                }
                header baseIn extends traceIn{
                    notnull String caller;
                    unique String messageId;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateHeaderDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Header baseIn field caller duplicates inherited field caller"));
    }

    @Test
    public void headerDeclaredFieldsMustNotRepeatNames() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{
                    notnull String caller;
                    unique String caller;
                }
                header baseOut{
                    notnull String appId(Default = "commerce");
                }
                scope{
                    local (in baseIn, out baseOut){
                        submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validateHeaderDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Header baseIn declares duplicate field caller"));
    }

    @Test
    public void sagaScopeRequiresIdempotentInputHeader() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                Scenario serial submitOrder(SalesOrder order){ transaction{} }
                """, """
                domain commerce;
                header baseIn{ unique String messageId; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        saga submitOrder;
                    }
                }
                """);
        assertTrue(valid.validateSagaIdempotencyHeaders().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                Scenario serial submitOrder(SalesOrder order){ transaction{} }
                """, """
                domain commerce;
                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        saga submitOrder;
                    }
                }
                """);
        List<String> errors = invalid.validateSagaIdempotencyHeaders();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder must use an input header with unique globalSerialNumber or messageId"));

        SppDomain inheritedWrongType = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                Scenario serial submitOrder(SalesOrder order){ transaction{} }
                """, """
                domain commerce;
                header traceIn{ unique int messageId; }
                header baseIn extends traceIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        saga submitOrder;
                    }
                }
                """);
        errors = inheritedWrongType.validateHeaderDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Header traceIn idempotency field messageId must have type String"));
        errors = inheritedWrongType.validateSagaIdempotencyHeaders();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder must use an input header with unique globalSerialNumber or messageId"));
    }

    @Test
    public void localTransactionalServicesDoNotRequireIdempotencyHeaders() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                atomic update reserveCredit(CreditAccount account);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header channelIn extends baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in channelIn, out baseOut){
                        at resource(creditReservation) reserveCredit;
                    }
                }
                """);
        assertTrue(valid.validateTransactionalIdempotencyHeaders().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; quantum double balance; quantum double occupied; }
                Contract SalesOrder{ String orderNo; }
                atomic update reserveCredit(CreditAccount account);
                atomic update occupyCredit(CreditAccount account);
                Scenario serial submitOrder(SalesOrder order){ transaction{} }
                atomic query queryCredit(CreditAccount account);
                """, """
                domain commerce;
                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(creditReservation) reserveCredit;
                        quantum occupyCredit;
                        saga submitOrder;
                        queryCredit;
                    }
                }
                """);
        assertTrue(invalid.validateTransactionalIdempotencyHeaders().isEmpty());
    }

    @Test
    public void sagaScopeMustBindLocalScenarioService() throws Exception {
        SppDomain remoteSaga = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                Scenario serial submitOrder(SalesOrder order){ transaction{} }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    remote (in baseIn, out baseOut){
                        saga submitOrder;
                    }
                }
                """);
        List<String> remoteErrors = remoteSaga.validateSagaScopeDeclarations();
        assertEquals(1, remoteErrors.size());
        assertTrue(remoteErrors.get(0), remoteErrors.get(0).contains(
                "Saga service submitOrder must be declared in local scope"));

        SppDomain atomicSaga = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update submitOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        saga submitOrder;
                    }
                }
                """);
        List<String> atomicErrors = atomicSaga.validateSagaScopeDeclarations();
        assertEquals(1, atomicErrors.size());
        assertTrue(atomicErrors.get(0), atomicErrors.get(0).contains(
                "Saga service submitOrder must be a Scenario service"));

        SppDomain resourceSaga = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                Scenario serial submitOrder(SalesOrder order){ transaction{} }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        saga resource(orderSaga) submitOrder;
                    }
                }
                """);
        List<String> resourceErrors = resourceSaga.validateSagaScopeDeclarations();
        assertEquals(1, resourceErrors.size());
        assertTrue(resourceErrors.get(0), resourceErrors.get(0).contains(
                "Saga service submitOrder must not declare a transaction resource"));
    }

    @Test
    public void sagaTransactionBlocksMustCallLocalTransactionalUpdates() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                """);
        assertTrue(valid.validateSagaTransactionCalls().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                Entity Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                atomic update openSalesOrder(SalesOrder order);
                Scenario serial submitOrder(SalesOrder order, Customer customer){
                    transaction{
                        queryCustomer(customer);
                        openSalesOrder(order);
                    }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        queryCustomer;
                        openSalesOrder;
                        saga submitOrder;
                    }
                }
                """);
        List<String> errors = invalid.validateSagaTransactionCalls();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Saga service submitOrder transaction block can only call local atomic update transaction services, but calls queryCustomer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Saga service submitOrder transaction block call openSalesOrder must declare a local transaction mode or resource")));

        SppDomain remoteUpdate = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update reserveRemoteOrder(SalesOrder order);
                Scenario serial submitOrder(SalesOrder order){
                    transaction{ reserveRemoteOrder(order); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        saga submitOrder;
                    }
                    remote (in baseIn, out baseOut){
                        reserveRemoteOrder;
                    }
                }
                """);
        errors = remoteUpdate.validateSagaTransactionCalls();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder transaction block can only call local atomic update transaction services, but calls remote service reserveRemoteOrder"));

        SppDomain emptyTransaction = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                Scenario serial submitOrder(SalesOrder order){
                    transaction{}
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        saga submitOrder;
                    }
                }
                """);
        errors = emptyTransaction.validateSagaTransactionCalls();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder must declare at least one service call in a transaction block"));

        SppDomain missingSnapshot = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        at resource(salesOrder) openSalesOrder;
                        saga submitOrder;
                    }
                }
                """);
        errors = missingSnapshot.validateSagaTransactionCalls();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder transaction block call openSalesOrder must declare argument object snapshot rules"));

        SppDomain currentOnlySnapshot = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(SalesOrder order){
                    transaction{ reserveCredit(order.account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(creditAccounting) snapshot(current(SalesOrder.account.accountNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        errors = currentOnlySnapshot.validateSagaTransactionCalls();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder transaction block call reserveCredit must declare argument object snapshot rules"));

        SppDomain quantumBoundaryEvidence = compileDomain("""
                domain commerce;
                Role CreditAccount{ quantum double availableCredit; }
                Contract SalesOrder{ String orderNo; quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, SalesOrder order);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account, order); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit,
                            SalesOrder.occupiedAmount) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        errors = quantumBoundaryEvidence.validateSagaTransactionCalls();
        assertTrue(errors.toString(), errors.isEmpty());

        SppDomain transactionalCallOutsideBlock = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update reserveOrder(SalesOrder order);
                atomic update openSalesOrder(SalesOrder order);
                Scenario serial submitOrder(SalesOrder order){
                    reserveOrder(order);
                    transaction{ openSalesOrder(order); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(orderReservation) snapshot(argument(0, SalesOrder.orderNo)) reserveOrder;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                }
                """);
        errors = transactionalCallOutsideBlock.validateSagaTransactionCalls();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder call reserveOrder must be placed inside a transaction block"));

        SppDomain indirectTransactionalCallOutsideBlock = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update reserveOrder(SalesOrder order);
                atomic update openSalesOrder(SalesOrder order);
                Scenario serial reserveOrderStep(SalesOrder order){
                    reserveOrder(order);
                }
                Scenario serial submitOrder(SalesOrder order){
                    reserveOrderStep(order);
                    transaction{ openSalesOrder(order); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        reserveOrderStep;
                        at resource(orderReservation) snapshot(argument(0, SalesOrder.orderNo)) reserveOrder;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                }
                """);
        errors = indirectTransactionalCallOutsideBlock.validateSagaTransactionCalls();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Saga service submitOrder call reserveOrderStep reaches transactional service reserveOrder outside a transaction block"));
    }

    @Test
    public void currentSnapshotMustResolveFromLocalScenarioParameter() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
        """);
        assertTrue(valid.validateCurrentSnapshotContexts().isEmpty());

        SppDomain nestedCurrentPath = compileDomain("""
                domain commerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; Customer customer; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo),
                            current(SalesOrder.customer.customerId)) reserveCredit;
                        saga submitOrder;
                    }
                }
        """);
        assertTrue(nestedCurrentPath.validateCurrentSnapshotContexts().toString(),
                nestedCurrentPath.validateCurrentSnapshotContexts().isEmpty());

        SppDomain mixedSagaCallers = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                Scenario serial submitQuick(CreditAccount account){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                        saga submitQuick;
                    }
                }
        """);
        List<String> errors = mixedSagaCallers.validateCurrentSnapshotContexts();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Current snapshot SalesOrder.orderNo on service reserveCredit is called by saga submitQuick"));

        SppDomain ambiguousCurrentContext = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder draftOrder, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
        """);
        errors = ambiguousCurrentContext.validateCurrentSnapshotContexts();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Current snapshot SalesOrder.orderNo on service reserveCredit is ambiguous in saga submitOrder"));
        assertTrue(errors.get(0), errors.get(0).contains("draftOrder, order"));

        SppDomain indirectCall = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial reserveStep(CreditAccount account){
                    transaction{ reserveCredit(account); }
                }
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveStep(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        reserveStep;
                        saga submitOrder;
                    }
                }
                """);
        assertTrue(indirectCall.validateCurrentSnapshotContexts().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        errors = invalid.validateCurrentSnapshotContexts();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Current snapshot SalesOrder.orderNo on service reserveCredit must be resolvable"));

        SppDomain nonSagaScenario = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        submitOrder;
                    }
                }
                """);
        errors = nonSagaScenario.validateCurrentSnapshotContexts();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Current snapshot SalesOrder.orderNo on service reserveCredit must be resolvable from a local saga scenario parameter"));

        SppDomain missingCurrentPath = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.missingOrderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        errors = missingCurrentPath.validateCurrentSnapshotContexts();
        assertTrue(errors.isEmpty());
        errors = missingCurrentPath.validateSnapshotDeclarations();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Snapshot object id path SalesOrder.missingOrderNo not found"));

        SppDomain arrayCurrentContext = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder[] orders){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        errors = arrayCurrentContext.validateCurrentSnapshotContexts();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Current snapshot SalesOrder.orderNo on service reserveCredit must be resolvable from a local saga scenario parameter"));
    }

    @Test
    public void currentSnapshotStrictValidationRequiresAppDdlKeyCoverage() throws Exception {
        SppDomain missingAppDdlKey = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        assertTrue(missingAppDdlKey.validateCurrentSnapshotContexts().toString(),
                missingAppDdlKey.validateCurrentSnapshotContexts().isEmpty());
        List<String> errors = missingAppDdlKey.validateCurrentSnapshotAppDdlKeyDeclarations();
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "Current snapshot SalesOrder.orderNo on service reserveCredit must be covered by APP DDL"));
        assertTrue(errors.get(0), errors.get(0).contains(
                "missing=appDdlDerived+escapeHatchClear+appDdlObjectKeyPushdown"));
        assertTrue(errors.get(0), errors.get(0).contains("keySource=CURRENT_CONTEXT_ESCAPE_HATCH"));
        assertTrue(errors.get(0), errors.get(0).contains("paths=orderNo"));
        assertTrue(errors.get(0), errors.get(0).contains(
                "actions=declareAppDdlPrimaryKeyOrNamedIndex+pushCurrentObjectKeyToAppDdlColumns"));
        assertTrue(errors.get(0), errors.get(0).contains(
                "action=declareAppDdlPrimaryKeyOrNamedIndex+pushCurrentObjectKeyToAppDdlColumns"));

        SppDomain indexedCurrentKey = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                tables contracts{
                    table SALES_ORDER(SalesOrder){
                        primarykey String salesOrderId;
                        index salesOrderByOrderNo(SalesOrder.orderNo);
                    }
                }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        assertTrue(indexedCurrentKey.validateCurrentSnapshotAppDdlKeyDeclarations().toString(),
                indexedCurrentKey.validateCurrentSnapshotAppDdlKeyDeclarations().isEmpty());

        SppDomain primaryKeyCurrentKey = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey(SalesOrder.orderNo); }
                }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        assertTrue(primaryKeyCurrentKey.validateCurrentSnapshotAppDdlKeyDeclarations().toString(),
                primaryKeyCurrentKey.validateCurrentSnapshotAppDdlKeyDeclarations().isEmpty());

        SppDomain platformPrimaryKeyCurrentKey = compileDomain("""
                domain commerce;
                Entity CreditAccount{ String accountNo; double balance; }
                Contract SalesOrder{ String orderNo; CreditAccount account; }
                atomic update reserveCredit(CreditAccount account);
                Scenario serial submitOrder(CreditAccount account, SalesOrder order){
                    transaction{ reserveCredit(account); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                tables contracts{
                    table SALES_ORDER(SalesOrder){ primarykey String orderNo; }
                }
                scope{
                    local (in baseIn, out baseOut){
                        snapshot(argument(0, CreditAccount.accountNo), current(SalesOrder.orderNo)) reserveCredit;
                        saga submitOrder;
                    }
                }
                """);
        assertTrue(platformPrimaryKeyCurrentKey.validateCurrentSnapshotAppDdlKeyDeclarations().toString(),
                platformPrimaryKeyCurrentKey.validateCurrentSnapshotAppDdlKeyDeclarations().isEmpty());
    }

    @Test
    public void appParseErrorsAreReturnedByUnifiedValidation() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain creditcommerce;
                header baseIn{ String requestId; }
                header baseIn{ String traceId; }
                scope{ local{ queryCustomer; } }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Duplicate header baseIn")));
        assertTrue(domain.getAppParseErrors().toString(), domain.getAppParseErrors().stream()
                .anyMatch(error -> error.contains("Duplicate header baseIn")));
    }

    @Test
    public void phyFilesCanOnlyDeclarePhysicalBlocks() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain creditcommerce;
                scope{ local{ queryCustomer; } }
                """, """
                domain creditcommerce;
                scope{ remote{ queryCustomer; } }
                """);

        List<String> errors = domain.validateAppDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY file creditcommerce.phy can only declare phy blocks")));
        assertTrue(domain.getAppParseErrors().toString(), domain.getAppParseErrors().stream()
                .anyMatch(error -> error.contains("PHY file creditcommerce.phy can only declare phy blocks")));
    }

    @Test
    public void duplicateHeaderDeclarationsMustNotMergeIntoExistingHeader() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain creditcommerce;
                header baseIn{ String requestId; }
                header baseIn{ String traceId; }
                scope{ local(in baseIn, out baseOut){ queryCustomer; } }
                header baseOut{ String status; }
                """);

        AppHeader baseIn = domain.getRuleBlock().searchAppHeader(namedHeader("baseIn"));
        assertEquals("requestId", baseIn.findField("requestId").getName());
        assertTrue(baseIn.findField("traceId") == null);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("Duplicate header baseIn")));
    }

    @Test
    public void invalidServiceImplementationParseErrorStopsUnifiedValidation() throws Exception {
        SppDomain domain = compileDomain("""
                domain creditcommerce;
                Entity Customer{ business("customerId business credential") String customerId; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain creditcommerce;
                services{
                    missingService{
                        customer.customerId = "C001";
                    }
                }
                scope{ local{ queryCustomer; } }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "missingService is null or not a service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "customer.customerId not defined")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "APP service implementation missingService must bind a defined service")));
    }

    @Test
    public void phyBackendDeclarationsAreParsedAndValidated() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                atomic query queryRemoteOrder(SalesOrder order);
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
                    remote (in baseIn, out baseOut){
                        queryRemoteOrder;
                    }
                }
                phy{
                    backend "local-jdbc";
                    backend creditTx{
                        adapter seata;
                        config applicationId = "credit-commerce";
                        config transactionServiceGroup = "credit-commerce-tx-group";
                        config quantumSupported = false;
                        at salesOrder;
                        saga submitOrder;
                    }
                    remote queryRemoteOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/query";
                    }
                    repository objectStore{
                        adapter objectSnapshot;
                        config table = "spp_object_snapshot";
                    }
                }
                """);

        List<AppPhysicalBackend> backends = domain.getRuleBlock().getAppPhysicalBackends();
        assertEquals(2, backends.size());
        assertEquals("local-jdbc", backends.get(0).getName());
        assertEquals("creditTx", backends.get(1).getName());
        assertEquals("seata", backends.get(1).getAdapterName());
        assertEquals("credit-commerce", backends.get(1).getConfiguration().get("applicationId"));
        assertEquals("credit-commerce-tx-group", backends.get(1).getConfiguration().get("transactionServiceGroup"));
        assertEquals("false", backends.get(1).getConfiguration().get("quantumSupported"));
        assertEquals(AppPhysicalBinding.Kind.at, backends.get(1).getBindings().get(0).getKind());
        assertEquals("salesOrder", backends.get(1).getBindings().get(0).getTargetName());
        List<AppRemoteAdapter> remoteAdapters = domain.getRuleBlock().getAppRemoteAdapters();
        assertEquals(1, remoteAdapters.size());
        assertEquals("queryRemoteOrder", remoteAdapters.get(0).getName());
        assertEquals("httpJson", remoteAdapters.get(0).getAdapterName());
        assertEquals("https://orders.example.test/query", remoteAdapters.get(0).getConfiguration().get("endpoint"));
        List<AppRepositoryAdapter> repositoryAdapters = domain.getRuleBlock().getAppRepositoryAdapters();
        assertEquals(1, repositoryAdapters.size());
        assertEquals("objectStore", repositoryAdapters.get(0).getName());
        assertEquals("objectSnapshot", repositoryAdapters.get(0).getAdapterName());
        assertEquals("spp_object_snapshot", repositoryAdapters.get(0).getConfiguration().get("table"));
        assertTrue(domain.validatePhysicalRuntimeDeclarations().toString(),
                domain.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyBackendCanBindWholeTransactionKinds() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                phy{
                    backend creditTx{
                        adapter seata;
                        config transactionServiceGroup = "credit-commerce-tx-group";
                        at;
                        saga;
                    }
                }
                """);

        List<AppPhysicalBackend> backends = domain.getRuleBlock().getAppPhysicalBackends();
        assertEquals(1, backends.size());
        assertEquals(2, backends.get(0).getBindings().size());
        assertEquals(AppPhysicalBinding.Kind.at, backends.get(0).getBindings().get(0).getKind());
        assertEquals(AppPhysicalBinding.ALL_TARGETS, backends.get(0).getBindings().get(0).getTargetName());
        assertTrue(backends.get(0).getBindings().get(0).isAllTargets());
        assertEquals(AppPhysicalBinding.Kind.saga, backends.get(0).getBindings().get(1).getKind());
        assertEquals(AppPhysicalBinding.ALL_TARGETS, backends.get(0).getBindings().get(1).getTargetName());
        assertTrue(domain.validatePhysicalRuntimeDeclarations().toString(),
                domain.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyDeclarationsCanLiveInSeparatePhysicalFile() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Contract SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                atomic query queryRemoteOrder(SalesOrder order);
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
                    remote (in baseIn, out baseOut){
                        queryRemoteOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend seata{
                        config applicationId = "credit-commerce";
                        config transactionServiceGroup = "commerce-tx-group";
                        at salesOrder;
                        saga submitOrder;
                    }
                    remote queryRemoteOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/query";
                    }
                    repository objectStore{
                        adapter objectSnapshot;
                    }
                }
                """);

        assertEquals(1, domain.getRuleBlock().getAppPhysicalBackends().size());
        assertEquals("seata", domain.getRuleBlock().getAppPhysicalBackends().get(0).getName());
        assertEquals("credit-commerce",
                domain.getRuleBlock().getAppPhysicalBackends().get(0).getConfiguration().get("applicationId"));
        assertEquals("commerce-tx-group",
                domain.getRuleBlock().getAppPhysicalBackends().get(0)
                        .getConfiguration().get("transactionServiceGroup"));
        assertEquals(1, domain.getRuleBlock().getAppRemoteAdapters().size());
        assertEquals("queryRemoteOrder", domain.getRuleBlock().getAppRemoteAdapters().get(0).getName());
        assertEquals("httpJson", domain.getRuleBlock().getAppRemoteAdapters().get(0).getAdapterName());
        assertEquals(1, domain.getRuleBlock().getAppRepositoryAdapters().size());
        assertEquals("objectStore", domain.getRuleBlock().getAppRepositoryAdapters().get(0).getName());
        assertEquals("objectSnapshot", domain.getRuleBlock().getAppRepositoryAdapters().get(0).getAdapterName());
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
    }

    @Test
    public void phyHttpJsonRemoteAdapterMustDefineExecutableEndpointConfig() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic query queryRemoteOrder(SalesOrder order);
                atomic query queryBadOrder(SalesOrder order);
                atomic query querySlowOrder(SalesOrder order);
                atomic query queryTextTimeoutOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    remote (in baseIn, out baseOut){
                        queryRemoteOrder;
                        queryBadOrder;
                        querySlowOrder;
                        queryTextTimeoutOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryRemoteOrder{
                        adapter httpJson;
                    }
                    remote queryBadOrder{
                        adapter httpJson;
                        config endpoint = "ftp://orders.example.test/query";
                    }
                    remote querySlowOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/query";
                        config timeoutMillis = 0;
                    }
                    remote queryTextTimeoutOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/query";
                        config timeoutMillis = slow;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryRemoteOrder httpJson config endpoint must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryBadOrder httpJson config endpoint must be an absolute http or https URI")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter querySlowOrder httpJson config timeoutMillis must be positive")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryTextTimeoutOrder httpJson config timeoutMillis must be a number")));

        SppDomain customAdapter = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic query queryRemoteOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryRemoteOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryRemoteOrder{
                        adapter rpc;
                    }
                }
                """);
        assertTrue(customAdapter.validatePhysicalRuntimeDeclarations().toString(),
                customAdapter.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyDeploymentMustBindRemoteScopeServices() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                Entity SalesOrder{ String orderNo; }
                atomic query queryCustomer(Customer customer);
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote service queryCustomer is not assigned to any remote adapter")));

        SppDomain languageOnly = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """);

        assertTrue(languageOnly.validatePhysicalRuntimeDeclarations().toString(),
                languageOnly.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyHttpJsonRemoteAdapterValidatesDeploymentPlaceholders() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic query queryRemoteOrder(SalesOrder order);
                atomic query queryRuntimeOnlyOrder(SalesOrder order);
                atomic query queryBlankEndpointOrder(SalesOrder order);
                atomic query queryBlankTimeoutOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    remote (in baseIn, out baseOut){
                        queryRemoteOrder;
                        queryRuntimeOnlyOrder;
                        queryBlankEndpointOrder;
                        queryBlankTimeoutOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryRemoteOrder{
                        adapter httpJson;
                        config endpoint = "${credit.customer.endpoint:https://orders.example.test/query}";
                        config timeoutMillis = "${credit.customer.timeout:2500}";
                    }
                    remote queryRuntimeOnlyOrder{
                        adapter httpJson;
                        config endpoint = "${credit.runtime.endpoint}";
                        config timeoutMillis = "${credit.runtime.timeout}";
                    }
                    remote queryBlankEndpointOrder{
                        adapter httpJson;
                        config endpoint = "${credit.blank.endpoint:}";
                    }
                    remote queryBlankTimeoutOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/query";
                        config timeoutMillis = "${credit.blank.timeout:}";
                    }
                }
                """);

        List<String> errors = domain.validatePhysicalRuntimeDeclarations();
        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryBlankEndpointOrder httpJson config endpoint must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryBlankTimeoutOrder httpJson config timeoutMillis must not be blank when declared")));
    }

    @Test
    public void phyRemoteRoleAdapterProtocolMustBeSupported() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = true;
                        config roleType = "Customer";
                        config roleAdapterProtocol = "HTTP_JSON";
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain placeholderDefaults = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = "${commerce.remote.role-adapter:true}";
                        config roleType = "${commerce.remote.role-type:Customer}";
                        config roleAdapterProtocol = "${commerce.remote.role-protocol:HTTP_JSON}";
                    }
                }
                """);

        assertTrue(placeholderDefaults.validatePhysicalRuntimeDeclarations().toString(),
                placeholderDefaults.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain unsupported = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = true;
                        config roleType = "Customer";
                        config roleAdapterProtocol = "GRPC";
                    }
                }
                """);

        List<String> unsupportedErrors = unsupported.validatePhysicalRuntimeDeclarations();

        assertTrue(unsupportedErrors.toString(), unsupportedErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer config roleAdapterProtocol"
                        + " declares unsupported protocol GRPC; supported protocol is HTTP_JSON")));

        SppDomain unsupportedPlaceholderDefault = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = "${commerce.remote.role-adapter:true}";
                        config roleType = "${commerce.remote.role-type:Customer}";
                        config roleAdapterProtocol = "${commerce.remote.role-protocol:GRPC}";
                    }
                }
                """);

        List<String> unsupportedPlaceholderDefaultErrors =
                unsupportedPlaceholderDefault.validatePhysicalRuntimeDeclarations();

        assertTrue(unsupportedPlaceholderDefaultErrors.toString(),
                unsupportedPlaceholderDefaultErrors.stream().anyMatch(error -> error.contains(
                        "PHY remote adapter queryCustomer config roleAdapterProtocol"
                                + " declares unsupported protocol GRPC; supported protocol is HTTP_JSON")));

        SppDomain deploymentResolved = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = "${commerce.remote.role-adapter}";
                        config roleType = "${commerce.remote.role-type}";
                        config roleAdapterProtocol = "${commerce.remote.role-protocol}";
                    }
                }
                """);

        assertTrue(deploymentResolved.validatePhysicalRuntimeDeclarations().toString(),
                deploymentResolved.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain missing = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = true;
                        config roleType = "Customer";
                    }
                }
                """);

        List<String> missingErrors = missing.validatePhysicalRuntimeDeclarations();

        assertTrue(missingErrors.toString(), missingErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer config roleAdapterProtocol"
                        + " must declare HTTP_JSON when roleAdapter is true")));
    }

    @Test
    public void phyRemoteRoleAdapterRoleTypeMustReferenceDeclaredType() throws Exception {
        SppDomain missing = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = true;
                        config roleAdapterProtocol = "HTTP_JSON";
                    }
                }
                """);

        List<String> missingErrors = missing.validatePhysicalRuntimeDeclarations();

        assertTrue(missingErrors.toString(), missingErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer config roleType"
                        + " must not be blank when roleAdapter is true")));

        SppDomain undefined = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = true;
                        config roleType = "MissingCustomer";
                        config roleAdapterProtocol = "HTTP_JSON";
                    }
                }
                """);

        List<String> undefinedErrors = undefined.validatePhysicalRuntimeDeclarations();

        assertTrue(undefinedErrors.toString(), undefinedErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer config roleType"
                        + " references undefined SPP type MissingCustomer")));

        SppDomain undefinedPlaceholderDefault = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = "${commerce.remote.role-adapter:true}";
                        config roleType = "${commerce.remote.role-type:MissingCustomer}";
                        config roleAdapterProtocol = "${commerce.remote.role-protocol:HTTP_JSON}";
                    }
                }
                """);

        List<String> undefinedPlaceholderDefaultErrors =
                undefinedPlaceholderDefault.validatePhysicalRuntimeDeclarations();

        assertTrue(undefinedPlaceholderDefaultErrors.toString(),
                undefinedPlaceholderDefaultErrors.stream().anyMatch(error -> error.contains(
                        "PHY remote adapter queryCustomer config roleType"
                                + " references undefined SPP type MissingCustomer")));
    }

    @Test
    public void phyRemoteRoleAdapterFlagMustBeBoolean() throws Exception {
        SppDomain invalidValue = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = maybe;
                        config roleType = "Customer";
                        config roleAdapterProtocol = "HTTP_JSON";
                    }
                }
                """);

        List<String> invalidValueErrors = invalidValue.validatePhysicalRuntimeDeclarations();

        assertTrue(invalidValueErrors.toString(), invalidValueErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer config roleAdapter must be true or false")));

        SppDomain blankValue = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter httpJson;
                        config endpoint = "https://customer.example.test/query";
                        config roleAdapter = "";
                        config roleType = "Customer";
                        config roleAdapterProtocol = "HTTP_JSON";
                    }
                }
                """);

        List<String> blankValueErrors = blankValue.validatePhysicalRuntimeDeclarations();

        assertTrue(blankValueErrors.toString(), blankValueErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer config roleAdapter must be true or false")));
    }

    @Test
    public void phyRemoteRoleAdapterCannotUseReferenceAdapter() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                        config roleAdapter = true;
                        config roleType = "Customer";
                        config roleAdapterProtocol = "HTTP_JSON";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote reference adapter"
                        + " cannot declare roleAdapter = true")));
    }

    @Test
    public void phyObjectRepositoryRemoteAdapterRequiresObjectLookupConfig() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertEquals("objectStore",
                valid.getRuleBlock().getAppRemoteAdapters().get(0).getConfiguration().get("repository"));
        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config objectType = "";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config objectType"
                        + " must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config objectIdPath or objectIdPaths"
                        + " must not be blank")));

        SppDomain placeholderDefaults = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "${commerce.remote.object-type:Customer}";
                        config objectIdPath = "${commerce.remote.object-id:customerId}";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(placeholderDefaults.validatePhysicalRuntimeDeclarations().toString(),
                placeholderDefaults.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain blankPlaceholderDefaults = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config objectType = "${commerce.remote.object-type:}";
                        config objectIdPath = "${commerce.remote.object-id:}";
                    }
                }
                """);
        List<String> blankPlaceholderErrors = blankPlaceholderDefaults.validatePhysicalRuntimeDeclarations();
        assertTrue(blankPlaceholderErrors.toString(), blankPlaceholderErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config objectType"
                        + " must not be blank")));
        assertTrue(blankPlaceholderErrors.toString(), blankPlaceholderErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config objectIdPath or objectIdPaths"
                        + " must not be blank")));

        SppDomain deploymentResolvedLookup = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config objectType = "${commerce.remote.object-type}";
                        config objectIdPath = "${commerce.remote.object-id}";
                    }
                }
                """);
        assertTrue(deploymentResolvedLookup.validatePhysicalRuntimeDeclarations().toString(),
                deploymentResolvedLookup.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyObjectRepositoryRemoteAdapterRepositoryMustReferenceDeclaredRepository() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "missingStore";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config repository"
                        + " references undefined PHY repository adapter missingStore")));

        SppDomain defaultRepositoryReference = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "${commerce.remote.repository:objectStore}";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(defaultRepositoryReference.validatePhysicalRuntimeDeclarations().toString(),
                defaultRepositoryReference.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain blankDefaultRepositoryReference = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "${commerce.remote.repository:}";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        List<String> blankDefaultErrors = blankDefaultRepositoryReference.validatePhysicalRuntimeDeclarations();
        assertTrue(blankDefaultErrors.toString(), blankDefaultErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config repository must not be blank")));

        SppDomain deploymentRepositoryReference = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "${commerce.remote.repository}";
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                }
                """);
        assertTrue(deploymentRepositoryReference.validatePhysicalRuntimeDeclarations().toString(),
                deploymentRepositoryReference.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyObjectRepositoryRemoteAdapterObjectIdPathMustResolveOnRequestObject() throws Exception {
        SppDomain rolePerformerPath = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                Role CreditAccount{ double availableCredit; }
                take Customer as CreditAccount;
                atomic query queryCustomer(CreditAccount account, Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customer.customerId";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(rolePerformerPath.validatePhysicalRuntimeDeclarations().toString(),
                rolePerformerPath.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain missingRolePerformerField = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                Role CreditAccount{ double availableCredit; }
                take Customer as CreditAccount;
                atomic query queryCustomer(CreditAccount account, Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "Customer";
                        config objectIdPath = "customer.missingId";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        List<String> rolePerformerErrors = missingRolePerformerField.validatePhysicalRuntimeDeclarations();
        assertTrue(rolePerformerErrors.toString(), rolePerformerErrors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config object id path"
                        + " Customer.missingId not found on Role performer identity CreditAccount -> Customer")));

        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config objectType = "Customer";
                        config objectIdPath = "missingId";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config object id path"
                        + " Customer.missingId not found")));
    }

    @Test
    public void phyObjectRepositoryRemoteAdapterResultMustMatchStoredObjectType() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String name; }
                Reference Region{ String region; }
                atomic query queryCustomer(Customer customer, Region region);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config objectType = "Customer";
                        config objectIdPath = "customerId";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote result participant type must match configured objectType Customer")));
    }

    @Test
    public void phyObjectRepositoryRemoteAdapterAllowsCompositeObjectLookupConfig() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String region; String name; }
                atomic query queryCustomers(Customer selection, Customer[] customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomers;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomers{
                        adapter objectRepositoryRemote;
                        config repository = "objectStore";
                        config objectType = "${commerce.remote.object-type:Customer}";
                        config objectIdPaths = "${commerce.remote.object-ids:customerId, region}";
                        config repositoryObjectIdPaths =
                            "${commerce.remote.repository-object-ids:customer.customerId, customer.region}";
                    }
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String region; String name; }
                atomic query queryCustomers(Customer selection, Customer[] customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomers;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomers{
                        adapter objectRepositoryRemote;
                        config objectType = "Customer";
                        config objectIdPaths = "customerId, region";
                        config repositoryObjectIdPaths = "customer.customerId";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomers objectRepositoryRemote config repositoryObjectIdPaths"
                        + " must have the same number of paths as objectIdPath/objectIdPaths")));
    }

    @Test
    public void phyObjectRepositoryRemoteCompositeLookupRequiresCollectionReturn() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Reference Customer{ business("customerId business credential") String customerId; String region; String name; }
                atomic query queryCustomer(Customer customer);
                """, """
                domain commerce;
                scope{
                    remote{
                        queryCustomer;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote queryCustomer{
                        adapter objectRepositoryRemote;
                        config objectType = "Customer";
                        config objectIdPaths = "customerId, region";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryCustomer objectRepositoryRemote config objectIdPaths"
                        + " requires a result participant array or collection")));
    }

    @Test
    public void phyRemoteCompensationPolicyConfigIsParsedAndValidated() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update reserveRemoteOrder(SalesOrder order);
                atomic update releaseRemoteOrder(SalesOrder order);
                atomic update runtimePolicyReleaseRemoteOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    remote (in baseIn, out baseOut){
                        remoteCompensate(releaseRemoteOrder) reserveRemoteOrder;
                        releaseRemoteOrder;
                        runtimePolicyReleaseRemoteOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote reserveRemoteOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/reserve";
                    }
                    remote releaseRemoteOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/release";
                        config compensationMaxAttempts = "${remote.release.max-attempts:3}";
                        config compensationBackoffMillis = "${remote.release.backoff-millis:25}";
                        config compensationManualInterventionRequired =
                            "${remote.release.manual-intervention-required:true}";
                    }
                    remote runtimePolicyReleaseRemoteOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/runtime-release";
                        config compensationMaxAttempts = "${remote.runtime.max-attempts}";
                        config compensationBackoffMillis = "${remote.runtime.backoff-millis}";
                        config compensationManualInterventionRequired = "${remote.runtime.manual}";
                    }
                }
                """);

        AppRemoteAdapter adapter = valid.getRuleBlock().getAppRemoteAdapters().stream()
                .filter(item -> "releaseRemoteOrder".equals(item.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("${remote.release.max-attempts:3}",
                adapter.getConfiguration().get("compensationMaxAttempts"));
        assertEquals("${remote.release.backoff-millis:25}",
                adapter.getConfiguration().get("compensationBackoffMillis"));
        assertEquals("${remote.release.manual-intervention-required:true}",
                adapter.getConfiguration().get("compensationManualInterventionRequired"));
        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update releaseRemoteOrder(SalesOrder order);
                atomic update releaseBlankPolicyOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    remote (in baseIn, out baseOut){
                        releaseRemoteOrder;
                        releaseBlankPolicyOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    remote releaseRemoteOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/release";
                        config compensationMaxAttempts = 0;
                        config compensationBackoffMillis = slow;
                        config compensationManualInterventionRequired = "";
                    }
                    remote releaseBlankPolicyOrder{
                        adapter httpJson;
                        config endpoint = "https://orders.example.test/blank-release";
                        config compensationMaxAttempts = "${remote.release.max-attempts:}";
                        config compensationBackoffMillis = "${remote.release.backoff-millis:}";
                        config compensationManualInterventionRequired =
                            "${remote.release.manual-intervention-required:}";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter releaseRemoteOrder config compensationMaxAttempts"
                        + " must be a positive integer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter releaseRemoteOrder config compensationBackoffMillis"
                        + " must be a non-negative integer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter releaseRemoteOrder config compensationManualInterventionRequired"
                        + " must be true or false")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter releaseBlankPolicyOrder config compensationMaxAttempts"
                        + " must be a positive integer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter releaseBlankPolicyOrder config compensationBackoffMillis"
                        + " must be a non-negative integer")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter releaseBlankPolicyOrder config compensationManualInterventionRequired"
                        + " must be true or false")));
    }

    @Test
    public void phyObjectSnapshotRepositoryAdapterRejectsBlankJdbcConfig() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        adapter objectSnapshot;
                        config datasourceUrl = "";
                        config driverClassName = "";
                        config table = "";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore objectSnapshot config datasourceUrl must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore objectSnapshot config driverClassName must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore objectSnapshot config table must not be blank when declared")));

        SppDomain missingDriverRepository = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        adapter objectSnapshot;
                        config datasourceUrl = "jdbc:h2:mem:spp-object;DB_CLOSE_DELAY=-1";
                    }
                }
                """);
        List<String> missingDriverErrors = missingDriverRepository.validatePhysicalRuntimeDeclarations();
        assertEquals(missingDriverErrors.toString(), 1, missingDriverErrors.size());
        assertTrue(missingDriverErrors.get(0), missingDriverErrors.get(0).contains(
                "PHY repository adapter objectStore objectSnapshot config driverClassName"
                        + " must be declared when datasourceUrl is declared"));

        SppDomain missingDriverWithDefaultDatasource = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        adapter objectSnapshot;
                        config datasourceUrl = "${spp.repository.datasource:jdbc:h2:mem:spp-object;DB_CLOSE_DELAY=-1}";
                    }
                }
                """);
        List<String> missingDriverWithDefaultDatasourceErrors =
                missingDriverWithDefaultDatasource.validatePhysicalRuntimeDeclarations();
        assertEquals(missingDriverWithDefaultDatasourceErrors.toString(), 1,
                missingDriverWithDefaultDatasourceErrors.size());
        assertTrue(missingDriverWithDefaultDatasourceErrors.get(0),
                missingDriverWithDefaultDatasourceErrors.get(0).contains(
                        "PHY repository adapter objectStore objectSnapshot config driverClassName"
                                + " must be declared when datasourceUrl is declared"));

        SppDomain deploymentResolvedDatasource = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        adapter objectSnapshot;
                        config datasourceUrl = "${spp.repository.datasource}";
                    }
                }
                """);
        assertTrue(deploymentResolvedDatasource.validatePhysicalRuntimeDeclarations().toString(),
                deploymentResolvedDatasource.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain injectedRuntimeRepository = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        adapter objectSnapshot;
                    }
                }
                """);
        assertTrue(injectedRuntimeRepository.validatePhysicalRuntimeDeclarations().toString(),
                injectedRuntimeRepository.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyRepositoryAdapterMustHaveAppRepositoryOrSnapshotRules() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic query queryOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        queryOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY repository adapter objectStore declares adapter objectRepository"
                        + " but APP scope declares no repository hydration or object snapshot rules"));

        SppDomain snapshotBacked = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    repository objectStore{
                        adapter objectRepository;
                    }
                }
                """);
        assertTrue(snapshotBacked.validatePhysicalRuntimeDeclarations().toString(),
                snapshotBacked.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyObjectRepositoryAdapterRejectsBlankImplementationClass() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        adapter objectRepository;
                        config implementationClass = "";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY repository adapter objectStore objectRepository config implementationClass"
                        + " must not be blank when declared"));
    }

    @Test
    public void phyJdbcObjectRepositoryAdapterDeclaresPhysicalStore() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config driverClassName must be declared when datasourceUrl is declared"));
    }

    @Test
    public void phyJdbcObjectRepositoryRejectsInvalidAppDdlTablesFlag() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        config appDdlTables = yes;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config appDdlTables must be true or false"));

        SppDomain blankDefault = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                        config appDdlTables = "${repository.app-ddl-tables:}";
                    }
                }
                """);

        List<String> blankDefaultErrors = blankDefault.validatePhysicalRuntimeDeclarations();

        assertEquals(blankDefaultErrors.toString(), 1, blankDefaultErrors.size());
        assertTrue(blankDefaultErrors.get(0), blankDefaultErrors.get(0).contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config appDdlTables must be true or false"));
    }

    @Test
    public void phyJdbcObjectRepositoryRejectsInvalidTransactionRetentionConfig() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) snapshot(transaction) openSalesOrder;
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
                        config transactionRetentionMode = "AUTO_ARCHIVE";
                        config transactionRetentionPropertyPrefix = "";
                        config transactionRetentionEnabledByDefault = yes;
                        config transactionRetentionFixedDelayMillis = "0";
                        config transactionRetentionMillis = "soon";
                        config transactionArchiveRetentionMillis = "-1";
                        config transactionRetentionMillisByService = "openSalesOrder:abc";
                        config transactionRetentionMillisByResource = "salesOrder:0";
                        config transactionArchiveRetentionMillisByResource = "salesOrder";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionMode declares unsupported mode AUTO_ARCHIVE")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionPropertyPrefix must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionEnabledByDefault must be true or false")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionFixedDelayMillis must be positive")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionMillis must be a number")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionArchiveRetentionMillis must be positive")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionMillisByService entry openSalesOrder"
                        + " must declare a numeric millis value")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionMillisByResource entry salesOrder"
                        + " must declare a positive millis value")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionArchiveRetentionMillisByResource"
                        + " must be a comma-separated name:positiveMillis list")));

        SppDomain invalidPlaceholderDefaults = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) snapshot(transaction) openSalesOrder;
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
                        config transactionRetentionMode = "${retention.mode:AUTO_ARCHIVE}";
                        config transactionRetentionPropertyPrefix = "${retention.prefix:}";
                        config transactionRetentionEnabledByDefault = "${retention.enabled:}";
                        config transactionRetentionFixedDelayMillis = "${retention.delay:}";
                        config transactionRetentionMillisByResource = "${retention.resource:}";
                    }
                }
                """);

        List<String> placeholderErrors = invalidPlaceholderDefaults.validatePhysicalRuntimeDeclarations();

        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionMode declares unsupported mode AUTO_ARCHIVE")));
        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionPropertyPrefix must not be blank when declared")));
        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionEnabledByDefault must be true or false")));
        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionFixedDelayMillis must be positive")));
        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore jdbcObjectRepository"
                        + " config transactionRetentionMillisByResource"
                        + " must be a comma-separated name:positiveMillis list")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) snapshot(transaction) openSalesOrder;
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
                        config transactionRetentionMode = "${retention.mode:PHYSICAL_SCHEDULED_ARCHIVE}";
                        config transactionRetentionPropertyPrefix = "${retention.prefix:spp.commerce.retention}";
                        config transactionRetentionEnabledByDefault = "${retention.enabled:false}";
                        config transactionRetentionFixedDelayMillis = "${retention.delay:60000}";
                        config transactionRetentionMillis = "${retention.hot:86400000}";
                        config transactionArchiveRetentionMillis = "${retention.cold:2592000000}";
                        config transactionRetentionMillisByService = "${retention.service:openSalesOrder:604800000}";
                        config transactionRetentionMillisByResource = "${retention.resource:salesOrder:604800000}";
                        config transactionArchiveRetentionMillisByResource = "${retention.archive:salesOrder:2592000000}";
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void phyLocalJdbcBackendRejectsBlankDeclaredRuntimeConfig() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        config datasourceUrl = "";
                        config driverClassName = "";
                        config transactionCoordinator = "";
                        config quantumStore = "";
                        config idempotencyTable = "";
                        config sagaLogTable = "";
                        config tccBranchTable = "";
                        config tccReservationStore = "";
                        config tccReservationTable = "";
                        config tccReservationTtlMillis = "-1";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config datasourceUrl must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config driverClassName must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config transactionCoordinator must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config quantumStore must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config idempotencyTable must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config sagaLogTable must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config tccBranchTable must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config tccReservationStore must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config tccReservationTable must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config tccReservationTtlMillis must be non-negative")));

        SppDomain invalidPlaceholderDefaults = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        config datasourceUrl = "${local.datasource:}";
                        config driverClassName = "${local.driver:}";
                        config transactionCoordinator = "${local.tx:}";
                        config tccReservationTtlMillis = "${local.tcc-reservation-ttl:}";
                    }
                }
                """);

        List<String> placeholderErrors = invalidPlaceholderDefaults.validatePhysicalRuntimeDeclarations();

        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config datasourceUrl must not be blank when declared")));
        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config driverClassName must not be blank when declared")));
        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config transactionCoordinator must not be blank when declared")));
        assertTrue(placeholderErrors.toString(), placeholderErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config tccReservationTtlMillis must be non-negative")));

        SppDomain injectedRuntimeBackend = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc;
                }
                """);
        assertTrue(injectedRuntimeBackend.validatePhysicalRuntimeDeclarations().toString(),
                injectedRuntimeBackend.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalidTtl = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        config tccReservationTtlMillis = "later";
                    }
                }
                """);
        assertTrue(invalidTtl.validatePhysicalRuntimeDeclarations().toString(),
                invalidTtl.validatePhysicalRuntimeDeclarations().stream().anyMatch(error -> error.contains(
                        "PHY backend localJdbc localJdbc adapter config tccReservationTtlMillis must be a number")));

        SppDomain validTtl = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        config tccReservationTtlMillis = "${tcc.reservation.ttl:0}";
                    }
                }
                """);
        assertTrue(validTtl.validatePhysicalRuntimeDeclarations().toString(),
                validTtl.validatePhysicalRuntimeDeclarations().isEmpty());

        SppDomain invalidImplementations = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        config transactionCoordinator = "example.CustomTransactionManager";
                        config idempotencyStore = "example.CustomIdempotencyStore";
                        config sagaLogStore = "example.CustomSagaLogStore";
                        config tccBranchStore = "example.CustomTccBranchStore";
                        config tccReservationStore = "example.CustomTccReservationStore";
                    }
                }
                """);
        List<String> implementationErrors = invalidImplementations.validatePhysicalRuntimeDeclarations();
        assertTrue(implementationErrors.toString(), implementationErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config transactionCoordinator"
                        + " declares unsupported implementation example.CustomTransactionManager")));
        assertTrue(implementationErrors.toString(), implementationErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config idempotencyStore"
                        + " declares unsupported implementation example.CustomIdempotencyStore")));
        assertTrue(implementationErrors.toString(), implementationErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config sagaLogStore"
                        + " declares unsupported implementation example.CustomSagaLogStore")));
        assertTrue(implementationErrors.toString(), implementationErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config tccBranchStore"
                        + " declares unsupported implementation example.CustomTccBranchStore")));
        assertTrue(implementationErrors.toString(), implementationErrors.stream().anyMatch(error -> error.contains(
                "PHY backend localJdbc localJdbc adapter config tccReservationStore"
                        + " declares unsupported implementation example.CustomTccReservationStore")));

        SppDomain placeholderImplementations = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                phy{
                    backend localJdbc{
                        config transactionCoordinator = "${runtime.transactionCoordinator}";
                        config idempotencyStore = "${runtime.idempotencyStore}";
                        config sagaLogStore = "${runtime.sagaLogStore}";
                        config tccBranchStore = "${runtime.tccBranchStore}";
                        config tccReservationStore = "${runtime.tccReservationStore}";
                    }
                }
                """);
        assertTrue(placeholderImplementations.validatePhysicalRuntimeDeclarations().toString(),
                placeholderImplementations.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void localJdbcQuantumBackendMustSelectStoreWhenMultipleQuantumStoresExist() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                Role Inventory{ quantum int availableQty; }
                Role SalesItem{ quantum int orderQty; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update reserveInventory(Inventory inventory, SalesItem item);
                """, """
                domain commerce;
                quantumStore creditQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                quantumStore inventoryQuantum{
                    attribute Inventory.availableQty;
                    attribute SalesItem.orderQty;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                        quantum resource(inventoryAccounting,
                            Inventory.availableQty, SalesItem.orderQty) reserveInventory;
                    }
                }
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        quantum creditAccounting;
                        quantum inventoryAccounting;
                    }
                    quantumStore creditQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-credit-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_credit_quantum_journal";
                        config valueTable = "spp_credit_quantum_value";
                    }
                    quantumStore inventoryQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-inventory-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_inventory_quantum_journal";
                        config valueTable = "spp_inventory_quantum_value";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains(
                "PHY backend localJdbc localJdbc adapter must declare quantumStore <name>;"
                        + " when multiple APP quantum stores are declared"));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                Role Inventory{ quantum int availableQty; }
                Role SalesItem{ quantum int orderQty; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore creditQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                quantumStore inventoryQuantum{
                    attribute Inventory.availableQty;
                    attribute SalesItem.orderQty;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                    }
                }
                phy{
                    backend localJdbc{
                        adapter localJdbc;
                        quantumStore creditQuantum;
                        quantum creditAccounting;
                    }
                    quantumStore creditQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-credit-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_credit_quantum_journal";
                        config valueTable = "spp_credit_quantum_value";
                    }
                    quantumStore inventoryQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-inventory-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_inventory_quantum_journal";
                        config valueTable = "spp_inventory_quantum_value";
                    }
                }
                """);
        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void physicalFilesCanOnlyDeclarePhysicalBlocks() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """, """
                domain commerce;
                header deploymentOnly{
                    String forbidden;
                }
                phy{
                    backend localJdbc;
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY file creditcommerce.phy can only declare phy blocks")));
    }

    @Test
    public void physicalFilesMustReferenceExistingDomain() throws Exception {
        SppDomain domain = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                """, """
                domain missingcommerce;
                phy{
                    backend localJdbc;
                }
                """);

        List<String> errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY file creditcommerce.phy references undefined domain missingcommerce")));
    }

    @Test
    public void phyBackendDeclarationsMustReferenceAppSemantics() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                atomic query queryLocalOrder(SalesOrder order);
                Scenario serial submitOrder(SalesOrder order){
                    transaction{ openSalesOrder(order); }
                }
                """, """
                domain commerce;
                header baseIn{ unique String globalSerialNumber; }
                header baseOut{ notnull String appId(Default = "commerce"); }
                scope{
                    local (in baseIn, out baseOut){
                        queryLocalOrder;
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                        saga submitOrder;
                    }
                }
                phy{
                    backend unknownBackend;
                    backend seata{
                        at missingSalesOrder;
                        saga missingSubmitOrder;
                    }
                    backend seata{
                        config transactionServiceGroup = "first";
                        config transactionServiceGroup = "second";
                        at salesOrder;
                        at salesOrder;
                    }
                    backend creditTx{
                        adapter seata;
                        config transactionServiceGroup = "";
                        config quantumSupported = maybe;
                        config transactionBridgeClass = "";
                    }
                    remote queryLocalOrder{
                        adapter httpJson;
                    }
                    remote missingRemote{
                        adapter httpJson;
                    }
                    remote missingRemote{
                        adapter rpc;
                        config endpoint = "first";
                        config endpoint = "second";
                    }
                    repository objectStore{
                        adapter missingRepository;
                    }
                    repository objectStore{
                        adapter objectSnapshot;
                        config table = "first";
                        config table = "second";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Unsupported PHY backend unknownBackend")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seata is declared multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seata declares duplicate binding at:salesOrder")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seata declares duplicate config transactionServiceGroup")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seata Seata adapter config transactionServiceGroup must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend creditTx Seata adapter config transactionServiceGroup must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend creditTx Seata adapter config quantumSupported must be true or false")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend creditTx Seata adapter config transactionBridgeClass"
                        + " must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter for missingRemote is declared multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter queryLocalOrder must reference a remote service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter missingRemote must reference a remote service")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY remote adapter missingRemote declares duplicate config endpoint")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore is declared multiple times")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "Unsupported PHY repository adapter missingRepository on repository objectStore")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY repository adapter objectStore declares duplicate config table")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "at binding missingSalesOrder must reference a local AT resource")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "saga binding missingSubmitOrder must reference a local saga service")));
        assertTrue(invalid.validateAppDeclarations().toString(), invalid.validateAppDeclarations()
                .containsAll(errors));
    }

    @Test
    public void phyBindingsMustAssignEachTransactionTargetToOneBackend() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                phy{
                    backend localJdbc{
                        at salesOrder;
                        saga submitOrder;
                    }
                    backend seata{
                        config transactionServiceGroup = "commerce-tx-group";
                        at salesOrder;
                        saga submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY binding at:salesOrder is assigned to multiple backends: localJdbc and seata")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY binding saga:submitOrder is assigned to multiple backends: localJdbc and seata")));
    }

    @Test
    public void phyKindBindingsMustNotOverlapExactBindingsOnOtherBackends() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
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
                phy{
                    backend localJdbc{
                        at;
                        saga;
                    }
                    backend seata{
                        config transactionServiceGroup = "commerce-tx-group";
                        at salesOrder;
                        saga submitOrder;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY binding at:salesOrder overlaps at:* assigned to backend localJdbc")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY binding saga:submitOrder overlaps saga:* assigned to backend localJdbc")));
    }

    @Test
    public void phyBackendsMustCoverLocalTransactionTargetsWhenNotDefaultBackend() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                atomic update cancelSalesOrder(SalesOrder order);
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
                        at resource(orderCancel) snapshot(argument(0, SalesOrder.orderNo)) cancelSalesOrder;
                        saga submitOrder;
                    }
                }
                phy{
                    backend localJdbc{
                        at salesOrder;
                    }
                    backend seata{
                        config transactionServiceGroup = "commerce-tx-group";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 2, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY local AT resource orderCancel is not assigned to any backend")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY local saga service submitOrder is not assigned to any backend")));

        SppDomain valid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                atomic update cancelSalesOrder(SalesOrder order);
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
                        at resource(orderCancel) snapshot(argument(0, SalesOrder.orderNo)) cancelSalesOrder;
                        saga submitOrder;
                    }
                }
                phy{
                    backend localJdbc{
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void seataBackendMustExplicitlyEnableQuantumBindings() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update releaseCredit(CreditAccount account, OrderAmount amount);
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
                        quantum resource(creditReleaseAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) releaseCredit;
                    }
                }
                phy{
                    backend seataDefault{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        quantum creditAccounting;
                    }
                    backend seataDisabled{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config quantumSupported = false;
                        quantum creditReleaseAccounting;
                    }
                    backend seataBlankQuantumSupported{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config quantumSupported = "${commerce.seata.quantum-supported:}";
                    }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertEquals(errors.toString(), 3, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataDefault Seata adapter must set quantumSupported = true"
                        + " when declaring quantum bindings")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataDisabled Seata adapter must set quantumSupported = true"
                        + " when declaring quantum bindings")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataBlankQuantumSupported Seata adapter config quantumSupported"
                        + " must be true or false")));

        SppDomain validPlaceholders = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update releaseCredit(CreditAccount account, OrderAmount amount);
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
                        quantum resource(creditReleaseAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) releaseCredit;
                    }
                }
                phy{
                    backend seataPlaceholderTrue{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config quantumSupported = "${commerce.seata.quantum-supported:true}";
                        quantum creditAccounting;
                    }
                    backend seataRuntimeOnly{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config quantumSupported = "${commerce.runtime.quantum-supported}";
                        quantum creditReleaseAccounting;
                    }
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                }
                """);

        assertTrue(validPlaceholders.validatePhysicalRuntimeDeclarations().toString(),
                validPlaceholders.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void seataQuantumSupportedPlaceholderContributesToBackendCapabilities() throws Exception {
        SppDomain valid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{ quantum double occupiedAmount; }
                atomic update reserveCredit(CreditAccount account, OrderAmount amount);
                atomic update confirmReserveCredit(CreditAccount account, OrderAmount amount);
                atomic update cancelReserveCredit(CreditAccount account, OrderAmount amount);
                quantum update syncCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                tables roles{
                    table OrderAmount(OrderAmount){
                        primarykey String amountId;
                        null double lockedAmount;
                    }
                }
                scope{
                    local{
                        tcc resource(creditTcc){
                            reserve OrderAmount.occupiedAmount -> OrderAmount.lockedAmount;
                            confirm confirmReserveCredit;
                            cancel cancelReserveCredit;
                        } reserveCredit;
                        tcc resource(creditTcc) confirmReserveCredit;
                        tcc resource(creditTcc) cancelReserveCredit;
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) syncCredit;
                    }
                }
                phy{
                    backend creditTx{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config quantumSupported = "${commerce.seata.quantum-supported:true}";
                        quantumStore globalQuantum;
                        tcc;
                        quantum;
                    }
                    quantumStore globalQuantum{
                        adapter tccObjectStore;
                        config implementationStrategy = "TCC_OBJECT_STORE";
                        config accessMode = "TCC_OBJECT_STORE_ADAPTER";
                        config idempotencyMode = "OBJECT_STORE_IDEMPOTENCY";
                        config reversalMode = "NEGATIVE_DELTA";
                    }
                }
                """);

        assertTrue(valid.validatePhysicalRuntimeDeclarations().toString(),
                valid.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void physicalRuntimeCatalogGenerationRejectsMixedQuantumImplementationStrategies() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity CreditAccount{ quantum double availableCredit; }
                Role OrderAmount{
                    quantum double occupiedAmount;
                    quantum double capturedAmount;
                }
                quantum update reserveCredit(CreditAccount account, OrderAmount amount);
                quantum update captureCredit(CreditAccount account, OrderAmount amount);
                """, """
                domain commerce;
                quantumStore globalQuantum{
                    attribute CreditAccount.availableCredit;
                    attribute OrderAmount.occupiedAmount;
                }
                quantumStore serviceQuantum{
                    attribute OrderAmount.capturedAmount;
                }
                scope{
                    local{
                        quantum resource(creditAccounting,
                            CreditAccount.availableCredit, OrderAmount.occupiedAmount) reserveCredit;
                        quantum resource(creditCaptureAccounting,
                            OrderAmount.capturedAmount, CreditAccount.availableCredit) captureCredit;
                    }
                }
                phy{
                    quantumStore globalQuantum{
                        adapter jdbcQuantum;
                        config datasourceUrl = "jdbc:h2:mem:spp-quantum;DB_CLOSE_DELAY=-1";
                        config driverClassName = "org.h2.Driver";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                        config journalTable = "spp_quantum_journal";
                        config valueTable = "spp_quantum_attribute_value";
                    }
                    quantumStore serviceQuantum{
                        adapter quantumService;
                        config endpoint = "https://quantum.example.test";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeCatalogGeneration();

        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY quantum implementation strategies are mutually exclusive within one physical profile")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "GLOBAL_QUANTUM_STORE[globalQuantum=jdbcQuantum]")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "QUANTUM_SERVICE[serviceQuantum=quantumService]")));
    }

    @Test
    public void seataReflectiveBridgeStaticConfigsAreValidated() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity SalesOrder{ String orderNo; }
                atomic update openSalesOrder(SalesOrder order);
                """, """
                domain commerce;
                scope{
                    local{
                        at resource(salesOrder) snapshot(argument(0, SalesOrder.orderNo)) openSalesOrder;
                    }
                }
                phy{
                    backend seataInvalid{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config transactionBridgeClass =
                            "com.servpp.service.runtime.ReflectiveSeataGlobalTransactionBridge";
                        config seataGlobalTransactionContextClass = "";
                        config seataGlobalTransactionClass = "";
                        config seataTransactionTimeoutMillis = 0;
                        at salesOrder;
                    }
                    backend seataNaN{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config transactionBridgeClass =
                            "com.servpp.service.runtime.ReflectiveSeataGlobalTransactionBridge";
                        config seataTransactionTimeoutMillis = notANumber;
                    }
                    backend seataPlaceholder{
                        adapter seata;
                        config transactionServiceGroup = "${commerce.seata.tx-group:commerce-tx-group}";
                        config transactionBridgeClass =
                            "com.servpp.service.runtime.ReflectiveSeataGlobalTransactionBridge";
                        config seataTransactionTimeoutMillis = "${commerce.seata.timeout:1500}";
                    }
                    backend seataDeploymentGroup{
                        adapter seata;
                        config transactionServiceGroup = "${commerce.seata.tx-group}";
                        config transactionBridgeClass =
                            "com.servpp.service.runtime.ReflectiveSeataGlobalTransactionBridge";
                    }
                    backend seataBlankGroup{
                        adapter seata;
                        config transactionServiceGroup = "${commerce.seata.tx-group:}";
                    }
                    backend seataBlankTimeout{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config seataTransactionTimeoutMillis = "${commerce.seata.timeout:}";
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataBlankGroup Seata adapter config transactionServiceGroup"
                        + " must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataInvalid Seata adapter config seataGlobalTransactionContextClass"
                        + " must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataInvalid Seata adapter config seataGlobalTransactionClass"
                        + " must not be blank when declared")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataInvalid Seata adapter config seataTransactionTimeoutMillis must be positive")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataNaN Seata adapter config seataTransactionTimeoutMillis must be a number")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "PHY backend seataBlankTimeout Seata adapter config seataTransactionTimeoutMillis must be positive")));
        assertTrue(errors.toString(), errors.stream().noneMatch(error -> error.contains("seataPlaceholder")));
        assertTrue(errors.toString(), errors.stream().noneMatch(error -> error.contains("seataDeploymentGroup")));
    }

    @Test
    public void nativeSeataBridgeRequiresAtProxyUndoLogAndFrameworkStores() throws Exception {
        SppDomain invalid = compileDomain("""
                domain commerce;
                Entity Marker{ String value; }
                """, """
                domain commerce;
                scope{ local{} }
                phy{
                    backend creditTx{
                        adapter seata;
                        config transactionServiceGroup = "commerce-tx-group";
                        config transactionBridgeClass =
                            "com.servpp.service.runtime.seata.NativeSeataTransactionBridge";
                        config atDataSourceProxy = false;
                        config undoLogTable = "";
                        config frameworkStores = true;
                    }
                }
                """);

        List<String> errors = invalid.validatePhysicalRuntimeDeclarations();

        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config applicationId must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config atDataSourceProxy must be true for the native transaction bridge")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config undoLogTable must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config datasourceUrl must not be blank")));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(
                "config tccReservationTable must not be blank")));
    }

    private void assertTable(AppTables tables, AppTables.Category category,
                             String tableName, String className) {
        assertEquals(category, tables.getCategory());
        AppTable table = tables.getAppTableList().get(0);
        assertTable(table, category, tableName, className);
    }

    private void assertTable(AppTable table, AppTables.Category category,
                             String tableName, String className) {
        assertEquals(category, table.getCategory());
        assertEquals(tableName, table.getName());
        assertEquals(className, table.getEntity().getName());
    }

    private AppHeader namedHeader(String name) {
        AppHeader header = new AppHeader();
        header.setName(name);
        return header;
    }

    private SppDomain compileDomainWithRepository(String sppSource, String appSource) throws Exception {
        String domain = sourceDomainName(sppSource);
        return compileDomain(sppSource, appSource, "domain " + domain + "; phy { repository records { "
                + "adapter jdbcObjectRepository; config default = true; "
                + "config datasourceUrl = \"jdbc:h2:mem:app-semantics\"; config driverClassName = \"org.h2.Driver\"; } }");
    }

    private SppDomain compileDomainWithRepository(String sppSource, String appSource, String phySource) throws Exception {
        int end = phySource.lastIndexOf('}');
        String repository = "repository records { adapter jdbcObjectRepository; config default = true; "
                + "config datasourceUrl = \"jdbc:h2:mem:app-semantics\"; config driverClassName = \"org.h2.Driver\"; }";
        return compileDomain(sppSource, appSource, phySource.substring(0, end) + repository + phySource.substring(end));
    }

    @Test
    public void explicitConservationKeepsOtherQuantumWritesInGeneratedParticipant() throws Exception {
        var domain = compileDomain("""
                domain creditcommerce;
                Role Account { quantum double balance; quantum double debt; quantum double unused; }
                Role Cash { quantum double balance; }
                Role Income { quantum double balance; }
                quantum update settle(Account, Cash, Income) {
                    Account.balance += 90; Account.debt -= 10;
                    Cash.balance -= 100; Income.balance += 10;
                }
                """, """
                domain creditcommerce;
                quantumStore ledger { attribute Account.balance; attribute Account.debt; attribute Account.unused;
                    attribute Cash.balance; attribute Income.balance; }
                scope { local { quantum resource(funds, Account.balance, Cash.balance, Income.balance) settle; } }
                """);
        assertTrue(domain.validateQuantumConservationDeclarations().toString(), domain.validateQuantumConservationDeclarations().isEmpty());
        var service = (SppService) domain.getSppClass("Settle");
        String generated = service.getQuantumConservationParticipantMethod();
        assertTrue(generated, generated.contains("conserveArgument(0, \"$state\", \"Account.debt\", \"debt\")"));
        assertTrue(generated, generated.contains("conserveArgument(0, \"funds\", \"Account.balance\", \"balance\")"));
        assertFalse(generated, generated.contains("Account.unused"));
        assertTrue(service.getQuantumServiceMetadataMethod().contains("Account.debt"));
    }

    private SppDomain compileDomain(String sppSource, String appSource) throws Exception {
        File dir = Files.createTempDirectory("spp-app-semantics-test").toFile();
        String domainName = sourceDomainName(sppSource);
        File sppFile = write(new File(dir, domainName + ".spp"), sppSource);
        File appFile = write(new File(dir, domainName + ".app"), appSource);
        SppProject project = new SppProject();
        new SppCompiler(sppFile, project).compile();
        project.getSppDomainMap().values().forEach(SppDomain::dealEntityToRoleMaps);
        new AppCompiler(appFile, project).compile();
        return project.getSppDomainMap().values().iterator().next();
    }

    private SppDomain compileDomain(String sppSource, String appSource, String phySource) throws Exception {
        File dir = Files.createTempDirectory("spp-app-semantics-test").toFile();
        File sppFile = write(new File(dir, "creditcommerce.spp"), sppSource);
        File appFile = write(new File(dir, "creditcommerce.app"), appSource);
        File phyFile = write(new File(dir, "creditcommerce.phy"), phySource);
        SppProject project = new SppProject();
        new SppCompiler(sppFile, project).compile();
        project.getSppDomainMap().values().forEach(SppDomain::dealEntityToRoleMaps);
        new AppCompiler(appFile, project).compile();
        new AppCompiler(phyFile, project).compile();
        return project.getSppDomainMap().values().iterator().next();
    }

    private SppDomain compileDomainWithDefaultServices(String sppSource, String appSource) throws Exception {
        File dir = Files.createTempDirectory("spp-app-semantics-test").toFile();
        File sppFile = write(new File(dir, "creditcommerce.spp"), sppSource);
        File appFile = write(new File(dir, "creditcommerce.app"), appSource);
        SppProject project = new SppProject();
        new SppCompiler(sppFile, project).compile();
        project.getSppDomainMap().values().forEach(SppDomain::dealEntityToRoleMaps);
        SppDomain domain = project.getSppDomainMap().values().iterator().next();
        domain.generateDefaultServices(new ILogable() {
        });
        new AppCompiler(appFile, project).compile();
        return domain;
    }

    private File write(File file, String source) throws IOException {
        Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
        return file;
    }

    private String sourceDomainName(String source) {
        String marker = "domain ";
        int start = source.indexOf(marker);
        if (start < 0) {
            return "creditcommerce";
        }
        int nameStart = start + marker.length();
        int nameEnd = source.indexOf(';', nameStart);
        if (nameEnd < 0) {
            return "creditcommerce";
        }
        return source.substring(nameStart, nameEnd).trim();
    }
}
