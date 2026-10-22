package com.servpp.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

public final class JavaProjectWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record RuntimeDependency(String groupId, String artifactId, String version) {
        public RuntimeDependency {
            for (String value : new String[]{groupId, artifactId, version})
                if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
                    throw new IllegalArgumentException("Invalid runtime dependency coordinate.");
        }
    }

    public void write(Path root, String basePackage) throws IOException {
        write(root, basePackage, java.util.List.of());
    }

    /** The selected edition or third-party distribution supplies its runtime dependencies. */
    public void write(Path root, String basePackage, java.util.List<RuntimeDependency> runtimeDependencies) throws IOException {
        runtimeDependencies = java.util.List.copyOf(runtimeDependencies);
        if (!isValidPackage(basePackage))
            throw new IOException("Invalid generated Java package.");
        Path specifications = root.resolve("openapi");
        java.util.List<Path> inputs;
        try (var files = Files.list(specifications)) {
            inputs = files.filter(file -> file.getFileName().toString().endsWith(".spp.yaml")).sorted().toList();
        }
        if (inputs.isEmpty()) throw new IOException("No generated S++ OpenAPI definitions.");
        var domains = new LinkedHashSet<String>();
        for (Path input : inputs) {
            OpenAPI api = Yaml.mapper().readValue(input.toFile(), OpenAPI.class);
            String title = api.getInfo() == null ? "" : api.getInfo().getTitle();
            String domain = title != null && title.startsWith("OpenAPI ") ? title.substring(8) : "";
            if (!javax.lang.model.SourceVersion.isIdentifier(domain)
                    || javax.lang.model.SourceVersion.isKeyword(domain, javax.lang.model.SourceVersion.RELEASE_17)
                    || !domains.add(domain))
                throw new IOException("Invalid or duplicate S++ domain in generated OpenAPI.");
            Path numeric = specifications.resolve(domain + ".numeric-codegen.json");
            CodegenConfigurator configuration = Files.isRegularFile(numeric)
                    ? CodegenConfigurator.fromFile(numeric.toString()) : new CodegenConfigurator();
            if (configuration == null) throw new IOException("Cannot read generated numeric codegen configuration.");
            configuration.setGeneratorName("spring").setInputSpec(input.toString()).setOutputDir(root.toString())
                    .setApiPackage(basePackage + "." + domain + ".handler")
                    .setModelPackage(basePackage + "." + domain + ".model")
                    .setInvokerPackage(basePackage + "." + domain + ".handler")
                    .setValidateSpec(false)
                    .addAdditionalProperty("useSpringBoot3", true)
                    .addAdditionalProperty("useTags", true)
                    .addAdditionalProperty("hideGenerationTimestamp", true)
                    .setGlobalProperties(Map.of("apis", "", "models", "", "supportingFiles", "ApiUtil.java",
                            "apiTests", "false", "modelTests", "false", "apiDocs", "false", "modelDocs", "false"));
            Path templates = specifications.resolve("JavaSpring");
            if (Files.isDirectory(templates)) configuration.setTemplateDir(templates.toString());
            var generator = new DefaultGenerator();
            generator.setGenerateMetadata(false);
            generator.opts(configuration.toClientOptInput()).generate();
        }
        Files.writeString(root.resolve("pom.xml"), pom(basePackage, runtimeDependencies));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(root.resolve("spp-generation.json").toFile(),
                Map.of("schemaVersion", 1, "sdkVersion", "0.1.0", "openapiGeneratorVersion", "6.5.0",
                        "runtimeVersion", "1.0-SNAPSHOT", "basePackage", basePackage, "domains", domains, "runtimeExtensions", runtimeDependencies));
        Files.writeString(root.resolve("README.md"), """
                # Generated S++ Java project

                Java 17+ Maven library project with generated models, API handlers,
                S++ service implementations and runtime catalogs. Run `mvn verify`
                after installing the matching `com.servpp:appframe:1.0-SNAPSHOT`.

                The generated project does not depend on the CLI or an SDK subscription
                for compilation or execution. The reference runtime has its own license
                and is not a production-readiness guarantee. This is an integration
                library, not a configured production server or a data migration tool.
                Further conversion follows the selected compiler edition: the open basic
                compiler works offline; official member components use their own authorization.
                """);
    }

    public static boolean isValidPackage(String name) {
        return name != null && name.contains(".")
                && javax.lang.model.SourceVersion.isName(name, javax.lang.model.SourceVersion.RELEASE_17);
    }

    private String pom(String basePackage, java.util.List<RuntimeDependency> runtimeDependencies) {
        String extensions = runtimeDependencies.stream().map(dependency ->
                "    <dependency><groupId>" + dependency.groupId() + "</groupId><artifactId>" + dependency.artifactId()
                + "</artifactId><version>" + dependency.version() + "</version></dependency>\n").collect(java.util.stream.Collectors.joining());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId><artifactId>spp-generated-project</artifactId><version>1.0-SNAPSHOT</version>
                  <properties><maven.compiler.release>17</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
                  <dependencies>
                    <dependency><groupId>com.servpp</groupId><artifactId>appframe</artifactId><version>1.0-SNAPSHOT</version></dependency>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId><version>3.5.15</version></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId><version>3.1.5</version></dependency>
                    <dependency><groupId>org.openapitools</groupId><artifactId>jackson-databind-nullable</artifactId><version>0.2.6</version></dependency>
                    <dependency><groupId>io.swagger.core.v3</groupId><artifactId>swagger-annotations</artifactId><version>2.2.17</version></dependency>
                    <dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId><version>3.0.2</version></dependency>
                    <dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>2.1.1</version></dependency>
                    <dependency><groupId>com.fasterxml.jackson.datatype</groupId><artifactId>jackson-datatype-jsr310</artifactId><version>2.18.3</version></dependency>
                %s  </dependencies>
                  <build><plugins>
                    <plugin><artifactId>maven-compiler-plugin</artifactId><version>3.11.0</version></plugin>
                    <plugin><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version></plugin>
                  </plugins></build>
                </project>
                """.formatted(basePackage, extensions);
    }
}
