package com.servpp.service.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.servpp.service.transaction.TransactionContext;

public class ReflectiveSeataGlobalTransactionBridge implements SeataTransactionBridge {
    public static final String GLOBAL_TRANSACTION_CONTEXT_CLASS_CONFIG = "seataGlobalTransactionContextClass";
    public static final String GLOBAL_TRANSACTION_CLASS_CONFIG = "seataGlobalTransactionClass";
    public static final String TRANSACTION_TIMEOUT_MILLIS_CONFIG = "seataTransactionTimeoutMillis";
    private static final String DEFAULT_GLOBAL_TRANSACTION_CONTEXT_CLASS =
            "io.seata.tm.api.GlobalTransactionContext";
    private static final String DEFAULT_GLOBAL_TRANSACTION_CLASS =
            "io.seata.tm.api.GlobalTransaction";
    private static final int DEFAULT_TRANSACTION_TIMEOUT_MILLIS = 60000;
    private static final String STATE_ATTRIBUTE = ReflectiveSeataGlobalTransactionBridge.class.getName() + ".state";

    private String globalTransactionContextClassName = DEFAULT_GLOBAL_TRANSACTION_CONTEXT_CLASS;
    private String globalTransactionClassName = DEFAULT_GLOBAL_TRANSACTION_CLASS;
    private String transactionTimeoutMillisValue = String.valueOf(DEFAULT_TRANSACTION_TIMEOUT_MILLIS);
    private int transactionTimeoutMillis = DEFAULT_TRANSACTION_TIMEOUT_MILLIS;

    @Override
    public void configure(Map<String, String> configuration) {
        globalTransactionContextClassName = valueOrDefault(configuration.get(GLOBAL_TRANSACTION_CONTEXT_CLASS_CONFIG),
                DEFAULT_GLOBAL_TRANSACTION_CONTEXT_CLASS);
        globalTransactionClassName = valueOrDefault(configuration.get(GLOBAL_TRANSACTION_CLASS_CONFIG),
                DEFAULT_GLOBAL_TRANSACTION_CLASS);
        transactionTimeoutMillisValue = valueOrDefault(configuration.get(TRANSACTION_TIMEOUT_MILLIS_CONFIG),
                String.valueOf(DEFAULT_TRANSACTION_TIMEOUT_MILLIS));
        transactionTimeoutMillis = parsePositiveInt(transactionTimeoutMillisValue, DEFAULT_TRANSACTION_TIMEOUT_MILLIS);
    }

    @Override
    public String bridgeType() {
        return "seata-reflective-global-transaction";
    }

    @Override
    public Map<String, String> configuration() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(GLOBAL_TRANSACTION_CONTEXT_CLASS_CONFIG, globalTransactionContextClassName);
        values.put(GLOBAL_TRANSACTION_CLASS_CONFIG, globalTransactionClassName);
        values.put(TRANSACTION_TIMEOUT_MILLIS_CONFIG, transactionTimeoutMillisValue);
        return Map.copyOf(values);
    }

    @Override
    public List<String> validationErrors(String backendName, String applicationId, String transactionServiceGroup) {
        List<String> errors = new ArrayList<>();
        if (globalTransactionContextClassName == null || globalTransactionContextClassName.isBlank()) {
            errors.add("Physical backend " + backendName
                    + " Seata reflective bridge config " + GLOBAL_TRANSACTION_CONTEXT_CLASS_CONFIG
                    + " must not be blank.");
        }
        if (globalTransactionClassName == null || globalTransactionClassName.isBlank()) {
            errors.add("Physical backend " + backendName
                    + " Seata reflective bridge config " + GLOBAL_TRANSACTION_CLASS_CONFIG
                    + " must not be blank.");
        }
        if (!isPositiveInt(transactionTimeoutMillisValue)) {
            errors.add("Physical backend " + backendName
                    + " Seata reflective bridge config " + TRANSACTION_TIMEOUT_MILLIS_CONFIG
                    + " must be positive.");
        }
        validateSeataClasses(backendName, errors);
        return List.copyOf(errors);
    }

    @Override
    public void begin(String applicationId, String transactionServiceGroup,
                      PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        BridgeState state = state(context);
        synchronized (state) {
            state.beginCount++;
            if (state.transaction == null) {
                state.transaction = createGlobalTransaction();
                invokeBegin(state.transaction, transactionName(applicationId, transactionServiceGroup, context));
            }
        }
    }

    @Override
    public void commit(String applicationId, String transactionServiceGroup,
                       PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        BridgeState state = state(context);
        synchronized (state) {
            state.commitCount++;
            if (!state.completed && state.transaction != null && state.commitCount >= state.beginCount) {
                invokeNoArg(state.transaction, "commit");
                state.completed = true;
            }
        }
    }

    @Override
    public void rollback(String applicationId, String transactionServiceGroup,
                         PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                         Throwable cause) {
        BridgeState state = state(context);
        synchronized (state) {
            if (!state.completed && state.transaction != null) {
                invokeNoArg(state.transaction, "rollback");
                state.completed = true;
            }
        }
    }

    private Object createGlobalTransaction() {
        try {
            Class<?> contextClass = Class.forName(globalTransactionContextClassName);
            Method factory = contextClass.getMethod("getCurrentOrCreate");
            return factory.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException error) {
            throw new IllegalStateException("Unable to create Seata global transaction via "
                    + globalTransactionContextClassName + ".getCurrentOrCreate().", unwrap(error));
        }
    }

    private void invokeBegin(Object transaction, String transactionName) {
        if (invokeIfPresent(transaction, "begin",
                new Class<?>[] { int.class, String.class }, transactionTimeoutMillis, transactionName)) {
            return;
        }
        if (invokeIfPresent(transaction, "begin", new Class<?>[] { String.class }, transactionName)) {
            return;
        }
        if (invokeIfPresent(transaction, "begin", new Class<?>[] { int.class }, transactionTimeoutMillis)) {
            return;
        }
        if (invokeIfPresent(transaction, "begin", new Class<?>[0])) {
            return;
        }
        throw new IllegalStateException("Seata global transaction class "
                + transaction.getClass().getName()
                + " does not expose a supported begin method.");
    }

    private void invokeNoArg(Object transaction, String methodName) {
        if (!invokeIfPresent(transaction, methodName, new Class<?>[0])) {
            throw new IllegalStateException("Seata global transaction class "
                    + transaction.getClass().getName()
                    + " does not expose " + methodName + "().");
        }
    }

    private boolean invokeIfPresent(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.invoke(target, args);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Unable to invoke Seata global transaction method "
                    + methodName + ".", unwrap(error));
        }
    }

    private void validateSeataClasses(String backendName, List<String> errors) {
        Class<?> contextClass = loadClass(backendName, globalTransactionContextClassName,
                GLOBAL_TRANSACTION_CONTEXT_CLASS_CONFIG, errors);
        Class<?> transactionClass = loadClass(backendName, globalTransactionClassName,
                GLOBAL_TRANSACTION_CLASS_CONFIG, errors);
        if (contextClass != null && !hasStaticFactory(contextClass)) {
            errors.add("Physical backend " + backendName
                    + " Seata reflective bridge class " + globalTransactionContextClassName
                    + " must expose static getCurrentOrCreate().");
        }
        if (transactionClass != null) {
            if (!hasBeginMethod(transactionClass)) {
                errors.add("Physical backend " + backendName
                        + " Seata reflective bridge class " + globalTransactionClassName
                        + " must expose begin(...).");
            }
            if (!hasNoArgMethod(transactionClass, "commit")) {
                errors.add("Physical backend " + backendName
                        + " Seata reflective bridge class " + globalTransactionClassName
                        + " must expose commit().");
            }
            if (!hasNoArgMethod(transactionClass, "rollback")) {
                errors.add("Physical backend " + backendName
                        + " Seata reflective bridge class " + globalTransactionClassName
                        + " must expose rollback().");
            }
        }
    }

    private Class<?> loadClass(String backendName, String className, String configKey, List<String> errors) {
        if (className == null || className.isBlank()) {
            return null;
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException error) {
            errors.add("Physical backend " + backendName
                    + " Seata reflective bridge config " + configKey
                    + " class " + className + " cannot be loaded.");
            return null;
        }
    }

    private boolean hasStaticFactory(Class<?> contextClass) {
        try {
            contextClass.getMethod("getCurrentOrCreate");
            return true;
        } catch (NoSuchMethodException error) {
            return false;
        }
    }

    private boolean hasBeginMethod(Class<?> transactionClass) {
        return hasMethod(transactionClass, "begin", int.class, String.class)
                || hasMethod(transactionClass, "begin", String.class)
                || hasMethod(transactionClass, "begin", int.class)
                || hasMethod(transactionClass, "begin");
    }

    private boolean hasNoArgMethod(Class<?> transactionClass, String methodName) {
        return hasMethod(transactionClass, methodName);
    }

    private boolean hasMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            type.getMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException error) {
            return false;
        }
    }

    private BridgeState state(TransactionContext context) {
        return context.getOrCreateAttribute(STATE_ATTRIBUTE, BridgeState::new);
    }

    private String transactionName(String applicationId, String transactionServiceGroup, TransactionContext context) {
        String app = applicationId == null || applicationId.isBlank() ? "spp" : applicationId;
        String group = transactionServiceGroup == null || transactionServiceGroup.isBlank()
                ? "default" : transactionServiceGroup;
        return app + ":" + group + ":" + context.getTraceId();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private int parsePositiveInt(String value, int defaultValue) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NullPointerException | NumberFormatException error) {
            return defaultValue;
        }
    }

    private boolean isPositiveInt(String value) {
        try {
            return Integer.parseInt(value.trim()) > 0;
        } catch (NullPointerException | NumberFormatException error) {
            return false;
        }
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return error;
    }

    private static class BridgeState {
        private Object transaction;
        private int beginCount;
        private int commitCount;
        private boolean completed;
    }
}
