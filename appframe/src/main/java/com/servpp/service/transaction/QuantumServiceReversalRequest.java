package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.util.List;

public record QuantumServiceReversalRequest(String operation,
                                            String storeName,
                                            String endpoint,
                                            String transactionId,
                                            String businessTraceId,
                                            String idempotencyKey,
                                            String boundaryServiceId,
                                            List<String> resourceIds,
                                            String targetStatus,
                                            String reversalMode,
                                            List<Record> records) {
    public QuantumServiceReversalRequest {
        operation = operation == null || operation.isBlank() ? "REVERSAL" : operation;
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static QuantumServiceReversalRequest from(String storeName, String endpoint, String reversalMode,
                                                     TransactionContext context,
                                                     QuantumEntanglementBoundary boundary) {
        QuantumEntanglementBoundary reversalBoundary = boundary.negativeDeltaReversal();
        return new QuantumServiceReversalRequest("REVERSAL", storeName, endpoint, context.getId(),
                businessTraceId(context), idempotencyKey(context), reversalBoundary.getServiceId(),
                reversalBoundary.getResourceIds(), reversalBoundary.getStatus().name(), reversalMode,
                reversalBoundary.getRecords().stream()
                        .map(Record::from)
                        .toList());
    }

    private static String businessTraceId(TransactionContext context) {
        return context == null || context.getGlobalSerialNumber() == null ? "" : context.getGlobalSerialNumber();
    }

    private static String idempotencyKey(TransactionContext context) {
        return businessTraceId(context);
    }

    public record Record(String groupId,
                         String attributeId,
                         String objectId,
                         BigDecimal beforeValue,
                         BigDecimal deltaValue,
                         BigDecimal afterValue) {
        private static Record from(QuantumConservationRecord record) {
            return new Record(record.getGroupId(), record.getAttributeId(), record.getObjectId(),
                    record.getBeforeValue(), record.getDeltaValue(), record.getAfterValue());
        }
    }
}
