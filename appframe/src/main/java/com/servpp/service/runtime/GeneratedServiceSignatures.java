package com.servpp.service.runtime;

import com.servpp.service.Service;
import java.lang.reflect.Method;
import java.util.*;

/** Class-scoped structural reflection only; never caches mutable service/deployment policy. */
final class GeneratedServiceSignatures {
    private GeneratedServiceSignatures() { }
    private static final ClassValue<Map<String,List<String>>> SIGNATURES = new ClassValue<>() {
        @Override protected Map<String,List<String>> computeValue(Class<?> type) {
            var declared = candidates(type.getDeclaredMethods());
            var inherited = candidates(type.getMethods());
            var names = new HashSet<>(declared.keySet()); names.addAll(inherited.keySet());
            var result = new HashMap<String,List<String>>();
            for (String name : names) {
                var methods = declared.getOrDefault(name,List.of());
                if (methods.size() != 1) methods = inherited.getOrDefault(name,List.of());
                if (methods.size() == 1) result.put(name,methods.get(0));
            }
            return Map.copyOf(result);
        }
    };
    static List<String> parameters(Service service) {
        String name = service.getName();
        return name == null ? List.of() : SIGNATURES.get(service.getClass()).getOrDefault(name,List.of());
    }
    private static Map<String,List<List<String>>> candidates(Method[] methods) {
        var result = new HashMap<String,List<List<String>>>();
        for (Method method : methods) {
            if (method.isSynthetic()) continue;
            var parameters = Arrays.stream(method.getParameterTypes()).map(Class::getName).toList();
            result.computeIfAbsent(method.getName(),ignored -> new ArrayList<>()).add(parameters);
        }
        return result;
    }
}
