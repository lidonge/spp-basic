package com.servpp.service.technical;

import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.GeneratedServiceKind;
import com.servpp.service.runtime.InvocationContext;

/** Technical metadata only: no mutable business participants are exposed. */
public record TechnicalInvocation(String serviceName, Phase phase, GeneratedServiceKind kind,
                                  InvocationContext.Header header, long elapsedNanos,
                                  BusinessChecks.Result businessResult, Throwable failure, TechnicalCall call) {
    public TechnicalInvocation(String serviceName, Phase phase, GeneratedServiceKind kind,
                               InvocationContext.Header header, long elapsedNanos,
                               BusinessChecks.Result businessResult, Throwable failure) {
        this(serviceName,phase,kind,header,elapsedNanos,businessResult,failure,new TechnicalCall(null,null));
    }
    public enum Phase { BEFORE, AFTER, FAILURE }
}
