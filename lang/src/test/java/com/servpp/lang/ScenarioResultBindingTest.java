package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Queries populate declared participants in place; service return assignments are prohibited. */
public class ScenarioResultBindingTest {
    private SppDomain compile(String call, String result) throws Exception {
        Path file = Files.createTempFile("scene-result", ".spp");
        try {
            Files.writeString(file, "domain rules; Role Item { String code; } Role Other { String code; } "
                    + "atomic query read(Item key, " + result + "); "
                    + "Scenario serial run(Item key, Item item, Item[] items, Other other) { " + call + " }");
            SppProject project = new SppProject();
            new SppCompiler(file.toFile(), project).compile();
            return project.getDomain("rules");
        } finally { Files.deleteIfExists(file); }
    }

    @Test public void scalarAndCollectionQueriesPopulateDeclaredParticipants() throws Exception {
        for (String[] fixture : new String[][] {
                {"read(key, item);", "Item result"},
                {"serial { read(key, items); }", "Item[] results"}}) {
            var domain = compile(fixture[0], fixture[1]);
            assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
            assertTrue(((SppService) domain.getSppClass("Read")).getReturns().getSppLocalVarList().isEmpty());
        }
    }

    @Test public void wrongTypeDimensionAndAmbiguousParticipantsAreRejected() throws Exception {
        for (String[] fixture : new String[][] {
                {"read(key, other);", "Item result"},
                {"read(key, item);", "Item[] results"},
                {"read;", "Item result"},
                {"read(key, unknown);", "Item result"}}) {
            var domain = compile(fixture[0], fixture[1]);
            assertFalse(fixture[0], domain.validateAppDeclarations().isEmpty());
        }
    }

    @Test public void serviceReturnAssignmentsAreSyntaxErrors() {
        assertThrows(java.io.IOException.class, () -> compile("item = read(key);", "Item result"));
    }
}
