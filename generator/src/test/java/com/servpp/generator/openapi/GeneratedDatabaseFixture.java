package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import java.util.ArrayList;
import javax.tools.ToolProvider;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

final class GeneratedDatabaseFixture {
    static void run(Path root, Path source, String domain, Class<?> fixture, String variant) throws Exception {
        Path yaml = root.resolve("yaml");
        Path services = root.resolve("java");
        GeneratorUtil.execute(source.toFile(), yaml.toFile(), services.toFile(), "generated");
        Path client = root.resolve("client");
        var config = new CodegenConfigurator().setGeneratorName("spring")
                .setInputSpec(yaml.resolve(domain + ".spp.yaml").toString()).setOutputDir(client.toString())
                .setModelPackage("generated." + domain + ".model").setTemplateDir(yaml.resolve("JavaSpring").toString())
                .setValidateSpec(false).addAdditionalProperty("useSpringBoot3", true)
                .addAdditionalProperty("useBeanValidation", false).addAdditionalProperty("annotationLibrary", "none")
                .addAdditionalProperty("documentationProvider", "none").addAdditionalProperty("openApiNullable", false)
                .setGlobalProperties(Map.of("models", "", "modelTests", "false", "modelDocs", "false"));
        new DefaultGenerator().opts(config.toClientOptInput()).generate();

        Path classes = Files.createDirectory(root.resolve("classes"));
        String classpath = System.getProperty("java.class.path");
        var args = new ArrayList<>(List.of("-classpath", classpath, "-d", classes.toString()));
        for (Path generated : List.of(services, client.resolve("src/main/java"))) {
            try (var files = Files.walk(generated)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> args.add(path.toString()));
            }
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        // Generated service registration uses Class.forName against the application classpath.
        Path log = root.resolve("runtime.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", classpath + java.io.File.pathSeparator + classes + java.io.File.pathSeparator + root.resolve("resources"),
                fixture.getName(), variant).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            fail("Generated H2 fixture timed out: " + Files.readString(log));
        }
        int status = process.exitValue();
        assertEquals(Files.readString(log), 0, status);
    }

}
