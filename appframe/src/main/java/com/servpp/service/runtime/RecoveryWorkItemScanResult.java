package com.servpp.service.runtime;

import com.servpp.service.transaction.SagaLogEntry;
import com.servpp.service.transaction.SagaRecoveryScanResult;
import com.servpp.service.transaction.TccBranchRecord;

public record RecoveryWorkItemScanResult(RecoveryWorkItem workItem,
                                         RecoveryWorkItemDecision decision,
                                         TccBranchRecord recoveredBranch,
                                         SagaLogEntry recoveredLog,
                                         SagaRecoveryScanResult nativeSagaRecoveryResult,
                                         String errorMessage) {
    public RecoveryWorkItemScanResult(RecoveryWorkItem workItem,
                                      RecoveryWorkItemDecision decision,
                                      TccBranchRecord recoveredBranch,
                                      SagaLogEntry recoveredLog,
                                      String errorMessage) {
        this(workItem, decision, recoveredBranch, recoveredLog, null, errorMessage);
    }
}
