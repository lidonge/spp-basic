package com.servpp.lang.general;

import com.servpp.lang.antlr.SppListener;
import com.servpp.lang.antlr.SppParser;

/** Calculations are built as a subtree when exiting the atomic service. */
public interface ICalculationListener extends SppListener {
    @Override default void enterCalculationCase(SppParser.CalculationCaseContext ctx) { }
    @Override default void exitCalculationCase(SppParser.CalculationCaseContext ctx) { }
    @Override default void enterCalculationLocal(SppParser.CalculationLocalContext ctx) { }
    @Override default void exitCalculationLocal(SppParser.CalculationLocalContext ctx) { }
    @Override default void enterCalculationUpdates(SppParser.CalculationUpdatesContext ctx) { }
    @Override default void exitCalculationUpdates(SppParser.CalculationUpdatesContext ctx) { }
    @Override default void enterCalculationBody(SppParser.CalculationBodyContext ctx) { }
    @Override default void exitCalculationBody(SppParser.CalculationBodyContext ctx) { }
    @Override default void enterCalculationStatement(SppParser.CalculationStatementContext ctx) { }
    @Override default void exitCalculationStatement(SppParser.CalculationStatementContext ctx) { }
    @Override default void enterCalculationAssignment(SppParser.CalculationAssignmentContext ctx) { }
    @Override default void exitCalculationAssignment(SppParser.CalculationAssignmentContext ctx) { }
    @Override default void enterParticipantConstruction(SppParser.ParticipantConstructionContext ctx) { }
    @Override default void exitParticipantConstruction(SppParser.ParticipantConstructionContext ctx) { }
    @Override default void enterCalculationFunction(SppParser.CalculationFunctionContext ctx) { }
    @Override default void exitCalculationFunction(SppParser.CalculationFunctionContext ctx) { }
    @Override default void enterPrimaryExpr(SppParser.PrimaryExprContext ctx) { }
    @Override default void exitPrimaryExpr(SppParser.PrimaryExprContext ctx) { }
    @Override default void enterPrimaryExprQualified(SppParser.PrimaryExprQualifiedContext ctx) { }
    @Override default void exitPrimaryExprQualified(SppParser.PrimaryExprQualifiedContext ctx) { }
    @Override default void enterPrimaryExprLiteral(SppParser.PrimaryExprLiteralContext ctx) { }
    @Override default void exitPrimaryExprLiteral(SppParser.PrimaryExprLiteralContext ctx) { }
    @Override default void enterLiteral(SppParser.LiteralContext ctx) { }
    @Override default void exitLiteral(SppParser.LiteralContext ctx) { }
    @Override default void enterPrimExpr(SppParser.PrimExprContext ctx) { }
    @Override default void exitPrimExpr(SppParser.PrimExprContext ctx) { }
    @Override default void enterParenthesesExpr(SppParser.ParenthesesExprContext ctx) { }
    @Override default void exitParenthesesExpr(SppParser.ParenthesesExprContext ctx) { }
    @Override default void enterArrayExpr(SppParser.ArrayExprContext ctx) { }
    @Override default void exitArrayExpr(SppParser.ArrayExprContext ctx) { }
    @Override default void enterRightIncDecExpr(SppParser.RightIncDecExprContext ctx) { }
    @Override default void exitRightIncDecExpr(SppParser.RightIncDecExprContext ctx) { }
    @Override default void enterLeftIncDecAndSignExpr(SppParser.LeftIncDecAndSignExprContext ctx) { }
    @Override default void exitLeftIncDecAndSignExpr(SppParser.LeftIncDecAndSignExprContext ctx) { }
    @Override default void enterBitAndLogicNotExpr(SppParser.BitAndLogicNotExprContext ctx) { }
    @Override default void exitBitAndLogicNotExpr(SppParser.BitAndLogicNotExprContext ctx) { }
    @Override default void enterDevMultModeExpr(SppParser.DevMultModeExprContext ctx) { }
    @Override default void exitDevMultModeExpr(SppParser.DevMultModeExprContext ctx) { }
    @Override default void enterPlusMinusExpr(SppParser.PlusMinusExprContext ctx) { }
    @Override default void exitPlusMinusExpr(SppParser.PlusMinusExprContext ctx) { }
    @Override default void enterShiftExpr(SppParser.ShiftExprContext ctx) { }
    @Override default void exitShiftExpr(SppParser.ShiftExprContext ctx) { }
    @Override default void enterLogicGreatLessExpr(SppParser.LogicGreatLessExprContext ctx) { }
    @Override default void exitLogicGreatLessExpr(SppParser.LogicGreatLessExprContext ctx) { }
    @Override default void enterLogicEqualsExpr(SppParser.LogicEqualsExprContext ctx) { }
    @Override default void exitLogicEqualsExpr(SppParser.LogicEqualsExprContext ctx) { }
    @Override default void enterBitAndExpr(SppParser.BitAndExprContext ctx) { }
    @Override default void exitBitAndExpr(SppParser.BitAndExprContext ctx) { }
    @Override default void enterBitXorExpr(SppParser.BitXorExprContext ctx) { }
    @Override default void exitBitXorExpr(SppParser.BitXorExprContext ctx) { }
    @Override default void enterBitOrExpr(SppParser.BitOrExprContext ctx) { }
    @Override default void exitBitOrExpr(SppParser.BitOrExprContext ctx) { }
    @Override default void enterLogicAndExpr(SppParser.LogicAndExprContext ctx) { }
    @Override default void exitLogicAndExpr(SppParser.LogicAndExprContext ctx) { }
    @Override default void enterLogicOrExpr(SppParser.LogicOrExprContext ctx) { }
    @Override default void exitLogicOrExpr(SppParser.LogicOrExprContext ctx) { }
    @Override default void enterQuestionExpr(SppParser.QuestionExprContext ctx) { }
    @Override default void exitQuestionExpr(SppParser.QuestionExprContext ctx) { }
    @Override default void enterAssignExpr(SppParser.AssignExprContext ctx) { }
    @Override default void exitAssignExpr(SppParser.AssignExprContext ctx) { }
}
