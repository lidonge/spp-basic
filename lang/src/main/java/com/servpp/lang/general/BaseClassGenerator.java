package com.servpp.lang.general;

import com.servpp.lang.models.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lidong@date 2023-11-13@version 1.0
 */
public class BaseClassGenerator {
    private void dealMaps(SppDomain sppDomain) {
        Map<String, SppCompilationUnit> sppClassMap = sppDomain.getMapsOfClass();
        for (SppCompilationUnit sppClass : sppClassMap.values()) {
            if (sppClass.getType() == IConstance.CompilationUnitType.rolemapper) {
//                System.out.println(sppClass);
                Map<String, String> entityToRole = new HashMap<>();
                SppRoleMapper sppRoleMapper = (SppRoleMapper) sppClass;
                for (SppLocalVar var : sppRoleMapper.getSppFieldList()) {
                    SppRoleField roleField = (SppRoleField) var;
                    entityToRole.put(roleField.getEntityName(), roleField.getName());
                }
                takeAll(sppRoleMapper, entityToRole);
            }
        }
    }

    private void takeAll(SppRoleMapper sppRoleMapper, Map<String, String> entityToRole) {
        SppDomain.takeAll(sppRoleMapper,entityToRole);
    }
}
