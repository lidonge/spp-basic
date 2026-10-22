package com.servpp.service.runtime;

import java.util.Map;

public record RemoteServiceAssignment(String serviceName,
                                      boolean adapterAvailable,
                                      boolean adapterExecutable,
                                      String adapterImplementationType,
                                      String adapterRole,
                                      boolean referenceImplementation,
                                      boolean externalImplementation,
                                      Map<String, String> adapterConfiguration) {
}
