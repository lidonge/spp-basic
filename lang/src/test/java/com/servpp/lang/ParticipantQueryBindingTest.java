package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Compile-only fixtures; no databases, service starts or business transaction execution. */
public class ParticipantQueryBindingTest {
    private void parse(SppProject project, String source, String suffix) throws Exception {
        Path file = Files.createTempFile("participant-query-binding",suffix);
        try {
            Files.writeString(file,source);
            if (suffix.equals(".spp")) new SppCompiler(file.toFile(),project).compile();
            else new AppCompiler(file.toFile(),project).compile();
        } finally { Files.deleteIfExists(file); }
    }
    private SppProject project(boolean take, boolean arrayInput) throws Exception {
        var project = new SppProject();
        parse(project,"domain provider; Role Offered { String code; } atomic query lookup(Offered selection, Offered[] matches);", ".spp");
        parse(project,"domain client; Role Local { String code; } Reference Offered from provider.Offered { String code; } "
                + (take ? "take Local as Offered { mapsame; } " : "")
                + "atomic query search(Local" + (arrayInput ? "[]" : "") + " selection, Local[] matches);", ".spp");
        parse(project,"domain provider; tables roles { table OFFERED(Offered) { primarykey(Offered.code); } } "
                + "scope { local { repository(argument(0,Offered.code), matches) using records lookup; } } phy { repository records { adapter jdbcObjectRepository; config default = true; config datasourceUrl = \"jdbc:h2:mem:binding\"; config driverClassName = \"org.h2.Driver\"; } }", ".app");
        parse(project,"domain client; bind search to provider.lookup; scope { local {} }", ".app");
        return project;
    }
    @Test public void compactBindingRequiresAndUsesExplicitRole() throws Exception {
        var project = project(true,false);
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(),errors.isEmpty());
        var binding = project.getDomain("client").getRuleBlock().getLocalServiceBindings().get(0);
        assertTrue(binding.isRoleBinding());
        assertNotNull(binding.getParticipantRoles().get(0).mapper());
        assertEquals(List.of("code"),binding.getParticipantIdentities().get(1));
        assertFalse(binding.getExistingCollections().contains(1));
    }
    @Test public void associationQueryUsesIndependentRoleIdentityWithoutExposingItsKey() throws Exception {
        var project = new SppProject();
        parse(project,"domain accounts; Role Account { String email; } Contract Agreement { String receipt; Account holder; } "
                + "atomic query listAgreements(key Account account, Agreement[] agreements);", ".spp");
        parse(project,"domain accounts; tables roles { table ACCOUNTS(Account) { primarykey String accountRowId; identity independent; index contact(Account.email); } } "
                + "tables contracts { table AGREEMENTS(Agreement) { primarykey(Agreement.receipt); foreignkey(Agreement.holder) references Account; } } "
                + "scope { local {} } phy { repository records { adapter jdbcObjectRepository; config default = true; config datasourceUrl = \"jdbc:h2:mem:opaque-association\"; config driverClassName = \"org.h2.Driver\"; } }", ".app");
        var domain=project.getDomain("accounts");var errors=domain.validateAppDeclarations();assertTrue(errors.toString(),errors.isEmpty());
        var service=(SppService)domain.getSppClass("ListAgreements");var rule=service.getScopeItem().getRepositoryHydrationRules().get(0);
        assertEquals("@storageIdentity",rule.firstKey().getObjectIdPath());
        assertEquals("Agreement.holder.accountRowId",rule.firstKey().repositoryObjectPath());
        assertNull(((SppClass)domain.getSppClass("Account")).getField("accountRowId"));
    }
    @Test public void SameFieldsDoNotSubstituteForTake() throws Exception {
        var errors = DomainDeploymentValidator.validate(project(false,false));
        assertTrue(errors.toString(),errors.stream().anyMatch(error -> error.contains("must explicitly take")));
    }
    @Test public void CollectionDimensionMustMatchProviderRole() throws Exception {
        var errors = DomainDeploymentValidator.validate(project(true,true));
        assertTrue(errors.toString(),errors.stream().anyMatch(error -> error.contains("matching collection dimensions")));
    }

    @Test public void crossDomainCalculationCanCreateMembersOfAnEntryCollection() throws Exception {
        var project = new SppProject();
        parse(project, "domain provider; Role Item { String code; } atomic calculate expand(Item[]) { "
                + "Item[] = new Item[] {new Item()}; for (Item item : Item[]) { item.code = \"A\"; } }", ".spp");
        parse(project, "domain client; Reference Item from provider.Item { String code; } "
                + "atomic calculate expand(Item[]);", ".spp");
        parse(project, "domain provider; tables roles { table ITEMS(Item) { primarykey(Item.code); } } "
                + "scope { local {} } phy { repository records { adapter jdbcObjectRepository; "
                + "config default = true; config datasourceUrl = \"jdbc:h2:mem:calculation\"; "
                + "config driverClassName = \"org.h2.Driver\"; } }", ".app");
        parse(project, "domain client; bind expand to provider.expand; scope { local {} }", ".app");
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(), errors.isEmpty());
        var binding = project.getDomain("client").getRuleBlock().getLocalServiceBindings().get(0);
        assertEquals(List.of("code"), binding.getParticipantIdentities().get(0));
        assertFalse(binding.getExistingCollections().contains(0));
    }

    @Test public void referencedCollectionsCanUseNestedPrivateAccountIdentities() throws Exception {
        var project=new SppProject();
        parse(project,"domain identities; Role Account { String email; }", ".spp");
        parse(project,"domain teams; Reference Account from identities.Account { String email; } "
                + "Role Member { Account account; String team; } atomic query listMembers(key Account, Member[]);", ".spp");
        parse(project,"domain cleanup; Reference Account from identities.Account { String email; } "
                + "Reference Member from teams.Member { Account account; String team; } atomic query listMembers(key Account, Member[]);", ".spp");
        String repository=" phy { repository records { adapter jdbcObjectRepository; config default = true; config datasourceUrl = \"jdbc:h2:mem:nested-private-binding\"; config driverClassName = \"org.h2.Driver\"; } }";
        parse(project,"domain identities; tables roles { table ACCOUNTS(Account) { primarykey String accountRowId; identity independent; } } scope { local {} }"+repository,".app");
        parse(project,"domain teams; tables roles { table MEMBERS(Member) { primarykey(Member.account, Member.team); foreignkey(Member.account) references Account; } } scope { local {} }"+repository,".app");
        parse(project,"domain cleanup; bind listMembers to teams.listMembers; scope { local {} }", ".app");
        var errors=DomainDeploymentValidator.validate(project);assertTrue(errors.toString(),errors.isEmpty());
        var binding=project.getDomain("cleanup").getRuleBlock().getLocalServiceBindings().get(0);
        assertEquals(List.of("account.@storageIdentity","team"),binding.getParticipantIdentities().get(1));
        assertNull(((SppClass)project.getDomain("cleanup").getSppClass("Account")).getField("accountRowId"));
    }

    @Test public void foreignParticipantKeyUsesItsOwningTableWithoutDuplicatingStorage() throws Exception {
        var project = new SppProject();
        parse(project,"domain accounts; Role Account { String email; }", ".spp");
        parse(project,"domain membership; Reference Account from accounts.Account { String email; } "
                + "Contract Membership { Account holder; } atomic query listMemberships(key Account, Membership[]); "
                + "Scenario serial readMembership(Membership) { findMembership; }", ".spp");
        parse(project,"domain accounts; tables roles { table ACCOUNTS(Account) { primarykey(Account.email); } } "
                + "scope { local {} } phy { repository records { adapter jdbcObjectRepository; config default = true; "
                + "config datasourceUrl = \"jdbc:h2:mem:foreign-key\"; config driverClassName = \"org.h2.Driver\"; } }", ".app");
        parse(project,"domain membership; tables contracts { table MEMBERSHIPS(Membership) { primarykey(Membership.holder); "
                + "foreignkey(Membership.holder) references Account; } } scope { local {} } "
                + "phy { repository records { adapter jdbcObjectRepository; config default = true; "
                + "config datasourceUrl = \"jdbc:h2:mem:foreign-key\"; config driverClassName = \"org.h2.Driver\"; } }", ".app");
        var errors=DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(),errors.isEmpty());
        var domain=project.getDomain("membership");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        assertEquals("ACCOUNTS",domain.resolveAppTable("Account").getName());
        assertEquals(1,domain.getRuleBlock().getAppTables().stream().mapToInt(group->group.getAppTableList().size()).sum());
    }

    @Test public void explicitCheckBindingReusesTheDeclaredProvider() throws Exception {
        var project=new SppProject();
        parse(project,"domain accounts; Role Account { boolean active; } atomic check checkActive(Account) { require(Account.active, \"INACTIVE\"); }", ".spp");
        parse(project,"domain teams; Reference Account from accounts.Account { boolean active; } atomic check checkActive(Account); "
                + "Scenario serial verify(Account) { checkActive; }", ".spp");
        parse(project,"domain accounts; scope { local {} }", ".app");
        parse(project,"domain teams; bind checkActive to accounts.checkActive; scope { local {} }", ".app");
        var errors=DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(),errors.isEmpty());
        var service=(SppService)project.getDomain("teams").getSppClass("CheckActive");
        assertTrue(service.getLocalServiceBindingStatement().contains("accounts.checkActive"));
    }

    @Test(timeout=3000) public void cyclicDelegationIsRejectedWithoutHangingRouteValidation() throws Exception {
        var project=new SppProject();
        parse(project,"domain left; Role Value { String text; } atomic calculate loop(Value); Scenario serial run(Value) { loop; }", ".spp");
        parse(project,"domain right; Reference Value from left.Value { String text; } atomic calculate loop(Value);", ".spp");
        parse(project,"domain left; application sample { deploy left; deploy right; } bind loop to right.loop; scope { local {} }", ".app");
        parse(project,"domain right; bind loop to left.loop; scope { local {} }", ".app");
        var errors=DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(),errors.stream().anyMatch(error->error.contains("Cyclic local service binding")));
    }

    @Test public void referencedContractRetainsItsBusinessAgreementMeaning() throws Exception {
        var project=new SppProject();
        parse(project,"domain accounts; Contract Approval { String receipt; }", ".spp");
        parse(project,"domain consumer; Reference Approval from accounts.Approval { String receipt; } "
                + "Role Value { String code; int amount; } atomic update updateValue(Value); "
                + "Scenario serial apply(Value, Approval) { transaction { updateValue; } }", ".spp");
        parse(project,"domain accounts; scope { local {} }", ".app");
        parse(project,"domain consumer; tables roles { table VALUES_TABLE(Value) { primarykey(Value.code); } } "
                + "scope { local {} } phy { repository records { adapter jdbcObjectRepository; config default = true; "
                + "config datasourceUrl = \"jdbc:h2:mem:reference-contract\"; config driverClassName = \"org.h2.Driver\"; "
                + "config persistenceMode = \"AT\"; config persistenceResource = \"recordsResource\"; } }", ".app");
        var errors=DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(),errors.isEmpty());
        assertTrue(project.getDomain("consumer").validateScenarioContracts().toString(),project.getDomain("consumer").validateScenarioContracts().isEmpty());
    }
}
