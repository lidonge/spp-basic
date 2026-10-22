package com.servpp.service.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class GeneratedRoleIdentityContextBinder {
    private static final String ROLE_PARTICIPANTS_RESOURCE = "META-INF/spp/role-participants.properties";
    private static volatile List<RoleBindingPlan> roleBindingPlans;

    private GeneratedRoleIdentityContextBinder() {
    }

    public static void attach(Object request) {
        attach(request, roleBindingPlans());
    }

    static void attach(Object request, List<RoleBindingPlan> plans) {
        if (request == null) {
            return;
        }
        List<RoleBindingPlan> safePlans = plans == null ? List.of() : plans;
        attach(request, request, "", safePlans, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void attach(Object rootRequest, Object currentValue, String propertyPath,
                               List<RoleBindingPlan> plans, Set<Object> visited) {
        if (currentValue == null || !isTraversable(currentValue) || !visited.add(currentValue)) {
            return;
        }
        for (Method roleGetter : currentValue.getClass().getMethods()) {
            if (roleGetter.getParameterCount() != 0 || !roleGetter.getName().startsWith("get")) {
                continue;
            }
            if ("getClass".equals(roleGetter.getName())) {
                continue;
            }
            Object roleValue = invoke(roleGetter, currentValue).orElse(null);
            if (roleValue == null) {
                continue;
            }
            String roleProperty = propertyName(roleGetter.getName());
            String rolePath = propertyPath.isBlank() ? roleProperty : propertyPath + capitalize(roleProperty);
            if (roleValue instanceof Iterable<?> iterable) {
                int index = 0;
                for (Object item : iterable) {
                    attachRoleValue(rootRequest, rolePath, item, index, plans, visited);
                    index++;
                }
                continue;
            }
            if (roleValue.getClass().isArray()) {
                int length = Array.getLength(roleValue);
                for (int index = 0; index < length; index++) {
                    attachRoleValue(rootRequest, rolePath, Array.get(roleValue, index), index, plans, visited);
                }
                continue;
            }
            attachRoleValue(rootRequest, rolePath, roleValue, null, plans, visited);
        }
    }

    private static void attachRoleValue(Object rootRequest, String rolePath, Object roleValue, Integer collectionIndex,
                                        List<RoleBindingPlan> plans, Set<Object> visited) {
        if (roleValue == null) {
            return;
        }
        String roleType = roleValue.getClass().getSimpleName();
        List<RoleBindingPlan> rolePlans = plans.stream()
                .filter(plan -> plan.roleType().equals(roleType))
                .toList();
        List<RoleBindingPlan> bindableRolePlans = rolePlans.stream()
                .filter(plan -> roleBindingApplies(rootRequest, rolePath, roleType, plan))
                .filter(plan -> roleBindingSidecarsDeclared(rootRequest, rolePath, plan))
                .toList();
        if (!bindableRolePlans.isEmpty()) {
            if (bindableRolePlans.stream()
                    .anyMatch(plan -> roleBindingSidecarContainersAvailable(rootRequest, rolePath, plan))) {
                RoleIdentityContext.clear(roleValue);
            }
            if (rolePlans.size() > 1) {
                Set<String> present = new LinkedHashSet<>();
                for (var plan : rolePlans) for (String actor : plan.performerTypes()) {
                    Object value = getter(rootRequest.getClass(),rolePath + actor)
                            .flatMap(method -> invoke(method,rootRequest)).orElse(null);
                    if (collectionIndex != null) value = indexedValue(value,collectionIndex);
                    if (value != null) present.add(actor);
                }
                if (!present.isEmpty()) {
                    var matching = bindableRolePlans.stream().filter(plan -> new LinkedHashSet<>(plan.performerTypes()).equals(present)).toList();
                    if (matching.size() != 1) throw new IllegalStateException("ROLE_PERFORMER_VARIANT_INVALID: " + roleType);
                    attach(rootRequest,rolePath,roleValue,collectionIndex,matching.get(0));
                }
            } else bindableRolePlans.stream()
                    .filter(plan -> attach(rootRequest, rolePath, roleValue, collectionIndex, plan))
                    .findFirst();
        }
        attach(rootRequest, roleValue, rolePath, plans, visited);
    }

    private static boolean roleBindingApplies(Object rootRequest, String rolePath, String roleType,
                                              RoleBindingPlan plan) {
        if (rootRequest == null || rolePath == null || roleType == null || plan == null) {
            return false;
        }
        if (rolePath.equals(propertyName("get" + roleType)) || !rolePath.endsWith(roleType)) {
            return true;
        }
        return plan.performerTypes().stream()
                .allMatch(performerType -> getter(rootRequest.getClass(), rolePath + performerType).isPresent());
    }

    private static boolean roleBindingSidecarsDeclared(Object rootRequest, String rolePath, RoleBindingPlan plan) {
        if (rootRequest == null || rolePath == null || plan == null) {
            return false;
        }
        return plan.performerTypes().stream()
                .allMatch(performerType -> getter(rootRequest.getClass(), rolePath + performerType).isPresent());
    }

    private static boolean roleBindingSidecarContainersAvailable(Object rootRequest, String rolePath,
                                                                 RoleBindingPlan plan) {
        if (rootRequest == null || rolePath == null || plan == null) {
            return false;
        }
        return plan.performerTypes().stream()
                .allMatch(performerType -> getter(rootRequest.getClass(), rolePath + performerType)
                        .flatMap(method -> invoke(method, rootRequest))
                        .isPresent());
    }

    private static boolean attach(Object request, String rolePath, Object roleValue, Integer collectionIndex,
                                  RoleBindingPlan plan) {
        List<Object> performers = new ArrayList<>();
        for (String performerType : plan.performerTypes()) {
            Optional<Method> performerGetter = getter(request.getClass(), rolePath + performerType);
            if (performerGetter.isEmpty()) {
                return false;
            }
            Object performer = invoke(performerGetter.get(), request).orElse(null);
            if (collectionIndex != null) {
                performer = indexedValue(performer, collectionIndex);
            }
            if (performer == null) {
                return false;
            }
            performers.add(performer);
        }
        RoleIdentityContext.attachPerformers(roleValue, plan.roleType(), performers.toArray());
        return true;
    }

    private static Object indexedValue(Object value, int index) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return index < list.size() ? list.get(index) : null;
        }
        if (value instanceof Iterable<?> iterable) {
            int current = 0;
            for (Object item : iterable) {
                if (current == index) {
                    return item;
                }
                current++;
            }
            return null;
        }
        if (value.getClass().isArray()) {
            return index < Array.getLength(value) ? Array.get(value, index) : null;
        }
        return index == 0 ? value : null;
    }

    private static boolean isTraversable(Object value) {
        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || type.isArray()) {
            return false;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Iterable<?> || value instanceof java.util.Map<?, ?>) {
            return false;
        }
        Package valuePackage = type.getPackage();
        String packageName = valuePackage == null ? "" : valuePackage.getName();
        return !packageName.startsWith("java.");
    }

    private static List<RoleBindingPlan> roleBindingPlans() {
        List<RoleBindingPlan> cached = roleBindingPlans;
        if (cached != null) {
            return cached;
        }
        synchronized (GeneratedRoleIdentityContextBinder.class) {
            if (roleBindingPlans == null) {
                roleBindingPlans = loadRoleBindingPlans();
            }
            return roleBindingPlans;
        }
    }

    private static List<RoleBindingPlan> loadRoleBindingPlans() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = classLoader == null
                ? GeneratedRoleIdentityContextBinder.class.getClassLoader()
                        .getResourceAsStream(ROLE_PARTICIPANTS_RESOURCE)
                : classLoader.getResourceAsStream(ROLE_PARTICIPANTS_RESOURCE);
        if (stream == null) {
            return List.of();
        }
        Properties properties = new Properties();
        try (InputStream input = stream) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load generated role participant catalog", e);
        }
        return parseRoleBindingPlans(properties);
    }

    static List<RoleBindingPlan> parseRoleBindingPlans(Properties properties) {
        int count = parseInt(properties.getProperty("roleParticipant.count", "0"), "roleParticipant.count");
        List<RoleBindingPlan> plans = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "roleParticipant." + index + ".";
            String roleType = properties.getProperty(prefix + "roleType");
            String performerTypes = properties.getProperty(prefix + "performerTypes", "");
            if (roleType == null || roleType.isBlank() || performerTypes.isBlank()) {
                continue;
            }
            String normalizedRoleType = roleType.trim();
            List<String> normalizedPerformerTypes = parseList(
                    performerTypes, "Generated role participant catalog role " + normalizedRoleType
                            + " performer type");
            requireUnique("Generated role participant catalog role " + normalizedRoleType
                    + " performer type", normalizedPerformerTypes);
            int performerCount = parseInt(properties.getProperty(prefix + "performerCount",
                    String.valueOf(normalizedPerformerTypes.size())), prefix + "performerCount");
            boolean jointPerformer = parseBoolean(properties.getProperty(prefix + "jointPerformer",
                    String.valueOf(performerCount > 1)), prefix + "jointPerformer");
            validateRoleParticipantShape(prefix, normalizedPerformerTypes, performerCount, jointPerformer);
            plans.add(new RoleBindingPlan(normalizedRoleType, normalizedPerformerTypes));
        }
        return List.copyOf(plans);
    }

    private static void validateRoleParticipantShape(String prefix, List<String> performerTypes,
                                                     int performerCount, boolean jointPerformer) {
        int actualPerformerCount = performerTypes == null ? 0 : performerTypes.size();
        if (performerCount != actualPerformerCount) {
            throw new IllegalStateException("Generated role participant catalog " + prefix
                    + "performerCount must match performerTypes size " + actualPerformerCount
                    + ", but was " + performerCount);
        }
        boolean expectedJointPerformer = performerCount > 1;
        if (jointPerformer != expectedJointPerformer) {
            throw new IllegalStateException("Generated role participant catalog " + prefix
                    + "jointPerformer must be " + expectedJointPerformer
                    + " when performerCount is " + performerCount + ", but was " + jointPerformer);
        }
    }

    private static int parseInt(String value, String label) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                throw new IllegalStateException("Generated role participant catalog " + label
                        + " must be non-negative, but was " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Generated role participant catalog " + label
                    + " must be a number", e);
        }
    }

    private static boolean parseBoolean(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalStateException("Generated role participant catalog " + label
                + " must be true or false, but was " + value);
    }

    private static List<String> parseList(String value, String label) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",", -1)) {
            String normalized = item.trim();
            if (normalized.isEmpty()) {
                throw new IllegalStateException(label + " contains blank value");
            }
            values.add(normalized);
        }
        return List.copyOf(values);
    }

    private static void requireUnique(String label, List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (!seen.add(value)) {
                throw new IllegalStateException(label + " declares duplicate value " + value);
            }
        }
    }

    private static Optional<Method> getter(Class<?> type, String propertyName) {
        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            return Optional.of(type.getMethod("get" + suffix));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static Optional<Object> invoke(Method method, Object target) {
        try {
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read generated role identity metadata", e);
        }
    }

    private static String propertyName(String getterName) {
        String suffix = getterName.substring(3);
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    record RoleBindingPlan(String roleType, List<String> performerTypes) {
        RoleBindingPlan {
            performerTypes = performerTypes == null ? List.of() : performerTypes.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
        }
    }
}
