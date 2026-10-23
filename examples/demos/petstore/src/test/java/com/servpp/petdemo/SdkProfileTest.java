package com.servpp.petdemo;

import com.servpp.service.idempotency.BusinessIdempotencyManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class SdkProfileTest {
    @Test public void requestedProfileMatchesLoadedProviders() {
        boolean enhanced = Boolean.parseBoolean(System.getProperty("spp.examples.enhanced", "false"));
        assertEquals("Request coordination must match the selected Maven profile",
                enhanced, BusinessIdempotencyManager.isAvailable());
        assertEquals("Official tracing must match the selected Maven profile", enhanced,
                getClass().getClassLoader().getResource("com/servpp/service/technical/InvocationTraceService.class") != null);
    }
}
