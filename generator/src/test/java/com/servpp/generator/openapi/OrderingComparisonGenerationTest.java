package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OrderingComparisonGenerationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void binaryOrdering() throws Exception { generate("binary"); }
    @Test public void ebcdicOrdering() throws Exception { generate("ebcdic"); }

    private void generate(String collation) throws Exception {
        var root = temporary.newFolder().toPath();
        var source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("ordering.spp"), """
                domain ordering;
                Enum Priority { LOW:"Z", HIGH:"A" }
                Enum State { FIRST:"ZZ", UNKNOWN:other, LAST:"AA" }
                Role Row {
                    String code;
                    String missing;
                    State state;
                    boolean ordered;
                    boolean serviceOrder;
                    boolean scenarioOrder;
                }
                atomic calculate classify(Row row) {
                    row.serviceOrder = "1" < "A";
                    row.ordered = "AB" <= "AB " && "AB" >= "AB " && "AB" != "AB "
                        && "01" < "02" && row.code > BLANK && (BLANK) <= row.missing
                        && BLANK >= BLANK && Priority.LOW < Priority.HIGH
                        && State.FIRST < row.state && row.state <= State.LAST
                        && "🌱" > "�" && "🌱" > "9" && "�" > "9";
                }
                atomic calculate mark(Row row) { row.scenarioOrder = true; }
                readonly Scenario serial run(Row row) {
                    classify(row);
                    if ("1" < "A") { mark(row); }
                }
                """);
        Files.writeString(source.resolve("ordering.app"), """
                domain ordering;
                application rankings { deploy ordering; }
                blank { read: null, ""; write: null; }
                """ + (collation.equals("ebcdic") ? "collation ebcdic;" : ""));
        GeneratedDatabaseFixture.run(root, source, "ordering", Fixture.class, collation);
    }

    public static class Fixture {
        public static void main(String[] args) throws Exception {
            var source = new com.servpp.service.jdbc.DriverManagerJdbcDataSource("org.h2.Driver",
                    "jdbc:h2:mem:ordering;DB_CLOSE_DELAY=-1", null, null);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "rankings");
            var requestType = Class.forName("generated.ordering.model.RunRequest");
            var row = new HashMap<String, Object>();
            row.put("code", "A");
            row.put("state", "unmapped");
            row.put("missing", null);
            row.put("scenarioOrder", false);
            var request = mapper.convertValue(Map.of("row", row), requestType);
            runtime.executeGeneratedService("ordering.run", request);
            var result = mapper.valueToTree(request).path("row");
            assertTrue(result.toString(), result.path("ordered").asBoolean());
            assertEquals(args[0].equals("binary"), result.path("serviceOrder").asBoolean());
            assertEquals(args[0].equals("binary"), result.path("scenarioOrder").asBoolean());
        }
    }
}
