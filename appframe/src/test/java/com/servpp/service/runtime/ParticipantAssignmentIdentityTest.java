package com.servpp.service.runtime;

import com.servpp.service.repository.RepositoryParticipantSnapshots;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantAssignmentIdentityTest {
    public static class Account {
        private String email;
        public String getEmail(){return email;}
        public void setEmail(String value){email=value;}
    }
    public static class Team {
        private Account owner;
        public Account getOwner(){return owner;}
        public void setOwner(Account value){owner=value;}
    }
    private Account account(String email,String identity){
        var value=new Account();value.setEmail(email);
        if(identity!=null)RepositoryParticipantSnapshots.attachIdentity(value,"Account",identity);
        return value;
    }
    @Test public void anExplicitAssignmentChangesTheReferencedIdentityAndKeepsTheTargetInstance(){
        ParticipantIdentity.execute(()->{
            var previous=account("previous@example.test","previous");var next=account("next@example.test","next");
            assertSame(previous,ParticipantFacts.assign(previous,next));
            assertEquals("next",RepositoryParticipantSnapshots.objectIdentity(previous,"Account").orElseThrow());
            assertEquals("next@example.test",previous.getEmail());assertEquals("next",RepositoryParticipantSnapshots.objectIdentity(next,"Account").orElseThrow());
        });
    }
    @Test public void nestedAssignmentDoesNotCarryThePreviousOwnersIdentityIntoANewValue(){
        ParticipantIdentity.execute(()->{
            var previous=new Team();previous.setOwner(account("previous@example.test","previous"));
            var next=new Team();next.setOwner(account("new@example.test",null));
            Account entry=previous.getOwner();ParticipantFacts.assign(previous,next);assertSame(entry,previous.getOwner());
            assertEquals("new@example.test",entry.getEmail());assertTrue(RepositoryParticipantSnapshots.objectIdentity(entry,"Account").isEmpty());
        });
    }
}
