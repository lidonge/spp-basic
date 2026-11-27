package com.servpp.lang.models;

import java.util.*;
import com.servpp.lang.general.IConstance.CompilationUnitType;

/** Resolve compatible role views only through a single declared shared player. */
public record SharedPlayerBinding(SppClass source, Map<String, String> fields) {
    private record View(SppClass player, Map<String, String> fields) { }

    public static boolean exact(SppParameter actual, SppParameter formal) {
        return actual != null && actual.getType() != null && formal.getType() != null
                && actual.getType().getName().equals(formal.getType().getName())
                && actual.getArrayDimension() == formal.getArrayDimension();
    }

    public static List<SppLocalVar> candidates(SppDomain domain, List<SppLocalVar> actuals, SppParameter formal) {
        var exact = actuals.stream().filter(actual -> exact(actual, formal)).toList();
        if (!exact.isEmpty()) return exact;
        return actuals.stream().filter(actual -> resolve(domain, actual, formal) != null).toList();
    }

    public static SharedPlayerBinding resolve(SppDomain domain, SppParameter actual, SppParameter formal) {
        if (actual == null || actual.getArrayDimension() != formal.getArrayDimension()
                || actual.getArrayDimension() > 1 || !(actual.getType() instanceof SppClass from)
                || !(formal.getType() instanceof SppClass to) || from == to) return null;
        var source = view(domain, from);
        if (source == null) return null;
        var target = source.player == to ? new View(to, sameFields(to)) : view(domain, to);
        if (target == null || source.player != target.player) return null;
        Map<String, String> byPlayer = new HashMap<>();
        source.fields.forEach((field, playerField) -> byPlayer.put(playerField, field));
        Map<String, String> mapped = new LinkedHashMap<>();
        for (var field : to.getSppFieldList()) {
            String original = byPlayer.get(target.fields.get(field.getName()));
            var available = original == null ? null : from.getField(original);
            if (available == null || !available.getType().getName().equals(field.getType().getName())
                    || available.getArrayDimension() != field.getArrayDimension()
                    || available.isQuantum() != field.isQuantum()) return null;
            mapped.put(field.getName(), original);
        }
        return new SharedPlayerBinding(from, Collections.unmodifiableMap(mapped));
    }

    private static View view(SppDomain domain, SppClass role) {
        if (role.getType() != CompilationUnitType.role) return null;
        var plays = domain.getRuleBlock().getRolePlays().stream()
                .filter(play -> play.role().equals(role.getName())).toList();
        if (!plays.isEmpty()) {
            if (plays.size() != 1 || !plays.get(0).codeTables().isEmpty()) return null;
            var play = plays.get(0);
            var owner = domain.getProject() == null ? null : domain.getProject().getDomain(play.providerDomain());
            if (owner == null || !(owner.getSppClass(play.providerType()) instanceof SppClass player)) return null;
            return new View(player, play.fields());
        }
        var takes = domain.getMapsOfClass().values().stream().filter(SppRoleMapper.class::isInstance)
                .map(SppRoleMapper.class::cast).filter(take -> take.getRole() == role).toList();
        if (takes.size() != 1 || takes.get(0).getDeclaredPerformers().size() != 1) return null;
        var take = takes.get(0);
        var player = take.getDeclaredPerformers().get(0);
        Map<String, String> fields = new LinkedHashMap<>();
        for (var field : role.getSppFieldList())
            if (player.getField(field.getName()) != null && !field.isSnapshotStorageFact())
                fields.put(field.getName(), field.getName());
        for (var field : take.getSppFieldList())
            if (field instanceof SppRoleField mapping && player.getField(mapping.getEntityName()) != null)
                fields.put(mapping.getName(), mapping.getEntityName());
        return new View(player, fields);
    }

    private static Map<String, String> sameFields(SppClass type) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (var field : type.getSppFieldList()) fields.put(field.getName(), field.getName());
        return fields;
    }

    public static String invocation(String receiver, List<String> values, List<SppLocalVar> formals,
                                    Map<Integer, SharedPlayerBinding> bindings) {
        if (bindings.isEmpty()) return receiver + "(" + String.join(",", values) + ")";
        var plans = new ArrayList<String>();
        var arguments = new ArrayList<String>();
        for (int index = 0; index < values.size(); index++) {
            var formal = formals.get(index);
            var binding = bindings.get(index);
            String type = (formal.getType().getType() == null ? "" : formal.getType().getPackageName() + ".")
                    + formal.getJavaType();
            if (binding == null) plans.add("null");
            else {
                var fields = binding.fields.entrySet().stream()
                        .map(field -> "java.util.Map.entry(\"" + field.getKey() + "\", \"" + field.getValue() + "\")")
                        .toList();
                plans.add("new com.servpp.service.runtime.LocalDomainServices.RoleArgument(" + type
                        + ".class, " + binding.source.getPackageName() + "." + binding.source.getName()
                        + ".class, java.util.Map.ofEntries(" + String.join(",", fields) + "))");
            }
            String cast = formal.isParticipantCollection() ? "java.util.List<" + type + ">" : type + formal.getArrayString();
            arguments.add("(" + cast + ") $sppRoleArgs[" + index + "]");
        }
        return "com.servpp.service.runtime.LocalDomainServices.withRoleArguments(new Object[]{"
                + String.join(",", values) + "}, new com.servpp.service.runtime.LocalDomainServices.RoleArgument[]{"
                + String.join(",", plans) + "}, $sppRoleArgs -> " + receiver + "(" + String.join(",", arguments) + "))";
    }
}
