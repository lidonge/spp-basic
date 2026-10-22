package com.servpp.service.runtime;

import org.junit.Test;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

/** Runs with only the basic runtime: no commercial provider is on the classpath. */
public class BasicTrackingBoundaryTest {
    @Test public void basicMappingWorksWithoutLoadingAnOfficialTrackingImplementation() {
        assertFalse(PropertyChanges.isAvailable());
        var source = new String[]{"value"};
        var target = new String[]{null};
        PropertyChanges.mapped(source, "value", target, "value", () -> target[0] = source[0]);
        assertEquals("value", target[0]);
        assertEquals(Set.of(), PropertyChanges.paths(target));
        assertTrue(PropertyChanges.checkpoint(List.of(target)).isEmpty());
    }

    @Test public void trackedModelCannotSilentlyRunWithoutItsSelectedImplementation() {
        var failure = assertThrows(IllegalStateException.class, PropertyChanges.State::new);
        assertEquals("PROPERTY_TRACKING_PROVIDER_REQUIRED", failure.getMessage());
    }
}
