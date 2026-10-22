package com.servpp.lang.models;

import com.servpp.lang.models.app.IQualifiedNameUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class ServiceBody extends ServiceBaseBody implements IQualifiedNameUtil {
    SppClass realm;
    Map<String, SppLocalVar> sppLocalVarHashMap = new HashMap<>();
    List<SppLocalVar> sppLocalVarList = new ArrayList<>();

    public ServiceBody(SppClass realm) {
        this.realm = realm;
    }

    public List<SppLocalVar> getSppLocalVarList() {
        return sppLocalVarList;
    }

    public List<IServiceBodyStatement> getTopLevelServiceCallList() {
        return getSppServiceCallList();
    }

    public Map<String, SppLocalVar> getSppLocalVarHashMap() {
        return sppLocalVarHashMap;
    }

    public SppClass getRealm() {
        return realm;
    }

    public void setRealm(SppClass realm) {
        this.realm = realm;
    }

    public SppLocalVar getQualifieField(String qualifiedName){
        return getQualifieField(com.servpp.lang.general.NameUtil.normalizeParticipantReference(qualifiedName),sppLocalVarHashMap);
    }

    public String checkQualifiedName(String qualifiedName){
        String[] parts = com.servpp.lang.general.NameUtil.normalizeParticipantReference(qualifiedName).split("\\.");
        SppLocalVar inst = sppLocalVarHashMap.get(parts[0]);
        if(inst == null)
            return "Var " + parts[0] + " not found!";
        if (parts.length == 1) { return null; }
        if (!(inst.getType() instanceof SppClass cls)) { return "Var " + parts[0] + " is not an object"; }
        for(int i = 1;i<parts.length;i++){
            SppField field = cls.getField(parts[i]);
            if(field == null){
                return "Field " + parts[i] + " not found!";
            }
            if (i < parts.length - 1) {
                cls = (SppClass) field.getType();
            }
        }
        return null;
    }
    public String addLocalVar(SppLocalVar inst){
        String name = inst.getName();
        if( sppLocalVarHashMap.get(name) != null)
            return "Duplicate Instance " + name;
        sppLocalVarHashMap.put(name,inst);
        sppLocalVarList.add(inst);
        return null;
    }
    public SppLocalVar getLocalVar(String methodName){
        return sppLocalVarHashMap.get(com.servpp.lang.general.NameUtil.normalizeParticipantReference(methodName));
    }
    public SppLocalVar getLocalVar(int index){
        return sppLocalVarList.get(index);
    }

    @Override
    public String toString() {
        return "ServiceBody{" +
                "realm=" + realm.getName() +
                ", sppLocalVarList=" + sppLocalVarList +
                ", sppServiceCallList=" + sppServiceCallList +
                '}';
    }
}
