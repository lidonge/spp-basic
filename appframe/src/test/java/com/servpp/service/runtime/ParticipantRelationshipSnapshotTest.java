package com.servpp.service.runtime;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ParticipantRelationshipSnapshotTest {
    public static class Receipt {
        private String number = "UNCOMMITTED";
        public String getNumber() { return number; }
        public void setNumber(String value) { number=value; }
    }
    public static class Record {
        private Receipt original = new Receipt();
        private List<Receipt> attempts = List.of(new Receipt());
        public Receipt getOriginal() { return original; }
        public void setOriginal(Receipt value) { original=value; }
        public List<Receipt> getAttempts() { return attempts; }
        public void setAttempts(List<Receipt> value) { attempts=value; }
    }
    private AppDdlCatalog catalog(String snapshots) {
        var p = new Properties(); p.setProperty("tables","RECORDS,RECEIPTS");
        p.setProperty("table.RECORDS.category","roles"); p.setProperty("table.RECORDS.businessType","Record");
        p.setProperty("table.RECORDS.snapshotFacts",snapshots);
        p.setProperty("table.RECEIPTS.category","contracts"); p.setProperty("table.RECEIPTS.businessType","Receipt");
        p.setProperty("table.RECEIPTS.primaryKeys","number");
        p.setProperty("table.RECEIPTS.queryService","snapshotTest.findUncommittedReceipt");
        return AppDdlCatalog.fromProperties(p);
    }
    @Test public void snapshotsDoNotQueryMissingAssociationOrCollectionMembers() {
        var record = new Record(); var original = record.getOriginal(); var attempts = record.getAttempts();
        ParticipantRelationshipQueries.complete(record,catalog("original,attempts"));
        assertSame(original,record.getOriginal()); assertSame(attempts,record.getAttempts());
        assertEquals("UNCOMMITTED",record.getOriginal().getNumber());
    }
    @Test public void ordinaryAssociationStillRequiresItsQueryService() {
        assertThrows(RuntimeException.class,() -> ParticipantRelationshipQueries.complete(new Record(),catalog("")));
    }
}
