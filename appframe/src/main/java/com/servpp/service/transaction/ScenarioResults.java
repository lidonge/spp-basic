package com.servpp.service.transaction;

import java.lang.reflect.InvocationTargetException;

/** Accesses generated system result models without coupling the runtime to business domains. */
public final class ScenarioResults {
    private ScenarioResults() { }
    public static void initialize(Object result) {
        write(result, "Status", "PENDING"); write(result, "Handled", false); write(result, "Failure", null);
        com.servpp.service.runtime.ParticipantIdentity.confirm(result, true);
    }
    public static void complete(Object result) {
        if ("PENDING".equals(read(result, "Status")))
            write(result, "Status", com.servpp.service.runtime.BusinessChecks.scenarioResult().passed() ? "SUCCEEDED" : "REJECTED");
    }
    public static void attach(Object contract, Object result) { write(contract, "ResultInfo", result); }
    static Object newFailure(Object result) {
        try {
            var type = result.getClass().getMethod("getFailure").getReturnType();
            Object failure = type.getConstructor().newInstance();
            write(result, "Failure", failure);
            com.servpp.service.runtime.ParticipantIdentity.confirm(failure, true);
            return failure;
        } catch (ReflectiveOperationException failure) { throw bindingFailure(failure); }
    }
    static Object read(Object target, String name) {
        try { return target.getClass().getMethod("get" + name).invoke(target); }
        catch (ReflectiveOperationException failure) { throw bindingFailure(failure); }
    }
    static void write(Object target, String name, Object value) {
        try {
            var getter = target.getClass().getMethod("get" + name);
            target.getClass().getMethod("set" + name, getter.getReturnType()).invoke(target, value);
        } catch (ReflectiveOperationException failure) { throw bindingFailure(failure); }
    }
    private static IllegalStateException bindingFailure(ReflectiveOperationException failure) {
        return new IllegalStateException("SYSTEM_RESULT_BINDING_FAILED",
                failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure);
    }
}
