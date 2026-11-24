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
                Role Item { String code; int edition; String title; }
                Role Selection { String wanted; }
                Role Release { int edition; }
                take Selection, Release as Item { String code = wanted; mapsame; }
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
        }

        private static com.fasterxml.jackson.databind.JsonNode execute(LocalGeneratedServiceRuntime runtime,
                ObjectMapper mapper, String name, Map<String, ?> input) throws Exception {
            var request = mapper.convertValue(input, Class.forName(MODEL + name + "Request"));
            runtime.executeGeneratedService("reads." + Character.toLowerCase(name.charAt(0)) + name.substring(1), request);
            return mapper.valueToTree(request);
        }
    }
}
