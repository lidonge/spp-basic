package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.runtime.AppDdlCatalog;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ParticipantStorageSnapshotTest {
    public static class Receipt {
        public String getCode() { return "UNCOMMITTED"; }
        public String getFailureReason() { return "Original failure"; }
    }
    public static class Record {
        public Receipt getOriginal() { return new Receipt(); }
        public List<Receipt> getAttempts() { return List.of(new Receipt()); }
    }
    private AppDdlCatalog catalog(String snapshots) {
        var p = new Properties(); p.setProperty("tables","RECORDS,RECEIPTS");
        p.setProperty("table.RECORDS.category","roles"); p.setProperty("table.RECORDS.businessType","Record");
        p.setProperty("table.RECORDS.snapshotFacts",snapshots);
        p.setProperty("table.RECEIPTS.category","contracts"); p.setProperty("table.RECEIPTS.businessType","Receipt");
        p.setProperty("table.RECEIPTS.primaryKeys","code");
        p.setProperty("table.RECEIPTS.queryService","snapshotTest.findReceipt");
        return AppDdlCatalog.fromProperties(p);
    }
    @Test public void snapshotRetainsFullFactsWhileOrdinaryReferenceStoresOnlyIdentity() {
        var mapper = new ObjectMapper(); var original = new Record();
        var facts = mapper.convertValue(original,Map.class);
        var snapshot = mapper.valueToTree(ParticipantStorageFacts.project(facts,original,catalog("original,attempts"),mapper));
        assertEquals("Original failure",snapshot.path("original").path("failureReason").asText());
        assertEquals("Original failure",snapshot.path("attempts").get(0).path("failureReason").asText());
        var live = mapper.valueToTree(ParticipantStorageFacts.project(facts,original,catalog(""),mapper));
        assertEquals("UNCOMMITTED",live.path("original").path("code").asText());
        assertFalse(live.path("original").has("failureReason"));
        assertFalse(live.path("attempts").get(0).has("failureReason"));
    }
}
