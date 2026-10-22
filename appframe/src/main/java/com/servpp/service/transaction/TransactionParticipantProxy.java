package com.servpp.service.transaction;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import com.servpp.service.DelegatingService;
import com.servpp.service.Service;
import com.servpp.service.SourceScenarioService;

public final class TransactionParticipantProxy {
    private TransactionParticipantProxy() {
    }

    public static Service decorate(Service delegate,
                                   Function<TransactionParticipant, TransactionParticipant> decorator) {
        if (!(delegate instanceof TransactionalService transactional)) {
            throw new IllegalArgumentException("Delegate service must implement TransactionalService");
        }
        if (delegate instanceof SourceScenarioService scenario) {
            scenario.decorateTransactionParticipant(decorator);
            return scenario;
        }
        Set<Class<?>> interfaces = collectInterfaces(delegate.getClass());
        interfaces.add(Service.class);
        interfaces.add(DelegatingService.class);
        interfaces.add(TransactionalService.class);
        InvocationHandler handler = new ParticipantDecoratingInvocationHandler(delegate, transactional, decorator);
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

    private record ParticipantDecoratingInvocationHandler(Service delegate,
                                                          TransactionalService transactional,
                                                          Function<TransactionParticipant, TransactionParticipant> decorator)
            implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObject(proxy, method, args);
            }
            if ("getDelegateService".equals(method.getName()) && method.getParameterCount() == 0) {
                return delegate;
            }
            if ("getTransactionParticipant".equals(method.getName()) && method.getParameterCount() == 0) {
                return decorator.apply(transactional.getTransactionParticipant());
            }
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
