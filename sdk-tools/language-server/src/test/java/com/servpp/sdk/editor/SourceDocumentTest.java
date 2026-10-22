package com.servpp.sdk.editor;

import org.eclipse.lsp4j.Position;
import org.junit.Test;

import static org.junit.Assert.*;

public class SourceDocumentTest {
    @Test public void parsesBusinessAndConfigurationWithoutGeneration() {
        for (String extension : new String[]{"spp", "enum"}) {
            var document = new SourceDocument("file:///test." + extension, 1,
                    "domain ledger; Role Facts { int amount; }\n");
            assertEquals("ledger", document.domain());
            assertTrue(document.problems().toString(), document.problems().isEmpty());
        }
        for (String extension : new String[]{"app", "phy", "rdb"}) {
            var document = new SourceDocument("file:///test." + extension, 2, "domain ledger; scope { local { twice; } }");
            assertEquals("ledger", document.domain());
            assertTrue(document.problems().toString(), document.problems().isEmpty());
        }
    }

    @Test public void mapsCodePointsToUtf16AndHandlesCrLf() {
        String text = "domain ledger;\r\n/*😀*/ Role 账户 { int amount; }\r\n";
        var document = new SourceDocument("file:///test.spp", 7, text);
        assertTrue(document.problems().isEmpty());
        int start = text.indexOf("账户");
        var position = document.position(start);
        assertEquals(new Position(1, 12), position);
        var token = document.identifierAt(position);
        assertNotNull(token);
        assertEquals("账户", token.getText());
        assertEquals(position, document.range(token).getStart());
        assertEquals(new Position(1, 14), document.range(token).getEnd());
        assertEquals(start, document.offset(position));
        assertEquals(7, document.version());
    }

    @Test public void excludesCommentsAndStringsFromIdentifierQueries() {
        String text = "domain ledger; // Fake\nRole Facts { business(\"Fake\") int amount; }\n";
        var document = new SourceDocument("file:///test.spp", 1, text);
        assertNull(document.identifierAt(document.position(text.indexOf("Fake"))));
        assertNull(document.identifierAt(document.position(text.lastIndexOf("Fake"))));
        assertEquals("amount", document.identifierAt(document.position(text.indexOf("amount"))).getText());
        assertEquals("amount", document.identifierAt(document.position(text.indexOf("amount") + "amount".length())).getText());
    }

    @Test public void collectsLexerParserAndTrailingInputErrors() {
        for (String text : new String[]{"domain ledger; Role Facts { int ; }", "domain ledger; §", "domain ledger; extra"}) {
            var document = new SourceDocument("file:///test.spp", 1, text);
            assertFalse(text, document.problems().isEmpty());
        }
    }

    @Test public void enforcesFileTypeSizeAndPositionLimits() {
        assertThrows(IllegalArgumentException.class, () -> new SourceDocument("file:///test.java", 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new SourceDocument("file:///test.spp", 1, "中".repeat(400000)));
        var document = new SourceDocument("file:///test.spp", 1, "domain ledger;");
        assertThrows(IllegalArgumentException.class, () -> document.offset(new Position(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> document.offset(new Position(0, 100)));
    }

    @Test public void eofLineCommentsStayHiddenEvenInIncompleteCode() {
        for (String text : new String[]{"domain ledger; // Role Hidden {}", "domain ledger; Role Facts { int amount; }\n"
                + "atomic calculate run(Facts) { Facts.amount = 1 // Facts.amount"}) {
            var document = new SourceDocument("file:///comment.spp", 1, text);
            String fragment = text.contains("Hidden") ? "Hidden" : "amount";
            assertNull(document.identifierAt(document.position(text.lastIndexOf(fragment))));
            assertEquals(text, document.text());
            assertEquals(text.length(), document.offset(document.position(text.length())));
        }
    }
}
