package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.util.Objects;

public class QuantumConservationRecord {
    /** Reserved protocol group for bounded quantum state that is not a conserved quantity. */
    public static final String STATE_GROUP = "$state";

    public static boolean isConservedGroup(String groupId) { return !STATE_GROUP.equals(groupId); }

    private final String groupId;
    private final String attributeId;
    private final String objectId;
    private final BigDecimal beforeValue;
    private final BigDecimal deltaValue;
    private final BigDecimal afterValue;
    private final BigDecimal minimum;
    private final BigDecimal maximum;

    public QuantumConservationRecord(String groupId, String attributeId, BigDecimal beforeValue,
                                     BigDecimal deltaValue, BigDecimal afterValue) {
        this(groupId, attributeId, JdbcQuantumJournalStore.DEFAULT_OBJECT_ID, beforeValue, deltaValue, afterValue);
    }

    public QuantumConservationRecord(String groupId, String attributeId, String objectId, BigDecimal beforeValue,
                                     BigDecimal deltaValue, BigDecimal afterValue) {
        this(groupId, attributeId, objectId, beforeValue, deltaValue, afterValue, null, null);
    }

    public QuantumConservationRecord(String groupId, String attributeId, String objectId, BigDecimal beforeValue,
                                     BigDecimal deltaValue, BigDecimal afterValue, BigDecimal minimum, BigDecimal maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.attributeId = Objects.requireNonNull(attributeId, "attributeId");
        this.objectId = objectId == null || objectId.isBlank()
                ? JdbcQuantumJournalStore.DEFAULT_OBJECT_ID
                : objectId.trim();
        this.beforeValue = Objects.requireNonNull(beforeValue, "beforeValue");
        this.deltaValue = Objects.requireNonNull(deltaValue, "deltaValue");
        this.afterValue = Objects.requireNonNull(afterValue, "afterValue");
    }

    public BigDecimal getMinimum() { return minimum; }
    public BigDecimal getMaximum() { return maximum; }

    public String getGroupId() {
        return groupId;
    }

    public String getAttributeId() {
        return attributeId;
    }

    public String getObjectId() {
        return objectId;
    }

    public BigDecimal getBeforeValue() {
        return beforeValue;
    }

    public BigDecimal getDeltaValue() {
        return deltaValue;
    }

    public BigDecimal getAfterValue() {
        return afterValue;
    }
}
