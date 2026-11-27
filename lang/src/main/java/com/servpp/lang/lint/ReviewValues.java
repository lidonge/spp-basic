package com.servpp.lang.lint;

import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.antlr.SppParser.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Domain enum vocabulary and literal counts for executable SPP. */
final class ReviewValues {
    private record Member(String type, String name) {
        String qualified() { return type + "." + name; }
    }

    private final Map<String, Map<String, String>> fields;
    private final Map<String, Map<String, Member>> members = new HashMap<>();
    private final Map<String, Integer> occurrences = new HashMap<>();
    private final RedundancyLint.Reporter reporter;

    ReviewValues(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        fields = LocalRedundancy.typeMaps(sources);
        this.reporter = reporter;
        Map<String, List<Member>> owners = new HashMap<>();
        for (var source : sources) {
            if (!(source.tree() instanceof ProgramContext program)) continue;
            for (var unit : program.compilationUnits().compilationUnit()) {
                var enumeration = unit.enumeration();
                if (enumeration == null) continue;
                String type = enumeration.Identifier().getText();
                var byCode = members.computeIfAbsent(type, unused -> new HashMap<>());
                for (var item : enumeration.enumBody().enumBodyIdentifier()) {
                    var member = new Member(type, item.Identifier().getText());
                    if (item.StringLiteral().isEmpty()) {
                        registerCode(source, member, "\"" + member.name + "\"", item.Identifier().getSymbol(), byCode, owners);
                    } else for (var alias : item.StringLiteral())
                        registerCode(source, member, alias.getText(), alias.getSymbol(), byCode, owners);
                }
            }
        }
    }

    private void registerCode(RedundancyLint.Source source, Member member, String code, Token token,
                              Map<String, Member> byCode, Map<String, List<Member>> owners) {
        byCode.put(code, member);
        var earlier = owners.computeIfAbsent(code, unused -> new ArrayList<>());
        for (var previous : earlier) {
            if (!previous.type.equals(member.type) && !previous.name.equals(member.name)) {
                reporter.warn(source, token, "SPP_DUPLICATE_ENUM_CODE",
                        member.qualified() + " and " + previous.qualified() + " both represent " + code
                                + ". Check whether they describe the same business concept before sharing an enum.");
                break;
            }
        }
        earlier.add(member);
    }

    void checkBody(RedundancyLint.Source source, ServiceDefinitionContext definition, ParserRuleContext body) {
        Map<String, String> variables = new HashMap<>();
        if (definition.parameterDeclarations() != null) {
            for (var parameter : definition.parameterDeclarations().parameterDeclaration()) {
                if (parameter.roletype() == null) continue;
                String type = parameter.roletype().getText();
                String name = com.servpp.lang.ParticipantNames.bindingName(parameter);
                variables.put(name, type);
            }
        }
        checkNode(source, body, variables);
    }

    private void checkNode(RedundancyLint.Source source, ParserRuleContext node, Map<String, String> variables) {
        if (node instanceof RejectionCodeContext) return; // require expects a scalar enum, not a primitive constant.
        if (node instanceof CalculationBodyContext || node instanceof CalculationCaseContext
                || node instanceof CalculationStatementContext && node.getStart().getText().equals("for"))
            variables = new HashMap<>(variables);
        if (node instanceof CalculationLocalContext local) {
            String type = local.getChild(0).getText();
            if (local.children.stream().anyMatch(c -> c.getText().equals("["))) type += "[]";
            for (var id : local.Identifier())
                if (id != local.getChild(0)) variables.put(id.getText(), type);
        } else if (node instanceof LogicEqualsExprContext equals) {
            compare(source, equals.expression(0), equals.expression(1), variables);
            compare(source, equals.expression(1), equals.expression(0), variables);
        } else if (node instanceof CalculationCaseContext arm && arm.expression() != null
                && arm.getParent() instanceof CalculationStatementContext choice) {
            compare(source, choice.expression(), arm.expression(), variables);
        } else if (node instanceof PrimExprContext primary && primary.primaryExpr().primaryExprLiteral() != null) {
            Token token = primary.getStart();
            // A unary sign is outside the literal rule in calculations.
            ParserRuleContext operand = primary;
            while (operand.getParent() instanceof ParenthesesExprContext) operand = operand.getParent();
            boolean negative = operand.getParent() instanceof LeftIncDecAndSignExprContext sign && sign.getStart().getText().equals("-");
            count(source, token, (negative ? "-" : "") + token.getText());
        } else if (node instanceof ReadConstantContext value && value.Identifier() == null) {
            Token literal = value.getStop(); // readConstant includes an optional leading minus.
            count(source, literal, value.getText());
        }
        for (int i = 0; i < node.getChildCount(); i++)
            if (node.getChild(i) instanceof ParserRuleContext child) checkNode(source, child, variables);
    }

    private void compare(RedundancyLint.Source source, ExpressionContext field, ExpressionContext raw,
                         Map<String, String> variables) {
        var left = primary(field);
        var right = primary(raw);
        if (left == null || left.primaryExprQualified() == null || right == null || right.primaryExprLiteral() == null) return;
        var literal = right.primaryExprLiteral().literal().StringLiteral();
        if (literal == null) return;
        var path = left.primaryExprQualified().qualifiedName();
        var names = path.Identifier();
        if (names.size() < 2 || path.getChildCount() != 2 * names.size() - 1) return;
        String type = variables.get(names.get(0).getText());
        for (int i = 1; i < names.size() && type != null; i++)
            type = fields.getOrDefault(type, Map.of()).get(names.get(i).getText());
        if (type == null) return;
        var member = members.getOrDefault(type, Map.of()).get(literal.getText());
        if (member != null) {
            String replacement = raw.getParent() instanceof CalculationCaseContext ? member.name : member.qualified();
            reporter.warn(source, literal.getSymbol(), "SPP_RAW_ENUM_CODE", path.getText() + " has enum type " + type
                    + " and " + literal.getText() + " names " + member.qualified() + ". Compare using " + replacement + ".");
        }
    }

    private static PrimaryExprContext primary(ExpressionContext expression) {
        while (expression instanceof ParenthesesExprContext parens) expression = parens.expression();
        return expression instanceof PrimExprContext primary ? primary.primaryExpr() : null;
    }

    private static final Pattern TRIVIAL_NUMBER = Pattern.compile("-?(?:0+|0*1)(?:\\.0*)?[lLfFdD]?");

    private void count(RedundancyLint.Source source, Token token, String spelling) {
        switch (token.getType()) {
            case SppParser.StringLiteral -> {
                if (spelling.substring(1, spelling.length() - 1).isBlank()) return;
            }
            case SppParser.IntegerLiteral, SppParser.FloatingPointLiteral -> {
                if (TRIVIAL_NUMBER.matcher(spelling).matches()) return;
            }
            case SppParser.CharacterLiteral -> { }
            default -> { return; }
        }
        if (occurrences.merge(spelling, 1, Integer::sum) == 3)
            reporter.warn(source, token, "SPP_REPEATED_LITERAL", spelling + " appears at least three times in business logic. "
                    + "Use a domain constant for occurrences with the same meaning, or a member for enum values.");
    }
}
