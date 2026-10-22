package com.servpp.service.idempotency;

import java.util.Optional;
import java.util.ServiceLoader;

final class RequestCoordinators {
    private static final Optional<RequestCoordinationProvider> PROVIDER = discover();
    private RequestCoordinators() { }
    static Optional<RequestCoordinationProvider> provider() { return PROVIDER; }
    static RequestCoordinationProvider required() {
        return PROVIDER.orElseThrow(() -> new IllegalStateException("REQUEST_COORDINATION_PROVIDER_REQUIRED"));
    }
    private static Optional<RequestCoordinationProvider> discover() {
        var providers = ServiceLoader.load(RequestCoordinationProvider.class).iterator();
        if (!providers.hasNext()) return Optional.empty();
        var result = providers.next();
        if (providers.hasNext()) throw new IllegalStateException("REQUEST_COORDINATION_PROVIDER_AMBIGUOUS");
        return Optional.of(result);
    }
    static CoordinatedRequestStore defaultStore() {
        return PROVIDER.map(provider -> provider.memoryStore(java.time.Clock.systemUTC())).orElseGet(() -> new CoordinatedRequestStore() {
            public Object execute(String key,java.util.function.Supplier<Object> action) { throw missing(); }
            public Object executeRequest(String key,java.time.Duration retention,java.util.function.Supplier<Object> action) { throw missing(); }
            public boolean contains(String key) { return false; }
            public int purgeExpired() { return 0; }
            public void clear() { }
            private IllegalStateException missing() { return new IllegalStateException("REQUEST_COORDINATION_PROVIDER_REQUIRED"); }
        });
    }
}
