package com.servpp.lang.models;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class SppProject {
    Map<String, SppDomain> sppDomainMap = new HashMap<>();

    public SppDomain addDomain(String name){
        SppDomain sppDomain = sppDomainMap.get(name);
        if(sppDomain == null) {
            sppDomain = new SppDomain(name);
            sppDomain.setProject(this);
            sppDomainMap.put(name, sppDomain);
        }
        return sppDomain;
    }

    public Map<String, SppDomain> getSppDomainMap() {
        return sppDomainMap;
    }

    public SppDomain getDomain(String name){
        return sppDomainMap.get(name);
    }
}
