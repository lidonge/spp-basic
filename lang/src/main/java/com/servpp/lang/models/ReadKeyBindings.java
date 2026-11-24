package com.servpp.lang.models;

import com.servpp.lang.general.IConstance.ServiceType;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.app.NumericBinding;
import com.servpp.lang.models.app.expr.NumericJavaRenderer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Resolves scenario read operands once the APP identity and call arguments are known. */
final class ReadKeyBindings {
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\\\\\|\\\\u([0-9a-fA-F]{4})");

    private ReadKeyBindings() { }

    static void bind(SppDomain domain, SppService owner, SppServiceCall call, List<String> errors) {
        call.getBoundReadKeys().clear();
        var service = call.getCallee();
        String label = "Scenario " + owner.getFuncName() + " by read binding";
        if (service == null || service instanceof SppScenario || service.getServiceType() != ServiceType.query) {
            errors.add(label + " requires a keyed query");
            return;
        }
        label += " for " + service.getFuncName();
        var formals = service.getServiceBody().getSppLocalVarList();
        if (formals.size() != call.getSppParameterList().size()) return;
        var conditions = formals.stream().filter(SppLocalVar::isQueryKey).toList();
        if (conditions.isEmpty()) {
            var scope = service.getScopeItem();
            var indexes = scope == null ? List.<Integer>of() : scope.getRepositoryHydrationRules().stream()
                    .flatMap(rule -> rule.keyTargets().stream()).map(key -> key.argumentIndex()).distinct().toList();
            if (indexes.size() == 1 && indexes.get(0) >= 0 && indexes.get(0) < formals.size())
                conditions = List.of(formals.get(indexes.get(0)));
            else if (indexes.isEmpty() && formals.size() == 1) conditions = formals;
        }
        if (conditions.size() != 1 || conditions.get(0).getArrayDimension() != 0
                || !(conditions.get(0).getType() instanceof SppClass type)) {
            errors.add(label + " requires a unique scalar key participant");
            return;
        }
        var crudTarget = service.getImplicitCrudTarget();
        if (crudTarget != null && !ParticipantRoleBindings.identity(domain, crudTarget)
                .equals(ParticipantRoleBindings.identity(domain, type))) {
            errors.add(label + " requires a scenario participant of the target type " + crudTarget.getName());
            return;
        }
        var actual = call.getSppParameterList().get(formals.indexOf(conditions.get(0)));
        var keys = readKeys(domain, type, label, errors);
        if (keys.isEmpty()) return;
        Map<String, Source> sources = new HashMap<>();
        for (var key : call.getReadKeys()) {
            collectSources(domain, owner, type, actual.getName(), keys, key, sources, label, errors);
        }
        for (String path : keys) {
            var field = scalarField(type, path);
            var source = sources.get(path);
            if (source == null) {
                errors.add(label + " has a missing source for " + path);
                continue;
            }
            if (field == null) {
                errors.add(label + " requires a writable APP primary-key field: " + path);
                continue;
            }
            if (!compatible(field, source, path, label, errors)) continue;
            var parents = new ArrayList<SppServiceCall.ReadKeyParent>();
            String target = actual.getName();
            SppClass current = type;
            String[] parts = path.split("\\.");
            for (int member = 0; member < parts.length - 1; member++) {
                current = (SppClass) current.getField(parts[member]).getType();
                target += "." + parts[member];
                parents.add(new SppServiceCall.ReadKeyParent(target, current.getName()));
            }
            String javaValue = source.java();
            if ("BLANK".equals(javaValue)) {
                try { javaValue = com.servpp.lang.models.app.BlankPolicy.writeJava(domain, field, null); }
                catch (IllegalArgumentException invalid) { errors.add(label + " " + invalid.getMessage()); continue; }
            }
            call.getBoundReadKeys().add(new SppServiceCall.BoundReadKey(actual.getName() + "." + path,
                    field, source.field(), javaValue, source.path(), List.copyOf(parents), "BLANK".equals(source.java())));
        }
    }

    private static List<String> readKeys(SppDomain domain, SppClass target, String label, List<String> errors) {
        if (DeploymentEnvironmentPlanner.isAppPlayedRole(domain, target)) {
            List<String> keys = List.of();
            var project = domain.getProject();
            for (var play : DeploymentEnvironmentPlanner.plays(domain, target)) {
                var provider = project == null ? null : project.getDomain(play.providerDomain());
                var performer = provider == null ? null : provider.getSppClass(play.providerType());
                var alternative = performer instanceof SppClass type
                        ? DeploymentEnvironmentPlanner.playKeys(provider, type, play) : List.<String>of();
                if (alternative.isEmpty()) {
                    errors.add(label + " requires APP primary-key fields");
                    return List.of();
                }
                // The provider query keeps its own key order. The scenario only binds consumer fields.
                if (keys.isEmpty()) keys = alternative;
                else if (!Set.copyOf(keys).equals(Set.copyOf(alternative))) {
                    errors.add(label + " requires the same primary-key fields for every APP play binding");
                    return List.of();
                }
            }
            return keys;
        }
        var keys = ParticipantRoleBindings.primaryKeys(domain, target);
        if (keys.isEmpty()) errors.add(label + " requires APP primary-key fields");
        return keys;
    }

    private static void collectSources(SppDomain domain, SppService owner, SppClass target, String targetName,
                                       List<String> keys, SppServiceCall.ReadKey binding, Map<String, Source> sources,
                                       String label, List<String> errors) {
        String[] parts = binding.path().split("\\.", 2);
        String qualifier = parts[0];
        boolean constant = binding.constant() != null;
        var participants = owner.getServiceBody().getSppLocalVarList();
        var matches = participants.stream().filter(var -> var.getName().equals(qualifier)).toList();
        if (matches.isEmpty()) matches = participants.stream()
                .filter(var -> var.getType().getName().equals(qualifier)).toList();
        var participant = matches.size() == 1 ? matches.get(0) : null;
        if (constant && parts.length != 2) {
            errors.add(label + " requires a qualified field for constant: " + binding.path());
            return;
        }
        Source value = constant ? constantSource(domain, binding) : null;
        if (constant && value == null) {
            errors.add(label + " has an unknown constant: " + binding.constant());
            return;
        }
        if (constant && (qualifier.equals(targetName) || qualifier.equals(target.getName()))) {
            if (keys.contains(parts[1])) addSource(parts[1], value, sources, label, errors);
            else errors.add(label + " constant does not bind a primary-key field: " + binding.path());
            return;
        }
        SppClass performer = null;
        if (participant != null && participant.getArrayDimension() == 0 && participant.getType() instanceof SppClass cls) {
            performer = cls;
        } else if (constant && matches.isEmpty() && domain.getSppClass(qualifier) instanceof SppClass cls) {
            performer = cls;
        }
        if (performer == null) {
            errors.add(label + " has an unknown or ambiguous scalar participant: " + qualifier);
            return;
        }
        var chains = takeChains(domain, target, performer, new HashSet<>());
        if (chains.isEmpty()) {
            errors.add(label + " participant " + qualifier + " has no take relation to " + target.getName());
            return;
        }
        boolean mapped = false;
        for (String key : keys) {
            Set<String> fields = new LinkedHashSet<>();
            for (var chain : chains) {
                String field = key;
                for (var step : chain) {
                    field = step.performerPath(field);
                    if (field == null) break;
                }
                if (field != null && scalarField(performer, field) != null
                        && (!constant || field.equals(parts[1]))) fields.add(field);
            }
            for (String field : fields) {
                mapped = true;
                var sourceField = scalarField(performer, field);
                if (constant) {
                    var targetField = scalarField(target, key);
                    var declared = new Source(sourceField.getType().getName(), null, sourceField, null, null);
                    if (compatible(sourceField, value, binding.path(), label, errors)
                            && targetField != null && compatible(targetField, declared, key, label, errors))
                        addSource(key, value, sources, label, errors);
                } else {
                    String path = participant.getName() + "." + field;
                    addSource(key, new Source(sourceField.getType().getName(), NameUtil.getNameCall(path),
                            sourceField, null, path), sources, label, errors);
                }
            }
        }
        if (constant && !mapped) errors.add(label + " constant field does not map to a primary key: " + binding.path());
    }

    private static void addSource(String key, Source value, Map<String, Source> sources,
                                  String label, List<String> errors) {
        if (sources.putIfAbsent(key, value) != null) errors.add(label + " has a duplicate source for " + key);
    }

    private static boolean compatible(SppField field, Source source, String path, String label, List<String> errors) {
        String type = field.getType().getName();
        if (!type.equals(source.type()) && !acceptsNumericLiteral(type, source)) {
            errors.add(label + " has a type mismatch for " + path + ": expected " + type + ", found " + source.type());
            return false;
        }
        if (!matchingWidth(field, source)) {
            errors.add(label + " has a declared-width mismatch for " + path);
            return false;
        }
        return true;
    }

    /** Walk from the repository role back through declared take performers. */
    private static List<List<TakeStep>> takeChains(SppDomain domain, SppClass target, SppClass performer,
                                                   Set<String> visiting) {
        String targetId = ParticipantRoleBindings.identity(domain, target);
        if (!visiting.add(targetId)) return List.of();
        List<List<TakeStep>> chains = new ArrayList<>();
        for (var unit : domain.getMapsOfClass().values()) {
            if (!(unit instanceof SppRoleMapper mapper) || mapper.getRole() == null
                    || !targetId.equals(ParticipantRoleBindings.identity(domain, mapper.getRole()))) continue;
            for (var from : mapper.getDeclaredPerformers()) {
                var step = new TakeStep(mapper, from);
                if (ParticipantRoleBindings.identity(domain, from).equals(ParticipantRoleBindings.identity(domain, performer))) {
                    chains.add(List.of(step));
                    continue;
                }
                for (var tail : takeChains(domain, from, performer, visiting)) {
                    List<TakeStep> chain = new ArrayList<>();
                    chain.add(step);
                    chain.addAll(tail);
                    chains.add(chain);
                }
            }
        }
        visiting.remove(targetId);
        return chains;
    }

    private record TakeStep(SppRoleMapper mapper, SppClass performer) {
        String performerPath(String rolePath) {
            String[] parts = rolePath.split("\\.", 2);
            String source = parts[0];
            if (mapper.getField(source) instanceof SppRoleField mapping) {
                if (mapping.getType() instanceof SppClass && performer.getName().equals(mapping.getEntityName())) {
                    return parts.length == 2 && scalarField(performer, parts[1]) != null ? parts[1] : null;
                }
                source = mapping.getEntityName();
            }
            if (parts.length == 2) source += "." + parts[1];
            return scalarField(performer, source) != null ? source : null;
        }
    }

    private static boolean acceptsNumericLiteral(String type, Source source) {
        if (source.field() != null || !NumericBinding.isNumeric(source.type())) return false;
        return switch (type) {
            case "short", "BigInteger" -> "int".equals(source.type()) || "long".equals(source.type());
            case "decimal", "BigDecimal" -> true;
            default -> false;
        };
    }

    private static boolean matchingWidth(SppField target, Source source) {
        if (source.field() instanceof SppField field) {
            return target.getDeclaredNumericSpecification().equals(field.getDeclaredNumericSpecification())
                    && Objects.equals(target.getDefaultStringLength(), field.getDefaultStringLength());
        }
        if (source.literal() == null) return true;
        Integer width = target.getDefaultStringLength();
        if (width != null && source.literal().startsWith("\"")) {
            String contents = source.literal().substring(1, source.literal().length() - 1);
            return UNICODE_ESCAPE.matcher(contents).replaceAll(match -> match.group(1) == null
                    ? java.util.regex.Matcher.quoteReplacement(match.group()) : "x").translateEscapes().length() <= width;
        }
        try {
            int[] dimensions = NumericBinding.dimensions(target.getDeclaredNumericSpecification());
            if (dimensions == null && !"short".equals(target.getType().getName())) return true;
            var value = numericLiteral(source.literal());
            if (dimensions == null) {
                value.shortValueExact();
                return true;
            }
            return value.setScale(dimensions[1], RoundingMode.UNNECESSARY).precision() <= dimensions[0];
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return false;
        }
    }

    static BigDecimal numericLiteral(String value) {
        String clean = value.replace("_", "");
        if (clean.matches("(?i)-?0x[0-9a-f.]+p[+-]?[0-9]+[fd]?"))
            return BigDecimal.valueOf(Double.parseDouble(clean));
        return new BigDecimal(NumericJavaRenderer.normalizeLiteral(clean));
    }

    private static Source constantSource(SppDomain domain, SppServiceCall.ReadKey key) {
        String text = key.constant();
        if ("BLANK".equals(text)) return new Source("String", text, null, null, null);
        if (key.literal()) return new Source(key.literalType(), text, null, text, null);
        var constant = domain.getConstants().get(text);
        return constant == null ? null : new Source(constant.getType().getName(), text, constant, constant.getLiteral(), null);
    }

    private static SppField scalarField(SppClass type, String path) {
        int dot = path.indexOf('.');
        var field = type.getField(dot < 0 ? path : path.substring(0, dot));
        if (field == null || field.getArrayDimension() != 0) return null;
        if (dot < 0) return field;
        return field.getType() instanceof SppClass nested ? scalarField(nested, path.substring(dot + 1)) : null;
    }

    private record Source(String type, String java, SppLocalVar field, String literal, String path) { }
}
