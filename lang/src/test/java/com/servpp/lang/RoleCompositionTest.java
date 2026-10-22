package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoleCompositionTest {
    private SppDomain compile(String source) throws Exception {
        var project = new SppProject(); var file = Files.createTempFile("role-composition", ".spp");
        try {
            Files.writeString(file,"domain sample; " + source);
            new SppCompiler(file.toFile(),project).compile();
            var domain = project.getDomain("sample"); domain.dealEntityToRoleMaps(); return domain;
        } finally { Files.deleteIfExists(file); }
    }
    private SppRoleMapper mapper(SppDomain domain,String name) {
        return domain.getMapsOfClass().values().stream().filter(unit -> unit instanceof SppRoleMapper mapping
                && mapping.getRole().getName().equals(name)).map(unit -> (SppRoleMapper)unit).findFirst().orElseThrow();
    }
    @Test public void declaredMembersProvideIntrinsicCompositionAndTypeNamedAccess() throws Exception {
        var domain = compile("Role Buyer { String name; } Role Goods { String name; } "
                + "Role CartLine { Buyer; Goods; int quantity; } "
                + "atomic calculate prepare(CartLine) { CartLine.quantity = 2; CartLine.Goods.name = CartLine.Buyer.name; }");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        var role = (SppClass)domain.getSppClass("CartLine");
        assertEquals(3,role.getSppFieldList().size());
        assertSame(role.getField("goods"),role.getField("Goods"));
        assertEquals(Set.of("Buyer","Goods"),new HashSet<>(mapper(domain,"CartLine").getEntities().stream().map(SppClass::getName).toList()));
        assertTrue(mapper(domain,"CartLine").getPhysicalEntityToRole().contains("role.setGoods"));
    }
    @Test public void explicitPerformerExtendsRatherThanReplacesIntrinsicRoles() throws Exception {
        var domain = compile("Role Buyer { String name; } Role Goods { String sku; } Entity Extra { String code; } "
                + "Role CartLine { Buyer; Goods; int quantity; } take Extra as CartLine;");
        var mapping = mapper(domain,"CartLine");
        assertEquals(Set.of("Extra","Buyer","Goods"),new HashSet<>(mapping.getEntities().stream().map(SppClass::getName).toList()));
        assertNull(mapping.getRole().getField("code"));
        assertNull(mapping.getRole().getField("Extra"));
        assertEquals(3,mapping.getRole().getSppFieldList().size());
    }
    @Test public void identityOnlyRoleCanBePlayedBySeveralEntities() throws Exception {
        var domain = compile("Entity Product { String code; } Entity Package { String code; } Role Goods; take Product, Package as Goods;");
        assertTrue(domain.prepareRoleCompositions().isEmpty());
        assertEquals(2,mapper(domain,"Goods").getEntities().size());
        assertTrue(((SppClass)domain.getSppClass("Goods")).getSppFieldList().isEmpty());
    }
    @Test public void separateTakeClausesRemainAlternativesAndEachIncludesIntrinsicMembers() throws Exception {
        var domain = compile("Role R1; Role R2; Entity A {} Entity B {} Entity C {} Entity D {} "
                + "Role R { R1; R2; } take A as R; take B as R; take C, D as R;");
        var branches = domain.getMapsOfClass().values().stream().filter(unit -> unit instanceof SppRoleMapper m
                && m.getRole().getName().equals("R")).map(unit -> (SppRoleMapper)unit).toList();
        assertEquals(3,branches.size());
        var combinations = branches.stream().map(m -> m.getEntities().stream().map(SppClass::getName)
                .collect(java.util.stream.Collectors.toSet())).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(Set.of("R1","R2","A"),Set.of("R1","R2","B"),Set.of("R1","R2","C","D")),combinations);
        domain.prepareRoleCompositions();
        assertEquals(3,branches.get(0).getAlternatives().size());
    }
    @Test public void emptyRoleSupportsSeparateAndJointAlternatives() throws Exception {
        var domain = compile("Entity A {} Entity B {} Entity C {} Entity D {} Role R; take A as R; take B as R; take C, D as R;");
        var sets = domain.getMapsOfClass().values().stream().filter(unit -> unit instanceof SppRoleMapper m && m.getRole().getName().equals("R"))
                .map(unit -> ((SppRoleMapper)unit).getEntities().stream().map(SppClass::getName).collect(java.util.stream.Collectors.toSet()))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(Set.of("A"),Set.of("B"),Set.of("C","D")),sets);
    }
    @Test public void mappingCannotInventADeclaredRoleMember() throws Exception {
        var domain = compile("Entity Product { String code; } Role Goods { int quantity; } take Product as Goods { Product product = Product; }");
        assertTrue(domain.validateAppDeclarations().stream().anyMatch(error -> error.contains("requires a declared role member")));
        assertNull(((SppClass)domain.getSppClass("Goods")).getField("product"));
    }
    @Test public void ambiguousCompositionRequiresExplicitMemberMappings() throws Exception {
        var domain = compile("Role Buyer { String name; } Role Deal { Buyer first; Buyer second; } take Buyer as Deal;");
        assertTrue(domain.prepareRoleCompositions().toString(),domain.prepareRoleCompositions().stream().anyMatch(error -> error.contains("Ambiguous performer")));
    }
    @Test public void explicitlyNamedIntrinsicMembersDoNotNeedAnAmbiguousTypeBinding() throws Exception {
        var domain = compile("Role Buyer { String name; } Role Deal { Buyer first; Buyer second; } "
                + "atomic calculate prepare(Deal) { Deal.first.name = Deal.second.name; }");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        assertEquals(List.of("Buyer_first","Buyer_second"),domain.roleStoragePerformers((SppClass)domain.getSppClass("Deal")).stream().map(SppDomain.RoleStoragePerformer::slot).toList());
    }
    @Test public void unnamedCollectionsKeepTheirDimensionsAndDoNotAliasScalarMembers() throws Exception {
        var domain = compile("Role Goods { String sku; } Role Holder { Goods; Goods[]; }");
        var holder = (SppClass)domain.getSppClass("Holder");
        assertEquals(0,holder.getField("Goods").getArrayDimension());
        assertEquals(1,holder.getField("GoodsArray").getArrayDimension());
        assertNotSame(holder.getField("Goods"),holder.getField("GoodsArray"));
        assertSame(holder.getField("goodsArray"),holder.getField("GoodsArray"));
    }
    @Test public void reResolvedParticipantTypesDoNotDuplicatePerformerSlots() throws Exception {
        var domain = compile("Role Buyer { String name; } Role Cart { Buyer; }");
        var cart = (SppClass)domain.getSppClass("Cart");
        var resolvedAgain = new SppClass("Buyer"); resolvedAgain.setType(com.servpp.lang.general.IConstance.CompilationUnitType.role);
        cart.getField("Buyer").setType(resolvedAgain);
        assertTrue(domain.prepareRoleCompositions().isEmpty());
        assertEquals(1,mapper(domain,"Cart").getEntities().size());
        var players = domain.roleStoragePerformers(cart);
        assertEquals(1,players.size()); assertSame(domain.getSppClass("Buyer"),players.get(0).type());
    }
    @Test public void appCanChooseIndependentIdentityAndSeparateRelationshipStorage() throws Exception {
        var domain = compile("Role Buyer { String number; } Role Line { Buyer; int quantity; }");
        var project = new SppProject(); project.getSppDomainMap().put("sample",domain);
        var app = Files.createTempFile("role-storage", ".app");
        try {
            Files.writeString(app,"domain sample; tables roles { table LINES(Line) { performers in LINE_PLAYERS; } }");
            new AppCompiler(app.toFile(),project).compile();
            assertTrue(domain.getAppParseErrors().toString(),domain.getAppParseErrors().isEmpty());
            var table = domain.getRuleBlock().getAppTables().get(0).getAppTableList().get(0);
            assertTrue(table.isIndependentIdentity()); assertEquals("roleId",table.getAppColumns().get(0).getName());
            assertEquals("LINE_PLAYERS",table.getPerformerTable());
            assertNull(((SppClass)domain.getSppClass("Line")).getField("roleId"));
        } finally { Files.deleteIfExists(app); }
    }
}
