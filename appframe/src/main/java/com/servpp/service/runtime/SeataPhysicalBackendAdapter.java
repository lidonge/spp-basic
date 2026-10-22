package com.servpp.service.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.sql.DataSource;

import com.servpp.service.transaction.TransactionContext;

public class SeataPhysicalBackendAdapter implements PhysicalBackendAdapter {
    private final String applicationId;
    private final String transactionServiceGroup;
    private final boolean quantumSupported;
    private final String quantumSupportedValue;
    private final String seataTransactionTimeoutMillis;
    private final SeataTransactionBridge transactionBridge;
    private final String transactionBridgeClassName;
    private final String transactionBridgeLoadError;

    public SeataPhysicalBackendAdapter(String transactionServiceGroup) {
        this(null, transactionServiceGroup, false);
    }

    public SeataPhysicalBackendAdapter(String applicationId, String transactionServiceGroup) {
        this(applicationId, transactionServiceGroup, false);
    }

    public SeataPhysicalBackendAdapter(String applicationId, String transactionServiceGroup,
                                       boolean quantumSupported) {
        this(applicationId, transactionServiceGroup, quantumSupported, new MetadataOnlySeataTransactionBridge());
    }

    public SeataPhysicalBackendAdapter(String applicationId, String transactionServiceGroup,
                                       boolean quantumSupported, SeataTransactionBridge transactionBridge) {
        this.applicationId = normalize(applicationId);
        this.transactionServiceGroup = normalize(transactionServiceGroup);
        this.quantumSupported = quantumSupported;
        this.quantumSupportedValue = String.valueOf(quantumSupported);
        this.seataTransactionTimeoutMillis = null;
        this.transactionBridge = Objects.requireNonNull(transactionBridge, "transactionBridge");
        this.transactionBridgeClassName = null;
        this.transactionBridgeLoadError = null;
    }

    private SeataPhysicalBackendAdapter(String applicationId, String transactionServiceGroup,
                                        String quantumSupportedValue, String seataTransactionTimeoutMillis,
                                        SeataTransactionBridge transactionBridge,
                                        String transactionBridgeClassName, String transactionBridgeLoadError) {
        this.applicationId = normalize(applicationId);
        this.transactionServiceGroup = normalize(transactionServiceGroup);
        this.quantumSupportedValue = normalize(quantumSupportedValue);
        this.quantumSupported = this.quantumSupportedValue != null
                && Boolean.parseBoolean(this.quantumSupportedValue);
        this.seataTransactionTimeoutMillis = normalize(seataTransactionTimeoutMillis);
        this.transactionBridge = Objects.requireNonNull(transactionBridge, "transactionBridge");
        this.transactionBridgeClassName = normalize(transactionBridgeClassName);
        this.transactionBridgeLoadError = transactionBridgeLoadError;
    }

    public static SeataPhysicalBackendAdapter from(PhysicalRuntimeCatalog.PhysicalBackend backend) {
        Map<String, String> configuration = backend.configuration();
        String transactionBridgeClassName = normalize(configuration.get("transactionBridgeClass"));
        LoadedTransactionBridge loadedBridge = loadTransactionBridge(transactionBridgeClassName, configuration);
        return new SeataPhysicalBackendAdapter(
                configuration.get("applicationId"),
                configuration.get("transactionServiceGroup"),
                configuration.getOrDefault("quantumSupported", "false"),
                configuration.get(ReflectiveSeataGlobalTransactionBridge.TRANSACTION_TIMEOUT_MILLIS_CONFIG),
                loadedBridge.bridge(),
                transactionBridgeClassName,
                loadedBridge.error());
    }

    private static LoadedTransactionBridge loadTransactionBridge(String transactionBridgeClassName,
                                                                 Map<String, String> configuration) {
        if (transactionBridgeClassName == null || transactionBridgeClassName.isBlank()) {
            return new LoadedTransactionBridge(new MetadataOnlySeataTransactionBridge(), null);
        }
        try {
            Class<?> bridgeClass = Class.forName(transactionBridgeClassName.trim());
            Object bridge = bridgeClass.getDeclaredConstructor().newInstance();
            if (!(bridge instanceof SeataTransactionBridge seataBridge)) {
                return new LoadedTransactionBridge(new MetadataOnlySeataTransactionBridge(),
                        "class does not implement " + SeataTransactionBridge.class.getName());
            }
            seataBridge.configure(Map.copyOf(configuration));
            return new LoadedTransactionBridge(seataBridge, null);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | NoSuchMethodException | InvocationTargetException error) {
            return new LoadedTransactionBridge(new MetadataOnlySeataTransactionBridge(), error.getMessage());
        }
    }

    @Override
    public Set<String> backendNames() {
        return Set.of("seata");
    }

    @Override
    public boolean supportsTransaction(String transactionKind, String targetName) {
        return ("xa".equals(transactionKind) && "true".equals(transactionBridge.configuration().get("nativeXa")))
                || Set.of("at", "tcc", "saga").contains(transactionKind)
                || (quantumSupported && "quantum".equals(transactionKind));
    }

    @Override
    public List<String> validationErrors(String backendName) {
        List<String> errors = new ArrayList<>();
        if (transactionServiceGroup == null || transactionServiceGroup.isBlank()) {
            errors.add("Physical backend " + backendName
                    + " Seata adapter must configure transactionServiceGroup.");
        }
        if (quantumSupportedValue != null && !quantumSupportedValue.isBlank()
                && !Set.of("true", "false").contains(quantumSupportedValue.trim())) {
            errors.add("Physical backend " + backendName
                    + " Seata adapter quantumSupported must be true or false.");
        }
        if (transactionBridgeClassName != null && transactionBridgeClassName.isBlank()) {
            errors.add("Physical backend " + backendName
                    + " Seata adapter transactionBridgeClass must not be blank.");
        }
        if (transactionBridgeLoadError != null) {
            errors.add("Physical backend " + backendName
                    + " Seata adapter transactionBridgeClass " + transactionBridgeClassName
                    + " cannot be loaded: " + transactionBridgeLoadError + ".");
        }
        if (transactionBridgeLoadError == null) {
            errors.addAll(transactionBridge.validationErrors(backendName,
                    applicationId, transactionServiceGroup));
        }
        return List.copyOf(errors);
    }

    @Override
    public String implementationType() {
        return quantumSupported ? "seata-at-tcc-saga-quantum" : "seata-at-tcc-saga";
    }

    @Override
    public Map<String, String> configuration() {
        Map<String, String> values = new LinkedHashMap<>();
        if (applicationId != null && !applicationId.isBlank()) {
            values.put("applicationId", applicationId);
        }
        if (transactionServiceGroup != null && !transactionServiceGroup.isBlank()) {
            values.put("transactionServiceGroup", transactionServiceGroup);
        }
        values.put("supportedTransactions", "at,tcc,saga"
                + ("true".equals(transactionBridge.configuration().get("nativeXa")) ? ",xa" : "")
                + (quantumSupported ? ",quantum" : ""));
        if (seataTransactionTimeoutMillis != null && !seataTransactionTimeoutMillis.isBlank()) {
            values.put(ReflectiveSeataGlobalTransactionBridge.TRANSACTION_TIMEOUT_MILLIS_CONFIG,
                    seataTransactionTimeoutMillis);
        }
        values.put("transactionBridge", transactionBridge.bridgeType());
        if (transactionBridgeClassName != null && !transactionBridgeClassName.isBlank()) {
            values.put("transactionBridgeClass", transactionBridgeClassName);
        }
        for (Map.Entry<String, String> entry : transactionBridge.configuration().entrySet()) {
            values.put("transactionBridge." + entry.getKey(), entry.getValue());
        }
        return Map.copyOf(values);
    }

    @Override
    public void useTccPhaseTwoRecovery(TccPhaseTwoRecoveryHandler recoveryHandler) {
        transactionBridge.useTccPhaseTwoRecovery(recoveryHandler);
    }

    @Override
    public boolean ownsTccPhaseTwo(PhysicalBackendAssignment assignment) {
        return transactionBridge.ownsTccPhaseTwo(assignment);
    }

    @Override
    public void registerTccPhaseTwoCallback(PhysicalBackendAssignment assignment,
                                            TransactionContext context,
                                            TccPhaseTwoRecoveryHandler callback) {
        transactionBridge.registerTccPhaseTwoCallback(assignment, context, callback);
    }

    @Override
    public void unregisterTccPhaseTwoCallback(PhysicalBackendAssignment assignment,
                                              TransactionContext context) {
        transactionBridge.unregisterTccPhaseTwoCallback(assignment, context);
    }

    @Override
    public DataSource wrapDataSource(String backendName, String resourceKind, String resourceName,
                                     DataSource dataSource) {
        return transactionBridge.wrapDataSource(applicationId, transactionServiceGroup,
                backendName, resourceKind, resourceName, dataSource);
    }

    @Override
    public void begin(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        transactionBridge.begin(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    @Override
    public void beforeExecute(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        transactionBridge.beforeExecute(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    @Override
    public void afterExecute(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        transactionBridge.afterExecute(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    @Override
    public void commit(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        transactionBridge.commit(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    @Override
    public void rollback(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                         Throwable cause) {
        transactionBridge.rollback(applicationId, transactionServiceGroup, assignment, context, arguments, cause);
    }

    @Override
    public void beforeTry(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        transactionBridge.beforeTry(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    @Override
    public void afterTry(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        transactionBridge.afterTry(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    @Override
    public void confirm(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments) {
        transactionBridge.confirm(applicationId, transactionServiceGroup, assignment, context, arguments);
    }

    @Override
    public void cancel(PhysicalBackendAssignment assignment, TransactionContext context, Object[] arguments,
                       Throwable cause) {
        transactionBridge.cancel(applicationId, transactionServiceGroup, assignment, context, arguments, cause);
    }

    private record LoadedTransactionBridge(SeataTransactionBridge bridge, String error) {
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
