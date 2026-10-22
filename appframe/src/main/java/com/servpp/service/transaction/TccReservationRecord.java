package com.servpp.service.transaction;

import java.math.BigDecimal;

public record TccReservationRecord(String reservationId, String branchId, String globalSerialNumber,
                                   String transactionId, String resourceId, String actionName,
                                   String sourceAttributeId, String reservationAttributeId,
                                   BigDecimal quantity, TccReservationStatus status,
                                   String message, String updatedAt, String expiresAt,
                                   String objectId) {
    public TccReservationRecord(String reservationId, String branchId, String globalSerialNumber,
                                String transactionId, String resourceId, String actionName,
                                String sourceAttributeId, String reservationAttributeId,
                                BigDecimal quantity, TccReservationStatus status,
                                String message, String updatedAt, String expiresAt) {
        this(reservationId, branchId, globalSerialNumber, transactionId, resourceId, actionName,
                sourceAttributeId, reservationAttributeId, quantity, status, message, updatedAt, expiresAt, null);
    }

    public TccReservationRecord(String reservationId, String globalSerialNumber, String transactionId,
                                String resourceId, String actionName, String sourceAttributeId,
                                String reservationAttributeId, BigDecimal quantity,
                                TccReservationStatus status, String message, String updatedAt) {
        this(reservationId, null, globalSerialNumber, transactionId, resourceId, actionName,
                sourceAttributeId, reservationAttributeId, quantity, status, message, updatedAt, null, null);
    }
}
