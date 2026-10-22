package com.servpp.lang;
import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;
public class PersistenceDeclarationTest {
    private SppDomain compile(String parameters,String modifiers) throws Exception {
        var spp=Files.createTempFile("persistence", ".spp"); var app=Files.createTempFile("persistence", ".app");
        try {
            Files.writeString(spp,"domain rules; Role Stored { string id; long version; } atomic update store("+parameters+");");
            Files.writeString(app,"domain rules; scope { local { "+(modifiers.startsWith("quantum ") ? "" : "at resource(records) ")+modifiers+" store; } }");
            SppProject p=new SppProject(); new SppCompiler(spp.toFile(),p).compile(); new AppCompiler(app.toFile(),p).compile();
            return p.getSppDomainMap().get("rules");
        } finally { Files.deleteIfExists(spp); Files.deleteIfExists(app); }
    }
    @Test public void deleteSupportsCompositeCollectionsAndRejectsUpdateVersionGuards() throws Exception {
        var scalar=compile("Stored record", "persist(delete, argument(0, Stored.id))");
        assertTrue(scalar.validatePersistenceDeclarations().toString(), scalar.validatePersistenceDeclarations().isEmpty());
        assertTrue(((SppService)scalar.getSppClass("Store")).getPersistenceRuleMethods().contains("Operation.DELETE"));
        assertTrue(compile("Stored[] records", "persist(delete, argument(0, Stored.id), argument(0, Stored.version))")
                .validatePersistenceDeclarations().isEmpty());
        assertFalse(compile("Stored before, Stored after", "persist(delete, argument(1, Stored.id)) compareVersion(argument(0, Stored.version), argument(1, Stored.version))")
                .validatePersistenceDeclarations().isEmpty());
    }
    @Test public void generatesWritesAndVersionGuardInsideInvocation() throws Exception {
        var domain=compile("Stored before, Stored after","persist(update, argument(1, Stored.id)) compareVersion(argument(0, Stored.version), argument(1, Stored.version))");
        assertTrue(domain.validatePersistenceDeclarations().toString(),domain.validatePersistenceDeclarations().isEmpty());
        var service=(SppService)domain.getSppClass("Store");
        assertTrue(service.getPersistenceRuleMethods().contains("Operation.UPDATE"));
        assertTrue(service.getPersistenceRuleMethods().contains("VersionCheck(0, \"version\", \"version\", false)"));
        assertTrue(service.getPersistencePreparationStatement().contains("new Object[]{before, after}"));
        assertTrue(service.getPersistenceCompletionStatement().contains(".persist()"));
    }
    @Test public void singleParticipantVersionGuardUsesItsQueryBaseline() throws Exception {
        var d=compile("Stored record","persist(update, argument(0, Stored.id)) compareVersion(argument(0, Stored.version), argument(0, Stored.version))");
        assertTrue(d.validatePersistenceDeclarations().toString(), d.validatePersistenceDeclarations().isEmpty());
        assertTrue(((SppService)d.getSppClass("Store")).getPersistenceRuleMethods().contains("VersionCheck(0, \"version\", \"version\", false)"));
    }
    @Test public void insertCannotCarryVersionUpdateGuard() throws Exception {
        var d=compile("Stored before, Stored after","persist(insert, argument(1, Stored.id)) compareVersion(argument(0, Stored.version), argument(1, Stored.version))");
        assertFalse(d.validatePersistenceDeclarations().isEmpty());
    }
    @Test public void writesSupportCollectionsWithoutScalarVersionGuard() throws Exception {
        var d=compile("Stored[] records","persist(insert, argument(0, Stored.id))");
        assertTrue(d.validatePersistenceDeclarations().toString(),d.validatePersistenceDeclarations().isEmpty());
    }
    @Test public void outOfRangeAndRepeatedKeysAreRejected() throws Exception {
        assertFalse(compile("Stored record","persist(insert, argument(1, Stored.id))").validatePersistenceDeclarations().isEmpty());
        assertFalse(compile("Stored record","persist(insert, argument(0, Stored.id), argument(0, Stored.id))").validatePersistenceDeclarations().isEmpty());
    }    @Test public void quantumPersistenceEnlistsPreparedRecordsWithoutFakeConservationAttributes() throws Exception {
        var domain=compile("Stored record","quantum resource(records) persist(insert, argument(0, Stored.id))");
        assertTrue(domain.validatePersistenceDeclarations().toString(),domain.validatePersistenceDeclarations().isEmpty());
        var service=(SppService)domain.getSppClass("Store");
        assertEquals("QUANTUM",service.getTransactionModeName());assertTrue(service.getQuantumServiceAttributes().isEmpty());
    }

}
