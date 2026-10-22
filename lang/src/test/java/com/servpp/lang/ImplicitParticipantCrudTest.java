package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression coverage for implicit participant CRUD generation and binding. */
public class ImplicitParticipantCrudTest {
    @Test public void builtinCrudKeysCanReferenceAnIndependentRole() throws Exception {
        var domain=compile("Contract Extension { Registration trial; int extensionNumber; } Scenario serial persist(Buyer, Registration, Extension) { findRegistration; findExtension; transaction { insertRegistration; updateRegistration; deleteRegistration; insertExtension; updateExtension; deleteExtension; } }");
        var project=new SppProject();project.getSppDomainMap().put("shop",domain);
        var app=Files.createTempFile("implicit-crud-private-association", ".app");
        try {
            Files.writeString(app,"domain shop; tables roles { table BUYERS(Buyer) { primarykey String buyerRowId; identity independent; } } "
                    + "tables contracts { table REGISTRATIONS(Registration) { primarykey(Registration.buyer); foreignkey(Registration.buyer) references Buyer; } table EXTENSIONS(Extension) { primarykey(Extension.trial, Extension.extensionNumber); foreignkey(Extension.trial) references Registration; } } "
                    + "scope { local {} } phy { repository records { adapter jdbcObjectRepository; config default = true; config persistenceMode = AT; config persistenceResource = records; config datasourceUrl = \"jdbc:h2:mem:private-association-crud\"; config driverClassName = \"org.h2.Driver\"; } }");
            new AppCompiler(app.toFile(),project).compile();var errors=domain.validateAppDeclarations();assertTrue(errors.toString(),errors.isEmpty());
            var query=(SppService)domain.getSppClass("FindRegistration");var key=query.getScopeItem().getRepositoryHydrationRules().get(0).firstKey();
            assertEquals("buyer.@storageIdentity",key.getObjectIdPath());assertEquals("Registration.buyer.buyerRowId",key.repositoryObjectPath());
            var extension=(SppService)domain.getSppClass("FindExtension");var extensionKeys=extension.getScopeItem().getRepositoryHydrationRules().get(0).keyTargets();
            assertEquals(java.util.List.of("trial.buyer.@storageIdentity","extensionNumber"),extensionKeys.stream().map(keyItem->keyItem.getObjectIdPath()).toList());
            for(String operation:java.util.List.of("Insert","Update","Delete")) {
                var service=(SppService)domain.getSppClass(operation+"Registration");
                assertEquals("buyer.@storageIdentity",service.getScopeItem().getPersistenceRules().get(0).keys().get(0).getObjectIdPath());
            }
        }finally{Files.deleteIfExists(app);}
    }
    private SppDomain compile(String source) throws Exception {
        var path = Files.createTempFile("implicit-participant-crud", ".spp");
        try {
            Files.writeString(path, "domain shop; Role Buyer { String membershipNumber; } "
                    + "Contract Registration { Buyer buyer; } " + source);
            var project = new SppProject();
            var handler = (com.servpp.lang.general.SppGeneralHandler) new SppCompiler(path.toFile(), project).compile();
            var domain = project.getDomain("shop");
            domain.generateDefaultServices(handler);
            domain.prepareImplicitParticipantServices();
            return domain;
        } finally { Files.deleteIfExists(path); }
    }

    @Test public void defaultBindingErrorsAreReportedOnFirstValidationWithoutDuplicates() throws Exception {
        var domain=compile("atomic query lookup(Buyer);");
        var project=new SppProject();project.getSppDomainMap().put("shop",domain);
        var app=Files.createTempFile("implicit-crud-invalid-identity", ".app");
        try {
            Files.writeString(app,"domain shop; tables roles { table BUYERS(Buyer) { primarykey String lookupKey; } } scope { local { lookup; } } "
                    + "phy { repository records { adapter jdbcObjectRepository; config default = true; "
                    + "config datasourceUrl = \"jdbc:h2:mem:invalid-crud-key\"; config driverClassName = \"org.h2.Driver\"; } }");
            new AppCompiler(app.toFile(),project).compile();
            var first=domain.validateAppDeclarations();
            assertTrue(first.toString(),first.stream().anyMatch(error -> error.contains("cannot infer APP DDL primary key lookupKey")));
            var second=domain.validateAppDeclarations();
            assertEquals(first,second);
            assertEquals(first.size(),new java.util.HashSet<>(first).size());
        } finally { Files.deleteIfExists(app); }
    }

    @Test public void opaqueAppPrimaryKeyQueriesUseFrameworkStorageIdentity() throws Exception {
        var domain=compile("Scenario serial read(Buyer) { findBuyer; }");
        var project=new SppProject();project.getSppDomainMap().put("shop",domain);
        var app=Files.createTempFile("implicit-crud-storage-identity", ".app");
        try {
            Files.writeString(app,"domain shop; tables roles { table BUYERS(Buyer) { primarykey String buyerRowId; } } scope { local { } } "
                    + "phy { repository records { adapter jdbcObjectRepository; config default = true; "
                    + "config datasourceUrl = \"jdbc:h2:mem:opaque-crud-key\"; config driverClassName = \"org.h2.Driver\"; } }");
            new AppCompiler(app.toFile(),project).compile();
            var errors=domain.validateAppDeclarations();
            assertTrue(errors.toString(),errors.isEmpty());
            var query=(SppService)domain.getSppClass("FindBuyer");
            var rule=query.getScopeItem().getRepositoryHydrationRules().get(0);
            assertEquals("@storageIdentity",rule.getObjectIdPath());
            assertEquals("APP_DDL_STORAGE_IDENTITY",rule.sourceHint());
            assertNull(((SppClass)domain.getSppClass("Buyer")).getField("buyerRowId"));
        } finally { Files.deleteIfExists(app); }
    }

    @Test public void inferredPersistencePreservesAnExplicitTransactionBinding() throws Exception {
        var domain = compile("atomic update save(Buyer);");
        var project = new SppProject(); project.getSppDomainMap().put("shop", domain);
        var app = Files.createTempFile("implicit-crud-transaction", ".app");
        try {
            Files.writeString(app, "domain shop; tables roles { table BUYERS(Buyer) { primarykey(Buyer.membershipNumber); } } "
                    + "scope { local { at resource(records) save; } } phy { repository records { adapter jdbcObjectRepository; "
                    + "config default = true; config datasourceUrl = \"jdbc:h2:mem:implicit-crud\"; config driverClassName = \"org.h2.Driver\"; } }");
            new AppCompiler(app.toFile(), project).compile();
            var errors = domain.prepareDefaultParticipantBindings();
            assertTrue(errors.toString(), errors.isEmpty());
            var scope = ((SppService)domain.getSppClass("Save")).getScopeItem();
            assertEquals(java.util.List.of("AT"), scope.getTransactionModifierNames());
            assertEquals(java.util.List.of("records"), scope.getResourceModifierIds());
            assertEquals(1, scope.getPersistenceRules().size());
            domain.prepareDefaultParticipantBindings();
            assertEquals(1, scope.getTransactionModifierNames().size());
            assertEquals(1, scope.getPersistenceRules().size());
        } finally { Files.deleteIfExists(app); }
    }

    @Test public void defaultCallsInferParticipantAndPersistenceOperation() throws Exception {
        var domain = compile("Scenario serial register(Buyer, Registration) { findBuyer; "
                + "transaction { insertBuyer; updateBuyer; deleteBuyer; } }");
        for (String operation : new String[]{"Find", "Insert", "Update", "Delete"}) {
            var service = (SppService) domain.getSppClass(operation + "Buyer");
            assertTrue(service.isReal());
            assertTrue(service.isImplicitParticipantCrud());
            assertEquals("Buyer", service.getServiceBody().getLocalVar(0).getType().getName());
            assertTrue(service.getReturns().getSppLocalVarList().isEmpty());
        }
        assertEquals("insert", ((SppService) domain.getSppClass("InsertBuyer")).getPersistenceOperation());
    }

    private java.util.List<SppServiceCall> calls(java.util.List<IServiceBodyStatement> statements) {
        var result = new java.util.ArrayList<SppServiceCall>();
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call) result.add(call);
            else if (statement instanceof ExecuteBlock block) result.addAll(calls(block.getExecuteCallList()));
        }
        return result;
    }

    @Test public void scalarAndCollectionCallsShareSourceSpelling() throws Exception {
        var domain = compile("Scenario serial load(Buyer buyer, Buyer[] buyers) { "
                + "findBuyer(buyers); findBuyer(buyer); }");
        var scene = (SppService) domain.getSppClass("Load");
        var calls = calls(scene.getServiceBody().getSppServiceCallList());
        assertEquals(2, calls.size());
        var first = calls.get(0);
        var second = calls.get(1);
        assertEquals(1, first.getCallee().getServiceBody().getLocalVar(0).getArrayDimension());
        assertEquals(0, second.getCallee().getServiceBody().getLocalVar(0).getArrayDimension());
        assertNotSame(first.getCallee(), second.getCallee());
        assertNotNull(scene.getField(first.getCallee().getFuncName()));
        assertNotNull(scene.getField(second.getCallee().getFuncName()));
        domain.prepareImplicitParticipantServices();
        assertEquals(1, first.getCallee().getServiceBody().getSppLocalVarList().size());
    }

    @Test public void ambiguousImplicitParticipantIsRejected() throws Exception {
        var domain = compile("Scenario serial load(Buyer one, Buyer two) { findBuyer; }");
        assertTrue(domain.validateAppDeclarations().stream().anyMatch(message -> message.contains("unambiguous")));
    }

    @Test public void explicitBusinessServiceIsPreserved() throws Exception {
        var domain = compile("atomic query findBuyer(Buyer buyer); Scenario serial load(Buyer buyer) { findBuyer; }");
        assertFalse(((SppService)domain.getSppClass("FindBuyer")).isImplicitParticipantCrud());
    }

    @Test public void entityUpdateUsesRuntimeCrudInsteadOfLegacyMapper() throws Exception {
        var domain = compile("Entity Person { String customerNumber; } Scenario serial revise(Person, Registration) { transaction { updatePerson; } }");
        var update = (SppService) domain.getSppClass("UpdatePerson");
        assertTrue(update.isImplicitParticipantCrud());
        assertNull(update.getDefaultClass());
        assertEquals("update", update.getPersistenceOperation());
        assertEquals(1, update.getServiceBody().getSppLocalVarList().size());
        assertTrue(domain.getSppDefaultServices().stream().noneMatch(group -> group.getServiceList().contains(update)));
    }

    @Test public void explicitTakeUsesNamedTargetWithoutProxyDeclaration() throws Exception {
        var domain = compile("Entity Person { String customerNumber; } take Buyer as Person { String customerNumber = membershipNumber; } "
                + "Scenario serial revise(Buyer buyer, Registration agreement) { transaction { updatePerson(buyer); updateBuyer(buyer); } }");
        var person = (SppService) domain.getSppClass("UpdatePerson");
        var buyer = (SppService) domain.getSppClass("UpdateBuyer");
        assertEquals("Person", person.getImplicitCrudTarget().getName());
        assertEquals("Buyer", person.getServiceBody().getLocalVar(0).getType().getName());
        assertEquals("Buyer", buyer.getImplicitCrudTarget().getName());
        assertTrue(domain.getAppParseErrors().toString(), domain.getAppParseErrors().isEmpty());
    }

    @Test public void similarFactsWithoutTakeCannotBindDifferentParticipant() throws Exception {
        var domain = compile("Entity Person { String membershipNumber; } Scenario serial revise(Buyer buyer, Registration agreement) { transaction { updatePerson(buyer); } }");
        assertTrue(domain.getAppParseErrors().stream().anyMatch(message -> message.contains("unambiguous")));
    }

    @Test public void implicitTakeRequiresOneCandidate() throws Exception {
        var domain = compile("Entity Person { String membershipNumber; } Role Merchant { String membershipNumber; } "
                + "take Buyer as Person { mapsame; } take Merchant as Person { mapsame; } "
                + "Scenario serial revise(Buyer buyer, Merchant merchant, Registration agreement) { transaction { updatePerson; } }");
        assertTrue(domain.getAppParseErrors().stream().anyMatch(message -> message.contains("unambiguous")));
    }
}
