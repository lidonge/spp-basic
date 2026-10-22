package com.servpp.service.transaction;

import com.servpp.service.scenario.IExecutor;
import java.util.List;

/** A source-planned stage already owns its resource transaction and preparation barriers. */
public interface SourceSagaStage extends IExecutor {
    TransactionMode getStageMode();
    List<IExecutor> getStageCalls();
    default boolean isIndependentBoundary() { return false; }
    default String getCommittedQuantumBoundary() { return ""; }
}
