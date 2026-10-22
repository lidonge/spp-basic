package com.servpp.service.runtime;

import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantRelationshipViewCacheTest {
    public static class Row {
        private String label;
        public String getLabel() { return label; }
        public void setLabel(String value) { label = value; }
    }
    public static class Rows {
        private List<Row> rows = new ArrayList<>();
        public List<Row> getRows() { return rows; }
        public void setRows(List<Row> value) { rows = value; }
    }
    public static class RowFinder implements Service {
        final com.servpp.service.repository.JdbcObjectRepository repository;
        RowFinder(com.servpp.service.repository.JdbcObjectRepository repository) { this.repository = repository; }
        public String getName() { return "privateRows.findRow"; }
        public Object execute(Object... values) { throw new AssertionError("Use typed query"); }
        public void findRow(Row row) {
            String id = com.servpp.service.repository.RepositoryParticipantSnapshots.objectIdentity(row, "Row").orElseThrow();
            ParticipantFacts.hydrate(row, repository.require("Row", id, Row.class));
            ParticipantIdentity.confirm(row, true);
        }
    }

    @Test public void distinctStoredRowsDoNotShareAnUnidentifiedRootCacheEntry() {
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:relationship_rows_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var p = new Properties(); p.setProperty("tables", "ROWS");
        p.setProperty("table.ROWS.category", "roles"); p.setProperty("table.ROWS.businessType", "Row");
        p.setProperty("table.ROWS.primaryKeys", "rowKey"); p.setProperty("table.ROWS.columns", "rowKey,label");
        p.setProperty("table.ROWS.column.rowKey.objectBacked", "false");
        p.setProperty("table.ROWS.queryService", "privateRows.findRow");
        var store = new com.servpp.service.repository.JdbcObjectSnapshotStore(source, value -> mapper.valueToTree(value).toString());
        var repository = new com.servpp.service.repository.JdbcObjectRepository(store, mapper).useAppDdlCatalog(AppDdlCatalog.fromProperties(p));
        com.servpp.service.transaction.DeclaredTransactionContext.execute(com.servpp.service.transaction.TransactionMode.LOCAL,
                () -> repository.executeInTransaction(() -> {
                    store.insert("Row", "one", Map.of("label", "first"));
                    store.insert("Row", "two", Map.of("label", "second"));
                    store.insert("Rows", "root", Map.of("rows", List.of(
                            Map.of("label", "", "$sppStorageIdentity", Map.of("type", "Row", "id", "one")),
                            Map.of("label", "", "$sppStorageIdentity", Map.of("type", "Row", "id", "two")))));
                }));
        var container = DefaultServiceContainer.getInstance();
        Service previous = container.registerService("privateRows.findRow", new RowFinder(repository));
        try {
            LocalDomainServices.executeScope(() -> {
                var rows = repository.require("Rows", "root", Rows.class).getRows();
                assertEquals(List.of("first", "second"), rows.stream().map(Row::getLabel).toList());
                assertEquals("one", com.servpp.service.repository.RepositoryParticipantSnapshots.objectIdentity(rows.get(0), "Row").orElseThrow());
                assertEquals("two", com.servpp.service.repository.RepositoryParticipantSnapshots.objectIdentity(rows.get(1), "Row").orElseThrow());
            });
        } finally {
            if (previous != null) container.registerService("privateRows.findRow", previous);
            else container.clear();
        }
    }
    public static class Facts {
        private String id="same",name="unloaded";
        public String getId(){return id;} public void setId(String value){id=value;}
        public String getName(){return name;} public void setName(String value){name=value;}
    }
    public static class Owner { public static class Account extends Facts {} }
    public static class Consumer { public static class Account extends Facts {} }
    public static class Associations {
        private Owner.Account first=new Owner.Account();
        private Consumer.Account second=new Consumer.Account(),third=new Consumer.Account();
        public Owner.Account getFirst(){return first;} public void setFirst(Owner.Account value){first=value;}
        public Consumer.Account getSecond(){return second;} public void setSecond(Consumer.Account value){second=value;}
        public Consumer.Account getThird(){return third;} public void setThird(Consumer.Account value){third=value;}
    }
    public static class Finder implements Service {
        int calls;
        public void findAccount(Owner.Account account){calls++;account.setName("loaded");ParticipantIdentity.confirm(account,true);}
        public String getName(){return "cacheViews.findAccount";}
        public Object execute(Object... input){throw new AssertionError("Invoke declared participant method");}
    }
    @Test public void completedViewsAreCachedByJavaTypeWhileCanonicalCycleDetectionStaysShared() {
        var p=new Properties();p.setProperty("tables","ACCOUNTS");p.setProperty("table.ACCOUNTS.category","roles");
        p.setProperty("table.ACCOUNTS.businessType","Account");p.setProperty("table.ACCOUNTS.primaryKeys","id");
        p.setProperty("table.ACCOUNTS.queryService","cacheViews.findAccount");
        var container=DefaultServiceContainer.getInstance();container.clear();var finder=new Finder();container.registerService(finder.getName(),finder);
        try {
            var root=new Associations();var first=root.getFirst();var second=root.getSecond();var third=root.getThird();
            LocalDomainServices.executeScope(()->ParticipantRelationshipQueries.complete(root,AppDdlCatalog.fromProperties(p)));
            assertSame(first,root.getFirst());assertSame(second,root.getSecond());assertSame(third,root.getThird());
            assertEquals("loaded",first.getName());assertEquals("loaded",second.getName());assertEquals("loaded",third.getName());
            assertEquals(2,finder.calls);
        }finally{container.clear();}
    }
}
