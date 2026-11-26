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
            Files.writeString(file, "domain rules; Enum Fault { INELIGIBLE } Contract Work { boolean allowed; int value; } "
                    + "atomic check eligible(Work work) { require(work.allowed, Fault.INELIGIBLE); } "
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
        var domain = compile("eligible(work),breakTransaction; step(work);");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("breakTransaction requires an enclosing transaction")));
        assertTrue(domain.unusedParticipantWarnings().isEmpty());
    }

    @Test public void rollbackFlagBelongsOnlyToTheNestedCall() throws Exception {
        var domain = compile("transaction { eligible(work) { eligible(work),breakTransaction; }; }");
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var scene = (SppScenario) domain.getSppClass("Run");
        var block = (ExecuteBlock) scene.getServiceBody().getSppServiceCallList().get(0);
        var transaction = (TransactionBlock) block.getExecuteCallList().get(0);
        var outer = (SppServiceCall) transaction.getTransactionCallList().get(0);
        var branch = (ExecuteBlock) outer.getGuardStatements().get(0);
        assertFalse(outer.isBreaksTransaction());
        assertTrue(((SppServiceCall) branch.getExecuteCallList().get(0)).isBreaksTransaction());
        assertTrue(domain.unusedParticipantWarnings().isEmpty());
    }

    @Test public void formerRollbackSpellingIsASyntaxError() {
        for (String call : new String[]{"eligible(work)", "identify(work)"}) {
            var error = assertThrows(java.io.IOException.class,
                    () -> compile("transaction { " + call + ",breakTransction; }"));
            assertTrue(error.getMessage(), error.getMessage().contains("syntax error"));
        }
    }

    @Test public void identifyCanRequestTransactionRollback() throws Exception {
        var domain = compile("transaction { identify(work),breakTransaction; }");
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        assertTrue(domain.unusedParticipantWarnings().isEmpty());
        var scene = (SppScenario) domain.getSppClass("Run");
        assertTrue(scene.getScenarioBodyJava().contains("SourceTransactionBoundary.rejectCurrent"));
    }

    @Test public void guardedCheckHasItsOwnBusinessScope() throws Exception {
        var domain = compile("eligible(work) { step(work); }; step(work);");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        String code = ((SppService)domain.getSppClass("Run")).getScenarioBodyJava();
        assertFalse(code, code.contains("markRollbackOnly"));
        assertTrue(code, code.contains("step"));
    }

    private String rollbackCalls(String body) throws Exception {
        var domain = compile(body);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        String code = ((SppService) domain.getSppClass("Run")).getScenarioBodyJava();
        return code;
    }
    private static int rejections(String code) { return code.split("SourceTransactionBoundary.rejectCurrent", -1).length - 1; }

    @Test public void plainCheckDirectlyInTransactionRollsBack() throws Exception {
        assertEquals(1, rejections(rollbackCalls("transaction { eligible(work); step(work); }")));
        assertEquals(1, rejections(rollbackCalls("transaction { identify(work); }")));
    }

    @Test public void ifBodiesAreTransparentToTheTransactionRollback() throws Exception {
        assertEquals(1, rejections(rollbackCalls("transaction { if (work.allowed) { eligible(work); } else { step(work); } }")));
        assertEquals(1, rejections(rollbackCalls("transaction { eligible(work) { eligible(work); }; }")));
    }

    @Test public void nestedBareBlockExitsOnlyItself() throws Exception {
        assertEquals(0, rejections(rollbackCalls("transaction { serial { eligible(work); } step(work); }")));
        assertEquals(1, rejections(rollbackCalls("transaction { serial { eligible(work),breakTransaction; } step(work); }")));
    }

    @Test public void checkOutsideTransactionIsUnchanged() throws Exception {
        assertEquals(0, rejections(rollbackCalls("eligible(work); if (work.allowed) { eligible(work); } serial { eligible(work); }")));
    }
}
