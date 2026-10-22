package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import com.servpp.service.idempotency.JdbcBusinessIdempotencyStore;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.transaction.DefaultTransactionManager;
import com.servpp.service.transaction.JdbcSagaLogStore;
import com.servpp.service.transaction.JdbcTccBranchStore;
import com.servpp.service.transaction.JdbcTccReservationStore;

public class LocalJdbcPhysicalBackendAdapter implements PhysicalBackendAdapter {
    private final LocalPhysicalRuntime runtime;
    private final Map<String, String> declaredConfiguration;
    private final String quantumStoreName;

    public LocalJdbcPhysicalBackendAdapter(LocalPhysicalRuntime runtime) {
        this(runtime, Map.of(), "");
    }

    public LocalJdbcPhysicalBackendAdapter(LocalPhysicalRuntime runtime, Map<String, String> declaredConfiguration) {
        this(runtime, declaredConfiguration, "");
    }

    public LocalJdbcPhysicalBackendAdapter(LocalPhysicalRuntime runtime, Map<String, String> declaredConfiguration,
                                           String quantumStoreName) {
        this.runtime = runtime;
        this.declaredConfiguration = declaredConfiguration == null ? Map.of() : Map.copyOf(declaredConfiguration);
        this.quantumStoreName = quantumStoreName == null ? "" : quantumStoreName;
    }

    @Override
    public Set<String> backendNames() {
        return Set.of("localJdbc", "local-jdbc");
    }

    @Override
    public String implementationType() {
        return "appframe-local-jdbc";
    }

    @Override
    public List<String> validationErrors(String backendName) {
        List<String> errors = new ArrayList<>();
        validateNonBlank(errors, backendName, "datasourceUrl");
        validateNonBlank(errors, backendName, "driverClassName");
        validateNonBlank(errors, backendName, "schema");
        validateNonBlank(errors, backendName, "transactionCoordinator");
        validateNonBlank(errors, backendName, "idempotencyStore");
        validateNonBlank(errors, backendName, "sagaLogStore");
        validateNonBlank(errors, backendName, "tccBranchStore");
        validateNonBlank(errors, backendName, "tccReservationStore");
        validateNonBlank(errors, backendName, "idempotencyTable");
        validateNonBlank(errors, backendName, "sagaLogTable");
        validateNonBlank(errors, backendName, "tccBranchTable");
        validateNonBlank(errors, backendName, "tccReservationTable");
        validateNonNegativeLong(errors, backendName, "tccReservationTtlMillis");
        if (declaredConfiguration.containsKey("quantumStore")) {
            errors.add("Physical backend " + backendName
                    + " localJdbc adapter config quantumStore is no longer a backend config;"
                    + " use backend." + backendName + ".quantumStore instead.");
        }
        validateSupportedClass(errors, backendName, "transactionCoordinator",
                DefaultTransactionManager.class.getName());
        validateSupportedClass(errors, backendName, "idempotencyStore",
                JdbcBusinessIdempotencyStore.class.getName());
        validateSupportedClass(errors, backendName, "sagaLogStore",
                JdbcSagaLogStore.class.getName());
        validateSupportedClass(errors, backendName, "tccBranchStore",
                JdbcTccBranchStore.class.getName());
        validateSupportedClass(errors, backendName, "tccReservationStore",
                JdbcTccReservationStore.class.getName());
        return List.copyOf(errors);
    }

    @Override
    public Map<String, String> configuration() {
        Map<String, String> values = new LinkedHashMap<>(declaredConfiguration);
        if (!quantumStoreName.isBlank()) {
            values.put("quantumStore", quantumStoreName);
        }
        values.putIfAbsent("quantumJournalStore", runtime.getQuantumJournalStore().getClass().getName());
        values.putIfAbsent("quantumJournalTable", runtime.getQuantumJournalStore().getJournalTable());
        values.putIfAbsent("quantumValueTable", runtime.getQuantumJournalStore().getValueTable());
        putDataSourceUrl(values, "quantumDatasourceUrl", runtime.getQuantumJournalStore().getDataSource());
        values.put("idempotencyStore", runtime.getIdempotencyStore().getClass().getName());
        values.putIfAbsent("idempotencyTable", runtime.getIdempotencyStore().getTableName());
        putDataSourceUrl(values, "localJdbcDatasourceUrl", runtime.getIdempotencyStore().getDataSource());
        values.put("sagaLogStore", runtime.getSagaLogStore().getClass().getName());
        values.putIfAbsent("sagaLogTable", runtime.getSagaLogStore().getTableName());
        values.put("tccBranchStore", runtime.getTccBranchStore().getClass().getName());
        values.putIfAbsent("tccBranchTable", runtime.getTccBranchStore().getTableName());
        values.put("tccReservationStore", runtime.getTccReservationStore().getClass().getName());
        values.putIfAbsent("tccReservationTable", runtime.getTccReservationStore().getTableName());
        values.putIfAbsent("tccReservationTtlMillis",
                Long.toString(runtime.getTccReservationTtlMillis()));
        return Map.copyOf(values);
    }

    public LocalPhysicalRuntime getRuntime() {
        return runtime;
    }

    private void validateNonBlank(List<String> errors, String backendName, String key) {
        if (declaredConfiguration.containsKey(key) && declaredConfiguration.get(key).isBlank()) {
            errors.add("Physical backend " + backendName
                    + " localJdbc adapter config " + key + " must not be blank.");
        }
    }

    private void validateSupportedClass(List<String> errors, String backendName, String key, String supportedClass) {
        String declaredClass = declaredConfiguration.get(key);
        if (declaredClass == null || declaredClass.isBlank() || supportedClass.equals(declaredClass)) {
            return;
        }
        errors.add("Physical backend " + backendName
                + " localJdbc adapter config " + key
                + " declares unsupported implementation " + declaredClass
                + "; supported implementation is " + supportedClass + ".");
    }

    private void validateNonNegativeLong(List<String> errors, String backendName, String key) {
        if (!declaredConfiguration.containsKey(key)) {
            return;
        }
        String value = declaredConfiguration.get(key);
        try {
            if (Long.parseLong(value.trim()) < 0L) {
                errors.add("Physical backend " + backendName
                        + " localJdbc adapter config " + key + " must be a non-negative integer.");
            }
        } catch (NullPointerException | NumberFormatException e) {
            errors.add("Physical backend " + backendName
                    + " localJdbc adapter config " + key + " must be a non-negative integer.");
        }
    }

    private void putDataSourceUrl(Map<String, String> values, String key, DataSource dataSource) {
        if (dataSource instanceof DriverManagerJdbcDataSource driverManagerDataSource) {
            values.putIfAbsent(key, driverManagerDataSource.getUrl());
        }
    }
}
