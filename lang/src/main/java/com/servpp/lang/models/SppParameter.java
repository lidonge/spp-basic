package com.servpp.lang.models;

import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.app.NumericBinding;

import java.math.RoundingMode;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class SppParameter {
    SppCompilationUnit type;
    String name;
    private String declaredNumericSpecification;
    private String physicalNumericSpecification;
    private RoundingMode declaredRoundingMode;
    public String getDeclaredNumericSpecification() { return declaredNumericSpecification == null ? type.getName() : declaredNumericSpecification; }
    public void setDeclaredNumericSpecification(String value) {
        int firstComma = value.indexOf(',');
        int modeComma = firstComma < 0 ? -1 : value.indexOf(',', firstComma + 1);
        RoundingMode mode = modeComma < 0 ? null
                : NumericBinding.roundingMode(value.substring(modeComma + 1, value.length() - 1));
        String specification = modeComma < 0 ? value : value.substring(0, modeComma) + ")";
        NumericBinding.dimensions(specification);
        declaredNumericSpecification = specification;
        declaredRoundingMode = mode;
    }
    public RoundingMode getDeclaredRoundingMode() { return declaredRoundingMode; }
    public Integer getDeclaredNumericScale() {
        int[] dimensions = NumericBinding.dimensions(getDeclaredNumericSpecification());
        if (dimensions != null) return dimensions[1];
        return switch (type.getName()) {
            case "short", "int", "long", "BigInteger", "java.math.BigInteger" -> 0;
            default -> null;
        };
    }
    public String getPhysicalNumericSpecification() { return physicalNumericSpecification; }
    public void setPhysicalNumericSpecification(String value) { physicalNumericSpecification = value; }
    private String physicalJavaType;
    private String numericAdapter;
    public String getNumericAdapter() { return numericAdapter; }
    public void setNumericAdapter(String adapter) { numericAdapter = adapter; }

    public String getJavaType() { return physicalJavaType == null ? type.getName() : physicalJavaType; }
    public void setPhysicalJavaType(String value) { physicalJavaType = value; }
    public String getPhysicalJavaType() { return physicalJavaType; }


    private int arrayDimension;

    protected SppParameter(SppCompilationUnit type, String name, int arrayDimension) {
        this.type = type;
        this.name = name;
        this.arrayDimension = arrayDimension;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        var parameter = new SppParameter(type,name,arrayDimension);
        copyNumericConfiguration(parameter);
        return parameter;
    }

    protected void copyNumericConfiguration(SppParameter target) {
        target.declaredNumericSpecification = declaredNumericSpecification;
        target.declaredRoundingMode = declaredRoundingMode;
        target.physicalNumericSpecification = physicalNumericSpecification;
        target.physicalJavaType = physicalJavaType;
        target.numericAdapter = numericAdapter;
    }

    public SppParameter(SppCompilationUnit type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getArrayString(){
        String ret = "";
        for(int i = 0;i<arrayDimension;i++){
            ret +="[]";
        }
        return ret;
    }

    public boolean isParticipantCollection() {
        return arrayDimension == 1 && type instanceof SppClass && type.getType() != null;
    }
    public String getServiceJavaType() {
        return isParticipantCollection() ? "java.util.List<" + getJavaType() + ">" : getJavaType() + getArrayString();
    }

    public String getRequestArgumentCall() {
        String getter = "arg.get" + getUpperName() + "()";
        String value = isParticipantCollection() ? getter : arrayDimension == 1 ? modelCollectionArgument(getter) : getter + getToArrayString();
        if (getArrayDimension() == 0 && "short".equals(getJavaType())) {
            return "com.servpp.service.numeric.ExactNumbers.decimal(" + value + ").shortValueExact()";
        }
        return value;
    }

    public String getToArrayString(){
        if(arrayDimension != 0){
            return ".toArray(new "+getJavaType()+"[arg.get"+getUpperName()+"().size()])";
        }
        return "";
    }

    public String getUpperName(){
        return NameUtil.firstToLowerCase(getName(),false);
    }

    public int getArrayDimension() {
        return arrayDimension;
    }

    public SppParameter setArrayDimension(int arrayDimension) {
        this.arrayDimension = arrayDimension;
        return this;
    }


    public SppCompilationUnit getType() {
        return type;
    }

    public void setType(SppCompilationUnit type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * For mustache
     * @return
     */
    public String getNameCall(){
        String value = NameUtil.getNameCall(name);
        return isParticipantCollection() ? "com.servpp.service.runtime.ParticipantCollections.list(" + value + ")" : arrayDimension == 1 && name.contains(".") ? modelCollectionArgument(value) : value;
    }

    private String modelCollectionArgument(String getter) {
        String element = getJavaType();
        if (physicalJavaType == null && type != null && type.getBasePackage() != null
                && type.getType() != null) { element = type.getPackageName() + "." + element; }
        return "(" + element + "[]) com.servpp.service.runtime.ParticipantCollections.array("
                + getter + ", " + element + ".class)";
    }
    @Override
    public String toString() {
        return "SppParameter{" +
                "type=" + getTypeName() + getArrayString()+
                ", name='" + name + '\'' +
                '}';
    }

    private String getTypeName() {
        return type == null ? null:type.getName();
    }
}
