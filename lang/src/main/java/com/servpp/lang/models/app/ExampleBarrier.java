package com.servpp.lang.models.app;

/** One bounded rendezvous for an APP example's concurrent calls. */
public record ExampleBarrier(String serviceName, int parties) {}
