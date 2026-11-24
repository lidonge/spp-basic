package com.servpp.generator.openapi;

import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.Map;

final class ServiceRegistryFixture {
    private ServiceRegistryFixture() { }

    static void restore(String name, Service previous) throws ReflectiveOperationException {
        var container = DefaultServiceContainer.getInstance();
        if (previous != null) {
            container.registerService(name, previous);
            return;
        }
        var field = DefaultServiceContainer.class.getDeclaredField("serviceMap");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(container)).remove(name);
    }
}
