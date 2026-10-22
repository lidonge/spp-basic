package com.servpp.lang.general.app;

import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.app.AppHeader;
import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.models.app.SppExtendField;
import com.servpp.lang.antlr.AppParser;
import org.antlr.v4.runtime.misc.NotNull;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public interface IHeaderHandler extends IApplicationHandler {
    @Override
    default void enterHeader(@NotNull AppParser.HeaderContext ctx){
        String name = ctx.getChild(1).getText();
        AppHeader lookup = new AppHeader();
        lookup.setName(name);
        AppHeader existing = getCurrentRuleBlock().searchAppHeader(lookup);
        AppHeader appHeader = existing == null ? getCurrentRuleBlock().newOrExistAppHeader(name) : existing;
        String err = null;
        if (existing != null && !existing.isTemp()) {
            err = "Duplicate header " + name;
            appHeader = new AppHeader();
            appHeader.setName(name);
        } else {
            appHeader.setTemp(false);
        }
        getCurrentRuleBlock().setCurrentAnnotatable(appHeader);
        if(err != null){
            logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(),err);
        }
    }

    @Override
    default void enterExtendsHeaders(@NotNull AppParser.ExtendsHeadersContext ctx){
        RuleBlock ruleBlock = getCurrentRuleBlock();
        AppHeader curHeader = getCurrentHeader();
        for (var identifier : ctx.Identifier()) {
            AppHeader header = ruleBlock.newOrExistAppHeader(identifier.getText());
            curHeader.addExtendsHeader(header);
        }
    }

    @Override
    default void enterHeaderFieldDefine(AppParser.HeaderFieldDefineContext ctx){
        SppExtendField field = new SppExtendField(null,null);
        getCurrentHeader()._addExtendField(field);
    }

    @Override
    default void enterHeaderFieldModifierNotNull(AppParser.HeaderFieldModifierNotNullContext ctx){
        AppHeader appHeader = getCurrentHeader();
        SppExtendField sppExtendField = getLastElement(appHeader.getSppExtendFields());
        sppExtendField.addNotNullModifier();
    }

    @Override
    default void enterHeaderFieldModifierUnique(AppParser.HeaderFieldModifierUniqueContext ctx){
        AppHeader appHeader = getCurrentHeader();
        SppExtendField sppExtendField = getLastElement(appHeader.getSppExtendFields());
        sppExtendField.addUniqueModifier();
    }

    @Override
    default void enterHeaderFieldDefinePrimitiveType(AppParser.HeaderFieldDefinePrimitiveTypeContext ctx){
        String text = ctx.getChild(0).getText();
        SppCompilationUnit type = getSppDomian().getSppClass(null,text);
        AppHeader appHeader = getCurrentHeader();
        SppExtendField sppExtendField = getLastElement(appHeader.getSppExtendFields());
        sppExtendField.setType(type);
    }

    @Override
    default void enterHeaderFieldDefineIdentifier(AppParser.HeaderFieldDefineIdentifierContext ctx){
        AppHeader appHeader = getCurrentHeader();
        SppExtendField sppExtendField = getLastElement(appHeader.getSppExtendFields());
        removeLastElement(appHeader.getSppExtendFields());
        sppExtendField.setName(ctx.getChild(0).getText());
        String err = appHeader.addExtendField(sppExtendField);
        if(err != null)
            logSppError(ctx,err);
    }
    @Override
    default void enterDefaultValueliteral(AppParser.DefaultValueliteralContext ctx){
        AppHeader appHeader = getCurrentHeader();
        SppExtendField sppExtendField = getLastElement(appHeader.getSppExtendFields());
        sppExtendField.setDefaultValue(ctx.getChild(0).getText());
    }

    @Override
    default void enterOverride(AppParser.OverrideContext ctx){
        AppHeader appHeader = getCurrentHeader();
        SppExtendField sppExtendField = getLastElement(appHeader.getSppExtendFields());
        sppExtendField.setOverrideSuper(ctx.getChild(1).getText());
    }

    default AppHeader getCurrentHeader() {
        return (AppHeader) getCurrentRuleBlock().getCurrentAnnotatable();
    }
}
