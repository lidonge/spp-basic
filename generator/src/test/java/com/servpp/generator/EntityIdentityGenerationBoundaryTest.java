package com.servpp.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Private entity identity is generated without becoming a business field. */
public class EntityIdentityGenerationBoundaryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path generate(String identity) throws Exception {
        Path source = temporary.newFolder().toPath(), output = temporary.newFolder().toPath();
        Files.writeString(source.resolve("people.spp"), """
                domain people;
                Entity Person { String email; String name; }
                Role AccountHolder { String email; String name; boolean active; }
                take Person as AccountHolder { mapsame; }
                Contract RegistrationAgreement {
                    business("Registration receipt") String receipt;
                    Person applicant;
                }
                atomic query lookupPerson(Person);
                atomic calculate prepareRegistration(Person, RegistrationAgreement) {
                    RegistrationAgreement.applicant = Person;
                }
                Scenario serial refresh(Person) { findPerson; }
                Scenario serial revise(Person, RegistrationAgreement) {
                    prepareRegistration;
                    transaction { updatePerson; insertRegistrationAgreement; }
                }
                Scenario serial register(Person, AccountHolder, RegistrationAgreement) {
                    prepareRegistration;
                    transaction { insertPerson; insertAccountHolder; insertRegistrationAgreement; }
                }
                """);
        Files.writeString(source.resolve("people.app"), """
                domain people;
                transactions { default at resource records; }
                tables entities { table PEOPLE(Person) { %s } }
                tables roles { table ACCOUNTS(AccountHolder) {
                    primarykey String accountRowId; identity independent;
                } }
                tables contracts { table REGISTRATIONS(RegistrationAgreement) {
                    primarykey(RegistrationAgreement.receipt);
                    foreignkey(RegistrationAgreement.applicant) references Person;
                    auto RegistrationAgreement.receipt prefix "REG";
                } }
                scope { local {
                    optional repository(argument(0, Person.email), Person) using records lookupPerson;
                } }
                phy { repository records {
                    adapter jdbcObjectRepository;
                    config default = true;
                    config persistenceMode = LOCAL;
                    config persistenceResource = records;
                    config datasourceUrl = "jdbc:h2:mem:entity_boundary";
                    config driverClassName = "org.h2.Driver";
                } }
                """.formatted(identity));
        GeneratorUtil.execute(source.toFile(), output.resolve("yaml").toFile(),
                output.resolve("java").toFile(), "example");
        return output;
    }

    @Test public void businessKeyControlGeneratesTheActualInsert() throws Exception {
        Path output = generate("primarykey(Person.email);");
        String service = Files.readString(output.resolve("java/example/people/service/SimpleInsertPerson.java"));
        assertTrue(service, service.contains("email"));
    }

    @Test public void opaqueKeyWithoutIdentityGenerationCannotSilentlyGenerateAnInsert() throws Exception {
        Exception failure = assertThrows(Exception.class, () -> generate("primarykey String personRowId;"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("cannot infer APP DDL primary key personRowId"));
    }

    @Test public void independentEntityIdentityGeneratesInsertReadAndUpdate() throws Exception {
        Path output = generate("primarykey String personRowId; identity independent; unique index contact(Person.email);");
        String insert = Files.readString(output.resolve("java/example/people/service/SimpleInsertPerson.java"));
        String find = Files.readString(output.resolve("java/example/people/service/SimpleFindPerson.java"));
        String update = Files.readString(output.resolve("java/example/people/service/SimpleUpdatePerson.java"));
        assertTrue(insert, insert.contains("@generatedIdentity"));
        assertTrue(find, find.contains("@storageIdentity"));
        assertTrue(update, update.contains("@storageIdentity"));
        String schema = Files.readString(output.resolve("yaml/people.spp.yaml"));
        assertFalse(schema, schema.contains("personRowId:"));
        var properties = new java.util.Properties();
        try (var stream = Files.newInputStream(output.resolve("resources/META-INF/spp/app-ddl.properties"))) {
            properties.load(stream);
        }
        var catalog = com.servpp.service.runtime.AppDdlCatalog.fromProperties(properties);
        assertTrue(catalog.roleStorage().get("Person").independent());
        assertTrue(catalog.roleStorage().get("Person").performers().isEmpty());
        assertEquals(java.util.List.of("@storageIdentity"),
                catalog.roleStorage().get("AccountHolder").performers().get(0).keys());
        assertEquals("false", properties.getProperty("table.PEOPLE.column.personRowId.objectBacked"));
        assertEquals("people.findPerson", catalog.tableForBusinessType("Person").orElseThrow().queryService());
        assertEquals(java.util.List.of("contact"), catalog.tableForBusinessType("Person").orElseThrow().uniqueIndexNames());
        String ddl = Files.readString(output.resolve("yaml/sql/ddl.sql"));
        assertTrue(ddl, ddl.contains("create unique index idx_PEOPLE_contact"));
    }

    @Test public void uniqueIndexRequiresAName() {
        Exception failure = assertThrows(Exception.class,
                () -> generate("primarykey(Person.email); unique index(Person.name);"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("unique index requires an explicit name"));
    }
}
