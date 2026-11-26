package com.servpp.service.quantum.settlement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.servpp.service.quantum.settlement.QuantumDefinition.NumberType;
import com.servpp.service.quantum.settlement.SettlementCommand.AtomicGroupShape;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class SettlementRecordsTest {
    private static final QuantumDefinition LONG_DEFINITION = new QuantumDefinition(NumberType.LONG, "0", "1000", 0);

    private static QuantumMutation mutation(String attribute, String object, String delta) {
        return new QuantumMutation(new LogicalQuantumKey("ns", attribute, object), LONG_DEFINITION, delta);
    }

    private static SettlementCommand command(AtomicGroupShape shape, Optional<String> original,
                                             QuantumMutation... mutations) {
        return new SettlementCommand(new SettlementKey("ns", "r1"), original, "res", shape, List.of(mutations));
    }

    @Test
    public void identityMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> new SettlementKey(" ", "r1"));
        assertThrows(IllegalArgumentException.class, () -> new SettlementKey("ns", null));
        assertThrows(IllegalArgumentException.class, () -> new LogicalQuantumKey("ns", "", "o"));
        assertThrows(IllegalArgumentException.class, () -> new LogicalQuantumKey("ns", "A.b", " "));
        assertEquals("r1", new SettlementKey(" ns ", " r1 ").requestKey());
    }

    @Test
    public void definitionRejectsInvalidRangeScaleAndNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(NumberType.LONG, "5", "4", 0));
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(NumberType.LONG, "0", "1", -1));
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(NumberType.DOUBLE, "0", "1", 2));
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(NumberType.BYTE, "0", "300", 0));
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(NumberType.DOUBLE, "0", "NaN", 0));
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(NumberType.DOUBLE, "0", "Infinity", 0));
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(null, "0", "1", 0));
        assertThrows(IllegalArgumentException.class, () -> new QuantumDefinition(NumberType.INT, "0", " ", 0));
    }

    @Test
    public void definitionScalesExactDecimalsAndRejectsWhatItCannotRepresent() {
        QuantumDefinition money = new QuantumDefinition(NumberType.LONG, "0", "1000000", 2);
        assertEquals("1234", money.physicalValue("12.34"));
        assertThrows(IllegalArgumentException.class, () -> money.physicalValue("12.345"));
        assertThrows(IllegalArgumentException.class, () -> money.physicalValue("20000"));
        assertThrows(IllegalArgumentException.class, () -> money.physicalValue("abc"));
    }

    @Test
    public void mutationDeltaIsAFiniteNonZeroString() {
        LogicalQuantumKey key = new LogicalQuantumKey("ns", "A.b", "o");
        assertThrows(IllegalArgumentException.class, () -> new QuantumMutation(key, LONG_DEFINITION, "NaN"));
        assertThrows(IllegalArgumentException.class, () -> new QuantumMutation(key, LONG_DEFINITION, "1e999999999999"));
        assertThrows(IllegalArgumentException.class, () -> new QuantumMutation(key, LONG_DEFINITION, "0.00"));
        assertThrows(IllegalArgumentException.class, () -> new QuantumMutation(key, LONG_DEFINITION, null));
        assertThrows(IllegalArgumentException.class, () ->
                new QuantumMutation(key, LONG_DEFINITION, "1", Optional.of(new QuantumMutation.Interval("5", "1"))));
        assertThrows(IllegalArgumentException.class, () -> new QuantumMutation.Interval(null, null));
    }

    @Test
    public void commandMustBeNettedConservedAndMatchTheDerivedShape() {
        assertEquals(AtomicGroupShape.NONE,
                command(AtomicGroupShape.NONE, Optional.empty(), mutation("A.x", "1", "-5"), mutation("B.x", "1", "5")).shape());
        assertEquals(AtomicGroupShape.ONE_TO_MANY, command(AtomicGroupShape.ONE_TO_MANY, Optional.empty(),
                mutation("A.x", "1", "-5"), mutation("B.x", "1", "3"), mutation("C.x", "1", "2")).shape());
        assertEquals(AtomicGroupShape.MANY_TO_ONE, command(AtomicGroupShape.MANY_TO_ONE, Optional.empty(),
                mutation("A.x", "1", "-3"), mutation("B.x", "1", "-2"), mutation("C.x", "1", "5")).shape());
        assertEquals(AtomicGroupShape.NETTED, command(AtomicGroupShape.NETTED, Optional.empty(),
                mutation("A.x", "1", "-3"), mutation("B.x", "1", "-2"), mutation("C.x", "1", "4"),
                mutation("D.x", "1", "1")).shape());
        assertThrows(IllegalArgumentException.class, () -> command(AtomicGroupShape.NETTED, Optional.empty(),
                mutation("A.x", "1", "-5"), mutation("B.x", "1", "5")));
        assertThrows(IllegalArgumentException.class, () -> command(AtomicGroupShape.NONE, Optional.empty(),
                mutation("A.x", "1", "-5"), mutation("B.x", "1", "4")));
        assertThrows(IllegalArgumentException.class, () -> command(AtomicGroupShape.NONE, Optional.empty(),
                mutation("A.x", "1", "-5"), mutation("A.x", "1", "5")));
        assertThrows(IllegalArgumentException.class, () -> command(AtomicGroupShape.NONE, Optional.empty(),
                mutation("A.x", "1", "5")));
        assertThrows(IllegalArgumentException.class, () -> new SettlementCommand(
                new SettlementKey("ns", "r1"), Optional.empty(), " ", AtomicGroupShape.NONE,
                List.of(mutation("A.x", "1", "-5"), mutation("B.x", "1", "5"))));
    }

    @Test
    public void reversalRequiresADifferentOriginalRequestKey() {
        QuantumMutation from = mutation("A.x", "1", "-5");
        QuantumMutation to = mutation("B.x", "1", "5");
        SettlementCommand forward = command(AtomicGroupShape.NONE, Optional.empty(), from, to);
        SettlementCommand reversal = command(AtomicGroupShape.NONE, Optional.of("orig"), from, to);
        assertThrows(IllegalArgumentException.class, forward::requireReversal);
        assertThrows(IllegalArgumentException.class, reversal::requireForward);
        assertEquals("orig", reversal.requireReversal().originalRequestKey().orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> command(AtomicGroupShape.NONE, Optional.of("r1"), from, to));
        assertThrows(IllegalArgumentException.class,
                () -> command(AtomicGroupShape.NONE, Optional.of(" "), from, to));
    }

    @Test
    public void provisioningValuesAreValidatedAgainstTheDefinition() {
        QuantumDefinition money = new QuantumDefinition(NumberType.LONG, "0", "1000000", 2);
        LogicalQuantumKey key = new LogicalQuantumKey("ns", "A.x", "1");
        assertEquals("1234", new ProvisionCommand(key, money, "12.34").expectedPhysicalInitialValue());
        assertThrows(IllegalArgumentException.class, () -> new ProvisionCommand(key, money, "-1"));
        assertThrows(IllegalArgumentException.class, () -> new ProvisionCommand(key, money, "1.234"));
        assertThrows(IllegalArgumentException.class, () -> new ProvisionResponse(key, money, " ",
                SettlementResponse.Outcome.SUCCESS));
    }

    @Test
    public void responseVerifiesTheEchoOfTheRequest() {
        SettlementCommand command = command(AtomicGroupShape.NONE, Optional.empty(),
                mutation("A.x", "1", "-5"), mutation("B.x", "1", "5"));
        SettlementResponse good = new SettlementResponse(command.key(), Optional.empty(),
                SettlementResponse.Operation.SETTLE, SettlementResponse.Outcome.SUCCESS,
                Optional.of("42"), Optional.empty());
        good.requireIdentity(command, SettlementResponse.Operation.SETTLE);
        assertThrows(IllegalStateException.class,
                () -> good.requireIdentity(command, SettlementResponse.Operation.REVERSE));
        SettlementResponse wrongKey = new SettlementResponse(new SettlementKey("ns", "other"), Optional.empty(),
                SettlementResponse.Operation.SETTLE, SettlementResponse.Outcome.SUCCESS,
                Optional.empty(), Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> wrongKey.requireIdentity(command, SettlementResponse.Operation.SETTLE));
    }
}
