package com.servpp.lang.models.app;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public class ScopeDefine {
    boolean isLocal;
    AppHeader in;
    AppHeader out;
    List<ScopeItem> scopeItems = new ArrayList<>();

    public boolean isLocal() {
        return isLocal;
    }

    public void setLocal(boolean local) {
        isLocal = local;
    }

    public void addScopeItem(ScopeItem scopeItem){
        scopeItem.setScopeDefine(this);
        scopeItems.add(scopeItem);
    }
    public List<ScopeItem> getScopeItems() {
        return scopeItems;
    }

    public AppHeader getIn() {
        return in;
    }

    public void setIn(AppHeader in) {
        this.in = in;
    }

    public AppHeader getOut() {
        return out;
    }

    public void setOut(AppHeader out) {
        this.out = out;
    }

    @Override
    public String toString() {
        return "ScopeBody{" +
                "isLocal=" + isLocal +
                ", in=" + in +
                ", out=" + out +
                ", scopeItems=" + scopeItems +
                '}';
    }
}
