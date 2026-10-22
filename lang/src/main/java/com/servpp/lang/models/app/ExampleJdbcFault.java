package com.servpp.lang.models.app;

/** One JDBC lifecycle fault armed by a declared update call. */
public record ExampleJdbcFault(String serviceName, String point) {}
