package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;
import com.servpp.lang.general.NameUtil;

/** Conservative source-level checks for structurally redundant SPP services. */
public final class StructuralRedundancy {
    private StructuralRedundancy() { }

    private record Service(RedundancyLint.Source source, ParserRuleContext context, Token nameToken,
                           String name, String kind, List<Token> body, List<String> parameters,
                           List<String> types, SppParser.AtomicserviceContext atomic) { }

    public static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        List<Service> services = new ArrayList<>();
        Set<String> constants = new HashSet<>();
        for (var source : sources) {
            if (!(source.tree() instanceof SppParser.ProgramContext program)) continue;
            for (var unit : program.compilationUnits().compilationUnit()) {
                if (unit.constantDeclaration() != null) constants.add(unit.constantDeclaration().Identifier().getText());
                if (unit.atomicservice() != null) {
                    var ctx = unit.atomicservice(); var def = ctx.serviceDefinition();
                    var params = new ArrayList<String>(); var types = new ArrayList<String>();
                    if (def.parameterDeclarations() != null) for (var p : def.parameterDeclarations().parameterDeclaration()) {
                        params.add(p.Identifier() == null ? p.roletype().getText() : p.Identifier().getText());
                        types.add((p.queryKey() == null ? "" : "key ") + (p.roletype() == null ? p.type().getText() : p.roletype().getText()));
                    }
                    services.add(new Service(source, ctx, def.Identifier().getSymbol(), def.Identifier().getText(),
                            (ctx.quantumService() == null ? "" : "quantum ") + ctx.servicetype().getText(),
                            ctx.calculationBody() == null ? List.of() : terminals(ctx.calculationBody()), params, types, ctx));
                } else if (unit.scenario() != null) {
                    var ctx = unit.scenario(); var def = ctx.serviceDefinition();
                    var params = new ArrayList<String>(); var types = new ArrayList<String>();
                    if (def.parameterDeclarations() != null) for (var p : def.parameterDeclarations().parameterDeclaration()) {
                        params.add(p.Identifier() == null ? p.roletype().getText() : p.Identifier().getText());
                        types.add((p.queryKey() == null ? "" : "key ") + (p.roletype() == null ? p.type().getText() : p.roletype().getText()));
                    }
                    services.add(new Service(source, ctx, def.Identifier().getSymbol(), def.Identifier().getText(),
                            "scenario " + (ctx.readonlyModifier() == null ? "" : "readonly ")
                                    + (ctx.autosort() == null ? "" : ctx.autosort().getText()),
                            terminals(ctx.scenarioDeclaration()), params, types, null));
                }
            }
        }
        checkDuplicateServices(services, constants, reporter);
        checkPresenceChecks(services, sources, reporter);
        checkPairedParticipants(services, reporter);
    }

    private static void checkDuplicateServices(List<Service> services, Set<String> constants,
                                               RedundancyLint.Reporter reporter) {
        List<List<String>> signatures = services.stream()
                .map(service -> canonicalTokens(service.body(), service.parameters(), service.types())).toList();
        Set<ParserRuleContext> reported = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < services.size(); i++) for (int j = i + 1; j < services.size(); j++) {
            Service left = services.get(i), right = services.get(j);
            if (left.body().isEmpty() || right.body().isEmpty() || !left.kind().equals(right.kind())
                    || !left.types().equals(right.types())) continue;
            if (signatures.get(i).equals(signatures.get(j))
                    || differsByOneConstant(left.body(), right.body(), constants)) {
                if (!reported.add(right.context())) continue;
                reporter.warn(right.source(), right.nameToken(), "SPP_REDUNDANT_DUPLICATE_SERVICE",
                        "Services '" + left.name() + "' and '" + right.name()
                                + "' repeat the same operations. Merge them or use an array participant.");
            }
        }
    }

    private static List<String> canonicalTokens(List<Token> tokens, List<String> params, List<String> types) {
        List<String> out = new ArrayList<>();
        String previous = "";
        for (Token token : tokens) {
            int index = previous.equals(".") ? -1 : params.indexOf(token.getText());
            previous = token.getText();
            out.add(index >= 0 && !params.get(index).isEmpty() ? "<participant:" + index + ":" + types.get(index) + ">"
                    : token.getType() + ":" + token.getText());
        }
        return out;
    }

    private static boolean differsByOneConstant(List<Token> a, List<Token> b, Set<String> constants) {
        if (a.size() != b.size()) return false;
        int differences = 0;
        for (int i = 0; i < a.size(); i++) if (a.get(i).getType() != b.get(i).getType()
                || !a.get(i).getText().equals(b.get(i).getText())) {
            if (!constantToken(a.get(i), constants) || !constantToken(b.get(i), constants) || ++differences > 1) return false;
        }
        return differences == 1;
    }

    private static boolean constantToken(Token token, Set<String> constants) {
        return constants.contains(token.getText()) || token.getType() == SppParser.StringLiteral
                || token.getType() == SppParser.IntegerLiteral || token.getType() == SppParser.FloatingPointLiteral
                || token.getType() == SppParser.NullLiteral || token.getType() == SppParser.BooleanLiteral;
    }

    private static void checkPresenceChecks(List<Service> services, List<RedundancyLint.Source> sources,
                                           RedundancyLint.Reporter reporter) {
        Map<String, List<SppParser.AtomiccallContext>> calls = new HashMap<>();
        Set<String> bindings = new HashSet<>();
        for (var source : sources) {
            if (source.tree() instanceof SppParser.ProgramContext program) {
                for (var call : descendants(program, SppParser.AtomiccallContext.class))
                    if (call.Identifier() != null) calls.computeIfAbsent(canonical(call.Identifier().getText()),
                            ignored -> new ArrayList<>()).add(call);
            } else if (source.tree() instanceof AppParser.ProgramContext app) {
                for (var token : terminals(app))
                    if (token.getType() == AppParser.Identifier) bindings.add(canonical(token.getText()));
            }
        }
        for (Service service : services) {
            var ctx = service.atomic();
            if (ctx == null || !service.kind().equals("check") || ctx.calculationBody() == null
                    || service.parameters().size() != 1 || ctx.serviceDefinition().parameterDeclarations()
                    .parameterDeclaration(0).roletype() == null) continue;
            String name = canonical(service.name());
            var uses = calls.getOrDefault(name, List.of());
            if (uses.size() != 1 || bindings.contains(name)) continue;
            var statements = ctx.calculationBody().calculationStatement();
            if (statements.size() != 1 || statements.get(0).rejectionCode() == null
                    || !statements.get(0).expression().getText().equals(service.parameters().get(0))) continue;
            var call = uses.get(0);
            SppParser.ScenarioContext scenario = null;
            for (var ancestor = call.getParent(); ancestor != null; ancestor = ancestor.getParent())
                if (ancestor instanceof SppParser.ScenarioContext found) { scenario = found; break; }
            if (scenario == null) continue;
            String actual;
            if (call.parameters() == null) {
                var params = scenario.serviceDefinition().parameterDeclarations();
                if (params == null) continue;
                var matches = params.parameterDeclaration().stream().filter(p -> p.roletype() != null
                        && p.roletype().getText().equals(service.types().get(0))).toList();
                if (matches.size() != 1) continue;
                actual = matches.get(0).Identifier() == null ? matches.get(0).roletype().getText() : matches.get(0).Identifier().getText();
            } else {
                if (call.parameters().parameter().size() != 1) continue;
                actual = call.parameters().parameter(0).getText();
            }
            String code = statements.get(0).rejectionCode().getText();
            String hint;
            if (code.equals("\"PARTICIPANT_NOT_IDENTIFIED\"")) {
                hint = "Inline identify(" + actual + ") in scenario " + scenario.serviceDefinition().Identifier().getText() + ".";
            } else {
                // Scenario syntax supports custom failure codes inside atomic bodies only.
                // Fold the guard into the next single-use check on the same argument.
                if (!call.getParent().getClass().equals(SppParser.SerialgroupContext.class)
                        && !(call.getParent() instanceof SppParser.ExecutegroupContext && scenario.autosort() != null
                        && scenario.autosort().getText().equals("serial"))) continue;
                var next = nextCall(call);
                if (next == null || next.Identifier() == null || next.parameters() == null
                        || !next.parameters().getText().equals(actual) || call.scenarioBranch() != null
                        || call.transactionBreak() != null || next.scenarioBranch() != null) continue;
                String nextName = canonical(next.Identifier().getText());
                if (bindings.contains(nextName) || calls.getOrDefault(nextName, List.of()).size() != 1) continue;
                var destination = services.stream().filter(s -> canonical(s.name()).equals(nextName)
                        && s.kind().equals("check") && !s.body().isEmpty() && s.types().equals(service.types())).findFirst();
                if (destination.isEmpty() || !(code.startsWith("\"") || code.matches("[\\p{L}_][\\p{L}\\p{N}_.]*"))) continue;
                hint = "Inline require(" + destination.get().parameters().get(0) + ", " + code + ") at the start of "
                        + destination.get().name() + ", the next check in " + scenario.serviceDefinition().Identifier().getText() + ".";
            }
            reporter.warn(service.source(), service.nameToken(), "SPP_REDUNDANT_PRESENCE_CHECK",
                    service.name() + " checks one participant at its only call. " + hint);
        }
    }

    private static SppParser.AtomiccallContext nextCall(SppParser.AtomiccallContext call) {
        var parent = call.getParent();
        for (int i = 1; i < parent.getChildCount(); i++) {
            if (parent.getChild(i - 1) == call && parent.getChild(i) instanceof SppParser.AtomiccallContext next) return next;
        }
        return null;
    }

    private static String canonical(String name) { return NameUtil.firstToLowerCase(name, true); }

    private static void checkPairedParticipants(List<Service> services, RedundancyLint.Reporter reporter) {
        for (Service service : services) {
            if (service.parameters().size() < 2) continue;
            if (service.atomic() == null) {
                checkScenarioPairedParticipants(service, reporter);
                continue;
            }
            var statements = service.atomic().calculationBody() == null ? List.<SppParser.CalculationStatementContext>of()
                    : service.atomic().calculationBody().calculationStatement();
            if (statements.isEmpty()) continue;
            for (int i = 0; i < service.parameters().size(); i++) for (int j = i + 1; j < service.parameters().size(); j++) {
                String a = service.parameters().get(i), b = service.parameters().get(j);
                if (a.isEmpty() || b.isEmpty() || !service.types().get(i).equals(service.types().get(j)) || !pairedNames(a, b)) continue;
                if (processedIdentically(statements, a, b)) {
                    reporter.warn(service.source(), service.nameToken(), "SPP_REDUNDANT_PAIRED_PARTICIPANTS",
                            "Participants '" + a + "' and '" + b + "' of the same type are processed alike; use an array participant.");
                    i = service.parameters().size(); break;
                }
            }
        }
    }

    private static void checkScenarioPairedParticipants(Service service, RedundancyLint.Reporter reporter) {
        if (!(service.context() instanceof SppParser.ScenarioContext scenario)) return;
        var group = scenario.scenarioDeclaration().executegroup();
        var calls = group.atomiccall();
        if (calls.size() != group.getChildCount() || calls.stream().anyMatch(call -> call.scenarioBranch() != null)) return;
        for (int i = 0; i < service.parameters().size(); i++) for (int j = i + 1; j < service.parameters().size(); j++) {
            String a = service.parameters().get(i), b = service.parameters().get(j);
            if (a.isEmpty() || b.isEmpty() || !service.types().get(i).equals(service.types().get(j)) || !pairedNames(a, b)) continue;
            List<List<String>> left = new ArrayList<>(), right = new ArrayList<>();
            boolean mixed = false;
            for (var call : calls) {
                List<Token> tokens = terminals(call);
                boolean hasA = hasIdentifier(tokens, a), hasB = hasIdentifier(tokens, b);
                if (hasA && hasB) { mixed = true; break; }
                if (hasA == hasB) continue;
                String target = hasA ? a : b;
                List<String> normalized = new ArrayList<>();
                Token callName = call.Identifier() == null ? null : call.Identifier().getSymbol();
                for (Token token : tokens) normalized.add(token.getType() == SppParser.Identifier && token.getText().equals(target)
                        ? "<participant>" : token == callName ? token.getType() + ":" + token.getText().toLowerCase(Locale.ROOT)
                        : token.getType() + ":" + token.getText());
                (hasA ? left : right).add(normalized);
            }
            if (!mixed && !left.isEmpty() && left.equals(right)) {
                reporter.warn(service.source(), service.nameToken(), "SPP_REDUNDANT_PAIRED_PARTICIPANTS",
                        "Scenario participants '" + a + "' and '" + b + "' of the same type are processed alike; use an array participant.");
                return;
            }
        }
    }

    private static boolean processedIdentically(List<SppParser.CalculationStatementContext> statements, String a, String b) {
        List<List<String>> left = new ArrayList<>(), right = new ArrayList<>();
        for (var statement : statements) {
            List<Token> tokens = terminals(statement);
            boolean hasA = hasIdentifier(tokens, a), hasB = hasIdentifier(tokens, b);
            if (hasA && hasB) return false;
            if (!hasA && !hasB) continue;
            String target = hasA ? a : b;
            List<String> normalized = new ArrayList<>();
            for (Token token : tokens) normalized.add(token.getText().equals(target) ? "<participant>"
                    : token.getType() + ":" + token.getText());
            (hasA ? left : right).add(normalized);
        }
        return !left.isEmpty() && left.equals(right);
    }

    private static boolean pairedNames(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT), y = b.toLowerCase(Locale.ROOT);
        if (x.equals("a") && y.equals("b") || x.equals("b") && y.equals("a")) return true;
        for (String[] pair : List.of(new String[]{"source", "target"}, new String[]{"first", "second"})) {
            if (x.startsWith(pair[0]) && y.startsWith(pair[1])
                    && x.substring(pair[0].length()).equals(y.substring(pair[1].length()))) return true;
            if (y.startsWith(pair[0]) && x.startsWith(pair[1])
                    && y.substring(pair[0].length()).equals(x.substring(pair[1].length()))) return true;
            if (x.endsWith(pair[0]) && y.endsWith(pair[1])
                    && x.substring(0, x.length() - pair[0].length()).equals(y.substring(0, y.length() - pair[1].length()))) return true;
            if (y.endsWith(pair[0]) && x.endsWith(pair[1])
                    && y.substring(0, y.length() - pair[0].length()).equals(x.substring(0, x.length() - pair[1].length()))) return true;
        }
        return x.matches(".+[12]") && y.equals(x.substring(0, x.length() - 1) + (x.endsWith("1") ? "2" : "1"));
    }

    private static boolean hasIdentifier(List<Token> tokens, String name) {
        return tokens.stream().anyMatch(token -> token.getType() == SppParser.Identifier && token.getText().equals(name));
    }

    private static List<Token> terminals(ParseTree tree) {
        List<Token> tokens = new ArrayList<>(); collectTokens(tree, tokens); return tokens;
    }

    private static void collectTokens(ParseTree tree, List<Token> out) {
        if (tree instanceof TerminalNode terminal) out.add(terminal.getSymbol());
        else for (int i = 0; i < tree.getChildCount(); i++) collectTokens(tree.getChild(i), out);
    }

    private static <T extends ParserRuleContext> List<T> descendants(ParseTree tree, Class<T> kind) {
        List<T> found = new ArrayList<>(); collectDescendants(tree, kind, found); return found;
    }

    private static <T extends ParserRuleContext> void collectDescendants(ParseTree tree, Class<T> kind, List<T> found) {
        if (kind.isInstance(tree)) found.add(kind.cast(tree));
        for (int i = 0; i < tree.getChildCount(); i++) collectDescendants(tree.getChild(i), kind, found);
    }
}
