package com.servpp.generator;

import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class RolePerformerCallGenerationTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private Path generate(String mapping, String call)throws Exception {
        Path source=temporary.newFolder().toPath(),output=temporary.newFolder().toPath();
        Files.writeString(source.resolve("people.spp"),"""
                domain people;
                Entity Person { String email; }
                Role Holder { String email; boolean active; }
                %s
                atomic query lookupPerson(Person);
                """.formatted(mapping));
        Files.writeString(source.resolve("enroll.spp"),"""
                domain enroll;
                Reference Person from people.Person { String email; }
                Reference Holder from people.Holder { String email; boolean active; }
                Contract Registration { business("Receipt") String receipt; Holder holder; }
                atomic query lookupRegistration(Registration);
                atomic calculate prepare(Holder, Registration) { Holder.active = true; Registration.holder = Holder; }
                Scenario serial register(Holder, Registration) {
                    prepare;
                    transaction { serial { %s insertHolder; insertRegistration; } }
                }
                """.formatted(call));
        Files.writeString(source.resolve("people.app"),"""
                domain people;
                transactions { default at resource records; }
                tables entities { table PEOPLE(Person) { primarykey String personRowId; identity independent; } }
                tables roles { table HOLDERS(Holder) { primarykey String holderRowId; identity independent; } }
                scope { local { optional repository(argument(0, Person.email), Person) using records lookupPerson; } }
                phy { repository records { adapter jdbcObjectRepository; config default = true;
                    config persistenceMode = LOCAL; config persistenceResource = records;
                    config datasourceUrl = "jdbc:h2:mem:performer"; config driverClassName = "org.h2.Driver";
                } }
                """);
        Files.writeString(source.resolve("enroll.app"),"""
                domain enroll;
                transactions { default at resource records; }
                tables contracts { table REGISTRATIONS(Registration) {
                    primarykey(Registration.receipt); auto Registration.receipt prefix "REG";
                } }
                scope { local { optional repository(argument(0, Registration.receipt), Registration) using records lookupRegistration; } }
                phy { repository records { adapter jdbcObjectRepository; config default = true;
                    config persistenceMode = LOCAL; config persistenceResource = records;
                    config datasourceUrl = "jdbc:h2:mem:performer"; config driverClassName = "org.h2.Driver";
                } }
                """);
        if (mapping.contains("Entity Other")) {
            Path app=source.resolve("people.app");
            Files.writeString(app,Files.readString(app).replace("tables roles", "tables entities { table OTHERS(Other) { primarykey(Other.email); } } tables roles"));
        }
        if (call.equals("insertPerson(Holders);")) {
            Path spp=source.resolve("enroll.spp");
            Files.writeString(spp,Files.readString(spp).replace("Scenario serial register(Holder, Registration)",
                    "Scenario serial register(Holder, Holder[] Holders, Registration)"));
        }
        GeneratorUtil.execute(source.toFile(),output.resolve("yaml").toFile(),output.resolve("java").toFile(),"example");
        return output;
    }
    @Test public void referenceRoleCanExplicitlyInsertItsOwnedPerformer()throws Exception {
        verify(generate("take Person as Holder { mapsame; }", "insertPerson(Holder);"));
    }
    @Test public void implicitCallCanResolveTheUniquePerformer()throws Exception {
        verify(generate("take Person as Holder { mapsame; }", "insertPerson;"));
    }
    @Test public void alternativeTakesCannotBeGuessedByAReverseCall()throws Exception {
        Exception failure=assertThrows(Exception.class,()->generate(
                "Entity Other { String email; } atomic query findOther(Other); take Person as Holder { mapsame; } take Other as Holder { mapsame; }",
                "insertPerson(Holder);"));
        assertTrue(failure.getMessage(),failure.getMessage().contains("requires one unambiguous Person"));
    }
    @Test public void reverseCollectionsRequireExplicitIdentityMatchingSupport()throws Exception {
        Exception failure=assertThrows(Exception.class,()->generate("take Person as Holder { mapsame; }", "insertPerson(Holders);"));
        assertTrue(failure.getMessage(),failure.getMessage().contains("reverse performer calls currently require a scalar role"));
    }
    private void verify(Path output)throws Exception {
        String wrapper=Files.readString(output.resolve("java/example/enroll/service/SimpleInsertPerson.java"));
        assertTrue(wrapper,wrapper.contains("example.people.model.Person.class"));
        assertTrue(wrapper,wrapper.contains("example.people.model.MapperPersonToHolder"));
        assertTrue(wrapper,wrapper.contains("false, true)"));
        assertFalse(Files.readString(output.resolve("yaml/enroll.spp.yaml")).contains("personRowId:"));
    }
}
