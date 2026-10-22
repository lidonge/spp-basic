package com.servpp.service.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.servpp.service.DelegatingService;
import com.servpp.service.Service;

public final class RemoteServiceProxy {
    private static final RemoteCompensationPolicyProvider DEFAULT_COMPENSATION_POLICY =
            new RemoteCompensationPolicyProvider() {
            };

    private RemoteServiceProxy() {
    }

    public static Service decorate(Service delegate,
                                   Function<String, Optional<RemoteServiceAdapter>> adapterResolver) {
        return decorate(delegate, adapterResolver, serviceName -> Optional.empty());
    }

    public static Service decorate(Service delegate,
                                   Function<String, Optional<RemoteServiceAdapter>> adapterResolver,
                                   Function<String, Optional<RemoteCompensationPolicyProvider>> policyResolver) {
        if (!(delegate instanceof RemoteGeneratedService)) {
            return delegate;
        }
        Set<Class<?>> interfaces = collectInterfaces(delegate.getClass());
        interfaces.add(Service.class);
        interfaces.add(DelegatingService.class);
        interfaces.add(RemoteGeneratedService.class);
        interfaces.add(RemoteCompensationPolicyProvider.class);
        InvocationHandler handler = new RemoteServiceInvocationHandler(delegate, adapterResolver, policyResolver);
        return (Service) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
                interfaces.toArray(Class[]::new), handler);
    }

    private static Set<Class<?>> collectInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = type;
        while (current != null) {
            collectInterfaces(current, interfaces);
            current = current.getSuperclass();
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> interfaces) {
        for (Class<?> item : type.getInterfaces()) {
            interfaces.add(item);
            collectInterfaces(item, interfaces);
        }
    }

    private record RemoteServiceInvocationHandler(Service delegate,
                                                  Function<String, Optional<RemoteServiceAdapter>> adapterResolver,
                                                  Function<String, Optional<RemoteCompensationPolicyProvider>> policyResolver)
            implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObject(proxy, method, args);
            }
            if ("getDelegateService".equals(method.getName()) && method.getParameterCount() == 0) {
                return delegate;
            }
            if (isRemoteCompensationPolicyMethod(method)) {
                return invokeRemoteCompensationPolicy(method);
            }
            if (isLocalMetadataMethod(method)) {
                return invokeDelegate(method, args);
            }
            RemoteServiceAdapter adapter = adapterResolver.apply(delegate.getName())
                    .orElseThrow(() -> new IllegalStateException("Remote service " + delegate.getName()
                            + " has no registered adapter."));
            Object[] arguments = normalizeArguments(method, args);
            if (adapter instanceof MethodAwareRemoteServiceAdapter methodAwareAdapter) {
                return methodAwareAdapter.execute(delegate.getName(), method, arguments);
            }
            return adapter.execute(delegate.getName(), arguments);
        }

        private Object[] normalizeArguments(Method method, Object[] args) {
            if (args == null) {
                return new Object[0];
            }
            if ("execute".equals(method.getName())
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isArray()
                    && args.length == 1
                    && args[0] instanceof Object[] values) {
                return values;
            }
            return args;
        }

        private boolean isRemoteCompensationPolicyMethod(Method method) {
            return switch (method.getName()) {
                case "getRemoteCompensationMaxAttempts", "getRemoteCompensationBackoffMillis",
                        "isRemoteCompensationManualInterventionRequired" -> method.getParameterCount() == 0;
                default -> false;
            };
        }

        private Object invokeRemoteCompensationPolicy(Method method) throws Throwable {
            RemoteCompensationPolicyProvider provider = policyResolver.apply(delegate.getName())
                    .orElse(delegate instanceof RemoteCompensationPolicyProvider value
                            ? value
                            : DEFAULT_COMPENSATION_POLICY);
            try {
                return method.invoke(provider);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private boolean isLocalMetadataMethod(Method method) {
            return switch (method.getName()) {
                case "getName", "copyFrom", "getTransactionMode", "getTransactionResourceId",
                        "getTransactionParticipant", "startTransaction", "endTransaction",
                        "getRepositoryHydrationRules", "getObjectSnapshotRules", "getCompensationServiceNames",
                        "getRemoteCompensationServiceNames",
                        "getRemoteCompensationMaxAttempts", "getRemoteCompensationBackoffMillis",
                        "isRemoteCompensationManualInterventionRequired",
                        "getTccConfirmServiceName", "getTccCancelServiceName",
                        "isLocalScope", "isAsyncScope", "getInputHeaderName", "getOutputHeaderName",
                        "requiresPhysicalBackendBinding", "requiresIdempotencyKey",
                        "getIdempotencyKeyFieldName", "hasIdempotencyKeyField",
                        "getServiceParameterNames",
                        "getGeneratedServiceKind", "getQuantumAttributeIds", "getTechnicalInterceptors",
                        "isUpdateEntry", "isSystemIdempotencyEnabled", "isSystemDuplicateGuardEnabled",
                        "getDeclaredIdempotencyKeyPaths", "getDuplicateWindow",
                        "getDeclaredIdempotencyFactsPath", "getDeclaredIdempotencyFactsPaths" -> true;
                default -> false;
            };
        }

        private Object invokeDelegate(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Object invokeObject(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> delegate.toString();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new IllegalStateException("Unsupported Object method " + method.getName());
            };
        }
    }
}
