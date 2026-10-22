package com.servpp.lang.models;

import com.servpp.lang.general.IConstance.CompilationUnitType;
import java.util.List;

/** Compiler-owned participants; never source parameters, CRUD targets or business storage. */
public final class SystemResultModels {
    private SystemResultModels() { }
    public static void install(SppDomain domain) {
        for (String name : List.of("FailureInfo", "ResultInfo")) {
            if (domain.getSppClass(name) instanceof SppClass existing && existing.isReal()) {
                if (!existing.isSystemBuiltin()) domain.addAppParseError(name + " is a reserved system participant; do not declare it in SPP.");
                continue;
            }
            var model = new SppClass(name, CompilationUnitType.role);
            model.setSystemBuiltin(true);
            if (name.equals("FailureInfo")) {
                for (String field : List.of("code", "reason", "serviceName", "resourceName", "phase", "transactionOutcome", "recoveryStatus",
                        "attribute", "actualValue", "minimum", "maximum"))
                    model.addField(new SppField(domain.getSppClass("String"), field));
                for (String field : List.of("causes", "suppressedCodes")) {
                    var array = new SppField(domain.getSppClass("String"), field); array.setArrayDimension(1); model.addField(array);
                }
            } else {
                model.addField(new SppField(domain.getSppClass("String"), "status"));
                model.addField(new SppField(domain.getSppClass("boolean"), "handled"));
                model.addField(new SppField(domain.getSppClass("FailureInfo"), "failure"));
            }
            domain.addClass(model);
        }
    }
    public static void enable(SppScenario scene, SppDomain domain) {
        var current = domain.getCurrentClass();
        install(domain); domain.setCurrentClass(current);
        scene.getServiceBody().getSppLocalVarHashMap().put("ResultInfo",
                new SppLocalVar(domain.getSppClass("ResultInfo"), "ResultInfo"));
        scene.setUsesSystemResult(true);
    }
    public static void attach(SppClass contract, SppDomain domain) {
        var field = contract.getField("resultInfo");
        if (field != null) {
            if (!field.isSystemResult()) domain.addAppParseError("Contract " + contract.getName() + " resultInfo is reserved for the system result.");
            return;
        }
        field = new SppField(domain.getSppClass("ResultInfo"), "resultInfo");
        field.setSystemResult(true); contract.addField(field);
    }
}
