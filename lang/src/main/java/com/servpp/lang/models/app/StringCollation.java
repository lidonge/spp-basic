package com.servpp.lang.models.app;

import com.servpp.lang.models.SppDomain;
import java.util.EnumSet;
import java.util.List;

/** APP's collating sequence for SPP string ordering. */
public enum StringCollation {
    BINARY, EBCDIC;

    public static StringCollation resolve(SppDomain domain, List<String> errors) {
        var choices = EnumSet.noneOf(StringCollation.class);
        addChoice(choices, domain);
        if (domain.getProject() != null) {
            for (var owner : domain.getProject().getSppDomainMap().values()) {
                for (var deployed : owner.getRuleBlock().getApplicationDeployments().values()) {
                    if (!deployed.contains(domain.getName())) continue;
                    for (String name : deployed) {
                        var peer = domain.getProject().getDomain(name);
                        if (peer != null) addChoice(choices, peer);
                    }
                }
            }
        }
        if (choices.size() > 1) {
            errors.add("Conflicting APP collations for deployed domain " + domain.getName()
                    + ". Use the same collation in its deployment declarations.");
        }
        return choices.isEmpty() ? BINARY : choices.iterator().next();
    }

    private static void addChoice(EnumSet<StringCollation> choices, SppDomain domain) {
        var declared = domain.getRuleBlock().getStringCollation();
        if (declared != null) choices.add(declared);
    }
}
