package com.servpp.service.runtime;

import com.servpp.service.numeric.NumericAdapter;
import org.junit.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import static org.junit.Assert.*;

public class ParticipantFactsTest {
    public record Amount(BigDecimal value) {}
    public record Evidence(String id, Object lines) {
        public Object getTechnicalCache() { throw new AssertionError("not a declared property"); }
    }
    private final ParticipantFacts.Type amount = new ParticipantFacts.Type("Amount", List.of(new ParticipantFacts.Field("value")));
    private final ParticipantFacts.Type evidence = new ParticipantFacts.Type("Evidence", List.of(
            new ParticipantFacts.Field("id"), new ParticipantFacts.Field("lines")));
    @Test public void comparesDeclaredNestedFactsAndOrderedCollectionsWithExactNumericValues() {
        var a = new Evidence("A", List.of(new Amount(new BigDecimal("1.0")), new Amount(new BigDecimal("2"))));
        var b = new Evidence("A", new Amount[]{new Amount(new BigDecimal("1.0000")), new Amount(new BigDecimal("2.00"))});
        assertNotSame(a, b); assertTrue(ParticipantFacts.same(a, b, evidence, amount));
        assertFalse(ParticipantFacts.same(a, new Evidence("B", b.lines()), evidence, amount));
        assertFalse(ParticipantFacts.same(a, new Evidence("A", List.of(new Amount(new BigDecimal("2")),
                new Amount(new BigDecimal("1")))), evidence, amount));
        assertFalse(ParticipantFacts.same(a, new Evidence("A", List.of(new Amount(new BigDecimal("1")))), evidence, amount));
        assertFalse(ParticipantFacts.same(a, null, evidence, amount));
        assertTrue(ParticipantFacts.same(new Evidence("A", null), new Evidence("A", null), evidence, amount));
        assertEquals("1.0", ((Amount) ((List<?>) a.lines()).get(0)).value().toPlainString());
    }
    public static class Node {
        final String name; Node next;
        Node(String name) { this.name = name; }
        public String getName() { return name; }
        public Node getNext() { return next; }
    }
    @Test public void terminatesCyclesAndStillFindsDifferentDeclaredValues() {
        var type = new ParticipantFacts.Type("Node", List.of(new ParticipantFacts.Field("getName"), new ParticipantFacts.Field("getNext")));
        Node a = new Node("A"), b = new Node("A"), c = new Node("B");
        a.next = a; b.next = b; c.next = c;
        assertTrue(ParticipantFacts.same(a, b, type)); assertFalse(ParticipantFacts.same(a, c, type));
    }
    public record Wrapped(String encoded) {}
    public record Holder(Wrapped value) {}
    public static class ComparisonAdapter implements NumericAdapter<Wrapped> {
        public Wrapped literal(String value) { return new Wrapped(value); }
        public int compare(Wrapped a, Wrapped b) { return new BigDecimal(a.encoded()).compareTo(new BigDecimal(b.encoded())); }
        public Wrapped add(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
        public Wrapped subtract(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
        public Wrapped multiply(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
        public Wrapped divide(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
        public Wrapped remainder(Wrapped a, Wrapped b) { throw new UnsupportedOperationException(); }
        public Wrapped negate(Wrapped a) { throw new UnsupportedOperationException(); }
        public Wrapped round(Wrapped a, int places, RoundingMode mode) { throw new UnsupportedOperationException(); }
    }
    @Test public void customPhyNumericComparisonDoesNotRequireADecimalCodec() {
        var adapter = new ComparisonAdapter();
        var type = new ParticipantFacts.Type("Holder", List.of(new ParticipantFacts.Field("value", adapter)));
        Holder a = new Holder(new Wrapped("2.0")), b = new Holder(new Wrapped("2.0000"));
        assertThrows(ArithmeticException.class, () -> adapter.toDecimal(a.value()));
        assertTrue(ParticipantFacts.same(a, b, type));
        assertFalse(ParticipantFacts.same(a, new Holder(new Wrapped("3")), type));
        var unbound = new ParticipantFacts.Type("Holder", List.of(new ParticipantFacts.Field("value")));
        assertTrue(assertThrows(IllegalStateException.class, () -> ParticipantFacts.same(a, b, unbound)).getMessage().contains("SCHEMA_MISSING"));
    }
    @Test public void rejectsMissingOrConflictingMetadataRatherThanDeclaringDifferentInstancesEqual() {
        var a = new Evidence("A", null); var b = new Evidence("A", null);
        assertThrows(IllegalStateException.class, () -> ParticipantFacts.same(a, b));
        assertThrows(IllegalArgumentException.class, () -> ParticipantFacts.same(a, b, evidence, evidence));
        var wrong = new ParticipantFacts.Type("Evidence", List.of(new ParticipantFacts.Field("missingGetter")));
        assertTrue(assertThrows(IllegalStateException.class, () -> ParticipantFacts.same(a, b, wrong)).getMessage().contains("READ_FAILED"));
    }
}
