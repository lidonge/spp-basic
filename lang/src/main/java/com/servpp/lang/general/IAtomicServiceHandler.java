package com.servpp.lang.general;


import com.servpp.lang.models.SppService;
import com.servpp.lang.antlr.SppParser;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public interface IAtomicServiceHandler extends IServiceDefinitionHandler {
    @Override
    default void enterAtomicservice(SppParser.AtomicserviceContext ctx) {
        SppService currentClass = new SppService("");
        currentClass.setType(CompilationUnitType.atomicservice);
        boolean quantumService = ctx.quantumService() != null;
        currentClass.setQuantumService(quantumService);
        currentClass.setDeclaredQuantumService(quantumService);
        getSppDomain().setCurrentClass(currentClass);

//        push(CompilationUnitType.atomicservice);
    }

    @Override
    default void exitAtomicservice(SppParser.AtomicserviceContext ctx) {
        if (ctx.calculationBody() != null) {
            SppService service = (SppService) getSppDomain().getCurrentClass();
            try {
                service.setBusinessImplementation(com.servpp.lang.general.expr.BusinessCalculationCompiler.body(ctx.calculationBody(), getSppDomain()));
            } catch (IllegalArgumentException e) {
                getSppDomain().addAppParseError("Business service " + service.getName() + " at line "
                        + ctx.getStart().getLine() + ": " + e.getMessage());
            }
        }
        getSppDomain().setCurrentClass(null);
    }

    @Override
    default void enterServicetype(SppParser.ServicetypeContext ctx){
        String stype = ctx.getChild(0).getText();
        SppService service = (SppService) getSppDomain().getCurrentClass();
        if ("insert".equals(stype) || "delete".equals(stype)) {
            service.setServiceType(ServiceType.update);
            service.setPersistenceOperation(stype);
        } else {
            service.setServiceType(ServiceType.valueOf(stype));
            if ("update".equals(stype)) service.setPersistenceOperation("update");
        }
    }
    @Override
    default void exitServicetype(SppParser.ServicetypeContext ctx){
//        push(ServiceType.valueOf(stype));
    }

}
