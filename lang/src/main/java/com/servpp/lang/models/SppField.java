package com.servpp.lang.models;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class SppField extends SppLocalVar {
    private SppClass parent;
    private boolean inheritedThroughTakeAll;
    public boolean isInheritedThroughTakeAll() { return inheritedThroughTakeAll; }
    public void setInheritedThroughTakeAll(boolean value) { inheritedThroughTakeAll = value; }
    private boolean borrowedStorageFact;
    private boolean performerStorageIdentity;
    private boolean performerRelation;
    public boolean isPerformerRelation() { return performerRelation; }
    public void setPerformerRelation(boolean relation) { performerRelation = relation; }
    public boolean isBorrowedStorageFact() { return borrowedStorageFact; }
    public void setBorrowedStorageFact(boolean value) { borrowedStorageFact = value; }
    public boolean isPerformerStorageIdentity() { return performerStorageIdentity; }
    public void setPerformerStorageIdentity(boolean value) { performerStorageIdentity = value; }
    private boolean snapshotStorageFact;
    public boolean isSnapshotStorageFact() { return snapshotStorageFact; }
    public void setSnapshotStorageFact(boolean value) { snapshotStorageFact = value; }
    private boolean systemResult;
    public boolean isSystemResult() { return systemResult; }
    public void setSystemResult(boolean value) { systemResult = value; }
    public boolean isStoredHere() { return !systemResult && (snapshotStorageFact || !borrowedStorageFact || performerStorageIdentity); }
    private boolean syntheticRolePerformer;
    private String businessMeaning;
    private boolean defaultParticipantName;
    public boolean isDefaultParticipantName() { return defaultParticipantName; }
    public void setDefaultParticipantName(boolean value) { defaultParticipantName = value; }
    public String getBusinessMeaning() { return businessMeaning; }
    public void setBusinessMeaning(String meaning) { businessMeaning = meaning; }

    protected SppField(SppCompilationUnit type, String name, int arrayDimension, int index, boolean isQuantum) {
        super(type, name, arrayDimension, index, isQuantum);
    }

    public SppField(SppCompilationUnit type, String name, int index, boolean isQuantum) {
        super(type, name, index, isQuantum);
    }

    public SppField(SppCompilationUnit cls, String name) {
        super(cls, name);
    }
    @Override
    protected Object clone() throws CloneNotSupportedException {
        SppField field = new SppField(type,name,getArrayDimension(),index,isQuantum);
        field.inheritedThroughTakeAll = inheritedThroughTakeAll;
        field.borrowedStorageFact = borrowedStorageFact;
        field.snapshotStorageFact = snapshotStorageFact;
        field.performerStorageIdentity = performerStorageIdentity;
        field.performerRelation = performerRelation;
        field.syntheticRolePerformer = syntheticRolePerformer;
        field.businessMeaning = businessMeaning;
        field.defaultParticipantName = defaultParticipantName;
        field.systemResult = systemResult;
        field.setQueryKey(isQueryKey());
        copyNumericConfiguration(field);
        return field;
    }

    public SppClass getParent() {
        return parent;
    }

    public void setParent(SppClass parent) {
        this.parent = parent;
    }

    public boolean isSyntheticRolePerformer() {
        return syntheticRolePerformer;
    }

    public void setSyntheticRolePerformer(boolean syntheticRolePerformer) {
        this.syntheticRolePerformer = syntheticRolePerformer;
    }

    public String getQualifiedInstName(){
        if(getParent() == null)
            return name;

        return getParent().getQualifiedInstName() + "." + name;
    }
    @Override
    public String toString() {
        return "SppField{" +
                "type=" + type.getName() +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        boolean ret = super.equals(obj);
        if(obj instanceof SppField){
            SppField field = (SppField) obj;
            ret |= getType().equals(field.getType()) && getName().equals(field.getName());
        }
        return ret;
    }
}
