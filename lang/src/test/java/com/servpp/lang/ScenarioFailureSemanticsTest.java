package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** SPP uses explicit check control; technical exceptions belong to APP/PHY. */
public class ScenarioFailureSemanticsTest {
    private SppDomain compile(String code) throws Exception {
        Path file = Files.createTempFile("scenario-failure", ".spp");
        try {
            Files.writeString(file, "domain rules; Contract Work { boolean allowed; int value; } "
                    + "atomic check eligible(Work work) { require(work.allowed, \"INELIGIBLE\"); } "
                    + "atomic calculate step(Work work) { work.value += 1; } "
                    + "Scenario serial run(Work work) { " + code + " }");
            var project = new SppProject(); new SppCompiler(file.toFile(), project).compile();
            return project.getDomain("rules");
        } finally { Files.deleteIfExists(file); }
    }

    @Test public void technicalExceptionSyntaxIsRejected() {
        for (String body : new String[] {
                "try { step(work); } catch (Work work) { step(work); }",
                "throw work;", "try { step(work); } finally { step(work); }"}) {
            assertThrows(java.io.IOException.class, () -> compile(body));
        }
    }

    @Test public void ordinaryCheckDoesNotRequestTransactionRollback() throws Exception {
        var domain = compile("eligible(work); step(work);");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        String code = ((SppService)domain.getSppClass("Run")).getScenarioBodyJava();
        assertTrue(code, code.contains("eligible"));
        assertFalse(code, code.contains("markRollbackOnly"));
    }

    @Test public void explicitRollbackCheckRequiresATransaction() throws Exception {
        var domain = compile("eligible(work),breakTransction; step(work);");
        assertFalse(domain.validateAppDeclarations().isEmpty());
    }

    @Test public void guardedCheckHasItsOwnBusinessScope() throws Exception {
        var domain = compile("eligible(work) { step(work); }; step(work);");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        String code = ((SppService)domain.getSppClass("Run")).getScenarioBodyJava();
        assertFalse(code, code.contains("markRollbackOnly"));
        assertTrue(code, code.contains("step"));
    }
}
