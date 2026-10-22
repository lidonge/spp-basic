package com.servpp.lang.general;


import com.servpp.lang.antlr.SppParser;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public interface IEntityHandler extends IEntityBodyHandler {
    @Override
    default void enterEntity(SppParser.EntityContext ctx) {
//        String objOrRef = ctx.getChild(0).getText();
        boolean isReference = "Reference".equals(ctx.getChild(0).getText());
        String objName = ctx.getChild(1).getText();
//        System.out.println("enterEntity:" + objName);

        var participant = (com.servpp.lang.models.SppClass) generateClass(
                isReference ? CompilationUnitType.reference: CompilationUnitType.entity, ctx, objName);
        if (ctx.qualifiedName() != null) {
            if (!isReference) logSppError(ctx, "Only Reference can declare a provider domain.");
            participant.setReferenceSource(ctx.qualifiedName().getText());
        }
    }

}
