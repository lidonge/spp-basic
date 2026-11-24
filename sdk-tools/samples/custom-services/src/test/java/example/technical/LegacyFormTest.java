package example.technical;

import example.generated.reserves.forms.ReserveSlipForm;
import example.generated.reserves.model.ReserveSlip;
import example.generated.reserves.model.SlipEntry;
import org.junit.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LegacyFormTest {
    private static ReserveSlip slip() {
        return new ReserveSlip().referenceCode("clé").amount(new BigDecimal("-12.50")).postingDate("2024-02-29")
                .entries(List.of(new SlipEntry().label("a").units(BigDecimal.ONE),
                        new SlipEntry().label("b").units(BigDecimal.TEN)));
    }

    @Test public void mapsBothDirectionsAtExactBytePositions() {
        byte[] bytes = ReserveSlipForm.write(slip());
        assertEquals(63, bytes.length);
        assertEquals(0, bytes[0]);
        assertEquals(63, bytes[1]);
        assertEquals("PZZZ7602clé        " + " ".repeat(11) + "-12.5020240229a      1b     10",
                new String(bytes, 2, 61, StandardCharsets.UTF_8));
        var result = ReserveSlipForm.read(bytes);
        assertEquals(slip(), result);
        assertArrayEquals(bytes, ReserveSlipForm.write(result));
        assertNull(ReserveSlipForm.read(ReserveSlipForm.write(slip().referenceCode(null))).getReferenceCode());
    }

    @Test public void rejectsLossyValuesAndMalformedRecords() {
        assertThrows(IllegalArgumentException.class, () -> ReserveSlipForm.write(slip().referenceCode("界".repeat(5))));
        assertThrows(IllegalArgumentException.class, () -> ReserveSlipForm.write(slip().entries(List.of())));
        assertThrows(ArithmeticException.class, () -> ReserveSlipForm.write(slip().amount(new BigDecimal("1.001"))));
        byte[] bytes = ReserveSlipForm.write(slip());
        assertThrows(IllegalArgumentException.class, () -> ReserveSlipForm.read(Arrays.copyOf(bytes, 62)));
        bytes[2] = 'X';
        assertThrows(IllegalArgumentException.class, () -> ReserveSlipForm.read(bytes));
        bytes[2] = 'P';
        bytes[46] = '0';
        bytes[45] = '3';
        assertThrows(java.time.DateTimeException.class, () -> ReserveSlipForm.read(bytes));
    }
}
