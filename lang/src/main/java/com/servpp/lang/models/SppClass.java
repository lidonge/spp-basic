package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.IScenarioHandler;
import com.servpp.lang.models.app.INamedObject;
import com.servpp.lang.models.app.SppFieldReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class SppClass extends SppCompilationUnit implements INamedObject {
    private boolean systemBuiltin;
    public boolean isSystemBuiltin() { return systemBuiltin; }
    public void setSystemBuiltin(boolean value) { systemBuiltin = value; }
    private boolean storageOwnershipResolved;
    public boolean isStorageOwnershipResolved() { return storageOwnershipResolved; }
    public void setStorageOwnershipResolved(boolean resolved) { storageOwnershipResolved = resolved; }
    private String referenceSource;
    private com.servpp.lang.general.IConstance.CompilationUnitType referenceSourceKind;
    public com.servpp.lang.general.IConstance.CompilationUnitType getReferenceSourceKind() { return referenceSourceKind; }
    public void setReferenceSourceKind(com.servpp.lang.general.IConstance.CompilationUnitType kind) { referenceSourceKind = kind; }
    public String getReferenceSource() { return referenceSource; }
    public void setReferenceSource(String source) { referenceSource = source; }
    private java.util.List<String> identityPaths = java.util.List.of();
    public java.util.List<String> getIdentityPaths() { return identityPaths; }
    public void setIdentityPaths(java.util.List<String> paths) { identityPaths = java.util.List.copyOf(paths); }


    Map<String, SppField> sppFieldMap = new HashMap<>();
    List<SppField> sppFieldList = new ArrayList<>();

    private IScenarioHandler.CurrentCall currentCall;
    private SppField instance;

    public SppClass(String name) {
        super(name);
    }

    public String getQualifiedInstName() {
        return instance.getQualifiedInstName();
    }

    public SppClass instance(SppField inst, SppDomain sppDomain){
        return instance(inst, sppDomain, new HashMap<>());
    }

    private SppClass instance(SppField inst, SppDomain sppDomain, Map<String, SppClass> visiting){
        SppClass existing = visiting.get(getName());
        if (existing != null) {
            return existing;
        }
        SppClass ret = new SppClass(getName());
        visiting.put(getName(), ret);
        ret.copyFrom(this);
        ret.instance = inst;
        ret.sppFieldMap = new HashMap<>();
        ret.sppFieldList = new ArrayList<>();
        for(SppField field:sppFieldList){
            try {
                SppField newField = (SppField) field.clone();
                ret.addField(newField);

                SppFieldReference sppFieldReference = sppDomain.getSppFieldReference(field);
                if(sppFieldReference != null){
                    sppFieldReference.setSppField(newField);
                }
                if(newField.getType() instanceof SppClass){
                    SppClass cls = (SppClass) newField.getType();
                    newField.setType(cls.instance(newField, sppDomain, visiting));
                }
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
        visiting.remove(getName());
        return ret;
    }
    public SppClass(String name, IConstance.CompilationUnitType type) {
        super(name);
        this.type = type;
    }

    public List<SppField> getSppFieldList() {
        return sppFieldList;
    }

    public IScenarioHandler.CurrentCall getCurrentCall() {
        return currentCall;
    }

    public void setCurrentCall(IScenarioHandler.CurrentCall currentCall) {
        this.currentCall = currentCall;
    }

    public Map<String, SppField> getSppFieldMap() {
        return sppFieldMap;
    }

    public String addField(SppField field){
//        if("QueryAccount".equals(getName()))
//            System.out.println(getName() +" add field "+ field.getName());
        String fieldName = field.getName();
        SppField theField = sppFieldMap.get(fieldName);
        if(  theField != null ) {
            if(type == IConstance.CompilationUnitType.scenario && theField.getType() == field.getType()){
                return null;
            }else
                return "Duplicate Field " + fieldName + " in class " + getName();
        }
        field.setIndex(sppFieldMap.size());
        sppFieldMap.put(fieldName,field);
        sppFieldList.add(field);
        field.setParent(this);
        return null;
    }

    public SppField getField(String fieldName) {
        SppField field = sppFieldMap.get(fieldName);
        if (field == null) for (SppField candidate : sppFieldList)
            if (candidate.isDefaultParticipantName() && com.servpp.lang.general.NameUtil.participantName(candidate.getType().getName(),candidate.getArrayDimension()).equals(fieldName)) return candidate;
        return field;
    }

    public SppField getField(int index) {
        return sppFieldList.get(index);
    }

    @Override
    public String toString() {
        return "SppClass{" +
                "name='" + getName() + '\'' +
                ", type=" + type +
                ", sppFieldList=" + sppFieldList +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        boolean ret = super.equals(obj);

        if (obj instanceof SppClass){
            SppClass cls = (SppClass) obj;

            ret |= getName().equals(cls.getName());
        }
        return ret;
    }

    public void copyFrom(SppCompilationUnit sppCompilationUnit) {
        super.copyFrom(sppCompilationUnit);
        SppClass cls = (SppClass) sppCompilationUnit;
        systemBuiltin = cls.systemBuiltin;
        storageOwnershipResolved = cls.storageOwnershipResolved;
        sppFieldMap = cls.sppFieldMap;
        sppFieldList = cls.sppFieldList;
        currentCall = cls.currentCall;
    }

//    public String addMethod(SppMethod sppMethod){
//        String sppMethodName = sppMethod.getName();
//        if( methodMap.get(sppMethodName) != null)
//            return "Duplicate method " + sppMethodName + " in class " + name;
//        methodMap.put(sppMethodName,sppMethod);
//        return null;
//    }
//
//    public SppMethod getMethod(String methodName){
//        return methodMap.get(methodName);
//    }
}
