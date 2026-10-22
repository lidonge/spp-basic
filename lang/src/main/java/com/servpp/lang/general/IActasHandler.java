package com.servpp.lang.general;

import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppRoleField;
import com.servpp.lang.models.SppRoleMapper;
import com.servpp.lang.models.SppVarMaker;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

/**
 * @author lidong@date 2023-11-03@version 1.0
 */
public interface IActasHandler extends ICompilationUnitHandler {
    @Override
    default void enterActas(SppParser.ActasContext ctx){
        List<TerminalNode> tokens = ctx.getTokens(SppParser.Identifier);
        String entityName = "";
        String roleName = getLastElement(tokens).getText();
//        System.out.println("entity : " + entityName + ", role:"+roleName);
        for(int i = 0;i< tokens.size() -1;i++){
            entityName += tokens.get(i).getText();
        }
        SppRoleMapper sppRoleMapper = (SppRoleMapper) generateClass(CompilationUnitType.rolemapper,ctx,
                "Mapper"+entityName+"To"+roleName);
        SppClass role = (SppClass) getSppDomain().getSppClass(CompilationUnitType.role,roleName);
        sppRoleMapper.setRole(role);
        for(int i = 0;i< tokens.size() -1;i++) {
            String participantName = tokens.get(i).getText();
            SppClass participant = (SppClass) getSppDomain().getSppClass(participantName);
            if (participant == null) {
                participant = (SppClass) getSppDomain().getSppClass(CompilationUnitType.entity, participantName);
            }
            sppRoleMapper.addEntity(participant);
        }
    }
    @Override
    default void exitActas(SppParser.ActasContext ctx){

    }

    @Override
    default void enterMapBody(SppParser.MapBodyContext ctx){

    }
    @Override
    default void exitMapBody(SppParser.MapBodyContext ctx){

    }
    @Override
    default void enterMapDeclaration(SppParser.MapDeclarationContext ctx){
    }
    @Override
    default void exitMapDeclaration(SppParser.MapDeclarationContext ctx){

    }
    default void enterMapfield(SppParser.MapfieldContext ctx){
        String type = ctx.getChild(0).getText();
        var names = ctx.getTokens(SppParser.Identifier);
        String roleName = names.get(names.size()-2).getText();
        String entityName = names.get(names.size()-1).getText();
//        System.out.println(type + "," +roleName+"," +entityName);

        SppVarMaker<SppClass, String> maker = (cls, name) ->{return new SppRoleField(cls,name);};
        String err = generateField(type,roleName,maker);
        SppRoleField roleField = (SppRoleField) ((SppRoleMapper) getSppDomain().getCurrentClass()).getField(roleName);
        roleField.setEntityName(entityName);
        if (ctx.entitytype() != null) {
            SppRoleMapper mapper = (SppRoleMapper) getSppDomain().getCurrentClass();
            if (!type.equals(entityName) || mapper.getEntities().stream().noneMatch(p -> p.getName().equals(entityName)))
                logSppError(ctx, "Participant mapping must name a declared performer of the same type.");
            SppClass role = mapper.getRole();
            if (role.getField(roleName) != null && !role.getField(roleName).getType().getName().equals(type))
                logSppError(ctx, "Participant mapping must match the role field type.");
        }
        logSppError(ctx,err);
    }
    default void exitMapfield(SppParser.MapfieldContext ctx){

    }

    @Override
    default void enterTakeall(SppParser.TakeallContext ctx) {
        SppRoleMapper sppRoleMapper = (SppRoleMapper) getSppDomain().getCurrentClass();
        sppRoleMapper.setTakeAll(true);
    }

    @Override
    default void exitTakeall(SppParser.TakeallContext ctx) {

    }

    @Override
    default void enterMapsame(SppParser.MapsameContext ctx) {
        SppRoleMapper sppRoleMapper = (SppRoleMapper) getSppDomain().getCurrentClass();
        sppRoleMapper.setMapSame(true);
    }

    @Override
    default void exitMapsame(SppParser.MapsameContext ctx) {

    }


}
