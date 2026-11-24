package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.generator.GeneratorUtil;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import static org.junit.Assert.*;

public class ReadKeyBindingPersistenceTest {
    private static final String JDBC_URL = "jdbc:h2:mem:read_key_binding;DB_CLOSE_DELAY=-1";
    private static final String MODEL = "generated.reads.model.";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void generatedScenarioReadsH2WithMappedKeysAndFieldConstants() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("reads.spp"), """
                domain reads;
                Role Item { String code; int edition; String title; }
                Role Selection { String wanted; }
                Role Release { int edition; }
                take Selection, Release as Item { String code = wanted; mapsame; }
                Entity Stock { String code; int edition; String title; }
                Role Lookup { String wanted; }
                take Stock as Lookup { String wanted = code; }
                Enum ReadError { DUPLICATE:"D02", MISSING:"M00" }
                Contract Marker { String receipt; }
                atomic check checkItem(Stock item) { require(item, ReadError.MISSING); }
                Scenario serial readUnique(Stock item, Lookup selection, Marker marker) {
                    transaction {
                        unique findStock by selection;
                        checkItem,breakTransaction;
                        insertMarker;
                    }
                }
                atomic check checkRole(Item item) { require(item, ReadError.MISSING); }
                Scenario serial readUniqueNamed(Item item, Selection selection, Marker marker) {
                    transaction {
                        unique findItem by selection else ReadError.DUPLICATE;
                        checkRole,breakTransaction;
                        insertMarker;
                    }
                }
                const int CURRENT = 3;
                atomic query listItems(key Item item, Item[] matches);
                Scenario serial readSelected(Item, Selection, Release) {
                    findItem by Selection, Release;
                }
                Scenario serial readDefault(Item) {
                    findItem by Selection.wanted = "A", Item.edition = CURRENT;
                }
                Scenario serial readList(Item, Item[] matches) {
                    listItems(Item, matches) by Selection.wanted = "A", Item.edition = CURRENT;
                }
                """);
        Files.writeString(source.resolve("reads.app"), """
                domain reads;
                application readBinding { deploy reads; }
                transactions { default at resource records; }
                tables entities { table STOCK(Stock) { primarykey(Stock.edition, Stock.code); } }
                tables contracts { table MARKERS(Marker) { primarykey(Marker.receipt); } }
                tables roles {
                    table ITEMS(Item) { primarykey(Item.edition, Item.code); }
                    table SELECTIONS(Selection) { primarykey(Selection.wanted); }
                    table RELEASES(Release) { primarykey(Release.edition); }
                }
                scope { local {
                    repository(argument(0, Item.edition), argument(0, Item.code), matches) using records listItems;
                } }
                phy { repository records {
                    adapter jdbcObjectRepository;
                    config default = true;
                    config appDdlTables = true;
                    config persistenceMode = LOCAL; config persistenceResource = records;
                    config datasourceUrl = "%s"; config driverClassName = "org.h2.Driver";
                } }
                """.formatted(JDBC_URL));
        Path yaml = root.resolve("yaml");
        Path services = root.resolve("java");
        GeneratorUtil.execute(source.toFile(), yaml.toFile(), services.toFile(), "generated");
        Path client = root.resolve("client");
        var config = new CodegenConfigurator().setGeneratorName("spring")
                .setInputSpec(yaml.resolve("reads.spp.yaml").toString()).setOutputDir(client.toString())
                .setModelPackage("generated.reads.model").setTemplateDir(yaml.resolve("JavaSpring").toString())
                .setValidateSpec(false).addAdditionalProperty("useSpringBoot3", true)
                .addAdditionalProperty("useBeanValidation", false).addAdditionalProperty("annotationLibrary", "none")
                .addAdditionalProperty("documentationProvider", "none").addAdditionalProperty("openApiNullable", false)
                .setGlobalProperties(Map.of("models", "", "modelTests", "false", "modelDocs", "false"));
        new DefaultGenerator().opts(config.toClientOptInput()).generate();

        Path classes = Files.createDirectory(root.resolve("classes"));
        String classpath = System.getProperty("java.class.path");
        var args = new ArrayList<>(List.of("-classpath", classpath, "-d", classes.toString()));
        for (Path generated : List.of(services, client.resolve("src/main/java"))) {
            try (var files = Files.walk(generated)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> args.add(path.toString()));
            }
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        // Generated service registration uses Class.forName against the application classpath.
        Path log = root.resolve("runtime.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", classpath + java.io.File.pathSeparator + classes + java.io.File.pathSeparator + root.resolve("resources"),
                DatabaseFixture.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            fail("Generated H2 fixture timed out: " + Files.readString(log));
        }
        int status = process.exitValue();
        assertEquals(Files.readString(log), 0, status);
    }

    public static class DatabaseFixture {
        public static void main(String[] args) throws Exception {
            var dataSource = new DriverManagerJdbcDataSource("org.h2.Driver", JDBC_URL, null, null);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(dataSource, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "readBinding");
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                assertEquals(2, statement.executeUpdate("""
                        INSERT INTO ITEMS (code, edition, title) VALUES ('A', 3, 'selected'), ('B', 4, 'original')
                        """));
                assertEquals(2, statement.executeUpdate("INSERT INTO SELECTIONS (wanted) VALUES ('A'), ('B')"));
                assertEquals(2, statement.executeUpdate("INSERT INTO RELEASES (edition) VALUES (3), (4)"));
            }
            var stale = Map.of("code", "B", "edition", 4);
            var selected = execute(runtime, mapper, "ReadSelected", Map.of("Item", stale,
                    "Selection", Map.of("wanted", "A"), "Release", Map.of("edition", 3))).get("Item");
            assertEquals(Map.of("code", "A", "edition", 3, "title", "selected"),
                    mapper.convertValue(selected, Map.class));
            var defaults = execute(runtime, mapper, "ReadDefault", Map.of("Item", stale)).get("Item");
            assertEquals(selected, defaults);
            var missing = execute(runtime, mapper, "ReadSelected", Map.of("Item", stale,
                    "Selection", Map.of("wanted", "missing"), "Release", Map.of("edition", 3))).get("Item");
            assertEquals("missing", missing.get("code").asText());
            assertEquals(3, missing.get("edition").asInt());
            var list = execute(runtime, mapper, "ReadList", Map.of("Item", stale, "matches", List.of()));
            assertEquals("A", list.get("Item").get("code").asText());
            assertEquals(3, list.get("Item").get("edition").asInt());
            assertEquals(1, list.get("matches").size());
            assertEquals(selected, list.get("matches").get(0));
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO STOCK (code, edition, title) VALUES ('B',4,'original'),('C',1,'first'),('C',2,'second'),('C',3,'third')");
                statement.executeUpdate("INSERT INTO ITEMS (code, edition, title) SELECT code,edition,title FROM STOCK WHERE code='C'");
                for (String scenario : List.of("ReadUnique", "ReadUniqueNamed")) {
                    for (String key : List.of("missing", "B", "C")) {
                        String marker = scenario + key;
                        var response = execute(runtime, mapper, scenario, Map.of("item", stale,
                                "selection", Map.of("wanted", key), "marker", Map.of("receipt", marker)));
                        var result = com.servpp.service.runtime.BusinessChecks.scenarioResult();
                        if (key.equals("B")) {
                            assertTrue(result.toString(), result.passed());
                            assertEquals("original", response.get("item").get("title").asText());
                        } else {
                            assertFalse(result.toString(), result.passed());
                            assertEquals(key.equals("missing") ? "M00" : scenario.equals("ReadUniqueNamed")
                                    ? "D02" : "PERSISTENCE_NOT_UNIQUE", result.code());
                        }
                        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM MARKERS WHERE receipt='" + marker + "'")) {
                            assertTrue(rows.next());
                            assertEquals(key.equals("B") ? 1 : 0, rows.getInt(1));
                        }
                    }
                }
            }
        }

        private static com.fasterxml.jackson.databind.JsonNode execute(LocalGeneratedServiceRuntime runtime,
                ObjectMapper mapper, String name, Map<String, ?> input) throws Exception {
            var request = mapper.convertValue(input, Class.forName(MODEL + name + "Request"));
            runtime.executeGeneratedService("reads." + Character.toLowerCase(name.charAt(0)) + name.substring(1), request);
            return mapper.valueToTree(request);
        }
    }
}
