package com.servpp.sdk.basic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.sdk.CompilationException;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.repository.JdbcAppDdlObjectTableReader;
import com.servpp.service.repository.JdbcAppDdlObjectTableWriter;
import com.servpp.service.repository.JdbcAppDdlSchemaManager;
import com.servpp.service.runtime.AppDdlCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BasicCompilerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void requireUsesTheCanonicalWireCodeOfAnEnumMember() throws Exception {
        Path source = temporary.newFolder().toPath();
        Path check = source.resolve("checks.spp");
        String declarations = """
                domain checks;
                Enum AccountError { NUMBER_REQUIRED:"E01","legacy", NOT_FOUND }
                Role Input { boolean valid; }
                """;
        Files.writeString(check, declarations + """
                atomic check checkAccount(Input) {
                    require(Input.valid, AccountError.NUMBER_REQUIRED);
                    require(Input.valid, AccountError.NOT_FOUND);
                    require(Input.valid, "LITERAL");
                }
                """);
        Path output = temporary.getRoot().toPath().resolve("enum-rejection");
        Main.run(new String[]{"generate", "--source", source.toString(), "--output", output.toString()});
        String java = Files.readString(output.resolve("src/main/java/com/servpp/generated/checks/service/SimpleCheckAccount.java"));
        assertTrue(java.contains("BusinessChecks.fail(\"E01\")"));
        assertTrue(java.contains("BusinessChecks.fail(\"NOT_FOUND\")"));
        assertTrue(java.contains("BusinessChecks.fail(\"LITERAL\")"));
        assertFalse(java.contains("BusinessChecks.fail(\"legacy\")"));

        for (String code : List.of("UnknownError.NUMBER_REQUIRED", "AccountError.UNKNOWN", "Input.valid")) {
            Files.writeString(check, declarations + """
                    atomic check checkAccount(Input) { require(Input.valid, %s); }
                    """.formatted(code));
            assertThrows(code, MojoExecutionException.class,
                    () -> Main.run(new String[]{"check", "--source", source.toString()}));
        }
    }

    @Test
    public void enumFallbacksDistinguishMissingAndUnrecognizedValues() throws Exception {
        Path source = temporary.newFolder().toPath();
        Files.writeString(source.resolve("routing.spp"), """
                domain routing;
                Enum Route { DIRECT, OTHER:"fallback" }
                Enum StrictRoute { DIRECT, INDIRECT }
                Enum Completion { COMPLETE:"complete":other, NULL_VALUE:null, ACTIVE, OTHER }
                Enum NullOnly { NULL_VALUE:null, ACTIVE }
                Enum OtherOnly { FALLBACK:other, PUBLIC:"02","04","06","25","42","62" }
                Role Facts { Route route; StrictRoute strictRoute; Completion completion; NullOnly nullOnly; OtherOnly otherOnly; boolean unknown; int selected; }
                atomic calculate choose(Facts) {
                    Facts.unknown = Facts.route == Route.OTHER;
                    switch (Facts.route) {
                        case DIRECT: Facts.selected = 1; break;
                        case OTHER: Facts.selected = 2; break;
                    }
                    switch (Facts.completion) {
                        case NULL_VALUE: Facts.selected = 3; break;
                        case COMPLETE: Facts.selected = 4; break;
                    }
                    switch (Facts.otherOnly) {
                        case PUBLIC: Facts.selected = 5; break;
                    }
                }
                Scenario serial determine(Facts) { choose; }
                """);
        Path output = temporary.getRoot().toPath().resolve("enum-fallback");
        Main.run(new String[]{"generate", "--source", source.toString(), "--output", output.toString()});
        Path library = output.resolve("src/main/java/com/servpp/generated/routing");
        String fallback = Files.readString(library.resolve("model/Route.java"));
        assertTrue(fallback.contains("OTHER(\"fallback\")"));
        assertTrue(fallback.contains("return values()[1];"));
        assertFalse(fallback.contains("Unexpected value"));
        String strict = Files.readString(library.resolve("model/StrictRoute.java"));
        assertTrue(strict.contains("Unexpected value"));
        assertFalse(strict.contains("return values()["));
        String completion = Files.readString(library.resolve("model/Completion.java"));
        assertTrue(completion.contains("if (value == null) return values()[1];"));
        assertTrue(completion.contains("return values()[0];"));
        String nullOnly = Files.readString(library.resolve("model/NullOnly.java"));
        assertTrue(nullOnly.contains("if (value == null) return values()[0];"));
        assertTrue(nullOnly.contains("Unexpected value"));
        String calculation = Files.readString(library.resolve("service/SimpleChoose.java"));
        assertTrue(calculation.contains("requireNonNullElse("));
        assertTrue(calculation.contains("Route.fromValue(\"fallback\")"));
        assertTrue(calculation.contains("Completion.fromValue(\"NULL_VALUE\")"));
        assertTrue(calculation.contains("case \"complete\":"));
        assertTrue(calculation.contains("case \"02\", \"04\", \"06\", \"25\", \"42\", \"62\":"));

        Path classes = temporary.newFolder().toPath();
        Path storageFixture = temporary.newFolder().toPath().resolve("WireAccount.java");
        Files.writeString(storageFixture, """
                package com.servpp.generated.routing.model;

                public class WireAccount {
                    private String accountNumber;
                    private OtherOnly accountType;
                    public String getAccountNumber() { return accountNumber; }
                    public void setAccountNumber(String value) { accountNumber = value; }
                    public OtherOnly getAccountType() { return accountType; }
                    public void setAccountType(OtherOnly value) { accountType = value; }
                }
                """);
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, "-d", classes.toString(),
                library.resolve("model/Completion.java").toString(),
                library.resolve("model/NullOnly.java").toString(),
                library.resolve("model/OtherOnly.java").toString(), storageFixture.toString()));
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            var mapper = new ObjectMapper();
            var completionClass = loader.loadClass("com.servpp.generated.routing.model.Completion");
            assertEquals("NULL_VALUE", mapper.readValue("null", completionClass).toString());
            assertEquals("complete", mapper.readValue("\"unrecognized\"", completionClass).toString());
            assertEquals("OTHER", mapper.readValue("\"OTHER\"", completionClass).toString());
            assertEquals("ACTIVE", mapper.readValue("\"ACTIVE\"", completionClass).toString());
            var nullOnlyClass = loader.loadClass("com.servpp.generated.routing.model.NullOnly");
            assertEquals("NULL_VALUE", mapper.readValue("null", nullOnlyClass).toString());
            assertThrows(com.fasterxml.jackson.databind.exc.InvalidFormatException.class,
                    () -> mapper.readValue("\"unrecognized\"", nullOnlyClass));
            var otherOnlyClass = loader.loadClass("com.servpp.generated.routing.model.OtherOnly");
            assertEquals("FALLBACK", mapper.readValue("null", otherOnlyClass).toString());
            assertEquals("FALLBACK", mapper.readValue("\"unrecognized\"", otherOnlyClass).toString());
            assertEquals(2, otherOnlyClass.getEnumConstants().length);
            for (String alias : java.util.List.of("02", "04", "06", "25", "42", "62")) {
                var member = mapper.readValue("\"" + alias + "\"", otherOnlyClass);
                assertSame(otherOnlyClass.getEnumConstants()[1], member);
                assertEquals("\"02\"", mapper.writeValueAsString(member));
            }
            verifyMappedEnumStorage(loader.loadClass("com.servpp.generated.routing.model.WireAccount"),
                    otherOnlyClass, otherOnlyClass.getEnumConstants()[1]);
        }
    }

    private void verifyMappedEnumStorage(Class<?> accountType, Class<?> enumType, Object publicMember) throws Exception {
        var properties = new Properties();
        properties.setProperty("tables", "WIRE_ACCOUNTS");
        properties.setProperty("table.WIRE_ACCOUNTS.category", "entities");
        properties.setProperty("table.WIRE_ACCOUNTS.businessType", "WireAccount");
        properties.setProperty("table.WIRE_ACCOUNTS.primaryKeys", "accountNumber");
        properties.setProperty("table.WIRE_ACCOUNTS.columns", "accountNumber,accountType");
        properties.setProperty("table.WIRE_ACCOUNTS.column.accountNumber.type", "VARCHAR");
        properties.setProperty("table.WIRE_ACCOUNTS.column.accountNumber.precision", "16");
        properties.setProperty("table.WIRE_ACCOUNTS.column.accountType.type", "VARCHAR");
        properties.setProperty("table.WIRE_ACCOUNTS.column.accountType.precision", "16");
        var catalog = AppDdlCatalog.fromProperties(properties);
        var source = new DriverManagerJdbcDataSource("org.h2.Driver",
                "jdbc:h2:mem:enum_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", null, null);
        new JdbcAppDdlSchemaManager(source).ensureTables(catalog);
        Object account = accountType.getDeclaredConstructor().newInstance();
        accountType.getMethod("setAccountNumber", String.class).invoke(account, "CANONICAL");
        accountType.getMethod("setAccountType", enumType).invoke(account, publicMember);
        new JdbcAppDdlObjectTableWriter(source, catalog).save("WireAccount", account);
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            assertEquals(1, sql.executeUpdate("insert into WIRE_ACCOUNTS(accountNumber,accountType) values ('LEGACY','62')"));
            var reader = new JdbcAppDdlObjectTableReader(source, catalog);
            var method = accountType.getMethod("getAccountType");
            for (String id : List.of("CANONICAL", "LEGACY")) {
                assertSame(publicMember, method.invoke(reader.find("WireAccount", id, accountType).orElseThrow()));
            }
            assertEquals(List.of("CANONICAL"), reader.findObjectIdsBy("WireAccount", "accountType", publicMember));
            try (var rows = sql.executeQuery("select accountType from WIRE_ACCOUNTS order by accountNumber")) {
                assertTrue(rows.next());
                assertEquals("02", rows.getString(1));
                assertTrue(rows.next());
                assertEquals("62", rows.getString(1));
                assertFalse(rows.next());
            }
            sql.execute("SHUTDOWN");
        }
    }

    @Test
    public void enumFallbackKeywordsAreReservedAndEachCanBeAssignedOnce() throws Exception {
        Path source = temporary.newFolder().toPath();
        for (String declaration : java.util.List.of(
                "Enum Choice { FIRST:other, SECOND:other }",
                "Enum Choice { FIRST:null, SECOND:null }",
                "Enum Choice { FIRST(\"first\") }",
                "Enum Choice { FIRST(other) }",
                "Enum Choice { FIRST(null) }",
                "Enum Choice { FIRST:\"01\",\"02\", SECOND:\"02\" }",
                "Enum Choice { FIRST:\"01\",\"01\" }",
                "Enum Choice { FIRST:\"01\",\"SECOND\", SECOND }",
                "Role Facts { String other; }",
                "Role Facts { String null; }")) {
            Files.writeString(source.resolve("choices.spp"), "domain choices; " + declaration);
            assertThrows(declaration, MojoExecutionException.class,
                    () -> Main.run(new String[]{"check", "--source", source.toString()}));
        }
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
