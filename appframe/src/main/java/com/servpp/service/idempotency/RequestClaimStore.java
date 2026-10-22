package com.servpp.service.idempotency;

import java.time.Duration;

/** Atomic request reservation for before/after/failure services, independent of the business transaction. */
public interface RequestClaimStore extends RequestIdempotencyStore {
    record Claim(String key,String token,boolean replay,Object response,Duration retention) { }
    default boolean supportsClaims() { return true; }
    Claim claim(String key,Duration retention);
    void complete(Claim claim,Object response);
    /** Only a measured complete rollback permits releasing a claim; otherwise retain it for recovery. */
    void failed(Claim claim,boolean safelyRolledBack);
}
