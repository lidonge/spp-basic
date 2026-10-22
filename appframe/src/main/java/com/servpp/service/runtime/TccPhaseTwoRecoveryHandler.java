package com.servpp.service.runtime;

import com.servpp.service.transaction.TccBranchStatus;

@FunctionalInterface
public interface TccPhaseTwoRecoveryHandler {
    void recover(String branchId, TccBranchStatus targetStatus);
}
