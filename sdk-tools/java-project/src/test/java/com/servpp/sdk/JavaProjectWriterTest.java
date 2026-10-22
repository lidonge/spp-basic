package com.servpp.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class JavaProjectWriterTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path project() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path specifications = Files.createDirectory(root.resolve("openapi"));
        Files.writeString(specifications.resolve("ledger.spp.yaml"), """
                openapi: 3.0.1
                info:
                  title: OpenAPI ledger
                  version: 1.0.0
                paths:
                  /ledger/preview:
                    post:
                      operationId: preview
                      tags: [ledger]
                      responses:
                        '200':
                          description: Success
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/Facts'
                components:
                  schemas:
                    Facts:
                      type: object
                      properties:
                        amount:
                          type: integer
                          format: int32
                """);
        return root;
    }

    @Test
    public void emitsCompleteModelApiAndMavenMetadata() throws Exception {
        Path root = project();
        new JavaProjectWriter().write(root, "com.example.generated");
        Path java = root.resolve("src/main/java/com/example/generated/ledger");
        assertTrue(Files.isRegularFile(java.resolve("model/Facts.java")));
        assertTrue(Files.isRegularFile(java.resolve("handler/LedgerApi.java")));
        assertTrue(Files.isRegularFile(java.resolve("handler/ApiUtil.java")));
        String pom = Files.readString(root.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>appframe</artifactId>"));
        assertTrue(pom.contains("<artifactId>mybatis</artifactId>"));
        assertFalse(pom.contains("<artifactId>generator</artifactId>"));
        var manifest = new ObjectMapper().readTree(root.resolve("spp-generation.json").toFile());
        assertEquals("ledger", manifest.path("domains").get(0).asText());
        String validationOutput = System.getProperty("spp.sdk.validationOutput");
        if (validationOutput != null) {
            Path destination = Path.of(validationOutput);
            if (Files.exists(destination)) throw new IllegalArgumentException("Validation output must be new.");
            try (var entries = Files.walk(root)) {
                for (Path entry : entries.toList()) {
                    Path target = destination.resolve(root.relativize(entry));
                    if (Files.isDirectory(entry)) Files.createDirectories(target);
                    else Files.copy(entry, target);
                }
            }
        }
    }

    @Test
    public void rejectsNonGeneratedDomainAndInvalidJavaPackage() throws Exception {
        Path root = project();
        try { new JavaProjectWriter().write(root, "invalid-package"); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("package")); }
        Path input = root.resolve("openapi/ledger.spp.yaml");
        Files.writeString(input, Files.readString(input).replace("OpenAPI ledger", "untrusted title"));
        try { new JavaProjectWriter().write(root, "com.example.generated"); fail(); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("domain")); }
    }

    @Test public void explicitlySelectedRuntimeExtensionsAreRecordedAndValidated()throws Exception {
        Path root=project();
        new JavaProjectWriter().write(root,"com.example.generated",java.util.List.of(
                new JavaProjectWriter.RuntimeDependency("com.example","custom-runtime","2.0")));
        String pom=Files.readString(root.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>custom-runtime</artifactId>"));
        var manifest=new ObjectMapper().readTree(root.resolve("spp-generation.json").toFile());
        assertEquals("custom-runtime",manifest.path("runtimeExtensions").get(0).path("artifactId").asText());
        try{new JavaProjectWriter.RuntimeDependency("com.example","x</artifactId>","2.0");fail();}
        catch(IllegalArgumentException expected){}
    }

    @Test
    public void validatesJavaIdentifiersRatherThanJustDottedStrings() {
        assertTrue(JavaProjectWriter.isValidPackage("com.example.generated"));
        assertFalse(JavaProjectWriter.isValidPackage("com.class.generated"));
        assertFalse(JavaProjectWriter.isValidPackage("com._.generated"));
        assertFalse(JavaProjectWriter.isValidPackage("invalid-package"));
        assertFalse(JavaProjectWriter.isValidPackage(null));
    }

    @Test
    public void keepsMultipleDomainPackagesAndMetadata() throws Exception {
        Path root = project();
        String second = Files.readString(root.resolve("openapi/ledger.spp.yaml")).replace("ledger", "billing");
        Files.writeString(root.resolve("openapi/billing.spp.yaml"), second);
        new JavaProjectWriter().write(root, "com.example.generated");
        for (String domain : new String[]{"ledger", "billing"}) {
            assertTrue(Files.isRegularFile(root.resolve("src/main/java/com/example/generated/" + domain + "/model/Facts.java")));
            assertTrue(Files.isRegularFile(root.resolve("src/main/java/com/example/generated/" + domain + "/handler/ApiUtil.java")));
        }
        var manifest = new ObjectMapper().readTree(root.resolve("spp-generation.json").toFile());
        assertEquals(2, manifest.path("domains").size());
    }
}
