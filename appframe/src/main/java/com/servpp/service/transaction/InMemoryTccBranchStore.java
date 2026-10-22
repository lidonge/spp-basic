package com.servpp.service.transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryTccBranchStore implements TccBranchStore {
    private final Map<String, TccBranchRecord> records = new LinkedHashMap<>();

    @Override
    public synchronized void save(TccBranchRecord record) {
        records.put(record.branchId(), record);
    }

    @Override
    public synchronized Optional<TccBranchRecord> find(String branchId) {
        return Optional.ofNullable(records.get(branchId));
    }

    @Override
    public synchronized List<TccBranchRecord> findAll() {
        return new ArrayList<>(records.values());
    }

    @Override
    public synchronized void clear() {
        records.clear();
    }
}
