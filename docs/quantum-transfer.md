# Quantum transfer statements

Requirement QT-1, revision 1. The statement belongs to a `quantum update`
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

The endpoints are two distinct scalar quantum attribute declarations on service
parameters or on elements bound by a participant collection loop. An explicit APP conservation selection must include both endpoints.
Multiple statements express multiple transfers, each with its own amount.
APP chooses the conservation resource and the deployment transaction. Grouping
the calls in a scenario transaction makes the transfers commit together.

Acceptance covers the two generated movement directions, one evaluation of
the amount expression, and rejection of ordinary services, ordinary fields,
identical attribute declarations and a standalone state binding. The
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
