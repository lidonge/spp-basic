package com.servpp.lang;
import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;
public class ParticipantConstructionTest {
    private SppDomain compile(String body) throws Exception {
        return compile("calculate", body);
    }
    private SppDomain compile(String kind, String body) throws Exception {
        var file=Files.createTempFile("participant-new", ".spp");
        try {
            Files.writeString(file,"domain rules; Role Item { long value; } Role Other { long value; } Role Holder { Item item; Item[] items; } atomic "+kind+" prepare(Holder h) { "+body+" }");
            SppProject project=new SppProject();new SppCompiler(file.toFile(),project).compile();return project.getSppDomainMap().get("rules");
        } finally { Files.deleteIfExists(file); }
    }
    @Test public void nestedAndLocalModelsAreConstructedFromDeclaredTypes() throws Exception {
        var domain=compile("h.item = new Item(); Item temp = new Item(); temp.value = 7; h.item.value = temp.value;");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        var code=((SppService)domain.getSppClass("Prepare")).getPhysicalJavaStatements().toString();
        assertNotNull(code);
    }
    @Test public void participantCollectionsCheckElementTypesAndDimensions() throws Exception {
        assertTrue(compile("h.items = new Item[] { new Item(), null }; Item[] values = new Item[] {}; h.items = values;").validateAppDeclarations().isEmpty());
        assertFalse(compile("h.items = new Item[] { new Other() };").validateAppDeclarations().isEmpty());
        assertFalse(compile("h.items = new Item[] { 1 };").validateAppDeclarations().isEmpty());
        assertFalse(compile("h.item = new Item[] {};").validateAppDeclarations().isEmpty());
        assertFalse(compile("int[] numbers = 1;").validateAppDeclarations().isEmpty());
        assertFalse(compile("Item value = new Item[] {};").validateAppDeclarations().isEmpty());
    }
    @Test public void boxedModelNumbersMayBeCheckedForAbsence() throws Exception {
        assertTrue(compile("check", "require(h.item.value != null, \"MISSING_VALUE\");").validateAppDeclarations().isEmpty());
        assertFalse(compile("check", "require(1 == null, \"INVALID\");").validateAppDeclarations().isEmpty());
    }
    @Test public void differentTypesAndArbitraryClassesAreRejected() throws Exception {
        assertFalse(compile("h.item = new Other();").validateAppDeclarations().isEmpty());
        assertFalse(compile("Item temp = new Other();").validateAppDeclarations().isEmpty());
        assertFalse(compile("h.item = new ProcessBuilder();").validateAppDeclarations().isEmpty());
    }
}
