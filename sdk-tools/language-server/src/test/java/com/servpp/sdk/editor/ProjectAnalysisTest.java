package com.servpp.sdk.editor;

import org.eclipse.lsp4j.SymbolKind;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ProjectAnalysisTest {
    private final SourceDocument model = source("model.spp", "domain ledger; Role Facts { int amount; } Role Other { int amount; }");
    private final SourceDocument service = source("logic.spp", "domain ledger;\natomic calculate twice(Facts, Other other) { Facts.amount *= 2; other.amount += 1; }\n"
            + "Scenario serial preview(Facts) { twice; }");

    @Test public void bindsTypesServicesAndMembersAcrossFiles() {
        var project = new ProjectAnalysis(List.of(model, service));
        var type = resolve(project, service, "Facts,", 0).get(0);
        assertEquals(SymbolKind.Interface, type.kind());
        assertEquals(model.uri(), type.document().uri());
        var amount = resolve(project, service, "amount *=", 0).get(0);
        assertEquals("Facts", amount.owner().name());
        var otherAmount = resolve(project, service, "amount +=", 0).get(0);
        assertEquals("Other", otherAmount.owner().name());
        var call = resolve(project, service, "twice;", 0).get(0);
        assertEquals("twice", call.name());
        assertEquals(SymbolKind.Function, call.kind());
    }

    @Test public void preservesDomainIsolationAndExplicitProviderRelations() {
        var external = source("external.spp", "domain remote; Role Facts { int amount; }");
        var reference = source("reference.spp", "domain client; Reference Local from remote.Facts { int amount; }");
        var project = new ProjectAnalysis(List.of(model, service, external, reference));
        assertEquals(model.uri(), resolve(project, service, "Facts,", 0).get(0).document().uri());
        assertEquals(external.uri(), resolve(project, reference, "Facts", 0).get(0).document().uri());
        assertEquals("remote", resolve(project, reference, "remote.", 0).get(0).domain());
    }

    @Test public void retainsAmbiguousTargetsAndAvoidsUnrelatedNameFallback() {
        var duplicate = source("duplicate.spp", "domain ledger; Role Facts { int amount; }");
        var unknown = source("unknown.spp", "domain ledger; Scenario serial run(Facts) { missing; }");
        var project = new ProjectAnalysis(List.of(model, duplicate, service, unknown));
        assertEquals(2, resolve(project, service, "Facts,", 0).size());
        assertTrue(resolve(project, unknown, "missing", 0).isEmpty());
    }

    @Test public void resolvesAppScopeAndImplementationParametersButNotJavaNames() {
        var app = source("service.app", "domain ledger; scope { local { twice; } }\nservices { twice { Facts.amount *= 2; } }\n"
                + "bind twice to com.example.Handler;");
        var project = new ProjectAnalysis(List.of(model, service, app));
        assertEquals(SymbolKind.Function, resolve(project, app, "twice;", 0).get(0).kind());
        assertEquals("Facts", resolve(project, app, "amount", 0).get(0).owner().name());
        assertTrue(resolve(project, app, "Handler", 0).isEmpty());
    }

    @Test public void localBindingDoesNotEscapeNestedBlock() {
        var document = source("nested.spp", "domain ledger; Role Facts { int amount; }\n"
                + "atomic calculate run(Facts) { { int value = 1; value += 1; } Facts.amount += 1; }");
        var project = new ProjectAnalysis(List.of(document));
        assertEquals("value", resolve(project, document, "value +=", 0).get(0).name());
        assertEquals("amount", resolve(project, document, "amount +=", 0).get(0).name());
    }

    @Test public void resolvesMappingEndsAndInheritedFactsToTheirTrueSources() {
        var document = source("mapping.spp", "domain ledger; Entity Account { String id; int balance; }\n"
                + "Role Facts { int amount; }\n"
                + "take Account as Facts { int amount = balance; int fee; takeall; }\n"
                + "atomic calculate run(Facts) { Facts.amount += 1; Facts.fee += 1; Facts.id = \"new\"; }\n");
        assertTrue(document.problems().toString(), document.problems().isEmpty());
        var project = new ProjectAnalysis(List.of(document));
        assertEquals("Facts", resolve(project, document, "amount = balance", 0).get(0).owner().name());
        assertEquals("Account", resolve(project, document, "balance; int fee", 0).get(0).owner().name());
        assertEquals("Facts", resolve(project, document, "fee +=", 0).get(0).owner().name());
        assertEquals("Account", resolve(project, document, "id =", 0).get(0).owner().name());
    }

    @Test public void mappingDeclaredParticipantsNormalizeAgainstTheActualTargetType() {
        var declarations = source("declarations.spp", "domain ledger; Entity Account { int amount; } Role Facts;");
        var mapping = source("mapping.spp", "domain ledger; take Account as Facts { Account; }\n"
                + "atomic calculate run(Facts) { Facts.account.amount += 1; }");
        var project = new ProjectAnalysis(List.of(declarations, mapping));
        var participant = resolve(project, mapping, "account.amount", 0).get(0);
        assertEquals("account", participant.name());
        assertEquals("Facts", participant.owner().name());
        assertEquals("Account", resolve(project, mapping, "amount +=", 0).get(0).owner().name());
    }

    @Test public void resolvesEnumMembersAndRetainsArrayDeclaratorTypes() {
        var document = source("enums.spp", "domain ledger; Enum State { Pending, Complete }\n"
                + "Role Facts { State state; int amounts[]; }\n"
                + "atomic calculate run(Facts) { Facts.state = State.Complete; int[] values; }");
        var project = new ProjectAnalysis(List.of(document));
        assertEquals(SymbolKind.EnumMember, resolve(project, document, "Complete;", 0).get(0).kind());
        assertEquals("int[]", project.symbols().stream().filter(symbol -> symbol.name().equals("amounts")).findFirst().orElseThrow().type());
        assertEquals("int[]", project.symbols().stream().filter(symbol -> symbol.name().equals("values")).findFirst().orElseThrow().type());
    }

    @Test public void normalizesArrayParticipantsAndDefaultRoleMemberAliases() {
        var document = source("arrays.spp", "domain ledger; Entity Account { int amount; } Role Facts { Account[]; }\n"
                + "atomic calculate run(Facts[], Facts one) { Facts[].Account[].amount += 1; one.accountArray.amount += 1; }");
        assertTrue(document.problems().toString(), document.problems().isEmpty());
        var project = new ProjectAnalysis(List.of(document));
        assertEquals("FactsArray", resolve(project, document, "Facts[].Account", 0).get(0).name());
        assertEquals("accountArray", resolve(project, document, "Account[].amount", 0).get(0).name());
        assertEquals("Account", resolve(project, document, "amount +=", 0).get(0).owner().name());
    }

    @Test public void physicalProfilesRemainReadableWithoutMergingInactiveDeclarations() {
        var common = source("shared.phy", "domain ledger; header Shared { String trace; }");
        var development = source("domain.dev.phy", "domain ledger; header Development { String trace; }");
        var production = source("domain.prod.phy", "domain ledger; header Production { String trace; }");
        var project = new ProjectAnalysis(List.of(common, development, production), "prod", "develop");
        assertTrue(project.symbols().stream().anyMatch(symbol -> symbol.name().equals("Shared")));
        assertTrue(project.symbols().stream().anyMatch(symbol -> symbol.name().equals("Production")));
        assertTrue(project.symbols().stream().noneMatch(symbol -> symbol.name().equals("Development")));
        var inactive = resolve(project, development, "Development", 0).get(0);
        assertFalse(project.active(inactive));
        assertEquals("Development", inactive.name());
    }

    @Test public void explainsImplicitCrudAndNavigatesToItsParticipant() {
        var document = source("implicit.spp", "domain ledger; Entity Account { int amount; }\n"
                + "Scenario serial run(Account) { findAccount; unknownAccount; }\n");
        var project = new ProjectAnalysis(List.of(document));
        var position = document.position(document.text().indexOf("findAccount"));
        assertEquals("Account", project.resolve(document.uri(), position).get(0).name());
        assertTrue(project.implicitDescription(document.uri(), position).contains("built-in (implicit) service"));
        assertEquals("", project.implicitDescription(document.uri(), document.position(document.text().indexOf("unknownAccount"))));
    }

    @Test public void implicitCrudRespectsDomainsAmbiguityAndExplicitOverrides() {
        var model = source("crud-model.spp", "domain shop; Entity Account { int amount; } Role Buyer; Reference Person from customer.Person;");
        var other = source("customer.spp", "domain customer; Entity Person; Entity Missing;");
        var calls = source("crud.spp", "domain shop; Scenario serial run(Account[] accounts, Buyer, Person) { "
                + "findAccount(accounts); insertAccount(accounts); updateAccount(accounts); deleteAccount(accounts); "
                + "findBuyer; updatePerson; findMissing; findResultInfo; unknownAccount; }");
        var app = source("crud.app", "domain shop; scope { local { findAccount; updatePerson; } }");
        var project = new ProjectAnalysis(List.of(model, other, calls, app));
        for (String operation : List.of("find", "insert", "update", "delete")) {
            var targets = resolve(project, calls, operation + "Account", 0);
            assertEquals(1, targets.size());
            assertEquals(model.uri(), targets.get(0).document().uri());
            assertEquals("Account", targets.get(0).name());
        }
        assertEquals("Buyer", resolve(project, calls, "findBuyer", 0).get(0).name());
        assertEquals("Person", resolve(project, calls, "updatePerson", 0).get(0).name());
        assertEquals("shop", resolve(project, calls, "updatePerson", 0).get(0).domain());
        assertTrue(resolve(project, calls, "findMissing", 0).isEmpty());
        assertTrue(resolve(project, calls, "findResultInfo", 0).isEmpty());
        assertTrue(resolve(project, calls, "unknownAccount", 0).isEmpty());
        assertEquals("Account", resolve(project, app, "findAccount", 0).get(0).name());
        assertTrue(project.implicitDescription(app.uri(), app.position(app.text().indexOf("updatePerson"))).contains("built-in"));
        var explicit = source("override.spp", "domain shop; atomic update updateAccount(Account);");
        var overridden = new ProjectAnalysis(List.of(model, calls, explicit));
        assertEquals(SymbolKind.Function, resolve(overridden, calls, "updateAccount", 0).get(0).kind());
        assertEquals("", overridden.implicitDescription(calls.uri(), calls.position(calls.text().indexOf("updateAccount"))));
        var duplicate = source("duplicate.spp", "domain shop; Entity Account;");
        var ambiguous = new ProjectAnalysis(List.of(model, calls, duplicate));
        assertTrue(resolve(ambiguous, calls, "findAccount", 0).isEmpty());
        assertEquals("", ambiguous.implicitDescription(calls.uri(), calls.position(calls.text().indexOf("findAccount"))));
    }

    @Test public void serviceLookupSharesCompilerCanonicalNamingWithoutRenamingSourceSymbols() {
        var document = source("case.spp", "domain ledger; Role Facts; atomic calculate CalculateTotal(Facts);\n"
                + "Scenario serial preview(Facts) { calculateTotal; }");
        var app = source("case.app", "domain ledger; scope { local { calculateTotal; } }");
        var project = new ProjectAnalysis(List.of(document, app));
        assertEquals("CalculateTotal", resolve(project, document, "calculateTotal;", 0).get(0).name());
        assertEquals("CalculateTotal", resolve(project, app, "calculateTotal;", 0).get(0).name());
    }

    @Test public void quantumKeywordNeverStripsAnIdentifierPrefix() {
        var document = source("quantum.spp", "domain ledger; Entity quantumParticle { int amount; }\n"
                + "Role Facts { quantumParticle particle; quantum decimal(18,2) total; }\n"
                + "atomic calculate run(Facts, quantumParticle) { Facts.particle.amount += 1; quantumParticle.amount += 1; }\n"
                + "quantum calculate observe(Facts); Scenario parallel preview(Facts) { run; }");
        assertTrue(document.problems().toString(), document.problems().isEmpty());
        var project = new ProjectAnalysis(List.of(document));
        assertEquals("quantumParticle", resolve(project, document, "amount +=", 0).get(0).owner().name());
        assertTrue(project.symbols().stream().anyMatch(symbol -> symbol.name().equals("quantumParticle")
                && symbol.kind() == SymbolKind.Variable && symbol.implicit()));
        assertEquals("decimal", DocumentAnalysis.baseType(project.symbols().stream().filter(symbol -> symbol.name().equals("total"))
                .findFirst().orElseThrow().type()));
        assertTrue(project.symbols().stream().anyMatch(symbol -> symbol.name().equals("observe") && symbol.detail().startsWith("quantum calculate")));
        assertTrue(project.symbols().stream().anyMatch(symbol -> symbol.name().equals("preview") && symbol.detail().startsWith("Scenario parallel")));
    }

    @Test public void externalJavaNamesRemainOpaqueEvenWhenEverySegmentMatchesBusinessSymbols() {
        var model = source("opaque.spp", "domain ledger; Entity Handler { int value; } Entity Example { Handler Handler; }\n"
                + "Entity com { Example example; } Role Facts; atomic calculate twice(Facts);");
        var binding = source("opaque.app", "domain ledger; bind twice to com.example.Handler;");
        var physical = source("opaque.phy", "domain ledger; phy { numeric decimal as double using com.example.Handler; }");
        var project = new ProjectAnalysis(List.of(model, binding, physical));
        for (var document : List.of(binding, physical)) {
            int start = document.text().indexOf("com.example.Handler");
            assertTrue(project.resolve(document.uri(), document.position(start)).isEmpty());
            assertTrue(project.resolve(document.uri(), document.position(start + "com.example.".length())).isEmpty());
        }
        assertEquals("twice", resolve(project, binding, "twice", 0).get(0).name());
    }

    @Test public void contextualCommitScopeNameCanReferenceADeclaredService() {
        var model = source("commit.spp", "domain ledger; Role Facts; atomic update commit(Facts);");
        var app = source("commit.app", "domain ledger; scope { local { commit; } }");
        var project = new ProjectAnalysis(List.of(model, app));
        assertEquals("commit", resolve(project, app, "commit;", 0).get(0).name());
    }

    private static List<SourceSymbol> resolve(ProjectAnalysis project, SourceDocument document, String fragment, int offset) {
        int index = document.text().indexOf(fragment);
        assertTrue(fragment, index >= 0);
        return project.resolve(document.uri(), document.position(index + offset));
    }

    private static SourceDocument source(String filename, String text) {
        return new SourceDocument("file:///project/" + filename, 1, text);
    }
}
