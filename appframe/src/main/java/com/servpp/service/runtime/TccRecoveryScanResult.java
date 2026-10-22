package com.servpp.service.runtime;

import com.servpp.service.transaction.TccBranchRecord;

public record TccRecoveryScanResult(TccRecoveryCandidate candidate,
                                    TccRecoveryDecision decision,
                                    TccBranchRecord recoveredBranch,
                                    String errorMessage) {
}
