package com.servpp.service.runtime;

import com.servpp.service.Service;
import java.util.Map;

/** Protocol boundary for authenticated RPC/HTTP/TCC facilities calling generated services. */
public final class RemoteServiceIngress {
    private RemoteServiceIngress() {}
    public static Object execute(Service service, Object request, String authenticatedCallerId, String requestSerialNumber) {
        return execute(service, request, authenticatedCallerId, requestSerialNumber, Map.of());
    }
    /** {@code identifiers} are the trusted technical identifiers (see {@link InvocationContext#IDENTIFIER_NAMES}). */
    public static Object execute(Service service, Object request, String authenticatedCallerId, String requestSerialNumber,
                                 Map<String, String> identifiers) {
        if (!(service instanceof ScopeMetadataProvider metadata))
            throw new IllegalArgumentException("GENERATED_ENTRY_METADATA_REQUIRED");
        if (metadata.isAsyncScope()) throw new IllegalArgumentException("SYNCHRONOUS_REMOTE_REPLAY_REQUIRED");
        return InvocationContext.remote(InvocationContext.Origin.SERVICE, authenticatedCallerId, requestSerialNumber, identifiers, () -> {
            TechnicalCallHeaders.apply(request, InvocationContext.current());
            return GeneratedServiceEntry.dispatch(service, () -> service.execute(request));
        });
    }
}
