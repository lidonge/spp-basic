package com.servpp.sdk.editor;

import com.google.gson.GsonBuilder;
import org.eclipse.lsp4j.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class EditorBenchmark {
    private EditorBenchmark() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Supply a new benchmark JSON output path.");
        Path output = Path.of(arguments[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Benchmark output must not already exist.");
        Path workspace = Files.createTempDirectory("spp-editor-benchmark-");
        try {
            Path source = workspace.resolve("src/main/spp");
            Files.createDirectories(source);
            for (int file = 0; file < 1000; file++) Files.writeString(source.resolve("facts-" + file + ".spp"), fixture(file));
            try (var server = new EditorServer()) {
                var initialize = new InitializeParams();
                initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(workspace.toUri().toString(), "benchmark")));
                long started = System.nanoTime();
                server.initialize(initialize).get(120, TimeUnit.SECONDS);
                definition(server, source, 0);
                double coldMillis = (System.nanoTime() - started) / 1_000_000.0;
                List<Double> definitions = new ArrayList<>();
                List<Double> hovers = new ArrayList<>();
                for (int sample = 0; sample < 200; sample++) {
                    int file = sample * 37 % 1000;
                    started = System.nanoTime();
                    definition(server, source, file);
                    definitions.add((System.nanoTime() - started) / 1_000_000.0);
                    started = System.nanoTime();
                    var hover = server.hover(new HoverParams(identifier(source, file), new Position(54, 10)))
                            .get(10, TimeUnit.SECONDS);
                    if (!hover.getContents().getRight().getValue().contains("int value0"))
                        throw new AssertionError("Benchmark hover returned an incorrect result.");
                    hovers.add((System.nanoTime() - started) / 1_000_000.0);
                }
                var result = new LinkedHashMap<String, Object>();
                result.put("measurement", "Java LSP handlers including workspace index; excludes network authorization and VS Code transport/rendering");
                result.put("files", 1000);
                result.put("lines", 100000);
                result.put("samplesPerOperation", 200);
                result.put("java", System.getProperty("java.version"));
                result.put("os", System.getProperty("os.name"));
                result.put("processors", Runtime.getRuntime().availableProcessors());
                result.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
                result.put("coldIndexAndFirstDefinitionMillis", coldMillis);
                result.put("definitionP50Millis", percentile(definitions, 0.50));
                result.put("definitionP95Millis", percentile(definitions, 0.95));
                result.put("hoverP50Millis", percentile(hovers, 0.50));
                result.put("hoverP95Millis", percentile(hovers, 0.95));
                result.put("coldWithinTarget", coldMillis <= 10000);
                result.put("warmWithinTarget", percentile(definitions, 0.95) <= 200 && percentile(hovers, 0.95) <= 200);
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(result);
                Files.writeString(output, json + "\n", StandardOpenOption.CREATE_NEW);
                System.out.println(json);
            }
        } finally {
            try (var paths = Files.walk(workspace)) {
                for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static TextDocumentIdentifier identifier(Path source, int file) {
        return new TextDocumentIdentifier(source.resolve("facts-" + file + ".spp").toUri().toString());
    }

    private static void definition(EditorServer server, Path source, int file) throws Exception {
        var locations = server.definition(new DefinitionParams(identifier(source, file), new Position(54, 10)))
                .get(10, TimeUnit.SECONDS).getRight();
        if (locations.size() != 1 || !locations.get(0).getTargetSelectionRange().getStart().equals(new Position(2, 8)))
            throw new AssertionError("Benchmark definition returned an incorrect result.");
    }

    private static double percentile(List<Double> samples, double quantile) {
        return samples.stream().sorted().toList().get((int) Math.ceil(samples.size() * quantile) - 1);
    }

    private static String fixture(int file) {
        StringBuilder source = new StringBuilder("domain bench;\nRole Facts" + file + " {\n");
        for (int field = 0; field < 50; field++) source.append("    int value").append(field).append(";\n");
        source.append("}\natomic calculate compute").append(file).append("(Facts").append(file).append(" facts) {\n");
        for (int statement = 0; statement < 40; statement++) source.append("    facts.value").append(statement).append(" += 1;\n");
        source.append("}\nScenario serial preview").append(file).append("(Facts").append(file).append(" facts) {\n    compute")
                .append(file).append(";\n}\n// benchmark fixture\n// end\n");
        return source.toString();
    }
}
