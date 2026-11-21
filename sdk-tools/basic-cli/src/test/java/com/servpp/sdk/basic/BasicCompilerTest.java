package com.servpp.sdk.basic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.sdk.CompilationException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BasicCompilerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void onlyEnumsDeclaringOtherGenerateFallback() throws Exception {
        Path source = temporary.newFolder().toPath();
        Files.writeString(source.resolve("routing.spp"), """
                domain routing;
                Enum Route { DIRECT, OTHER("fallback") }
                Enum StrictRoute { DIRECT, INDIRECT }
                Role Facts { Route route; StrictRoute strictRoute; boolean other; int selected; }
                atomic calculate choose(Facts) {
                    Facts.other = Facts.route == Route.OTHER;
                    switch (Facts.route) {
                        case DIRECT: Facts.selected = 1; break;
                        case OTHER: Facts.selected = 2; break;
                    }
                }
                Scenario serial determine(Facts) { choose; }
                """);
        Path output = temporary.getRoot().toPath().resolve("enum-fallback");
        Main.run(new String[]{"generate", "--source", source.toString(), "--output", output.toString()});
        Path library = output.resolve("src/main/java/com/servpp/generated/routing");
        String fallback = Files.readString(library.resolve("model/Route.java"));
        assertTrue(fallback.contains("OTHER(\"fallback\")"));
        assertTrue(fallback.contains("return OTHER;"));
        assertFalse(fallback.contains("Unexpected value"));
        String strict = Files.readString(library.resolve("model/StrictRoute.java"));
        assertTrue(strict.contains("Unexpected value"));
        assertFalse(strict.contains("return OTHER;"));
        String calculation = Files.readString(library.resolve("service/SimpleChoose.java"));
        assertTrue(calculation.contains("requireNonNullElse("));
        assertTrue(calculation.contains("Route.fromValue(\"fallback\")"));
    }

    private Path source()throws Exception {
        Path source=temporary.newFolder().toPath();
        var text=new StringBuilder("domain ledger; Role Facts { int amount; } atomic calculate twice(Facts) { Facts.amount *= 2; }\n");
        for(int i=0;i<40;i++)text.append("Scenario serial preview").append(i).append("(Facts) { twice; }\n");
        Files.writeString(source.resolve("ledger.spp"),text);return source;
    }
    @Test public void noAccountQuotaOrOfficialClassesAndGeneratesPlainModels()throws Exception {
        for(String name:new String[]{"com.servpp.sdk.OnlineAuthorizer","com.servpp.sdk.MembershipCapabilities",
                "com.servpp.service.runtime.OfficialPropertyChanges","com.servpp.generator.enhanced.PropertyTrackingModelExtension"}) {
            try{Class.forName(name);fail("Commercial class on basic compiler path: "+name);}catch(ClassNotFoundException expected){}
        }
        assertEquals(false,Main.run(new String[]{"status"}).get("authorizationRequired"));
        Path source=source(),output=temporary.getRoot().toPath().resolve("generated");
        var report=Main.run(new String[]{"generate","--source",source.toString(),"--output",output.toString()});
        assertEquals("basic",report.get("edition"));
        assertEquals(40,((JsonNode)report.get("project")).path("scenarioCount").asInt());
        assertTrue(Files.exists(output.resolve("pom.xml")));
        String pom=Files.readString(output.resolve("pom.xml"));
        assertFalse(pom.contains("appframe-enhanced"));assertFalse(pom.contains("appframe-seata"));
        String model=Files.readString(output.resolve("src/main/java/com/servpp/generated/ledger/model/Facts.java"));
        assertFalse(model.contains("PropertyChanges"));assertFalse(model.contains("TrackedObject"));
        var provenance=new ObjectMapper().readTree(output.resolve("spp-generation.json").toFile());
        assertEquals("basic",provenance.path("edition").asText());assertEquals(0,provenance.path("runtimeExtensions").size());
        try{Main.run(new String[]{"generate","--source",source.toString(),"--output",output.toString()});fail();}
        catch(CompilationException expected){assertEquals("OUTPUT_EXISTS",expected.code());}
    }
    @Test public void checkHasNoOutputAndRejectsInvalidEnvironment()throws Exception {
        Path source=source(),output=temporary.getRoot().toPath().resolve("absent");
        var report=Main.run(new String[]{"check","--source",source.toString(),"--output",output.toString()});
        assertEquals(true,report.get("checked"));assertFalse(Files.exists(output));
        try{Main.run(new String[]{"check","--source",source.toString(),"--environment","../invalid"});fail();}
        catch(CompilationException expected){assertEquals("INVALID_ARGUMENT",expected.code());}
    }

    @Test
    public void repositoryKeyInterceptionChecksDeclaredTextKeys() throws Exception {
        Path source = temporary.newFolder().toPath();
        Files.writeString(source.resolve("catalog.spp"), """
                domain catalog;
                Entity Record { String category; String code; int regionNumber; }
                atomic query lookup(Record selection, Record[] matches);
                Scenario serial search(Record selection, Record[] matches) { lookup; }
                """);
        Path app = source.resolve("catalog.app");
        String binding = """
                domain catalog;
                scope {
                    local {
                        repository(argument(0, Record.category), argument(0, Record.code),
                            argument(0, Record.regionNumber), matches) intercept key {
                            %s
                        } lookup;
                    }
                }
                """;
        String[] check = {"check", "--source", source.toString()};
        Files.writeString(app, binding.formatted("""
                category = "PHS\\u0022\\u000a";
                code = "00000000" + category;
                """));
        Path output = temporary.getRoot().toPath().resolve("key-interception");
        Main.run(new String[]{"generate", "--source", source.toString(), "--output", output.toString()});
        String generated = Files.readString(
                output.resolve("src/main/java/com/servpp/generated/catalog/service/SimpleLookup.java"));
        assertTrue(generated.contains("keys -> \"PHS\\042\\012\""));
        for (String invalid : java.util.List.of("""
                absent = "PHS";
                """, """
                code = absent;
                """, """
                category = "PHS";
                category = "DUP";
                """, """
                regionNumber = "V1";
                """, """
                code = "PREFIX" + regionNumber;
                """)) {
            Files.writeString(app, binding.formatted(invalid));
            assertThrows(invalid, MojoExecutionException.class, () -> Main.run(check));
        }
    }

    @Test
    public void stringFieldLengthIsAnOverridableColumnDefault() throws Exception {
        Path source = temporary.newFolder().toPath();
        Files.writeString(source.resolve("widths.spp"), """
                domain widths;
                Entity Address {
                    String(20) customerNumber;
                    String(80) address;
                    String(6) postalCode;
                    String(4) areaCode;
                }
                Role TextValue { String(3) value; }
                atomic calculate appendText(TextValue text) { text.value = text.value + "long"; }
                Scenario serial preview(TextValue text) { appendText(text); }
                """);
        Files.writeString(source.resolve("widths.app"), """
                domain widths;
                tables entities {
                    table ADDRESS_ROWS(Address) {
                        primarykey(Address.customerNumber);
                        notnull String(9) postalCode;
                        notnull String areaCode;
                    }
                }
                map ADDRESS_ROWS {
                    address = String(120) street;
                    areaCode = String zone_code;
                }
                """);
        Path output = temporary.getRoot().toPath().resolve("widths");
        Main.run(new String[]{"generate", "--source", source.toString(), "--output", output.toString()});
        String ddl = Files.readString(output.resolve("openapi/sql/ddl.sql"));
        assertTrue(ddl, ddl.contains("customerNumber VARCHAR(20)"));
        assertTrue(ddl, ddl.contains("street VARCHAR(120)"));
        assertTrue(ddl, ddl.contains("postalCode VARCHAR(9)"));
        assertTrue(ddl, ddl.contains("zone_code VARCHAR(4)"));
        String metadata = Files.readString(output.resolve(
                "src/main/resources/META-INF/spp/app-ddl.properties"));
        assertTrue(metadata, metadata.contains("table.ADDRESS_ROWS.column.street.precision=120"));
        assertTrue(metadata, metadata.contains("table.ADDRESS_ROWS.column.zone_code.precision=4"));
        String api = Files.readString(output.resolve("openapi/widths.spp.yaml"));
        assertFalse(api, api.contains("maxLength:"));
    }

    @Test
    public void stringFieldLengthMustBePositiveAndInRange() throws Exception {
        Path source = temporary.newFolder().toPath();
        for (String length : java.util.List.of("0", "2147483648", "0x10")) {
            Files.writeString(source.resolve("widths.spp"),
                    "domain widths; Role TextValue { String(" + length + ") value; }");
            assertThrows(length, MojoExecutionException.class,
                    () -> Main.run(new String[]{"check", "--source", source.toString()}));
        }
    }
}
