package com.servpp.service.technical;

import com.servpp.service.repository.ObjectPropertyAccess;
import com.servpp.service.runtime.PhysicalRuntimeCatalog;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Record-level interception: value producers run before an insert and fill an empty field. */
public final class RecordInterceptors {
    /** Generated from APP {@code intercept} and from the {@code auto ... serial/context} sugar alike. */
    public record Binding(String service, String implementation, Map<String, String> configuration) {
        public Binding { configuration = Map.copyOf(configuration); }
    }
    private static final Map<Binding, TechnicalService> INSTANCES = new ConcurrentHashMap<>();
    private RecordInterceptors() { }

    /** Fills {@code fieldPath} of {@code target} (or of each element when it is a collection) when it is empty. */
    public static void insert(Object target, String entityName, String fieldPath, Binding binding) {
        insert(target, entityName, fieldPath, binding, null);
    }

    /**
     * As above; {@code repository} identifies the resource whose business transaction a value producer may join
     * (see {@link FieldValueContext#connection()}).
     */
    public static void insert(Object target, String entityName, String fieldPath, Binding binding,
                              java.util.function.Supplier<com.servpp.service.repository.ObjectRepository> repository) {
        if (target instanceof Iterable<?> values) { for (Object item : values) insert(item, entityName, fieldPath, binding, repository); return; }
        if (target == null) throw new IllegalStateException("RECORD_INTERCEPTION_TARGET_REQUIRED");
        String[] parts = fieldPath.split("\\.");
        Object owner = target;
        for (int i = 0; i < parts.length - 1; i++) owner = ObjectPropertyAccess.read(owner, parts[i]);
        if (owner == null) throw new IllegalStateException("RECORD_INTERCEPTION_OWNER_REQUIRED: " + fieldPath);
        String property = parts[parts.length - 1];
        Object current = ObjectPropertyAccess.read(owner, property);
        if (!empty(current)) return;
        Method setter = setter(owner, property);
        Class<?> type = setter.getParameterTypes()[0];
        var resolved = resolve(binding.configuration());
        var service = instance(binding, FieldValueInterceptor.class);
        Object produced = ((FieldValueInterceptor) service).produce(
                new FieldValueContext(entityName, property, type, owner, resolved, repository));
        try { setter.invoke(owner, convert(produced, type, binding.service(), property)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("RECORD_INTERCEPTION_ASSIGNMENT_FAILED: " + property, failure); }
    }

    /** One shared instance per binding, configured once; the contract type is verified. */
    public static TechnicalService instance(Binding binding, Class<? extends TechnicalService> contract) {
        TechnicalService service = INSTANCES.get(binding);
        if (service == null) {
            // Not computeIfAbsent: a service may configure nested services (an engine) while it is being created.
            synchronized (INSTANCES) {
                service = INSTANCES.get(binding);
                if (service == null) { service = instantiate(binding); INSTANCES.put(binding, service); }
            }
        }
        if (!contract.isInstance(service))
            throw new IllegalArgumentException("TECHNICAL_SERVICE_CONTRACT_REQUIRED: " + binding.service() + " must implement " + contract.getSimpleName());
        return service;
    }
    /** Drops cached instances; for tests that reconfigure a binding. */
    public static void reset() { INSTANCES.clear(); }

    private static TechnicalService instantiate(Binding binding) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> type = Class.forName(binding.implementation(), true, loader == null ? RecordInterceptors.class.getClassLoader() : loader);
            if (!TechnicalService.class.isAssignableFrom(type)) throw new IllegalArgumentException("TECHNICAL_SERVICE_INTERFACE_REQUIRED: " + binding.service());
            TechnicalService service = (TechnicalService) type.getConstructor().newInstance();
            service.configure(resolve(binding.configuration()));
            return service;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TECHNICAL_SERVICE_INITIALIZATION_FAILED: " + binding.service(), failure);
        }
    }
    /** A field is empty when it is null, blank text, or a zero number (the unset state of a primitive). */
    private static boolean empty(Object value) {
        if (value == null) return true;
        if (value instanceof Number number) return number.doubleValue() == 0;
        return value.toString().isBlank();
    }
    private static Map<String, String> resolve(Map<String, String> configuration) {
        var resolved = new LinkedHashMap<String, String>();
        configuration.forEach((name, value) -> resolved.put(name, PhysicalRuntimeCatalog.resolveConfigValue(value)));
        return Map.copyOf(resolved);
    }
    private static Method setter(Object owner, String property) {
        String name = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Method method : owner.getClass().getMethods())
            if (method.getName().equals(name) && method.getParameterCount() == 1) return method;
        throw new IllegalStateException("RECORD_INTERCEPTION_SETTER_MISSING: " + property);
    }
    static Object convert(Object value, Class<?> type, String service, String field) {
        if (value == null) throw new IllegalStateException("FIELD_VALUE_REQUIRED: " + service + " -> " + field);
        if (type.isInstance(value)) return value;
        String text = value.toString();
        try {
            if (type == String.class) return text;
            if (type == long.class || type == Long.class) return value instanceof Number n ? n.longValue() : Long.parseLong(text);
            if (type == int.class || type == Integer.class) {
                long number = value instanceof Number n ? n.longValue() : Long.parseLong(text);
                return Math.toIntExact(number);
            }
            if (type == BigInteger.class) return new BigInteger(text);
            if (type == BigDecimal.class) return new BigDecimal(text);
        } catch (NumberFormatException | ArithmeticException failure) {
            throw new IllegalStateException("FIELD_VALUE_TYPE_MISMATCH: " + service + " -> " + field, failure);
        }
        throw new IllegalStateException("FIELD_VALUE_TYPE_MISMATCH: " + service + " -> " + field);
    }
}
