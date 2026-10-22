package com.servpp.service.transaction;

import java.util.List;
import java.util.Optional;

public interface TccBranchStore {
    void save(TccBranchRecord record);

    Optional<TccBranchRecord> find(String branchId);

    List<TccBranchRecord> findAll();

    default List<TccBranchRecord> findByStatus(TccBranchStatus status) {
        return findAll().stream()
                .filter(record -> record.status() == status)
                .toList();
    }

    void clear();
}
