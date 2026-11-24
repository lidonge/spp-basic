package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BusinessCalculationSemanticsTest {
    private static final String ROLE = "domain rules; Enum Fault { bad, rejected, INVALID } Role Values { double a; double b; boolean allowed; String label; } ";
    @Test public void constructsEnumValuesFromScalarStringExpressions() throws Exception {
        var domain = compile("""
                domain rules;
                const String CLOSED = "EC031";
                Enum Rejection { LIMIT:"EC012", CLOSED:"EC031", UNKNOWN:other }
                Role ProviderResult { String code; boolean passed; Rejection reason; }
                atomic check checkProvider(ProviderResult result) {
                    require(result.passed, Rejection(result.code));
                }
                atomic calculate classify(ProviderResult result) {
                    Rejection reason = Rejection(CLOSED);
                    result.reason = Rejection(result.code);
                    result.passed = Rejection("EC031") == reason;
                    switch (Rejection(result.code)) {
                        case UNKNOWN: result.reason = reason; break;
                    }
                }
                """);
        var project = new SppProject();
        project.getSppDomainMap().put("rules", domain);
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test public void enumConstructorAndRejectionArgumentsCountAsParticipantUses() throws Exception {
        var domain = compile("""
                domain rules;
                Enum Outcome { SUCCESS:"", UNKNOWN:other }
                Role Result { String code; }
                atomic check checkProvider(Result result, Result reason, Result unused) {
                    require(Outcome(result.code) == Outcome.SUCCESS, Outcome(reason.code));
                }
                """);
        var warnings = domain.unusedParticipantWarnings();
        assertEquals(warnings.toString(), 1, warnings.size());
        assertTrue(warnings.toString(), warnings.get(0).contains("participant 'unused'"));
    }

    @Test public void rejectsEnumConstructionWithWrongArityOrType() throws Exception {
        String source = """
                domain rules;
                Enum Rejection { UNKNOWN:other }
                Enum OtherCode { CLOSED }
                Role ProviderResult { String code; String[] codes; Rejection reason; }
                """;
        String calculation = "atomic calculate classify(ProviderResult result) { result.reason = %s; }";
        assertThrows(java.io.IOException.class, () -> compile(source + calculation.formatted("Rejection()")));
        assertTrue(compile(source + calculation.formatted("Rejection(result.code, result.code)"))
                .validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("Enum Rejection requires one String argument.")));
        for (String argument : java.util.List.of("1", "true", "'X'", "null", "result", "result.codes", "result.reason")) {
            var domain = compile(source + calculation.formatted("Rejection(" + argument + ")"));
            assertTrue(argument, domain.validateAppDeclarations().stream()
                    .anyMatch(error -> error.contains("Enum Rejection requires one scalar String argument.")));
        }
        var domain = compile(source + calculation.formatted("OtherCode(result.code)"));
        assertTrue(domain.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("Enum type mismatch: Rejection and OtherCode")));
    }

    @Test public void asciiIntegerTextHasDistinctBooleanAndExactNumericResults() throws Exception {
        var domain = compile("""
                domain rules;
                Enum Fault { INVALID_TEXT } Role Text { String digits; BigDecimal(30,0) value; boolean valid; }
                atomic calculate decode(Text text) {
                    text.valid = isAsciiDigits(text.digits);
                    if (text.valid) { text.value = parseUnsignedInteger(text.digits); }
                }
                atomic check checkText(Text text) { require(isAsciiDigits(text.digits), Fault.INVALID_TEXT); }
                """);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        var service = (SppService) domain.getSppClass("Decode");
        service.setNumericGeneration(true);
        String generated = service.getJavaStatements().stream().map(s -> s.getJavaStatement())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(generated, generated.contains("AsciiIntegerText.isAsciiDigits"));
        assertTrue(generated, generated.contains("AsciiIntegerText.parseUnsignedInteger"));
        assertTrue(compile(ROLE + "atomic check checkText(Values v) { require(isAsciiDigits(null), Fault.INVALID); }")
                .validateAppDeclarations().isEmpty());
    }
    @Test public void asciiIntegerTextRejectsWrongArityAndNonStringArguments() throws Exception {
        for (String name : java.util.List.of("isAsciiDigits", "parseUnsignedInteger")) {
            assertThrows(java.io.IOException.class, () -> compile(ROLE
                    + "atomic calculate decode(Values v) { v.a = " + name + "(); }"));
            for (String args : java.util.List.of("v.label, v.label", "v.a", "v.allowed", "v", "v.labels")) {
                String assignment = name.equals("isAsciiDigits") ? "v.allowed" : "v.a";
                var domain = compile("domain rules; Role Values { double a; boolean allowed; String label; String[] labels; } "
                        + "atomic calculate decode(Values v) { " + assignment + " = " + name + "(" + args + "); }");
                assertFalse(name + "(" + args + ")", domain.validateAppDeclarations().isEmpty());
            }
        }
    }
    @Test public void utcMonthArithmeticRequiresTwoNumbersAndGeneratesGenericCalendarCall() throws Exception {
        var domain=compile("domain rules; Role Clock { long now; long end; } atomic calculate advance(Clock clock) { clock.end = addUtcMonths(clock.now, 3); }");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        var service=(SppService)domain.getSppClass("Advance");service.setNumericGeneration(true);
        assertTrue(service.getJavaStatements().toString(),service.getJavaStatements().stream().anyMatch(statement -> statement.getJavaStatement().contains("GregorianDates.addUtcMonths")));
        for(String expression:java.util.List.of("addUtcMonths(clock.now)","addUtcMonths(clock.now, true)")) {
            var invalid=compile("domain rules; Role Clock { long now; long end; } atomic calculate advance(Clock clock) { clock.end = "+expression+"; }");
            assertFalse(invalid.validateAppDeclarations().isEmpty());
        }
    }
    @Test public void sameFactsRequiresMatchingDeclaredParticipantsOrCollectionDimensions() throws Exception {
        String source = """
                domain rules;
                Enum Fault { CHANGED } Role Evidence { double value; }
                Role Other { double value; }
                Contract Work { Evidence expected; Evidence actual; Evidence[] oldRows; Evidence[] newRows; Other secondary; }
                """;
        String check = "atomic check compare(Work work) { require(%s, Fault.CHANGED); }";
        var valid = compile(source + check.formatted("sameFacts(work.expected, work.actual) && sameFacts(work.oldRows, work.newRows)"));
        assertTrue(valid.validateAppDeclarations().toString(), valid.validateAppDeclarations().isEmpty());
        for (String expression : java.util.List.of("sameFacts(work.expected)", "sameFacts(work.expected, work.secondary)",
                "sameFacts(work.oldRows, work.actual)", "sameFacts(work.expected.value, work.actual.value)")) {
            assertTrue(compile(source + check.formatted(expression)).validateAppDeclarations().stream()
                    .anyMatch(error -> error.contains("sameFacts requires")));
        }
    }

    @Test public void builtinLocalTypesRemainUnqualifiedAfterPackageBinding() throws Exception {
        SppDomain domain = compile(ROLE + """
                atomic calculate decide(Values v) {
                    boolean enabled = true;
                    String label = "accepted";
                    v.allowed = enabled;
                    v.label = label;
                }
                """);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        SppService service = (SppService) domain.getSppClass("Decide");
        for (var statement : service.getDeclaredJavaStatements()) {
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationControl control
                    && control.local != null) {
                control.local.getType().setBasePackage("demo.generated");
            }
        }
        service.setNumericGeneration(true);
        String generated = service.getJavaStatements().stream().map(s -> s.getJavaStatement())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(generated, generated.contains("boolean $sppLocal_enabled"));
        assertTrue(generated, generated.contains("String $sppLocal_label"));
        assertFalse(generated, generated.contains("demo.generated"));
    }

    @Test public void booleanConstantsAreLiteralsInBusinessAndAppBodies() throws Exception {
        SppDomain domain = compile(ROLE + """
                atomic calculate decide(Values v) {
                    v.allowed = false;
                    if (true) { v.allowed = !false; }
                }
                atomic update record(Values v);
                """);
        Path app = Files.createTempFile("spp-booleans", ".app");
        Files.writeString(app, "domain rules; services { record { v.allowed = true; } }");
        SppProject project = new SppProject(); project.getSppDomainMap().put("rules", domain);
        new AppCompiler(app.toFile(), project).compile();
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertTrue(((SppService) domain.getSppClass("Record")).getJavaStatements().get(0).getJavaStatement().contains("true"));
    }

    @Test public void mathematicalAndLogicalBodiesUseExistingServiceKinds() throws Exception {
        SppDomain domain = compile(ROLE + """
                atomic calculate decide(Values v) {
                    v.a = roundHalfUp(v.b * 2 + 5, 2);
                    v.allowed = v.a > 0 && !(v.b == 0);
                    if (v.allowed) { v.a += 1; }
                    else { if (v.b < 0) { v.a = abs(v.b); } }
                    v.label = v.allowed ? "yes" : "no";
                }
                atomic check checkValues(Values v) { require(v.allowed, Fault.rejected); }
                """);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertTrue(((SppService) domain.getSppClass("Decide")).hasBusinessImplementation());
    }

    @Test public void invalidBodiesProduceSemanticDiagnostics() throws Exception {
        for (String source : new String[] {
                "atomic update decide(Values v) { v.a = 1; }",
                "atomic query decide(Values v) { v.a = 1; }",
                "atomic check decide(Values v) { v.a = 1; }",
                "atomic calculate decide(Values v) { v.missing = 1; }",
                "atomic calculate decide(Values v) { v.a = true; }",
                "atomic calculate decide(Values v) { if (v.a) { v.a = 1; } }",
                "atomic calculate decide(Values v) { v.allowed = v.a && v.b; }",
                "atomic calculate decide(Values v) { v.a = min(v.b); }",
                "atomic calculate decide(Values v) { v.a = javaMethod(v.b); }",
                "atomic calculate decide(double v) { v = 1; }",
                "atomic calculate decide(Values v) { v.a = v.b++; }",
                "atomic calculate decide(Values v) { v.a = (v.b = 1); }"
        }) {
            SppDomain domain = compile(ROLE + source);
            assertFalse(source, domain.validateAppDeclarations().isEmpty());
        }
    }

    @Test public void appCannotSilentlyReplaceBusinessBody() throws Exception {
        SppDomain domain = compile(ROLE + "atomic calculate decide(Values v) { v.a = 1; }");
        Path app = Files.createTempFile("spp-rules", ".app");
        Files.writeString(app, "domain rules; services { decide { v.a = 2; } }");
        SppProject project = new SppProject(); project.getSppDomainMap().put("rules", domain);
        new AppCompiler(app.toFile(), project).compile();
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("both SPP and APP")));
        assertTrue(((SppService) domain.getSppClass("Decide")).getJavaStatements().get(0).getJavaStatement().contains("\"1\""));
    }

    @Test public void inheritedRoleFieldsResolveBeforeValidation() throws Exception {
        SppDomain domain = compile("""
                domain rules;
                Entity Source { double a; }
                Role Values { }
                take Source as Values { takeall; mapsame; }
                atomic calculate decide(Values v) { v.a = v.a + 1; }
                """);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
    }

    @Test public void functionNamesRemainValidBusinessFieldNamesAndEnumsUseDeclaredValues() throws Exception {
        SppDomain domain = compile("""
                domain rules;
                Enum Status { Pending:"pending", Cancelled:"cancelled" }
                Role Values { double min; double max; Status status; boolean allowed; }
                atomic calculate decide(Values v) {
                    v.min = min(v.min, v.max);
                    v.allowed = v.status == "pending";
                    if (v.allowed) { v.status = "cancelled"; }
                }
                """);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        SppDomain invalid = compile("""
                domain rules;
                Enum Status { Pending }
                Role Values { Status status; }
                atomic calculate decide(Values v) { v.status = "missing"; }
                """);
        assertTrue(invalid.validateAppDeclarations().toString(), invalid.validateAppDeclarations().stream()
                .anyMatch(error -> error.contains("Unknown value")));
        SppDomain wrongWireValue = compile("""
                domain rules;
                Enum Status { Enabled:"E" }
                Role Values { Status status; }
                atomic calculate decide(Values v) { v.status = "Enabled"; }
                """);
        assertFalse(wrongWireValue.validateAppDeclarations().isEmpty());
    }

    @Test public void controlScopeAndJumpErrorsAreDiagnosed() throws Exception {
        for (String body : new String[] {
                "break;", "continue;", "while (v.a) { break; }",
                "for (int i=0; i<2; i++) {} v.a=i;",
                "if (true) { int n=1; } v.a=n;",
                "int n=1; int n=2;", "int v=1;",
                "int n=true;", "switch (v.allowed) { default: break; }",
                "switch (v.a) { case 1: break; case 1.0: break; }",
                "switch (v.a) { default: break; default: break; }",
                "switch (v.a) { case v.b: break; }", "for (Values item : v) {}"
        }) { assertFalse(body, compile(ROLE + "atomic calculate decide(Values v) { " + body + " }").validateAppDeclarations().isEmpty()); }
    }

    @Test public void checkServicesMayComputeLocalsButCannotMutateParticipantAliases() throws Exception {
        SppDomain valid = compile(ROLE + "atomic check decide(Values v) { int total=0; for (int i=0; i<3; i++) total+=i; require(total==3, Fault.bad); }");
        assertTrue(valid.validateAppDeclarations().toString(), valid.validateAppDeclarations().isEmpty());
        SppDomain invalid = compile(ROLE + "atomic check decide(Values v) { Values alias=v; alias.a=1; }");
        assertFalse(invalid.validateAppDeclarations().isEmpty());
    }

    @Test public void removedDecisionKeywordsAreSyntaxErrors() {
        assertThrows(java.io.IOException.class, () -> compile(ROLE + "atomic calculate decide(Values v) { when (true) { v.a=1; } otherwise { v.a=2; } }"));
    }

    @Test public void switchEnumLabelsResolveDeclaredNames() throws Exception {
        String declarations = "domain rules; Enum Status { Pending:\"pending\", Accepted:\"accepted\",\"A\" } Role Values { Status status; double a; } ";
        SppDomain domain = compile(declarations
                + "atomic calculate decide(Values v) { switch (v.status) { case Pending: v.a=1; break; case Status.Accepted: v.a=2; break; default: v.a=0; } }");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        SppDomain repeated = compile(declarations
                + "atomic calculate decide(Values v) { switch (v.status) { case Accepted: break; case \"A\": break; } }");
        assertTrue(repeated.validateAppDeclarations().stream().anyMatch(error -> error.contains("Duplicate switch case")));
    }

    @Test public void stringConcatenationKeepsStringTypeAndRequiresExplicitNumericConversion() throws Exception {
        String declarations="domain rules; Role Names { String prefix; String suffix; String key; double amount; } ";
        var valid=compile(declarations+"atomic calculate concatenate(Names n) { n.key = n.prefix + n.suffix + \"-ID\"; }");
        assertTrue(valid.validateAppDeclarations().toString(),valid.validateAppDeclarations().isEmpty());
        assertFalse(compile(declarations+"atomic calculate concatenate(Names n) { n.key = n.prefix + n.amount; }")
                .validateAppDeclarations().isEmpty());
        assertFalse(compile(declarations+"atomic calculate concatenate(Names n) { n.key = n.prefix - n.suffix; }")
                .validateAppDeclarations().isEmpty());
    }

    @Test public void appQuantumImplementationIsNotMistakenForAnSppBody() throws Exception {
        for (boolean sppBody : new boolean[]{false, true}) {
            SppDomain domain = compile("domain rules; Role Wallet { quantum double balance; } "
                    + "quantum update transfer(Wallet debit, Wallet credit) "
                    + (sppBody ? "{ debit.balance -= 1; credit.balance += 1; }" : ";"));
            Path app = Files.createTempFile("quantum-app-body", ".app");
            try {
                Files.writeString(app, "domain rules; services { transfer { debit.balance -= 1; credit.balance += 1; } }");
                SppProject project = new SppProject(); project.getSppDomainMap().put("rules", domain);
                new AppCompiler(app.toFile(), project).compile();
                assertEquals(sppBody, domain.validateAppServiceImplementations().stream()
                        .anyMatch(error -> error.contains("second update implementation")));
            } finally { Files.deleteIfExists(app); }
        }
    }

    @Test public void numericRoundingModesOnFieldsQuantumAttributesAndLocals() throws Exception {
        for (String mode : java.util.List.of("DOWN", "UP", "HALF_UP", "HALF_DOWN", "HALF_EVEN", "CEILING", "FLOOR")) {
            SppDomain domain = compile("""
                    domain rules;
                    Entity Ledger { BigDecimal(15,2,%1$s) amount; }
                    Contract Agreement { BigDecimal(15,2,%1$s) amount; }
                    Role Values { quantum BigDecimal(15,2,%1$s) balance; BigDecimal(15,2,%1$s) amount; }
                    atomic calculate decide(Values v) {
                        BigDecimal(18,6,%1$s) rate = 1 / 3, previous = rate;
                        v.amount = previous;
                    }
                    """.formatted(mode));
            var errors = domain.validateBusinessCalculations();
            assertTrue(errors.toString(), errors.isEmpty());
            var expected = java.math.RoundingMode.valueOf(mode);
            for (String name : java.util.List.of("Ledger", "Agreement", "Values"))
                assertEquals(expected, ((com.servpp.lang.models.SppClass) domain.getSppClass(name)).getField("amount").getDeclaredRoundingMode());
            assertEquals(expected, ((com.servpp.lang.models.SppClass) domain.getSppClass("Values")).getField("balance").getDeclaredRoundingMode());
            assertTrue(domain.numericRoundingWarnings().isEmpty());
        }
        var invalid = compile("""
                domain rules;
                Role Values { BigDecimal(15,2,UNNECESSARY) amount; }
                atomic calculate decide(Values v) { BigDecimal(15,2,BOGUS) rate; }
                """).validateAppDeclarations();
        for (String mode : java.util.List.of("UNNECESSARY", "BOGUS"))
            assertTrue(invalid.toString(), invalid.stream().anyMatch(error -> error.contains("Unsupported numeric rounding mode " + mode)));
    }

    @Test public void numericWarningsTrackExpressionAndCompoundScales() throws Exception {
        SppDomain domain = compile("""
                domain rules;
                Role Values { BigDecimal(15,2) amount; BigDecimal(15,3) rate; }
                atomic calculate decide(Values v) {
                    BigDecimal(15,2) local = v.amount / 3;
                    v.amount = local * v.rate;
                    v.amount += v.rate;
                    v.amount /= 3;
                    v.amount = local + 1.25;
                    v.amount = roundDown(v.amount / 3, 2);
                }
                """);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        var warnings = domain.numericRoundingWarnings();
        assertEquals(warnings.toString(), 4, warnings.size());
        assertTrue(warnings.toString(), warnings.get(0).contains("local may need rounding"));
        assertTrue(warnings.toString(), warnings.subList(1, 4).stream().allMatch(w -> w.contains("v.amount may need rounding")));
    }

    @Test public void quantumWritesRequireTransfersAtTheAssignmentSite() throws Exception {
        String model = "domain rules; Role Wallet { quantum int balance; int copy; }\n";
        for (String update : java.util.List.of("a.balance = 0", "a.balance += 1", "a.balance -= 1",
                "++a.balance", "a.balance--", "a.balance *= 2", "a.balance /= 2", "a.balance %= 2")) {
            for (String kind : java.util.List.of("atomic calculate", "atomic check", "quantum update")) {
                var errors = compile(model + kind + " change(Wallet a) {\n" + update + "; }").validateBusinessCalculations();
                assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.contains(".spp:3:1 [P0-a]") && e.contains("a -> b : x")));
            }
        }
        assertTrue(compile(model + "quantum update move(Wallet a, Wallet b) { a.balance -> b.copy : 1; }")
                .validateBusinessCalculations().stream().anyMatch(e -> e.contains("quantum parameter attribute")));
        assertTrue(compile(model + "atomic calculate read(Wallet a) { a.copy = a.balance; }")
                .validateBusinessCalculations().isEmpty());
    }

    @Test public void queryCallsAreRejectedAtTheCallSite() throws Exception {
        String model = "domain rules; Role Row { int value; } atomic query read(Row r);\n";
        for (String prefix : java.util.List.of("", "while (true) ", "for (int i=0; i<2; i++) ")) {
            var errors = compile(model + "atomic calculate load(Row r) { " + prefix + "\nread(r); }").validateBusinessCalculations();
            assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.contains(".spp:3:1 [data-access]") && e.contains("array query")));
        }
        assertTrue(compile(model + "Scenario serial preview(Row r) { read(r); }").validateScenarioCalls().isEmpty());
        assertThrows(java.io.IOException.class, () -> compile(model + "Scenario serial preview(Row r) { while (true) { read(r); } }"));
    }

    @Test public void onlyEnumExpressionsSupplyRejectionCodes() throws Exception {
        String model = "domain rules; Enum Fault { INVALID } Role Row { Fault reason; String text; }\n";
        for (String code : java.util.List.of("r.text", "\"INVALID\"", "1")) {
            var errors = compile(model + "atomic check valid(Row r) { require(true,\n" + code + "); }").validateBusinessCalculations();
            assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.contains(".spp:3:1 [require-enum]")));
        }
        assertTrue(compile(model + "atomic check valid(Row r) { require(true, r.reason); require(true, Fault.INVALID); }")
                .validateBusinessCalculations().isEmpty());
    }

    @Test public void rejectNestedServiceCallsButAllowCalculateComposition() throws Exception {
        String model = "domain rules; Role Row { int value; }\n"
                + "atomic calculate twice(Row r) { r.value *= 2; }\n"
                + "atomic check ready(Row r);\nScenario serial preview(Row r) { ready(r); }\n";
        for (String call : java.util.List.of("ready", "preview")) {
            var errors = compile(model + "atomic calculate compose(Row r) {\n" + call + "(r); }").validateBusinessCalculations();
            assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.contains(".spp:6:1 [service-call]")));
        }
        var nested = compile(model + "Scenario serial outer(Row r) {\npreview(r); }").validateAppDeclarations();
        assertTrue(nested.toString(), nested.stream().anyMatch(e -> e.contains(".spp:6:1 [service-call]")));
        assertTrue(compile(model + "atomic calculate compose(Row r) { twice(r); }").validateBusinessCalculations().isEmpty());
    }

    @Test public void migrationWarningRetainsSourceLocation() throws Exception {
        String property = "spp.rule.require-enum";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "warning");
            var domain = compile("domain rules; Role Row { int value; }\natomic check valid(Row r) { require(true, \"OLD_CODE\"); }");
            assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
            assertEquals(1, domain.migrationWarnings().size());
            assertTrue(domain.migrationWarnings().get(0).contains(".spp:2:"));
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private SppDomain compile(String source) throws Exception {
        Path file = Files.createTempFile("spp-calculation", ".spp"); Files.writeString(file, source);
        SppProject project = new SppProject(); new SppCompiler(file.toFile(), project).compile();
        SppDomain domain = project.getSppDomainMap().get("rules"); domain.dealEntityToRoleMaps(); return domain;
    }
}
