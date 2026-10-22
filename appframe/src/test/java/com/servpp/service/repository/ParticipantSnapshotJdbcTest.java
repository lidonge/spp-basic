package com.servpp.service.repository;

import org.junit.Test;
import java.util.*;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.AppDdlCatalog;
import static org.junit.Assert.*;

public class ParticipantSnapshotJdbcTest {
    public static class Failure {
        private String reason;
        private List<String> causes;
        public String getReason() { return reason; }
        public void setReason(String value) { reason=value; }
        public List<String> getCauses() { return causes; }
        public void setCauses(List<String> value) { causes=value; }
    }
    public static class Record {
        private String code;
        private Failure original;
        private List<Failure> attempts;
        public String getCode() { return code; }
        public void setCode(String value) { code=value; }
        public Failure getOriginal() { return original; }
        public void setOriginal(Failure value) { original=value; }
        public List<Failure> getAttempts() { return attempts; }
        public void setAttempts(List<Failure> value) { attempts=value; }
    }
    @Test public void jdbcSnapshotRoundTripsLongFactsAndTypedCollections() {
        var p = new Properties(); p.setProperty("tables","RECORDS");
        p.setProperty("table.RECORDS.category","roles");p.setProperty("table.RECORDS.businessType","Record");
        p.setProperty("table.RECORDS.primaryKeys","code");p.setProperty("table.RECORDS.columns","code,original,attempts");
        p.setProperty("table.RECORDS.column.code.type","VARCHAR");p.setProperty("table.RECORDS.column.code.precision","50");
        p.setProperty("table.RECORDS.column.original.type","CLOB");p.setProperty("table.RECORDS.column.attempts.type","CLOB");
        p.setProperty("table.RECORDS.snapshotFacts","original,attempts");
        var catalog = AppDdlCatalog.fromProperties(p);
        var ds = new DriverManagerJdbcDataSource("org.h2.Driver","jdbc:h2:mem:snapshot_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1",null,null);
        new JdbcAppDdlSchemaManager(ds).ensureTables(catalog);
        var failure = new Failure(); failure.setReason("详细失败信息".repeat(1000)); failure.setCauses(List.of("first".repeat(100),"second".repeat(100)));
        var record = new Record();record.setCode("REC-1");record.setOriginal(failure);record.setAttempts(List.of(failure));
        new JdbcAppDdlObjectTableWriter(ds,catalog).save("Record",record);
        var read = new Record();
        assertTrue(new JdbcAppDdlObjectTableReader(ds,catalog).hydrate("Record","REC-1",read));
        assertEquals(failure.getReason(),read.getOriginal().getReason());
        assertEquals(failure.getCauses(),read.getAttempts().get(0).getCauses());
        record.setOriginal(null); record.setAttempts(List.of());
        new JdbcAppDdlObjectTableWriter(ds,catalog).save("Record",record);
        assertTrue(new JdbcAppDdlObjectTableReader(ds,catalog).hydrate("Record","REC-1",read));
        assertNull(read.getOriginal());assertTrue(read.getAttempts().isEmpty());
    }
}
