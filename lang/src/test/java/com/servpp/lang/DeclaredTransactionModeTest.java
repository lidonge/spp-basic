package com.servpp.lang;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source fixes business boundaries; APP chooses implementation strategy. */
public class DeclaredTransactionModeTest {
    @Test public void sameBusinessSourceAcceptsDifferentAppStrategies() throws Exception {
        for (String mode : new String[]{"at", "tcc", "saga", "quantum"}) {
            SppDomain domain = compile("atomic update write(Values v); Scenario serial save(Values v) { transaction { write(v); } }");
            bind(domain, "domain rules; scope { local { " + mode + " resource(store) write; save; } }");
            assertTrue(domain.validateDeclaredTransactionGroups().toString(), domain.validateDeclaredTransactionGroups().isEmpty());
            String generated = ((SppService) domain.getSppClass("Save")).getScenarioBodyJava();
            assertTrue(generated, generated.contains("TransactionMode." + mode.toUpperCase(java.util.Locale.ROOT)));
            assertTrue(generated, generated.contains("TransactionPropagation.REQUIRED"));
        }
    }

    @Test public void incompatibleAppStrategiesAreRejectedBeforeGeneration() throws Exception {
        SppDomain domain = compile("atomic update first(Values v); atomic update second(Values v); Scenario serial save(Values v) { transaction { first(v); second(v); } }");
        bind(domain, "domain rules; scope { local { at resource(one) first; quantum resource(two) second; save; } }");
        assertTrue(domain.validateDeclaredTransactionGroups().stream().anyMatch(e -> e.contains("incompatible APP modes")));
    }

    @Test public void emptyBoundariesAreRejected() throws Exception {
        assertFalse(compile("Scenario serial save(Values v) { transaction { } }").validateDeclaredTransactionGroups().isEmpty());
    }

    @Test public void propagationAndDirectNestingSurviveGeneration() throws Exception {
        SppDomain domain = compile("atomic update write(Values v); Scenario serial save(Values v) { transaction REQUIRED { write(v); transaction NESTED { write(v); } transaction REQUIRES_NEW { write(v); } } }");
        bind(domain, "domain rules; scope { local { quantum resource(store) write; save; } }");
        assertTrue(domain.validateDeclaredTransactionGroups().toString(), domain.validateDeclaredTransactionGroups().isEmpty());
        String java = ((SppService) domain.getSppClass("Save")).getScenarioBodyJava();
        for (String propagation : new String[]{"REQUIRED", "NESTED", "REQUIRES_NEW"})
            assertTrue(java, java.contains("TransactionPropagation." + propagation));
    }

    @Test public void independentBoundaryMayUseDifferentAppStrategy() throws Exception {
        SppDomain domain = compile("atomic update first(Values v); atomic update second(Values v); Scenario serial save(Values v) { transaction { first(v); transaction REQUIRES_NEW { second(v); } } }");
        bind(domain, "domain rules; scope { local { at resource(one) first; quantum resource(two) second; save; } }");
        assertTrue(domain.validateDeclaredTransactionGroups().toString(), domain.validateDeclaredTransactionGroups().isEmpty());
    }

    @Test public void joinedReadOnlyBoundaryInheritsOuterStrategy() throws Exception {
        SppDomain domain = compile("atomic update write(Values v); atomic check valid(Values v) { require(v.amount > 0, \"INVALID\"); } Scenario serial save(Values v) { transaction { write(v); transaction { valid(v); } } }");
        bind(domain, "domain rules; scope { local { quantum resource(store) write; valid; save; } }");
        assertTrue(domain.validateDeclaredTransactionGroups().toString(), domain.validateDeclaredTransactionGroups().isEmpty());
        assertFalse(((SppService) domain.getSppClass("Save")).getScenarioBodyJava().contains("TransactionMode.AT"));
    }

    @Test public void transactionRejectsSubscenario() throws Exception {
        SppDomain domain = compile("atomic update write(Values v); Scenario serial child(Values v) { write(v); } Scenario serial save(Values v) { transaction { child(v); } }");
        bind(domain, "domain rules; scope { local { quantum resource(store) write; child; save; } }");
        assertTrue(domain.validateDeclaredTransactionGroups().stream().anyMatch(e -> e.contains("may contain only atomic services")));
    }

    @Test public void opaqueSerialGroupDescribesItsWritersWithoutEnlistingIndependentBoundary()throws Exception {
        var domain=compile("atomic update first(Values v); atomic update second(Values v); atomic update separate(Values v); Scenario serial save(Values v) { transaction { serial { first(v); if (v.amount > 0) { second(v); } transaction REQUIRES_NEW { separate(v); } } } }");
        bind(domain,"domain rules; scope { local { at resource(store) first; second; separate; save; } }");
        assertTrue(domain.validateDeclaredTransactionGroups().toString(),domain.validateDeclaredTransactionGroups().isEmpty());
        String generated=((SppService)domain.getSppClass("Save")).getScenarioBodyJava();
        assertTrue(generated,generated.contains("getRepositoryScopeServices() { return java.util.List.of(first,second); }"));
        assertFalse(generated,generated.contains("java.util.List.of(first,second,separate)"));
    }

    private void bind(SppDomain domain, String source) throws Exception {
        var file = Files.createTempFile("spp-boundary", ".app");
        try {
            Files.writeString(file, source);
            SppProject project = new SppProject(); project.getSppDomainMap().put("rules", domain);
            new AppCompiler(file.toFile(), project).compile();
        } finally { Files.deleteIfExists(file); }
    }

    private SppDomain compile(String source) throws Exception {
        var file = Files.createTempFile("spp-boundary", ".spp");
        try {
            Files.writeString(file, "domain rules; Role Values { quantum double amount; } " + source);
            SppProject project = new SppProject(); new SppCompiler(file.toFile(), project).compile();
            return project.getSppDomainMap().get("rules");
        } finally { Files.deleteIfExists(file); }
    }
}
