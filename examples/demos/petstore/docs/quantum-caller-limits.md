# Quantum call bounds and automatic settlement checks

Bounds are dedicated quantum-call syntax, not service arguments, wrapper fields
in an agreement or handwritten checks inside the service. The service declares
only the numeric transfer:

```spp
quantum update payOrderFunds(Buyer customer, Merchant merchant, PaymentAgreement agreement) {
    customer.wallet -= agreement.paymentAmount;
    merchant.cash += agreement.paymentAmount;
}
```

Inside an explicit transaction, the caller selects closed intervals:

```spp
payOrderFunds(customer, merchant, agreement) bounds {
    customer.wallet: [0, null];
    merchant.cash: [0, null];
};
```

Each endpoint is a numeric expression or null. A null lower/upper endpoint disables
that respective limit; two null endpoints impose no interval check. Compatible
calls that omit `bounds` add no range restriction, while quantum conservation
and transaction consistency still apply. A caller supporting overdraft may use a
negative lower bound or derive one from credit facts without changing the shared
quantum service. Petstore demonstrates ordinary accounts only and explicitly
chooses zero lower bounds; no credit approval or overdraft business is added.

The compiler requires a quantum update inside an explicit transaction and a
quantum field belonging to an actual participant in that call. It rejects duplicate
fields, non-quantum targets and nonnumeric bounds. Endpoints are evaluated before
the call and travel to the settlement provider. Its implementation constrains the
actual settlement; rejection triggers recovery of the bound transaction, not the
ordinary-check behavior of skipping a block. Collection bounds apply separately
to every member; totals cannot hide a per-product violation.

The inventory domain defines per-product reservation, shipment, release and
return increments/decrements. Sales scenarios select ranges for
`stock.available/reserved/sold`; APP invokes the domain provider. The provider
records conserved changes and the consumer does not post them twice. All service
and scenario entries still consist only of participants; business source has no
exception-control syntax.

When PHY selects BigInteger, generated models use a generic exact-integer
deserializer. Fractional input is rejected, never silently truncated. Physical
input typing belongs to APP/PHY; SPP expresses business conditions such as
positive integral quantities.

The recorded stage performed generation, compilation and static review only.
Runtime checks for negative/null bounds, rejected settlements, per-element
conservation and joint cross-domain recovery still required regression execution.
This translation of revision `417a41f` does not claim those runs.

The JDBC quantum implementation places bounds in the settlement SQL condition.
Other versions may use different mechanisms, but cannot replace enforcement with
a query after the call. Non-JDBC support depends on implementation capability;
an implementation without dynamic bounds must reject the operation explicitly.
See [quantum call bounds](https://github.com/lidonge/spp-examples/blob/main/book/references/quantum-call-bounds.md).
