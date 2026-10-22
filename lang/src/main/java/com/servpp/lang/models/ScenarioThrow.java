package com.servpp.lang.models;
/** Rethrows the original failure associated with a catch participant. */
public final class ScenarioThrow implements IServiceBodyStatement {
    private ServiceBaseBody parent;
    private final String target;
    public ScenarioThrow(String target){this.target=target;}
    public String getTarget(){return target;}
    public ServiceBaseBody getParent(){return parent;}
    public void setParent(ServiceBaseBody parent){this.parent=parent;}
}
