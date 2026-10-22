package com.servpp.service.transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class QuantumBoundaryPayloadCodec {
    private static final int VERSION = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private QuantumBoundaryPayloadCodec() {
    }

    public static Optional<String> encode(TransactionContext context) {
        if (context == null) {
            return Optional.empty();
        }
        QuantumEntanglementBoundary boundary = context.getQuantumEntanglementBoundary();
        if (boundary == null || !boundary.isCommitted() || boundary.getRecords().isEmpty()) {
            return Optional.empty();
        }
        Payload payload = new Payload(
                VERSION,
                context.getId(),
                context.getTraceId(),
                boundary.getServiceId(),
                boundary.getResourceIds(),
                boundary.getRecords().stream().map(RecordPayload::from).toList());
        try {
            return Optional.of(OBJECT_MAPPER.writeValueAsString(payload));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize committed quantum boundary", e);
        }
    }

    public static DecodedBoundary decode(String payloadJson, QuantumJournalStore journalStore) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("Committed quantum boundary payload must not be blank");
        }
        try {
            Payload payload = OBJECT_MAPPER.readValue(payloadJson, Payload.class);
            if (payload.version() != VERSION) {
                throw new IllegalStateException("Unsupported committed quantum boundary payload version "
                        + payload.version());
            }
            if (payload.transactionId() == null || payload.transactionId().isBlank()) {
                throw new IllegalStateException("Committed quantum boundary payload requires transactionId");
            }
            if (payload.serviceId() == null || payload.serviceId().isBlank()) {
                throw new IllegalStateException("Committed quantum boundary payload requires serviceId");
            }
            if (payload.resourceIds().isEmpty()) {
                throw new IllegalStateException("Committed quantum boundary payload requires resourceIds");
            }
            if (payload.records().isEmpty()) {
                throw new IllegalStateException("Committed quantum boundary payload requires records");
            }
            QuantumEntanglementBoundary boundary =
                    new QuantumEntanglementBoundary(payload.serviceId(), payload.resourceIds());
            for (RecordPayload record : payload.records()) {
                boundary.addRecord(record.toRecord());
            }
            boundary.validateConservation();
            boundary.markCommitted();
            TransactionContext context = TransactionContext.reversal(
                    payload.transactionId() + ":reversal",
                    payload.traceId(),
                    payload.resourceIds(),
                    journalStore);
            return new DecodedBoundary(context, boundary);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot deserialize committed quantum boundary", e);
        }
    }

    public record DecodedBoundary(TransactionContext context,
                                  QuantumEntanglementBoundary boundary) {
    }

    private record Payload(int version,
                           String transactionId,
                           String traceId,
                           String serviceId,
                           List<String> resourceIds,
                           List<RecordPayload> records) {
        @JsonCreator
        private Payload(@JsonProperty("version") int version,
                        @JsonProperty("transactionId") String transactionId,
                        @JsonProperty("traceId") String traceId,
                        @JsonProperty("serviceId") String serviceId,
                        @JsonProperty("resourceIds") List<String> resourceIds,
                        @JsonProperty("records") List<RecordPayload> records) {
            this.version = version;
            this.transactionId = transactionId;
            this.traceId = traceId;
            this.serviceId = serviceId;
            this.resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
            this.records = records == null ? List.of() : List.copyOf(records);
        }
    }

    private record RecordPayload(String groupId,
                                 String attributeId,
                                 String objectId,
                                 BigDecimal beforeValue,
                                 BigDecimal deltaValue,
                                 BigDecimal afterValue) {
        @JsonCreator
        private RecordPayload(@JsonProperty("groupId") String groupId,
                              @JsonProperty("attributeId") String attributeId,
                              @JsonProperty("objectId") String objectId,
                              @JsonProperty("beforeValue") BigDecimal beforeValue,
                              @JsonProperty("deltaValue") BigDecimal deltaValue,
                              @JsonProperty("afterValue") BigDecimal afterValue) {
            this.groupId = groupId;
            this.attributeId = attributeId;
            this.objectId = objectId;
            this.beforeValue = beforeValue;
            this.deltaValue = deltaValue;
            this.afterValue = afterValue;
        }

        private static RecordPayload from(QuantumConservationRecord record) {
            return new RecordPayload(
                    record.getGroupId(),
                    record.getAttributeId(),
                    record.getObjectId(),
                    record.getBeforeValue(),
                    record.getDeltaValue(),
                    record.getAfterValue());
        }

        private QuantumConservationRecord toRecord() {
            return new QuantumConservationRecord(
                    groupId, attributeId, objectId, beforeValue, deltaValue, afterValue);
        }
    }
}
