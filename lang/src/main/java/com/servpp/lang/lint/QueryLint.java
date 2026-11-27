package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.lint.DataAccessLint.Effect;
import com.servpp.lang.models.app.expr.NumericJavaRenderer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.servpp.lang.antlr.SppParser.*;

/** Source-level query and transfer checks for AI-facing diagnostics revision 2. */
public final class QueryLint {
    private final DataAccessLint effects;
    private final RedundancyLint.Reporter reporter;
    private final Map<String, AtomicserviceContext> services = new HashMap<>();
    private final Set<String> ambiguous = new HashSet<>();
    private final Map<String, Map<String, String>> fields;
    private final Map<String, List<AppParser.TableDefineContext>> tables = new HashMap<>();
    private final Map<String, List<AppParser.RepositoryModifierContext>> repositoryTargets = new HashMap<>();
    private final Set<String> externalProviders = new HashSet<>();
    private final Set<String> physicalFloors = new HashSet<>();
    private final List<ActasContext> takes = new ArrayList<>();

    private QueryLint(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        this.reporter = reporter;
        effects = DataAccessLint.indexed(sources);
        fields = LocalRedundancy.typeMaps(sources);
        for (var source : sources) {
            if (source.tree() instanceof ProgramContext spp) {
                for (var unit : spp.compilationUnits().compilationUnit()) {
                    if (unit.actas() != null) takes.add(unit.actas());
                    var service = unit.atomicservice();
                    if (service == null) continue;
                    String name = method(service.serviceDefinition().Identifier().getText());
                    if (services.putIfAbsent(name, service) != null) ambiguous.add(name);
                }
            } else if (source.tree() instanceof AppParser.ProgramContext app) {
                for (var binding : nodes(app, AppParser.LocalServiceBindingContext.class))
                    externalProviders.add(method(binding.Identifier().getText()));
                for (var remote : nodes(app, AppParser.RemoteBodyContext.class))
                    for (var item : remote.scopeItem()) externalProviders.add(method(item.scopeItemIdentifier().getText()));
                for (var table : nodes(app, AppParser.TableDefineContext.class))
                    for (var type : table.identifyList().identifyItem())
                        tables.computeIfAbsent(type.getText(), ignored -> new ArrayList<>()).add(table);
                for (var item : nodes(app, AppParser.ScopeItemContext.class))
                    for (var mod : item.scopeModifier())
                        if (mod instanceof AppParser.RepositoryModifierContext repo)
                            repositoryTargets.computeIfAbsent(method(item.scopeItemIdentifier().getText()), ignored -> new ArrayList<>())
                                    .add(repo);
                for (var def : nodes(app, AppParser.PhyQuantumDefinitionContext.class))
                    for (var item : def.phyQuantumDefinitionItem())
                        if (item.phyQuantumMinValue() != null && nonnegative(unquote(item.phyQuantumMinValue().StringLiteral().getText())))
                            physicalFloors.add(unquote(def.StringLiteral().getText()));
            }
        }
    }

    public static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        var lint = new QueryLint(sources, reporter);
        for (var source : sources) {
            if (!(source.tree() instanceof ProgramContext spp)) continue;
            for (var unit : spp.compilationUnits().compilationUnit())
                if (unit.scenario() != null) lint.scenario(source, unit.scenario());
        }
    }

    private void scenario(RedundancyLint.Source source, ScenarioContext scene) {
        var body = scene.scenarioDeclaration().executegroup();
        var participants = parameters(scene.serviceDefinition());
        var calls = new IdentityHashMap<AtomiccallContext, Invocation>();
        for (var call : nodes(body, AtomiccallContext.class)) {
            if (call.Identifier() == null) continue;
            var invocation = bind(call, participants);
            if (invocation == null) continue;
            calls.put(call, invocation);
            if (invocation.effect == Effect.QUERY) indexSuggestion(source, invocation, participants);
            if (invocation.service != null && invocation.service.quantumService() != null)
                transferBounds(source, invocation);
        }
        for (var tx : nodes(body, TransactionStatementsContext.class)) {
            // Propagation, rollback handlers and explicit aborts give the boundary its own meaning.
            if (tx.transactionFailure() != null || tx.transactionPropagation() != null
                    || nodes(tx, TransactionStatementsContext.class).size() > 1) continue;
            var reads = nodes(tx, AtomiccallContext.class);
            if (reads.stream().anyMatch(c -> effects.effect(c) == Effect.QUERY)
                    && reads.stream().allMatch(c -> c.transactionBreak() == null
                    && (effects.effect(c) == Effect.QUERY || effects.effect(c) == Effect.CHECK)))
                reporter.warn(source, tx.getStart(), "SPP_READ_ONLY_TRANSACTION",
                        "This transaction contains only reads. Review whether they require a shared transaction.");
        }
        boolean serial = scene.autosort() != null && scene.autosort().getText().equals("serial");
        var pending = new LinkedHashMap<ParameterDeclarationContext, Result>();
        sequence(source, body, serial, calls, pending);
        for (var result : pending.values()) result.finish(source, reporter, false);
    }

    private record Invocation(AtomiccallContext call, AtomicserviceContext service, Effect effect,
                              List<ParameterDeclarationContext> actuals, int output) { }

    private Invocation bind(AtomiccallContext call, List<ParameterDeclarationContext> participants) {
        String name = method(call.Identifier().getText());
        if (ambiguous.contains(name) || externalProviders.contains(name)) return null;
        var service = services.get(name);
        var effect = effects.effect(call);
        if (service == null && (effect != Effect.QUERY || !name.startsWith("find"))) return null;
        var formals = service == null ? List.<ParameterDeclarationContext>of() : parameters(service.serviceDefinition());
        int count = service == null ? 1 : formals.size();
        if (call.parameters() != null && call.parameters().parameter().size() != count) return null;
        var actuals = new ArrayList<ParameterDeclarationContext>();
        for (int i = 0; i < count; i++) {
            String type = service == null ? name.substring(4) : type(formals.get(i));
            String arg = call.parameters() == null ? null : call.parameters().parameter(i).getText();
            var matches = participants.stream().filter(p -> p.roletype() != null && (service == null
                    ? p.roletype().Identifier().getText().equals(type) : type(p).equals(type)))
                    .filter(p -> arg == null || name(p).equals(arg)).toList();
            if (matches.size() != 1) return null;
            actuals.add(matches.get(0));
        }
        int output = -1;
        if (effect == Effect.QUERY) {
            var targets = repositoryTargets.get(name);
            if (targets != null && service != null) {
                for (int i = 0; i < count; i++)
                    if (targets.size() == 1 && targets.get(0).qualifiedName().getText().equals(name(formals.get(i)))) output = i;
            } else if (count == 1) output = 0;
            else if (count == 2) {
                for (int i = 0; i < count; i++)
                    if (array(actuals.get(i)) && !array(actuals.get(1 - i))) output = i;
            }
        }
        return new Invocation(call, service, effect, actuals, output);
    }

    private static List<ParameterDeclarationContext> parameters(ServiceDefinitionContext definition) {
        return definition.parameterDeclarations() == null ? List.of() : definition.parameterDeclarations().parameterDeclaration();
    }

    private static String type(ParameterDeclarationContext p) {
        return p.roletype() == null ? p.type().getText() : p.roletype().getText();
    }

    private static String name(ParameterDeclarationContext p) {
        return com.servpp.lang.ParticipantNames.bindingName(p);
    }

    private static boolean array(ParameterDeclarationContext p) { return type(p).endsWith("[]"); }
    private static String method(String name) { return NameUtil.firstToLowerCase(name, true); }

    private record Result(AtomiccallContext origin, boolean firstOnly) {
        void finish(RedundancyLint.Source source, RedundancyLint.Reporter reporter, boolean replaced) {
            if (firstOnly)
                reporter.warn(source, origin.Identifier().getSymbol(), "SPP_QUERY_FIRST_ONLY",
                        "Only the first match is read in this scenario. Consider a scalar unique find if duplicates should be rejected.");
            else if (replaced)
                reporter.warn(source, origin.Identifier().getSymbol(), "SPP_UNUSED_QUERY_RESULT",
                        "The query result is replaced before it is read. Review whether the earlier lookup is needed.");
        }
    }

    // Carry evidence through ordered blocks. At a control-flow join an unexamined path
    // may consume or preserve the participant, so discard the evidence from that path.
    private void sequence(RedundancyLint.Source source, ParserRuleContext block, boolean serial,
                          Map<AtomiccallContext, Invocation> calls, Map<ParameterDeclarationContext, Result> pending) {
        var steps = block.getRuleContexts(ParserRuleContext.class);
        if (!serial && steps.size() > 1) {
            // A concurrently executing sibling could observe the result between two serial calls.
            pending.clear();
            return;
        }
        for (var step : steps) {
            if (step instanceof AtomiccallContext call) {
                var invocation = calls.get(call);
                if (invocation != null && call.scenarioBranch() == null && call.transactionBreak() == null) {
                    use(source, invocation, pending);
                } else {
                    pending.clear();
                    if (call.scenarioBranch() != null)
                        sequence(source, call.scenarioBranch(), true, calls, new LinkedHashMap<>());
                }
            } else if (step instanceof SerialgroupContext || step instanceof ParallelgroupContext) {
                sequence(source, step, step instanceof SerialgroupContext, calls, pending);
            } else if (step instanceof TransactionStatementsContext tx && tx.transactionFailure() == null) {
                sequence(source, tx, true, calls, pending);
            } else {
                pending.clear();
                if (step instanceof ScenarioIfContext conditional) {
                    for (var branch = conditional; branch != null; branch = branch.scenarioIf())
                        for (var arm : branch.scenarioBranch())
                            sequence(source, arm, true, calls, new LinkedHashMap<>());
                }
            }
        }
    }

    private void use(RedundancyLint.Source source, Invocation invocation, Map<ParameterDeclarationContext, Result> pending) {
        var call = invocation.call;
        // A scalar find with constant keys supplies a new identity without reading the
        // previously hydrated participant. A bare scalar find can reuse its current keys.
        if (rebindsIdentity(invocation)) {
            var previous = pending.remove(invocation.actuals.get(0));
            if (previous != null) previous.finish(source, reporter, true);
        }
        // Read bindings, bounds and aliased arguments may observe more than the formal arguments show.
        if (call.quantumBounds() != null || call.readBinding() != null
                || new HashSet<>(invocation.actuals).size() != invocation.actuals.size()) pending.clear();
        for (int i = 0; i < invocation.actuals.size(); i++) {
            var arg = invocation.actuals.get(i);
            var prior = pending.remove(arg);
            if (prior == null) continue;
            if (invocation.effect == Effect.CALCULATE && array(arg) && invocation.service.calculationBody() != null) {
                var formal = parameters(invocation.service.serviceDefinition()).get(i);
                if (firstOnly(invocation.service.calculationBody(), name(formal)))
                    pending.put(arg, new Result(prior.origin, true));
                else if (cleared(invocation.service.calculationBody(), formal))
                    prior.finish(source, reporter, true);
            } else if (i == invocation.output && separateSelector(invocation)) {
                prior.finish(source, reporter, true);
            }
        }
        if (invocation.output >= 0 && call.uniqueModifier() == null)
            pending.put(invocation.actuals.get(invocation.output), new Result(call, false));
    }

    private static boolean rebindsIdentity(Invocation invocation) {
        var binding = invocation.call.readBinding();
        if (invocation.service != null || invocation.output != 0 || binding == null) return false;
        var target = invocation.actuals.get(0);
        return binding.readKey().stream().allMatch(key -> {
            if (key.readConstant() == null || key.qualifiedName() == null) return false;
            String qualifier = key.qualifiedName().Identifier(0).getText();
            return qualifier.equals(type(target)) || qualifier.equals(name(target));
        });
    }

    private boolean separateSelector(Invocation invocation) {
        if (invocation.actuals.size() < 2) return false;
        var bindings = repositoryTargets.get(method(invocation.call.Identifier().getText()));
        if (bindings == null) return array(invocation.actuals.get(invocation.output));
        // A repository can read key fields on the destination before it hydrates it.
        var targets = bindings.get(0).repositoryTarget();
        return targets.stream().allMatch(t -> t instanceof AppParser.ArgumentRepositoryTargetContext arg
                && !arg.IntegerLiteral().getText().equals(Integer.toString(invocation.output)));
    }

    private static boolean cleared(CalculationBodyContext body, ParameterDeclarationContext formal) {
        if (body.calculationStatement().isEmpty()) return false;
        var a = body.calculationStatement(0).calculationAssignment();
        return a != null && a.qualifiedName().getText().equals(name(formal)) && a.expression() != null
                && a.getChild(1).getText().equals("=")
                && a.expression().getText().equals("new" + type(formal) + "{}");
    }

    private static boolean firstOnly(CalculationBodyContext body, String formal) {
        if (!nodes(body, AtomiccallContext.class).isEmpty()) return false;
        boolean found = false;
        for (var path : nodes(body, QualifiedNameContext.class)) {
            String ref = path.getText();
            if (!ref.equals(formal) && !ref.startsWith(formal + ".")) continue;
            if (!ref.equals(formal) || !(path.getParent() instanceof PrimaryExprQualifiedContext)) return false;
            var expression = path.getParent().getParent().getParent();
            if (expression.getParent() instanceof ArrayExprContext access && access.expression(0) == expression
                    && access.expression(1).getText().equals("0")) {
                found = true;
                continue;
            }
            if (!(expression.getParent() instanceof CalculationStatementContext loop)
                    || !loop.getStart().getText().equals("for") || loop.calculationLocal() == null
                    || !loop.getChild(3).getText().equals(":")) return false;
            var statements = loop.calculationStatement(0).calculationBody();
            if (statements == null || statements.calculationStatement().size() < 2) return false;
            var loopVars = loop.calculationLocal().Identifier();
            String element = loopVars.get(loopVars.size() - 1).getText();
            if (nodes(statements, PrimaryExprQualifiedContext.class).stream()
                    .noneMatch(p -> p.qualifiedName().Identifier(0).getText().equals(element))) return false;
            var steps = statements.calculationStatement();
            if (!steps.get(steps.size() - 1).getText().equals("break;")
                    || nodes(statements, CalculationStatementContext.class).stream()
                    .anyMatch(s -> s.getStart().getText().equals("continue"))) return false;
            found = true;
        }
        return found;
    }

    private void indexSuggestion(RedundancyLint.Source source, Invocation invocation,
                                 List<ParameterDeclarationContext> participants) {
        var call = invocation.call;
        if (call.uniqueModifier() == null || call.readBinding() == null || invocation.service != null || invocation.output < 0) return;
        var target = invocation.actuals.get(invocation.output);
        String type = type(target);
        var mapped = tables.getOrDefault(type, List.of());
        if (mapped.size() != 1 || mapped.get(0).identifyList().identifyItem().size() != 1) return;
        var predicates = new LinkedHashSet<String>();
        for (var key : call.readBinding().readKey()) {
            var path = key.qualifiedName();
            if (path != null && path.Identifier().size() != 2) return;
            String owner = path == null ? key.Identifier().getText() : path.Identifier(0).getText();
            String field = path == null ? null : path.Identifier(1).getText();
            if (field != null && (owner.equals(type) || owner.equals(name(target)))) {
                if (!fields.get(type).containsKey(field)) return;
                predicates.add(type + "." + field);
            } else {
                var performers = participants.stream().filter(p -> owner.equals(name(p))).toList();
                if (performers.isEmpty()) performers = participants.stream().filter(p -> owner.equals(type(p))).toList();
                if (performers.size() != 1) return;
                var mappedFields = mappedPredicates(type, type(performers.get(0)), field);
                if (mappedFields.isEmpty()) return;
                predicates.addAll(mappedFields);
            }
        }
        var table = mapped.get(0);
        Set<String> primaryFields = new HashSet<>();
        for (var column : table.columnDefine())
            if (column.columnModifier().stream().anyMatch(AppParser.ColumnPrimaryKeyContext.class::isInstance))
                primaryFields.add(type + "." + column.columnName().getText());
        if (primaryFields.stream().anyMatch(predicates::contains)) return;
        for (var key : table.keyDefine()) {
            var primary = key.primaryKeyDefine();
            var index = key.indexDefine();
            if (primary == null && index == null) continue;
            var columns = (primary == null ? index.qualifiedItemList() : primary.qualifiedItemList()).qualifiedItem();
            if (columns.isEmpty()) return;
            if (primary != null) columns.forEach(c -> primaryFields.add(c.getText()));
            // An existing index with a constrained leading column can already narrow the lookup.
            if (predicates.contains(columns.get(0).getText())) return;
        }
        if (!primaryFields.isEmpty() && !primaryFields.containsAll(predicates))
            reporter.warn(source, call.Identifier().getSymbol(), "SPP_FIND_MISSING_INDEX",
                    "Consider declaring index(" + String.join(", ", predicates) + "); in table "
                            + table.Identifier().getText() + " for this lookup.");
    }

    private Set<String> mappedPredicates(String target, String performer, String sourceField) {
        var mappings = takes.stream().filter(t -> t.Identifier().size() == 2
                && t.Identifier(1).getText().equals(target) && t.Identifier(0).getText().equals(performer)).toList();
        if (mappings.size() != 1 || mappings.get(0).mapBody() == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (var declaration : mappings.get(0).mapBody().mapDeclaration()) {
            var field = declaration.mapfield();
            if (field != null) {
                if (sourceField == null || field.Identifier(1).getText().equals(sourceField))
                    result.add(target + "." + field.Identifier(0).getText());
            } else if (declaration.mapsame() != null) {
                for (var entry : fields.get(target).entrySet())
                    if ((sourceField == null || sourceField.equals(entry.getKey())) && entry.getValue().equals(
                            fields.getOrDefault(performer, Map.of()).get(entry.getKey()))) result.add(target + "." + entry.getKey());
            } else return Set.of();
        }
        return result;
    }

    private void transferBounds(RedundancyLint.Source source, Invocation invocation) {
        var service = invocation.service;
        if (service.calculationBody() == null || !service.servicetype().getText().equals("update")) return;
        var call = invocation.call;
        var protectedPaths = new HashSet<String>();
        if (call.quantumBounds() != null)
            for (var bound : call.quantumBounds().quantumBound())
                if (bound.expression().stream().anyMatch(e -> !unwrap(e).getText().equals("null")))
                    protectedPaths.add(bound.qualifiedName().getText());
        var formals = parameters(service.serviceDefinition());
        var floors = declaredFloors(service.calculationBody());
        for (var transfer : nodes(service.calculationBody(), QuantumTransferContext.class)) {
            var debit = transfer.qualifiedName(0);
            var amount = unwrap(transfer.expression());
            if (debit.Identifier().size() != 2 || floors.contains(debit.getText())
                    || amount.getText().equals(debit.getText()) || zeroOrNegative(amount)) continue;
            String root = debit.Identifier(0).getText();
            // A collection transfer names its element in the loop and the collection in bounds.
            for (var parent = transfer.getParent(); parent != null; parent = parent.getParent()) {
                if (!(parent instanceof CalculationStatementContext loop) || loop.calculationLocal() == null
                        || loop.getChildCount() < 5 || !loop.getChild(3).getText().equals(":")) continue;
                var ids = loop.calculationLocal().Identifier();
                if (!ids.isEmpty() && ids.get(ids.size() - 1).getText().equals(root)) {
                    root = loop.expression().getText();
                    break;
                }
            }
            String field = debit.Identifier(1).getText();
            for (int i = 0; i < formals.size(); i++) {
                var formal = formals.get(i);
                if (!name(formal).equals(root) || formal.roletype() == null) continue;
                String type = formal.roletype().Identifier().getText();
                String path = name(invocation.actuals.get(i)) + "." + field;
                if (!fields.getOrDefault(type, Map.of()).getOrDefault(field, "").startsWith("quantum")
                        || physicalFloors.contains(type + "." + field) || !protectedPaths.add(path)) continue;
                reporter.warn(source, call.Identifier().getSymbol(), "SPP_UNBOUNDED_TRANSFER",
                        "This transfer can debit " + path + " below zero. If overdrafts are forbidden, use bounds { "
                                + path + ": [0, null]; } on the call inside a transaction.");
            }
        }
    }

    // Top-level quantum checks are revalidated on the resulting positions.
    private static Set<String> declaredFloors(CalculationBodyContext body) {
        Set<String> protectedPaths = new HashSet<>();
        for (var statement : body.calculationStatement()) {
            if (!statement.getStart().getText().equals("check")
                    || !(unwrap(statement.expression()) instanceof LogicGreatLessExprContext comparison)) continue;
            var operator = comparison.getChild(1).getText();
            boolean lower = operator.startsWith(">");
            var path = unwrap(comparison.expression(lower ? 0 : 1));
            var limit = unwrap(comparison.expression(lower ? 1 : 0));
            if (nonnegative(limit.getText())) protectedPaths.add(path.getText());
        }
        return protectedPaths;
    }

    private static ExpressionContext unwrap(ExpressionContext expression) {
        while (expression instanceof ParenthesesExprContext parens) expression = parens.expression();
        return expression;
    }

    private static boolean zeroOrNegative(ExpressionContext amount) {
        try {
            return new BigDecimal(NumericJavaRenderer.normalizeLiteral(amount.getText())).signum() <= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean nonnegative(String value) {
        try { return new BigDecimal(value).signum() >= 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static String unquote(String value) { return value.substring(1, value.length() - 1); }

    private static <T> List<T> nodes(ParserRuleContext tree, Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(tree, type, result);
        return result;
    }

    private static <T> void collect(ParseTree tree, Class<T> type, List<T> result) {
        if (type.isInstance(tree)) result.add(type.cast(tree));
        for (int i = 0; i < tree.getChildCount(); i++) collect(tree.getChild(i), type, result);
    }
}
