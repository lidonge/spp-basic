package com.servpp.generator.openapi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.servpp.generator.GeneratorUtil;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.Test;

/** Equal sources generate byte-identical files, so jars are reproducible. */
public class DeterministicGenerationTest {
    private static Path generate(String label) throws Exception {
        Path source = Files.createTempDirectory("spp-determinism-source-" + label);
        Path root = Files.createTempDirectory("spp-determinism-generated-" + label);
        Files.writeString(source.resolve("shop.spp"), """
                domain shop;

                Entity Item{
                    String code;
                    String name;
                }
                """);
        Files.writeString(source.resolve("shop.app"), """
                domain shop;

                tables entities {
                    table ITEMS(Item) { primarykey(Item.code); }
                }
                """);
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(),
                "example");
        return root;
    }

    private static TreeMap<String, byte[]> snapshot(Path root) throws Exception {
        TreeMap<String, byte[]> files = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                files.put(root.relativize(path).toString(), Files.readAllBytes(path));
            }
        }
        return files;
    }

    @Test
    public void generatedFilesAreByteStableAcrossRunsAndCarryNoDateStamp() throws Exception {
        Path first = generate("a");
        Thread.sleep(1100); // Properties.store stamps the second
        Path second = generate("b");

        TreeMap<String, byte[]> one = snapshot(first);
        TreeMap<String, byte[]> two = snapshot(second);
        assertEquals(one.keySet(), two.keySet());
        int properties = 0;
        for (var entry : one.entrySet()) {
            if (entry.getKey().endsWith(".yaml") && new String(entry.getValue(), StandardCharsets.UTF_8)
                    .contains(first.toString())) {
                continue; // an absolute temp path is the only legitimate difference
            }
            assertArrayEquals(entry.getKey(), entry.getValue(), two.get(entry.getKey()));
            if (entry.getKey().endsWith(".properties")) {
                properties++;
                String text = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
                assertFalse(entry.getKey() + ": " + text, text.lines()
                        .anyMatch(line -> line.matches("#\\w{3} \\w{3} \\d{2} .*\\d{4}")));
            }
        }
        assertTrue("expected generated .properties files", properties > 0);
    }

    @Test
    public void storeStableKeepsEverythingButTheDateLine() throws Exception {
        Properties values = new Properties();
        values.setProperty("b", "2");
        values.setProperty("a", "café");
        ByteArrayOutputStream stable = new ByteArrayOutputStream();
        GeneratorUtil.storeStable(values, stable, "Generated test");
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        values.store(plain, "Generated test");

        List<String> expected = plain.toString(StandardCharsets.ISO_8859_1).lines().toList();
        List<String> actual = stable.toString(StandardCharsets.ISO_8859_1).lines().toList();
        assertEquals(expected.size() - 1, actual.size());
        assertEquals(expected.get(0), actual.get(0));
        assertEquals(expected.subList(2, expected.size()), actual.subList(1, actual.size()));
        Properties reloaded = new Properties();
        reloaded.load(new java.io.ByteArrayInputStream(stable.toByteArray()));
        assertEquals(values, reloaded);
    }
}
