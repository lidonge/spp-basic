package com.servpp.lang;

import com.servpp.lang.lint.CompilerDiagnostics;
import com.servpp.lang.lint.RepairDiagnostic;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import com.servpp.lang.models.app.StringCollation;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OrderingComparisonTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void acceptsScalarStringsAndSameEnumAndReportsOrderingAtOperator() throws Exception {
        var domain = compile("""
                atomic calculate rank(Row r) {
                    r.result = ("01" < r.code && r.code >= BLANK) || (r.priority <= Priority.HIGH);
                }
                Scenario serial preview(Row r) {
                    if (Priority.HIGH > r.priority) { rank(r); }
                }
                """);
        var diagnostics = new ArrayList<RepairDiagnostic>();
        try (var observer = CompilerDiagnostics.observe(diagnostics::add)) {
            var errors = domain.validateAppDeclarations();
            assertTrue(errors.toString(), errors.isEmpty());
        }
        assertEquals(2, diagnostics.size());
        for (var diagnostic : diagnostics) {
            assertEquals("SPP_ENUM_ORDER_COMPARE", diagnostic.code());
            assertEquals("info", diagnostic.severity());
            assertTrue(diagnostic.file().endsWith("order.spp"));
        }
        assertEquals(List.of(6, 9), diagnostics.stream().map(RepairDiagnostic::line).sorted().toList());
    }

    @Test public void rejectsMixedOperandsWithMatchingTypeHint() throws Exception {
        for (String comparison : List.of("r.code < 2", "r.priority >= r.code",
                "r.priority > Other.HIGH", "BLANK <= 2", "r.code < null",
                "(true ? r.priority : Other.HIGH) < r.priority",
                "(true ? r.priority : \"H\") < r.priority")) {
            var errors = compile("atomic calculate rank(Row r) { r.result = " + comparison + "; }")
                    .validateBusinessCalculations();
            assertTrue(comparison + " " + errors, errors.stream().anyMatch(error ->
                    error.contains("cannot order") && error.contains("two values of the same enum type")));
        }
    }

    @Test public void inheritsAppCollationAcrossDeployedDomains() throws Exception {
        var domain = compile("atomic calculate rank(Row r) { r.result = r.code < \"A\"; }");
        var directory = temporary.newFolder().toPath();
        var owner = directory.resolve("owner.spp");
        Files.writeString(owner, "domain owner; Role Owner { String name; }");
        new SppCompiler(owner.toFile(), domain.getProject()).compile();
        var app = directory.resolve("owner.app");
        Files.writeString(app, "domain owner; application demo { deploy owner; deploy order; } collation ebcdic;");
        new AppCompiler(app.toFile(), domain.getProject()).compile();
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        assertEquals(StringCollation.EBCDIC, ((SppService) domain.getSppClass("Rank")).getStringCollation());
    }

    private SppDomain compile(String body) throws Exception {
        var input = temporary.newFolder().toPath().resolve("order.spp");
        Files.writeString(input, """
                domain order;
                Enum Priority { LOW:"Z", HIGH:"A" }
                Enum Other { HIGH }
                Role Row { String code; Priority priority; boolean result; }
                """ + body);
        var project = new SppProject();
        new SppCompiler(input.toFile(), project).compile();
        return project.getDomain("order");
    }
}
