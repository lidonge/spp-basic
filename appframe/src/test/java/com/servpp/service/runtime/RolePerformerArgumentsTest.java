package com.servpp.service.runtime;

import com.servpp.service.Service;
import com.servpp.service.repository.RepositoryParticipantSnapshots;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RolePerformerArgumentsTest {
    public static class Person {
        private String email;
        public String getEmail() { return email; }
        public void setEmail(String value) { email=value; }
    }
    public static class Owner {
        public static class Holder {
            private String email;
            private boolean active;
            public String getEmail() { return email; }
            public void setEmail(String value) { email=value; }
            public boolean isActive() { return active; }
            public void setActive(boolean value) { active=value; }
        }
    }
    public static class Client {
        public static class Holder {
            private String email;
            private boolean active;
            public String getEmail() { return email; }
            public void setEmail(String value) { email=value; }
            public boolean isActive() { return active; }
            public void setActive(boolean value) { active=value; }
        }
    }
    public static class Mapping {
        public void roleToEntity(Owner.Holder role,Map<String,Object> actors) { ((Person)actors.get("Person")).setEmail(role.getEmail()); }
        public void entityToRole(Map<String,Object> actors,Owner.Holder role) { role.setEmail(((Person)actors.get("Person")).getEmail()); }
    }
    public interface Insert extends Service { void insert(Person person); }
    public interface Save extends Service { void save(Owner.Holder holder); }
    public static class Writer implements Insert {
        String identity;
        public String getName() { return "owner.insert"; }
        public Object execute(Object... args) { insert((Person)args[0]);return null; }
        public void insert(Person person) {
            identity=RepositoryParticipantSnapshots.identityOrCreate(person,"Person");
            person.setEmail("stored@example.test");
        }
    }
    public static class Saver implements Save {
        Object seen;
        public String getName() { return "owner.save"; }
        public Object execute(Object... args) { save((Owner.Holder)args[0]);return null; }
        public void save(Owner.Holder holder) { seen=RoleIdentityContext.performers(holder,"Holder").orElseThrow().get(0); }
    }
    private RoleBoundArguments.Adapter adapter() {
        return new RoleBoundArguments.Adapter(Person.class,new Mapping(),"Person",new String[]{"email"},new String[]{},false,true);
    }
    @Test public void reverseCallKeepsTheActualPerformerAcrossSubsequentLocalDomainCalls() {
        var container=DefaultServiceContainer.getInstance();container.clear();
        var writer=new Writer();var saver=new Saver();
        container.registerService("owner.insert",writer);container.registerService("owner.save",saver);
        try { RepositoryParticipantSnapshots.execute(()->LocalDomainServices.executeScope(()->{
            var holder=new Client.Holder();holder.setEmail("input@example.test");holder.setActive(true);
            Object actor=RolePerformerArguments.performer(holder,adapter());
            RoleBoundArguments.invoke("owner.insert",new Object[]{holder},new Class<?>[]{Client.Holder.class},new RoleBoundArguments.Adapter[]{adapter()},false);
            assertSame(actor,RoleIdentityContext.performers(holder,"Holder").orElseThrow().get(0));
            assertEquals(writer.identity,RepositoryParticipantSnapshots.objectIdentity(actor,"Person").orElseThrow());
            assertEquals("stored@example.test",holder.getEmail());assertTrue(holder.isActive());
            LocalDomainServices.invoke("owner.save",new Object[]{holder},new Class<?>[]{Client.Holder.class},
                    new String[][]{{"email","active"}},new String[][]{{}},new boolean[]{false},false);
            assertSame(actor,saver.seen);
            var unrelated=new Client.Holder();unrelated.setEmail("stored@example.test");
            assertNotSame(actor,RolePerformerArguments.performer(unrelated,adapter()));
        })); } finally {container.clear();}
    }
    @Test public void ambiguousBoundPerformersAreRejectedRatherThanSelectedByFacts() {
        var holder=new Owner.Holder();
        RoleIdentityContext.attachPerformers(holder,"Holder",new Person(),new Person());
        assertThrows(IllegalStateException.class,()->RolePerformerArguments.performer(holder,adapter()));
    }
    @Test public void queryHydrationRetainsStoredPerformerIdentityAndAssignmentCopiesIt() {
        RepositoryParticipantSnapshots.execute(()->{
            var loaded=new Owner.Holder();var actor=new Person();actor.setEmail("stored@example.test");
            RepositoryParticipantSnapshots.attachIdentity(actor,"Person","private-person");
            RoleIdentityContext.attachPerformers(loaded,"Holder",actor);
            var input=new Owner.Holder();ParticipantFacts.hydrate(input,loaded);
            assertSame(actor,RoleIdentityContext.performers(input,"Holder").orElseThrow().get(0));
            var assigned=new Owner.Holder();ParticipantFacts.assign(assigned,input);
            Object copy=RoleIdentityContext.performers(assigned,"Holder").orElseThrow().get(0);
            assertNotSame(actor,copy);
            assertEquals("private-person",RepositoryParticipantSnapshots.objectIdentity(copy,"Person").orElseThrow());
        });
    }
}
