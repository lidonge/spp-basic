package com.servpp.lang.models;

import java.util.List;

/**
 * @author lidong@date 2023-11-02@version 1.0
 */
public class TransactionBlock extends ServiceBaseBody implements IServiceBodyStatement {
    // Resolved from APP service bindings, never from SPP syntax.
    private boolean parallelOrdinaryUpdates;
    public boolean isParallelOrdinaryUpdates() { return parallelOrdinaryUpdates; }
    public void setParallelOrdinaryUpdates(boolean value) { parallelOrdinaryUpdates = value; }
    private String declaredMode;
    private boolean sourcePlan;
    public boolean isSourcePlan() { return sourcePlan; }
    public void setSourcePlan(boolean value) { sourcePlan = value; }
    private String propagation = "REQUIRED";

    public String getPropagation() { return propagation; }
    public void setPropagation(String propagation) { this.propagation = propagation; }

    public String getDeclaredMode() {
        return declaredMode;
    }

    public void setDeclaredMode(String declaredMode) {
        this.declaredMode = declaredMode == null ? null : declaredMode.toUpperCase(java.util.Locale.ROOT);
    }

    public Object getExecuteCall() {
        return List.of();
    }

    public boolean getHasExecuteCall() {
        return false;
    }

    public List<IServiceBodyStatement> getExecuteCallList() {
        return List.of();
    }

    public List<ExecuteBlock> getExecuteGroup() {
        return List.of();
    }

    public List<IServiceBodyStatement> getTransactionCall() {
        return getSppServiceCallList();
    }

    public boolean getHasTransactionCall() {
        return getSppServiceCallList() != null && !getSppServiceCallList().isEmpty();
    }

    public List<IServiceBodyStatement> getTransactionCallList() {
        return getSppServiceCallList();
    }

    public List<TransactionBlock> getTransactionGroup() {
        return List.of(this);
    }

    public String getParentUpperType() {
        if (getParent() instanceof ExecuteBlock executeBlock) {
            return executeBlock.getUpperType();
        }
        return "";
    }
}
