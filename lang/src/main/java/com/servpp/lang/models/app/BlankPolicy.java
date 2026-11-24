package com.servpp.lang.models.app;

import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppField;
import com.servpp.lang.models.SppLocalVar;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/** Physical forms of the SPP BLANK literal, selected at application generation. */
public record BlankPolicy(Set<String> reads, String write) {
    private static final String EMPTY = "\"\"";
    private static final Set<String> FORMS = Set.of("null", EMPTY, "spaces");
    private static final BlankPolicy DEFAULT = new BlankPolicy(Set.of("null", EMPTY), EMPTY);

    public BlankPolicy {
        reads = Set.copyOf(reads);
        if (!FORMS.containsAll(reads) || !FORMS.contains(write))
            throw new IllegalArgumentException("Blank forms must be null, \"\" or spaces.");
    }

    private static BlankPolicy resolve(SppDomain domain, SppLocalVar target) {
        var override = override(domain, target);
        return override == null ? deploymentDefault(domain) : override;
    }

    private static BlankPolicy override(SppDomain domain, SppLocalVar target) {
        var policies = domain.getRuleBlock().getBlankPolicies();
        if (!(target instanceof SppField field) || field.getParent() == null || policies.isEmpty()) return null;
        String owner = field.getParent().getName();
        var roleField = policies.get(owner + "." + field.getName());
        if (roleField != null) return roleField;
        var table = domain.resolveAppTable(owner);
        if (table == null) return null;
        return policies.getOrDefault(table.getName() + "." + field.getName(), policies.get(table.getName()));
    }

    public static String readJava(SppDomain domain, SppLocalVar target, String receiver, String value) {
        return render(domain, target, receiver, (policy, field) -> policy.matchJava(value));
    }

    public static String writeJava(SppDomain domain, SppLocalVar target, String receiver) {
        return render(domain, target, receiver, BlankPolicy::canonicalValue);
    }

    /** A play projects the original provider value. Select its physical policy at the literal use site. */
    private static String render(SppDomain domain, SppLocalVar target, String receiver,
                                 BiFunction<BlankPolicy, SppLocalVar, String> render) {
        var local = resolve(domain, target);
        if (!(target instanceof SppField field) || field.getParent() == null || domain.getProject() == null
                || override(domain, target) != null) return render.apply(local, target);
        var plays = domain.getRuleBlock().getRolePlays().stream()
                .filter(play -> play.role().equals(field.getParent().getName())
                        && play.fields().containsKey(field.getName())).toList();
        String fallback = null;
        for (var play : plays) {
            var provider = domain.getProject().getDomain(play.providerDomain());
            if (provider == null || !(provider.getSppClass(play.providerType()) instanceof SppClass type)) continue;
            var bound = type.getField(play.fields().get(field.getName()));
            if (bound == null) continue;
            String projected = render.apply(resolve(provider, bound), bound);
            if (plays.size() == 1) return projected;
            if (receiver == null) continue;
            if (fallback == null) fallback = render.apply(local, field);
            if (projected.equals(fallback)) continue;
            String service = play.providerDomain() + ".find" + play.providerType();
            fallback = "(com.servpp.service.runtime.LocalDomainServices.playedBy(" + receiver + ", \""
                    + service + "\") ? " + projected + " : " + fallback + ")";
        }
        return fallback == null ? render.apply(local, target) : fallback;
    }

    /** Application members share the default declared by a member of their deployment. */
    private static BlankPolicy deploymentDefault(SppDomain domain) {
        var own = domain.getRuleBlock().getBlankPolicies().get("");
        if (own != null) return own;
        if (domain.getProject() != null) {
            for (var member : domain.getProject().getSppDomainMap().values()) {
                var rules = member.getRuleBlock();
                for (var deployed : rules.getApplicationDeployments().values()) {
                    if (!deployed.contains(domain.getName())) continue;
                    for (String name : deployed) {
                        var peer = domain.getProject().getDomain(name);
                        if (peer == null) continue;
                        var policy = peer.getRuleBlock().getBlankPolicies().get("");
                        if (policy != null) return policy;
                    }
                }
            }
        }
        return DEFAULT;
    }

    public static void validate(SppDomain domain, List<String> errors) {
        var rules = domain.getRuleBlock();
        for (String target : rules.getBlankPolicies().keySet()) {
            if (target.isEmpty()) continue;
            String[] parts = target.split("\\.", 2);
            var table = rules.getAllTables().get(parts[0]);
            if (parts.length == 1 && table != null) continue;
            var owner = table == null ? domain.getSppClass(parts[0]) : table.getEntity();
            var field = parts.length == 2 && owner instanceof SppClass type ? type.getField(parts[1]) : null;
            if (field == null || field.getArrayDimension() != 0 || !"String".equals(field.getType().getName()))
                errors.add("Blank policy requires a mapped table or scalar String field: " + target);
        }
        if (domain.getProject() == null) return;
        for (var deployed : rules.getApplicationDeployments().values()) {
            long defaults = deployed.stream().distinct().map(domain.getProject()::getDomain)
                    .filter(java.util.Objects::nonNull)
                    .filter(member -> member.getRuleBlock().getBlankPolicies().containsKey("")).count();
            if (defaults > 1) errors.add("Duplicate deployment blank default");
        }
    }

    private String canonicalValue(SppLocalVar target) {
        if ("null".equals(write)) return "(String) null";
        if (!"spaces".equals(write)) return EMPTY;
        if (!(target instanceof SppField field) || field.getDefaultStringLength() == null)
            throw new IllegalArgumentException("Writing BLANK as spaces requires a declared String width.");
        return "\" \".repeat(" + field.getDefaultStringLength() + ")";
    }

    private String matchJava(String value) {
        return "com.servpp.service.runtime.BlankStrings.matches(" + value + ", " + reads.contains("null")
                + ", " + reads.contains(EMPTY) + ", " + reads.contains("spaces") + ")";
    }
}
