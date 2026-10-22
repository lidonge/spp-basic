package com.servpp.service.transaction;

public class QuantumServiceOutcomeException extends IllegalStateException {
    private final LogicalQuantumServiceResponse.Outcome outcome;

    public QuantumServiceOutcomeException(LogicalQuantumServiceResponse.Outcome outcome, String message) {
        super(message);
        this.outcome = outcome;
    }

    public QuantumServiceOutcomeException(LogicalQuantumServiceResponse.Outcome outcome,
                                          String message, Throwable cause) {
        super(message, cause);
        this.outcome = outcome;
    }

    public LogicalQuantumServiceResponse.Outcome getOutcome() {
        return outcome;
    }
}
