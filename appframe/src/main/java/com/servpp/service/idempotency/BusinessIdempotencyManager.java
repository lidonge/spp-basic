package com.servpp.service.idempotency;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.servpp.service.transaction.BusinessTraceContext;

public final class BusinessIdempotencyManager {
    private static BusinessIdempotencyStore store = RequestCoordinators.defaultStore();
    private static Set<String> frameworkBypassServices = Set.of();

    private BusinessIdempotencyManager() {
    }

    public static boolean isAvailable() { return RequestCoordinators.provider().isPresent(); }

    public static boolean wasReplay() { return RequestCoordinators.provider().map(RequestCoordinationProvider::wasReplay).orElse(false); }

    public static void resetReplay() { RequestCoordinators.provider().ifPresent(RequestCoordinationProvider::resetReplay); }

    public static Object executeScenario(String serviceName, Object request,
            com.servpp.service.runtime.ScopeMetadataProvider metadata, boolean businessManaged, Supplier<Object> action) {
        return invokeEntry(serviceName,request,metadata,true,action);
    }
    private static Object invokeEntry(String serviceName,Object request,
            com.servpp.service.runtime.ScopeMetadataProvider metadata,boolean scenario,Supplier<Object> action) {
        var provider = RequestCoordinators.provider();
        if (provider.isEmpty()) {
            if (com.servpp.service.runtime.InvocationContext.current().mode() == com.servpp.service.runtime.InvocationContext.Mode.REMOTE)
                com.servpp.service.runtime.BusinessChecks.restoreScenarioResult(null);
            return com.servpp.service.runtime.ServiceInvocationScope.execute(request,action);
        }
        return provider.get().execute(serviceName,request,metadata,scenario,BusinessIdempotencyManager::requestStore,action);
    }

    public static Object executeScenario(String serviceName, Object request, boolean businessManaged, Supplier<Object> action) {
        return execute(serviceName, request, action);
    }

    public static Object execute(String serviceName, Object request,
            com.servpp.service.runtime.ScopeMetadataProvider metadata, Supplier<Object> action) {
        return invokeEntry(serviceName, request, metadata, false, action);
    }

    public static Object execute(String serviceName, Object request, Supplier<Object> action) {
        RequestCoordinators.provider().ifPresent(RequestCoordinationProvider::resetReplay);
        // Calls without generated entry metadata are local/legacy and do not register requests.
        return com.servpp.service.runtime.InvocationContext.local(() -> executeWithBusinessTrace(request, action));
    }

    public static Object execute(String serviceName, Object request, Supplier<Object> action, Supplier<Object> duplicateAction) {
        return execute(serviceName, request, action);
    }

    private static RequestIdempotencyStore overrideRequestStore;
    public static void setRequestStore(RequestIdempotencyStore nextStore) { overrideRequestStore = nextStore; }
    public static RequestIdempotencyStore requestStore() {
        if (overrideRequestStore != null) return overrideRequestStore;
        if (store instanceof RequestIdempotencyStore protocol) return protocol;
        throw new IllegalStateException("REQUEST_IDEMPOTENCY_PROTOCOL_REQUIRED");
    }

    public static void setStore(BusinessIdempotencyStore nextStore) {
        overrideRequestStore = null;
        store = nextStore == null ? RequestCoordinators.defaultStore() : nextStore;
        clearFrameworkBypassServices();
    }

    public static BusinessIdempotencyStore getStore() {
        return store;
    }

    public static void clear() {
        store.clear();
        clearFrameworkBypassServices();
    }

    public static void setFrameworkBypassServices(Collection<String> serviceNames) {
        LinkedHashSet<String> nextServices = new LinkedHashSet<>();
        if (serviceNames != null) {
            for (String serviceName : serviceNames) {
                String normalized = stringValue(serviceName);
                if (!normalized.isBlank()) {
                    nextServices.add(normalized);
                }
            }
        }
        frameworkBypassServices = Set.copyOf(nextServices);
    }

    public static Set<String> frameworkBypassServices() {
        return frameworkBypassServices;
    }

    public static boolean isFrameworkBypassed(String serviceName) {
        return frameworkBypassServices.contains(stringValue(serviceName));
    }

    public static void clearFrameworkBypassServices() {
        frameworkBypassServices = Set.of();
    }

    /** Legacy trace-key diagnostic; request replay uses the namespaced transport identity instead. */
    @Deprecated
    public static Optional<String> idempotencyKey(String serviceName, Object request) {
        Optional<String> traceId = businessTraceId(request);
        if (traceId.isEmpty()) {
            return Optional.empty();
        }
        Object header = readNoArgMethod(request, "getAppHeader");
        String globalSerialNumber = stringValue(readNoArgMethod(header, "getGlobalSerialNumber"));
        if (!globalSerialNumber.isBlank()) {
            return Optional.of(serviceName + ":globalSerialNumber:" + globalSerialNumber);
        }
        return Optional.of(serviceName + ":messageId:" + traceId.get());
    }

    public static Optional<String> businessTraceId(Object request) {
        return com.servpp.service.runtime.ServiceInvocationScope.businessTraceId(request);
    }

    private static Object readNoArgMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot read idempotency method " + methodName
                    + " on " + target.getClass().getName(), e);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static Object executeWithBusinessTrace(Object request, Supplier<Object> action) {
        String traceId = businessTraceId(request).orElse(BusinessTraceContext.currentGlobalSerialNumber().orElse(""));
        return BusinessTraceContext.execute(traceId, action);
    }
}
