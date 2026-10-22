package com.servpp.lang.general.expr;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.ISppErrorLogger;
import com.servpp.lang.general.app.BracketOperation;
import com.servpp.lang.general.app.IRecursionProcess;
import com.servpp.lang.general.app.IServicesHandler;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.models.app.IQualifiedNameUtil;
import com.servpp.lang.models.app.expr.*;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Map;

/**
 * @author lidong@date 2023-12-01@version 1.0
 */
public interface IJavaExprHandler extends IRecursionProcess, IConstance, ISppErrorLogger, IQualifiedNameUtil {

    Map<String, ? extends SppLocalVar> getLocalVarMap();

    default void _enterAssignExpr(ParserRuleContext ctx) {
        String text = ctx.getChild(1).getText();
        AssignExpression assignExpression = getAssignExpression(text);
        addRecursionComponent(assignExpression);
    }

    default void _exitAssignExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterPrimaryExprQualified(ParserRuleContext ctx) {
        String text = ctx.getChild(0).getText();
        Reference reference = new Reference();
        reference.setValue(text);
        SppLocalVar sppLocalVar = getQualifieField(text, getLocalVarMap());
        if (sppLocalVar == null) {
            logSppError(ctx, text + " not defined");
            addRecursionComponent(reference);
            return;
        }
        reference.setReturnType(sppLocalVar.getType());
        addRecursionComponent(reference);
    }


    default void _exitPrimaryExprQualified(ParserRuleContext ctx) {

    }


    default void _enterPrimaryExprLiteral(ParserRuleContext ctx) {
        String text = ctx.getChild(0).getText();
        Literal reference = new Literal();
        reference.setValue(text);
        addRecursionComponent(reference);

    }


    default void _exitPrimaryExprLiteral(ParserRuleContext ctx) {
    }


    default void _enterRightIncDecExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Right, () -> {
            return new AssignOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitRightIncDecExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterLeftIncDecAndSignExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Left, () -> {
            return new ArithmeticOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitLeftIncDecAndSignExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterBitAndLogicNotExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Left, () -> {
            String text = ctx.getChild(0).getText();
            if (text.equals("~"))
                return new BitOperation();
            else
                return new LogicOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitBitAndLogicNotExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterDevMultModeExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new ArithmeticOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitDevMultModeExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterPlusMinusExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new ArithmeticOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitPlusMinusExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterShiftExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new BitOperation();
        });
        addRecursionComponent(operationExpression);

    }


    default void _exitShiftExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterLogicGreatLessExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new LogicOperation();
        });
        addRecursionComponent(operationExpression);

    }


    default void _exitLogicGreatLessExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterLogicEqualsExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new LogicOperation();
        });
        addRecursionComponent(operationExpression);

    }


    default void _exitLogicEqualsExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterBitAndExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new BitOperation();
        });
        addRecursionComponent(operationExpression);

    }


    default void _exitBitAndExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterBitXorExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new BitOperation();
        });
        addRecursionComponent(operationExpression);

    }


    default void _exitBitXorExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterBitOrExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new BitOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitBitOrExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterLogicAndExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new LogicOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitLogicAndExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterLogicOrExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            return new LogicOperation();
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitLogicOrExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterQuestionExpr(ParserRuleContext ctx) {

    }


    default void _exitQuestionExpr(ParserRuleContext ctx) {

    }


    default void _enterParenthesesExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Left, () -> {
            BracketOperation bracketOperation = new BracketOperation();
            bracketOperation.setRightBracket(ctx.getChild(2).getText());
            return bracketOperation;
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitParenthesesExpr(ParserRuleContext ctx) {
        exitCurrent();
    }


    default void _enterArrayExpr(ParserRuleContext ctx) {
        OperationExpression operationExpression = createOperationExpression(ctx, IOperation.Opers_Type.Double, () -> {
            BracketOperation bracketOperation = new BracketOperation();
            bracketOperation.setRightBracket(ctx.getChild(3).getText());
            return bracketOperation;
        });
        addRecursionComponent(operationExpression);
    }


    default void _exitArrayExpr(ParserRuleContext ctx) {
        exitCurrent();
    }

    interface IOperationCreator {
        IOperation createOperation();
    }

    private OperationExpression createOperationExpression(ParserRuleContext ctx, IOperation.Opers_Type type, IServicesHandler.IOperationCreator creator) {
        OperationExpression operationExpression = new OperationExpression();
        int pos = 0;
        switch (type) {
            case Right:
            case Double:
                pos = 1;
                break;
        }
        String text = ctx.getChild(pos).getText();
        IOperation operation = creator.createOperation();

        operation.setOperator(text);
        operation.setType(type);
        operationExpression.setOperation(operation);
        return operationExpression;
    }

    private AssignExpression getAssignExpression(String text) {
        AssignExpression assignExpression = new AssignExpression();
        String soper = text.substring(0, text.indexOf("="));
        AssignOperation assignOperation = new AssignOperation();
        if (!soper.isEmpty()) {
            assignOperation.setAssoc(soper);
        }
        assignOperation.setType(IOperation.Opers_Type.Double);
        assignExpression.setOperation(assignOperation);
        return assignExpression;
    }
}
