package com.servpp.xboddemo;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class XbodRuleTest {
    @Test public void recordedBankQualificationAndDocumentNotices() throws Exception { verify("/bank-qualification-cases.json",54,"DepositNotices"); }
    @Test public void recordedAdmissionAndAuthorizationRules() throws Exception { verify("/admission-cases.json",49,"AdmissionDecision"); }
    @Test public void recordedFeeRoundingAndTariffRules() throws Exception { verify("/fee-cases.json",16,"DepositFeePlan"); }
    private void verify(String resource, int count, String participant) throws Exception {
        try (var demo = new XbodTestSupport(); var input = getClass().getResourceAsStream(resource)) {
            var rows = demo.mapper.readTree(input); assertEquals(count,rows.size());
            for (var row : rows) {
                String code = row.path("code").asText();
                if (!code.isEmpty()) {
                    var failure = assertThrows(row.toString(), IllegalStateException.class, () -> demo.call(row.path("service").asText(),demo.mapper.convertValue(row.path("input"),Map.class)));
                    assertEquals(row.toString(),code,failure.getMessage());
                } else {
                    var result = demo.call(row.path("service").asText(),demo.mapper.convertValue(row.path("input"),Map.class));
                    var fields = row.path("expected").fields();
                    while (fields.hasNext()) {
                        var field = fields.next(); var actual = result.path(participant).path(field.getKey());
                        if (field.getValue().isNumber()) assertEquals(row.toString(),0,field.getValue().decimalValue().compareTo(actual.decimalValue()));
                        else if (!field.getValue().isNull()) assertEquals(row.toString(),field.getValue().asText(),actual.asText());
                    }
                }
            }
        }
    }
}
