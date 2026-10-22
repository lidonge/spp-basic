package com.servpp.multiexpr;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2024-07-30@version 1.0
 */
public class ReflectTool {
    public static boolean DEBUG = false;
    public static Object getQualifiedOrSimpleValue(List<Object> parents, Object currentObj, String varName) {
        Object ret = getVarValue(parents, currentObj, varName);
        if(DEBUG){
            if(ret != getVarValue(null,currentObj,varName))
                System.err.println("====look up in parent: " + varName + ", parent is " +parents);
        }
        return ret;
    }

    private static Object getVarValue(List<Object> parents, Object currentObj, String varName) {
        Object ret = null;
        int count = parents == null ? -1: parents.size() - 1;
        do {
            ret = getVarValue(currentObj, varName);
            if (count < 0)
                break;
            currentObj = parents.get(count);
            count--;
        } while (ret == null);
        return ret;
    }

    private static Object getVarValue(Object currentObj, String varName) {
        Object ret = null;
        if (varName.indexOf('.') != -1) {
            ret = getQualifiedValue(currentObj, varName);
        } else if (currentObj instanceof Map) {
            ret = ((Map<?, ?>) currentObj).get(varName);
        } else {
            try {
                Field field = currentObj.getClass().getDeclaredField(varName);
                field.setAccessible(true);
                ret = field.get(currentObj);
            } catch (NoSuchFieldException e) {
                ret = invockNamedMethod(currentObj, varName, ret);
            } catch (IllegalAccessException e) {
            }
        }
        return ret;
    }

    private static Object invockNamedMethod(Object root, String varName, Object ret) {
        String vname = capitalizeFirstLetter(varName);
        String getName = "get" + vname;
        String isName = "is" + vname;
        Method method = null;
        try {
            method = root.getClass().getMethod(getName, null);
        } catch (NoSuchMethodException ex) {
            try {
                method = root.getClass().getMethod(isName, null);
            } catch (NoSuchMethodException exc) {
            }
        }
        if (method != null) {
            try {
                ret = method.invoke(root, null);
            } catch (IllegalAccessException ex) {
            } catch (InvocationTargetException ex) {
            }
        }
        return ret;
    }

    public static Object getQualifiedValue(Object obj, String qualifiedName) {
        String[] fieldNames = qualifiedName.split("\\.");
        Object currentObject = obj;

        for (String fieldName : fieldNames) {
            currentObject = getVarValue(currentObject, fieldName);

            if (currentObject == null) {
                return null;
            }
        }

        return currentObject;
    }

    private static String capitalizeFirstLetter(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

}
