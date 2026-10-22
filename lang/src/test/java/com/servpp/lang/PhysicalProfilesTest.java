package com.servpp.lang;

import com.servpp.lang.general.PhysicalProfiles;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhysicalProfilesTest {
    @Test public void preservesCommonFilesAndExplicitProfileSelection() {
        assertTrue(PhysicalProfiles.matches("shared.phy", "prod"));
        assertTrue(PhysicalProfiles.matches("domain.prod.phy", " prod "));
        assertTrue(PhysicalProfiles.matchesExplicit("prod.phy", "prod"));
        assertFalse(PhysicalProfiles.matches("domain.dev.phy", "prod"));
        assertTrue(PhysicalProfiles.matches("domain.dev.phy", ""));
        assertTrue(PhysicalProfiles.matches("domain.dev.phy", null));
        assertTrue(PhysicalProfiles.isQualified("domain.dev.phy"));
        assertFalse(PhysicalProfiles.isQualified("shared.phy"));
    }
}
