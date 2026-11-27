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
        if (domain == null || actual == null || actual.getArrayDimension() != formal.getArrayDimension()
                || actual.getArrayDimension() > 1 || !(actual.getType() instanceof SppClass from)
                || !(formal.getType() instanceof SppClass to) || from == to) return null;
        var source = view(domain, from);
        var target = view(domain, to);
        if (source != null && source.player == to) target = new View(to, sameFields(to));
        else if (target != null && target.player == from) source = new View(from, sameFields(from));
        if (source == null || target == null || source.player != target.player) return null;
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

    public static SppClass player(SppDomain domain, SppClass role) {
        var view = view(domain, role);
        return view == null ? role : view.player;
    }

    public static String sourcePath(Map<String, String> fields, String targetPath) {
        if (targetPath.startsWith("@")) return targetPath;
        int dot = targetPath.indexOf('.');
        String source = fields.get(dot < 0 ? targetPath : targetPath.substring(0, dot));
        return source == null || dot < 0 ? source : source + targetPath.substring(dot);
    }

    public static SharedPlayerBinding resolve(SppDomain domain, SppClass from, SppClass to) {
        return resolve(domain, new SppParameter(from, from.getName()), new SppParameter(to, to.getName()));
    }

    private static View view(SppDomain domain, SppClass role) {
        return view(domain, role, 0);
    }

    private static View view(SppDomain domain, SppClass role, int depth) {
        if (role.getReferenceSource() != null && domain.getProject() != null) {
            String[] provider = role.getReferenceSource().split("\\.", 2);
            var owner = domain.getProject().getDomain(provider[0]);
            if (owner != null && provider.length == 2 && owner.getSppClass(provider[1]) instanceof SppClass player)
                return new View(player, sameFields(role));
        }
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
        // The first take names the player; further takes only map other sources onto its facts.
        var takes = domain.getMapsOfClass().values().stream().filter(SppRoleMapper.class::isInstance)
                .map(SppRoleMapper.class::cast).filter(take -> take.getRole() == role)
                .sorted(Comparator.comparingLong(SppRoleMapper::getSequence)).toList();
        if (takes.isEmpty() || takes.get(0).getDeclaredPerformers().size() != 1) return null;
        var take = takes.get(0);
        var player = take.getDeclaredPerformers().get(0);
        Map<String, String> fields = new LinkedHashMap<>();
        for (var field : role.getSppFieldList())
            if (player.getField(field.getName()) != null && !field.isSnapshotStorageFact())
                fields.put(field.getName(), field.getName());
        for (var field : take.getSppFieldList())
            if (field instanceof SppRoleField mapping && player.getField(mapping.getEntityName()) != null)
                fields.put(mapping.getName(), mapping.getEntityName());
        // A role taken from another role is played by that role's player.
        if (player != role && player.getType() == CompilationUnitType.role && depth < 8) {
            var inner = view(domain, player, depth + 1);
            if (inner != null && inner.player != player) {
                Map<String, String> composed = new LinkedHashMap<>();
                fields.forEach((roleField, middle) -> {
                    String original = inner.fields.get(middle);
                    if (original != null) composed.put(roleField, original);
                });
                return new View(inner.player, composed);
            }
        }
        return new View(player, fields);
    }

    private static Map<String, String> sameFields(SppClass type) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (var field : type.getSppFieldList()) fields.put(field.getName(), field.getName());
        return fields;
    }

    public String javaFields() {
        return fields.entrySet().stream()
                .map(field -> "java.util.Map.entry(\"" + field.getKey() + "\", \"" + field.getValue() + "\")")
                .collect(java.util.stream.Collectors.joining(",", "java.util.Map.ofEntries(", ")"));
    }

    public String projectJava(String value, SppClass target, int dimensions) {
        String method = dimensions == 0 ? "projectRole" : "projectRoleCollection";
        return "com.servpp.service.runtime.LocalDomainServices." + method + "(" + value + ", "
                + target.getPackageName() + "." + target.getName() + ".class, " + javaFields() + ")";
    }

    private static String javaType(SppLocalVar formal) {
        return (formal.getType().getType() == null ? "" : formal.getType().getPackageName() + ".") + formal.getJavaType();
    }

    private String roleArgument(String type) {
        return "new com.servpp.service.runtime.LocalDomainServices.RoleArgument(" + type
                + ".class, " + source.getPackageName() + "." + source.getName() + ".class, " + javaFields() + ")";
    }

    /** The call-site view of each argument, for the participants that book the call; null where passed as is. */
    public static String roleArguments(List<SppLocalVar> formals, Map<Integer, SharedPlayerBinding> bindings) {
        var plans = new ArrayList<String>();
        for (int index = 0; index < formals.size(); index++) {
            var binding = bindings.get(index);
            plans.add(binding == null ? "null" : binding.roleArgument(javaType(formals.get(index))));
        }
        return "new com.servpp.service.runtime.LocalDomainServices.RoleArgument[]{" + String.join(",", plans) + "}";
    }

    public static String invocation(String receiver, List<String> values, List<SppLocalVar> formals,
                                    Map<Integer, SharedPlayerBinding> bindings) {
        if (bindings.isEmpty()) return receiver + "(" + String.join(",", values) + ")";
        var plans = new ArrayList<String>();
        var arguments = new ArrayList<String>();
        for (int index = 0; index < values.size(); index++) {
            var formal = formals.get(index);
            var binding = bindings.get(index);
            String type = javaType(formal);
            plans.add(binding == null ? "null" : binding.roleArgument(type));
            String cast = formal.isParticipantCollection() ? "java.util.List<" + type + ">" : type + formal.getArrayString();
            arguments.add("(" + cast + ") $sppRoleArgs[" + index + "]");
        }
        return "com.servpp.service.runtime.LocalDomainServices.withRoleArguments(new Object[]{"
                + String.join(",", values) + "}, new com.servpp.service.runtime.LocalDomainServices.RoleArgument[]{"
                + String.join(",", plans) + "}, $sppRoleArgs -> " + receiver + "(" + String.join(",", arguments) + "))";
    }
}
