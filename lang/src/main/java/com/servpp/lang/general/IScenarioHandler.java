package com.servpp.lang.general;

import com.servpp.lang.models.*;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.misc.NotNull;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public interface IScenarioHandler extends IServiceDefinitionHandler {
    @Override
    default void enterScenario(SppParser.ScenarioContext ctx) {
//        push(CompilationUnitType.scenario);
//        push(ScenarioType.parallel);
        SppScenario currentClass = new SppScenario("");
        currentClass.setType(CompilationUnitType.scenario);
        currentClass.setScenarioType(ScenarioType.parallel);
        currentClass.setReadonly(ctx.readonlyModifier() != null);
        getSppDomain().setCurrentClass(currentClass);
    }

    @Override
    default void exitScenario(SppParser.ScenarioContext ctx) {
        getSppDomain().setCurrentClass(null);
    }
    @Override default void enterReadonlyModifier(SppParser.ReadonlyModifierContext ctx) { }
    @Override default void exitReadonlyModifier(SppParser.ReadonlyModifierContext ctx) { }

    @Override
    default void enterAutosort(SppParser.AutosortContext ctx) {
        String st = ctx.getChild(0).getText();
        IConstance.ScenarioType scenarioType = ScenarioType.valueOf(st);
        SppScenario sppScenario = (SppScenario) getSppDomain().getCurrentClass();
        sppScenario.setScenarioType(scenarioType);
    }
    @Override
    default void exitAutosort(SppParser.AutosortContext ctx) {
    }

    @Override
    default void enterExecutegroup(SppParser.ExecutegroupContext ctx){
        SppScenario sppScenario = (SppScenario) getSppDomain().getCurrentClass();
        if (usesSystemResult(ctx))
            SystemResultModels.enable(sppScenario, getSppDomain());
        ExecuteBlock executeBlock = createExecuteBlock(sppScenario.getScenarioType());

        push(executeBlock);
    }

    private boolean usesSystemResult(org.antlr.v4.runtime.tree.ParseTree tree) {
        if (tree instanceof SppParser.TransactionFailureContext) return true;
        if (tree instanceof SppParser.QualifiedNameContext path
                && (path.getText().equals("ResultInfo") || path.getText().startsWith("ResultInfo.")
                    || path.getText().contains(".resultInfo"))) return true;
        for (int index = 0; index < tree.getChildCount(); index++)
            if (usesSystemResult(tree.getChild(index))) return true;
        return false;
    }

    @Override
    default void exitExecutegroup(SppParser.ExecutegroupContext ctx){
        ExecuteBlock executeBlock = (ExecuteBlock) pop();
    }

    @Override
    default void enterSerialgroup(@NotNull SppParser.SerialgroupContext ctx){
        ExecuteBlock executeBlock = createExecuteBlock(ScenarioType.serial);

        push(executeBlock);
    }

    @Override
    default void exitSerialgroup(@NotNull SppParser.SerialgroupContext ctx){
        ExecuteBlock executeBlock = (ExecuteBlock) pop();
    }

    @Override
    default void enterParallelgroup(@NotNull SppParser.ParallelgroupContext ctx){
        ExecuteBlock executeBlock = createExecuteBlock(ScenarioType.parallel);

        push(executeBlock);
    }
    @Override
    default void exitParallelgroup(@NotNull SppParser.ParallelgroupContext ctx){
        ExecuteBlock executeBlock = (ExecuteBlock) pop();
    }

    @Override
    default void enterScenarioIf(SppParser.ScenarioIfContext ctx) {
        com.servpp.lang.models.app.expr.IExpression condition;
        try {
            condition = com.servpp.lang.general.expr.BusinessCalculationCompiler.expression(ctx.expression(), getSppDomain());
        } catch (IllegalArgumentException e) {
            getSppDomain().addAppParseError("Scenario " + getSppDomain().getCurrentClass().getName()
                    + " condition at line " + ctx.getStart().getLine() + ": " + e.getMessage());
            var invalid = new com.servpp.lang.models.app.expr.Literal();
            invalid.setValue("false");
            condition = invalid;
        }
        ConditionalBlock block = new ConditionalBlock(condition);
        getCurrentServiceBaseBody().addServiceCall(block);
        push(block);
    }

    @Override
    default void exitScenarioIf(SppParser.ScenarioIfContext ctx) { pop(); }

    @Override
    default void enterScenarioBranch(SppParser.ScenarioBranchContext ctx) {
        if (getSppDomain().getCurrentClass() instanceof SppScenario) push(createExecuteBlock(ScenarioType.serial));
    }

    @Override
    default void exitScenarioBranch(SppParser.ScenarioBranchContext ctx) { if (getSppDomain().getCurrentClass() instanceof SppScenario) pop(); }

    @Override
    default void enterTransactionStatements(SppParser.TransactionStatementsContext ctx) {
        ServiceBaseBody serviceBaseBody = getCurrentServiceBaseBody();
        if (ctx.transactionFailure() != null) {
            var failureBlock = new com.servpp.lang.models.TransactionFailureBlock();
            if (ctx.transactionFailure().qualifiedName() != null)
                failureBlock.setContractTarget(ctx.transactionFailure().qualifiedName().getText());
            serviceBaseBody.addServiceCall(failureBlock);
            push(failureBlock);
            serviceBaseBody = failureBlock;
        }
        TransactionBlock transactionBlock = new TransactionBlock();
        if (ctx.transactionPropagation() != null) {
            transactionBlock.setPropagation(ctx.transactionPropagation().getText());
        }

        serviceBaseBody.addServiceCall(transactionBlock);
        push(transactionBlock);
    }

    @Override
    default void exitTransactionStatements(SppParser.TransactionStatementsContext ctx) {
        pop();
    }

    @Override
    default void enterTransactionFailure(SppParser.TransactionFailureContext ctx) {
        pop(); // Finish the failed transaction before entering its sibling failure block.
        push(createExecuteBlock(ScenarioType.serial));
    }

    @Override
    default void exitTransactionFailure(SppParser.TransactionFailureContext ctx) {
        pop();
    }

    @Override
    default void enterTransactionPropagation(SppParser.TransactionPropagationContext ctx) {
    }

    @Override
    default void exitTransactionPropagation(SppParser.TransactionPropagationContext ctx) {
    }

    @Override
    default void enterAtomiccall(SppParser.AtomiccallContext ctx) {
        if (!(getSppDomain().getCurrentClass() instanceof SppScenario)) return;
        if (ctx.Identifier() == null) {
            SppServiceCall call = new SppServiceCall();
            call.setIdentityTarget(NameUtil.normalizeParticipantReference(ctx.qualifiedName().getText()));
            call.setBreaksTransaction(ctx.transactionBreak() != null);
            getCurrentServiceBaseBody().addServiceCall(call);
            if (ctx.scenarioBranch() != null) push(call);
            return;
        }
        String serviceName = ctx.Identifier().getText();
        String serviceClassName = NameUtil.firstToLowerCase(serviceName,false);
        SppDomain checker = checkClass(ctx,serviceClassName);
        SppService currentCLass = (SppService) checker.getCurrentClass();
        SppServiceCall sppServiceCall = new SppServiceCall();
        sppServiceCall.sourceLocation = com.servpp.lang.general.expr.BusinessCalculationCompiler.sourceLocation(ctx.getStart());
        sppServiceCall.setUniqueRead(ctx.uniqueModifier() != null);
        if (ctx.rejection != null) sppServiceCall.setUniqueCodeName(ctx.rejection.getText());
        if (sppServiceCall.isUniqueRead() && ctx.readBinding() == null)
            checker.addAppParseError("unique requires a find by read binding");
        if (!sppServiceCall.isUniqueRead() && ctx.rejection != null)
            checker.addAppParseError("else on a keyed read requires unique");
        sppServiceCall.setInvocationOrdinal(currentCLass.allocateInvocationOrdinal());
        if (ctx.quantumBounds() != null) for (var interval : ctx.quantumBounds().quantumBound())
            sppServiceCall.getQuantumBounds().add(new QuantumCallBound(NameUtil.normalizeParticipantReference(interval.qualifiedName().getText()),
                    com.servpp.lang.general.expr.BusinessCalculationCompiler.expression(interval.expression(0), getSppDomain()),
                    com.servpp.lang.general.expr.BusinessCalculationCompiler.expression(interval.expression(1), getSppDomain())));
        if (ctx.readBinding() != null) for (var key : ctx.readBinding().readKey()) {
            var constant = key.readConstant();
            if (constant == null) {
                sppServiceCall.getReadKeys().add(new SppServiceCall.ReadKey(key.Identifier().getText(), null, null));
                continue;
            }
            String text = constant.getText();
            String literalType = constant.Identifier() != null ? null
                    : constant.StringLiteral() != null ? "String"
                    : constant.CharacterLiteral() != null ? "char"
                    : constant.BooleanLiteral() != null ? "boolean"
                    : constant.NullLiteral() != null ? "null"
                    : constant.IntegerLiteral() != null ? (text.matches(".*[lL]$") ? "long" : "int")
                    : text.matches(".*[fF]$") ? "float" : "double";
            sppServiceCall.getReadKeys().add(new SppServiceCall.ReadKey(
                    NameUtil.normalizeParticipantReference(key.qualifiedName().getText()), text, literalType));
        }
        sppServiceCall.setImplicitArguments(ctx.parameters() == null);
        sppServiceCall.setBreaksTransaction(ctx.transactionBreak() != null);
        getCurrentServiceBaseBody().addServiceCall(sppServiceCall);

        SppCompilationUnit callee = checker.getSppClass(CompilationUnitType.atomicservice, serviceClassName);
        if (callee instanceof SppService service) {
            sppServiceCall.setCallee(service);
            logSppError(ctx, generateField(serviceClassName, 0, serviceName));
        } else {
            checker.addAppParseError(sppServiceCall.sourceLocation + " [service-call] Compose shared atomic services instead. Scenario " + currentCLass.getFuncName() + " calls '" + serviceName
                    + "', which is not an atomic service, at line " + ctx.getStart().getLine() + ".");
        }
        if (ctx.scenarioBranch() != null) push(sppServiceCall);
    }

    @Override
    default void exitAtomiccall(SppParser.AtomiccallContext ctx) {
        if (getSppDomain().getCurrentClass() instanceof SppScenario && ctx.scenarioBranch() != null) pop();
    }
    @Override default void enterTransactionBreak(SppParser.TransactionBreakContext ctx) { }
    @Override default void exitTransactionBreak(SppParser.TransactionBreakContext ctx) { }
    @Override default void enterUniqueModifier(SppParser.UniqueModifierContext ctx) { }
    @Override default void exitUniqueModifier(SppParser.UniqueModifierContext ctx) { }
    @Override default void enterReadBinding(SppParser.ReadBindingContext ctx) { }
    @Override default void exitReadBinding(SppParser.ReadBindingContext ctx) { }
    @Override default void enterReadKey(SppParser.ReadKeyContext ctx) { }
    @Override default void exitReadKey(SppParser.ReadKeyContext ctx) { }
    @Override default void enterReadConstant(SppParser.ReadConstantContext ctx) { }
    @Override default void exitReadConstant(SppParser.ReadConstantContext ctx) { }
    @Override default void enterQuantumBounds(SppParser.QuantumBoundsContext ctx) { }
    @Override default void exitQuantumBounds(SppParser.QuantumBoundsContext ctx) { }
    @Override default void enterQuantumBound(SppParser.QuantumBoundContext ctx) { }
    @Override default void exitQuantumBound(SppParser.QuantumBoundContext ctx) { }

    @Override
    default void enterParameters(SppParser.ParametersContext ctx) {
    }

    @Override
    default void exitParameters(SppParser.ParametersContext ctx) {
    }

    @Override
    default void enterParameter(SppParser.ParameterContext ctx) {
        if (!(getSppDomain().getCurrentClass() instanceof SppScenario)) return;
        String name = NameUtil.normalizeParticipantReference(ctx.qualifiedName().getText());
        ServiceBaseBody body = getCurrentServiceBaseBody();
        SppServiceCall call = body instanceof SppServiceCall guarded
                ? guarded : (SppServiceCall) body.getLastServiceCall();
        SppLocalVar participant = body.getQualifieField(name);
        String scenario = ((SppScenario) getSppDomain().getCurrentClass()).getFuncName();
        if (participant == null) {
            getSppDomain().addAppParseError("Scenario " + scenario + ": participant argument '" + name
                    + "' must resolve to an explicitly declared participant or its declared attribute."
                    + " At line " + ctx.getStart().getLine() + ".");
            call.addParameter(new SppParameter(null, name));
            return;
        }
        String duplicate = call.addParameter(new SppParameter(participant.getType(), name)
                .setArrayDimension(participant.getArrayDimension()));
        if (duplicate != null)
            getSppDomain().addAppParseError("Scenario " + scenario + ": " + duplicate
                    + " at line " + ctx.getStart().getLine() + ".");
    }

    @Override
    default void exitParameter(SppParser.ParameterContext ctx) {
    }

    @Override
    default void exitScenarioDeclaration(SppParser.ScenarioDeclarationContext ctx) {
    }

    private ServiceBaseBody getCurrentServiceBaseBody() {
        SppService currentCLass = (SppService) getSppDomain().getCurrentClass();
        ServiceBaseBody serviceBaseBody = currentCLass.getServiceBody();
        if(stackSize() != 0 && peek() instanceof ServiceBaseBody){
            serviceBaseBody = (ServiceBaseBody) peek();
        }
        return serviceBaseBody;
    }

    private ExecuteBlock createExecuteBlock(ScenarioType scenarioType) {
        ServiceBaseBody serviceBaseBody = getCurrentServiceBaseBody();
        ExecuteBlock executeBlock = new ExecuteBlock();
        executeBlock.setScenarioType(scenarioType);

        serviceBaseBody.addServiceCall(executeBlock);
        return executeBlock;
    }
}
