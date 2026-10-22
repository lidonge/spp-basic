package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record TccReservationReadinessIndex(
        List<TccReservationPlan> plans,
        Map<String, List<TccReservationPlan>> byReadiness,
        Map<String, List<TccReservationPlan>> byReadyReason,
        Map<String, List<TccReservationPlan>> byMissingReason,
        Map<String, List<TccReservationPlan>> byAction,
        Map<String, List<TccReservationPlan>> byService,
        Map<String, List<TccReservationPlan>> byResource,
        Map<String, List<TccReservationPlan>> bySourceQuantumStore,
        Map<String, List<TccReservationPlan>> bySourceImplementationStrategy,
        Map<String, List<TccReservationPlan>> byTargetRepository,
        Map<String, List<TccReservationPlan>> byTargetColumn,
        Map<String, List<TccReservationPlan>> byTargetColumnKind,
        Map<String, List<TccReservationPlan>> byReservationBoundary,
        Map<String, List<TccReservationPlan>> byPhysicalReservationPath,
        Map<String, List<TccReservationPlan>> byReservationContract) {
}
