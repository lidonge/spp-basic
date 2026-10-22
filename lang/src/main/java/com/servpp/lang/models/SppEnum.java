package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public class SppEnum extends SppCompilationUnit{
    List<SppEnumItem> items = new ArrayList<>();

    public SppEnum(String name) {
        super(name);
        setType(IConstance.CompilationUnitType.Enum);
    }

    public List<SppEnumItem> getItems() {
        return items;
    }

}
