package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exports declared example facts. Business descriptions and expectations remain source data. */
public final class ExampleEvidenceWriter {
    private static final ObjectMapper FACTS = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .disable(MapperFeature.AUTO_DETECT_FIELDS, MapperFeature.AUTO_DETECT_GETTERS,
                    MapperFeature.AUTO_DETECT_IS_GETTERS);
    private ExampleEvidenceWriter() {}

    public static void write(Path directory, String service, Object participants, Object result,
                             Throwable failure) throws IOException {
        if (service == null || !service.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("Invalid example evidence service name: " + service);
        }
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("service", service);
        evidence.put("status", failure == null ? "PASS" : "FAIL");
        evidence.put("participants", participants);
        evidence.put("result", result);
        evidence.put("failure", failure == null ? null : failure(failure, new IdentityHashMap<>()));
        // Jackson's source @JsonProperty annotations select fields; runtime helper getters are excluded.
        String json = FACTS.writerWithDefaultPrettyPrinter().writeValueAsString(evidence);
        StringBuilder markdown = new StringBuilder("# ").append(service).append("\n\n")
                .append(failure == null ? "PASS" : "FAIL").append("\n\n");
        render(FACTS.readTree(json), "", markdown);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(service + ".json"), json + "\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(service + ".md"), markdown.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> failure(Throwable error, IdentityHashMap<Throwable, Boolean> visited) {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("type", error.getClass().getName());
        facts.put("message", error.getMessage());
        if (visited.put(error, Boolean.TRUE) != null) {
            facts.put("repeatedReference", true);
            return facts;
        }
        var suppressed = new ArrayList<Map<String, Object>>();
        for (Throwable additional : error.getSuppressed()) { suppressed.add(failure(additional, visited)); }
        facts.put("suppressed", suppressed);
        facts.put("cause", error.getCause() == null ? null : failure(error.getCause(), visited));
        return facts;
    }

    private static void render(JsonNode node, String path, StringBuilder output) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> render(field.getValue(),
                    path.isEmpty() ? field.getKey() : path + "." + field.getKey(), output));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) { render(node.get(index), path + "[" + index + "]", output); }
        } else {
            String value = node.isTextual() ? node.textValue() : node.toString();
            output.append("- **").append(escape(path)).append("**: ").append(escape(value)).append("\n");
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("*", "\\*").replace("`", "\\`")
                .replace("[", "\\[").replace("]", "\\]").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\r", "").replace("\n", "<br>");
    }
}
