package com.servpp.sdk.basic;

import java.nio.file.Files;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class RedundancyCliTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void strictPromotesWarningsBeforePublishingOutput() throws Exception {
        var source = temporary.newFolder().toPath();
        Files.writeString(source.resolve("shop.spp"), """
                domain shop;
                Role Input { String name; }
                Role Duplicate { String name; }
                """);
        assertEquals(true, Main.run(new String[]{"check", "--source", source.toString()}).get("checked"));
        var output = temporary.getRoot().toPath().resolve("strict-output");
        assertThrows(MojoExecutionException.class, () -> Main.run(new String[]{"generate", "--strict",
                "--source", source.toString(), "--output", output.toString()}));
        assertFalse(Files.exists(output));
        Files.writeString(source.resolve("shop.spp"), "domain shop; Role Input { String name; }");
        assertEquals(true, Main.run(new String[]{"check", "--strict", "--source", source.toString()}).get("checked"));
    }
}
