package com.servpp.service.transaction;

import java.util.List;
import java.util.Optional;

public interface TccReservationStore {
    void save(TccReservationRecord record);

    Optional<TccReservationRecord> find(String reservationId);

    List<TccReservationRecord> findAll();

    List<TccReservationRecord> findExpiredTrying(String cutoffIso);

    void clear();
}
