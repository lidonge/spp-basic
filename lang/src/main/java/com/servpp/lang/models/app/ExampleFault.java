package com.servpp.lang.models.app;

/** Technical faults declared for a generated example entrypoint. */
public record ExampleFault(String serviceName, String timing, int times, String afterServiceName) {
    public ExampleFault(String serviceName, String timing) { this(serviceName, timing, 1, null); }
}
