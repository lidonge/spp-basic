package com.servpp.lang;

import com.servpp.lang.models.SppDomain;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class SystemServicesPolicyTest {
    private SppDomain parse(String policy) throws Exception {
        var file = Files.createTempFile("system-services-", ".app");
        Files.writeString(file, "domain sample; " + policy);
        var project = new com.servpp.lang.models.SppProject();
        new AppCompiler(file.toFile(), project).compile();
        return project.getDomain("sample");
    }

    @Test public void switchesDefaultOnAndCanBeDisabledIndependently() throws Exception {
        for (String selected : java.util.List.of("idempotency", "duplicateGuard", "propertyTracking", "partialUpdate", "optimisticConcurrency")) {
            assertTrue(parse("").getRuleBlock().isSystemServiceEnabled(selected));
            var domain = parse("system services { " + selected + ": false; }");
            assertTrue(domain.getAppParseErrors().toString(), domain.getAppParseErrors().isEmpty());
            for (String name : java.util.List.of("idempotency", "duplicateGuard", "propertyTracking", "partialUpdate", "optimisticConcurrency"))
                assertEquals(name, !name.equals(selected), domain.getRuleBlock().isSystemServiceEnabled(name));
            assertTrue(parse("system services { " + selected + ": true; }").getRuleBlock().isSystemServiceEnabled(selected));
        }
    }

    @Test public void unknownAndDuplicateSwitchesAreRejected() throws Exception {
        assertTrue(parse("system services { unknown: false; }").getAppParseErrors().toString().contains("Unknown system service unknown"));
        var duplicate = parse("system services { idempotency: false; } system services { idempotency: true; }");
        assertFalse(duplicate.getRuleBlock().isSystemServiceEnabled("idempotency"));
        assertTrue(duplicate.getAppParseErrors().toString().contains("Duplicate idempotency"));
        var legacy = parse("propertyTracking: false; system services { propertyTracking: true; }");
        assertFalse(legacy.getRuleBlock().isPropertyTracking());
        assertTrue(legacy.getAppParseErrors().toString().contains("Duplicate propertyTracking"));
        assertThrows(java.io.IOException.class, () -> parse("system services { idempotency: disabled; }"));
    }
}
