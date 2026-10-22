package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.models.app.expr.IExpression;
import java.util.List;

/** A business decision chooses one branch; both branches remain visible to static checks. */
public final class ConditionalBlock extends ExecuteBlock {
    private final IExpression condition;

    public ConditionalBlock(IExpression condition) {
        this.condition = condition;
        setScenarioType(IConstance.ScenarioType.serial);
    }

    public IExpression getCondition() { return condition; }

    public List<IServiceBodyStatement> getThenStatements() { return branch(0); }

    public List<IServiceBodyStatement> getElseStatements() { return branch(1); }

    private List<IServiceBodyStatement> branch(int index) {
        if (getSppServiceCallList() == null || getSppServiceCallList().size() <= index) {
            return List.of();
        }
        if (getSppServiceCallList().get(index) instanceof ConditionalBlock conditional) {
            return List.of(conditional);
        }
        ExecuteBlock block = (ExecuteBlock) getSppServiceCallList().get(index);
        return block.getExecuteCallList() == null ? List.of() : block.getExecuteCallList();
    }
}
