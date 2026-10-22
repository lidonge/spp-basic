package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.util.List;

public record QuantumServiceCommitRequest(String storeName,
                                          String endpoint,
                                          String transactionId,
                                          String businessTraceId,
                                          String idempotencyKey,
                                          String boundaryServiceId,
                                          List<String> resourceIds,
                                          String targetStatus,
                                          String reversalMode,
                                          List<Record> records) {
    public QuantumServiceCommitRequest {
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static QuantumServiceCommitRequest from(String storeName, String endpoint, String reversalMode,
                                                   TransactionContext context,
                                                   QuantumEntanglementBoundary boundary) {
        return new QuantumServiceCommitRequest(storeName, endpoint, context.getId(),
                businessTraceId(context), idempotencyKey(context), boundary.getServiceId(),
                boundary.getResourceIds(), QuantumEntanglementBoundary.Status.COMMITTED.name(),
                reversalMode, boundary.getRecords().stream()
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
                         BigDecimal afterValue,
                         BigDecimal minimum,
                         BigDecimal maximum) {
        public Record(String groupId, String attributeId, String objectId, BigDecimal beforeValue,
                      BigDecimal deltaValue, BigDecimal afterValue) {
            this(groupId, attributeId, objectId, beforeValue, deltaValue, afterValue, null, null);
        }
        private static Record from(QuantumConservationRecord record) {
            return new Record(record.getGroupId(), record.getAttributeId(), record.getObjectId(),
                    record.getBeforeValue(), record.getDeltaValue(), record.getAfterValue(), record.getMinimum(), record.getMaximum());
        }
    }
}
