package com.servpp.service.runtime;

import com.servpp.service.transaction.SagaLogEntry;

public record RemoteCompensationRecoveryScanResult(RemoteCompensationRecoveryCandidate candidate,
                                                   RemoteCompensationRecoveryDecision decision,
                                                   SagaLogEntry recoveredLog,
                                                   String errorMessage) {
}
