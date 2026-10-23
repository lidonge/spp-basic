# Technical service example: invocation logging and timing

A LAN HTTP page also needs request serial numbers for transport retries and
duplicate-submission protection. The UI prefers `crypto.randomUUID()`; if it is
unavailable in a non-secure context, it uses `crypto.getRandomValues()` to build
a UUID v4 serial number. Retries reuse the original serial number for the same
command. This does not change business participants, idempotency or duplicate
protection semantics.

The requirement is to observe shop-domain scenarios and atomic calls, including
standard CRUD and quantum services, without adding log calls or participants to SPP.

[APP](../src/main/spp/petshop/technical-services.app) declares `invocationTrace`
and matches all shop-domain services with `intercept services`, handling before,
after and failure events. [PHY](../src/main/spp/petshop/technical-services.phy)
selects the generic `InvocationTraceService` and logger
`spp.petstore.invocations`. The framework implementation does not depend on
Petstore-specific fields or business states.

Browse products, reserve stock or place an order in the web UI to observe events:

```text
phase=BEFORE service=petshop.reserveShopPet kind=SCENARIO ...
phase=BEFORE service=petshop.findBuyer kind=ATOMIC_QUERY ...
phase=AFTER service=petshop.findBuyer ... elapsedMicros=...
phase=AFTER service=petshop.reserveShopPet ... elapsedMicros=...
```

Logs contain service names, call kinds, trusted technical headers, timing and
validation results, not customer or order contents. Propagated technical errors
use FAILURE. When insufficient stock is handled by a scenario's `fail` branch
and returns normally, the scenario records AFTER; the failed quantum operation
is recorded where its error actually propagates. A quantum method can return
before its enclosing transaction settles: AFTER does not prove successful settlement.

The shop's consumer proxy and another domain's provider are separate calls.
APP rules apply only to their declaring domain. Configure the product domain
separately to observe it; its SPP need not change. A remote idempotency replay
still logs the scenario entry but does not execute its business atomic services again.

Compared with handwritten logging, the business source stays unchanged and
implicit CRUD is covered. The trade-off is a fixed three-phase protocol without
participant exposure, rather than arbitrary AOP pointcuts. See
[APP technical services](https://github.com/lidonge/spp-examples/blob/main/book/references/app-technical-services.md)
for event semantics, error policy and implementation history.

## Historical verification

The 2026-10-13 record reports 1,123 passing tests in the complete `develop`
regression, including 39 Petstore tests. Three technical-service checks covered:
one BEFORE/AFTER pair for both scenario and type-based implicit `findBuyer`;
normal scenario AFTER following handled insufficient stock, with no repeated
reservation on idempotency replay; and a technical `findBuyer` fault producing
both atomic-service and scenario FAILURE while retaining the original error.

Business Java remained generated from SPP. That implementation added APP/PHY
technical bindings and generic framework capability. These are historical
claims from revision `417a41f`, not tests rerun during this English translation.
