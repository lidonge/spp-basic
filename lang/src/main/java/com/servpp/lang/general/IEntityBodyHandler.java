package com.servpp.lang.general;

import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppRoleMapper;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public interface IEntityBodyHandler extends ICompilationUnitHandler {
    @Override
    default void enterEntityBody(SppParser.EntityBodyContext ctx) {
    }

    @Override
    default void exitEntityBody(SppParser.EntityBodyContext ctx) {
    }

    @Override
    default void enterFieldDeclaration(SppParser.FieldDeclarationContext ctx) {
        SppCompilationUnit owner = getSppDomain().getCurrentClass();
        // A declaration in take introduces a role fact, rather than a mapping
        // to an identically named fact on its performer.
        if (owner instanceof SppRoleMapper mapper) {
            getSppDomain().setCurrentClass(mapper.getRole());
        }
        try {
            declareParticipantField(ctx);
        } finally {
            getSppDomain().setCurrentClass(owner);
        }
    }

    default void declareParticipantField(SppParser.FieldDeclarationContext ctx) {
        boolean isQuantum = false;
        String type = ctx.type() != null ? ctx.type().getText() : ctx.entitytype().getText();
        if(type.startsWith("quantum")) {
            isQuantum = true;
            type =  type.substring("quantum".length());
        }
        String specification = type.replaceAll("\\[\\]", "");
        type = type.replaceAll("\\([^)]*\\)", "");
        String[] sdims = type.split("\\[");
        boolean defaultParticipantName = ctx.variableDeclaratorId() == null;
        String name = defaultParticipantName ? NameUtil.participantName(sdims[0], sdims.length - 1) : ctx.variableDeclaratorId().getText();
        if (defaultParticipantName && getSppDomain().getCurrentClass().getType() == CompilationUnitType.role)
            name = NameUtil.firstToLowerCase(name,true);

        SppDomain sppDomain = checkClass(ctx, sdims[0]);
        CompilationUnitType clstype = sppDomain.getCurrentClass().getType();
        SppCompilationUnit sppType = sppDomain.getSppClass(clstype, sdims[0]);
        int dim = sdims.length -1;
//        if( (clstype == CompilationUnitType.entity || clstype == CompilationUnitType.reference) &&
//                (sppType.getType() != null && sppType.getType() != CompilationUnitType.Enum) )
//            logSppError(ctx, "The field "+name+" of entity should be primary type!" );
        logSppError(ctx,generateField(sdims[0],dim,name,isQuantum));
        ((com.servpp.lang.models.SppClass)sppDomain.getCurrentClass()).getField(name)
                .setDefaultParticipantName(defaultParticipantName);
        var field = ((com.servpp.lang.models.SppClass)sppDomain.getCurrentClass()).getField(name);
        if (specification.contains("(") && !com.servpp.lang.models.app.NumericBinding.isNumeric(sdims[0]))
            logSppError(ctx, "Precision and scale are numeric physical defaults only");
        if (com.servpp.lang.models.app.NumericBinding.isNumeric(sdims[0])) {
            field.setDeclaredNumericSpecification(specification);
            try { com.servpp.lang.models.app.NumericBinding.dimensions(specification); }
            catch (IllegalArgumentException failure) { logSppError(ctx, failure.getMessage()); }
        }
        if (ctx.StringLiteral() != null) {
            String literal = ctx.StringLiteral().getText();
            ((com.servpp.lang.models.SppClass)sppDomain.getCurrentClass()).getField(name).setBusinessMeaning(literal.substring(1, literal.length()-1));
        }
    }

    @Override default void enterNumericPrecision(SppParser.NumericPrecisionContext ctx) { }
    @Override default void exitNumericPrecision(SppParser.NumericPrecisionContext ctx) { }

    @Override
    default void enterQuantumNumber(SppParser.QuantumNumberContext ctx){

    }

    @Override
    default void exitQuantumNumber(SppParser.QuantumNumberContext ctx){

    }
}
