package com.servpp.lang.models;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-02@version 1.0
 */
public class ServiceBaseBody{
    private ServiceBaseBody parent;
    protected List<IServiceBodyStatement> sppServiceCallList;

    public ServiceBaseBody getParent() {
        return parent;
    }

    public void setParent(ServiceBaseBody parent) {
        this.parent = parent;
    }

    public List<IServiceBodyStatement> getSppServiceCallList() {
        return sppServiceCallList;
    }
    public SppLocalVar getQualifieField(String qualifiedName){
        return parent.getQualifieField(qualifiedName);
    }

    public String checkQualifiedName(String qualifiedName){
        return parent.checkQualifiedName(qualifiedName);
    }

    public void addServiceCall(IServiceBodyStatement sppServiceCall) {
        if(sppServiceCallList == null)
            sppServiceCallList = new ArrayList<>();
        sppServiceCallList.add(sppServiceCall);
        sppServiceCall.setParent(this);
    }

    public IServiceBodyStatement getLastServiceCall() {
        return sppServiceCallList.get(sppServiceCallList.size() - 1);
    }
}
