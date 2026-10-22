package com.servpp.lang.general;

import com.servpp.lang.models.*;
import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.models.*;
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
        getSppDomain().setCurrentClass(currentClass);
    }

    @Override
    default void exitScenario(SppParser.ScenarioContext ctx) {
        getSppDomain().setCurrentClass(null);
    }
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
            call.setBreaksTransaction(ctx.getText().contains(",breakTransction"));
            getCurrentServiceBaseBody().addServiceCall(call);
            if (ctx.scenarioBranch() != null) push(call);
            return;
        }
        String serviceName = ctx.Identifier().getText();
        String serviceClassName = NameUtil.firstToLowerCase(serviceName,false);
        SppDomain checker = checkClass(ctx,serviceClassName);
        SppService currentCLass = (SppService) checker.getCurrentClass();
        SppServiceCall sppServiceCall = new SppServiceCall();
        sppServiceCall.setInvocationOrdinal(currentCLass.allocateInvocationOrdinal());
        if (ctx.quantumBounds() != null) for (var interval : ctx.quantumBounds().quantumBound())
            sppServiceCall.getQuantumBounds().add(new QuantumCallBound(NameUtil.normalizeParticipantReference(interval.qualifiedName().getText()),
                    com.servpp.lang.general.expr.BusinessCalculationCompiler.expression(interval.expression(0), getSppDomain()),
                    com.servpp.lang.general.expr.BusinessCalculationCompiler.expression(interval.expression(1), getSppDomain())));
        sppServiceCall.setImplicitArguments(ctx.parameters() == null);
        sppServiceCall.setBreaksTransaction(ctx.getText().contains(",breakTransction"));
        getCurrentServiceBaseBody().addServiceCall(sppServiceCall);

        SppClass callee = (SppClass) checker.getSppClass(CompilationUnitType.atomicservice,serviceClassName);
        if(callee instanceof SppService) {
            sppServiceCall.setCallee((SppService) callee);
            currentCLass.setCurrentCall(new CurrentCall((SppService) callee,0));
        }else if(!callee.isReal()){
            checker.addUnFoundClass(new ErrorContent(serviceClassName,
                    ctx.getStart().getLine(),ctx.getStart().getCharPositionInLine(),
                    "Callee:"+serviceClassName+" is not  exists!"));
//            logSppError(ctx,"Callee:"+serviceClassName+" is not  exists!");
        }
        else
            logSppError(ctx,"Callee:"+serviceClassName+" is not a service!");
        String err = generateField(serviceClassName,0,serviceName);
        logSppError(ctx,err);
        if (ctx.scenarioBranch() != null) push(sppServiceCall);
    }

    @Override
    default void exitAtomiccall(SppParser.AtomiccallContext ctx) {
        if (getSppDomain().getCurrentClass() instanceof SppScenario && ctx.scenarioBranch() != null) pop();
    }
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
        String name = NameUtil.normalizeParticipantReference(ctx.getChild(0).getText());
        String paramName = name;
        ServiceBaseBody serviceBaseBody = getCurrentServiceBaseBody();
        SppClass currentCLass = (SppClass) getSppDomain().getCurrentClass();
        //check call parameter type and position
        CurrentCall currentCall = currentCLass.getCurrentCall();
        SppLocalVar nameVar = serviceBaseBody.getQualifieField(paramName);
        SppServiceCall sppServiceCall = serviceBaseBody instanceof SppServiceCall guarded
                ? guarded : (SppServiceCall) serviceBaseBody.getLastServiceCall();
        if (nameVar == null) {
            String message = "Scenario " + currentCLass.getName() + ": participant argument '" + paramName
                    + "' must resolve to an explicitly declared participant or its declared attribute.";
            getSppDomain().addAppParseError(message + " At line " + ctx.getStart().getLine() + ".");
            logSppError(ctx, message);
            sppServiceCall.addParameter(new SppParameter(null, paramName));
            currentCLass.setCurrentCall(new CurrentCall(currentCall.callee(), currentCall.paramIndex() + 1));
            return;
        }
        //callee's n parameter
//        System.out.println("Callee "+currentCall.callee().getName() +" get parameter " + currentCall.paramIndex);
        if(currentCall.callee().getServiceBody().getSppLocalVarList().size() <= currentCall.paramIndex()) {
            if(!getSppDomain().deferCrudParameterValidation(currentCall.callee()))
                throw new RuntimeException("Scenario " + currentCLass.getName() + ": Parameter " +
                        currentCall.paramIndex() + " not exist in service " + currentCall.callee().getName());
        }
        //local var type
        sppServiceCall.addParameter( new SppParameter(nameVar.getType(),paramName)
                .setArrayDimension(nameVar.getArrayDimension()));
        if(!getSppDomain().deferCrudParameterValidation(currentCall.callee())) {
                SppLocalVar indexVar = currentCall.callee().getServiceBody().getLocalVar(currentCall.paramIndex());
                if (!getSppDomain().isSameTypeWithParameter(indexVar, nameVar))
                    logSppError(ctx, "Parameter " + name + " is  excepted " + indexVar.getType().getName() + (indexVar.getArrayString()));
        }
        if(getSppDomain().deferCrudParameterValidation(currentCall.callee())){
            Object[] checker = new Object[]{
                ctx, currentCLass, nameVar, currentCall.callee(), currentCall.paramIndex()
            };
            getSppDomain().getUnknownServiceCall().add(checker);
        }
        currentCLass.setCurrentCall(new CurrentCall(currentCall.callee,currentCall.paramIndex+1));
    }

    @Override
    default void exitParameter(SppParser.ParameterContext ctx) {
    }

    @Override
    default void exitScenarioDeclaration(SppParser.ScenarioDeclarationContext ctx) {
    }

    record CurrentCall(SppService callee, int paramIndex){

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
