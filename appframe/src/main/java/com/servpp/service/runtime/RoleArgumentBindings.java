package com.servpp.service.runtime;

/**
 * How a scenario call passes its arguments: where it passes a role to a parameter of the role's player,
 * the participant that books the call (quantum conservation) must read the player's facts through the role.
 */
public final class RoleArgumentBindings {
    private static final ThreadLocal<LocalDomainServices.RoleArgument[]> CURRENT = new ThreadLocal<>();
    private RoleArgumentBindings() { }

    /** The bindings of the call whose participants are being prepared on this thread, if any. */
    public static LocalDomainServices.RoleArgument[] current() { return CURRENT.get(); }

    public static void during(LocalDomainServices.RoleArgument[] roles, Runnable action) {
        var previous = CURRENT.get();
        if (roles == null) CURRENT.remove(); else CURRENT.set(roles);
        try { action.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
