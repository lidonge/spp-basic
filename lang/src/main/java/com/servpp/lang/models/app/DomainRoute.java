package com.servpp.lang.models.app;

/** Physical endpoint for the domain owning a generated service. */
public record DomainRoute(String domain, String endpoint, int timeoutMillis) { }
