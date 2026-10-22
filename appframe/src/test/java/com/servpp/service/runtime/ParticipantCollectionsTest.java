package com.servpp.service.runtime;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class ParticipantCollectionsTest {
    @Test public void optionalCollectionsStayAbsentAndTypedElementsAreRetained() {
        assertNull(ParticipantCollections.array(null,String.class));
        assertNull(ParticipantCollections.list((String[])null));
        assertArrayEquals(new String[]{"a","b"},(String[])ParticipantCollections.array(List.of("a","b"),String.class));
    }
    @Test public void appendProducesIndependentSequencesForModelsAndParameters() {
        var original = List.of("a");
        var result = ParticipantCollections.append(original, "b");
        assertEquals(List.of("a", "b"), result); assertEquals(List.of("a"), original);
        assertEquals(List.of("a", "b"), ParticipantCollections.append(new String[]{"a"}, "b"));
        assertThrows(IllegalArgumentException.class, () -> ParticipantCollections.append(null, "a"));
        assertThrows(IllegalArgumentException.class, () -> ParticipantCollections.append(original, null));
    }
    @Test public void primitiveCollectionsDoNotRequireBusinessBoxing() {
        assertArrayEquals(new int[]{1,2},(int[])ParticipantCollections.array(List.of(1,2),int.class));
        assertEquals(List.of(1,2),ParticipantCollections.<Integer>list(new int[]{1,2}));
    }
}
