package com.servpp.lang.models.app;

import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppService;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Record-level interception. {@code intercept Entity { insert: service -> field; }} and the sugar
 * {@code auto Entity.field serial|context ...} both become one hook: a technical service that produces the field value
 * before the insert. The sugar only selects the built-in service classes and fills their configuration from the APP
 * rule and the PHY engine binding.
 */
public final class RecordInterceptions {
    private RecordInterceptions() { }
    public static final String SERIAL_SERVICE = "com.servpp.service.serial.SerialValueService";
    public static final String CONTEXT_SERVICE = "com.servpp.service.serial.ContextValueService";
    static final String VALUE_CONTRACT = "com.servpp.service.technical.FieldValueInterceptor";
    static final String ENGINE_CONTRACT = "com.servpp.service.serial.SerialEngine";
    static final String GAPLESS_CONTRACT = "com.servpp.service.serial.GaplessSerialEngine";
    static final Set<String> CONTEXT_NAMES = Set.of("transactionLogNumber", "auditLogNumber", "nonAccountingLogNumber", "triggerSequence");
    static final Set<String> VALUE_TYPES = Set.of("String", "int", "long", "BigInteger", "BigDecimal", "decimal");
    static final Set<String> NUMERIC_TYPES = Set.of("short", "int", "long", "BigInteger");
    public static final Pattern FORMAT_PART = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}");

    public record Hook(String entity, String field, String service, String implementation, Map<String, String> configuration) { }

    public static boolean isRecordPhase(String phase) { return phase.equals("insert") || phase.equals("update"); }

    /** Validates the declarations and attaches the resulting value producers to each insert scope. */
    public static List<String> apply(SppDomain domain) {
        var errors = new ArrayList<String>();
        var block = domain.getRuleBlock();
        var technical = block.getTechnicalServices();
        var hooks = new LinkedHashMap<String, Hook>();
        var classes = new HashMap<String, SppClass>();
        domain.getMapsOfClass().forEach((name, unit) -> { if (unit instanceof SppClass type) classes.put(name, type); });

        for (var rule : block.getSerialRules().values()) validateRule(rule, technical, errors);

        for (var hook : technical.hooks()) {
            if (!isRecordPhase(hook.phase())) {
                if (hook.field() != null) errors.add("Interception phase " + hook.phase() + " does not take '-> field'.");
                continue;
            }
            if (hook.phase().equals("update")) { errors.add("Record interception phase update is not supported; use insert."); continue; }
            if (hook.field() == null) { errors.add("Record interception insert requires '-> field'."); continue; }
            if (hook.services().size() != 1) { errors.add("Record interception " + hook.phase() + " takes exactly one technical service."); continue; }
            String service = hook.services().get(0);
            if (!technical.names().contains(service)) { errors.add("Undeclared technical service " + service); continue; }
            var implementation = technical.implementations().get(service);
            if (implementation == null) { errors.add("Technical service " + service + " requires a PHY implementation."); continue; }
            if (Boolean.FALSE.equals(implementsContract(implementation.className(), VALUE_CONTRACT)))
                errors.add("Technical service " + service + " does not implement FieldValueInterceptor, which a record interception requires.");
            for (String target : hook.targets()) {
                var entity = classes.get(target);
                if (entity == null) { errors.add("Unknown interception entity " + target); continue; }
                var field = scalarField(entity, hook.field(), errors, "interception " + service + " -> " + target + "." + hook.field());
                if (field == null) continue;
                String type = field.getType().getName();
                if (!VALUE_TYPES.contains(type))
                    errors.add("Interception " + service + " cannot fill " + target + "." + hook.field() + " of type " + type
                            + "; use String, int, long, BigInteger or BigDecimal.");
                else put(hooks, new Hook(target, hook.field(), service, implementation.className(), implementation.configuration()), errors);
            }
        }

        for (var group : block.getAppTables()) for (var table : group.getAppTableList()) {
            var entity = table.getEntity();
            if (entity == null) continue;
            for (var auto : table.getRecordAutos().entrySet()) {
                String path = entity.getName() + "." + auto.getKey();
                var field = scalarField(entity, auto.getKey(), errors, "auto " + path);
                if (field == null) continue;
                String type = field.getType().getName();
                if (auto.getValue().kind().equals("context")) {
                    String name = auto.getValue().source();
                    if (!CONTEXT_NAMES.contains(name)) errors.add("Unknown context identifier " + name + " for auto " + path
                            + "; expected one of " + new TreeSet<>(CONTEXT_NAMES) + ".");
                    else if (!Set.of("String", "int", "long").contains(type))
                        errors.add("auto " + path + " context " + name + " requires a String, int or long field, not " + type + ".");
                    else put(hooks, new Hook(entity.getName(), auto.getKey(), "context." + name, CONTEXT_SERVICE,
                            Map.of("name", name)), errors);
                    continue;
                }
                var rule = block.getSerialRules().get(auto.getValue().source());
                if (rule == null) { errors.add("Unknown serial rule " + auto.getValue().source() + " for auto " + path); continue; }
                boolean ok = true;
                for (String scope : rule.getScope()) {
                    var scopeField = entity.getField(scope);
                    if (scopeField == null || scopeField.getArrayDimension() != 0) {
                        errors.add("Serial rule " + rule.getName() + " scope field " + scope + " is not a field of " + entity.getName() + ".");
                        ok = false;
                    } else if (scope.equals(auto.getKey())) {
                        errors.add("Serial rule " + rule.getName() + " scope must not include the serial field " + path + ".");
                        ok = false;
                    }
                }
                ok &= checkSerialType(rule, path, type, errors);
                if (!ok) continue;
                var config = new LinkedHashMap<String, String>();
                config.put("rule", rule.getName());
                config.put("scope", String.join(",", rule.getScope()));
                config.put("min", Long.toString(rule.getMin()));
                config.put("max", Long.toString(rule.getMax()));
                config.put("wrap", Boolean.toString(rule.isWrap()));
                config.put("gapless", Boolean.toString(rule.isGapless()));
                if (rule.getFormat() != null) config.put("format", rule.getFormat());
                var engine = technical.implementations().get(rule.getEngine());
                if (engine == null) continue; // reported by validateRule
                config.put("engine.name", rule.getEngine());
                config.put("engine.class", engine.className());
                engine.configuration().forEach((key, value) -> config.put("engine.config." + key, value));
                put(hooks, new Hook(entity.getName(), auto.getKey(), "serial." + rule.getName(), SERIAL_SERVICE, config), errors);
            }
        }

        for (var unit : domain.getMapsOfClass().values()) if (unit instanceof SppService service)
            for (String assigned : service.assignedFieldReferences())
                if (hooks.containsKey(assigned))
                    errors.add("Service " + service.getFuncName() + " assigns " + assigned
                            + ", which a technical service fills on insert; SPP must not assign it.");

        if (hooks.isEmpty()) return errors;
        for (var unit : domain.getMapsOfClass().values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null || service.hasLocalServiceBinding()) continue;
            var scope = service.getScopeItem();
            for (var write : scope.getPersistenceRules()) {
                if (!"insert".equals(write.operation()) && !"insertIfAbsent".equals(write.operation())) continue;
                for (var hook : hooks.values()) {
                    if (!hook.entity().equals(write.objectType())) continue;
                    boolean clash = scope.getBusinessNumbers().stream().anyMatch(number ->
                            number.target().argumentIndex() == write.argumentIndex() && number.target().getObjectIdPath().equals(hook.field()));
                    if (clash) { errors.add("Field " + hook.entity() + "." + hook.field() + " has both a business number and a technical value."); continue; }
                    boolean present = scope.getRecordValues().stream().anyMatch(value ->
                            value.target().argumentIndex() == write.argumentIndex() && value.field().equals(hook.field())
                            && value.entity().equals(hook.entity()));
                    if (!present) scope.getRecordValues().add(new ScopeItem.RecordValue(
                            new RepositoryHydrationRule.KeyTarget(write.argumentIndex(), hook.entity() + "." + hook.field()),
                            hook.entity(), hook.field(), hook.service(), hook.implementation(), hook.configuration()));
                }
            }
        }
        return errors;
    }

    private static void put(Map<String, Hook> hooks, Hook hook, List<String> errors) {
        if (hooks.putIfAbsent(hook.entity() + "." + hook.field(), hook) != null)
            errors.add("Field " + hook.entity() + "." + hook.field() + " is filled by more than one technical declaration.");
    }

    private static com.servpp.lang.models.SppField scalarField(SppClass entity, String name, List<String> errors, String what) {
        var field = entity.getField(name);
        if (field == null || field.getArrayDimension() != 0) {
            errors.add("Unknown or non-scalar field " + entity.getName() + "." + name + " in " + what + ".");
            return null;
        }
        return field;
    }

    private static void validateRule(AppSerialRule rule, TechnicalServices technical, List<String> errors) {
        String label = "Serial rule " + rule.getName();
        if (!technical.names().contains(rule.getEngine()))
            errors.add(label + " names unknown engine " + rule.getEngine() + "; declare it in 'technical services' and bind it in PHY.");
        else if (!technical.implementations().containsKey(rule.getEngine()))
            errors.add(label + " engine " + rule.getEngine() + " requires a PHY implementation.");
        else {
            String className = technical.implementations().get(rule.getEngine()).className();
            if (Boolean.FALSE.equals(implementsContract(className, ENGINE_CONTRACT)))
                errors.add(label + " engine " + rule.getEngine() + " (" + className + ") does not implement SerialEngine.");
            else if (rule.isGapless() && Boolean.FALSE.equals(implementsContract(className, GAPLESS_CONTRACT)))
                errors.add(label + " is gapless, but engine " + rule.getEngine() + " (" + className + ") cannot allocate inside the business transaction.");
        }
        if (rule.getFormat() != null) {
            var seen = new ArrayList<String>();
            Matcher matcher = FORMAT_PART.matcher(rule.getFormat());
            String rest = matcher.replaceAll("");
            if (rest.contains("{") || rest.contains("}")) errors.add(label + " format has an unbalanced brace.");
            matcher.reset();
            int sequences = 0;
            while (matcher.find()) {
                String name = matcher.group(1), argument = matcher.group(2);
                if (name.equals("seq")) {
                    sequences++;
                    if (argument != null && !argument.isBlank()) {
                        if (!argument.matches("[0-9]{1,2}") || Integer.parseInt(argument) < 1 || Integer.parseInt(argument) > 18)
                            errors.add(label + " format {seq:N} requires N between 1 and 18.");
                        else if (Long.toString(rule.getMax()).length() > Integer.parseInt(argument) && rule.isRangeDeclared())
                            errors.add(label + " format width " + argument + " cannot hold the range maximum " + rule.getMax() + ".");
                    }
                } else if (!rule.getScope().contains(name))
                    errors.add(label + " format refers to " + name + ", which is not a scope field.");
            }
            if (sequences != 1) errors.add(label + " format must contain {seq} exactly once.");
        }
    }

    private static boolean checkSerialType(AppSerialRule rule, String path, String type, List<String> errors) {
        if (rule.getFormat() != null) {
            if (!type.equals("String")) {
                errors.add("auto " + path + " serial " + rule.getName() + " has a format, so the field must be String, not " + type + ".");
                return false;
            }
            return true;
        }
        if (!NUMERIC_TYPES.contains(type)) {
            errors.add("auto " + path + " serial " + rule.getName() + " has no format, so the field must be short, int, long or BigInteger, not " + type + ".");
            return false;
        }
        long limit = switch (type) { case "short" -> Short.MAX_VALUE; case "int" -> Integer.MAX_VALUE; default -> Long.MAX_VALUE; };
        if (rule.getMax() > limit) {
            errors.add("auto " + path + " serial " + rule.getName() + " range maximum " + rule.getMax() + " does not fit " + type + ".");
            return false;
        }
        return true;
    }

    /** True or false when the class is loadable here; null when it is not (checked again at deployment). */
    static Boolean implementsContract(String className, String contract) {
        ClassLoader[] loaders = {Thread.currentThread().getContextClassLoader(), RecordInterceptions.class.getClassLoader()};
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try {
                Class<?> type = Class.forName(className, false, loader);
                return implementsByName(type, contract, new HashSet<>());
            } catch (ClassNotFoundException | LinkageError absent) { /* try the next loader */ }
        }
        return null;
    }
    private static boolean implementsByName(Class<?> type, String contract, Set<Class<?>> seen) {
        if (type == null || !seen.add(type)) return false;
        if (type.getName().equals(contract)) return true;
        for (Class<?> parent : type.getInterfaces()) if (implementsByName(parent, contract, seen)) return true;
        return implementsByName(type.getSuperclass(), contract, seen);
    }
}
