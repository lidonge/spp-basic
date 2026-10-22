package com.servpp.service.transaction;

/** Optional adapter protocol for descriptive failure facts, not an SPP exception API. */
public interface FailureReasonProvider {
    String getFailureCode();
    String getFailureReason();
    default String getFailureAttribute() { return null; }
    default String getFailureActualValue() { return null; }
    default String getFailureMinimum() { return null; }
    default String getFailureMaximum() { return null; }
}
