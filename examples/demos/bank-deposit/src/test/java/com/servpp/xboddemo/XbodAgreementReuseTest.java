package com.servpp.xboddemo;

import org.junit.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.amount;

public class XbodAgreementReuseTest {
    @Test public void switchingModeClearsPreviousBandProductsOnSameParticipants() throws Exception {
        try (var demo = new XbodTestSupport(); var input = getClass().getResourceAsStream("/agreement-product-cases.json")) {
            var cases = demo.mapper.readTree(input);
            var source = cases.get(1); assertEquals("02",source.path("name").asText());
            var request = demo.request("previewAgreementProduct",demo.mapper.convertValue(source.path("input"),Map.class));
            var first = demo.execute("previewAgreementProduct",request);
            amount("2000",first.path("AgreementBandArray").get(0).path("product"));
            for (String name : new String[]{"Normal","Overlap"}) {
                var period = request.getClass().getMethod("get"+name).invoke(request);
                period.getClass().getMethod("setAmount",BigDecimal.class).invoke(period,new BigDecimal("500"));
            }
            var policy = request.getClass().getMethod("getAgreementPolicy").invoke(request);
            policy.getClass().getMethod("setMode",String.class).invoke(policy,"2");
            policy.getClass().getMethod("setCurrency",String.class).invoke(policy,"CNY");
            var result = demo.execute("previewAgreementProduct",request);
            amount("3400",result.path("AgreementProduct").path("normalProduct"));
            amount("1600",result.path("AgreementProduct").path("bandProduct"));
            amount("100",result.path("AgreementProduct").path("remainingAmount"));
            assertEquals(0,result.path("AgreementProduct").path("selectedSlot").asInt());
            assertEquals("2",result.path("AgreementProduct").path("appliedMode").asText());
            for (int i=0;i<5;i++) amount(i==0?"0":"400",result.path("AgreementBandArray").get(i).path("product"));
        }
    }
    @Test public void invalidBandsAndOverlapLeaveAllParticipantsUnchanged() throws Exception {
        try (var demo = new XbodTestSupport()) {
            for (int caseNo=14;caseNo<=17;caseNo++) {
                var bands = new ArrayList<Map<String,Object>>();
                for (int slot=5;slot>=1;slot--) bands.add(new HashMap<>(Map.of("slot",slot,"threshold",slot*100,"product",999)));
                if (caseNo==14) bands.remove(4);
                if (caseNo==15) Collections.swap(bands,0,1);
                if (caseNo==16) bands.get(4).put("threshold",-1);
                var request = demo.request("previewAgreementProduct",Map.of("normal",Map.of("amount",650,"unit",1,"days",10),
                        "overlap",Map.of("amount",650,"unit",1,"days",caseNo==17?11:4),
                        "AgreementPolicy",Map.of("enabled",true,"mode","2","currency","CNY"),"AgreementBandArray",bands));
                var before = demo.mapper.valueToTree(request);
                assertEquals(caseNo==17?"INVALID_AGREEMENT_PERIOD":"INVALID_AGREEMENT_BANDS",
                        assertThrows(IllegalStateException.class,() -> demo.execute("previewAgreementProduct",request)).getMessage());
                assertEquals(before,demo.mapper.valueToTree(request));
            }
        }
    }
}
