package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.general.NameUtil;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.servpp.lang.antlr.SppParser.*;

/** Source-level data access checks for AI-facing diagnostics revision 2. */
public final class DataAccessLint {
    private DataAccessLint() { }

    enum Effect { QUERY, CHECK, CALCULATE, WRITE, UNKNOWN }

    private final Map<String, Effect> services = new HashMap<>();
    private final Set<String> participants = new HashSet<>();
    private final Set<String> externalOrLocking = new HashSet<>();

    /** The dispatcher supplies the SPP and APP sources for one service domain. */
    public static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        var lint = indexed(sources);
        for (var source : sources) {
            if (!(source.tree() instanceof SppParser.ProgramContext spp)) continue;
            for (var unit : spp.compilationUnits().compilationUnit()) {
                if (unit.scenario() != null) lint.checkScenario(source, unit.scenario(), reporter);
            }
        }
    }

    static DataAccessLint indexed(List<RedundancyLint.Source> sources) {
        var lint = new DataAccessLint();
        for (var source : sources) {
            if (source.tree() instanceof SppParser.ProgramContext spp) {
                for (var unit : spp.compilationUnits().compilationUnit()) lint.index(unit);
            } else if (source.tree() instanceof AppParser.ProgramContext app) {
                lint.index(app);
            }
        }
        return lint;
    }

    private void index(CompilationUnitContext unit) {
        if (unit.entity() != null) participants.add(unit.entity().Identifier().getText());
        if (unit.role() != null) participants.add(unit.role().Identifier().getText());
        var atomic = unit.atomicservice();
        if (atomic == null && unit.scenario() == null) return;
        var definition = atomic == null ? unit.scenario().serviceDefinition() : atomic.serviceDefinition();
        var effect = atomic == null || atomic.quantumService() != null ? Effect.UNKNOWN : switch (atomic.servicetype().getText()) {
            case "query" -> Effect.QUERY;
            case "check" -> Effect.CHECK;
            case "calculate" -> Effect.CALCULATE;
            default -> Effect.WRITE;
        };
        services.merge(method(definition.Identifier().getText()), effect, (a, b) -> Effect.UNKNOWN);
    }

    private void index(AppParser.ProgramContext app) {
        for (var block : app.ruleBlock()) {
            var rule = block.annotatableRule();
            if (rule == null) continue;
            if (rule.localServiceBinding() != null)
                externalOrLocking.add(method(rule.localServiceBinding().Identifier().getText()));
            if (rule.scope() == null) continue;
            var scope = rule.scope().scopeDefine();
            for (var remote : scope.remoteBody())
                for (var item : remote.scopeItem()) externalOrLocking.add(method(item.scopeItemIdentifier().getText()));
            for (var local : scope.localBody()) {
                for (var item : local.scopeItem()) {
                    for (var modifier : item.scopeModifier()) {
                        if (modifier instanceof AppParser.TransModifierContext tx && tx.getText().equals("quantum")
                                || modifier instanceof AppParser.RepositoryModifierContext repo
                                && repo.children.stream().anyMatch(child -> child.getText().equals("for"))) {
                            externalOrLocking.add(method(item.scopeItemIdentifier().getText()));
                        }
                    }
                }
            }
        }
    }

    private static String method(String name) {
        return NameUtil.firstToLowerCase(name, true);
    }

    Effect effect(AtomiccallContext call) {
        if (call.Identifier() == null) return Effect.CHECK; // identify(participant)
        String name = method(call.Identifier().getText());
        if (externalOrLocking.contains(name)) return Effect.UNKNOWN;
        var declared = services.get(name);
        if (declared != null) return declared;
        for (String operation : List.of("find", "insert", "update", "delete")) {
            if (name.startsWith(operation) && participants.contains(name.substring(operation.length())))
                return operation.equals("find") ? Effect.QUERY : Effect.WRITE;
        }
        return Effect.UNKNOWN;
    }

    private void checkScenario(RedundancyLint.Source source, ScenarioContext scenario, RedundancyLint.Reporter reporter) {
        var body = scenario.scenarioDeclaration();
        var calls = Trees.findAllRuleNodes(body, SppParser.RULE_atomiccall);
        boolean readonly = !calls.isEmpty() && scenario.readonlyModifier() == null
                && Trees.findAllRuleNodes(body, SppParser.RULE_transactionStatements).isEmpty();
        Set<ParseTree> checked = new HashSet<>();
        for (var node : calls) {
            var call = (AtomiccallContext) node;
            var effect = effect(call);
            if (effect == Effect.UNKNOWN || effect == Effect.WRITE) readonly = false;
            var group = call.getParent();
            if (checked.add(group) && isSerial(group, scenario)) checkSequence(group, source, reporter);
        }
        if (readonly) {
            reporter.warn(source, scenario.getStart(), "SPP_MISSING_READONLY",
                    "Declare readonly Scenario " + scenario.serviceDefinition().Identifier().getText()
                            + " to enforce its read-only operations.");
        }
    }

    private static boolean isSerial(ParseTree group, ScenarioContext scenario) {
        return group instanceof SerialgroupContext || group instanceof ScenarioBranchContext
                || group instanceof TransactionStatementsContext || group instanceof TransactionFailureContext
                || group instanceof ExecutegroupContext && scenario.autosort() != null
                && scenario.autosort().getText().equals("serial");
    }

    private static boolean inTransaction(ParseTree node) {
        for (; node != null; node = node.getParent()) {
            if (node instanceof TransactionFailureContext) return false;
            if (node instanceof TransactionStatementsContext) return true;
        }
        return false;
    }

    private void checkSequence(ParseTree group, RedundancyLint.Source source, RedundancyLint.Reporter reporter) {
        for (int i = 1; i < group.getChildCount(); i++) {
            if (!(group.getChild(i - 1) instanceof AtomiccallContext previous)
                    || !(group.getChild(i) instanceof AtomiccallContext call)
                    || !plainCall(previous) || !plainCall(call)) continue;
            var current = effect(call);
            if (current == Effect.QUERY && previous.Identifier().getText().equals(call.Identifier().getText())
                    && previous.getText().equals(call.getText())) {
                reporter.warn(source, call.Identifier().getSymbol(), "SPP_REPEATED_QUERY",
                        "Review the repeated read " + call.Identifier().getText()
                                + ". Reuse the first result if the second call need not refresh it.");
            } else if (current == Effect.WRITE && effect(previous) == Effect.WRITE && !inTransaction(group)) {
                reporter.warn(source, call.Identifier().getSymbol(), "SPP_WRITES_OUTSIDE_TRANSACTION",
                        "If " + previous.Identifier().getText() + " and " + call.Identifier().getText()
                                + " must commit together, put both calls in an explicit transaction block.");
            }
        }
    }

    private static boolean plainCall(AtomiccallContext call) {
        return call.Identifier() != null && call.scenarioBranch() == null && call.transactionBreak() == null;
    }
}
