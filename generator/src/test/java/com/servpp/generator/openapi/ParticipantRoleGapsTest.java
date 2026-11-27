package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ParticipantRoleGapsTest {
    private static final String JDBC = "jdbc:h2:mem:role_gaps;DB_CLOSE_DELAY=-1";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void generatedRoleReadsWritesAndCollectionsUseThePlayerRows() throws Exception {
        var root = temporary.getRoot().toPath();
        var source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("01-services.spp"), """
                domain catalog;
                atomic calculate countStock(Stock) { inventory.quantity += 1; }
                atomic calculate arrange(Incoming[], Outgoing[]) {
                    outgoingLots = incomingLots;
                    for (Stock member : outgoingLots) { member.quantity += 1; }
                    outgoingLots = append(outgoingLots, new Incoming());
                }
                atomic insert createStock(Stock);
                atomic query lookup(Stock, Incoming);
                atomic query lookupAll(Selection, Incoming[]);
                Scenario revise(Change, Incoming, Selection) {
                    transaction {
                        findIncoming by selection;
                        countStock(incoming);
                        updateStock(incoming);
                    }
                }
                Scenario revisePlayed(Change, Outgoing) {
                    transaction {
                        findOutgoing;
                        countStock(outgoing);
                        updateStock(outgoing);
                    }
                }
                Scenario view(Stock, Selection, Incoming, Incoming[]) {
                    lookup(inventory, incoming);
                    lookupAll(selection, incomingLots);
                }
                Scenario addStocks(Change, Stock) { transaction { createStock(inventory); } }
                Scenario addLots(Change, Incoming[]) { transaction { insertStock(incomingLots); } }
                Scenario removeLots(Change, Incoming[]) { transaction { deleteStock(incomingLots); } }
                Scenario orderLots(Incoming[], Outgoing[]) { arrange(incomingLots, outgoingLots); }
                """);
        Files.writeString(source.resolve("99-types.spp"), """
                domain catalog;
                Contract Change { String reason; }
                Role StockFacts { int quantity; }
                Entity Stock short inventory { String sku; String label; }
                take StockFacts as Stock { takeall; }
                Role Incoming short incoming, incomingLots;
                take Stock as Incoming { String chosenSku = sku; }
                Role Outgoing short outgoing, outgoingLots;
                Role Selection;
                take Stock as Selection { String requestedSku = sku; }
                """);
        Files.writeString(source.resolve("catalog.app"), """
                domain catalog;
                application catalogApp { deploy catalog; }
                transactions { default at resource records; }
                play Outgoing by catalog.Stock { outgoingSku = sku; }
                tables entities { table STOCK(Stock) { primarykey(Stock.sku); index bySku(Stock.sku); } }
                scope { local {
                    repository(argument(0, Stock.sku), incoming) lookup;
                    repository(Stock by index bySku) lookupAll;
                } }
                phy { repository records {
                    adapter jdbcObjectRepository;
                    config default = true; config appDdlTables = true;
                    config persistenceMode = LOCAL; config persistenceResource = records;
                    config datasourceUrl = "%s"; config driverClassName = "org.h2.Driver";
                } }
                """.formatted(JDBC));
        GeneratedDatabaseFixture.run(root, source, "catalog", DatabaseFixture.class, "");
    }

    @Test public void ambiguousOrUnrelatedRepositoryDestinationsFailGeneration() throws Exception {
        for (boolean ambiguous : List.of(false, true)) {
            var root = temporary.newFolder().toPath();
            var source = Files.createDirectory(root.resolve("source"));
            Files.writeString(source.resolve("queries.spp"), """
                    domain stockroom;
                    Entity Stock { String sku; }
                    Role Upload { String text; }
                    Role SelectedStock;
                    take Stock as SelectedStock;
                    Role ReservedStock;
                    take Stock as ReservedStock;
                    atomic query load(Stock, %s);
                    """.formatted(ambiguous ? "SelectedStock[], ReservedStock[]" : "Upload"));
            Files.writeString(source.resolve("queries.app"), """
                    domain stockroom;
                    tables entities { table STOCK(Stock) { primarykey(Stock.sku); index bySku(Stock.sku); } }
                    scope { local { %s load; } }
                    phy { repository records { adapter jdbcObjectRepository; config default = true; } }
                    """.formatted(ambiguous ? "repository(Stock by index bySku)" : "repository(argument(0, Stock.sku), upload)"));
            var exception = assertThrows(org.apache.maven.plugin.MojoFailureException.class,
                    () -> com.servpp.generator.GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(),
                            root.resolve("java").toFile(), "generated"));
            assertTrue(exception.getMessage(), exception.getMessage().contains(ambiguous
                    ? "result collection is ambiguous" : "must have participant type Stock"));
        }
    }

    public static class DatabaseFixture {
        public static void main(String[] args) throws Exception {
            var dataSource = new DriverManagerJdbcDataSource("org.h2.Driver", JDBC, null, null);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(dataSource, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "catalogApp");
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                assertEquals(1, statement.executeUpdate("INSERT INTO STOCK (sku, label, quantity) VALUES ('A', 'alpha', 7)"));
                execute(runtime, mapper, "Revise", Map.of("change", Map.of(), "incoming", Map.of(), "selection", Map.of("requestedSku", "A")));
                execute(runtime, mapper, "RevisePlayed", Map.of("change", Map.of(), "outgoing", Map.of("outgoingSku", "A")));
                try (var rows = statement.executeQuery("SELECT quantity FROM STOCK WHERE sku='A'")) {
                    assertTrue(rows.next());
                    assertEquals(9, rows.getInt(1));
                }
                execute(runtime, mapper, "AddStocks", Map.of("change", Map.of(), "inventory", Map.of("sku", "B", "label", "beta", "quantity", 2)));
                try (var rows = statement.executeQuery("SELECT quantity FROM STOCK WHERE sku='B'")) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                }
                var incomingLots = Map.of("change", Map.of(), "incomingLots",
                        List.of(Map.of("chosenSku", "C", "label", "gamma", "quantity", 3)));
                execute(runtime, mapper, "AddLots", incomingLots);
                try (var rows = statement.executeQuery("SELECT quantity FROM STOCK WHERE sku='C'")) {
                    assertTrue(rows.next());
                    assertEquals(3, rows.getInt(1));
                }
                execute(runtime, mapper, "RemoveLots", incomingLots);
                try (var rows = statement.executeQuery("SELECT quantity FROM STOCK WHERE sku='C'")) {
                    assertFalse(rows.next());
                }
                var read = execute(runtime, mapper, "View", Map.of("inventory", Map.of("sku", "A"), "selection", Map.of("requestedSku", "A"),
                        "incoming", Map.of(), "incomingLots", List.of()));
                assertTrue(read.has("selection"));
                assertFalse(read.has("Selection"));
                assertEquals("alpha", read.path("incoming").path("label").asText());
                assertEquals("A", read.path("incomingLots").path(0).path("chosenSku").asText());
                var shaped = execute(runtime, mapper, "OrderLots", Map.of("incomingLots",
                        List.of(Map.of("chosenSku", "A", "label", "alpha", "quantity", 9)), "outgoingLots", List.of()));
                assertEquals(2, shaped.path("outgoingLots").size());
                assertEquals("A", shaped.path("outgoingLots").path(0).path("outgoingSku").asText());
                assertEquals(10, shaped.path("outgoingLots").path(0).path("quantity").asInt());
            }
        }

        private static com.fasterxml.jackson.databind.JsonNode execute(LocalGeneratedServiceRuntime runtime,
                ObjectMapper mapper, String name, Map<String, ?> input) throws Exception {
            var request = mapper.convertValue(input, Class.forName("generated.catalog.model." + name + "Request"));
            runtime.executeGeneratedService("catalog." + Character.toLowerCase(name.charAt(0)) + name.substring(1), request);
            return mapper.valueToTree(request);
        }
    }
}
