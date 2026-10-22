package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransactionFailureHandlingTest {
    private SppDomain parse(String definitions, String entrance, String body) throws Exception {
        var source = Files.createTempFile("transaction-fail", ".spp");
        try {
            Files.writeString(source, "domain demo; Role Buyer { String name; } Contract Agreement { Buyer buyer; } "
                    + definitions + " Scenario serial run(" + entrance + ") { " + body + " }");
            var project = new SppProject(); new SppCompiler(source.toFile(), project).compile();
            return project.getDomain("demo");
        } finally { Files.deleteIfExists(source); }
    }
    @Test public void systemResultIsImplicitAndAssociatedWithTheUniqueContract() throws Exception {
        var domain = parse("", "Buyer, Agreement", "transaction { updateBuyer; } fail { if (ResultInfo.failure.code != null) { transaction { updateAgreement; } } }");
        assertEquals(List.of(), domain.validateAppDeclarations());
        var scene = (SppScenario) domain.getSppClass("Run");
        assertEquals(2, scene.getServiceBody().getSppLocalVarList().size());
        assertNotNull(scene.getServiceBody().getLocalVar("ResultInfo"));
        assertTrue(((SppClass)domain.getSppClass("Agreement")).getField("resultInfo").isSystemResult());
        assertFalse(((SppClass)domain.getSppClass("Agreement")).getField("resultInfo").isStoredHere());
        String java = scene.getScenarioBodyJava();
        assertTrue(java, java.contains("TransactionFailureHandling.execute(ResultInfo, Agreement"));
        assertTrue(java, java.contains("ResultInfo.getFailure().getCode()"));
    }
    @Test public void multipleContractsRequireAnExplicitSelection() throws Exception {
        String definitions = "Contract Other { Buyer buyer; }";
        assertTrue(parse(definitions,"Buyer, Agreement, Other", "transaction { updateBuyer; } fail {}")
                .validateAppDeclarations().stream().anyMatch(error -> error.contains("one scalar contract")));
        assertEquals(List.of(), parse(definitions,"Buyer, Agreement, Other", "transaction { updateBuyer; } fail(Other) { transaction { updateOther; } }")
                .validateAppDeclarations());
    }
    @Test public void receiverMustBeAScalarContractAndNestedFailIsRejected() throws Exception {
        assertTrue(parse("", "Buyer, Agreement", "transaction { updateBuyer; } fail(Buyer) {}")
                .validateAppDeclarations().stream().anyMatch(error -> error.contains("scalar contract")));
        assertTrue(parse("", "Buyer, Agreement", "transaction { transaction { updateBuyer; } fail {} }")
                .validateAppDeclarations().stream().anyMatch(error -> error.contains("outermost")));
    }
    @Test public void systemParticipantCannotBeAnEntranceOrAtomicArgument() throws Exception {
        assertFalse(parse("atomic calculate consume(ResultInfo result);", "Buyer, Agreement", "transaction { updateBuyer; } fail { consume(ResultInfo); }")
                .validateAppDeclarations().isEmpty());
        assertFalse(parse("", "Buyer, Agreement, ResultInfo", "transaction { updateBuyer; } fail {}")
                .validateAppDeclarations().isEmpty());
    }
    @Test public void ordinaryTransactionSyntaxAndTechnicalExceptionRejectionRemain() throws Exception {
        assertEquals(List.of(), parse("", "Buyer, Agreement", "transaction { updateBuyer; }").validateAppDeclarations());
        assertThrows(java.io.IOException.class, () -> parse("", "Buyer, Agreement", "try { updateBuyer; } catch(Agreement) {}"));
    }
    @Test public void parallelFailAndExplicitSystemDefinitionsAreRejected() throws Exception {
        assertTrue(parse("", "Buyer, Agreement", "parallel { transaction { updateBuyer; } fail {} }")
                .validateAppDeclarations().stream().anyMatch(error -> error.contains("parallel branches")));
        assertFalse(parse("Role ResultInfo { String arbitrary; }", "Buyer, Agreement", "transaction { updateBuyer; } fail {}")
                .validateAppDeclarations().isEmpty());
    }
    @Test public void atomsCannotReadResultThroughTheContractAndSystemNamesCannotBeShadowed() throws Exception {
        assertTrue(parse("atomic calculate misuse(Agreement) { if (Agreement.resultInfo.status != null) { Agreement.buyer.name = \"bad\"; } }",
                "Buyer, Agreement", "transaction { updateBuyer; } fail {}")
                .validateAppDeclarations().stream().anyMatch(error -> error.contains("only accessible inside scenarios")));
        assertFalse(parse("", "Buyer ResultInfo, Agreement", "transaction { updateBuyer; } fail {}")
                .validateAppDeclarations().isEmpty());
    }

}
