package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestPolicyDeclarationTest {
    private SppDomain compile(String policy, String scope) throws Exception {
        var spp = Files.createTempFile("spp-request-policy", ".spp");
        var app = Files.createTempFile("spp-request-policy", ".app");
        try {
            Files.writeString(spp, "domain rules; Role Buyer { String membershipNumber; int quantity; } "
                    + "atomic calculate compute(Buyer buyer) {} Scenario serial submit(Buyer buyer) { compute(buyer); }");
            Files.writeString(app, "domain rules; " + policy + " scope { local { " + scope + " } }");
            var project = new SppProject(); new SppCompiler(spp.toFile(), project).compile(); new AppCompiler(app.toFile(), project).compile();
            return project.getSppDomainMap().get("rules");
        } finally { Files.deleteIfExists(spp); Files.deleteIfExists(app); }
    }
    @Test public void multipleFactPathsAreValidatedAndGeneratedTogether() throws Exception {
        var domain = compile("", "duplicate key(buyer.membershipNumber) facts(buyer.quantity, buyer.membershipNumber) submit;");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        var service = (SppService)domain.getSppClass("Submit");
        assertEquals(java.util.List.of("buyer.quantity","buyer.membershipNumber"),service.getScopeItem().getIdempotencyFactsPaths());
        assertNull(service.getScopeItem().getIdempotencyFactsPath());
        assertTrue(service.getDeclaredIdempotencyMethods().contains("getDeclaredIdempotencyFactsPaths"));
        assertTrue(service.getDeclaredIdempotencyMethods().contains("java.util.List.of(\"buyer.quantity\",\"buyer.membershipNumber\")"));
    }
    @Test public void unknownSecondFactPathIsRejected() throws Exception {
        assertTrue(compile("", "duplicate key(buyer.membershipNumber) facts(buyer.quantity, buyer.unknown) submit;")
                .validateAppDeclarations().stream().anyMatch(message -> message.contains("buyer.unknown")));
    }
    @Test public void durationsAndGuardAreDeclaredInApp() throws Exception {
        var domain = compile("requests { idempotency retention \"P7D\"; duplicateWindow \"PT10S\"; }",
                "duplicate key(buyer.membershipNumber) facts(buyer.quantity) window \"PT10S\" submit;");
        assertEquals("P7D", domain.getRuleBlock().getRequestPolicies().get("requestIdempotencyRetention"));
        assertEquals("PT10S", domain.getRuleBlock().getRequestPolicies().get("requestDuplicateWindow"));
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertFalse(((SppService)domain.getSppClass("Submit")).isUpdateEntry());
        assertEquals("PT10S", ((SppService)domain.getSppClass("Submit")).getScopeItem().getDuplicateWindow());
    }
    @Test public void atomicServiceCannotHaveUiDuplicateGuard() throws Exception {
        assertTrue(compile("", "duplicate key(buyer.membershipNumber) facts(buyer.quantity) compute;")
                .validateAppDeclarations().stream().anyMatch(message -> message.contains("synchronous local scenario")));
    }
    @Test public void nonpositiveDurationIsRejected() throws Exception {
        assertTrue(compile("requests { duplicateWindow \"PT0S\"; }", "").validateAppDeclarations()
                .stream().anyMatch(message -> message.contains("positive ISO-8601 duration")));
    }
    @Test public void readableDurationsUseExistingRuntimeValues() throws Exception {
        var domain = compile("requests { idempotency retention 3 days; duplicateWindow 5 seconds; }",
                "duplicate key(buyer.membershipNumber) facts(buyer.quantity) window 10 seconds submit;");
        assertEquals("P3D", domain.getRuleBlock().getRequestPolicies().get("requestIdempotencyRetention"));
        assertEquals("PT5S", domain.getRuleBlock().getRequestPolicies().get("requestDuplicateWindow"));
        assertEquals("PT10S", ((SppService)domain.getSppClass("Submit")).getScopeItem().getDuplicateWindow());
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
    }
    @Test public void singularUnitsAndMillisecondsAreSupported() throws Exception {
        for (String[] example : new String[][]{{"1 day","P1D"},{"1 hour","PT1H"},{"2 minutes","PT2M"},{"1 second","PT1S"},{"500 milliseconds","PT0.5S"}})
            assertEquals(example[1],compile("requests { duplicateWindow " + example[0] + "; }", "")
                    .getRuleBlock().getRequestPolicies().get("requestDuplicateWindow"));
    }
    @Test public void unsupportedUnitsAndZeroAreRejected() throws Exception {
        for (String duration : new String[]{"0 days", "1 month", "999999999999999999999 days"})
            assertTrue(compile("requests { duplicateWindow " + duration + "; }", "").validateAppDeclarations()
                    .stream().anyMatch(message -> message.contains("positive duration")));
    }
}
