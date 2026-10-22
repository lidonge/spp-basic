package com.servpp.service.transaction;

public record TccBranchRecord(String branchId, String globalSerialNumber, String resourceId,
                              String actionName, TccBranchStatus status, String message,
                              String updatedAt, String recoveryPayload) {
    public TccBranchRecord(String branchId, String globalSerialNumber, String resourceId,
                           String actionName, TccBranchStatus status, String message,
                           String updatedAt) {
        this(branchId, globalSerialNumber, resourceId, actionName, status, message, updatedAt, null);
    }
}
