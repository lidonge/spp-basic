package com.servpp.lang.general.app;

import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.app.AnnotationDefine;
import com.servpp.lang.models.app.AppAnnotation;
import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.antlr.AppParser;

import java.util.List;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public interface IAnnotateHandler extends IApplicationHandler, IRecursionProcess {
    @Override
    default void enterAnnotate(AppParser.AnnotateContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        AppAnnotation appAnnotation = new AppAnnotation();
        List<AppAnnotation> appAnnotationList = ruleBlock.getAppAnnotationList();
        if (ctx.getParent() instanceof AppParser.AnnotateBodyContext) {
            addRecursionComponent(appAnnotation);
        }
    }

    @Override
    default void exitAnnotate(AppParser.AnnotateContext ctx) {
        exitCurrent();
    }

    @Override
    default void enterAnnotateDefine(AppParser.AnnotateDefineContext ctx) {
        String text = ctx.getChild(1).getText();
        AnnotationDefine annotationDefine = new AnnotationDefine(text);
        IContainer cont = getCurrentContainer();
        if (cont == null || !(cont instanceof AppAnnotation)) {
            getCurrentRuleBlock().addLineAnn(annotationDefine);
        } else {
            AppAnnotation annotation = (AppAnnotation) cont;
            annotationDefine = annotation.addAnnotationDefine(annotationDefine);
            if (annotationDefine == null) {
                logSppError(ctx, "Duplicate  annotation " + text);
            }
        }
    }

    @Override
    default void exitAnnotateDefine(AppParser.AnnotateDefineContext ctx) {
    }

    @Override
    default void enterAnnotatableRule(AppParser.AnnotatableRuleContext ctx) {
    }

    @Override
    default void exitAnnotatableRule(AppParser.AnnotatableRuleContext ctx) {
        RuleBlock currentRuleBlock = getCurrentRuleBlock();
        List<AnnotationDefine> currentLineAnns = currentRuleBlock.getCurrentLineAnns();
        if (ctx.phy() != null) {
            if (currentLineAnns != null && !currentLineAnns.isEmpty()) {
                getSppDomian().addAppParseError("PHY blocks do not support APP annotations.");
            }
            currentRuleBlock.clearLineAnn();
            return;
        }
        if (ctx.applicationDeployment() != null || ctx.localServiceBinding() != null || ctx.internationalization() != null || ctx.requestPolicy() != null || ctx.transactionPolicy() != null || ctx.propertyTrackingPolicy() != null
                || ctx.technicalServices() != null || ctx.serviceInterception() != null || ctx.systemServices() != null) {
            if (currentLineAnns != null && !currentLineAnns.isEmpty())
                getSppDomian().addAppParseError("Deployment, binding, i18n, request, transaction and technical interception declarations do not support APP annotations.");
            currentRuleBlock.clearLineAnn();
            return;
        }
        currentRuleBlock.getCurrentAnnotatable().setAnnotations(currentLineAnns);
        currentRuleBlock.clearLineAnn();
    }

    @Override
    default void enterAnnotateParameter(AppParser.AnnotateParameterContext ctx) {
        String name = ctx.getChild(0).getText();
        String value = ctx.getChild(2).getText();
        value = value.substring(1, value.length() - 1);
        AnnotationDefine annotationDefine = null;
        IContainer cont = getCurrentContainer();
        if (cont == null || !(cont instanceof AppAnnotation)) {
            annotationDefine = getLastElement(getCurrentRuleBlock().getCurrentLineAnns());
        } else {
            AppAnnotation annotation = (AppAnnotation) cont;
            annotationDefine = getLastElement(annotation.getAnnotationDefineList());
        }
        annotationDefine.addParameter(name, value);
    }

    @Override
    default void exitAnnotateParameter(AppParser.AnnotateParameterContext ctx) {

    }

    @Override
    default void enterAnnotateBody(AppParser.AnnotateBodyContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        if (ruleBlock.getCurrentLineAnns() != null) {
            AppAnnotation appAnnotation = new AppAnnotation();
            ruleBlock.getAppAnnotations().addComponent(appAnnotation);
            setCurrentContainer(appAnnotation);
            for (AnnotationDefine annotationDefine : ruleBlock.getCurrentLineAnns())
                appAnnotation.addAnnotationDefine(annotationDefine);
            ruleBlock.clearLineAnn();
        }
    }

    @Override
    default void exitAnnotateBody(AppParser.AnnotateBodyContext ctx) {
    }

    @Override
    default void enterAnnotateBodyIdentifier(AppParser.AnnotateBodyIdentifierContext ctx) {
        String clsName = ctx.getChild(0).getText();
        SppCompilationUnit unit = getSppDomian().getSppClass(clsName);
        unit = unit == null ? getSppDomian().getSppClass(NameUtil.firstToLowerCase(clsName, false)) : unit;
        if (unit == null) {
            logSppError(ctx, "Can not find annotated unit " + clsName);
        } else {
            IContainer cont = getCurrentContainer();
            AppAnnotation annotation = (AppAnnotation) cont;
            if (annotation.getUnits().contains(unit)) {
                logSppError(ctx, "Duplicate unit " + clsName + " in annotation define.");
            } else {
                annotation.getUnits().add(unit);
            }
        }
    }

    @Override
    default void exitAnnotateBodyIdentifier(AppParser.AnnotateBodyIdentifierContext ctx) {
    }

    @Override
    default void enterAnnotateParameterDefine(AppParser.AnnotateParameterDefineContext ctx) {
    }

    @Override
    default void exitAnnotateParameterDefine(AppParser.AnnotateParameterDefineContext ctx) {
    }
}
