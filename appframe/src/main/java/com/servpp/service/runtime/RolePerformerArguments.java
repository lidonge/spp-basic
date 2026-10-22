package com.servpp.service.runtime;

import java.util.*;

/** A reverse take call uses the actual bound performer, not a lookup by matching business facts. */
final class RolePerformerArguments {
    private RolePerformerArguments() { }

    private static Class<?> roleType(RoleBoundArguments.Adapter adapter) {
        return Arrays.stream(adapter.mapper().getClass().getMethods())
                .filter(method -> method.getName().equals("entityToRole") && method.getParameterCount() == 2)
                .map(method -> method.getParameterTypes()[1]).findFirst().orElseThrow();
    }

    static Object performer(Object participant, RoleBoundArguments.Adapter adapter) {
        Class<?> roleType = roleType(adapter);
        Object role = LocalDomainServices.roleProjection(participant, roleType);
        var bound = RoleIdentityContext.performers(role, roleType.getSimpleName()).orElse(List.of());
        var matches = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        bound.stream().filter(adapter.viewType()::isInstance).forEach(matches::add);
        if (matches.size() > 1) throw new IllegalStateException("ROLE_PERFORMER_AMBIGUOUS");
        var definition = new MaterializedRolePerformers.Definition(roleType, List.of(adapter.viewType()), adapter.mapper());
        var performers = MaterializedRolePerformers.materialize(definition, role);
        RoleIdentityContext.attachPerformers(participant, roleType.getSimpleName(), performers.values().toArray());
        return performers.get(adapter.performer());
    }

    static void merge(Object participant, Object performer, RoleBoundArguments.Adapter adapter, boolean query) {
        Class<?> roleType = roleType(adapter);
        Object role = LocalDomainServices.roleProjection(participant, roleType);
        try {
            adapter.mapper().getClass().getMethod("entityToRole", Map.class, roleType)
                    .invoke(adapter.mapper(), Map.of(adapter.performer(), performer), role);
            RoleIdentityContext.attachPerformers(role, roleType.getSimpleName(), performer);
            RoleIdentityContext.attachPerformers(participant, roleType.getSimpleName(), performer);
            if (query) ParticipantIdentity.confirm(role, ParticipantIdentity.isIdentified(performer));
            LocalDomainServices.mergeRoleProjection(participant, role, query);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("ROLE_PERFORMER_MAPPING_FAILED", failure);
        }
    }
}
