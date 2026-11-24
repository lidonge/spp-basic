package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import java.util.List;

/** A BLANK write must generate exactly the previous stored value under the selected physical policy. */
final class BlankEdits {
    static boolean equivalent(List<RedundancyLint.Source> domain, RedundancyLint.Finding f) {
        var source = domain.stream().filter(s -> s.path().equals(f.path())).findFirst().orElseThrow();
        for (var entry : domain) if (entry.tree() instanceof AppParser.ProgramContext app
                && !Trees.findAllRuleNodes(app, AppParser.RULE_applicationDeployment).isEmpty()) return false;
        domain = domain.stream().filter(s -> source.domain().equals(s.domain())).toList();
        var node = contextAt(source.tree(), f.token().getTokenIndex());
        SppParser.CalculationAssignmentContext assignment = null;
        SppParser.ServiceDefinitionContext definition = null;
        for (var p = node; p != null; p = p.getParent()) {
            if (p instanceof SppParser.LogicEqualsExprContext) return false;
            if (p instanceof SppParser.CalculationAssignmentContext a) assignment = a;
            if (p instanceof SppParser.AtomicserviceContext service) definition = service.serviceDefinition();
        }
        String type = null, field = null;
        if (assignment != null && assignment.qualifiedName() != null && definition != null && definition.parameterDeclarations() != null) {
            var parts = assignment.qualifiedName().getText().split("\\.");
            if (parts.length == 2) {
                field = parts[1];
                for (var param : definition.parameterDeclarations().parameterDeclaration()) {
                    if (param.roletype() == null) continue;
                    String name = param.Identifier() == null ? param.roletype().getText() : param.Identifier().getText();
                    if (name.equals(parts[0])) type = param.roletype().getText();
                }
            }
        }
        String write = "\"\"";
        String override = null;
        boolean hasDefault = false;
        for (var entry : domain) {
            if (!(entry.tree() instanceof AppParser.ProgramContext app)) continue;
            if (!Trees.findAllRuleNodes(app, AppParser.RULE_rolePlay).isEmpty()) return false;
            for (ParseTree policy : Trees.findAllRuleNodes(app, AppParser.RULE_blankPolicy)) {
                var blank = (AppParser.BlankPolicyContext)policy;
                // Deployment, table, profile and field overrides may disagree. Withhold ambiguous edits.
                for (var parent = blank.getParent(); parent != null; parent = parent.getParent())
                    if (parent instanceof AppParser.PhyEnvironmentContext) return false;
                if (blank.qualifiedName() == null) {
                    if (hasDefault) return false;
                    hasDefault = true;
                    write = blank.written.getText();
                }
                else if (type != null && blank.qualifiedName().getText().equals(type + "." + field)) {
                    if (override != null) return false;
                    override = blank.written.getText();
                }
                else return false;
            }
        }
        String effective = override == null ? write : override;
        if (effective.equals("\"\"")) return f.token().getText().equals("\"\"");
        if (!effective.equals("spaces") || type == null || field == null) return false;
        String fieldType = LocalRedundancy.typeMaps(domain).getOrDefault(type, java.util.Map.of()).get(field);
        return ("String(" + (f.token().getText().length() - 2) + ")").equals(fieldType);
    }

    private static ParserRuleContext contextAt(ParserRuleContext node, int index) {
        for (int i = 0; i < node.getChildCount(); i++)
            if (node.getChild(i) instanceof ParserRuleContext child
                    && child.getStart().getTokenIndex() <= index && child.getStop().getTokenIndex() >= index)
                return contextAt(child, index);
        return node;
    }
}
