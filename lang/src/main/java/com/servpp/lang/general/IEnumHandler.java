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
        item.setName(ctx.getChild(0).getText());
        sppEnum.getItems().add(item);
        if(ctx.getChildCount() == 4){
            String value = ctx.getChild(2).getText();
            value = value.substring(1,value.length() -1);
            item.setValue(value);
        }
    }

    @Override
    default void exitEnumBodyIdentifier(SppParser.EnumBodyIdentifierContext ctx){

    }
}
