package com.servpp.service.idempotency;

/** Storage contract shared by legacy diagnostics and the independent request protocol. */
public interface CoordinatedRequestStore extends BusinessIdempotencyStore, RequestIdempotencyStore { }
