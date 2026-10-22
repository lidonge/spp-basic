package com.servpp.service.runtime;

import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalDomainCollectionFactsTest {
    public static class Line {
        private String number;
        public String getNumber() { return number; }
        public void setNumber(String value) { number = value; }
    }
    public static class Document {
        private List<Line> lines = new ArrayList<>();
        public List<Line> getLines() { return lines; }
        public void setLines(List<Line> value) { lines = value; }
    }
    public interface Provider extends Service { void complete(Document document); }
    public static class ProviderService implements Provider {
        public String getName() { return "collectionProvider.complete"; }
        public Object execute(Object... arguments) { complete((Document)arguments[0]); return null; }
        public void complete(Document document) { document.getLines().get(0).setNumber("LINE-1"); }
    }
    @Test public void delegatedFactsKeepDeclaredNestedCollectionElementTypes() {
        var container = DefaultServiceContainer.getInstance(); container.clear();
        container.registerService("collectionProvider.complete", new ProviderService());
        try {
            var document = new Document(); document.getLines().add(new Line());
            LocalDomainServices.executeScope(() -> LocalDomainServices.invoke("collectionProvider.complete",
                    new Object[]{document}, new Class<?>[]{Document.class}, new String[][]{{"lines"}},
                    new String[][]{{}}, new boolean[]{false}, false));
            assertEquals(Line.class, document.getLines().get(0).getClass());
            assertEquals("LINE-1", document.getLines().get(0).getNumber());
            BusinessNumbering.assign(document.getLines(), "number", "LINE");
            assertEquals("LINE-1", document.getLines().get(0).getNumber());
        } finally { container.clear(); }
    }
}
