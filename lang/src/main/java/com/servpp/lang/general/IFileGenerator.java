package com.servpp.lang.general;

import java.io.File;
import java.io.PrintStream;

/**
 * @author lidong@date 2023-11-02@version 1.0
 */
public interface IFileGenerator extends IConstance {
    void setBraceLevel(int i);
    void incBraceLevel();

    void decBraceLevel();

    int getBraceLevel();

    PrintStream getPrintStream();
    void setPrintStream(PrintStream printStream);

    File getDomainPath();

    default String typeToString(CompilationUnitType type) {
        String ret = "class";
        switch (type) {
            case atomicservice:
                ret = "interface";
                break;
        }
        return ret;
    }

    default String typeToExtendString(CompilationUnitType type) {
        String ret = null;
        switch (type) {

            case entity -> {
                ret = compilationUnitPackage + ".Entity";
            }
            case reference -> {
                ret = compilationUnitPackage + ".Reference";
            }
            case contract -> {
                ret = compilationUnitPackage + ".Contract";
            }
            case role -> {
                ret = compilationUnitPackage + ".Role";
            }
            case atomicservice -> {
                ret = compilationUnitPackage + ".AtomicService";
            }
            case scenario -> {
                ret = compilationUnitPackage + ".Scenario";
            }
            case rolemapper -> {
                ret = compilationUnitPackage + ".RoleMapper";
            }
        }
        return ret;
    }

    public static String toJavaPrimaryType(String spptype){
        if("date".equals(spptype))
            return "java.util.Date";
        return spptype;
    }
    public static String toOpenAPIPrimaryType(String spptype){
        String ret = "string";
        switch(spptype){
            case "char":
            case "String":
            case "date":
                break;
            case "short":
            case "int":
            case "long":
                ret = "integer";
                break;
            case "float":
            case "double":
                ret = "number";
                break;
            case "boolean":
                ret = "boolean";
                break;
        }
        return ret;
    }
    public static String toOpenAPITypeFormat(String spptype){
        String ret = null;
        switch(spptype){
            case "char":
            case "String":
                break;
            case "date":
                ret = "date-time";
                break;
            case "short":
            case "int":
                ret = "int32";
                break;
            case "long":
                ret = "int64";
                break;
            case "float":
            case "double":
                ret = "double";
                break;
            case "boolean":
                break;
        }
        return ret;
    }
    default void intoBrace() {
        incBraceLevel();
    }

    default void printTab(PrintStream out) {
        int braceLevel = getBraceLevel();
        for (int i = 0; i < braceLevel; i++)
            out.print("\t");
    }

    default void outBrace() {
        decBraceLevel();
    }

    default void printlnFromBeg(String s) {
        PrintStream out = getPrintStream();
        printTab(out);
        out.println(s);
    }

    default void printFromBeg(String s) {
        PrintStream out = getPrintStream();
        printTab(out);
        out.print(s);
    }

    default void println(String s) {
        PrintStream out = getPrintStream();
        out.println(s);
    }

    default void print(String s) {
        PrintStream out = getPrintStream();
        out.print(s);
    }

    default void outFile() {
        PrintStream out = getPrintStream();
        out.close();
    }

}
