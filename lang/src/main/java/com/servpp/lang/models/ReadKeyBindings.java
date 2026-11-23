package com.servpp.lang.models;

import com.servpp.lang.general.IConstance.ServiceType;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.app.NumericBinding;
import com.servpp.lang.models.app.expr.NumericJavaRenderer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
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
        var actual = call.getSppParameterList().get(formals.indexOf(conditions.get(0)));
        var keys = ParticipantRoleBindings.primaryKeys(domain, type);
        if (keys.isEmpty()) {
            errors.add(label + " requires APP primary-key fields");
            return;
        }
        if (keys.size() != call.getReadKeys().size()) {
            errors.add(label + " has wrong key count: expected " + keys.size() + ", found " + call.getReadKeys().size());
            return;
        }
        for (int index = 0; index < keys.size(); index++) {
            String path = keys.get(index);
            var field = scalarField(type, path);
            if (field == null) {
                errors.add(label + " requires a writable APP primary-key field: " + path);
                continue;
            }
            var key = call.getReadKeys().get(index);
            var source = source(domain, owner, key);
            if (source == null) {
                errors.add(label + " has unknown or ambiguous source: " + key.text());
                continue;
            }
            String typeName = field.getType().getName();
            if (!typeName.equals(source.type()) && !acceptsNumericLiteral(typeName, key)) {
                errors.add(label + " has a type mismatch for " + path + ": expected " + typeName + ", found " + source.type());
                continue;
            }
            if (!matchingWidth(field, source)) {
                errors.add(label + " has a declared-width mismatch for " + path + ": " + key.text());
                continue;
            }
            String value = source.java();
            var parents = new ArrayList<SppServiceCall.ReadKeyParent>();
            String target = actual.getName();
            SppClass current = type;
            String[] parts = path.split("\\.");
            for (int member = 0; member < parts.length - 1; member++) {
                current = (SppClass) current.getField(parts[member]).getType();
                target += "." + parts[member];
                parents.add(new SppServiceCall.ReadKeyParent(target, current.getName()));
            }
            call.getBoundReadKeys().add(new SppServiceCall.BoundReadKey(actual.getName() + "." + path,
                    field, source.field(), value, List.copyOf(parents)));
        }
    }

    private static boolean acceptsNumericLiteral(String type, SppServiceCall.ReadKey key) {
        if (!key.literal() || !NumericBinding.isNumeric(key.literalType())) return false;
        return switch (type) {
            case "short", "BigInteger" -> "int".equals(key.literalType()) || "long".equals(key.literalType());
            case "decimal", "BigDecimal" -> true;
            default -> false;
        };
    }

    private static boolean matchingWidth(SppField target, Source source) {
        if (source.field() instanceof SppField field) {
            return target.getDeclaredNumericSpecification().equals(field.getDeclaredNumericSpecification())
                    && (target.getDefaultStringLength() == null || field.getDefaultStringLength() == null
                        || target.getDefaultStringLength().equals(field.getDefaultStringLength()));
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

    private static Source source(SppDomain domain, SppService owner, SppServiceCall.ReadKey key) {
        String text = key.text();
        if (key.literal()) return new Source(key.literalType(), text, null, text);
        int dot = text.indexOf('.');
        if (dot < 0) {
            var constant = domain.getConstants().get(text);
            if (constant == null) return null;
            return new Source(constant.getType().getName(), text, constant, constant.getLiteral());
        }
        String root = text.substring(0, dot);
        var matches = owner.getServiceBody().getSppLocalVarList().stream().filter(var -> var.getName().equals(root)).toList();
        if (matches.size() != 1 || matches.get(0).getArrayDimension() != 0
                || !(matches.get(0).getType() instanceof SppClass type)) return null;
        var field = scalarField(type, text.substring(dot + 1));
        return field == null ? null : new Source(field.getType().getName(), NameUtil.getNameCall(text), field, null);
    }

    private static SppField scalarField(SppClass type, String path) {
        int dot = path.indexOf('.');
        var field = type.getField(dot < 0 ? path : path.substring(0, dot));
        if (field == null || field.getArrayDimension() != 0) return null;
        if (dot < 0) return field;
        return field.getType() instanceof SppClass nested ? scalarField(nested, path.substring(dot + 1)) : null;
    }

    private record Source(String type, String java, SppLocalVar field, String literal) { }
}
