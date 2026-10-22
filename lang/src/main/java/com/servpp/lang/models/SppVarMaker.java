package com.servpp.lang.models;

/**
 * @author lidong@date 2023-11-03@version 1.0
 */
public interface SppVarMaker<S, S1> {
    SppLocalVar create(SppCompilationUnit cls, String name);
}
