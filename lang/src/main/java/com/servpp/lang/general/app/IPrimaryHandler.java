package com.servpp.lang.general.app;

import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.models.app.SppFieldReference;
import com.servpp.lang.antlr.AppParser;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public interface IPrimaryHandler extends IApplicationHandler {

    @Override
    default void enterPrimaryQualified(AppParser.PrimaryQualifiedContext ctx){
        RuleBlock ruleBlock = getCurrentRuleBlock();
        String qualifiedName = ctx.getChild(0).getText();
        SppFieldReference sppField = getQualifiedField(ctx, null, qualifiedName);
        if(sppField == null)
            logSppError(ctx, "File " + qualifiedName+" not defined");
        else
            ruleBlock.addPrimaryKey(qualifiedName,sppField);
    }
}
