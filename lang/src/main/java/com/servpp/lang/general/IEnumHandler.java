package com.servpp.lang.general;

import com.servpp.lang.models.SppEnum;
import com.servpp.lang.models.SppEnumItem;
import com.servpp.lang.antlr.SppParser;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public interface IEnumHandler extends ICompilationUnitHandler{
    @Override
    default void enterEnumeration(SppParser.EnumerationContext ctx){
        String name = ctx.getChild(1).getText();
        if(getSppDomain().getSppClass(name)!= null){
            logSppError(ctx, "Duplicate enum " + name);
        }
        getSppDomain().addClass(new SppEnum(name));
    }

    @Override
    default void exitEnumeration(SppParser.EnumerationContext ctx){

    }

    @Override
    default void enterEnumBody(SppParser.EnumBodyContext ctx){
    }

    @Override
    default void exitEnumBody(SppParser.EnumBodyContext ctx){

    }

    @Override
    default void enterEnumBodyIdentifier(SppParser.EnumBodyIdentifierContext ctx){
        SppEnum sppEnum = (SppEnum) getSppDomain().getCurrentClass();
        SppEnumItem item = new SppEnumItem();
        item.setName(ctx.Identifier().getText());
        var aliases = ctx.StringLiteral().stream().map(token -> {
            String literal = token.getText();
            return literal.substring(1, literal.length() - 1);
        }).toList();
        item.setAliases(aliases);
        var seen = new java.util.HashSet<String>();
        for (String value : item.getWireValues()) {
            if (!seen.add(value) || sppEnum.getWireItem(value) != null) {
                getSppDomain().addAppParseError("Enum " + sppEnum.getName() + " repeats wire value "
                        + value + " (line " + ctx.getStart().getLine() + ").");
            }
        }
        if (ctx.enumFallback() != null) {
            var fallback = ctx.enumFallback().NullLiteral() != null
                    ? SppEnumItem.Fallback.NULL : SppEnumItem.Fallback.OTHER;
            if (sppEnum.getFallbackItem(fallback) != null) {
                getSppDomain().addAppParseError("Enum " + sppEnum.getName() + " can declare only one "
                        + ctx.enumFallback().getText() + " member (line " + ctx.getStart().getLine() + ").");
            } else {
                item.setFallback(fallback);
            }
        }
        sppEnum.getItems().add(item);
    }

    @Override
    default void exitEnumBodyIdentifier(SppParser.EnumBodyIdentifierContext ctx){

    }

    @Override
    default void enterEnumFallback(SppParser.EnumFallbackContext ctx) {
    }

    @Override
    default void exitEnumFallback(SppParser.EnumFallbackContext ctx) {
    }
}
