package com.servpp.service.transaction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.servpp.service.DelegatingService;
import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;

public final class TccLifecycleServiceInvoker {
    private TccLifecycleServiceInvoker() {
    }

    public static Object invoke(String serviceName, Object[] arguments) {
        Service service = DefaultServiceContainer.getInstance().getService(serviceName);
        if (service == null) {
            throw new IllegalStateException("TCC lifecycle service " + serviceName + " is not registered");
        }
        service = originalService(service);
        Method method = findLifecycleMethod(service.getClass(), serviceName, arguments);
        try {
            return method.invoke(service, arguments == null ? new Object[0] : arguments);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access TCC lifecycle service " + serviceName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("TCC lifecycle service " + serviceName + " failed", cause);
        }
    }

    private static Service originalService(Service service) {
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

    private static Method findLifecycleMethod(Class<?> type, String serviceName, Object[] arguments) {
        int argumentCount = arguments == null ? 0 : arguments.length;
        for (Method method : type.getMethods()) {
            if (method.getName().equals(serviceName)
                    && method.getParameterCount() == argumentCount
                    && accepts(method.getParameterTypes(), arguments)) {
                return method;
            }
        }
        throw new IllegalStateException("TCC lifecycle service " + serviceName
                + " does not expose a compatible method with " + argumentCount + " arguments");
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] arguments) {
        if (arguments == null) {
            return parameterTypes.length == 0;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (arguments[i] != null && !box(parameterTypes[i]).isInstance(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
