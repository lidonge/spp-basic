package com.servpp.multiexpr;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author lidong@date 2024-07-31@version 1.0
 */
public interface IEvaluatorEnvironment {
    String STRING_SUB = "str_sub";
    String STRING_INDEX_OF = "str_indexOf";
    String OBJ_GET_VAR = "obj_getVar";
    String OBJ_GET_CUR_VAR = "obj_getCurVar";
    String OBJ_PARENTS = "obj_parents";
    String OBJ_CURRENT_OBJ = "obj_currentObj";
    String OBJ_GET_CUR_OBJ = "obj_getCurObj";
    String OBJ_GET_PAR_OBJ = "obj_getParObj";
    String OBJ_NEW_LIST = "obj_newList";
    String OBJ_ADD_To_LIST = "obj_addToList";
    String OBJ_GET_FROM_LIST = "obj_getFromList";
    String OBJ_NEW_MAP = "obj_newMap";
    String OBJ_ADD_To_MAP = "obj_addToMap";
    String OBJ_GET_FROM_MAP = "obj_getFromMap";
    static class MyObject{
        public MyObject(Object value) {
            this.value = value;
        }

        Object value;

        public Object getValue() {
            return value;
        }

        @Override
        public String toString(){
            return value.toString();
        }

    }

    void partialCall(String ... params);
    void endPartialCall(String ... returns);

    default String getShortStackTrace(int deep){
        return "";
    }
    default String getStackTrace(int deep){
        return  "";
    }

    default void addDefault(){
        addFunction(STRING_SUB, args -> ((String) args[0]).substring(Integer.parseInt((String) args[1]), Integer.parseInt((String) args[2])));
        addFunction(STRING_INDEX_OF, args -> ((String) args[0]).indexOf((String) args[1]));
        addFunction("println", args -> {
            System.out.println(args[0]);
            return "";
        });
        addFunction(OBJ_GET_CUR_OBJ, args -> {
            return getVar(OBJ_CURRENT_OBJ);
        });
        addFunction(OBJ_GET_PAR_OBJ, args -> {
            List<Object> parents = (List<Object>) getVar(OBJ_PARENTS);
            return parents.size() == 0 ? getVar(OBJ_CURRENT_OBJ) : parents.get(parents.size() -1);
        });
        addFunction(OBJ_GET_VAR, args -> {
            List<Object> parents = (List<Object>) getVar(OBJ_PARENTS);
            Object currentObj = getVar(OBJ_CURRENT_OBJ);
//            ReflectTool.DEBUG = false;
            Object ret = ReflectTool.getQualifiedOrSimpleValue(parents, currentObj, (String) args[0]);
//            ReflectTool.DEBUG = true;
            return ret;
        });
        addFunction(OBJ_GET_CUR_VAR, args -> {
            Object currentObj = getVar(OBJ_CURRENT_OBJ);
            return ReflectTool.getQualifiedOrSimpleValue(null, currentObj, (String) args[0]);
        });
        addFunction(OBJ_NEW_LIST, args -> new ArrayList<Object>());
        addFunction(OBJ_ADD_To_LIST, args -> {
            List list = null;
            if(args[0] instanceof MyObject){
                list = (List) ((MyObject) args[0]).value;
            }else
                list = (List) args[0];
            return list.add(new MyObject(args[1]));
        });
        addFunction(OBJ_GET_FROM_LIST, args -> {
            List list = null;
            if(args[0] instanceof MyObject){
                list = (List) ((MyObject) args[0]).value;
            }else
                list = (List) args[0];
            return list.get((int) Double.parseDouble( args[1]+""));
        });
        addFunction(OBJ_NEW_MAP, args -> new HashMap<>());
        addFunction(OBJ_ADD_To_MAP, args -> {
            return ((Map) args[0]).put(args[1], new MyObject(args[2]));
        });
        addFunction(OBJ_GET_FROM_MAP, args -> {
            return ((Map)args[0]).get(args[1]);
        });
    }

    void setVar(String name, Object value);

    Object getVar(String name);

    void addFunction(String name, Function<Object[], Object> function);

    Function<Object[], Object> getFunction(String name);
    List<String> getGlobalVariables();

    void setGlobalVariables(List<String> globalVariables);
}
