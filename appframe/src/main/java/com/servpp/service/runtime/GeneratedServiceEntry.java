package com.servpp.service.runtime;

import com.servpp.service.DelegatingService;
import com.servpp.service.Service;
import com.servpp.service.Scenario;
import java.util.function.Supplier;

/** One front-end dispatch authorizes one generated scenario on this call stack. */
public final class GeneratedServiceEntry {
    private static final ThreadLocal<Service> ENTRY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private GeneratedServiceEntry() { }

    static Service originalService(Service service) {
        Service current = service;
        while (current instanceof DelegatingService delegatingService) {
            Service delegate = delegatingService.getDelegateService();
            if (delegate == null || delegate == current) {
                return current;
            }
            current = delegate;
        }
        return current;
    }

    static <T> T dispatch(Service service, Supplier<T> action) {
        if (ACTIVE.get() != null || ENTRY.get() != null)
            throw new IllegalStateException("P0-b: a service cannot start another scenario. Invoke it from the front end.");
        Service entry = originalService(service);
        if (!(entry instanceof Scenario) && !(entry instanceof GeneratedServiceKindProvider kind
                && kind.getGeneratedServiceKind() == GeneratedServiceKind.SCENARIO))
            throw new IllegalStateException("P0-b: front-end entries must be scenarios. Call the atomic service from its scenario.");
        ENTRY.set(entry);
        try { return inService(action); }
        finally { ENTRY.remove(); }
    }

    public static Object executeHttp(String name, Object... arguments) {
        Service service = com.servpp.service.server.DefaultServiceContainer.getInstance().getService(name);
        return dispatch(service, () -> service.execute(arguments));
    }

    public static void enterScenario(Service scenario) {
        if (ENTRY.get() != scenario)
            throw new IllegalStateException("P0-b: invoke the scenario through the front-end entry.");
        ENTRY.remove();
    }

    public static void requireAtomicCaller() {
        if (ACTIVE.get() == null || ENTRY.get() != null)
            throw new IllegalStateException("P0-b: call the atomic service from its scenario.");
    }

    static <T> T inService(Supplier<T> action) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try { return action.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    static Runnable bind(Runnable task) {
        return ACTIVE.get() == null ? task : () -> inService(() -> { task.run(); return null; });
    }
}
