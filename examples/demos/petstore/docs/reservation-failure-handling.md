# Business handling after an accepted reservation fails

Reservation first commits an accepted application, then attempts quantum stock
reservation. If the quantity exceeds available stock, the second transaction
fails but the accepted application remains. The framework and selected quantum
implementation recover stock. SPP's `fail` block rereads stock, records rejection
and its business reason, and saves that outcome in a separate transaction.

## Complete source entry points

The [reservation scenario](../src/main/spp/petshop/reservation/petshop-scene-reserveShopPet.spp)
and its [agreement, checks and calculations](../src/main/spp/petshop/reservation/petshop-35-reservation.spp)
together express:

```spp
transaction { insertPetReservationAgreement; }
prepareReservationInventory; findStockLine; identify(StockLine[]);
transaction {
    reserveRequestedInventory bounds {
        StockLine[].available: [0, null]; StockLine[].reserved: [0, null]; };
    confirmPetReservation; updatePetReservationAgreement;
} fail {
    if (ResultInfo.failure.recoveryStatus == "ROLLED_BACK" || ResultInfo.failure.recoveryStatus == "REVERSED") {
        findStockLine; identify(StockLine[]);
        rejectPetReservation;
        transaction { updatePetReservationAgreement; }
    }
}
```

ResultInfo is a built-in scenario participant, not an entry parameter or a
declared Role. The compiler supplies `resultInfo` in the agreement. The scenario
reads system failure facts, while the agreement's own `rejectionReason` enum
stores the business refusal. Atomic calculations read stock and requested quantity,
not exception objects or system participants.

| Input or fault | Accepted application | Final stock: available / reserved | Business outcome |
| --- | --- | --- | --- |
| Available 10, requested 11 | Same reservation number retained | 10 / 0 | REJECTED, INSUFFICIENT_STOCK |
| Available 10, requested 2 | Same reservation number retained | 8 / 2 | RESERVED, no rejection reason |
| Sufficient reservation, then ordinary save fails | Same reservation number retained | Restored to 10 / 0 | REJECTED, RESERVATION_NOT_COMPLETED; do not misreport insufficient stock |
| Commit or recovery outcome uncertain | Original application can be queried by reservation number | Do not claim recovery | RECOVERY_REQUIRED; APP/PHY continues technical handling |
| Business save inside fail also fails | Accepted facts remain | Prior technical recovery is not rewritten | Do not claim handling completed; APP/PHY receives original and additional handling errors |

A bound rejection has no successful quantum settlement to reverse. Under LOCAL,
executed but uncommitted changes roll back with the database transaction.
Under non-LOCAL policies, the system reverses the already-settled quantum phase.
The `fail` block does not manually add stock back, delete an accepted application
or overwrite quantum storage with a currently queried value.

After handling, this invocation's ResultInfo.status remains FAILED and handled is
true: the original reservation failure has been handled. The scenario may return
that business result normally. With compatible request coordination enabled,
remote replay returns the same result without accepting or compensating twice.
Historical queries read persisted business status and rejection reason;
`resultInfo` is per-invocation response metadata and is not stored in a business
table by default.

The first implementation supports fail on an outer transaction in a serial
scenario; fail in nested or parallel branches is rejected at compilation.
With multiple entry agreements, `fail(targetAgreement)` selects the target.
See [system results and failure handling](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-failure-handling.md).
Broader compensation policies require their own business definitions and cannot
be inferred from this example.

System failure facts include QUANTUM_BOUND_REJECTED, the QUANTUM_SETTLEMENT phase,
the failing service, StockLine.available, attempted value -1 and lower bound 0.
The business refusal is still derived from recovered stock facts and requested
quantity, not a Java exception class.

## Historical verification

The 2026-10-13 record reports the complete develop generation/regression passing
1,104 tests, including 36 Petstore tests. The insufficient-stock case retained the
same reservation number, quantity 11, REJECTED/INSUFFICIENT_STOCK, and stock 10 / 0.
Replaying the original input with the same serial number returned the same
reservation number and ResultInfo without another acceptance. The ordinary-save
fault case recovered stock, then saved RESERVATION_NOT_COMPLETED in a new
transaction without incorrectly reporting insufficient stock.

The runtime separately verified quantum reversal after an actual JDBC settlement
followed by failure. Petstore used LOCAL deployment in this record; real
distributed TCC or external quantum-service acceptance was not run.

English translation of revision `417a41f`; these are historical results, not new
runs. Replay assertions require the optional request-coordination capability;
the basic fallback must not be represented as providing it.
