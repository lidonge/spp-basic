package com.servpp.lang.general.expr;

import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.general.app.BracketOperation;
import com.servpp.lang.models.app.expr.*;
import java.util.List;
import java.util.ArrayList;

/** Builds the same mathematical AST used by APP, directly from the SPP parse tree. */
public final class BusinessCalculationCompiler {
    private BusinessCalculationCompiler() { }

    public static List<IJavaStatement> body(SppParser.CalculationBodyContext ctx, com.servpp.lang.models.SppDomain domain) {
        List<IJavaStatement> statements = new ArrayList<>();
        for (var child : ctx.calculationStatement()) { statements.add(statement(child, domain)); }
        return statements;
    }
    private static IJavaStatement statement(SppParser.CalculationStatementContext ctx, com.servpp.lang.models.SppDomain domain) {
        String keyword = ctx.getChild(0).getText();
        if (ctx.atomiccall() != null) {
            var call = ctx.atomiccall();
            if (call.Identifier() == null || call.scenarioBranch() != null || call.quantumBounds() != null || call.getText().contains(",breakTransction"))
                throw new IllegalArgumentException("Calculate calls cannot identify or guard other services");
            return new CalculationServiceCall(call.Identifier().getText(), domain, call.parameters() == null,
                call.parameters() == null ? List.of() : call.parameters().parameter().stream().map(p -> p.getText()).toList());
        }
        if (ctx.calculationBody() != null) {
            CalculationControl block = new CalculationControl(CalculationControl.Kind.BLOCK);
            block.body.addAll(body(ctx.calculationBody(), domain)); return block;
        }
        if (keyword.equals("if") || keyword.equals("for") || keyword.equals("while")) {
            for (var child : ctx.calculationStatement()) {
                if (child.calculationLocal() != null) { throw new IllegalArgumentException("Local declaration requires a block in a control body."); }
            }
        }
        if (keyword.equals("if")) {
            return new CalculationRule(expression(ctx.expression(), domain), List.of(statement(ctx.calculationStatement(0), domain)),
                    ctx.calculationStatement().size() == 2 ? List.of(statement(ctx.calculationStatement(1), domain)) : List.of());
        }
        if (keyword.equals("require") || keyword.equals("check")) {
            return new RequireStatement(expression(ctx.expression(), domain), ctx.StringLiteral() == null
                    ? "\"QUANTUM_CONSTRAINT_REJECTED\"" : ctx.StringLiteral().getText()).quantumConstraint(keyword.equals("check"));
        }
        if (keyword.equals("while") || keyword.equals("for") || keyword.equals("switch")) {
            boolean each = keyword.equals("for") && ctx.calculationLocal() != null
                    && ctx.children.stream().anyMatch(c -> c.getText().equals(":"));
            CalculationControl control = new CalculationControl(switch (keyword) {
                case "while" -> CalculationControl.Kind.WHILE;
                case "switch" -> CalculationControl.Kind.SWITCH;
                default -> each ? CalculationControl.Kind.EACH : CalculationControl.Kind.FOR;
            });
            control.condition = ctx.expression() == null ? null : expression(ctx.expression(), domain);
            if (each) {
                var declaration = local(ctx.calculationLocal(), domain);
                if (declaration.condition != null || !declaration.body.isEmpty()) { throw new IllegalArgumentException("Enhanced for declares exactly one uninitialized variable."); }
                control.local = declaration.local;
            }
            else if (ctx.calculationLocal() != null) { control.init.add(local(ctx.calculationLocal(), domain)); }
            for (var updates : ctx.calculationUpdates()) {
                boolean beforeSemicolon = updates.getStart().getTokenIndex() < ctx.children.stream()
                        .filter(c -> c.getText().equals(";")).map(c -> ((org.antlr.v4.runtime.tree.TerminalNode)c).getSymbol().getTokenIndex())
                        .findFirst().orElse(-1);
                for (var assignment : updates.calculationAssignment()) {
                    (beforeSemicolon ? control.init : control.update).add(assignment(assignment, domain));
                }
            }
            for (var child : ctx.calculationStatement()) { control.body.add(statement(child, domain)); }
            for (var group : ctx.calculationCase()) {
                CalculationControl entry = new CalculationControl(CalculationControl.Kind.CASE);
                entry.condition = group.expression() == null ? null : expression(group.expression(), domain);
                for (var child : group.calculationStatement()) { entry.body.add(statement(child, domain)); }
                control.body.add(entry);
            }
            return control;
        }
        if (ctx.calculationAssignment() != null) { return assignment(ctx.calculationAssignment(), domain); }
        if (ctx.calculationLocal() != null) { return local(ctx.calculationLocal(), domain); }
        return new CalculationControl(keyword.equals("break") ? CalculationControl.Kind.BREAK
                : keyword.equals("continue") ? CalculationControl.Kind.CONTINUE : CalculationControl.Kind.EMPTY);
    }
    private static CalculationControl local(SppParser.CalculationLocalContext ctx, com.servpp.lang.models.SppDomain domain) {
        String typeName = ctx.getChild(0).getText(), name = ctx.getChild(1).getText();
        var type = domain.getSppClass(typeName);
        if (type == null && !java.util.Set.of("short", "int", "long", "float", "double", "decimal", "boolean", "char", "String").contains(typeName)) {
            throw new IllegalArgumentException("Unknown local variable type " + typeName);
        }
        if (type == null) { type = new com.servpp.lang.models.SppClass(typeName); }
        CalculationControl result = null;
        int dimensions = ctx.getChild(1).getText().equals("[") ? 1 : 0;
        for (int i = dimensions == 1 ? 3 : 1; i < ctx.getChildCount();) {
            name = ctx.getChild(i++).getText();
            CalculationControl declaration = new CalculationControl(CalculationControl.Kind.LOCAL);
            declaration.local = new com.servpp.lang.models.SppLocalVar(type, name);
            declaration.local.setArrayDimension(dimensions);
            domain.applyCalculationNumericBinding(declaration.local);
            if (i < ctx.getChildCount() && ctx.getChild(i).getText().equals("=")) {
                i++; declaration.condition = expression((SppParser.ExpressionContext)ctx.getChild(i++), domain);
            }
            if (result == null) { result = declaration; } else { result.body.add(declaration); }
            if (i < ctx.getChildCount()) { i++; }
        }
        return result;
    }
    private static AssignExpression assignment(SppParser.CalculationAssignmentContext ctx, com.servpp.lang.models.SppDomain domain) {
        AssignOperation operator = new AssignOperation();
        String text = ctx.getChild(1).getText();
        if (ctx.expression() == null) { text = ctx.getText().contains("++") ? "+=" : "-="; }
        if (!text.equals("=")) { operator.setAssoc(text.substring(0, text.length() - 1)); }
        operator.setType(IOperation.Opers_Type.Double);
        AssignExpression result = new AssignExpression(); result.setOperation(operator);
        result.setLeft(reference(ctx.qualifiedName().getText()));
        if (ctx.expression() != null) { result.setRight(expression(ctx.expression(), domain)); }
        else { Literal one = new Literal(); one.setValue("1"); result.setRight(one); }
        return result;
    }
    private static Reference reference(String name) {
        Reference result = new CalculationReference(); result.setValue(name); return result;
    }

    public static IExpression expression(SppParser.ExpressionContext ctx) { return expression(ctx, null); }

    public static IExpression expression(SppParser.ExpressionContext ctx, com.servpp.lang.models.SppDomain domain) {
        if (ctx instanceof SppParser.PrimExprContext primary) {
            var p = primary.primaryExpr();
            if (p.primaryExprQualified() != null) {
                String name = p.getText();
                String[] parts = name.split("\\.");
                if (domain != null && parts.length == 2
                        && domain.getSppClass(parts[0]) instanceof com.servpp.lang.models.SppEnum enumeration) {
                    var item = enumeration.getItems().stream().filter(member -> member.getName().equals(parts[1]))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown enum member " + name));
                    return new EnumValue(enumeration, item);
                }
                return reference(name);
            }
            if (p.primaryExprLiteral() != null) {
                Literal result = new Literal(); result.setValue(p.getText()); return result;
            }
            if (p.participantConstruction() != null) {
                if (domain == null) { throw new IllegalArgumentException("Participant construction is only allowed in calculation bodies."); }
                String name = p.participantConstruction().Identifier().getText();
                var type = domain.getSppClass(name);
                if (!(type instanceof com.servpp.lang.models.SppClass) || type instanceof com.servpp.lang.models.SppService
                        || type.getType() == null || !java.util.Set.of(com.servpp.lang.general.IConstance.CompilationUnitType.role,
                            com.servpp.lang.general.IConstance.CompilationUnitType.entity,
                            com.servpp.lang.general.IConstance.CompilationUnitType.contract,
                            com.servpp.lang.general.IConstance.CompilationUnitType.reference).contains(type.getType())) {
                    throw new IllegalArgumentException("Construct a declared participant type: " + name);
                }
                var construction=p.participantConstruction();
                boolean collection=construction.getChild(2).getText().equals("[");
                return new ParticipantConstruction(type, collection, construction.expression().stream()
                        .map(element -> expression(element,domain)).toList());
            }
            var function = p.calculationFunction();
            return new CalculationFunction(function.getChild(0).getText(),
                    function.expression().stream().map(argument -> expression(argument, domain)).toList());
        }
        if (ctx instanceof SppParser.QuestionExprContext conditional) {
            return new ConditionalExpression(expression(conditional.expression(0), domain),
                    expression(conditional.expression(1), domain), expression(conditional.expression(2), domain));
        }
        if (ctx instanceof SppParser.AssignExprContext || ctx instanceof SppParser.ArrayExprContext
                || ctx instanceof SppParser.RightIncDecExprContext || ctx instanceof SppParser.ShiftExprContext
                || ctx instanceof SppParser.BitAndExprContext || ctx instanceof SppParser.BitOrExprContext
                || ctx instanceof SppParser.BitXorExprContext) {
            throw new IllegalArgumentException("Business calculations do not support side effects, indexing or bit operations in expressions: " + ctx.getText());
        }
        OperationExpression result = new OperationExpression();
        var operands = ctx.getRuleContexts(SppParser.ExpressionContext.class);
        String operator;
        IOperation operation;
        if (ctx instanceof SppParser.ParenthesesExprContext) {
            operator = "(";
            BracketOperation brackets = new BracketOperation(); brackets.setRightBracket(")"); operation = brackets;
        } else {
            operator = ctx.getChild(operands.size() == 1 ? 0 : 1).getText();
            if (operator.equals("++") || operator.equals("--") || operator.equals("~")) {
                throw new IllegalArgumentException("Unsupported business operator " + operator);
            }
            operation = List.of("+", "-", "*", "/", "%").contains(operator)
                    ? new ArithmeticOperation() : new LogicOperation();
        }
        operation.setOperator(operator);
        operation.setType(operands.size() == 1 ? IOperation.Opers_Type.Left : IOperation.Opers_Type.Double);
        result.setOperation(operation);
        if (operands.size() == 2) { result.setLeft(expression(operands.get(0), domain)); }
        result.setRight(expression(operands.get(operands.size() - 1), domain));
        return result;
    }
}
