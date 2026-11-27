package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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
                Role Item short item, matches { String code; int edition; String title; }
                Role Selection short selection { String wanted; }
                Role Release { int edition; }
                take Selection, Release as Item { String code = wanted; mapsame; }
                Entity Stock short item { String code; int edition; String title; }
                Role Lookup short selection { String wanted; }
                take Stock as Lookup { String wanted = code; }
                Enum ReadError { DUPLICATE:"D02", MISSING:"M00" }
                Contract Marker short marker { String receipt; }
                atomic check checkItem(Stock) { require(item, ReadError.MISSING); }
                Scenario serial readUnique(Stock, Lookup, Marker) {
                    transaction {
                        unique findStock by selection;
                        checkItem,breakTransaction;
                        insertMarker;
                    }
                }
                atomic check checkRole(Item) { require(item, ReadError.MISSING); }
                Scenario serial readUniqueNamed(Item, Selection, Marker) {
                    transaction {
                        unique findItem by selection else ReadError.DUPLICATE;
                        checkRole,breakTransaction;
                        insertMarker;
                    }
                }
                const int CURRENT = 3;
                atomic query listItems(key Item, Item[]);
                Scenario serial readSelected(Item, Selection, Release) {
                    findItem by Selection, Release;
                }
                Scenario serial readDefault(Item) {
                    findItem by Selection.wanted = "A", item.edition = CURRENT;
                }
                Scenario serial readList(Item, Item[]) {
                    listItems(item, matches) by Selection.wanted = "A", item.edition = CURRENT;
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
        GeneratedDatabaseFixture.run(root, source, "reads", DatabaseFixture.class, "");
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
            var selected = execute(runtime, mapper, "ReadSelected", Map.of("item", stale,
                    "selection", Map.of("wanted", "A"), "release", Map.of("edition", 3))).get("item");
            assertEquals(Map.of("code", "A", "edition", 3, "title", "selected"),
                    mapper.convertValue(selected, Map.class));
            var defaults = execute(runtime, mapper, "ReadDefault", Map.of("item", stale)).get("item");
            assertEquals(selected, defaults);
            var missing = execute(runtime, mapper, "ReadSelected", Map.of("item", stale,
                    "selection", Map.of("wanted", "missing"), "release", Map.of("edition", 3))).get("item");
            assertEquals("missing", missing.get("code").asText());
            assertEquals(3, missing.get("edition").asInt());
            var list = execute(runtime, mapper, "ReadList", Map.of("item", stale, "matches", List.of()));
            assertEquals("A", list.get("item").get("code").asText());
            assertEquals(3, list.get("item").get("edition").asInt());
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
