package com.servpp.service;

import com.servpp.service.transaction.TransactionParticipant;
import java.util.Objects;
import java.util.function.Function;

/** Keeps source-generated scenario classes intact while binding technical participant hooks. */
public abstract class SourceScenarioService implements Scenario {
    private com.servpp.service.technical.ServiceInterceptors technicalInterceptors = com.servpp.service.technical.ServiceInterceptors.none();
    public final void configureTechnicalInterceptors(com.servpp.service.technical.ServiceInterceptors interceptors) {
        technicalInterceptors = Objects.requireNonNull(interceptors);
    }
    protected final Object executeWithTechnicalInterceptors(java.util.function.Supplier<Object> action) {
        return technicalInterceptors.invoke(getName(), com.servpp.service.runtime.GeneratedServiceKind.SCENARIO, action);
    }
    protected final Object executeWithTechnicalInterceptors(Object[] arguments, java.util.function.Supplier<Object> action) {
        return technicalInterceptors.invoke(getName(), com.servpp.service.runtime.GeneratedServiceKind.SCENARIO, arguments, action);
    }
    private Function<TransactionParticipant, TransactionParticipant> participantDecorator = Function.identity();

    public final void decorateTransactionParticipant(Function<TransactionParticipant, TransactionParticipant> decorator) {
        participantDecorator = participantDecorator.andThen(Objects.requireNonNull(decorator, "decorator"));
    }

    @Override
    public TransactionParticipant getTransactionParticipant() {
        return participantDecorator.apply(TransactionParticipant.noop(getTransactionResourceId()));
    }
}
