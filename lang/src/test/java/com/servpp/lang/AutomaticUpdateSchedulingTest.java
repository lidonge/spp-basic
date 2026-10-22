package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutomaticUpdateSchedulingTest {
    private SppProject parse(String configuration) throws Exception {
        var project = new SppProject();
        var source = Files.createTempFile("auto-updates", ".spp");
        var app = Files.createTempFile("auto-updates", ".app");
        try {
            Files.writeString(source, "domain demo; Role First { String code; } Role Second { String code; } "
                    + "Contract Agreement { First first; Second second; } "
                    + "Scenario serial save(First, Second, Agreement) { transaction { updateFirst; updateSecond; } }");
            new SppCompiler(source.toFile(),project).compile();
            project.getSppDomainMap().values().forEach(SppDomain::dealEntityToRoleMaps);
            Files.writeString(app,"domain demo; transactions { mode: LOCAL; resource: records; " + configuration + " }");
            new AppCompiler(app.toFile(),project).compile();
            DomainDeploymentValidator.validate(project);
            project.getDomain("demo").validateAppDeclarations();
            return project;
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(app); }
    }
    @Test public void appOptionGeneratesParallelBoundaryAndPreparedWriteMarkers() throws Exception {
        var project = parse("ordinaryUpdates: PARALLEL;");
        assertTrue(project.getDomain("demo").getRuleBlock().isParallelOrdinaryUpdates());
        var service = (SppService)project.getDomain("demo").getSppClass("Save");
        String java = service.getScenarioBodyJava();
        assertTrue(java,java.contains("parallelOrdinaryUpdates() { return true; }"));
        assertTrue(java,java.contains("isPreparedOrdinaryUpdate() { return true; }"));
    }
    @Test public void existingPolicyKeepsSerialDefault() throws Exception {
        var project = parse("");
        assertFalse(project.getDomain("demo").getRuleBlock().isParallelOrdinaryUpdates());
        String java = ((SppService)project.getDomain("demo").getSppClass("Save")).getScenarioBodyJava();
        assertFalse(java.contains("parallelOrdinaryUpdates()"));
    }
    @Test public void environmentSelectionOverridesDomainDefault() throws Exception {
        var project = parse("ordinaryUpdates: PARALLEL;");
        var file = Files.createTempFile("scheduling-environment", ".app");
        try {
            Files.writeString(file,"domain demo; application example { environment develop { deploy demo; "
                    + "transactions { mode: LOCAL; resource: records; ordinaryUpdates: SERIAL; } } "
                    + "environment testing { deploy demo; transactions { ordinaryUpdates: PARALLEL; } } }");
            new AppCompiler(file.toFile(),project).compile();
            DomainDeploymentValidator.validate(project);
            assertFalse(project.getDomain("demo").getRuleBlock().isParallelOrdinaryUpdates());
            assertEquals("PARALLEL",project.getDomain("demo").getRuleBlock().getDeploymentEnvironments()
                    .get("example").get("testing").getOrdinaryUpdates());
        } finally { Files.deleteIfExists(file); }
    }
    @Test public void invalidAndRepeatedOptionsAreRejected() throws Exception {
        assertFalse(parse("ordinaryUpdates: FAST;").getDomain("demo").getAppParseErrors().isEmpty());
        assertThrows(Exception.class, () -> parse("ordinaryUpdates: PARALLEL; ordinaryUpdates: SERIAL;"));
    }
}
