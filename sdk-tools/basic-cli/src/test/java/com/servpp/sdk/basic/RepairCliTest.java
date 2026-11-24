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
                Role Item { String note; }
                atomic calculate clear(Item item) { /*😀*/ item.note = ""; }
                Scenario serial preview(Item item) { clear(item); }
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
