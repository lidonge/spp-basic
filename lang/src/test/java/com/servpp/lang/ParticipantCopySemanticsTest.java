package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantCopySemanticsTest {
    private java.util.List<String> validate(String body) throws Exception {
        var source=Files.createTempFile("participant-copy", ".spp");
        try {
            Files.writeString(source,"domain rules; Role Item { long amount; } Role Other { long amount; } Role Batch { Item item; Item[] items; Other other; } atomic calculate freeze(Batch a, Batch b, Item[] items) { "+body+" }");
            SppProject p=new SppProject();new SppCompiler(source.toFile(),p).compile();
            return p.getSppDomainMap().get("rules").validateBusinessCalculations();
        } finally { Files.deleteIfExists(source); }
    }
    @Test public void copiesKeepObjectTypesAndCollectionDimensions() throws Exception {
        assertTrue(validate("b.item = copy(a.item); b.items = copy(items); b.other = copy(null);").isEmpty());
        assertFalse(validate("b.item = copy(a.other);").isEmpty());
        assertFalse(validate("b.item = copy(items);").isEmpty());
        assertFalse(validate("b.items = copy(a.item);").isEmpty());
    }
    @Test public void numbersAreNotParticipantSnapshots() throws Exception {
        assertFalse(validate("b.item.amount = copy(a.item.amount);").isEmpty());
        assertFalse(validate("b.item = copy(a.item, b.item);").isEmpty());
        assertThrows(java.io.IOException.class, () -> validate("b.item = copy();"));
    }
}
