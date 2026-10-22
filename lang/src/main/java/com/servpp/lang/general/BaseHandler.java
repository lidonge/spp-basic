package com.servpp.lang.general;

import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public abstract class BaseHandler implements
        IEntityHandler, IRoleHandler, IAtomicServiceHandler, IScenarioHandler, IActasHandler,IEnumHandler, ICalculationListener {

    @Override public void enterConstantDeclaration(SppParser.ConstantDeclarationContext ctx) {
        String name = ctx.Identifier().getText();
        var type = getSppDomain().getSppClass(null, ctx.primitiveType().getText());
        var constant = new com.servpp.lang.models.SppConstant(type, name, getSppDomain().getName(), ctx.literal().getText());
        if (getSppDomain().getConstants().putIfAbsent(name, constant) != null)
            logSppError(ctx, "Duplicate business constant " + name);
    }
    @Override public void exitConstantDeclaration(SppParser.ConstantDeclarationContext ctx) { }

    @Override
    public void exitRole(SppParser.RoleContext ctx) {

    }

    @Override
    public void enterQualifiedName(SppParser.QualifiedNameContext ctx) {

    }

    @Override
    public void exitQualifiedName(SppParser.QualifiedNameContext ctx) {

    }

    @Override
    public void enterProgram(SppParser.ProgramContext ctx) {

    }

    @Override
    public void exitProgram(SppParser.ProgramContext ctx) {

    }

    @Override
    public void enterType(SppParser.TypeContext ctx) {

    }

    @Override
    public void exitType(SppParser.TypeContext ctx) {

    }

    @Override
    public void enterScenarioDeclaration(SppParser.ScenarioDeclarationContext ctx) {

    }

    @Override
    public void enterRoletype(SppParser.RoletypeContext ctx) {

    }

    @Override
    public void exitRoletype(SppParser.RoletypeContext ctx) {

    }

    @Override
    public void enterPrimitiveType(SppParser.PrimitiveTypeContext ctx) {

    }

    @Override
    public void exitPrimitiveType(SppParser.PrimitiveTypeContext ctx) {

    }

    @Override
    public void enterNumberType(SppParser.NumberTypeContext ctx) {

    }

    @Override
    public void exitNumberType(SppParser.NumberTypeContext ctx) {

    }

    @Override
    public void enterQuantumService(SppParser.QuantumServiceContext ctx) {

    }

    @Override
    public void exitQuantumService(SppParser.QuantumServiceContext ctx) {

    }

    @Override
    public void enterDomainname(SppParser.DomainnameContext ctx) {

    }

    @Override
    public void exitDomainname(SppParser.DomainnameContext ctx) {

    }

    @Override
    public void exitFieldDeclaration(SppParser.FieldDeclarationContext ctx) {

    }

    @Override
    public void enterServicedomain(SppParser.ServicedomainContext ctx) {

    }

    @Override
    public void exitServicedomain(SppParser.ServicedomainContext ctx) {

    }

    @Override
    public void enterRoleBody(SppParser.RoleBodyContext ctx) {

    }

    @Override
    public void exitRoleBody(SppParser.RoleBodyContext ctx) {

    }

    @Override
    public void enterCompilationUnits(SppParser.CompilationUnitsContext ctx) {

    }

    @Override
    public void exitCompilationUnits(SppParser.CompilationUnitsContext ctx) {

    }

    @Override
    public void enterEntityBodyDeclaration(SppParser.EntityBodyDeclarationContext ctx) {

    }

    @Override
    public void exitEntityBodyDeclaration(SppParser.EntityBodyDeclarationContext ctx) {

    }

    @Override
    public void enterCompilationUnit(SppParser.CompilationUnitContext ctx) {

    }

    @Override
    public void exitCompilationUnit(SppParser.CompilationUnitContext ctx) {

    }

    @Override
    public void enterEntitytype(SppParser.EntitytypeContext ctx) {

    }

    @Override
    public void exitEntitytype(SppParser.EntitytypeContext ctx) {

    }

    @Override
    public void exitParameterDeclaration(SppParser.ParameterDeclarationContext ctx) {

    }

    @Override
    public void exitEntity(SppParser.EntityContext ctx) {

    }

    @Override
    public void enterVariableDeclaratorId(SppParser.VariableDeclaratorIdContext ctx) {

    }

    @Override
    public void exitVariableDeclaratorId(SppParser.VariableDeclaratorIdContext ctx) {

    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {

    }

    @Override
    public void visitErrorNode(ErrorNode errorNode) {

    }

    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {

    }

    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {

    }
}
