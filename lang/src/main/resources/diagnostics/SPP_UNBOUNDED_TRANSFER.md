# SPP_UNBOUNDED_TRANSFER

## Rule

A quantum transfer can debit a position below zero and the call supplies no effective bounds for that position.

## Example

```spp
quantum update pay(Wallet source, Drawer target, Payment order) {
    source.balance -> target.cash : order.amount;
}
Scenario serial checkout(Wallet payer, Drawer till, Payment order) {
    transaction {
        pay(payer, till, order) bounds { payer.balance: [0, null]; };
    }
}
```

The zero lower bound rejects a debit that would overdraw the payer. Specify the scenario argument name in the bounds entry. A bound on the receiving drawer constrains a different position. A service constraint such as `check(source.balance >= 0);` can enforce the same floor. A PHY quantum minimum of zero or greater already protects the debit. Leave the position unbounded when the business model allows negative balances.
