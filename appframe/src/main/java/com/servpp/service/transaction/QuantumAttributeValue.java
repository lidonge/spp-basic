package com.servpp.service.transaction;

import java.math.BigDecimal;

public record QuantumAttributeValue(String attributeId, String objectId, String groupId, String boundaryServiceId,
                                    String transactionId, BigDecimal currentValue) {
}
