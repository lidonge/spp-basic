package com.servpp.service.transaction;

import java.util.ArrayList;
import java.util.List;

public class InMemorySagaLogStore implements SagaLogStore {
    private final List<SagaLogEntry> entries = new ArrayList<>();

    @Override
    public synchronized void append(SagaLogEntry entry) {
        entries.add(entry);
    }

    @Override
    public synchronized List<SagaLogEntry> findAll() {
        return new ArrayList<>(entries);
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }
}
