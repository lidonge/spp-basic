package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;

/**
 * @author lidong@date 2023-11-17@version 1.0
 */
public class SppScenario extends SppService {
    private boolean readonly;
    public boolean isReadonly() { return readonly; }
    public void setReadonly(boolean readonly) { this.readonly = readonly; }

    private boolean usesSystemResult;
    public boolean isUsesSystemResult() { return usesSystemResult; }
    public void setUsesSystemResult(boolean value) { usesSystemResult = value; }
    private IConstance.ScenarioType scenarioType;
    public SppScenario(String objName) {
        super(objName);
    }

    public IConstance.ScenarioType getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(IConstance.ScenarioType scenarioType) {
        this.scenarioType = scenarioType;
    }
    @Override
    public void copyFrom(SppCompilationUnit unit) {
        super.copyFrom(unit);
        if (unit instanceof SppScenario scenario) {
            readonly = scenario.readonly;
            usesSystemResult = scenario.usesSystemResult;
            scenarioType = scenario.scenarioType;
        }
    }

}
