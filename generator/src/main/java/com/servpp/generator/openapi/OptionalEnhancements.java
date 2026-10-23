package com.servpp.generator.openapi;

import com.servpp.lang.models.SppDomain;
import com.servpp.service.idempotency.BusinessIdempotencyManager;
import com.servpp.service.technical.SystemTechnicalServiceProvider;
import com.servpp.service.technical.TechnicalService;
import com.servpp.service.transaction.UpdateScheduling;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

final class OptionalEnhancements {
    private static final Map<String, String> OPTIONAL_SERVICES = Map.of(
            "com.servpp.service.technical.InvocationTraceService", "invocation tracing is not generated",
            "com.servpp.service.technical.PartialUpdateService", "ordinary updates write complete facts with unchanged revision checks");
    private final ClassLoader loader;

    OptionalEnhancements() {
        this(Thread.currentThread().getContextClassLoader());
    }

    OptionalEnhancements(ClassLoader loader) {
        this.loader = loader;
    }

    Set<String> omittedServices(SppDomain domain) {
        var omitted = new LinkedHashSet<String>();
        domain.getRuleBlock().getTechnicalServices().implementations().forEach((name, implementation) -> {
            if (OPTIONAL_SERVICES.containsKey(implementation.className()) && !available(implementation.className())) {
                omitted.add(name);
            }
        });
        return omitted;
    }

    List<String> warnings(SppDomain domain) {
        var warnings = new ArrayList<String>();
        var rules = domain.getRuleBlock();
        if (!BusinessIdempotencyManager.isAvailable()) {
            if (rules.isSystemServiceEnabled("idempotency")) {
                warnings.add(warning(domain, "idempotency", "request replay is unavailable; each invocation executes normally"));
            }
            if (rules.isSystemServiceEnabled("duplicateGuard")) {
                warnings.add(warning(domain, "duplicateGuard", "automatic duplicate-submission protection is unavailable"));
            }
            if ("memory".equals(rules.getPhysicalConfiguration().get("requestIdempotencyAdapter"))) {
                warnings.add(warning(domain, "requestIdempotencyAdapter", "the optional memory store is ignored; no request store is provisioned"));
            }
        }
        if (rules.isSystemServiceEnabled("partialUpdate")
                && !ServiceLoader.load(SystemTechnicalServiceProvider.class, loader).iterator().hasNext()) {
            warnings.add(warning(domain, "partialUpdate", "automatic partial updates are unavailable; basic full-fact updates and revision checks remain; explicit custom bindings are unchanged"));
        }
        if (rules.isParallelOrdinaryUpdates() && !UpdateScheduling.isAvailable()) {
            warnings.add(warning(domain, "ordinaryUpdates", "automatic update scheduling uses serial execution; explicit SPP parallel semantics are unchanged"));
        }
        for (String name : omittedServices(domain)) {
            String implementation = rules.getTechnicalServices().implementations().get(name).className();
            warnings.add(warning(domain, name, OPTIONAL_SERVICES.get(implementation)));
        }
        return warnings;
    }

    String requestAdapter(String configured) {
        return "memory".equals(configured) && !BusinessIdempotencyManager.isAvailable() ? "jdbc" : configured;
    }

    private boolean available(String implementation) {
        try {
            Class<?> type = Class.forName(implementation, false, loader);
            if (!TechnicalService.class.isAssignableFrom(type)) {
                throw new IllegalStateException("TECHNICAL_SERVICE_INTERFACE_REQUIRED: " + implementation);
            }
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private String warning(SppDomain domain, String feature, String fallback) {
        return "SPP_OPTIONAL_ENHANCEMENT_UNAVAILABLE: domain=" + domain.getName()
                + " feature=" + feature + "; " + fallback + ".";
    }
}
