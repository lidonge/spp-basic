package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.AppDdlCatalog;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

public class QuantumParticipantSnapshotTest {
    public static class Account {
        public String code;
        public BigDecimal balance;
        public String getCode() { return code; }
        public void setCode(String v) { code=v; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal v) { balance=v; }
    }
    public static class History {
        public Account original;
        public List<Account> attempts;
        public Account current;
        public Account getOriginal() { return original; }
        public void setOriginal(Account v) { original=v; }
        public List<Account> getAttempts() { return attempts; }
        public void setAttempts(List<Account> v) { attempts=v; }
        public Account getCurrent() { return current; }
        public void setCurrent(Account v) { current=v; }
    }
    @Test public void quantumHydrationPreservesHistoricalSnapshotSubtrees() throws Exception {
        var p = new Properties(); p.setProperty("tables","HISTORY,ACCOUNTS");
        p.setProperty("table.HISTORY.businessType","History");p.setProperty("table.HISTORY.snapshotFacts","original,attempts");
        p.setProperty("table.ACCOUNTS.businessType","Account");p.setProperty("table.ACCOUNTS.primaryKeys","code");
        p.setProperty("table.ACCOUNTS.columns","code");p.setProperty("table.ACCOUNTS.column.code.type","VARCHAR");
        var mapper = new ObjectMapper();
        var ds = new DriverManagerJdbcDataSource("org.h2.Driver","jdbc:h2:mem:history_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1",null,null);
        var ledger = new JdbcQuantumJournalStore(ds);
        var store = new JdbcObjectSnapshotStore(ds,ParticipantSnapshotJson::encode);
        var facts = new QuantumRepositoryFacts(mapper,ledger,AppDdlCatalog.fromProperties(p),List.of("Account.balance"),store);
        var account = new Account();account.code="A";account.balance=new BigDecimal("10");
        var history = new History();history.original=account;history.attempts=List.of(account);history.current=account;
        var stored = mapper.valueToTree(facts.ordinary(history));
        assertEquals(0,new BigDecimal(stored.path("original").path("balance").asText()).compareTo(BigDecimal.TEN));
        assertEquals(0,new BigDecimal(stored.path("attempts").get(0).path("balance").asText()).compareTo(BigDecimal.TEN));
        assertFalse(stored.path("current").has("balance"));
        ledger.seedCurrentValue("funds","Account.balance","A",new BigDecimal("99"));
        var loaded = mapper.treeToValue(stored,History.class);
        facts.hydrate("History","H",loaded);
        assertEquals(0,BigDecimal.TEN.compareTo(loaded.original.balance));
        assertEquals(0,BigDecimal.TEN.compareTo(loaded.attempts.get(0).balance));
        assertEquals(0,new BigDecimal("99").compareTo(loaded.current.balance));
    }
}
