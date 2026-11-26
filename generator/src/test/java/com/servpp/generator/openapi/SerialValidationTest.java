package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.service.serial.GaplessSerialEngine;
import com.servpp.service.serial.SerialEngine;
import com.servpp.service.serial.SerialRequest;
import com.servpp.service.technical.FieldValueContext;
import com.servpp.service.technical.FieldValueInterceptor;
import com.servpp.service.technical.TechnicalInvocation;
import com.servpp.service.technical.TechnicalService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Compile-time checks of APP serial rules, auto fields and record-level interception. */
public class SerialValidationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    static final String ENGINE = "com.servpp.service.serial.InMemorySerialEngine";
    static final String PLAIN_SERVICE = SerialValidationTest.class.getName() + "$PlainService";

    public static class PlainService implements TechnicalService { @Override public void handle(TechnicalInvocation invocation) { } }
    public static class ValueOnly implements FieldValueInterceptor {
        @Override public Object produce(FieldValueContext context) { return "v"; }
    }
    public static class NotGapless implements SerialEngine { @Override public long next(SerialRequest request) { return 1; } }
    public static class Gapped implements GaplessSerialEngine { @Override public long next(SerialRequest request) { return 1; } }

    private static final String SPP = """
            domain ledger;
            Contract Voucher { String voucherCode; String branch; String reference; int seqNo; boolean rejected; long total; }
            atomic update storeVoucher(Voucher voucher);
            Scenario serial record(Voucher voucher) { transaction { storeVoucher; } }
            """;

    /** Declarations go before the tables block; the table body holds the auto declarations. */
    private String compile(String declarations, String tableBody, String spp) throws Exception {
        Path root = temp.newFolder().toPath();
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("ledger.spp"), spp);
        Files.writeString(source.resolve("ledger.app"), """
                domain ledger;
                application ledgers { deploy ledger; }
                %s
                tables contracts { table VOUCHERS(Voucher) { primarykey(Voucher.voucherCode); %s } }
                scope { local { persist(insert, argument(0, Voucher.voucherCode)) storeVoucher; } }
                phy { repository records { adapter jdbcObjectRepository; config default = true;
                    config datasourceUrl = "jdbc:h2:mem:serial_validation"; config driverClassName = "org.h2.Driver"; } }
                """.formatted(declarations, tableBody));
        try {
            GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(), "generated");
            return "";
        } catch (Exception failure) { return String.valueOf(failure.getMessage()); }
    }
    private void assertRejected(String message, String expected) {
        assertTrue("expected [" + expected + "] in: " + message, message.contains(expected));
    }
    private static String serial(String engineClass, String rule, String tableBody) {
        return "technical services { engine; }\n" + rule + "\nphy { technical service engine { implementation: \"" + engineClass + "\"; } }\n";
    }

    @Test public void aCorrectDeclarationCompiles() throws Exception {
        assertEquals("", compile(serial(ENGINE, "serial s using engine { scope branch; range 1..9999 wrap; format \"{branch}-{seq:4}\"; }", ""),
                "auto Voucher.reference serial s; auto Voucher.seqNo context triggerSequence;", SPP));
    }
    @Test public void unknownRuleIsRejected() throws Exception {
        assertRejected(compile("", "auto Voucher.reference serial missing;", SPP), "Unknown serial rule missing");
    }
    @Test public void unknownEngineIsRejected() throws Exception {
        assertRejected(compile("serial s using nowhere { range 1..9; }", "auto Voucher.seqNo serial s;", SPP), "unknown engine nowhere");
    }
    @Test public void fieldTypeMustMatchTheFormat() throws Exception {
        String decl = serial(ENGINE, "serial s using engine { format \"{seq:4}\"; } serial n using engine { range 1..9; }", "");
        assertRejected(compile(decl, "auto Voucher.seqNo serial s;", SPP), "has a format, so the field must be String");
        assertRejected(compile(decl, "auto Voucher.reference serial n;", SPP), "has no format, so the field must be short, int, long or BigInteger");
        assertRejected(compile(serial(ENGINE, "serial big using engine { range 1..9999999999; }", ""), "auto Voucher.seqNo serial big;", SPP), "does not fit int");
    }
    @Test public void formatMustBeWellFormed() throws Exception {
        assertRejected(compile(serial(ENGINE, "serial s using engine { range 1..99999 ; format \"{seq:4}\"; }", ""), "auto Voucher.reference serial s;", SPP), "cannot hold the range maximum");
        assertRejected(compile(serial(ENGINE, "serial s using engine { format \"X-{branch}\"; }", ""), "auto Voucher.reference serial s;", SPP), "exactly once");
        assertRejected(compile(serial(ENGINE, "serial s using engine { format \"{seq}-{nowhere}\"; }", ""), "auto Voucher.reference serial s;", SPP), "not a scope field");
    }
    @Test public void scopeFieldsMustBeOnTheEntity() throws Exception {
        assertRejected(compile(serial(ENGINE, "serial s using engine { scope branch, nowhere; format \"{seq}\"; }", ""), "auto Voucher.reference serial s;", SPP),
                "scope field nowhere is not a field of Voucher");
    }
    @Test public void sppMustNotAssignAnAutoField() throws Exception {
        String spp = SPP.replace("atomic update", "atomic calculate stamp(Voucher voucher) { voucher.reference = \"X\"; }\natomic update");
        assertRejected(compile(serial(ENGINE, "serial s using engine { format \"{seq}\"; }", ""), "auto Voucher.reference serial s;", spp),
                "assigns Voucher.reference, which a technical service fills on insert");
    }
    @Test public void gaplessNeedsAnEngineThatCanDoIt() throws Exception {
        String rule = "serial s using engine { range 1..9; gapless; }";
        assertRejected(compile(serial(ENGINE, rule, ""), "auto Voucher.seqNo serial s;", SPP), "cannot allocate inside the business transaction");
        assertEquals("", compile(serial(Gapped.class.getName(), rule, ""), "auto Voucher.seqNo serial s;", SPP));
        assertEquals("", compile(serial("com.servpp.service.serial.GaplessCounterEngine", rule, ""), "auto Voucher.seqNo serial s;", SPP));
    }
    @Test public void aSerialEngineMustImplementTheEngineContract() throws Exception {
        assertRejected(compile(serial(PLAIN_SERVICE, "serial s using engine { range 1..9; }", ""), "auto Voucher.seqNo serial s;", SPP),
                "does not implement SerialEngine");
        assertRejected(compile(serial(ValueOnly.class.getName(), "serial s using engine { range 1..9; }", ""), "auto Voucher.seqNo serial s;", SPP),
                "does not implement SerialEngine");
        assertEquals("", compile(serial(NotGapless.class.getName(), "serial s using engine { range 1..9; }", ""), "auto Voucher.seqNo serial s;", SPP));
    }
    @Test public void unknownContextIdentifierIsRejected() throws Exception {
        assertRejected(compile("", "auto Voucher.reference context sessionToken;", SPP), "Unknown context identifier sessionToken");
        assertRejected(compile("", "auto Voucher.rejected context auditLogNumber;", SPP), "requires a String, int or long field");
    }
    @Test public void aFieldHasOneProducer() throws Exception {
        String decl = serial(ENGINE, "serial s using engine { format \"{seq}\"; }", "");
        assertRejected(compile(decl, "auto Voucher.reference serial s; auto Voucher.reference context auditLogNumber;", SPP), "Duplicate APP table auto");
    }

    private static String intercept(String serviceClass, String hook) {
        return "technical services { stamper; }\nintercept Voucher { " + hook + " }\nphy { technical service stamper { implementation: \""
                + serviceClass + "\"; } }\n";
    }
    @Test public void aCustomServiceInterceptsAnEntityInsert() throws Exception {
        assertEquals("", compile(intercept(ValueOnly.class.getName(), "insert: stamper -> reference;"), "", SPP));
    }
    @Test public void interceptValidation() throws Exception {
        assertRejected(compile(intercept(ValueOnly.class.getName(), "insert: ghost -> reference;"), "", SPP), "Undeclared technical service ghost");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "insert: stamper -> ghostField;"), "", SPP), "Unknown or non-scalar field Voucher.ghostField");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "insert: stamper -> rejected;"), "", SPP), "cannot fill Voucher.rejected of type boolean");
        assertRejected(compile(intercept(PLAIN_SERVICE, "insert: stamper -> reference;"), "", SPP), "does not implement FieldValueInterceptor");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "insert: stamper;"), "", SPP), "requires '-> field'");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "update: stamper -> reference;"), "", SPP), "phase update is not supported");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "before: stamper -> reference;"), "", SPP), "does not take '-> field'");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "insert: stamper -> reference;").replace("intercept Voucher", "intercept Nowhere"), "", SPP),
                "Unknown interception entity Nowhere");
        String spp = SPP.replace("atomic update", "atomic calculate stamp(Voucher voucher) { voucher.reference = \"X\"; }\natomic update");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "insert: stamper -> reference;"), "", spp), "SPP must not assign it");
        assertRejected(compile(intercept(ValueOnly.class.getName(), "insert: stamper -> reference;"), "auto Voucher.reference context auditLogNumber;", SPP),
                "filled by more than one technical declaration");
    }
    @Test public void aRuleNeedsItsBlockSyntax() throws Exception {
        assertRejected(compile("serial s { range 1..9; }", "", SPP), "Failed to compile");
    }
}
