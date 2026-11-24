# Quantum transfer statements

Requirement QT-1, revision 2. The statement belongs to a `quantum update`
body under [CORE-1](../EXTENSION-CONTRACTS.md#core-1-business-behavior-and-persistence).

```spp
quantum update receiveCash(CashHandover, TellerDrawer, CashReceipt) {
    CashHandover.cash -> TellerDrawer.cash : CashReceipt.amount;
}
```

The compiler evaluates the amount once, before either movement. It subtracts
the amount from the source and adds the same amount to the destination. A
negative or null amount is rejected. Zero has zero effect. Endpoint conversions
use the existing exact numeric rules. Bounds and runtime conservation checks
apply to the resulting changes.

Each endpoint identifies a scalar quantum field on a participant instance. Two
parameters of the same entity or take-mapped role can move the same field
between different keys (for example, `sender.balance -> recipient.balance`).
Two APP-played roles may also select the same provider field. The runtime uses
the selected provider's full primary key, including mapped key codes. A
successful movement records the debit and credit under their respective keys.
Bounds apply to the participant named by each interval.

Writing the same endpoint on both sides is a compile error. If two distinct
endpoints resolve to the same stored attribute and key at execution time, the
runtime rejects the statement with `QUANTUM_SELF_TRANSFER` and rolls back the
transaction, including for a zero amount. Different fields on the same object
remain distinct positions. Endpoints can also be elements bound by a participant
collection loop. An explicit APP conservation selection must include the field
of each endpoint. A shared field is listed once.

Multiple statements express multiple transfers, each with its own amount.
APP chooses the conservation resource and the deployment transaction. Grouping
the calls in a scenario transaction makes the transfers commit together.

Acceptance covers the two generated movement directions, one evaluation of
the amount expression, and rejection of ordinary services, ordinary fields,
identical endpoints and a standalone state binding. The
cash-deposit application tests the generated services against a JDBC store.

The compiler change was developed from Basic `15a6db2`. The language
reactor passed 396 tests using
`mvn -o -B -f basic/pom.xml -pl lang -am test`. A freshly packaged
Basic CLI generated the cash repayment application, whose 41 tests
passed. These checks cover the Basic H2 profile.


## Compiler enforcement

P0-a rejects direct assignments and increments to quantum fields in SPP and
APP. Move the update into a quantum transfer. A scenario may call atomic
services. Calculate services may compose other calculate services. Read
collections in the scenario before iterating over them in a calculation.
Supply a scalar enum value as the rejection code of an SPP `require`.

P0-b exposes scenarios as front-end operations. Use
`LocalGeneratedServiceRuntime.executeGeneratedService` or the transport entry
to invoke a scenario. Each invocation may enter one scenario.

For a staged migration, start the compiler JVM with
`-Dspp.rule.P0-a=warning` to report direct writes or
`-Dspp.rule.require-enum=warning` to report legacy string literals.
Each flag applies independently. Omit the flags to enforce both rules.
