package com.servpp.generator.openapi;

import com.servpp.lang.SppCompiler;
import com.servpp.lang.AppCompiler;
import com.servpp.lang.models.SppProject;
import java.nio.file.Files;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedParticipantGraphGenerationTest {
    @Test(timeout = 15000) public void sharedNestedFactsWithoutRoleMappingsGenerateWithoutPathExpansion() throws Exception {
        var root = Files.createTempDirectory("spp-shared-participant-graph");
        var source = root.resolve("sample.spp");
        var text = new StringBuilder("domain sample; Role Node0 { String value; }");
        for (int depth = 1; depth <= 35; depth++) {
            text.append(" Role Node").append(depth).append(" { Node").append(depth - 1)
                    .append(" first; Node").append(depth - 1).append(" second; }");
        }
        text.append(" atomic calculate inspect(Node35 facts, Node0 label) {}");
        Files.writeString(source, text);
        var project = new SppProject(); new SppCompiler(source.toFile(), project).compile();
        var app = root.resolve("sample.app");
        Files.writeString(app, "domain sample; scope { local { inspect; } }");
        new AppCompiler(app.toFile(), project).compile();
        var domain = project.getDomain("sample");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        var output = root.resolve("java"); Files.createDirectories(output);
        var api = new OpenApiGenerator().generate(domain, output.toFile(), "free.example.generated");
        assertEquals(Set.of("facts", "label"), api.getComponents().getSchemas().get("InspectRequest").getProperties().keySet());
        assertEquals(Set.of("first", "second"), api.getComponents().getSchemas().get("Node35").getProperties().keySet());
    }
}
