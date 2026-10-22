package com.servpp.lang.general;

/**
 * @author lidong@date 2023-11-17@version 1.0
 */
public class NameUtil {
    /** Surface participant names preserve dimensions; Java symbols use an internal suffix. */
    public static String participantName(String type, int dimensions) {
        return type + "Array".repeat(dimensions);
    }

    public static String normalizeParticipantReference(String reference) {
        return reference.replace("[]", "Array");
    }

    public static String firstToLowerCase(String ptype, boolean toLower) {
        String name;
        char first = ptype.charAt(0);
        first = toLower ? Character.toLowerCase(first) : Character.toUpperCase(first);
        StringBuffer sb = new StringBuffer();
        name = sb.append(first).append(ptype.substring(1)).toString();
        return name;
    }

    public static String getNameCall(String name){
        return getNameCall(name,false);
    }
    public static String getNameCall(String name, boolean isSet){
        if (isSet) {
            return getSetterCall(name);
        }
        int idx = -1;
        String rest = name;
        String ret = name;
        int count = 0;
        while(true){
            idx = rest.indexOf(".");
//            System.out.println("idx:"+idx + " ,rest: " +rest);
            if(count != 0) {
                ret += ".";
            }
            if(idx == -1 ){
                if(count !=0 ) {
                    if(isSet){
                        ret += "set" + NameUtil.firstToLowerCase(rest, false);
                    }else {
                        ret += "get" + NameUtil.firstToLowerCase(rest, false) + "()";
                    }
                }
                break;
            }else{
                if(count !=0 ) {
                    if(isSet){
                        ret += "set" + NameUtil.firstToLowerCase(rest.substring(0, idx), false);
                    }else {
                        ret += "get" + NameUtil.firstToLowerCase(rest.substring(0, idx), false) + "()";
                    }
                }
                else
                    ret = rest.substring(0,idx);
                rest = rest.substring(idx+1);
            }
            count++;
        }
//        System.out.println(ret);
        return ret;
    }

    private static String getSetterCall(String name) {
        String[] parts = name.split("\\.");
        if (parts.length == 1) {
            return name;
        }
        StringBuilder ret = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length - 1; i++) {
            ret.append(".get")
                    .append(firstToLowerCase(parts[i], false))
                    .append("()");
        }
        ret.append(".set")
                .append(firstToLowerCase(parts[parts.length - 1], false));
        return ret.toString();
    }
}
