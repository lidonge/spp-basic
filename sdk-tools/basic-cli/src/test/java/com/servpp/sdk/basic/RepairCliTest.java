package com.servpp.sdk.basic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class RepairCliTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private record Output(int status, String stdout) { }
    private Output cli(String... args) throws Exception {
        var cmd = new java.util.ArrayList<>(java.util.List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Main.class.getName()));
        java.util.Collections.addAll(cmd, args);
        var p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var text = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Output(p.waitFor(), text);
    }

    @Test public void jsonLinesSafeFixAndExplain() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path file = root.resolve("entry.spp");
        String initial = """
                domain shop;
                Role Item short item { String note; }
                atomic calculate clear(Item) { /*😀*/ item.note = ""; }
                Scenario serial preview(Item) { clear(item); }
                """;
        Files.writeString(file, initial);
        Files.writeString(root.resolve("entry.app"), """
                domain shop;
                system services { idempotency: false; duplicateGuard: false; partialUpdate: false; propertyTracking: false; }
                """);
        var result = cli("check", "--source", root.toString(), "--format", "json");
        assertEquals(result.stdout, 0, result.status);
        String previous = "";
        int count = 0;
        var json = new ObjectMapper();
        var schema = com.github.fge.jsonschema.main.JsonSchemaFactory.byDefault().getJsonSchema(
                json.readTree(getClass().getResource("/diagnostic.schema.json")));
        for (String line : result.stdout.lines().toList()) {
            JsonNode diagnostic = json.readTree(line);
            var validation = schema.validate(diagnostic);
            assertTrue(validation.toString(), validation.isSuccess());
            for (String key : new String[]{"code","severity","file","line","column","cause","message","fixHint","relatedLocations","count"})
                assertTrue(key, diagnostic.has(key));
            var code = diagnostic.get("code").asText();
            assertTrue(previous.compareTo(code) <= 0);
            previous = code;
            assertEquals("entry.spp", diagnostic.get("file").asText());
            assertTrue(diagnostic.path("safe").asBoolean());
            if (code.endsWith("PADDED_BLANK")) {
                var edit = diagnostic.path("edits").get(0);
                assertEquals("\"\"", initial.substring(edit.get("start").asInt(), edit.get("end").asInt()));
            }
            count++;
        }
        assertEquals(2, count);
        assertEquals(0, cli("fix", "--source", root.toString(), "--code", "SPP_REDUNDANT_PADDED_BLANK").status);
        assertEquals(initial.replace("= \"\"", "= BLANK"), Files.readString(file));
        assertEquals(0, cli("fix", "--source", root.toString(), "--all-safe").status);
        assertTrue(Files.readString(file).contains("readonly Scenario"));
        var clean = cli("check", "--source", root.toString(), "--format", "json");
        assertEquals(0, clean.status);
        assertEquals("", clean.stdout);
        var again = cli("fix", "--source", root.toString(), "--all-safe");
        assertEquals(0, json.readTree(again.stdout).path("result").path("applied").intValue());
        var page = cli("explain", "SPP_REDUNDANT_PADDED_BLANK");
        assertEquals(0, page.status);
        assertTrue(page.stdout.contains("BLANK") && page.stdout.contains("Right"));
    }

    @Test public void reportsAndExplainsEnumOrdering() throws Exception {
        var page = cli("explain", "SPP_ENUM_ORDER_COMPARE");
        assertEquals(0, page.status);
        assertTrue(page.stdout, page.stdout.contains("declaration order"));

        Path root = temporary.newFolder().toPath();
        Files.writeString(root.resolve("entry.spp"), """
                domain shop;
                Enum Priority { LOW:"Z", HIGH:"A" }
                Role Item short item { boolean ordered; }
                atomic calculate rank(Item) { item.ordered = Priority.LOW < Priority.HIGH; }
                readonly Scenario serial preview(Item) { rank(item); }
                """);
        var result = cli("check", "--source", root.toString(), "--format", "json");
        assertEquals(result.stdout, 0, result.status);
        var json = new ObjectMapper();
        var diagnostics = new java.util.ArrayList<JsonNode>();
        for (String line : result.stdout.lines().toList()) {
            var entry = json.readTree(line);
            if (entry.path("code").asText().equals("SPP_ENUM_ORDER_COMPARE")) diagnostics.add(entry);
        }
        assertEquals(result.stdout, 1, diagnostics.size());
        var diagnostic = diagnostics.get(0);
        var schema = com.github.fge.jsonschema.main.JsonSchemaFactory.byDefault().getJsonSchema(
                json.readTree(getClass().getResource("/diagnostic.schema.json")));
        var validation = schema.validate(diagnostic);
        assertTrue(validation.toString(), validation.isSuccess());
        assertEquals("info", diagnostic.path("severity").asText());
        assertEquals("entry.spp", diagnostic.path("file").asText());
    }

    @Test public void validationReportPreservesLocatedAndUnlocatedErrors() throws Exception {
        Path root = temporary.newFolder().toPath();
        var report = new DiagnosticReport(root);
        report.failed(new IllegalArgumentException("Validation failed:\n"
                + root.resolve("shop.spp") + ":4:2 First error\nUnknown participant elsewhere"));
        Path json = root.resolve("report.json");
        report.write(json);
        var rows = new ObjectMapper().readTree(json.toFile()).get("diagnostics");
        assertEquals(2, rows.size());
        assertEquals("Unknown participant elsewhere", rows.get(0).get("message").asText());
        assertEquals("shop.spp", rows.get(1).get("file").asText());
    }

    @Test public void syntaxFailureCarriesOriginalLocation() throws Exception {
        Path root = temporary.newFolder().toPath();
        Files.writeString(root.resolve("broken.spp"), "domain shop;\nRole Item { int ; }\n");
        var failure = cli("check", "--source", root.toString(), "--format", "json");
        assertNotEquals(0, failure.status);
        var first = new ObjectMapper().readTree(failure.stdout.lines().findFirst().orElseThrow());
        assertEquals("error", first.path("severity").asText());
        assertEquals("broken.spp", first.path("file").asText());
        assertEquals(2, first.path("line").intValue());
    }
}
