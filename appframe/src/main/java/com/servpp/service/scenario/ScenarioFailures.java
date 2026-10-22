package com.servpp.service.scenario;

import java.lang.reflect.InvocationTargetException;

/** Supplies failure facts; recovery and rethrow decisions remain in the source scene. */
public final class ScenarioFailures {
    private ScenarioFailures() { }
    public static <T> T describe(Throwable failure,T facts) {
        if (facts == null) throw new IllegalStateException("SCENARIO_FAILURE_PARTICIPANT_REQUIRED");
        String message=failure.getMessage();
        if(message==null || message.isBlank()) { message=failure.getClass().getSimpleName(); }
        write(facts,"Code",message,true);
        write(facts,"Message",message,false);
        write(facts,"Kind",failure.getClass().getName(),false);
        write(facts,"TransactionOutcome",com.servpp.service.transaction.TransactionOutcomes.outcome(failure).name(),false);
        writeSuppressedCodes(facts, failure);
        return facts;
    }
    private static void writeSuppressedCodes(Object facts, Throwable failure) {
        java.util.List<String> codes = java.util.Arrays.stream(failure.getSuppressed()).map(error -> {
            String message = error.getMessage();
            return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
        }).toList();
        try {
            try { facts.getClass().getMethod("setSuppressedCodes", java.util.List.class).invoke(facts, codes); }
            catch (NoSuchMethodException missingList) {
                facts.getClass().getMethod("setSuppressedCodes", String[].class).invoke(facts, (Object) codes.toArray(String[]::new));
            }
        } catch (NoSuchMethodException absentOptionalField) {
            // Existing failure roles need only code; this metadata is opt-in.
        } catch (IllegalAccessException | InvocationTargetException bindingFailure) {
            throw new IllegalStateException("SCENARIO_FAILURE_FACTS_BINDING_FAILED", bindingFailure);
        }
    }
    private static void write(Object facts,String name,String value,boolean required) {
        try { facts.getClass().getMethod("set"+name,String.class).invoke(facts,value); }
        catch(NoSuchMethodException missing) {
            if(required) { throw new IllegalStateException("SCENARIO_FAILURE_FACTS_REQUIRE_CODE",missing); }
        } catch(IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("SCENARIO_FAILURE_FACTS_BINDING_FAILED",failure);
        }
    }
}
