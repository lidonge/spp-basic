package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;
import com.servpp.lang.models.SppService;

import java.util.*;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public class AppScope extends Annotation {
    List<ScopeDefine> scopelist = new ArrayList<>();

    Map<String, SppService> serviceMap = new HashMap<>();

    public void _addLocalScope(ScopeDefine scopeItem){
        scopeItem.setLocal(true);
        scopelist.add(scopeItem);
    }
    public void _addRemoteScope(ScopeDefine scopeItem){
        scopeItem.setLocal(false);
        scopelist.add(scopeItem);
    }
    public String addLocalScope(ScopeDefine scopeItem){
        scopeItem.setLocal(true);
        return addScope(scopelist,scopeItem);
    }

    public String addRemoteScope(ScopeDefine scopeItem){
        scopeItem.setLocal(false);
        return addScope(scopelist,scopeItem);
    }

    public String addAtomicExtends(ScopeDefine scopeItem, ScopeItem atomicExtends){
        String funcName = atomicExtends.getService().getFuncName();
        if(serviceMap.get(funcName) != null){
            return "Duplicate service define "+ funcName;
        }
        serviceMap.put(funcName,atomicExtends.getService());
        scopeItem.addScopeItem(atomicExtends);
        return null;
    }
    private String addScope(List<ScopeDefine> itemList, ScopeDefine scopeItem){
        int exist = Arrays.binarySearch((ScopeDefine[])itemList.toArray(), scopeItem, new Comparator<ScopeDefine>() {
            @Override
            public int compare(ScopeDefine o1, ScopeDefine o2) {
                boolean eq = o1.in.getName().equals(o2.in.getName()) && o1.out.getName().equals(o2.out.getName());
                return eq ? 0 : 1;
            }
        });
        if(exist != -1)
            return "Duplicate scope (in = " + scopeItem.getIn().getName() + ", out = " + scopeItem.getOut().getName()+")";
        itemList.add(scopeItem);
        return null;
    }

    public List<ScopeDefine> getScopelist() {
        return scopelist;
    }

    @Override
    public String toString() {
        return "AppScope{" +
                "scopelist=" + scopelist +
                ", serviceMap=" + serviceMap +
                '}';
    }
}
