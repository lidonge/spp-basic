package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-03@version 1.0
 */
public class SppRoleMapper extends SppService{
    SppClass role;
    List<SppClass> entities;
    private List<List<SppClass>> alternatives = List.of();
    public List<List<SppClass>> getAlternatives() { return alternatives; }
    public void setAlternatives(List<List<SppClass>> value) { alternatives = value; }
    boolean isMapSame;
    boolean isTakeAll;

    public SppRoleMapper(String name) {
        super(name, IConstance.CompilationUnitType.rolemapper);
    }

    public SppClass getRole() {
        return role;
    }

    public void setRole(SppClass role) {
        this.role = role;
    }

    public List<SppClass> getEntities() {
        return entities;
    }

    public void setEntities(List<SppClass> value) { entities = new ArrayList<>(value); }

    public void addEntity(SppClass entity) {
        if(entities == null)
            entities = new ArrayList<>();
        entities.add(entity);
    }

    public boolean isMapSame() {
        return isMapSame;
    }

    public void setMapSame(boolean mapSame) {
        isMapSame = mapSame;
    }

    public boolean isTakeAll() {
        return isTakeAll;
    }

    public void setTakeAll(boolean takeAll) {
        isTakeAll = takeAll;
    }

    /** Bind declared participant members; take never invents their structure. */
    public List<String> prepareMemberBindings() {
        List<String> errors = new ArrayList<>();
        if (role == null || entities == null) return errors;
        for (SppClass performer : entities) {
            if (performer == null) continue;
            var members = role.getSppFieldList().stream().filter(field -> field.getType().getName().equals(performer.getName())
                    && field.getArrayDimension() == 0).toList();
            var explicit = getSppFieldList().stream().filter(field -> field instanceof SppRoleField mapping
                    && mapping.getType().getName().equals(performer.getName()) && performer.getName().equals(mapping.getEntityName())).toList();
            if (members.size() > 1 && members.stream().anyMatch(member -> explicit.stream()
                    .noneMatch(mapping -> mapping.getName().equals(member.getName())))) {
                errors.add("Ambiguous performer " + performer.getName() + " in " + role.getName()
                        + "; explicitly map the declared participant members.");
                continue;
            }
            for (SppField member : members) {
                if (getField(member.getName()) != null) continue;
                addField(new SppRoleField(performer, member.getName(), performer.getName()));
            }
        }
        for (SppField mapping : getSppFieldList()) {
            SppField target = role.getField(mapping.getName());
            if (target == null) errors.add("Role mapping " + role.getName() + "." + mapping.getName()
                    + " requires a declared role member; take does not add fields.");
            else if (!target.getType().getName().equals(mapping.getType().getName()) || target.getArrayDimension() != mapping.getArrayDimension())
                errors.add("Role mapping must match the declared member type: " + role.getName() + "." + mapping.getName());
        }
        return errors;
    }

    public boolean getHasPhysicalNumericMappings() {
        return role.getSppFieldList().stream().anyMatch(SppField::isSnapshotStorageFact) || getSppFieldList().stream().anyMatch(f -> f instanceof SppRoleField m
                && entities != null && entities.stream().anyMatch(e -> e.getName().equals(m.getType().getName())
                    && e.getName().equals(m.getEntityName())))
                || entities != null && entities.stream().anyMatch(entity -> entity.getSppFieldList().stream()
                .anyMatch(field -> {
                    SppField roleField = role.getField(field.getName());
                    return roleField != null && (!roleField.getJavaType().equals(field.getJavaType())
                            || !java.util.Objects.equals(roleField.getNumericAdapter(), field.getNumericAdapter()));
                })) || getSppFieldList().stream().anyMatch(field -> {
                    if (!(field instanceof SppRoleField mapping) || entities == null) { return false; }
                    return entities.stream().anyMatch(entity -> {
                        String sourceName = entity.getName().equals(mapping.getEntityName()) ? mapping.getName() : mapping.getEntityName();
                        SppField source = entity.getField(sourceName), target = role.getField(mapping.getName());
                        return source != null && target != null && (!source.getJavaType().equals(target.getJavaType())
                                || !java.util.Objects.equals(source.getNumericAdapter(), target.getNumericAdapter()));
                    });
                });
    }

    public String getPhysicalEntityToRole() { return physicalMappings(true); }
    public String getPhysicalRoleToEntity() { return physicalMappings(false); }

    private String physicalMappings(boolean toRole) {
        StringBuilder result = new StringBuilder();
        if (entities == null) { return ""; }
        List<SppLocalVar> mappings = new ArrayList<>(getServiceBody().getSppLocalVarList());
        mappings.addAll(getSppFieldList());
        for (SppLocalVar field : mappings) {
            if (!(field instanceof SppRoleField mapping)) { continue; }
            for (SppClass entity : entities) {
                if (entity.getName().equals(mapping.getType().getName()) && entity.getName().equals(mapping.getEntityName())) {
                    if (toRole) result.append("role.set").append(mapping.getUpperName()).append("((")
                            .append(entity.getName()).append(") entities.get(\"").append(entity.getName()).append("\"));\n");
                    else result.append("if (role.get").append(mapping.getUpperName()).append("() != null) entities.put(\"")
                            .append(entity.getName()).append("\", role.get").append(mapping.getUpperName()).append("());\n");
                    break;
                }
                String sourceName = entity.getName().equals(mapping.getEntityName()) ? mapping.getName() : mapping.getEntityName();
                SppField source = entity.getField(sourceName), target = role.getField(mapping.getName());
                if (source == null || target == null) { continue; }
                if (!toRole && target.isSnapshotStorageFact()) continue;
                String object = "((" + entity.getName() + ") entities.get(\"" + entity.getName() + "\"))";
                String value = toRole ? object + ".get" + source.getUpperName() + "()" : "role.get" + target.getUpperName() + "()";
                SppField destination = toRole ? target : source;
                SppField origin = toRole ? source : target;
                if (!source.getJavaType().equals(target.getJavaType())
                        || !java.util.Objects.equals(source.getNumericAdapter(), target.getNumericAdapter())) {
                    value = com.servpp.lang.models.app.expr.NumericJavaRenderer.convertNumber(value, origin, destination);
                }
                String receiver = toRole ? "role" : object;
                String supplier = toRole ? object : "role";
                result.append("com.servpp.service.runtime.PropertyChanges.mapped(").append(supplier)
                        .append(", \"").append(origin.getName()).append("\", ").append(receiver)
                        .append(", \"").append(destination.getName()).append("\", () -> ")
                        .append(receiver).append(".set").append(destination.getUpperName())
                        .append('(').append(value).append("));\n");
                break;
            }
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return "SppRoleMapper{" +
                "role=" + role +
                ", entity=" + entities +
                ", isMapSame=" + isMapSame +
                ", isTakeAll=" + isTakeAll +
                ", serviceType=" + serviceType +
                ", funcName='" + funcName + '\'' +
                ", serviceBody=" + serviceBody +
                ", name='" + getName() + '\'' +
                ", type=" + type +
                ", sppFieldMap=" + sppFieldMap +
                '}';
    }
}
