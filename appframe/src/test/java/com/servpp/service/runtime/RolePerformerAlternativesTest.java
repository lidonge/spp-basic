package com.servpp.service.runtime;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RolePerformerAlternativesTest {
    public static class A { }
    public static class B { }
    public static class C { }
    public static class D { }
    public static class R1 { }
    public static class R2 { }
    public static class R {
        private R1 r1 = new R1(); private R2 r2 = new R2();
        public R1 getR1() { return r1; } public void setR1(R1 value) { r1 = value; }
        public R2 getR2() { return r2; } public void setR2(R2 value) { r2 = value; }
    }
    public static class Mapper {
        int calls;
        public void roleToEntity(R role, Map<String,Object> actors) { calls++; }
    }
    private List<MaterializedRolePerformers.Definition> definitions() {
        List<List<Class<?>>> branches = List.of(List.of(R1.class,R2.class,A.class),
                List.of(R1.class,R2.class,B.class),List.of(R1.class,R2.class,C.class,D.class),
                List.of(R1.class,R2.class,A.class,B.class));
        return branches.stream().map(branch -> new MaterializedRolePerformers.Definition(R.class,branch,new Mapper(),List.of(),branches)).toList();
    }
    @Test public void eachAlternativeRetainsCommonMembersWithoutCreatingOtherAlternatives() {
        for (int selected = 0; selected < 4; selected++) {
            var definitions = definitions(); R role = new R();
            Object[] selectedActors = switch (selected) {
                case 0 -> new Object[]{new A()}; case 1 -> new Object[]{new B()};
                case 2 -> new Object[]{new C(),new D()}; default -> new Object[]{new A(),new B()};
            };
            RoleIdentityContext.attachPerformers(role,"R",selectedActors);
            MaterializedRolePerformers.bind(definitions,new Object[]{role});
            var actors = RoleIdentityContext.performers(role,"R").orElseThrow();
            assertEquals(new HashSet<>(definitions.get(selected).entities()),actors.stream().map(Object::getClass).collect(java.util.stream.Collectors.toSet()));
            assertSame(role.getR1(),actors.stream().filter(R1.class::isInstance).findFirst().orElseThrow());
            for (int index = 0; index < definitions.size(); index++) {
                assertEquals(index == selected,MaterializedRolePerformers.isActive(definitions.get(index),role));
                assertEquals(index == selected ? 1 : 0,((Mapper)definitions.get(index).mapper()).calls);
            }
        }
    }
    @Test public void partialJointOrUnlistedCombinationCannotSelectAnAlternative() {
        R partial = new R(); RoleIdentityContext.attachPerformers(partial,"R",new C());
        assertThrows(IllegalStateException.class,() -> MaterializedRolePerformers.bind(definitions(),new Object[]{partial}));
        R mixed = new R(); RoleIdentityContext.attachPerformers(mixed,"R",new A(),new D());
        assertThrows(IllegalStateException.class,() -> MaterializedRolePerformers.bind(definitions(),new Object[]{mixed}));
    }
    @Test public void keyOnlyQueryDefersSelectionUntilStoredLinksAreLoaded() {
        R role = new R(); var definitions = definitions();
        MaterializedRolePerformers.bind(definitions,new Object[]{role});
        assertTrue(RoleIdentityContext.performers(role,"R").isEmpty());
        RoleIdentityContext.attachPerformers(role,"R",new B());
        assertFalse(MaterializedRolePerformers.isActive(definitions.get(0),role));
        assertTrue(MaterializedRolePerformers.isActive(definitions.get(1),role));
        ParticipantIdentity.confirm(role,true);
        ParticipantRoleQueries.complete(role,new ParticipantRoleQueries.Step("absent.findA",A.class,new String[0],definitions.get(0)));
        assertTrue(ParticipantIdentity.isIdentified(role));
        R unresolved = new R(); ParticipantIdentity.confirm(unresolved,true);
        var error = assertThrows(IllegalStateException.class,() -> ParticipantRoleQueries.complete(unresolved,
                new ParticipantRoleQueries.Step("absent.findA",A.class,new String[0],definitions.get(0))));
        assertTrue(error.getMessage().contains("ROLE_PERFORMER_VARIANT_INVALID"));
    }
}
