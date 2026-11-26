package com.servpp.service.transaction;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;

/**
 * Strict mirror of QService's logical envelope (contract {@code spp-qservice-logical/1}).
 * {@code boundaryResponse} is absent only for the bare NOT_RECEIVED answer of a query, which then
 * carries neither {@code receipt} nor {@code failureReason}. {@code receipt} is a string (the
 * decimal conservation ID) and must equal {@code boundaryResponse.conservationID}; it is derived
 * when omitted. {@code failureReason} is present exactly for BUSINESS_FAILED.
 */
public record LogicalQuantumServiceResponse(
        String namespace,
        String requestKey,
        String originalRequestKey,
        BoundaryResponse boundaryResponse,
        @JsonDeserialize(using = StrictString.class) String receipt,
        FailureReason failureReason) {

    public enum Outcome {
        SUCCESS,
        BUSINESS_FAILED,
        NOT_RECEIVED,
        UNKNOWN
    }

    /** A string field that rejects JSON numbers instead of coercing them. */
    static final class StrictString extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getText();
        }
    }

    /** The closed set of failure codes of QService v1; branch on the code, never on the message. */
    public enum FailureCode {
        LOWER_BOUND,
        UPPER_BOUND,
        NON_FINITE,
        PRECISION,
        ORIGINAL_NOT_APPLIED,
        UNDETERMINED
    }

    public record BoundaryResponse(
            long conservationID,
            Long originalConservationID,
            LogicalQuantumServiceCommand.Operation operation,
            Outcome status) {
    }

    /** {@code quantum} is the logical key, or null when the service names none. */
    public record FailureReason(
            FailureCode code,
            LogicalQuantumServiceCommand.LogicalKey quantum,
            String message) {
        public FailureReason {
            if (code == null) {
                throw new IllegalArgumentException("failureReason.code must not be null");
            }
        }
    }

    public LogicalQuantumServiceResponse {
        if (boundaryResponse == null) {
            if (receipt != null || failureReason != null) {
                throw new IllegalArgumentException("a response without boundaryResponse carries no receipt or failureReason");
            }
        } else {
            String expected = Long.toString(boundaryResponse.conservationID());
            if (receipt == null) {
                receipt = expected;
            } else if (!receipt.equals(expected)) {
                throw new IllegalArgumentException("receipt does not match boundaryResponse.conservationID");
            }
            if ((boundaryResponse.status() == Outcome.BUSINESS_FAILED) != (failureReason != null)) {
                throw new IllegalArgumentException("failureReason is required exactly for BUSINESS_FAILED");
            }
        }
    }

    /** The outcome, NOT_RECEIVED for the bare query answer. */
    public Outcome outcome() {
        return boundaryResponse == null ? Outcome.NOT_RECEIVED : boundaryResponse.status();
    }

    public void requireIdentity(LogicalQuantumServiceCommand command) {
        if (boundaryResponse == null
                || !command.namespace().equals(namespace)
                || !command.requestKey().equals(requestKey)
                || !equal(command.originalRequestKey(), originalRequestKey)
                || command.operation() != boundaryResponse.operation()) {
            throw new IllegalStateException("QService logical response identity mismatch");
        }
    }

    /** Identity check for a query, whose operation is unknown when the answer is bare. */
    public void requireIdentity(String expectedNamespace, String expectedRequestKey) {
        if (!expectedNamespace.equals(namespace) || !expectedRequestKey.equals(requestKey)) {
            throw new IllegalStateException("QService logical response identity mismatch");
        }
    }

    private static boolean equal(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
