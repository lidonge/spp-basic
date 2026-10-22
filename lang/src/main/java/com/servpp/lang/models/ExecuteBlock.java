package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.NameUtil;

import java.util.List;

/**
 * @author lidong@date 2023-11-18@version 1.0
 */
public class ExecuteBlock extends ServiceBaseBody implements IServiceBodyStatement{
    private IConstance.ScenarioType scenarioType;
    public List<IServiceBodyStatement> getExecuteCall() {
        return getSppServiceCallList();
    }

    public boolean getHasExecuteCall() {
        return getSppServiceCallList() != null && !getSppServiceCallList().isEmpty();
    }

    public List<IServiceBodyStatement> getExecuteCallList() {
        return getSppServiceCallList();
    }

    public List<ExecuteBlock> getExecuteGroup() {
        return List.of(this);
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

    public IConstance.ScenarioType getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(IConstance.ScenarioType scenarioType) {
        this.scenarioType = scenarioType;
    }

    public String getUpperType(){
        return NameUtil.firstToLowerCase(scenarioType.name(),false);
    }

    public String getParentUpperType() {
        if (getParent() instanceof ExecuteBlock executeBlock) {
            return executeBlock.getUpperType();
        }
        return getUpperType();
    }
    @Override
    public String toString() {
        return "ExecuteBlock{" +
                "scenarioType=" + scenarioType +
                ", sppServiceCallList=" + sppServiceCallList +
                '}';
    }
}
