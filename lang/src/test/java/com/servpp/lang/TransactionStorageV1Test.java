package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

/** Compiler regression coverage for transaction planning and role storage ownership. */
public class TransactionStorageV1Test {
    private SppDomain compile(String body, String app) throws Exception {
        var project = new SppProject();
        var source = Files.createTempFile("spp-transaction-storage", ".spp");
        var config = Files.createTempFile("spp-transaction-storage", ".app");
        try {
            Files.writeString(source,"domain rules; Role Values { quantum double amount; } " + body);
            new SppCompiler(source.toFile(),project).compile();
            Files.writeString(config,"domain rules; transactions { default at resource records; } scope { local { " + app + " } }");
            new AppCompiler(config.toFile(),project).compile();
            return project.getDomain("rules");
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(config); }
    }
    @Test public void mixedSourceBoundaryGeneratesSourcePlanInsteadOfModeConflict() throws Exception {
        var domain = compile("atomic update save(Values v); quantum update change(Values v) { v.amount += 1; } Scenario serial commit(Values v) { transaction { save(v); change(v); } }",
                "at resource(records) save; quantum resource(ledger, Values.amount) change; commit;");
        assertTrue(domain.validateDeclaredTransactionGroups().toString(),domain.validateDeclaredTransactionGroups().isEmpty());
        assertTrue(((SppService)domain.getSppClass("Commit")).getScenarioBodyJava().contains("usesSourceTransactionPlan"));
        assertTrue(domain.unusedParticipantWarnings().stream().anyMatch(message -> message.contains("quantum commits first")));
    }
    @Test public void unsupportedNestedAndQuantumIndependentParentAreRejected() throws Exception {
        for (String propagation : new String[]{"NESTED","REQUIRES_NEW"}) {
            var domain = compile("quantum update change(Values v) { v.amount += 1; } Scenario serial commit(Values v) { transaction { change(v); transaction " + propagation + " { change(v); } } }",
                    "quantum resource(ledger,Values.amount) change; commit;");
            assertFalse(domain.validateDeclaredTransactionGroups().isEmpty());
        }
    }
    @Test public void inputCalculationCannotBeMovedAfterItsQuantumConsumer() throws Exception {
        var domain = compile("atomic update save(Values v); atomic calculate prepare(Values v) { } quantum update change(Values v) { v.amount += 1; } Scenario serial commit(Values v) { transaction { prepare(v); save(v); change(v); } }",
                "at resource(records) save; quantum resource(ledger,Values.amount) change; prepare; commit;");
        assertTrue(domain.validateDeclaredTransactionGroups().stream().anyMatch(message -> message.contains("preparation must be outside")));
    }
    @Test public void ownershipKeepsOverriddenFactButExcludesInheritedNonKeyFact() throws Exception {
        var project = new SppProject();
        var source = Files.createTempFile("role-storage-owner", ".spp");
        var app = Files.createTempFile("role-storage-owner", ".app");
        try {
            Files.writeString(source,"domain owners; Entity Product { String code; String name; double price; } Role Goods { double price; } take Product as Goods { takeall; } atomic query findProduct(Product); atomic query findGoods(Goods);");
            new SppCompiler(source.toFile(),project).compile();
            project.getDomain("owners").dealEntityToRoleMaps();
            Files.writeString(app,"domain owners; transactions { default at resource records; } tables entities { table PRODUCTS(Product) { primarykey(Product.code); } } tables roles { table GOODS(Goods) { primarykey(Goods.code); } } scope { local {} }");
            new AppCompiler(app.toFile(),project).compile();
            DomainDeploymentValidator.validate(project);
            var role = (SppClass)project.getDomain("owners").getSppClass("Goods");
            assertTrue(role.getField("price").isStoredHere());
            assertFalse(role.getField("name").isStoredHere());
            assertTrue(role.getField("code").isPerformerStorageIdentity());
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(app); }
    }

    @Test public void ordinaryTccAndConflictingRoleModesAreRejected() throws Exception {
        var ordinary = compile("atomic update save(Values v);", "tcc resource(records) save;");
        assertTrue(ordinary.validateTransactionResourceBindings().stream().anyMatch(message -> message.contains("cannot use TCC")));
        var project = new SppProject();
        var source = Files.createTempFile("role-mode", ".spp"); var app = Files.createTempFile("role-mode", ".app");
        try {
            Files.writeString(source,"domain owners; Entity Person { String code; } Role Buyer { String tier; } take Person as Buyer { takeall; } atomic update first(Buyer); atomic update second(Buyer);");
            new SppCompiler(source.toFile(),project).compile();
            project.getDomain("owners").dealEntityToRoleMaps();
            Files.writeString(app,"domain owners; transactions { default at resource records; } tables entities { table PEOPLE(Person) { primarykey(Person.code); } } tables roles { table BUYERS(Buyer) { primarykey(Buyer.code); } } scope { local { at resource(records) persist(update, argument(0, Buyer.code)) first; saga resource(actions) persist(update, argument(0, Buyer.code)) second; } }");
            new AppCompiler(app.toFile(),project).compile();
            assertTrue(DomainDeploymentValidator.validate(project).stream().anyMatch(message -> message.contains("must use one transaction mode")));
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(app); }
    }

}
