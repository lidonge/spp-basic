package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.servpp.service.AtomicService;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.quantum.settlement.ProvisionCommand;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.QuantumSettlementService;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

/**
 * {@code for (Hold h : holds) { account.available -> h.reserved : h.amount; }}: one source funding
 * several collection members is several ordinary transfers, so the boundary nets them as one group.
 */
public class QuantumCollectionFundingTest {
    public static class Account {
        public String accountNo;
        public BigDecimal available;

        public Account() { }

        Account(String accountNo, String available) {
            this.accountNo = accountNo;
            this.available = new BigDecimal(available);
        }

        public BigDecimal getAvailable() { return available; }
        public void setAvailable(BigDecimal value) { available = value; }
    }

    public static class Hold {
        public String holdNo;
        public BigDecimal amount;
        public BigDecimal reserved;

        public Hold() { }

        Hold(String holdNo, String amount) {
            this.holdNo = holdNo;
            this.amount = new BigDecimal(amount);
            this.reserved = BigDecimal.ZERO;
        }

        public BigDecimal getReserved() { return reserved; }
        public void setReserved(BigDecimal value) { reserved = value; }
    }

    /** The quantum update the generator emits for the loop form. */
    private static class FundHolds implements AtomicService {
        @Override
        public Object execute(Object... arguments) {
            Account account = (Account) arguments[0];
            @SuppressWarnings("unchecked")
            List<Hold> holds = (List<Hold>) arguments[1];
            for (Hold hold : holds) {
                account.available = account.available.subtract(hold.amount);
                hold.reserved = hold.reserved.add(hold.amount);
            }
            return null;
        }

        @Override public String getName() { return "fundHolds"; }
        @Override public String getTransactionResourceId() { return "ledger"; }
        @Override public TransactionMode getTransactionMode() { return TransactionMode.QUANTUM; }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return new QuantumConservationParticipant("ledger")
                    .conserveArgument(0, "g1", "Account.available", "available")
                    .conserveEachArgument(1, "g1", "Hold.reserved", "reserved")
                    .resolveObjectIdentities((type, arguments) -> {
                        for (Object argument : arguments) {
                            if (type.equals("Account") && argument instanceof Account account) {
                                return Optional.of(account.accountNo);
                            }
                            if (type.equals("Hold") && argument instanceof Hold hold) {
                                return Optional.of(hold.holdNo);
                            }
                        }
                        return Optional.empty();
                    });
        }
    }

    private static IExecutor executor(FundHolds service, Object... arguments) {
        return new IExecutor() {
            @Override public AtomicService getService() { return service; }
            @Override public Object[] getArguments() { return arguments; }
            @Override public boolean isQuantumUpdate() { return true; }
            @Override public void execute() { service.execute(arguments); }
        };
    }

    @Test
    public void localBoundaryNetsTheLoopAsOneGroup() {
        Account account = new Account("A", "100");
        List<Hold> holds = List.of(new Hold("H1", "30"), new Hold("H2", "20"));
        DefaultTransactionManager manager = new DefaultTransactionManager();

        manager.execute(List.of(executor(new FundHolds(), account, holds)));

        assertEquals(0, account.available.compareTo(new BigDecimal("50")));
        assertEquals(0, holds.get(0).reserved.compareTo(new BigDecimal("30")));
        List<QuantumConservationRecord> records =
                QuantumConservationParticipant.getRecords(DefaultTransactionManager.getLastContext());
        assertEquals(3, records.size());
        assertEquals(0, records.stream().map(QuantumConservationRecord::getDeltaValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum());
    }

    @Test
    public void aLoopThatDoesNotConserveIsStillRejected() {
        Account account = new Account("A", "100");
        List<Hold> holds = List.of(new Hold("H1", "30"), new Hold("H2", "20"));
        AtomicService leaking = new FundHolds() {
            @Override
            public Object execute(Object... arguments) {
                super.execute(arguments);
                ((Account) arguments[0]).available = ((Account) arguments[0]).available.add(BigDecimal.ONE);
                return null;
            }
        };
        IExecutor executor = new IExecutor() {
            @Override public AtomicService getService() { return leaking; }
            @Override public Object[] getArguments() { return new Object[] {account, holds}; }
            @Override public boolean isQuantumUpdate() { return true; }
            @Override public void execute() { leaking.execute(account, holds); }
        };
        assertThrows(IllegalStateException.class,
                () -> new DefaultTransactionManager().execute(List.of(executor)));
    }

    @Test
    public void settlementBridgeSettlesTheLoopAsOneOneToManyCommand() {
        List<SettlementCommand> sent = new ArrayList<>();
        QuantumDefinition money = new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 2);
        QuantumSettlementService service = new QuantumSettlementService() {
            public String name() { return "ledger"; }
            public Set<Capability> capabilities() { return EnumSet.of(Capability.ONE_PHASE); }
            public SettlementResponse settle(SettlementCommand command) {
                sent.add(command);
                return new SettlementResponse(command.key(), Optional.empty(), SettlementResponse.Operation.SETTLE,
                        SettlementResponse.Outcome.SUCCESS, Optional.of("1"), Optional.empty());
            }
            public SettlementResponse reverse(SettlementCommand command) { throw new UnsupportedOperationException(); }
            public SettlementResponse query(SettlementKey key) { throw new UnsupportedOperationException(); }
            public ProvisionResponse provision(ProvisionCommand command) { throw new UnsupportedOperationException(); }
        };
        DefaultTransactionManager manager = new DefaultTransactionManager();
        manager.setQuantumAccessAdapter(new SettlementQuantumAccessAdapter(service, "ns",
                Map.of("Account.available", money, "Hold.reserved", money)));
        Account account = new Account("A", "100");
        List<Hold> holds = List.of(new Hold("H1", "30"), new Hold("H2", "20"));

        manager.execute(List.of(executor(new FundHolds(), account, holds)));

        assertEquals(1, sent.size());
        SettlementCommand command = sent.get(0);
        assertEquals(SettlementCommand.AtomicGroupShape.ONE_TO_MANY, command.shape());
        assertEquals(3, command.mutations().size());
        assertEquals("-50", command.mutations().get(0).delta());
        assertEquals("Account.available", command.mutations().get(0).key().attributeId());
        assertEquals(List.of("H1", "H2"), command.mutations().subList(1, 3).stream()
                .map(mutation -> mutation.key().objectId()).toList());
    }
}
