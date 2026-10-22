package com.servpp.generator;

import java.nio.file.*;
import java.util.Properties;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** A contact lookup and a subsequent identity lookup are different generated queries. */
public class IndependentRoleContactQueryGenerationTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void contactLookupKeepsTheRolePrivateKeyAndItsFollowingBuiltinIdentityQuery()throws Exception {
        Path root=temporary.getRoot().toPath(),source=Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("accounts.spp"),"""
                domain accounts;
                Entity Person { String email; String name; }
                Role AccountHolder { String email; String name; boolean active; }
                take Person as AccountHolder { mapsame; }
                atomic query lookupByEmail(AccountHolder);
                Scenario serial readAccount(AccountHolder) { lookupByEmail; identify(AccountHolder); findAccountHolder; }
                """);
        Files.writeString(source.resolve("accounts.app"),"""
                domain accounts;
                transactions { default at resource records; }
                tables entities { table PERSONS(Person) { primarykey(Person.email); } }
                tables roles { table ACCOUNTS(AccountHolder) {
                    primarykey String accountRowId; identity independent; index contact(AccountHolder.email);
                } }
                scope { local { optional repository(argument(0, AccountHolder.email), AccountHolder) using records lookupByEmail; } }
                phy { repository records { adapter jdbcObjectRepository; config default = true;
                    config persistenceMode = LOCAL; config persistenceResource = records;
                    config datasourceUrl = "jdbc:h2:mem:contact_query"; config driverClassName = "org.h2.Driver";
                } }
                """);
        GeneratorUtil.execute(source.toFile(),root.resolve("yaml").toFile(),root.resolve("java").toFile(),"example");
        var properties=new Properties();try(var input=Files.newInputStream(root.resolve("resources/META-INF/spp/app-ddl.properties"))){properties.load(input);}
        assertEquals("accountRowId",properties.getProperty("table.ACCOUNTS.primaryKeys"));
        assertEquals("accounts.findAccountHolder",properties.getProperty("table.ACCOUNTS.queryService"));
        assertEquals("false",properties.getProperty("table.ACCOUNTS.column.accountRowId.objectBacked"));
        assertEquals("true",properties.getProperty("table.ACCOUNTS.roleStorage.independent"));
        assertEquals("AccountHolder.email",properties.getProperty("table.ACCOUNTS.index.contact.paths"));
        String contact=Files.readString(root.resolve("java/example/accounts/service/SimpleLookupByEmail.java"));
        assertTrue(contact,contact.contains("\"email\", \"@participant:0:\""));assertTrue(contact,contact.contains(".asOptional()"));
        String identity=Files.readString(root.resolve("java/example/accounts/service/SimpleFindAccountHolder.java"));
        assertTrue(identity,identity.contains("@storageIdentity"));assertTrue(identity,identity.contains("APP_DDL_STORAGE_IDENTITY"));
        String schema=Files.readString(root.resolve("yaml/accounts.spp.yaml"));assertFalse(schema,schema.contains("accountRowId:"));
    }
}
