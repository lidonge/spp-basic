package com.servpp.lang;

import com.servpp.lang.models.ExecuteBlock;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import com.servpp.lang.models.SppServiceCall;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BlankLiteralTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private SppDomain compile(String body, String policy) throws Exception {
        var dir = temp.newFolder().toPath();
        var project = new SppProject();
        var spp = dir.resolve("notes.spp");
        Files.writeString(spp, """
                domain notes;
                Role Note { String(8) code; String(3) flag; String memo; int count; boolean present; }
                """ + body);
        new SppCompiler(spp.toFile(), project).compile();
        var app = dir.resolve("notes.app");
        Files.writeString(app, "domain notes; tables roles { table NOTES(Note) { primarykey(Note.code); } }"
                + policy);
        new AppCompiler(app.toFile(), project).compile();
        return project.getDomain("notes");
    }

    @Test public void defaultAndOverridesOnlyApplyAtBlankUseSites() throws Exception {
        var body = """
                atomic calculate mark(Note n) {
                    n.present = n.code != BLANK && BLANK == n.flag;
                    n.code = BLANK;
                    n.flag = (BLANK);
                    n.present = n.flag == " ";
                }
                """;
        var baseline = generated(compile(body, ""));
        assertTrue(baseline, baseline.contains("n.setCode(\"\")"));
        assertTrue(baseline, baseline.contains("n.getCode(), true, true, false"));
        var overrides = generated(compile(body, """
                blank { read: null; write: null; }
                blank NOTES { read: spaces; write: spaces; }
                phy { blank NOTES.flag { read: ""; write: ""; } }
                """));
        assertTrue(overrides, overrides.contains("n.setCode(\" \".repeat(8))"));
        assertTrue(overrides, overrides.contains("n.getCode(), false, false, true"));
        assertTrue(overrides, overrides.contains("n.setFlag(\"\")"));
        assertTrue(overrides, overrides.contains("n.getFlag(), false, true, false"));
        assertTrue(overrides, overrides.contains("equals"));
    }

    private String generated(SppDomain domain) {
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var service = (SppService) domain.getSppClass("Mark");
        service.setNumericGeneration(true);
        return service.getJavaStatements().stream().map(s -> s.getJavaStatement())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test public void rejectsNonStringOperandsAndMissingPadWidth() throws Exception {
        for (String statement : List.of("n.count = BLANK;", "n.present = BLANK == n.count;",
                "n.present = n.count != BLANK;", "n.memo = BLANK;")) {
            var domain = compile("atomic calculate mark(Note n) { " + statement + " }",
                    "blank { read: spaces; write: spaces; }");
            var errors = domain.validateAppDeclarations();
            assertTrue(statement + errors, errors.stream().anyMatch(error -> error.contains("BLANK")));
        }
    }

    @Test public void bindsCanonicalStringToFindKey() throws Exception {
        var domain = compile("Scenario serial read(Note n) { findNote by Note.code = BLANK; }",
                "blank Note.code { read: null; write: null; }");
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var scene = (SppService) domain.getSppClass("Read");
        var block = (ExecuteBlock) scene.getServiceBody().getSppServiceCallList().get(0);
        var call = (SppServiceCall) block.getExecuteCallList().get(0);
        assertEquals("(String) null", call.getBoundReadKeys().get(0).javaValue());
    }
}
