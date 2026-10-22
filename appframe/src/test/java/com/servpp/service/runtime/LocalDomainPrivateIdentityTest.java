package com.servpp.service.runtime;

import com.servpp.service.Service;
import com.servpp.service.repository.RepositoryParticipantSnapshots;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Nominal cross-domain views keep private nested identities outside business JSON. */
public class LocalDomainPrivateIdentityTest {
    public static class Consumer {
        public static class Account {
            private String email;
            public String getEmail(){return email;}
            public void setEmail(String value){email=value;}
        }
        public static class Document {
            private Account owner;
            public Account getOwner(){return owner;}
            public void setOwner(Account value){owner=value;}
        }
    }
    public static class Provider {
        public static class Account {
            private String email;
            public String getEmail(){return email;}
            public void setEmail(String value){email=value;}
        }
        public static class Document {
            private Account owner;
            public Account getOwner(){return owner;}
            public void setOwner(Account value){owner=value;}
        }
    }
    public interface Complete extends Service {void complete(Provider.Document document);}
    public interface Read extends Service {void read(List<Provider.Document> documents);}
    private static String identity(Object value){return RepositoryParticipantSnapshots.objectIdentity(value,"Account").orElseThrow();}
    public static class Completer implements Complete {
        String seen;
        boolean replaceOwner;
        String replacementIdentity;
        public String getName(){return "private.complete";}
        public Object execute(Object... args){complete((Provider.Document)args[0]);return null;}
        public void complete(Provider.Document document){
            seen=identity(document.getOwner());assertTrue(ParticipantIdentity.isIdentified(document.getOwner()));
            document.getOwner().setEmail("changed@example.test");
            if (replaceOwner) {
                var next=new Provider.Account();next.setEmail("next@example.test");
                RepositoryParticipantSnapshots.prepareTransientIdentity(next,"Account");replacementIdentity=identity(next);
                ParticipantIdentity.confirm(next,true);document.setOwner(next);
            }
        }
    }
    public static class Reader implements Read {
        String returned;
        public String getName(){return "private.read";}
        @SuppressWarnings("unchecked") public Object execute(Object... args){read((List<Provider.Document>)args[0]);return null;}
        public void read(List<Provider.Document> documents){
            var owner=new Provider.Account();owner.setEmail("returned@example.test");
            RepositoryParticipantSnapshots.prepareTransientIdentity(owner,"Account");returned=identity(owner);ParticipantIdentity.confirm(owner,true);
            var document=new Provider.Document();document.setOwner(owner);documents.add(document);
        }
    }
    @Test public void inputAndOutputNestedIdentitySurviveDifferentDomainClassesWithoutReplacingTheEntryInstance(){
        var container=DefaultServiceContainer.getInstance();container.clear();var service=new Completer();container.registerService("private.complete",service);
        try{ParticipantIdentity.execute(()->{
            var owner=new Consumer.Account();owner.setEmail("before@example.test");RepositoryParticipantSnapshots.prepareTransientIdentity(owner,"Account");ParticipantIdentity.confirm(owner,true);
            String expected=identity(owner);var document=new Consumer.Document();document.setOwner(owner);
            LocalDomainServices.invoke("private.complete",new Object[]{document},new Class<?>[]{Consumer.Document.class},new String[][]{{"owner"}},new String[][]{{}},new boolean[]{false},false);
            assertEquals(expected,service.seen);assertSame(owner,document.getOwner());assertEquals(expected,identity(owner));
            assertEquals("changed@example.test",owner.getEmail());assertTrue(ParticipantIdentity.isIdentified(owner));
        });}finally{container.clear();}
    }
    @Test public void aProviderCanReturnANewOwnerWithoutMergingThePreviousOwnersPrivateIdentity(){
        var container=DefaultServiceContainer.getInstance();container.clear();var service=new Completer();service.replaceOwner=true;container.registerService("private.complete",service);
        try{ParticipantIdentity.execute(()->{
            var owner=new Consumer.Account();owner.setEmail("before@example.test");RepositoryParticipantSnapshots.prepareTransientIdentity(owner,"Account");ParticipantIdentity.confirm(owner,true);
            String previous=identity(owner);var document=new Consumer.Document();document.setOwner(owner);
            LocalDomainServices.invoke("private.complete",new Object[]{document},new Class<?>[]{Consumer.Document.class},new String[][]{{"owner"}},new String[][]{{}},new boolean[]{false},false);
            assertSame(owner,document.getOwner());assertNotEquals(previous,service.replacementIdentity);assertEquals(service.replacementIdentity,identity(owner));assertEquals("next@example.test",owner.getEmail());
        });}finally{container.clear();}
    }
    @Test public void returnedCollectionKeepsNestedPrivateIdentityForSubsequentQueries(){
        var container=DefaultServiceContainer.getInstance();container.clear();var service=new Reader();container.registerService("private.read",service);
        try{ParticipantIdentity.execute(()->{
            var documents=new ArrayList<Consumer.Document>();
            LocalDomainServices.invoke("private.read",new Object[]{documents},new Class<?>[]{Consumer.Document.class},new String[][]{{"owner"}},new String[][]{{"owner.@storageIdentity"}},new boolean[]{false},true);
            assertEquals(1,documents.size());assertEquals(service.returned,identity(documents.get(0).getOwner()));
            assertTrue(ParticipantIdentity.isIdentified(documents.get(0).getOwner()));
            assertEquals("returned@example.test",documents.get(0).getOwner().getEmail());
            Consumer.Document first=documents.get(0);String firstId=identity(first.getOwner());
            LocalDomainServices.invoke("private.read",new Object[]{documents},new Class<?>[]{Consumer.Document.class},new String[][]{{"owner"}},new String[][]{{"owner.@storageIdentity"}},new boolean[]{false},true);
            assertEquals(2,documents.size());assertSame(first,documents.get(0));assertEquals(firstId,identity(first.getOwner()));
            assertEquals(service.returned,identity(documents.get(1).getOwner()));assertNotEquals(firstId,service.returned);

        });}finally{container.clear();}
    }
}
