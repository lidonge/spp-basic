package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScenarioConditionTest {
    @Test public void branchUsesBusinessFactsAndCanEncloseAnExplicitBoundary() throws Exception {
        SppDomain domain = compile("""
                atomic calculate pending(Values v) { v.label = "Pending"; }
                atomic update write(Values v);
                Scenario serial save(Values v) {
                    if (v.allowed && v.amount > 0) {
                        transaction { write(v); }
                    } else { pending(v); }
                }
                """);
        assertTrue(domain.validateScenarioConditions().toString(), domain.validateScenarioConditions().isEmpty());
        assertTrue(domain.validateDeclaredTransactionGroups().isEmpty());
        String java = ((SppService)domain.getSppClass("Save")).getScenarioBodyJava();
        assertTrue(java, java.contains("v.getAllowed()"));
        assertTrue(java, java.contains("compareTo"));
        assertTrue(java, java.contains("TransactionMode.AT"));
        assertTrue(java, java.contains("else"));
    }

    @Test public void nestedScopesAndTheirTransactionsRemainVisibleToChecks() throws Exception {
        SppDomain domain = compile("""
                quantum update write(Values v);
                Scenario serial save(Values v) {
                    if (v.allowed) { if (true) { transaction { write(v); } } }
                    else { if (v.missing) { write(v); } }
                }
                """);
        assertTrue(domain.validateDeclaredTransactionGroups().isEmpty());
        assertTrue(domain.validateScenarioConditions().toString(), domain.validateScenarioConditions().stream()
                .anyMatch(e -> e.contains("missing")));
    }

    @Test public void javaStyleElseIfChoosesOneBranchRatherThanBothBodies() throws Exception {
        SppDomain domain = compile("""
                atomic calculate first(Values v) { }
                atomic calculate second(Values v) { }
                atomic calculate last(Values v) { }
                Scenario serial save(Values v) {
                    if (v.allowed) { first(v); }
                    else if (v.amount > 0) { second(v); }
                    else { last(v); }
                }
                """);
        assertTrue(domain.validateScenarioConditions().toString(), domain.validateScenarioConditions().isEmpty());
        String java = ((SppService)domain.getSppClass("Save")).getScenarioBodyJava();
        assertTrue(java, java.contains("else {") && java.contains("compareTo"));
        assertEquals(1, java.split("second.second", -1).length - 1);
        assertEquals(1, java.split("last.last", -1).length - 1);
    }

    @Test public void nonBooleanAndSideEffectingConditionsAreRejected() throws Exception {
        assertFalse(compile("Scenario serial save(Values v) { if (v.amount) { } }")
                .validateScenarioConditions().isEmpty());
        assertTrue(compile("Scenario serial save(Values v) { if (v.allowed = true) { } }")
                .validateAppDeclarations().stream().anyMatch(e -> e.contains("side effects")));
    }

    private SppDomain compile(String source) throws Exception {
        var file = Files.createTempFile("spp-scenario-condition", ".spp");
        try {
            Files.writeString(file, "domain rules; Role Values { double amount; boolean allowed; String label; } " + source);
            SppProject project = new SppProject(); new SppCompiler(file.toFile(), project).compile();
            return project.getSppDomainMap().get("rules");
        } finally { Files.deleteIfExists(file); }
    }
}
