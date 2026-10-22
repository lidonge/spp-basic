package com.servpp.lang.general.app;

import com.servpp.lang.general.NameUtil;
import com.servpp.lang.general.expr.IJavaExprHandler;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.models.SppService;
import com.servpp.lang.models.app.AppService;
import com.servpp.lang.models.app.AppServices;
import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.models.app.expr.RequireStatement;
import com.servpp.lang.antlr.AppParser;

import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface IServicesHandler extends IApplicationHandler, IJavaExprHandler {

    @Override
    default void enterServices(AppParser.ServicesContext ctx) {
        RuleBlock currentRuleBlock = getCurrentRuleBlock();
        currentRuleBlock.setCurrentAnnotatable(currentRuleBlock.getAppServices());
    }

    @Override
    public default void exitServices(AppParser.ServicesContext ctx) {

    }

    @Override
    default void enterService(AppParser.ServiceContext ctx) {
        String name = ctx.getChild(0).getText();
        AppService appService = new AppService();
        appService.setName(name);
        SppCompilationUnit sppClass = getSppDomian().getSppClass(NameUtil.firstToLowerCase(name, false));
        if (sppClass != null && sppClass instanceof SppService) {
            SppService sppService = (SppService) sppClass;
            String err = getCurrentRuleBlock().getAppServices().addService(appService);
            setCurrentContainer(appService);
            if (err != null) {
                logSppError(ctx, err);
                return;
            }
            appService.setSppService(sppService);
            if (sppService.hasBusinessImplementation() && !sppService.isDeclaredQuantumService()) {
                logSppError(ctx, name + " has both SPP and APP implementations.");
                getSppDomian().addAppParseError(name + " has both SPP and APP implementations.");
            } else {
                sppService.setExpressions(appService.getAppExpressions());
                sppService.setJavaStatements(appService.getJavaStatements());
            }
        } else {
            String err = getCurrentRuleBlock().getAppServices().addService(appService);
            setCurrentContainer(appService);
            if (err != null) {
                logSppError(ctx, err);
            }
            logSppError(ctx, name + " is null or not a service.");
        }
    }

    @Override
    default void enterExpressionStatement(AppParser.ExpressionStatementContext ctx) {
    }

    @Override
    default void exitExpressionStatement(AppParser.ExpressionStatementContext ctx) {
        setCurrentContainer(getTheAppService());
    }

    @Override
    default void enterRequireStatement(AppParser.RequireStatementContext ctx) {
        RequireStatement requireStatement = new RequireStatement(ctx.StringLiteral().getText());
        requireStatement.setParent(getTheAppService());
        setCurrentContainer(requireStatement);
    }

    @Override
    default void exitRequireStatement(AppParser.RequireStatementContext ctx) {
        RequireStatement requireStatement = (RequireStatement) getCurrentContainer();
        setCurrentContainer(getTheAppService());
        getTheAppService().addJavaStatement(requireStatement);
        setCurrentContainer(getTheAppService());
    }

    @Override
    default void enterAssignExpr(AppParser.AssignExprContext ctx) {
        _enterAssignExpr(ctx);
    }
    @Override
    default void exitAssignExpr(AppParser.AssignExprContext ctx){
        _exitAssignExpr(ctx);
    }

    @Override
    default void enterPrimaryExprQualified(AppParser.PrimaryExprQualifiedContext ctx) {
        _enterPrimaryExprQualified(ctx);
    }

    @Override
    default void exitPrimaryExprQualified(AppParser.PrimaryExprQualifiedContext ctx) {
        _exitPrimaryExprQualified(ctx);
    }

    @Override
    default void enterPrimaryExprLiteral(AppParser.PrimaryExprLiteralContext ctx) {
        _enterPrimaryExprLiteral(ctx);
    }

    @Override
    default void exitPrimaryExprLiteral(AppParser.PrimaryExprLiteralContext ctx) {
        _exitPrimaryExprLiteral(ctx);
    }

    @Override
    default void enterRightIncDecExpr(AppParser.RightIncDecExprContext ctx){
        _enterRightIncDecExpr(ctx);
    }

    @Override
    default void exitRightIncDecExpr(AppParser.RightIncDecExprContext ctx){
        _exitRightIncDecExpr(ctx);
    }

    @Override
    default void enterLeftIncDecAndSignExpr(AppParser.LeftIncDecAndSignExprContext ctx){
        _enterLeftIncDecAndSignExpr(ctx);
    }

    @Override
    default void exitLeftIncDecAndSignExpr(AppParser.LeftIncDecAndSignExprContext ctx){
        _exitLeftIncDecAndSignExpr(ctx);
    }

    @Override
    default void enterBitAndLogicNotExpr(AppParser.BitAndLogicNotExprContext ctx){
        _enterBitAndLogicNotExpr(ctx);
    }

    @Override
    default void exitBitAndLogicNotExpr(AppParser.BitAndLogicNotExprContext ctx) {
        _exitBitAndLogicNotExpr(ctx);
    }

    @Override
    default void enterDevMultModeExpr(AppParser.DevMultModeExprContext ctx) {
        _enterDevMultModeExpr(ctx);
    }

    @Override
    default void exitDevMultModeExpr(AppParser.DevMultModeExprContext ctx) {
        _exitDevMultModeExpr(ctx);
    }

    @Override
    default void enterPlusMinusExpr(AppParser.PlusMinusExprContext ctx) {
        _enterPlusMinusExpr(ctx);
    }

    @Override
    default void exitPlusMinusExpr(AppParser.PlusMinusExprContext ctx) {
        _exitPlusMinusExpr(ctx);
    }

    @Override
    default void enterShiftExpr(AppParser.ShiftExprContext ctx) {
        _enterShiftExpr(ctx);
    }

    @Override
    default void exitShiftExpr(AppParser.ShiftExprContext ctx) {
        _exitShiftExpr(ctx);
    }

    @Override
    default void enterLogicGreatLessExpr(AppParser.LogicGreatLessExprContext ctx) {
        _enterLogicGreatLessExpr(ctx);
    }

    @Override
    default void exitLogicGreatLessExpr(AppParser.LogicGreatLessExprContext ctx) {
        _exitLogicGreatLessExpr(ctx);
    }

    @Override
    default void enterLogicEqualsExpr(AppParser.LogicEqualsExprContext ctx) {
        _enterLogicEqualsExpr(ctx);
    }

    @Override
    default void exitLogicEqualsExpr(AppParser.LogicEqualsExprContext ctx) {
        _exitLogicEqualsExpr(ctx);
    }

    @Override
    default void enterBitAndExpr(AppParser.BitAndExprContext ctx) {
        _enterBitAndExpr(ctx);
    }

    @Override
    default void exitBitAndExpr(AppParser.BitAndExprContext ctx) {
        _exitBitAndExpr(ctx);
    }

    @Override
    default void enterBitXorExpr(AppParser.BitXorExprContext ctx) {
        _enterBitXorExpr(ctx);
    }

    @Override
    default void exitBitXorExpr(AppParser.BitXorExprContext ctx) {
        _exitBitXorExpr(ctx);
    }

    @Override
    default void enterBitOrExpr(AppParser.BitOrExprContext ctx) {
        _enterBitOrExpr(ctx);
    }

    @Override
    default void exitBitOrExpr(AppParser.BitOrExprContext ctx) {
        _exitBitOrExpr(ctx);
    }

    @Override
    default void enterLogicAndExpr(AppParser.LogicAndExprContext ctx) {
        _enterLogicAndExpr(ctx);
    }

    @Override
    default void exitLogicAndExpr(AppParser.LogicAndExprContext ctx) {
        _exitLogicAndExpr(ctx);
    }

    @Override
    default void enterLogicOrExpr(AppParser.LogicOrExprContext ctx) {
        _enterLogicOrExpr(ctx);
    }

    @Override
    default void exitLogicOrExpr(AppParser.LogicOrExprContext ctx) {
        _exitLogicOrExpr(ctx);
    }

    @Override
    default void enterQuestionExpr(AppParser.QuestionExprContext ctx) {
        _enterQuestionExpr(ctx);
    }

    @Override
    default void exitQuestionExpr(AppParser.QuestionExprContext ctx) {
        _exitQuestionExpr(ctx);
    }

    @Override
    default void enterParenthesesExpr(AppParser.ParenthesesExprContext ctx) {
        _enterParenthesesExpr(ctx);
    }

    @Override
    default void exitParenthesesExpr(AppParser.ParenthesesExprContext ctx) {
        _exitParenthesesExpr(ctx);
    }

    @Override
    default void enterArrayExpr(AppParser.ArrayExprContext ctx) {
        _enterArrayExpr(ctx);
    }

    @Override
    default void exitArrayExpr(AppParser.ArrayExprContext ctx) {
        _exitArrayExpr(ctx);
    }

    private AppService getTheAppService() {
        AppServices appServices = getCurrentRuleBlock().getAppServices();
        List<AppService> appServiceList = appServices.getAppServiceList();
        AppService appService = getLastElement(appServiceList);
        return appService;
    }

    @Override
    default Map<String, ? extends SppLocalVar> getLocalVarMap(){
        AppService appService = getTheAppService();
        if (appService.getSppService() == null) {
            return Map.of();
        }
        return appService.getSppService().getServiceBody().getSppLocalVarHashMap();
    }
}
