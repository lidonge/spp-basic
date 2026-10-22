package com.servpp.service.technical;

public interface TechnicalInterceptionProvider {
    default ServiceInterceptors getTechnicalInterceptors() { return ServiceInterceptors.none(); }
}
