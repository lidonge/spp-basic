package com.servpp.lang.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class SppServiceCall extends ServiceBaseBody implements IServiceBodyStatement{
    SppService callee;
    private final List<QuantumCallBound> quantumBounds = new ArrayList<>();
    public List<QuantumCallBound> getQuantumBounds() { return quantumBounds; }
    private boolean implicitArguments;
    private int invocationOrdinal = -1;
    public int getInvocationOrdinal() { return invocationOrdinal; }
    public void setInvocationOrdinal(int value) { invocationOrdinal=value; }
    private boolean breaksTransaction;
    public boolean isBreaksTransaction() { return breaksTransaction; }
    public void setBreaksTransaction(boolean value) { breaksTransaction=value; }
    private String identityTarget;
    public boolean isImplicitArguments() { return implicitArguments; }
    public void setImplicitArguments(boolean value) { implicitArguments = value; }
    public String getIdentityTarget() { return identityTarget; }
    public void setIdentityTarget(String value) { identityTarget = value; }
    public java.util.List<IServiceBodyStatement> getGuardStatements() { return super.getSppServiceCallList(); }
    private String resultTarget;
    public String getResultTarget() { return resultTarget; }
    public void setResultTarget(String resultTarget) { this.resultTarget=resultTarget; }

    /**
     * For generate call parameter in mustache
     */
    Map<String, SppParameter> sppParameterHashMap = new HashMap<>();
    List<SppParameter> sppParameterList = new ArrayList<>();

    public List<SppParameter> getSppParameterList() {
        return sppParameterList;
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

    public Object getTransactionCall() {
        return List.of();
    }

    public boolean getHasTransactionCall() {
        return false;
    }

    public List<IServiceBodyStatement> getTransactionCallList() {
        return List.of();
    }

    public List<TransactionBlock> getTransactionGroup() {
        return List.of();
    }

    public String getParentUpperType() {
        if (getParent() instanceof ExecuteBlock executeBlock) {
            return executeBlock.getUpperType();
        }
        return "";
    }

    public String addParameter(SppParameter parameter){
        String name = parameter.getName();
        if( sppParameterHashMap.get(name) != null)
            return "Duplicate parameter " + name;
        sppParameterHashMap.put(name,parameter);
        sppParameterList.add(parameter);
        return null;
    }

    public SppParameter getParameter(String name){
        return sppParameterHashMap.get(name);
    }
    public SppService getCallee() {
        return callee;
    }

    public void setCallee(SppService callee) {
        this.callee = callee;
    }

    @Override
    public String toString() {
        return "SppServiceCall{" +
                "callee="+(callee == null ? "identify" : callee.getFuncName())+
                ", sppParameterList=" + sppParameterList +
                '}';
    }

    @Override
    public ServiceBaseBody getParent() {
        return super.getParent();
    }

    @Override
    public void setParent(ServiceBaseBody parent) {
        super.setParent(parent);
    }
}
