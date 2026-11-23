package example.technical;

import com.servpp.generator.GeneratorUtil;
import com.servpp.sdk.JavaProjectWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class TransactionCompositionTest {
    @Parameterized.Parameters(name = "{0}")
    public static List<String> variants() {
        return List.of("flat", "interleaved", "nested", "serial");
    }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private final String variant;

    public TransactionCompositionTest(String variant) {
        this.variant = variant;
    }

    @Test
    public void generatedScenarioCommitsAndRollsBackOnH2() throws Exception {
        Path fixtures = Path.of("src/test/composition");
        Path project = temporary.newFolder().toPath();
        Path sources = project.resolve("src/main/java");
        Path openApi = project.resolve("openapi");
        String previousEnvironment = System.getProperty("spp.environment");
        System.setProperty("spp.environment", "testing");
        try {
            GeneratorUtil.execute(fixtures.resolve(variant).toFile(), openApi.toFile(), sources.toFile(), "com.example.cv1");
            new JavaProjectWriter().write(project, "com.example.cv1");
        } finally {
            if (previousEnvironment == null) System.clearProperty("spp.environment");
            else System.setProperty("spp.environment", previousEnvironment);
        }

        Path classes = Files.createDirectory(project.resolve("classes"));
        String testClasspath = System.getProperty("surefire.test.class.path");
        var options = new ArrayList<>(List.of("-classpath", testClasspath,
                "-d", classes.toString(), fixtures.resolve("ComposeTest.java").toString()));
        try (var files = Files.walk(sources)) {
            files.filter(path -> path.toString().endsWith(".java")).map(Path::toString).sorted().forEach(options::add);
        }
        assertEquals("Generated Java compilation for " + variant, 0, ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, options.toArray(String[]::new)));
        // Each generated application owns the APP catalogs and the runtime singletons.
        String classpath = String.join(File.pathSeparator, classes.toString(),
                project.resolve("src/main/resources").toString(), testClasspath);
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-classpath", classpath, "com.example.cv1.ComposeTest", variant).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("Generated H2 application for " + variant + "\n" + output, 0, process.waitFor());
    }
}
