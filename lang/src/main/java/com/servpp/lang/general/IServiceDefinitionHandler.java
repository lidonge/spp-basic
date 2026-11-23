package com.servpp.lang.general;

import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.models.ServiceBody;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.models.SppService;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public interface IServiceDefinitionHandler extends ICompilationUnitHandler {
    @Override default void enterQueryKey(SppParser.QueryKeyContext ctx) { }
    @Override default void exitQueryKey(SppParser.QueryKeyContext ctx) { }
    @Override
    default void enterServiceDefinition(SppParser.ServiceDefinitionContext ctx) {
        String methodName = ctx.getChild(0).getText();
        String clsName = NameUtil.firstToLowerCase(methodName,false);
        SppService sppService = (SppService) getSppDomain().getCurrentClass();
        sppService.setName(clsName);
        sppService.setFuncName(methodName);
        String err = getSppDomain().checkDupClass(clsName);
        if(err != null){
            getSppDomain().addAppParseError("Duplicate service declaration: " + methodName
                    + " at line " + ctx.getStart().getLine() + ".");
        } else {
            sppService = (SppService) getSppDomain().addClass(sppService);
        }
        push(sppService.getServiceBody());

    }

    @Override
    default void exitServiceDefinition(SppParser.ServiceDefinitionContext ctx) {
        ServiceBody serviceBody = (ServiceBody) pop();
    }

    @Override
    default void enterParameterDeclarations(SppParser.ParameterDeclarationsContext ctx) {

    }

    @Override
    default void exitParameterDeclarations(SppParser.ParameterDeclarationsContext ctx) {
    }

    @Override
    default void enterParameterDeclaration(SppParser.ParameterDeclarationContext ctx) {
        if (ctx.getChildCount() != 0) {
            String ptype = ctx.type() == null ? ctx.roletype().getText() : ctx.type().getText();
            String name = ctx.Identifier() == null ? null : ctx.Identifier().getText();

            String[] sdims = ptype.split("\\[");
            String paramName = name == null ? NameUtil.participantName(sdims[0], sdims.length - 1) : name;
            SppDomain checker = checkClass(ctx, sdims[0]);
            int dim = sdims.length - 1;
            SppLocalVar sppLocalVar = generateLocalVar(sdims[0], dim, paramName);
            sppLocalVar.setQueryKey(ctx.queryKey() != null);
            Object container = peek();
            if (container instanceof ServiceBody) {
                ServiceBody serviceBody = (ServiceBody) container;
                String err = serviceBody.addLocalVar(sppLocalVar);
                if (err != null)
                    getSppDomain().addAppParseError(err + " at line " + ctx.getStart().getLine() + ".");
            }
        }

    }

}
