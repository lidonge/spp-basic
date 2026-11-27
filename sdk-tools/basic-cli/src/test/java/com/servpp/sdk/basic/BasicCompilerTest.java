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
                Role Input { boolean valid; AccountError rejection; }
                """;
        Files.writeString(check, declarations + """
                atomic check checkAccount(Input) {
                    require(Input.valid, AccountError.NUMBER_REQUIRED);
                    require(Input.valid, AccountError.NOT_FOUND);
                    require(Input.rejection == null, Input.rejection);
                }
                """);
        Path output = temporary.getRoot().toPath().resolve("enum-rejection");
        Main.run(new String[]{"generate", "--source", source.toString(), "--output", output.toString()});
        String java = Files.readString(output.resolve("src/main/java/com/servpp/generated/checks/service/SimpleCheckAccount.java"));
        assertTrue(java.contains("BusinessChecks.fail(\"E01\")"));
        assertTrue(java.contains("BusinessChecks.fail(\"NOT_FOUND\")"));
        assertTrue(java.contains("BusinessChecks.fail(java.util.Objects.requireNonNull(Input.getRejection(), "
                + "\"REJECTION_CODE_REQUIRED\").getValue())"));
        assertFalse(java.contains("BusinessChecks.fail(\"legacy\")"));

        for (String code : List.of("\"LITERAL\"", "UnknownError.NUMBER_REQUIRED", "AccountError.UNKNOWN", "Input.valid")) {
            Files.writeString(check, declarations + """
                    atomic check checkAccount(Input) { require(Input.valid, %s); }
                    """.formatted(code));
            assertThrows(code, MojoExecutionException.class,
                    () -> Main.run(new String[]{"check", "--source", source.toString()}));
        }
    }

    @Test
    public void constructedProviderCodeSurvivesClassificationAndScenarioRejection() throws Exception {
        Path source = temporary.newFolder().toPath();
        Files.writeString(source.resolve("provider-code.spp"), """
                domain providerprobe;
                const String CLOSED_CODE = "EC031";
                Enum CardRejection { LIMIT_EXCEEDED:"EC012","LIMIT", PRODUCT_CLOSED:"EC031", UNKNOWN:other, MISSING:null }
                Enum StrictRejection { LIMIT_EXCEEDED:"EC012" }
                Enum LegacyRejection { LIMIT_EXCEEDED:"EC012", OTHER }
                Role ProviderResult short result { String(5) code; CardRejection reason; boolean unknown; boolean closed; }
                atomic check checkProvider(ProviderResult) {
                    require(result.code == "", CardRejection(result.code));
                }
                atomic check checkStrict(ProviderResult) {
                    require(result.code == "", StrictRejection(result.code));
                }
                atomic check checkLegacy(ProviderResult) {
                    require(result.code == "", LegacyRejection(result.code));
                }
                atomic calculate classify(ProviderResult) {
                    result.reason = CardRejection(result.code);
                    result.closed = CardRejection(result.code) == CardRejection(CLOSED_CODE);
                    switch (CardRejection(result.code)) {
                        case UNKNOWN: result.unknown = result.reason == CardRejection.UNKNOWN; break;
                        default: result.unknown = false;
                    }
                }
                readonly Scenario serial readProvider(ProviderResult) {
                    classify(result);
                    checkProvider(result);
                }
                readonly Scenario serial readStrict(ProviderResult) { checkStrict(result); }
                readonly Scenario serial readLegacy(ProviderResult) { checkLegacy(result); }
                """);
        Path output = temporary.getRoot().toPath().resolve("provider-probe");
        Main.run(new String[]{"generate", "--source", source.toString(), "--output", output.toString()});
        Path classes = temporary.newFolder().toPath();
        var arguments = new java.util.ArrayList<>(List.of("-d", classes.toString()));
        try (var files = Files.walk(output.resolve("src/main/java"))) {
            files.filter(file -> file.toString().endsWith(".java")).map(Path::toString).sorted().forEach(arguments::add);
        }
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            String prefix = "com.servpp.generated.providerprobe.";
            Class<?> role = loader.loadClass(prefix + "model.ProviderResult");
            Object result = role.getDeclaredConstructor().newInstance();
            var setCode = role.getMethod("setCode", String.class);
            var classify = loader.loadClass(prefix + "service.SimpleClassify");
            var calculation = (com.servpp.service.Service) classify.getDeclaredConstructor().newInstance();
            var check = (com.servpp.service.Service) loader.loadClass(prefix + "service.SimpleCheckProvider")
                    .getDeclaredConstructor().newInstance();
            var services = com.servpp.service.server.DefaultServiceContainer.getInstance();
            services.registerService(check.getName(), check);
            services.registerService(calculation.getName(), calculation);
            var scene = loader.loadClass(prefix + "service.SimpleReadProvider");
            var scenario = (com.servpp.service.Service) scene.getDeclaredConstructor().newInstance();
            var requestType = loader.loadClass(prefix + "model.ReadProviderRequest");
            Object request = requestType.getDeclaredConstructor().newInstance();
            requestType.getMethod("setResult", role).invoke(request, result);

            ObjectMapper mapper = new ObjectMapper();
            Object previous = null;
            for (String[] entry : new String[][]{{"UN001", "UN001"}, {" UN2 ", " UN2 "}, {"LIMIT", "EC012"},
                    {"EC031", "EC031"}, {null, "MISSING"}}) {
                setCode.invoke(result, (Object) entry[0]);
                com.servpp.service.runtime.RemoteServiceIngress.execute(scenario, request, "fixture", "classify");
                Object reason = role.getMethod("getReason").invoke(result);
                assertEquals(mapper.writeValueAsString(entry[1]), mapper.writeValueAsString(reason));
                assertEquals(reason, mapper.readValue(mapper.writeValueAsString(reason), reason.getClass()));
                assertEquals(entry[0] != null && entry[0].contains("UN"), role.getMethod("getUnknown").invoke(result));
                assertEquals("EC031".equals(entry[0]), role.getMethod("getClosed").invoke(result));
                if (previous != null) assertEquals("\"UN001\"", mapper.writeValueAsString(previous));
                if ("UN001".equals(entry[0])) previous = reason;

                assertEquals(new com.servpp.service.runtime.BusinessChecks.Result(false, entry[1]),
                        com.servpp.service.runtime.BusinessChecks.scenarioResult());
            }
            setCode.invoke(result, "");
            com.servpp.service.runtime.RemoteServiceIngress.execute(scenario, request, "fixture", "empty");
            assertTrue(com.servpp.service.runtime.BusinessChecks.scenarioResult().passed());

            setCode.invoke(result, "UN001");
            for (String name : List.of("Strict", "Legacy")) {
                var type = loader.loadClass(prefix + "service.SimpleCheck" + name);
                var atomic = (com.servpp.service.Service) type.getConstructor().newInstance();
                services.registerService(atomic.getName(), atomic);
                var entry = (com.servpp.service.Service) loader.loadClass(prefix + "service.SimpleRead" + name).getConstructor().newInstance();
                var input = loader.loadClass(prefix + "model.Read" + name + "Request").getConstructor().newInstance();
                input.getClass().getMethod("setResult", role).invoke(input, result);
                var exception = assertThrows(IllegalArgumentException.class,
                        () -> com.servpp.service.runtime.RemoteServiceIngress.execute(entry, input, "fixture", name));
                assertTrue(exception.getMessage().contains(name + "Rejection"));
                assertTrue(exception.getMessage().contains("UN001"));
            }
        } finally {
            com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(null);
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
                Enum Kind { KNOWN, OTHER:other }
                Enum MemberNamedKind { Kind, OTHER:other }
                Role Facts { Route route; StrictRoute strictRoute; Completion completion; NullOnly nullOnly; OtherOnly otherOnly; boolean unknown; int selected; }
                atomic calculate choose(Facts) {
                    facts.unknown = facts.route == Route.OTHER;
                    switch (facts.route) {
                        case DIRECT: facts.selected = 1; break;
                        case OTHER: facts.selected = 2; break;
                    }
                    switch (facts.completion) {
                        case NULL_VALUE: facts.selected = 3; break;
                        case COMPLETE: facts.selected = 4; break;
                    }
                    switch (facts.otherOnly) {
                        case PUBLIC: facts.selected = 5; break;
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
        assertTrue(fallback.contains("fromCode(String value)"));
        String strict = Files.readString(library.resolve("model/StrictRoute.java"));
        assertTrue(strict.contains("Unexpected value"));
        assertFalse(strict.contains("return values()["));
        String completion = Files.readString(library.resolve("model/Completion.java"));
        assertTrue(completion.contains("if (value == null) return $MEMBERS[1];"));
        String nullOnly = Files.readString(library.resolve("model/NullOnly.java"));
        assertTrue(nullOnly.contains("if (value == null) return values()[0];"));
        assertTrue(nullOnly.contains("Unexpected value"));
        String calculation = Files.readString(library.resolve("service/SimpleChoose.java"));
        assertTrue(calculation.contains("requireNonNullElse("));
        assertTrue(calculation.contains("Route.fromValue(\"fallback\")"));
        assertTrue(calculation.contains("Completion.fromValue(\"NULL_VALUE\")"));
        assertTrue(calculation.contains("case \"complete\":"));
        assertTrue(calculation.contains("case \"02\":"));

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

                    public static String classify(String value) {
                        var member = Kind.fromValue(value);
                        return switch (member.kind()) {
                            case KNOWN -> "known";
                            case OTHER -> member.getValue();
                        };
                    }
                }
                """);
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, "--release", "25", "-d", classes.toString(),
                library.resolve("model/Completion.java").toString(),
                library.resolve("model/Kind.java").toString(),
                library.resolve("model/MemberNamedKind.java").toString(),
                library.resolve("model/NullOnly.java").toString(),
                library.resolve("model/OtherOnly.java").toString(), storageFixture.toString()));
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            var mapper = new ObjectMapper();
            var accountClass = loader.loadClass("com.servpp.generated.routing.model.WireAccount");
            var classify = accountClass.getMethod("classify", String.class);
            assertEquals("known", classify.invoke(null, "KNOWN"));
            assertEquals(" XY901 ", classify.invoke(null, " XY901 "));
            var completionClass = loader.loadClass("com.servpp.generated.routing.model.Completion");
            var kind = completionClass.getMethod("kind");
            Object missing = mapper.readValue("null", completionClass);
            assertEquals("NULL_VALUE", missing.toString());
            assertEquals("NULL_VALUE", ((Enum<?>) kind.invoke(missing)).name());
            Object unknown = mapper.readValue("\"unrecognized\"", completionClass);
            assertEquals("unrecognized", unknown.toString());
            assertEquals("COMPLETE", ((Enum<?>) kind.invoke(unknown)).name());
            assertEquals("\"unrecognized\"", mapper.writeValueAsString(unknown));
            Object other = mapper.readValue("\"OTHER\"", completionClass);
            assertEquals("OTHER", other.toString());
            assertEquals("OTHER", ((Enum<?>) kind.invoke(other)).name());
            assertEquals("ACTIVE", mapper.readValue("\"ACTIVE\"", completionClass).toString());
            var nullOnlyClass = loader.loadClass("com.servpp.generated.routing.model.NullOnly");
            assertEquals("NULL_VALUE", mapper.readValue("null", nullOnlyClass).toString());
            assertThrows(com.fasterxml.jackson.databind.exc.InvalidFormatException.class,
                    () -> mapper.readValue("\"unrecognized\"", nullOnlyClass));
            var otherOnlyClass = loader.loadClass("com.servpp.generated.routing.model.OtherOnly");
            assertEquals("FALLBACK", mapper.readValue("null", otherOnlyClass).toString());
            assertEquals("unrecognized", mapper.readValue("\"unrecognized\"", otherOnlyClass).toString());
            Object publicMember = otherOnlyClass.getField("PUBLIC").get(null);
            for (String alias : java.util.List.of("02", "04", "06", "25", "42", "62")) {
                var member = mapper.readValue("\"" + alias + "\"", otherOnlyClass);
                assertSame(publicMember, member);
                assertEquals("PUBLIC", ((Enum<?>) otherOnlyClass.getMethod("kind").invoke(member)).name());
                assertEquals("\"02\"", mapper.writeValueAsString(member));
            }
            verifyMappedEnumStorage(accountClass, otherOnlyClass, publicMember);
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
            Object unknown = new ObjectMapper().readValue("\"XY901\"", enumType);
            accountType.getMethod("setAccountNumber", String.class).invoke(account, "UNKNOWN");
            accountType.getMethod("setAccountType", enumType).invoke(account, unknown);
            new JdbcAppDdlObjectTableWriter(source, catalog).save("WireAccount", account);
            var reader = new JdbcAppDdlObjectTableReader(source, catalog);
            var method = accountType.getMethod("getAccountType");
            for (String id : List.of("CANONICAL", "LEGACY")) {
                assertSame(publicMember, method.invoke(reader.find("WireAccount", id, accountType).orElseThrow()));
            }
            assertEquals(List.of("CANONICAL"), reader.findObjectIdsBy("WireAccount", "accountType", publicMember));
            assertEquals(unknown, method.invoke(reader.find("WireAccount", "UNKNOWN", accountType).orElseThrow()));
            assertEquals(List.of("UNKNOWN"), reader.findObjectIdsBy("WireAccount", "accountType", unknown));
            try (var rows = sql.executeQuery("select accountType from WIRE_ACCOUNTS order by accountNumber")) {
                assertTrue(rows.next());
                assertEquals("02", rows.getString(1));
                assertTrue(rows.next());
                assertEquals("62", rows.getString(1));
                assertTrue(rows.next());
                assertEquals("XY901", rows.getString(1));
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
        var text=new StringBuilder("domain ledger; Role Facts { int amount; } atomic calculate twice(Facts) { facts.amount *= 2; }\n");
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
                Entity Record short selection, matches { String category; String code; int regionNumber; }
                atomic query lookup(Record, Record[]);
                Scenario serial search(Record, Record[]) { lookup; }
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
                Role TextValue short text { String(3) value; }
                atomic calculate appendText(TextValue) { text.value = text.value + "long"; }
                Scenario serial preview(TextValue) { appendText(text); }
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
