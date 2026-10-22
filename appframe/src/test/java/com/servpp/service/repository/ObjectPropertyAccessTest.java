package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ObjectPropertyAccessTest {
    public static class Item { public String lineNumber; }
    @Test public void deserializedSnapshotsReadNestedFactsAndOmittedNullableProperties() throws Exception {
        var facts = new ObjectMapper().readValue("{\"lineNumber\":\"LINE-1\",\"goods\":{\"sku\":\"CAT-001\"}}", Map.class);
        assertEquals("LINE-1", ObjectPropertyAccess.read(facts, "lineNumber"));
        assertEquals("CAT-001", ObjectPropertyAccess.read(facts, "goods.sku"));
        assertNull(ObjectPropertyAccess.read(facts, "reason"));
        assertNull(ObjectPropertyAccess.read(facts, "missing.sku"));
        assertNull(ObjectPropertyAccess.read(new Item(), "lineNumber"));
        assertThrows(IllegalStateException.class, () -> ObjectPropertyAccess.read(new Item(), "unknown"));
    }
}
