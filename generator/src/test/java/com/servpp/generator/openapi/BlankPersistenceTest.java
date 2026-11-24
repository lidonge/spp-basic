package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BlankPersistenceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String URL = "jdbc:h2:mem:blanks;DB_CLOSE_DELAY=-1";

    private void generate(String canonical) throws Exception {
        Path root = temp.getRoot().toPath();
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("blanks.spp"), """
                domain blanks;
                Contract Receipt { String receiptNumber; String(8) note; boolean accepted; }
                Role Label { String(4) labelCode; String category; String description; }
                Scenario serial readLabel(Label label) { findLabel by Label.labelCode = BLANK, Label.category = "X"; }
                atomic calculate classify(Receipt receipt) {
                    receipt.accepted = receipt.note == BLANK;
                    receipt.note = BLANK;
                }
                atomic update storeReceipt(Receipt receipt);
                Scenario serial record(Receipt receipt) {
                    classify;
                    transaction { storeReceipt; }
                }
                """);
        Files.writeString(source.resolve("blanks.app"), """
                domain blanks;
                application receipts { deploy blanks; }
                blank { read: null, "", spaces; write: %s; }
                tables contracts { table RECEIPTS(Receipt) { primarykey(Receipt.receiptNumber); } }
                tables roles { table LABELS(Label) { primarykey(Label.labelCode, Label.category); } }
                scope { local {
                    persist(insert, argument(0, Receipt.receiptNumber)) storeReceipt;
                } }
                phy { repository records {
                    adapter jdbcObjectRepository;
                    config default = true;
                    config appDdlTables = true;
                    config datasourceUrl = "%s"; config driverClassName = "org.h2.Driver";
                } }
                """.formatted(canonical, URL));
        GeneratedDatabaseFixture.run(root, source, "blanks", DatabaseFixture.class, canonical);
    }

    @Test public void paddedDeployment() throws Exception { generate("spaces"); }
    @Test public void nullDeployment() throws Exception { generate("null"); }

    @Test public void emptyStringDeployment() throws Exception { generate("\"\""); }

    public static class DatabaseFixture {
        public static void main(String[] args) throws Exception {
            var source = new DriverManagerJdbcDataSource("org.h2.Driver", URL, null, null);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "receipts");
            var requestType = Class.forName("generated.blanks.model.RecordRequest");
            try (var connection = source.getConnection();
                 var query = connection.prepareStatement("SELECT note, accepted FROM RECEIPTS WHERE receiptNumber = ?")) {
                try (var seed = connection.createStatement()) {
                    seed.executeUpdate("INSERT INTO LABELS (labelCode, category, description) VALUES "
                            + "('    ', 'X', 'padded'), ('', 'X', 'empty')");
                }
                var readRequest = mapper.convertValue(Map.of("label", Map.of("category", "stale")),
                        Class.forName("generated.blanks.model.ReadLabelRequest"));
                runtime.executeGeneratedService("blanks.readLabel", readRequest);
                var label = mapper.valueToTree(readRequest).path("label");
                String expected = switch (args[0]) { case "null" -> null; case "spaces" -> "padded"; default -> "empty"; };
                assertEquals(expected, label.path("description").asText(null));
                var inputs = new String[] {null, "", "   ", "\t", " X "};
                for (int i = 0; i < inputs.length; i++) {
                    String id = "R" + i;
                    var data = new java.util.HashMap<String, Object>();
                    data.put("receiptNumber", id);
                    data.put("note", inputs[i]);
                    var request = mapper.convertValue(Map.of("receipt", data), requestType);
                    runtime.executeGeneratedService("blanks.record", request);
                    query.setString(1, id);
                    try (var row = query.executeQuery()) {
                        assertTrue("Missing " + id, row.next());
                        assertEquals(i < 3, row.getBoolean("accepted"));
                        String canonical = switch (args[0]) { case "null" -> null; case "spaces" -> " ".repeat(8); default -> ""; };
                        assertEquals(canonical, row.getString("note"));
                    }
                }
            }
        }
    }
}
