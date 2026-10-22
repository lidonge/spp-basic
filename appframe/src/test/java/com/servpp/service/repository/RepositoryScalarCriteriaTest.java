package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.*;
import com.servpp.service.transaction.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Contact lookup must preserve an account's private identity and honor the actual predicate. */
public class RepositoryScalarCriteriaTest {
    public static class Account { public String email; public String name; }
    public interface Lookup extends Service { void lookup(Account account); }
    public static class Query implements Lookup {
        public String getName(){return "lookup";}
        public void lookup(Account account) { }
        public Object execute(Object... arguments){lookup((Account)arguments[0]);return null;}
    }
    private final JdbcDataSource source=new JdbcDataSource();
    private final ObjectMapper json=new ObjectMapper();
    private JdbcObjectRepository repository()throws Exception {
        source.setURL("jdbc:h2:mem:scalar_criteria_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var p=new Properties();p.setProperty("tables","ACCOUNTS");p.setProperty("table.ACCOUNTS.category","roles");p.setProperty("table.ACCOUNTS.businessType","Account");
        p.setProperty("table.ACCOUNTS.primaryKeys","accountRowId");p.setProperty("table.ACCOUNTS.columns","accountRowId,email,name");
        p.setProperty("table.ACCOUNTS.column.accountRowId.type","VARCHAR");p.setProperty("table.ACCOUNTS.column.accountRowId.objectBacked","false");
        p.setProperty("table.ACCOUNTS.column.email.type","VARCHAR");p.setProperty("table.ACCOUNTS.column.name.type","VARCHAR");
        p.setProperty("table.ACCOUNTS.roleStorage.enabled","true");p.setProperty("table.ACCOUNTS.roleStorage.independent","true");
        var catalog=AppDdlCatalog.fromProperties(p);new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        return new JdbcObjectRepository(new JdbcObjectSnapshotStore(source,value->json.valueToTree(value).toString()),json).useAppDdlCatalog(catalog);
    }
    private void transaction(Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.LOCAL,()->{try{JdbcTransactionScope.execute(source,c->{action.run();return null;});}catch(java.sql.SQLException failure){throw new IllegalStateException(failure);}});
    }
    private Account insert(JdbcObjectRepository repository,String id,String email,String name) {
        var value=new Account();value.email=email;value.name=name;transaction(()->repository.insert("Account",id,value));return value;
    }
    private Lookup lookup(JdbcObjectRepository repository) {
        var rule=new RepositoryHydratingServiceProxy.HydrationRule("lookup",0,"Account","email","@participant:0").asOptional();
        return (Lookup)RepositoryHydratingServiceProxy.decorate(new Query(),repository,List.of(rule));
    }
    @Test public void scalarPredicateCannotAccidentallySelectAnotherRowsPrimaryKey()throws Exception {
        var repository=repository();String privateId=UUID.randomUUID().toString();
        insert(repository,privateId,"other@example.test","Unrelated");String expected=UUID.randomUUID().toString();insert(repository,expected,privateId,"Selected");
        var selection=new Account();selection.email=privateId;lookup(repository).lookup(selection);
        assertEquals("Selected",selection.name);assertEquals(expected,RepositoryParticipantSnapshots.objectIdentity(selection,"Account").orElseThrow());
    }
    @Test public void missingPredicateDoesNotBecomeAHitBecauseItsValueEqualsAStoredIdentity()throws Exception {
        var repository=repository();String privateId=UUID.randomUUID().toString();insert(repository,privateId,"other@example.test","Unrelated");
        var selection=new Account();selection.email=privateId;selection.name="Preserved input";lookup(repository).lookup(selection);
        assertFalse(ParticipantIdentity.isIdentified(selection));assertEquals("Preserved input",selection.name);assertEquals(privateId,selection.email);
    }
    @Test public void contactQueryPreservesTheRealPrivateIdentityPlatformFactsAndOptimisticUpdate()throws Exception {
        var repository=repository();String privateId=UUID.randomUUID().toString();insert(repository,privateId,"member@example.test","Before");
        var selection=new Account();selection.email="member@example.test";lookup(repository).lookup(selection);
        assertEquals(privateId,RepositoryParticipantSnapshots.objectIdentity(selection,"Account").orElseThrow());
        assertEquals(privateId,RepositoryParticipantSnapshots.platformFacts(selection).get("accountRowId"));
        selection.name="After";
        transaction(()->RepositoryWriteSession.prepare(()->repository,List.of(new RepositoryWriteRule(RepositoryWriteRule.Operation.UPDATE,0,"Account",List.of("@storageIdentity"),null)),new Object[]{selection}).persist());
        assertEquals("After",repository.require("Account",privateId,Account.class).name);
        assertTrue(repository.find("Account","member@example.test").isEmpty());
    }
    @Test public void aMissingIndependentParticipantHasOneTransientIdentityBeforeRelatedFactsAreCopied()throws Exception {
        var repository=repository();var selection=new Account();selection.email="new@example.test";selection.name="New";lookup(repository).lookup(selection);
        assertFalse(ParticipantIdentity.isIdentified(selection));String id=RepositoryParticipantSnapshots.objectIdentity(selection,"Account").orElseThrow();
        assertTrue(repository.find("Account",id).isEmpty());Account copied=ParticipantCopies.copy(selection);
        assertEquals(id,RepositoryParticipantSnapshots.objectIdentity(copied,"Account").orElseThrow());
        transaction(()->RepositoryWriteSession.prepare(()->repository,List.of(new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT,0,"Account",List.of("@generatedIdentity"),null)),new Object[]{selection}).persist());
        assertEquals(id,RepositoryParticipantSnapshots.objectIdentity(selection,"Account").orElseThrow());assertEquals("New",repository.require("Account",id,Account.class).name);
        assertEquals(id,RepositoryParticipantSnapshots.objectIdentity(copied,"Account").orElseThrow());
    }
    @Test public void aLaterContactHitReplacesAProvisionalIdentityWithoutKeepingTwoIdentities()throws Exception {
        var repository=repository();var selection=new Account();selection.email="not-yet@example.test";lookup(repository).lookup(selection);
        String provisional=RepositoryParticipantSnapshots.objectIdentity(selection,"Account").orElseThrow();String actual=UUID.randomUUID().toString();insert(repository,actual,"existing@example.test","Existing");
        selection.email="existing@example.test";lookup(repository).lookup(selection);assertTrue(ParticipantIdentity.isIdentified(selection));
        assertNotEquals(provisional,actual);assertEquals(actual,RepositoryParticipantSnapshots.objectIdentity(selection,"Account").orElseThrow());
    }
    @Test public void aMissAfterAnEarlierHitDoesNotReuseThePreviousAccountsIdentity()throws Exception {
        var repository=repository();String previous=UUID.randomUUID().toString();insert(repository,previous,"existing@example.test","Existing");
        var selection=new Account();selection.email="existing@example.test";lookup(repository).lookup(selection);
        selection.email="new@example.test";lookup(repository).lookup(selection);assertFalse(ParticipantIdentity.isIdentified(selection));
        assertNotEquals(previous,RepositoryParticipantSnapshots.objectIdentity(selection,"Account").orElseThrow());
        assertEquals("Existing",repository.require("Account",previous,Account.class).name);
    }

    @Test public void duplicatePredicateStillFailsWhenOneResultHasTheSamePrimaryKeyAsTheCondition()throws Exception {
        var repository=repository();String collision=UUID.randomUUID().toString();insert(repository,collision,collision,"First");insert(repository,UUID.randomUUID().toString(),collision,"Second");
        var selection=new Account();selection.email=collision;
        try{lookup(repository).lookup(selection);fail("ambiguous predicate accepted");}catch(IllegalStateException expected){assertEquals("REPOSITORY_SCALAR_QUERY_AMBIGUOUS",expected.getMessage());}
    }
}
