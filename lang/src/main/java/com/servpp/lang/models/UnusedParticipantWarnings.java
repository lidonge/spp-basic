package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.models.app.expr.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Syntactic usage of declared participants, independent of physical lowering. */
final class UnusedParticipantWarnings {
    private UnusedParticipantWarnings() { }

    static List<String> collect(SppService service) {
        // A prototype is not an implementation; APP/PHY may consume its arguments.
        if (!(service instanceof SppScenario) && !service.hasBusinessImplementation()) return List.of();
        Set<String> used = new HashSet<>();
        if (service instanceof SppScenario) {
            scenario(service.getServiceBody().getSppServiceCallList(), used);
        } else {
            calculations(service.getDeclaredJavaStatements(), used);
            if (service.isDeclaredQuantumService() && service.getExpressions() != null)
                service.getExpressions().forEach(expression -> expression(expression, used));
        }
        List<String> warnings = new ArrayList<>();
        for (SppLocalVar participant : service.getServiceBody().getSppLocalVarList()) {
            var type = participant.getType();
            if (type == null || type.getType() == null) continue;
            if (!Set.of(IConstance.CompilationUnitType.role, IConstance.CompilationUnitType.contract,
                    IConstance.CompilationUnitType.entity, IConstance.CompilationUnitType.reference)
                    .contains(type.getType())) continue;
            if (!used.contains(participant.getName())) {
                warnings.add("[SPP_UNUSED_PARTICIPANT] " + service.getFuncName()
                        + ": participant '" + participant.getName() + "' (" + type.getName()
                        + participant.getArrayString() + ") is declared but not used in the SPP body.");
            }
        }
        return warnings;
    }

    private static void path(String path, Set<String> used) {
        if (path != null && !path.isBlank()) used.add(path.split("\\.", 2)[0]);
    }

    private static void scenario(List<IServiceBodyStatement> statements, Set<String> used) {
        if (statements == null) return;
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall call) {
                call.getSppParameterList().forEach(parameter -> path(parameter.getName(), used));
                path(call.getResultTarget(), used);
                path(call.getIdentityTarget(),used); scenario(call.getGuardStatements(),used);
            } else if (statement instanceof ScenarioThrow rethrow) {
                path(rethrow.getTarget(), used);
            } else if (statement instanceof TryBlock block) {
                // The catch target receives the failure, even if the catch body is empty.
                path(block.getFailureTarget(), used);
                scenario(block.getTryStatements(), used);
                scenario(block.getCatchStatements(), used);
            } else if (statement instanceof ConditionalBlock block) {
                expression(block.getCondition(), used);
                scenario(block.getThenStatements(), used);
                scenario(block.getElseStatements(), used);
            } else if (statement instanceof ExecuteBlock block) {
                scenario(block.getExecuteCallList(), used);
            } else if (statement instanceof TransactionBlock block) {
                scenario(block.getTransactionCallList(), used);
            }
        }
    }

    private static void calculations(List<IJavaStatement> statements, Set<String> used) {
        if (statements == null) return;
        for (IJavaStatement statement : statements) {
            if (statement instanceof CalculationServiceCall call) {
                call.arguments.forEach(arg -> path(arg,used));
            } else if (statement instanceof IExpression expression) {
                expression(expression, used);
            } else if (statement instanceof RequireStatement require) {
                expression(require.getCondition(), used);
            } else if (statement instanceof CalculationRule rule) {
                expression(rule.getCondition(), used);
                calculations(rule.getWhenTrue(), used);
                calculations(rule.getWhenFalse(), used);
            } else if (statement instanceof CalculationControl control) {
                expression(control.condition, used);
                calculations(control.children(), used);
            }
        }
    }

    private static void expression(IExpression expression, Set<String> used) {
        if (expression instanceof Reference reference) {
            // Resolved lexical locals must not count as formal participants.
            if (!(reference instanceof CalculationReference calculation && calculation.local)) {
                path(reference.getValue(), used);
            }
        } else if (expression instanceof IOperationExpression operation) {
            expression(operation.getLeft(), used);
            expression(operation.getRight(), used);
        } else if (expression instanceof CalculationFunction function) {
            function.getArguments().forEach(argument -> expression(argument, used));
        } else if (expression instanceof ConditionalExpression conditional) {
            expression(conditional.getCondition(), used);
            expression(conditional.getWhenTrue(), used);
            expression(conditional.getWhenFalse(), used);
        } else if (expression instanceof ParticipantConstruction construction) {
            construction.getElements().forEach(element -> expression(element, used));
        }
    }
}
