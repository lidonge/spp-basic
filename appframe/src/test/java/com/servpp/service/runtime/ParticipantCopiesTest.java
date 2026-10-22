package com.servpp.service.runtime;

import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantCopiesTest {
    public static class Value {
        public BigDecimal amount;
        public List<Value> children;
    }
    @Test public void nestedParticipantsAndPhysicalNumbersAreIndependent() {
        Value child=new Value();child.amount=new BigDecimal("12345678901234567890.12345678901234567890");
        Value original=new Value();original.children=List.of(child);
        Value copied=ParticipantCopies.copy(original);
        assertNotSame(original,copied);assertNotSame(child,copied.children.get(0));
        assertEquals(child.amount,copied.children.get(0).amount);
        child.amount=BigDecimal.ZERO;
        assertEquals(new BigDecimal("12345678901234567890.12345678901234567890"),copied.children.get(0).amount);
    }
    @Test public void topLevelCollectionsRetainElementTypesAndNulls() {
        Value original=new Value();original.amount=BigDecimal.TEN;
        List<Value> copied=ParticipantCopies.copy(List.of(original));
        assertNotSame(original,copied.get(0));assertEquals(BigDecimal.TEN,copied.get(0).amount);
        Value[] array=ParticipantCopies.copy(new Value[]{original,null});
        assertNotSame(original,array[0]);assertNull(array[1]);
        assertNull(ParticipantCopies.copy(null));
    }
}
