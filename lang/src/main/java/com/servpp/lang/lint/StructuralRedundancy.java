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
        Set<String> constants = new HashSet<>();
        var services = services(sources, constants);
        checkDuplicateServices(services, constants, reporter);
        presenceChecks(services, sources, plan -> reporter.warn(plan.source(), plan.guard().serviceDefinition().Identifier().getSymbol(),
                "SPP_REDUNDANT_PRESENCE_CHECK", plan.hint()));
        checkPairedParticipants(services, reporter);
    }

    private static List<Service> services(List<RedundancyLint.Source> sources, Set<String> constants) {
        List<Service> services = new ArrayList<>();
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
        return services;
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

    record Presence(RedundancyLint.Source source, SppParser.AtomicserviceContext guard,
                    SppParser.AtomiccallContext call, String replacement, Service destination,
                    String requirement, String hint) {
        SppParser.CalculationBodyContext destinationBody() { return destination == null ? null : destination.atomic().calculationBody(); }
        String destinationPath() { return destination.source().path(); }
    }

    static Presence presence(List<RedundancyLint.Source> sources, Token token) {
        var matches = new ArrayList<Presence>();
        presenceChecks(services(sources, new HashSet<>()), sources, p -> {
            if (p.guard().serviceDefinition().Identifier().getSymbol() == token) matches.add(p);
        });
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static void presenceChecks(List<Service> services, List<RedundancyLint.Source> sources,
                                       java.util.function.Consumer<Presence> report) {
        Map<String, List<SppParser.AtomiccallContext>> calls = new HashMap<>();
        Set<String> bindings = new HashSet<>();
        Set<String> enumCodes = new HashSet<>();
        Set<String> builtinCodes = new HashSet<>(Set.of("\"PARTICIPANT_NOT_IDENTIFIED\""));
        for (var source : sources) {
            if (source.tree() instanceof SppParser.ProgramContext program) {
                for (var call : descendants(program, SppParser.AtomiccallContext.class))
                    if (call.Identifier() != null) calls.computeIfAbsent(canonical(call.Identifier().getText()),
                            ignored -> new ArrayList<>()).add(call);
                for (var enumeration : descendants(program, SppParser.EnumerationContext.class))
                    for (var item : enumeration.enumBody().enumBodyIdentifier()) {
                        String member = enumeration.Identifier().getText() + "." + item.Identifier().getText();
                        enumCodes.add(member);
                        if (!item.StringLiteral().isEmpty() && item.StringLiteral(0).getText().equals("\"PARTICIPANT_NOT_IDENTIFIED\""))
                            builtinCodes.add(member);
                    }
            } else if (source.tree() instanceof AppParser.ProgramContext app) {
                for (var id : descendants(app, TerminalNode.class)) {
                    if (id.getSymbol().getType() != AppParser.Identifier) continue;
                    if (id.getParent() instanceof AppParser.ScopeItemIdentifierContext out
                            && plainLocal((AppParser.ScopeItemContext) out.getParent())) continue;
                    bindings.add(canonical(id.getText()));
                }
            }
        }
        for (var service : services) {
            var code = presenceCode(service);
            if (code == null) continue;
            String name = canonical(service.name());
            var uses = calls.getOrDefault(name, List.of());
            if (uses.size() != 1 || bindings.contains(name)) continue;
            var call = uses.get(0);
            var actuals = actuals(call, service);
            if (actuals.size() != 1 || call.quantumBounds() != null || call.readBinding() != null
                    || call.uniqueModifier() != null) continue;
            String actual = actuals.get(0);
            if (builtinCodes.contains(code)) {
                String replacement = "identify(" + actual + ")";
                report.accept(new Presence(service.source(), service.atomic(), call, replacement, null, null,
                        "Inline " + replacement + " at the only call to " + name + "."));
                continue;
            }
            if (!enumCodes.contains(code) && !literalRejection(service)) continue;
            var next = nextCall(call);
            if (ServiceRedundancy.inSerialGroup(call) && next != null && next.Identifier() != null
                    && call.scenarioBranch() == null && call.transactionBreak() == null && next.scenarioBranch() == null
                    && next.transactionBreak() == null && next.quantumBounds() == null) {
                String nextName = canonical(next.Identifier().getText());
                var dest = services.stream().filter(s -> canonical(s.name()).equals(nextName)).toList();
                if (dest.size() == 1 && !bindings.contains(nextName) && calls.getOrDefault(nextName, List.of()).size() == 1) {
                    Service target = dest.get(0);
                    var args = actuals(next, target);
                    int i = args.indexOf(actual);
                    if (target.kind().equals("check") && !target.body().isEmpty() && i >= 0
                            && target.types().get(i).equals(service.types().get(0))) {
                        String requirement = "require(" + target.parameters().get(i) + ", " + code + ");";
                        report.accept(new Presence(service.source(), service.atomic(), call, "", target, requirement,
                                "Inline " + requirement + " at the start of " + target.name() + "."));
                        continue;
                    }
                }
            }
            // A read between two single-use guards must still be skipped if the first one fails.
            // Folding this pattern needs an extra parameter and an explicit scenario branch.
            if (ServiceRedundancy.inSerialGroup(call) && next != null && next.Identifier() != null
                    && next.scenarioBranch() == null && next.transactionBreak() == null && call.scenarioBranch() == null) {
                var following = nextCall(next);
                if (following != null && following.Identifier() != null && following.scenarioBranch() == null
                        && (call.transactionBreak() == null) == (following.transactionBreak() == null)) {
                    String followingName = canonical(following.Identifier().getText());
                    var other = services.stream().filter(s -> canonical(s.name()).equals(followingName)).findFirst();
                    if (other.isPresent() && presenceCode(other.get()) != null && !bindings.contains(followingName)
                            && calls.get(followingName).size() == 1) {
                        var args = actuals(following, other.get());
                        boolean read = canonical(next.Identifier().getText()).equals("find" + other.get().types().get(0))
                                && next.parameters() != null && args.size() == 1
                                && next.parameters().getText().equals(args.get(0)) && !actual.equals(args.get(0))
                                && services.stream().noneMatch(s -> canonical(s.name()).equals(canonical(next.Identifier().getText()))
                                && !s.kind().equals("query"));
                        if (read) {
                            report.accept(new Presence(service.source(), service.atomic(), call, null, null, null,
                                    "Guard the intervening read with if (" + actual + ") and combine the two presence checks at "
                                            + followingName + ". Preserve rejection order (" + code + " then " + presenceCode(other.get())
                                            + ") and the final call's transaction behavior."));
                            continue;
                        }
                    }
                }
            }
            // Prefer an already shared guard. A stable tie-breaker prevents mutually deleting aliases.
            var equivalent = services.stream().filter(s -> s != service && s.types().equals(service.types())
                    && code.equals(presenceCode(s)) && !bindings.contains(canonical(s.name())))
                    .filter(s -> {
                        int usesOfOther = calls.getOrDefault(canonical(s.name()), List.of()).size();
                        return usesOfOther > 1 || usesOfOther == 1 && s.name().compareTo(service.name()) < 0;
                    }).sorted(Comparator.comparing(Service::name)).findFirst();
            if (equivalent.isPresent()) {
                String replacement = equivalent.get().name() + "(" + actual + ")";
                report.accept(new Presence(service.source(), service.atomic(), call, null, null, null,
                        "Reuse " + replacement + " at this guard's only call to preserve " + code + "."));
            }
        }
    }

    static boolean plainLocal(AppParser.ScopeItemContext item) {
        return item.getChildCount() == 2 && item.getParent() instanceof AppParser.LocalBodyContext local
                && local.scopeParameter() == null;
    }

    private static String presenceCode(Service s) {
        if (!s.kind().equals("check") || s.atomic().calculationBody() == null || s.types().size() != 1
                || s.atomic().serviceDefinition().parameterDeclarations().parameterDeclaration(0).roletype() == null
                || s.types().get(0).contains("[")) return null;
        var statements = s.atomic().calculationBody().calculationStatement();
        if (statements.size() != 1 || statements.get(0).rejectionCode() == null) return null;
        var expression = statements.get(0).expression();
        while (expression instanceof SppParser.ParenthesesExprContext parens) expression = parens.expression();
        return expression.getText().equals(s.parameters().get(0)) ? statements.get(0).rejectionCode().getText() : null;
    }

    private static boolean literalRejection(Service service) {
        var expression = service.atomic().calculationBody().calculationStatement(0).rejectionCode().expression();
        return expression.getStart() == expression.getStop() && expression.getStart().getType() == SppParser.StringLiteral;
    }

    private static List<String> actuals(SppParser.AtomiccallContext call, Service service) {
        SppParser.ScenarioContext scenario = null;
        for (var p = call.getParent(); p != null; p = p.getParent())
            if (p instanceof SppParser.ScenarioContext s) { scenario = s; break; }
        if (scenario == null || scenario.serviceDefinition().parameterDeclarations() == null) return List.of();
        if (call.parameters() != null) {
            var args = call.parameters().parameter();
            return args.size() == service.parameters().size() ? args.stream().map(ParseTree::getText).toList() : List.of();
        }
        var args = new ArrayList<String>();
        for (var type : service.types()) {
            var matches = scenario.serviceDefinition().parameterDeclarations().parameterDeclaration().stream()
                    .filter(p -> p.roletype() != null && p.roletype().getText().equals(type)).toList();
            if (matches.size() != 1) return List.of();
            var p = matches.get(0);
            args.add(p.Identifier() == null ? type : p.Identifier().getText());
        }
        return args;
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
        var calls = descendants(scenario.scenarioDeclaration(), SppParser.AtomiccallContext.class);
        for (int i = 0; i < service.parameters().size(); i++) for (int j = i + 1; j < service.parameters().size(); j++) {
            String a = service.parameters().get(i), b = service.parameters().get(j);
            if (!service.types().get(i).equals(service.types().get(j)) || !pairedNames(a, b)) continue;
            Map<ParserRuleContext, List<List<String>>> left = new HashMap<>(), right = new HashMap<>();
            boolean unsupported = false;
            boolean separateRead = false, joint = false;
            for (var call : calls) {
                var tokens = terminals(call);
                boolean hasA = hasIdentifier(tokens, a), hasB = hasIdentifier(tokens, b);
                if (!hasA && !hasB) continue;
                if (call.scenarioBranch() != null) { unsupported = true; break; }
                if (hasA && hasB) { joint = true; continue; }
                String target = hasA ? a : b;
                var normalized = canonicalTokens(tokens, List.of(target), List.of(service.types().get(i)));
                (hasA ? left : right).computeIfAbsent(call.getParent(), ignored -> new ArrayList<>()).add(normalized);
                separateRead |= call.Identifier() != null && canonical(call.Identifier().getText()).startsWith("find");
            }
            if (!unsupported && !left.isEmpty() && left.equals(right)
                    && (separateRead || !joint)) {
                reporter.warn(service.source(), service.nameToken(), "SPP_REDUNDANT_PAIRED_PARTICIPANTS",
                        "Scenario participants '" + a + "' and '" + b + "' have matching individual calls in the same blocks. "
                                + "Use an ordered array for the repeated operations and preserve their positions at joint calls.");
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
            var normalized = canonicalTokens(tokens, List.of(target), List.of("paired"));
            // Mirror a named output slot (result.sourceFound/result.targetFound) or local
            // (sourceLevel/targetLevel). All input fields, operators and constants stay literal.
            Token output = null;
            var assignment = statement.calculationAssignment();
            if (assignment != null && assignment.qualifiedName() != null)
                output = assignment.qualifiedName().getStop();
            var local = statement.calculationLocal();
            if (local != null && local.primitiveType() != null && local.Identifier().size() == 1)
                output = local.Identifier(0).getSymbol();
            if (output != null && output.getText().startsWith(target) && output.getText().length() > target.length()
                    && Character.isUpperCase(output.getText().charAt(target.length())))
                normalized.set(tokens.indexOf(output), "<output>" + output.getText().substring(target.length()));
            (hasA ? left : right).add(normalized);
        }
        return !left.isEmpty() && left.equals(right);
    }

    private static boolean pairedNames(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT), y = b.toLowerCase(Locale.ROOT);
        if (x.equals("a") && y.equals("b") || x.equals("b") && y.equals("a")) return true;
        for (String[] pair : List.of(new String[]{"source", "target"}, new String[]{"first", "second"}, new String[]{"local", "internal"})) {
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

    private static <T extends ParseTree> List<T> descendants(ParseTree tree, Class<T> kind) {
        List<T> found = new ArrayList<>(); collectDescendants(tree, kind, found); return found;
    }

    private static <T extends ParseTree> void collectDescendants(ParseTree tree, Class<T> kind, List<T> found) {
        if (kind.isInstance(tree)) found.add(kind.cast(tree));
        for (int i = 0; i < tree.getChildCount(); i++) collectDescendants(tree.getChild(i), kind, found);
    }
}
