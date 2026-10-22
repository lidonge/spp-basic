package com.servpp.service.runtime;

import java.util.Objects;

import javax.sql.DataSource;

import com.servpp.service.idempotency.BusinessIdempotencyManager;
import com.servpp.service.idempotency.JdbcBusinessIdempotencyStore;
import com.servpp.service.transaction.DefaultTccTransactionParticipant;
import com.servpp.service.transaction.DefaultTransactionManager;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import com.servpp.service.transaction.JournalQuantumAccessAdapter;
import com.servpp.service.transaction.JdbcSagaLogStore;
import com.servpp.service.transaction.JdbcTccBranchStore;
import com.servpp.service.transaction.JdbcTccReservationStore;
import com.servpp.service.transaction.QuantumAccessAdapter;
import com.servpp.service.transaction.SagaLogManager;
import com.servpp.service.transaction.TccObjectStoreQuantumCoordinator;

public class LocalPhysicalRuntime {
    private JdbcQuantumJournalStore quantumJournalStore;
    private QuantumAccessAdapter quantumAccessAdapter;
    private JdbcBusinessIdempotencyStore idempotencyStore;
    private JdbcSagaLogStore sagaLogStore;
    private JdbcTccBranchStore tccBranchStore;
    private JdbcTccReservationStore tccReservationStore;
    private TccObjectStoreQuantumCoordinator tccObjectStoreQuantumCoordinator =
            TccObjectStoreQuantumCoordinator.noop();
    private long tccReservationTtlMillis;

    public LocalPhysicalRuntime(JdbcQuantumJournalStore quantumJournalStore,
                                JdbcBusinessIdempotencyStore idempotencyStore,
                                JdbcSagaLogStore sagaLogStore,
                                JdbcTccBranchStore tccBranchStore) {
        this(quantumJournalStore, idempotencyStore, sagaLogStore, tccBranchStore,
                new JdbcTccReservationStore(tccBranchStore.getDataSource()));
    }

    public LocalPhysicalRuntime(JdbcQuantumJournalStore quantumJournalStore,
                                JdbcBusinessIdempotencyStore idempotencyStore,
                                JdbcSagaLogStore sagaLogStore,
                                JdbcTccBranchStore tccBranchStore,
                                JdbcTccReservationStore tccReservationStore) {
        this.quantumJournalStore = quantumJournalStore;
        this.quantumAccessAdapter = new JournalQuantumAccessAdapter(quantumJournalStore);
        this.idempotencyStore = idempotencyStore;
        this.sagaLogStore = sagaLogStore;
        this.tccBranchStore = tccBranchStore;
        this.tccReservationStore = tccReservationStore;
    }

    public static LocalPhysicalRuntime jdbc(DataSource dataSource) {
        return new LocalPhysicalRuntime(
                new JdbcQuantumJournalStore(dataSource),
                new JdbcBusinessIdempotencyStore(dataSource),
                new JdbcSagaLogStore(dataSource),
                new JdbcTccBranchStore(dataSource));
    }

    private com.servpp.service.idempotency.RequestIdempotencyStore requestStore;
    public void setRequestStore(com.servpp.service.idempotency.RequestIdempotencyStore store) {
        requestStore = store; BusinessIdempotencyManager.setRequestStore(store);
    }
    public void install() {
        DefaultTransactionManager.getInstance().setQuantumJournalStore(quantumJournalStore);
        DefaultTransactionManager.getInstance().setQuantumAccessAdapter(quantumAccessAdapter);
        BusinessIdempotencyManager.setStore(idempotencyStore);
        BusinessIdempotencyManager.setRequestStore(requestStore);
        SagaLogManager.setStore(sagaLogStore);
        DefaultTccTransactionParticipant.setBranchStore(tccBranchStore);
    }

    public void clear() {
        install();
        BusinessIdempotencyManager.clear();
        DefaultTransactionManager.clearLastContext();
        quantumJournalStore.clear();
        tccObjectStoreQuantumCoordinator.clear();
        sagaLogStore.clear();
        tccBranchStore.clear();
        tccReservationStore.clear();
    }

    public JdbcQuantumJournalStore getQuantumJournalStore() {
        return quantumJournalStore;
    }

    public void setQuantumJournalStore(JdbcQuantumJournalStore quantumJournalStore) {
        this.quantumJournalStore = quantumJournalStore;
        this.quantumAccessAdapter = new JournalQuantumAccessAdapter(quantumJournalStore);
        DefaultTransactionManager.getInstance().setQuantumJournalStore(quantumJournalStore);
    }

    public QuantumAccessAdapter getQuantumAccessAdapter() {
        return quantumAccessAdapter;
    }

    public void setQuantumAccessAdapter(QuantumAccessAdapter quantumAccessAdapter) {
        this.quantumAccessAdapter = Objects.requireNonNull(quantumAccessAdapter, "quantumAccessAdapter");
        DefaultTransactionManager.getInstance().setQuantumAccessAdapter(quantumAccessAdapter);
    }

    public JdbcBusinessIdempotencyStore getIdempotencyStore() {
        return idempotencyStore;
    }

    public void setIdempotencyStore(JdbcBusinessIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        BusinessIdempotencyManager.setStore(idempotencyStore);
        BusinessIdempotencyManager.setRequestStore(requestStore);
    }

    public JdbcSagaLogStore getSagaLogStore() {
        return sagaLogStore;
    }

    public void setSagaLogStore(JdbcSagaLogStore sagaLogStore) {
        this.sagaLogStore = sagaLogStore;
        SagaLogManager.setStore(sagaLogStore);
    }

    public JdbcTccBranchStore getTccBranchStore() {
        return tccBranchStore;
    }

    public void setTccBranchStore(JdbcTccBranchStore tccBranchStore) {
        this.tccBranchStore = tccBranchStore;
        DefaultTccTransactionParticipant.setBranchStore(tccBranchStore);
    }

    public JdbcTccReservationStore getTccReservationStore() {
        return tccReservationStore;
    }

    public void setTccReservationStore(JdbcTccReservationStore tccReservationStore) {
        this.tccReservationStore = tccReservationStore;
    }

    public TccObjectStoreQuantumCoordinator getTccObjectStoreQuantumCoordinator() {
        return tccObjectStoreQuantumCoordinator;
    }

    public void setTccObjectStoreQuantumCoordinator(
            TccObjectStoreQuantumCoordinator tccObjectStoreQuantumCoordinator) {
        this.tccObjectStoreQuantumCoordinator = tccObjectStoreQuantumCoordinator == null
                ? TccObjectStoreQuantumCoordinator.noop()
                : tccObjectStoreQuantumCoordinator;
    }

    public long getTccReservationTtlMillis() {
        return tccReservationTtlMillis;
    }

    public void setTccReservationTtlMillis(long tccReservationTtlMillis) {
        this.tccReservationTtlMillis = Math.max(0L, tccReservationTtlMillis);
    }
}
