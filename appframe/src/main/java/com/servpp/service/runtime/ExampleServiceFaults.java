package com.servpp.service.runtime;

import com.servpp.service.AtomicService;
import com.servpp.service.DelegatingService;
import com.servpp.service.Service;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;

/** Technical service faults, enabled only by the declarative example runtime. */
public final class ExampleServiceFaults {
    private static final ThreadLocal<Plan> CURRENT = new ThreadLocal<>();
    private ExampleServiceFaults() {}
    public static Runnable bind(Runnable action) {
        Plan captured = CURRENT.get();
        return () -> {
            Plan previous = CURRENT.get();
            if (captured == null) CURRENT.remove(); else CURRENT.set(captured);
            try { action.run(); }
            finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
        };
    }

    public static Scope activate(String service, String timing) {
        return activate(service, timing, 1, null);
    }

    public static Scope activate(String service, String timing, int times, String afterService) {
        Plan plan = null;
        if (service != null) {
            if (service.isBlank() || !("before".equals(timing) || "after".equals(timing))
                    || times < 1 || (afterService != null && afterService.isBlank())) {
                throw new IllegalArgumentException("Invalid example service fault");
            }
            plan = new Plan(service, timing, times, afterService);
        } else if (timing != null || times != 1 || afterService != null) {
            throw new IllegalArgumentException("Example fault timing requires a service");
        }
        Plan previous = CURRENT.get();
        if (plan == null) CURRENT.remove(); else CURRENT.set(plan);
        return new Scope(plan, previous);
    }

    public static Service decorate(Service delegate) {
        if (!(delegate instanceof AtomicService)) return delegate;
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> type = delegate.getClass(); type != null; type = type.getSuperclass()) collect(type, interfaces);
        interfaces.add(DelegatingService.class);
        String name = delegate.getName();
        return (Service) Proxy.newProxyInstance(delegate.getClass().getClassLoader(), interfaces.toArray(Class[]::new),
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> delegate.toString();
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> throw new IllegalStateException("Unknown Object method");
                        };
                    }
                    if (method.getName().equals("getDelegateService") && method.getParameterCount() == 0) return delegate;
                    boolean invocation = method.getName().equals(name.substring(name.lastIndexOf('.') + 1)) || method.getName().equals("execute");
                    if (invocation) {
                        ExampleServiceBarrier.before(name);
                        trigger(name, "before");
                    }
                    Object result;
                    try { result = method.invoke(delegate, arguments); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                    if (invocation) {
                        ExampleJdbcFaults.arm(name);
                        trigger(name, "after");
                        arm(name);
                    }
                    return result;
                });
    }

    private static void collect(Class<?> type, Set<Class<?>> interfaces) {
        for (Class<?> item : type.getInterfaces()) {
            if (interfaces.add(item)) collect(item, interfaces);
        }
    }
    private static void trigger(String service, String timing) {
        Plan plan = CURRENT.get();
        if (plan == null) return;
        synchronized (plan) {
        if (plan.armed && plan.fired < plan.times
                && plan.service.equals(service) && plan.timing.equals(timing)) {
            plan.fired++;
            throw new IllegalStateException("EXAMPLE_SERVICE_FAULT");
        }
        }
    }
    private static void arm(String service) {
        Plan plan = CURRENT.get();
        if (plan != null) synchronized (plan) { if (service.equals(plan.afterService)) plan.armed = true; }
    }
    private static final class Plan {
        private final String service, timing, afterService;
        private final int times;
        private int fired;
        private boolean armed;
        private Plan(String service, String timing, int times, String afterService) {
            this.service = service; this.timing = timing; this.times = times; this.afterService = afterService;
            this.armed = afterService == null;
        }
    }
    public static final class Scope implements AutoCloseable {
        private final Plan plan, previous;
        private Scope(Plan plan, Plan previous) { this.plan = plan; this.previous = previous; }
        public void verifyTriggered() {
            if (plan != null && plan.fired != plan.times) {
                throw new IllegalStateException("EXAMPLE_FAULT_NOT_TRIGGERED: " + plan.service + " " + plan.fired + "/" + plan.times);
            }
        }
        @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
