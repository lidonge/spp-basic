package com.servpp.service.runtime;

import com.servpp.service.Service;

/** Protocol boundary for authenticated RPC/HTTP/TCC facilities calling generated services. */
public final class RemoteServiceIngress {
    private RemoteServiceIngress() {}
    public static Object execute(Service service, Object request, String authenticatedCallerId, String requestSerialNumber) {
        if (!(service instanceof ScopeMetadataProvider metadata))
            throw new IllegalArgumentException("GENERATED_ENTRY_METADATA_REQUIRED");
        if (metadata.isAsyncScope()) throw new IllegalArgumentException("SYNCHRONOUS_REMOTE_REPLAY_REQUIRED");
        return InvocationContext.remote(InvocationContext.Origin.SERVICE, authenticatedCallerId, requestSerialNumber, () -> {
            TechnicalCallHeaders.apply(request, InvocationContext.current());
            return service.execute(request);
        });
    }
}
