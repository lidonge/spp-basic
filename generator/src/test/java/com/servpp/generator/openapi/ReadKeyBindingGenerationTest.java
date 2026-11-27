package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
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

    @Test public void generatedPlayedReadUsesTheProviderPrimaryKey() throws Exception {
        var source = temporary.newFolder("played-sources").toPath();
        Files.writeString(source.resolve("consumer.spp"), """
                domain consumer;
                Role Played short played { String accountCode; String currency; }
                Scenario serial readPlayed(Played) {
                    findPlayed by Played.accountCode = "sample", Played.currency = "01";
                }
                """);
        Files.writeString(source.resolve("consumer.app"), """
                domain consumer;
                application reads { environment develop {
                    deploy consumer; deploy provider;
                    transactions { mode: LOCAL; resource: records; }
                } }
                play Played by provider.Position { accountCode = account; currency = unit; }
                scope { local {} }
                """);
        Files.writeString(source.resolve("provider.spp"), """
                domain provider;
                Entity Position { String account; String unit; }
                """);
        Files.writeString(source.resolve("provider.app"), """
                domain provider;
                tables entities { table POSITIONS(Position) { primarykey(Position.unit, Position.account); } }
                """);
        Files.writeString(source.resolve("provider.phy"), """
                domain provider;
                phy {
                    repository data {
                        adapter jdbcObjectRepository;
                        config default = true;
                        config appDdlTables = true;
                        config datasourceUrl = "jdbc:h2:mem:played_reads";
                        config driverClassName = "org.h2.Driver";
                    }
                    backend database {
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:played_reads";
                        config driverClassName = "org.h2.Driver";
                    }
                }
                """);
        var output = temporary.newFolder("played-output").toPath();
        var javaOutput = output.resolve("java");
        GeneratorUtil.execute(source.toFile(), output.resolve("resources").toFile(), javaOutput.toFile(), "example");
        String scenario = Files.readString(javaOutput.resolve("example/consumer/service/SimpleReadPlayed.java"));
        int find = scenario.indexOf(".findPlayed(played)");
        assertTrue(scenario, find >= 0);
        String beforeFind = scenario.substring(0, find);
        assertTrue(beforeFind, beforeFind.contains("played.setAccountCode("));
        assertTrue(beforeFind, beforeFind.contains("played.setCurrency("));
        String query = Files.readString(javaOutput.resolve("example/consumer/service/SimpleFindPlayed.java"));
        assertTrue(query, query.contains("ParticipantRoleQueries.Play(\"provider.findPosition\", example.provider.model.Position.class"));
        assertTrue(query, query.contains("Map.entry(\"accountCode\", \"account\")"));
        assertTrue(query, query.contains("Map.entry(\"currency\", \"unit\")"));
        assertTrue(query, query.contains("java.util.List.of(\"currency\", \"accountCode\")"));
    }

    @Test public void generatedReadBindsCompositeKeysBeforeCallingQueryAndKeepsThemAfterwards() throws Exception {
        var spp = temporary.newFile("reads.spp");
        Files.writeString(spp.toPath(), """
                domain reads;
                Role Item short item { String code; String region; }
                Role Selection short selection { String wanted; }
                take Selection as Item { String code = wanted; }
                Scenario serial read(Item, Selection) {
                    findItem by Selection, Item.region = "destination";
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
                    public static class Selection {
                        public String getWanted() { return "selected"; }
                    }
                    private static final Fixture findItem = new Fixture();
                    private static String seen;
                    public void findItem(Item item) {
                        seen = item.getRegion() + "/" + item.getCode();
                        item.setCode("physical");
                    }
                    public static String run() {
                        Item item = new Item();
                        Selection selection = new Selection();
                        %s
                        return seen + ":" + item.getRegion() + "/" + item.getCode();
                    }
                }
                """.formatted(body));
        var classes = temporary.newFolder("classes");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", System.getProperty("java.class.path"), "-d", classes.getPath(), fixture.getPath()));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toURI().toURL()}, getClass().getClassLoader())) {
            assertEquals("destination/selected:destination/selected", loader.loadClass("Fixture").getMethod("run").invoke(null));
        }
    }
}
