package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;

/** The failed boundary and the subsequent business disposition have distinct scopes. */
public final class TransactionFailureBlock extends ExecuteBlock {
    private String contractTarget;
    public String getContractTarget() { return contractTarget; }
    public void setContractTarget(String value) { contractTarget = value; }
    public TransactionFailureBlock() { setScenarioType(IConstance.ScenarioType.serial); }
    public TransactionBlock getTransaction() { return (TransactionBlock) getExecuteCallList().get(0); }
    public ExecuteBlock getFailureHandler() { return (ExecuteBlock) getExecuteCallList().get(1); }
}
