package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.AppDdlCatalog;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.TransactionMode;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

/** Same contact can describe distinct accounts; APP associations must preserve their storage identity. */
public class IndependentRoleAssociationTest {
    public static class Account { public String email; }
    public static class Agreement { public String receipt; public Account holder; }
    public static class Registration { public Account buyer; public int extensions; public Account getBuyer(){return buyer;} public void setBuyer(Account value){buyer=value;} public void setExtensions(int value){extensions=value;} }
    public static class Extension { public Registration trial; public int number; public int months; }
    public interface RegistrationQuery extends com.servpp.service.Service { void find(Registration registration); }
    public static class RegistrationQueryService implements RegistrationQuery {
        public void find(Registration registration) { }
        public Object execute(Object... arguments){find((Registration)arguments[0]);return null;}
        public String getName(){return "findRegistration";}
    }
    public interface Query extends com.servpp.service.Service { void find(Account account,List<Agreement> matches); }
    public static class QueryService implements Query {
        public void find(Account account,List<Agreement> matches) { }
        public Object execute(Object... arguments){find((Account)arguments[0],(List<Agreement>)arguments[1]);return null;}
        public String getName(){return "listAgreements";}
    }
    private String platformKey = "accountRowId";
    private boolean referenceOnly;
    private String foreignKey() { return "holder_"+platformKey.toLowerCase(Locale.ROOT); }
    private final JdbcDataSource source = new JdbcDataSource();
    private final ObjectMapper json = new ObjectMapper();
    private AppDdlCatalog catalog() {
        var p = new Properties();
        p.setProperty("tables", "ACCOUNTS,AGREEMENTS,REGISTRATIONS,EXTENSIONS");
        p.setProperty("table.ACCOUNTS.category", "roles");p.setProperty("table.ACCOUNTS.businessType", "Account");
        p.setProperty("table.ACCOUNTS.primaryKeys", "accountRowId");p.setProperty("table.ACCOUNTS.columns", "accountRowId,email");
        p.setProperty("table.ACCOUNTS.column.accountRowId.type", "VARCHAR");p.setProperty("table.ACCOUNTS.column.accountRowId.objectBacked", "false");
        p.setProperty("table.ACCOUNTS.column.email.type", "VARCHAR");
        p.setProperty("table.ACCOUNTS.roleStorage.enabled", "true");p.setProperty("table.ACCOUNTS.roleStorage.independent", "true");
        p.setProperty("table.AGREEMENTS.category", "contracts");p.setProperty("table.AGREEMENTS.businessType", "Agreement");
        p.setProperty("table.AGREEMENTS.primaryKeys", "receipt");p.setProperty("table.AGREEMENTS.columns", "receipt,holder_accountrowid");
        p.setProperty("table.AGREEMENTS.column.receipt.type", "VARCHAR");p.setProperty("table.AGREEMENTS.column.holder_accountrowid.type", "VARCHAR");
        p.setProperty("table.AGREEMENTS.indexes", "holder_accountrowid");
        p.setProperty("table.REGISTRATIONS.category","contracts");p.setProperty("table.REGISTRATIONS.businessType","Registration");
        p.setProperty("table.REGISTRATIONS.primaryKeys","buyer_accountrowid");p.setProperty("table.REGISTRATIONS.columns","buyer_accountrowid,extensions");
        p.setProperty("table.REGISTRATIONS.column.buyer_accountrowid.type","VARCHAR");p.setProperty("table.REGISTRATIONS.column.extensions.type","INTEGER");
        p.setProperty("table.EXTENSIONS.category","contracts");p.setProperty("table.EXTENSIONS.businessType","Extension");
        p.setProperty("table.EXTENSIONS.primaryKeys","trial_buyer_accountrowid,number");p.setProperty("table.EXTENSIONS.columns","trial_buyer_accountrowid,number,months");
        p.setProperty("table.EXTENSIONS.column.trial_buyer_accountrowid.type","VARCHAR");p.setProperty("table.EXTENSIONS.column.number.type","INTEGER");p.setProperty("table.EXTENSIONS.column.months.type","INTEGER");
        if(referenceOnly)p.setProperty("table.REGISTRATIONS.queryService","findRegistration");
        var physical=new Properties();
        p.forEach((key,value)->physical.setProperty(key.toString().replace("holder_accountrowid",foreignKey()).replace("accountRowId",platformKey),value.toString().replace("holder_accountrowid",foreignKey()).replace("accountRowId",platformKey)));
        return AppDdlCatalog.fromProperties(physical);
    }
    private JdbcObjectRepository repository(AppDdlCatalog catalog) {
        return new JdbcObjectRepository(new JdbcObjectSnapshotStore(source, value -> json.valueToTree(value).toString()),json).useAppDdlCatalog(catalog);
    }
    private void transaction(Runnable work) {
        DeclaredTransactionContext.execute(TransactionMode.AT, () -> {
            try { JdbcTransactionScope.execute(source,c -> {work.run();return null;}); }
            catch(java.sql.SQLException failure){throw new IllegalStateException(failure);}
        });
    }
    private Account insert(JdbcObjectRepository repository) {
        var account = new Account();account.email = "same@example.test";
        transaction(() -> RepositoryWriteSession.prepare(() -> repository,List.of(new RepositoryWriteRule(RepositoryWriteRule.Operation.INSERT,0,"Account",List.of("@generatedIdentity"),null)),new Object[]{account}).persist());
        return account;
    }
    private void write(JdbcObjectRepository repository,RepositoryWriteRule.Operation operation,String type,List<String> paths,Object value) {
        transaction(()->RepositoryWriteSession.prepare(()->repository,List.of(new RepositoryWriteRule(operation,0,type,paths,null)),new Object[]{value}).persist());
    }
    @Test public void ordinaryAndCompositeKeysCanContainNestedPrivateIdentities() throws Exception {
        source.setURL("jdbc:h2:mem:private_crud_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");var catalog=catalog();new JdbcAppDdlSchemaManager(source).ensureTables(catalog);var repository=repository(catalog);
        var first=insert(repository);var second=insert(repository);String firstId=RepositoryParticipantSnapshots.objectIdentity(first,"Account").orElseThrow(),secondId=RepositoryParticipantSnapshots.objectIdentity(second,"Account").orElseThrow();
        var trial=new Registration();trial.buyer=first;var other=new Registration();other.buyer=second;
        var registrationKeys=List.of("buyer.@storageIdentity");
        write(repository,RepositoryWriteRule.Operation.INSERT,"Registration",registrationKeys,trial);write(repository,RepositoryWriteRule.Operation.INSERT,"Registration",registrationKeys,other);
        var loaded=repository.require("Registration",firstId,Registration.class);loaded.extensions=1;
        write(repository,RepositoryWriteRule.Operation.UPDATE,"Registration",registrationKeys,loaded);
        assertEquals(1,repository.require("Registration",firstId,Registration.class).extensions);assertEquals(0,repository.require("Registration",secondId,Registration.class).extensions);
        var readRule=new RepositoryHydratingServiceProxy.HydrationRule("find",0,"Registration","buyer.@storageIdentity","@participant:0",null,true,"APP_DDL_PRIMARY_KEY").asOptional();
        var query=(RegistrationQuery)RepositoryHydratingServiceProxy.decorate(new RegistrationQueryService(),repository,List.of(readRule));
        var selection=new Registration();selection.buyer=first;query.find(selection);assertEquals(1,selection.extensions);assertTrue(com.servpp.service.runtime.ParticipantIdentity.isIdentified(selection));
        var missing=new Registration();missing.buyer=new Account();missing.buyer.email=first.email;query.find(missing);assertFalse(com.servpp.service.runtime.ParticipantIdentity.isIdentified(missing));
        var guarded=new RepositoryWriteRule(RepositoryWriteRule.Operation.UPDATE,0,"Registration",registrationKeys,new RepositoryWriteRule.VersionCheck(0,"extensions","extensions",true));
        transaction(()->RepositoryWriteSession.prepare(()->repository,List.of(guarded),new Object[]{loaded}).persist());
        assertEquals(2,repository.require("Registration",firstId,Registration.class).extensions);
        loaded.buyer=second;
        try{transaction(()->RepositoryWriteSession.prepare(()->repository,List.of(guarded),new Object[]{loaded}).persist());fail();}
        catch(IllegalStateException expected){assertEquals("PERSISTENCE_IDENTITY_CHANGED",expected.getMessage());}
        loaded.buyer=first;assertEquals(0,repository.require("Registration",secondId,Registration.class).extensions);
        var extension=new Extension();extension.trial=loaded;extension.number=1;extension.months=1;
        var extensionKeys=List.of("trial.buyer.@storageIdentity","number");
        write(repository,RepositoryWriteRule.Operation.INSERT,"Extension",extensionKeys,extension);
        var nativeReader = new JdbcAppDdlObjectTableReader(source,catalog);
        assertEquals(List.of(CompositeObjectId.encode(List.of(firstId,"1"))), nativeReader.findObjectIdsBy("Extension","trial.buyer.@storageIdentity",firstId));
        assertTrue(nativeReader.findObjectIdsBy("Extension","trial.buyer.@storageIdentity",secondId).isEmpty());
        assertEquals(List.of(firstId),nativeReader.findObjectIdsBy("Account","@storageIdentity",firstId));
        transaction(() -> {
            assertEquals(1,repository.findByForUpdate("Extension",List.of("trial.buyer.@storageIdentity"),List.of(firstId),Extension.class).size());
            assertTrue(repository.findByForUpdate("Extension",List.of("trial.buyer.@storageIdentity"),List.of(secondId),Extension.class).isEmpty());
            assertEquals(1,repository.findByForUpdate("Extension",List.of("trial.buyer.accountRowId"),List.of(firstId),Extension.class).size());
            assertEquals(1,repository.findByForUpdate("Account",List.of("@storageIdentity"),List.of(firstId),Account.class).size());
        });
        String extensionId=CompositeObjectId.encode(List.of(firstId,"1"));var reopened=repository(catalog);var reread=reopened.require("Extension",extensionId,Extension.class);reread.months=2;
        write(reopened,RepositoryWriteRule.Operation.UPDATE,"Extension",extensionKeys,reread);assertEquals(2,repository.require("Extension",extensionId,Extension.class).months);
        write(reopened,RepositoryWriteRule.Operation.DELETE,"Extension",extensionKeys,reread);write(repository,RepositoryWriteRule.Operation.DELETE,"Registration",registrationKeys,loaded);
        assertTrue(repository.find("Extension",extensionId).isEmpty());assertTrue(repository.find("Registration",firstId).isEmpty());assertTrue(repository.find("Registration",secondId).isPresent());
        var unknown=new Registration();unknown.buyer=new Account();unknown.buyer.email=first.email;
        try{write(repository,RepositoryWriteRule.Operation.INSERT,"Registration",registrationKeys,unknown);fail();}catch(IllegalStateException expected){assertEquals("PERSISTENCE_READ_IDENTITY_REQUIRED",expected.getMessage());}
    }
    @Test public void referenceOnlyContractKeysEncodeNestedPrivateIdentitiesAsMetadata()throws Exception {
        referenceOnly=true;source.setURL("jdbc:h2:mem:private_reference_keys_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");var catalog=catalog();new JdbcAppDdlSchemaManager(source).ensureTables(catalog);var repository=repository(catalog);
        var account=insert(repository);String id=RepositoryParticipantSnapshots.objectIdentity(account,"Account").orElseThrow();
        var registration=new Registration();registration.buyer=account;write(repository,RepositoryWriteRule.Operation.INSERT,"Registration",List.of("buyer.@storageIdentity"),registration);
        assertEquals("buyer.@storageIdentity",ParticipantStorageKeys.path(registration,"buyer_accountrowid",catalog));
        var extension=new Extension();extension.trial=registration;extension.number=1;extension.months=1;
        write(repository,RepositoryWriteRule.Operation.INSERT,"Extension",List.of("trial.buyer.@storageIdentity","number"),extension);
        var stored=json.readTree(repository.getStore().find("Extension",CompositeObjectId.encode(List.of(id,"1"))).orElseThrow().payloadJson());
        assertEquals(id,stored.path("trial").path("buyer").path("$sppStorageIdentity").path("id").asText());
        assertFalse(stored.toString().contains("@storageIdentity"));assertFalse(stored.toString().contains("same@example.test"));
    }

    @Test public void platformKeysContainingUnderscoresAlsoRoundTrip() throws Exception {
        platformKey="account_row_id";nativeAssociationsDistinguishSameContactAndRestorePrivateIdentity();
    }
    @Test public void nativeAssociationsDistinguishSameContactAndRestorePrivateIdentity() throws Exception {
        source.setURL("jdbc:h2:mem:association_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");var catalog=catalog();new JdbcAppDdlSchemaManager(source).ensureTables(catalog);var repository=repository(catalog);
        Account first=insert(repository),second=insert(repository);
        String firstId=RepositoryParticipantSnapshots.objectIdentity(first,"Account").orElseThrow(),secondId=RepositoryParticipantSnapshots.objectIdentity(second,"Account").orElseThrow();assertNotEquals(firstId,secondId);
        var a=new Agreement();a.receipt="A";a.holder=first;var b=new Agreement();b.receipt="B";b.holder=second;
        transaction(()->{repository.insert("Agreement","A",a);repository.insert("Agreement","B",b);});
        try(var c=source.getConnection();var q=c.createStatement();var r=q.executeQuery("SELECT "+foreignKey()+" FROM AGREEMENTS WHERE receipt='A'")){assertTrue(r.next());assertEquals(firstId,r.getString(1));}
        var reopened=repository(catalog);assertEquals(List.of("A"),reopened.findBy("Agreement",foreignKey(),firstId,Agreement.class).stream().map(v->v.receipt).toList());
        assertEquals(List.of("B"),reopened.findBy("Agreement",foreignKey(),secondId,Agreement.class).stream().map(v->v.receipt).toList());
        var rule=new RepositoryHydratingServiceProxy.HydrationRule("find","Agreement",List.of(new RepositoryHydratingServiceProxy.HydrationRule.KeyTarget(0,"Account","@storageIdentity","holder."+platformKey)),"@participant:1",null,true,"SPP_KEY_PARTICIPANT");
        var query=(Query)RepositoryHydratingServiceProxy.decorate(new QueryService(),reopened,List.of(rule));
        var matches=new ArrayList<Agreement>();query.find(first,matches);assertEquals(List.of("A"),matches.stream().map(v->v.receipt).toList());
        var unknown=new Account();unknown.email=first.email;matches.clear();query.find(unknown,matches);assertTrue(matches.isEmpty());
        var nativeValue=new Agreement();assertTrue(new JdbcAppDdlObjectTableReader(source,catalog).hydrate("Agreement","A",nativeValue));
        assertEquals(firstId,RepositoryParticipantSnapshots.objectIdentity(nativeValue.holder,"Account").orElseThrow());
        assertFalse(json.writeValueAsString(nativeValue).contains(firstId));assertFalse(json.writeValueAsString(nativeValue).contains(platformKey));
    }
}
