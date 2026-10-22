package com.servpp.lang;
import java.nio.file.Files;
import com.servpp.lang.models.SppProject;
import org.junit.Test;
import static org.junit.Assert.*;
public class ParticipantAppendTest {
    private java.util.List<String> validate(String body) throws Exception {
        var spp = Files.createTempFile("participant-append", ".spp");
        Files.writeString(spp, "domain sample; Role Item { int value; } Role Other { int value; } Contract Batch { Item[] items; Item item; Other other; } atomic calculate build(Batch batch, Item[] items) { " + body + " }");
        var project = new SppProject(); new SppCompiler(spp.toFile(), project).compile();
        return project.getDomain("sample").validateBusinessCalculations();
    }
    @Test public void appendChecksElementTypeAndCollectionDimension() throws Exception {
        assertTrue(validate("batch.items = append(batch.items, batch.item); Item[] local = append(items, batch.item); batch.items = local;").isEmpty());
        assertFalse(validate("batch.items = append(batch.items, batch.other);").isEmpty());
        assertFalse(validate("batch.items = append(batch.item, batch.item);").isEmpty());
        assertFalse(validate("batch.item = append(batch.items, batch.item);").isEmpty());
    }
}
