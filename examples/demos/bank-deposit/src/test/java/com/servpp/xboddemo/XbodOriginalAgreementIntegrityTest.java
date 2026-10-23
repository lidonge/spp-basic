package com.servpp.xboddemo;

import org.junit.Test;
import java.sql.DriverManager;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.servpp.service.repository.JdbcObjectSnapshotStore;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodOriginalAgreementIntegrityTest {
    @Test public void missingOriginalIsNotTreatedAsACommittedDeposit() throws Exception { verify(0); }
    @Test public void storedUnbalancedOriginalIsRejectedBeforeReversal() throws Exception { verify(1); }
    @Test public void storedOriginalAccountChangeIsRejectedBeforeReversal() throws Exception { verify(2); }
    private void verify(int mode) throws Exception {
        try(var demo=new XbodTestSupport()) {
            open(demo);openSecondAccount(demo);var paid=demo.call("depositCash",deposit(100));
            String number=paid.path("CashDepositAgreement").path("depositNumber").asText();
            String url=System.getProperty("spp.xbod.datasource-url");
            if(mode>0) {
                var store=new JdbcObjectSnapshotStore(new DriverManagerJdbcDataSource("org.h2.Driver",url,null,null),value->demo.mapper.valueToTree(value).toString(),"xbod_cash_record");
                int changed=0;
                for(var row:store.findAll()) {
                    if(!row.objectType().equals("CashDepositAgreement"))continue;
                    var payload=(ObjectNode)demo.mapper.readTree(row.payloadJson());
                    if(!number.equals(payload.path("depositNumber").asText()))continue;
                    if(mode==1)payload.put("amount",999);else ((ObjectNode)payload.path("account")).put("accountNumber","ACCOUNT-002");
                    store.save(row.objectType(),row.objectId(),payload);changed++;
                }
                assertEquals(1,changed);
                try(var connection=DriverManager.getConnection(url);var statement=connection.prepareStatement(mode==1 ? "UPDATE CASH_DEPOSITS SET amount=999 WHERE depositNumber=?" : "UPDATE CASH_DEPOSITS SET account_accountnumber='ACCOUNT-002' WHERE depositNumber=?")) {
                    statement.setString(1,number);assertEquals(1,statement.executeUpdate());
                }
            }
            var input=reversal(paid.path("CashDepositAgreement"),20261007,false);
            if(mode==0)input.put("CashDepositAgreement",Map.of("depositNumber","NONEXISTENT"));
            String expected=mode==0 ? "PARTICIPANT_NOT_IDENTIFIED" : mode==1 ? "ORIGINAL_DEPOSIT_MOVEMENT_NOT_BALANCED" : "ORIGINAL_DEPOSIT_ACCOUNT_MISMATCH";
            assertEquals(expected,assertThrows(IllegalStateException.class,()->demo.call("reverseCash",input)).getMessage());
            amount("100",demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            amount("0",demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-002"))).path("DepositAccount").path("interestBase"));
            amount("-100",demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001"))).path("CashSettlementAccount").path("balance"));
            try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement()) {
                for(String table:new String[]{"CASH_REVERSALS","CASH_REVERSAL_POSTINGS"})try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
                    assertTrue(rows.next());assertEquals(0,rows.getInt(1));
                }
            }
        }
    }
}
