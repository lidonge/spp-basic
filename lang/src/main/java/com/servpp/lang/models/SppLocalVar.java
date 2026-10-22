package com.servpp.lang.models;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class SppLocalVar extends SppParameter{
    int index;

    boolean isQuantum;
    private boolean contextParameter;
    private boolean queryKey;
    public boolean isQueryKey() { return queryKey; }
    public void setQueryKey(boolean value) { queryKey = value; }

    protected SppLocalVar(SppCompilationUnit type, String name, int arrayDimension, int index, boolean isQuantum) {
        super(type, name, arrayDimension);
        this.index = index;
        this.isQuantum = isQuantum;
    }

    public SppLocalVar(SppCompilationUnit type, String name, int index, boolean isQuantum) {
        super(type, name);
        this.index = index;
        this.isQuantum = isQuantum;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        SppLocalVar localVar = new SppLocalVar(type,name,getArrayDimension(),index,isQuantum);
        localVar.contextParameter = contextParameter;
        localVar.queryKey = queryKey;
        copyNumericConfiguration(localVar);
        return localVar;
    }

    public SppLocalVar(SppCompilationUnit type, String name) {
        super(type, name);
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean isQuantum() {
        return isQuantum;
    }

    public void setQuantum(boolean quantum) {
        isQuantum = quantum;
    }

    public boolean isContextParameter() {
        return contextParameter;
    }

    public void setContextParameter(boolean contextParameter) {
        this.contextParameter = contextParameter;
    }

    @Override
    public String toString() {
        return "SppLocalVar{" +
                "type=" + type.getName() +
                ", name='" + name + '\'' +
                '}';
    }

}
