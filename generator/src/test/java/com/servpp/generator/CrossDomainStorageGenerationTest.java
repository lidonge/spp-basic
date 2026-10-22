package com.servpp.generator;

import java.nio.file.*;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

/** Foreign identities stay in their owning domain, with one physical database and no duplicate tables. */
public class CrossDomainStorageGenerationTest {
    @Test public void foreignPrivateKeysRemainAssociationBackedAndDoNotRequireBusinessFields() throws Exception {
        Path root=Files.createTempDirectory("cross-domain-private-keys-"),source=Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("accounts.spp"),"domain accounts; Entity Person { String email; } Role Account { String email; } take Person as Account { mapsame; }");
        Files.writeString(source.resolve("teams.spp"),"domain teams; Reference Account from accounts.Account { String email; } Role Member { Account account; String team; boolean active; } Scenario serial readMember(Member) { findMember; }");
        Files.writeString(source.resolve("support.spp"),"domain support; Reference Account from accounts.Account { String email; } Reference Member from teams.Member { Account account; String team; boolean active; } Contract Case { String receipt; Member member; } Scenario serial readCase(Case) { findCase; }");
        String physical=" phy { repository records { adapter jdbcObjectRepository; config default = true; config datasourceUrl = \"jdbc:h2:mem:foreign-private-keys\"; config driverClassName = \"org.h2.Driver\"; } }";
        Files.writeString(source.resolve("accounts.app"),"domain accounts; tables entities { table PERSONS(Person) { primarykey(Person.email); } } tables roles { table ACCOUNTS(Account) { primarykey String accountRowId; identity independent; } } scope { local {} }"+physical);
        Files.writeString(source.resolve("teams.app"),"domain teams; tables roles { table MEMBERS(Member) { primarykey(Member.account, Member.team); foreignkey(Member.account) references Account; } } scope { local {} }"+physical);
        Files.writeString(source.resolve("support.app"),"domain support; application sample { deploy accounts; deploy teams; deploy support; } tables contracts { table CASES(Case) { primarykey(Case.receipt); foreignkey(Case.member) references Member; } } scope { local {} }"+physical);
        GeneratorUtil.execute(source.toFile(),root.resolve("yaml").toFile(),root.resolve("java").toFile(),"example");
        var teams=new Properties();try(var input=Files.newInputStream(root.resolve("resources/META-INF/spp/domains/teams/app-ddl.properties"))){teams.load(input);}
        assertEquals("account_accountrowid,team",teams.getProperty("table.MEMBERS.primaryKeys"));
        assertEquals("true",teams.getProperty("table.MEMBERS.column.account_accountrowid.objectBacked"));
        var support=new Properties();try(var input=Files.newInputStream(root.resolve("resources/META-INF/spp/domains/support/app-ddl.properties"))){support.load(input);}
        assertEquals("true",support.getProperty("table.CASES.column.member_account_accountrowid.objectBacked"));
        String schema=Files.readString(root.resolve("yaml/teams.spp.yaml"));assertFalse(schema,schema.contains("accountRowId:"));
    }

    @Test public void foreignPrimaryKeysAndCanonicalSpecsSurviveSplitSources() throws Exception {
        Path root=Files.createTempDirectory("cross-domain-storage-"),source=Files.createDirectory(root.resolve("source"));
        for(String domain:new String[]{"accounts","membership"})Files.createDirectory(source.resolve(domain));
        Files.writeString(source.resolve("accounts/model.spp"),"""
                domain accounts;
                Role Account { String email; }
                atomic check checkActive(Account) { require(Account.email != null, "EMAIL_REQUIRED"); }
                """);
        Files.writeString(source.resolve("membership/model.spp"),"""
                domain membership;
                Reference Account from accounts.Account { String email; }
                Contract Membership { Account holder; }
                atomic check checkActive(Account);
                Scenario serial readMembership(Membership) { findMembership; }
                """);
        String physical="phy { repository records { adapter jdbcObjectRepository; config default = true; "
                + "config datasourceUrl = \"jdbc:h2:mem:split-domains\"; config driverClassName = \"org.h2.Driver\"; } }";
        Files.writeString(source.resolve("accounts/storage.app"),"domain accounts; @column(varchar_precision=\"254\") tables roles { table ACCOUNTS(Account) { primarykey(Account.email); } } scope { local {} } "+physical);
        Files.writeString(source.resolve("membership/storage.app"),"domain membership; application sample { deploy accounts; deploy membership; } "
                + "bind checkActive to accounts.checkActive; tables contracts { table MEMBERSHIPS(Membership) { primarykey(Membership.holder); foreignkey(Membership.holder) references Account; } } "
                + "scope { local {} } "+physical);
        GeneratorUtil.execute(source.toFile(),root.resolve("yaml").toFile(),root.resolve("java").toFile(),"example");
        assertTrue(Files.exists(root.resolve("yaml/accounts.spp.yaml")));
        assertTrue(Files.exists(root.resolve("yaml/membership.spp.yaml")));
        var catalogue=new Properties();
        try(var input=Files.newInputStream(root.resolve("resources/META-INF/spp/domains/membership/app-ddl.properties"))){catalogue.load(input);}
        assertEquals("MEMBERSHIPS",catalogue.getProperty("tables"));
        assertEquals("holder_email",catalogue.getProperty("table.MEMBERSHIPS.primaryKeys"));
        String ddl=Files.readString(root.resolve("yaml/sql/membership/ddl.sql"));
        assertTrue(ddl,ddl.contains("ACCOUNTS"));
        assertTrue(ddl,ddl.toLowerCase(java.util.Locale.ROOT).contains("varchar(254)"));
        assertFalse(ddl,ddl.matches("(?is).*create\\s+table\\s+ACCOUNTS\\s*\\(.*"));
        String delegate=Files.readString(root.resolve("java/example/membership/service/SimpleCheckActive.java"));
        assertTrue(delegate,delegate.contains("accounts.checkActive"));
    }
}
