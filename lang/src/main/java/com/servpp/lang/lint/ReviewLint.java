package com.servpp.lang.lint;

import com.servpp.lang.antlr.SppParser.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Review warnings over the SPP, APP and PHY sources of one domain. */
public final class ReviewLint {
    private ReviewLint() { }

    public static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        ReviewUsage.check(sources, reporter);
        var values = new ReviewValues(sources, reporter);
        var limits = new Limits();
        for (var source : sources) {
            if (!(source.tree() instanceof ProgramContext program)) continue;
            boolean acceptance = acceptancePath(source.path());
            checkName(source, program.servicedomain().domainname().Identifier(), acceptance, reporter);
            for (var unit : program.compilationUnits().compilationUnit()) {
                walk(unit, node -> checkNames(source, node, acceptance, reporter));
                if (unit.scenario() != null) {
                    var scenario = unit.scenario();
                    var parameters = scenario.serviceDefinition().parameterDeclarations();
                    int count = parameters == null ? 0 : parameters.parameterDeclaration().size();
                    if (count > limits.participants)
                        reporter.warn(source, scenario.serviceDefinition().Identifier().getSymbol(), "SPP_TOO_MANY_PARTICIPANTS",
                                "Scenario declares " + count + " participants (limit " + limits.participants
                                        + "). Group equivalent participants in an array or split the scenario.");
                    checkBody(source, scenario.serviceDefinition(), scenario.scenarioDeclaration(), false, limits, values, reporter);
                } else if (unit.atomicservice() != null && unit.atomicservice().calculationBody() != null) {
                    var atomic = unit.atomicservice();
                    checkBody(source, atomic.serviceDefinition(), atomic.calculationBody(),
                            atomic.servicetype().getText().equals("calculate"), limits, values, reporter);
                }
            }
        }
    }

    static void walk(ParserRuleContext tree, Consumer<ParserRuleContext> visit) {
        visit.accept(tree);
        for (int i = 0; i < tree.getChildCount(); i++)
            if (tree.getChild(i) instanceof ParserRuleContext child) walk(child, visit);
    }

    private static final Pattern SHORT = Pattern.compile("[a-zA-Z][0-9]*");
    private static final Pattern NUMBERED = Pattern.compile("(.+?)([0-9]+)");
    private static final Pattern LEGACY = Pattern.compile("(?i).*(?:head|tail|para(?:graph)?|field|fld|detail|dtl)_?[0-9]+");
    private static final Pattern WORDS = Pattern.compile("[_$0-9]+|(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Set<String> TEST_WORDS = Set.of("fixture", "fixtures", "test", "tests", "probe", "probes");
    private static final Set<String> ACCEPTANCE_DIRS = Set.of("acceptance", "test", "tests", "fixture", "fixtures");

    private static boolean acceptancePath(String path) {
        String[] parts = path.replace('\\', '/').toLowerCase(Locale.ROOT).split("/");
        for (int i = 0; i < parts.length - 1; i++)
            if (ACCEPTANCE_DIRS.contains(parts[i])) return true;
        return false;
    }

    private static void checkNames(RedundancyLint.Source source, ParserRuleContext node, boolean acceptance,
                                   RedundancyLint.Reporter reporter) {
        TerminalNode name;
        if (node instanceof ParameterDeclarationContext parameter) {
            name = parameter.Identifier();
            if (name == null && parameter.roletype() != null) name = parameter.roletype().Identifier();
            if (name != null && opaqueParticipant(name.getText(), parameter))
                reporter.warn(source, name.getSymbol(), "SPP_OPAQUE_PARTICIPANT",
                        "Participant '" + name.getText() + "' gives little indication of its role. Use its business purpose as the name.");
            // An implicit participant has no separate name declaration.
            if (parameter.Identifier() == null) return;
        } else if (node instanceof FieldDeclarationContext field) {
            name = field.variableDeclaratorId() == null ? field.Identifier() : field.variableDeclaratorId().Identifier();
        } else if (node instanceof EntityContext entity) name = entity.Identifier();
        else if (node instanceof RoleContext role) name = role.Identifier();
        else if (node instanceof EnumerationContext enumeration) name = enumeration.Identifier();
        else if (node instanceof EnumBodyIdentifierContext member) name = member.Identifier();
        else if (node instanceof ConstantDeclarationContext constant) name = constant.Identifier();
        else if (node instanceof ServiceDefinitionContext service) name = service.Identifier();
        else if (node instanceof CalculationLocalContext local) {
            for (var id : local.Identifier())
                if (id != local.getChild(0)) checkName(source, id, acceptance, reporter);
            return;
        } else return;
        if (name != null) checkName(source, name, acceptance, reporter);
    }

    private static boolean opaqueParticipant(String name, ParameterDeclarationContext parameter) {
        if (SHORT.matcher(name).matches()) return true;
        Matcher numbered = NUMBERED.matcher(name);
        if (!numbered.matches()) return false;
        // A single name such as sha256 may identify a standard. Look for a numbered series.
        String stem = numbered.group(1);
        var parameters = (ParameterDeclarationsContext) parameter.getParent();
        for (var other : parameters.parameterDeclaration()) {
            if (other == parameter || other.Identifier() == null) continue;
            var peer = NUMBERED.matcher(other.Identifier().getText());
            if (peer.matches() && stem.equals(peer.group(1))) return true;
        }
        return false;
    }

    private static void checkName(RedundancyLint.Source source, TerminalNode token, boolean acceptance,
                                  RedundancyLint.Reporter reporter) {
        String name = token.getText();
        if (LEGACY.matcher(name).matches())
            reporter.warn(source, token.getSymbol(), "SPP_LEGACY_NAME",
                    "'" + name + "' ends in a numbered layout or paragraph label. Name the business fact or action it represents.");
        if (!acceptance && WORDS.splitAsStream(name).map(word -> word.toLowerCase(Locale.ROOT)).anyMatch(TEST_WORDS::contains))
            reporter.warn(source, token.getSymbol(), "SPP_TEST_NAME",
                    "'" + name + "' includes a test term in a production declaration. Use its business name or move the fixture into acceptance sources.");
    }

    private static class Limits {
        final int participants = limit("maxParticipants", 8);
        final int statements = limit("maxCalculateStatements", 40);
        final int depth = limit("maxNesting", 4);

        private static int limit(String key, int fallback) {
            try {
                int value = Integer.parseInt(System.getProperty("spp.lint." + key, String.valueOf(fallback)));
                return value > 0 ? value : fallback;
            } catch (NumberFormatException invalid) {
                return fallback;
            }
        }
    }

    private static void checkBody(RedundancyLint.Source source, ServiceDefinitionContext definition,
                                  ParserRuleContext body, boolean calculate, Limits limits, ReviewValues values,
                                  RedundancyLint.Reporter reporter) {
        var metrics = new Metrics();
        metrics.measure(body, 0);
        if (calculate && metrics.statements > limits.statements)
            reporter.warn(source, definition.Identifier().getSymbol(), "SPP_LONG_CALCULATE",
                    "Calculate contains " + metrics.statements + " statements (limit " + limits.statements
                            + "). Split a cohesive step into a named calculation.");
        if (metrics.depth > limits.depth)
            reporter.warn(source, metrics.deepest, "SPP_DEEP_NESTING",
                    "Control flow reaches depth " + metrics.depth + " (limit " + limits.depth
                            + "). Extract a nested step or simplify its conditions.");
        values.checkBody(source, definition, body);
    }

    private static final Set<String> CONTROLS = Set.of("if", "for", "while", "switch");

    private static class Metrics {
        int statements;
        int depth;
        Token deepest;

        void measure(ParserRuleContext node, int level) {
            boolean nested = false;
            if (node instanceof CalculationStatementContext statement) {
                String first = node.getStart().getText();
                if (statement.calculationBody() == null && !first.equals(";")) statements++;
                nested = CONTROLS.contains(first) && !(first.equals("if")
                        && statement.getParent() instanceof CalculationStatementContext parent
                        && parent.calculationStatement().size() == 2 && parent.calculationStatement(1) == statement);
            } else if (node instanceof ScenarioIfContext) nested = !(node.getParent() instanceof ScenarioIfContext);
            else nested = node instanceof SerialgroupContext || node instanceof ParallelgroupContext
                        || node instanceof TransactionStatementsContext
                        || node instanceof ScenarioBranchContext && node.getParent() instanceof AtomiccallContext;
            if (nested) level++;
            if (level > depth) {
                depth = level;
                deepest = node.getStart();
            }
            for (int i = 0; i < node.getChildCount(); i++)
                if (node.getChild(i) instanceof ParserRuleContext child) measure(child, level);
        }
    }
}
