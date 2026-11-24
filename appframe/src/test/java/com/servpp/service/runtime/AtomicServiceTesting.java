package com.servpp.service.runtime;

import com.servpp.service.Service;
import com.servpp.service.repository.CurrentObjectContext;
import com.servpp.service.server.DefaultServiceContainer;

/** Local generated atomic entry for tests using the installed application deployment. */
public final class AtomicServiceTesting {
    private AtomicServiceTesting() { }

    public static Object invokeAtomic(LocalGeneratedServiceRuntime runtime, String name, Object request) {
        runtime.install();
        Service service = DefaultServiceContainer.getInstance().getService(name);
        Service original = GeneratedServiceEntry.originalService(service);
        if (!(original instanceof GeneratedServiceKindProvider provider)
                || original instanceof RemoteGeneratedService
                || !isAtomic(provider.getGeneratedServiceKind())) {
            throw new IllegalArgumentException("Expected a registered local generated atomic service: " + name);
        }
        GeneratedRoleIdentityContextBinder.attach(request);
        return GeneratedServiceEntry.inService(() -> CurrentObjectContext.withObjects(
                new Object[] { request }, () -> service.execute(request)));
    }

    private static boolean isAtomic(GeneratedServiceKind kind) {
        return switch (kind) {
            case ATOMIC_QUERY, ATOMIC_UPDATE, ATOMIC_CHECK, ATOMIC_CALCULATE -> true;
            default -> false;
        };
    }
}
