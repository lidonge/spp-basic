package com.servpp.service.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

/** Compatibility handle; explicitly selecting memory coordination requires an installed provider. */
public class InMemoryBusinessIdempotencyStore implements CoordinatedRequestStore, RequestClaimStore {
    private final CoordinatedRequestStore delegate;
    public InMemoryBusinessIdempotencyStore() { this(Clock.systemUTC()); }
    public InMemoryBusinessIdempotencyStore(Clock clock) { delegate = RequestCoordinators.required().memoryStore(clock); }
    public Object execute(String key,Supplier<Object> action) { return delegate.execute(key,action); }
    public Object executeScenario(String key,Supplier<Object> action) { return delegate.executeScenario(key,action); }
    public Object executeRequest(String key,Duration retention,Supplier<Object> action) { return delegate.executeRequest(key,retention,action); }
    public boolean supportsClaims() { return delegate instanceof RequestClaimStore claims && claims.supportsClaims(); }
    private RequestClaimStore claims() {
        if (delegate instanceof RequestClaimStore claims && claims.supportsClaims()) return claims;
        throw new IllegalStateException("REQUEST_CLAIM_PROTOCOL_REQUIRED");
    }
    public Claim claim(String key,Duration retention) { return claims().claim(key,retention); }
    public void complete(Claim claim,Object response) { claims().complete(claim,response); }
    public void failed(Claim claim,boolean safelyRolledBack) { claims().failed(claim,safelyRolledBack); }
    public boolean contains(String key) { return delegate.contains(key); }
    public void clear() { delegate.clear(); }
    public int purgeExpired() { return delegate.purgeExpired(); }
}
