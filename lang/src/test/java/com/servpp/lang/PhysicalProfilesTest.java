package com.servpp.lang;

import com.servpp.lang.general.PhysicalProfiles;
import com.servpp.lang.models.SppProject;
import java.nio.file.Files;
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
    @Test public void numericOverrideUsesOnlyAnExplicitAdapter() throws Exception {
        var source = Files.createTempFile("numeric-overrides", ".phy");
        try {
            Files.writeString(source, """
                    domain sample;
                    phy {
                        numeric {
                            override Amount.housingFee with java.math.BigDecimal;
                            override Amount.causingAmount with java.math.BigDecimal using sample.DecimalAdapter;
                        }
                    }
                    """);
            var project = new SppProject();
            var spp = Files.createTempFile("numeric-overrides", ".spp");
            try {
                Files.writeString(spp, "domain sample; Role Amount { decimal housingFee; decimal causingAmount; }");
                new SppCompiler(spp.toFile(), project).compile();
                new AppCompiler(source.toFile(), project).compile();
            } finally {
                Files.deleteIfExists(spp);
            }
            var bindings = project.getDomain("sample").getRuleBlock().getNumericBindings();
            assertEquals(2, bindings.size());
            assertEquals("Amount.housingFee", bindings.get(0).target());
            assertNull(bindings.get(0).adapter());
            assertEquals("sample.DecimalAdapter", bindings.get(1).adapter());
        } finally {
            Files.deleteIfExists(source);
        }
    }
}
