package com.servpp.service.runtime;

import java.util.List;

public class TransactionReversalRule {
    public String ruleName;
    public String ruleKind;
    public String sourceServiceName;
    public String targetServiceName;
    public String phaseName;
    public int targetIndex;
    public boolean targetDeclared;
    public boolean lifecycleComplete;
    public String readiness;
    public List<String> readyReasons;
    public List<String> missingReasons;
    public List<String> actions;
    public String reversalPath;
    public String executionContract;

    public TransactionReversalRule() {
    }

    public TransactionReversalRule(String ruleKind,
                                   String sourceServiceName,
                                   String targetServiceName,
                                   String phaseName,
                                   int targetIndex,
                                   boolean targetDeclared,
                                   boolean lifecycleComplete,
                                   String readiness) {
        this(ruleKind, sourceServiceName, targetServiceName, phaseName, targetIndex,
                targetDeclared, lifecycleComplete, readiness, List.of(), List.of(),
                List.of(), null, null);
    }

    public TransactionReversalRule(String ruleKind,
                                   String sourceServiceName,
                                   String targetServiceName,
                                   String phaseName,
                                   int targetIndex,
                                   boolean targetDeclared,
                                   boolean lifecycleComplete,
                                   String readiness,
                                   List<String> readyReasons,
                                   List<String> missingReasons,
                                   List<String> actions,
                                   String reversalPath,
                                   String executionContract) {
        this.ruleKind = ruleKind;
        this.sourceServiceName = sourceServiceName;
        this.targetServiceName = targetServiceName;
        this.phaseName = phaseName;
        this.targetIndex = targetIndex;
        this.targetDeclared = targetDeclared;
        this.lifecycleComplete = lifecycleComplete;
        this.readiness = readiness;
        this.readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        this.missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        this.reversalPath = reversalPath == null
                ? sourceServiceName + "->" + targetServiceName + ":" + phaseName
                : reversalPath;
        this.executionContract = executionContract;
        this.ruleName = this.reversalPath;
    }
}
