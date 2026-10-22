package com.servpp.lang.models;
import com.servpp.lang.general.IConstance;
import java.util.List;
/** Both branches are visible to resource and service checks. */
public final class TryBlock extends ExecuteBlock {
    private final String failureType, failureTarget;
    private SppCompilationUnit failureModel;
    public SppCompilationUnit getFailureModel(){return failureModel;}
    public void setFailureModel(SppCompilationUnit model){failureModel=model;}
    public TryBlock(String failureType,String failureTarget) {
        this.failureType=failureType;this.failureTarget=failureTarget;setScenarioType(IConstance.ScenarioType.serial);
    }
    public String getFailureType(){return failureType;}
    public String getFailureTarget(){return failureTarget;}
    public List<IServiceBodyStatement> getTryStatements(){return branch(0);}
    public List<IServiceBodyStatement> getCatchStatements(){return branch(1);}
    private List<IServiceBodyStatement> branch(int index) {
        if (getSppServiceCallList() == null || getSppServiceCallList().size() <= index) return List.of();
        var statements = ((ExecuteBlock) getSppServiceCallList().get(index)).getExecuteCallList();
        return statements == null ? List.of() : statements;
    }
}
