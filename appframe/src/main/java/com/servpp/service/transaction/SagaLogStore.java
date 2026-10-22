package com.servpp.service.transaction;

import java.util.List;

public interface SagaLogStore {
    void append(SagaLogEntry entry);

    List<SagaLogEntry> findAll();

    void clear();
}
