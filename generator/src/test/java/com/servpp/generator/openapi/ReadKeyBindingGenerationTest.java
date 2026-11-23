package com.servpp.generator.openapi;

import com.servpp.lang.AppCompiler;
import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ReadKeyBindingGenerationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void generatedReadBindsCompositeKeysBeforeCallingQueryAndKeepsThemAfterwards() throws Exception {
        var spp = temporary.newFile("reads.spp");
        Files.writeString(spp.toPath(), """
                domain reads;
                Role Item { String code; String region; }
                Scenario serial read(Item item) {
                    findItem by item.code, item.region;
                }
                """);
        var app = temporary.newFile("reads.app");
        Files.writeString(app.toPath(), """
                domain reads;
                tables roles { table ITEMS(Item) { primarykey(Item.region, Item.code); } }
                scope { local {} }
                phy { repository records {
                    adapter jdbcObjectRepository;
                    config default = true;
                    config datasourceUrl = "jdbc:h2:mem:reads";
                    config driverClassName = "org.h2.Driver";
                } }
                """);
        var project = new SppProject();
        new SppCompiler(spp, project).compile();
        new AppCompiler(app, project).compile();
        var domain = project.getDomain("reads");
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        String body = ((SppService) domain.getSppClass("Read")).getScenarioBodyJava();
        var fixture = temporary.newFile("Fixture.java");
        Files.writeString(fixture.toPath(), """
                public class Fixture {
                    public static class Item {
                        private String code = "outdated", region = "origin";
                        public String getCode() { return code; }
                        public String getRegion() { return region; }
                        public void setCode(String code) { this.code = code; }
                        public void setRegion(String region) { this.region = region; }
                    }
                    private static final Fixture findItem = new Fixture();
                    private static String seen;
                    public void findItem(Item item) {
                        seen = item.getRegion() + "/" + item.getCode();
                        item.setCode("physical");
                    }
                    public static String run() {
                        Item item = new Item();
                        %s
                        return seen + ":" + item.getRegion() + "/" + item.getCode();
                    }
                }
                """.formatted(body));
        var classes = temporary.newFolder("classes");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", System.getProperty("java.class.path"), "-d", classes.getPath(), fixture.getPath()));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toURI().toURL()}, getClass().getClassLoader())) {
            assertEquals("outdated/origin:outdated/origin", loader.loadClass("Fixture").getMethod("run").invoke(null));
        }
    }
}
