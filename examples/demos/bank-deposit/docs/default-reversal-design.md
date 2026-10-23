> Historical document: this describes pre-refactoring design or acceptance, not current source and test results. See [the current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Default inverse transactions: draft and capability gaps

2026-10-07. This generic design discussion defines no new syntax. It did not modify shared reversal mechanisms. See the demo's [amount and policy plan](reversal-financial-plan.md) and the conceptual responsibilities in [rollback, reversal, and compensation](rollback-reversal-compensation.md).

## Record normal business once; reverse from settlement facts

Normal execution produces a business settlement record. A separate reversal request queries the original transaction and current participants, checks eligibility, generates a default inverse plan with business-specific adjustments, validates it, and executes updates under the APP-selected transaction mode. Deriving inverse arithmetic does not remove business eligibility checks.

Because reversal is independent of the original transaction mode, its basis should be the business update/settlement contract, not quantum-conservation logs alone. Quantum logs provide physical evidence within their scope. Ordinary local transactions and Saga settlements also need usable original facts; reversal must not be restricted to quantum transactions.

Suggested settlement facts include the original business identity, settlement state/date, stable participant identities, operation order, actual update components, original/settled values and versions needed for conditional restoration, repaid principal and interest categories, accrual-base and fee components, original entries, and relevant rule facts. Only business-required information belongs in the contract; network/database layout belongs in APP/PHY. Update records and business results must share the same commit guarantee.

A reversal has its own business identity and references the original transaction. Preserve both records and their association; do not delete original settlement. Repeated requests, reversal by another request, and unknown outcomes need queryable states. A failed call alone must not trigger compensation.

## User-confirmed inverse categories

Classify operations as quantum or non-quantum first, not by numeric field type or assignment syntax.

1. **Quantum updates have deterministic inverses.** Apply the negative original settled component to current state, preserving subsequent independent activity. If a deposit of 10 is followed by spending 100, reversing the deposit removes only that 10, not the spending. Do not require current state to equal the original post-settlement state.
2. **Non-quantum updates require checking subsequent dependencies.** An inverse is safe only if it cannot destroy later operations. After an account receives a deposit of 10, opening cannot simply be undone: the deposit depends on the account's existence even if its opening-status field has not changed.

Field/object version checks are concurrency conditions, not substitutes for business dependency analysis. Numeric fields are not automatically quantum. The defining property is a deterministic inverse that does not overwrite subsequent operations.

This business reversibility classification is distinct from APP transaction mode. Opening an account inside a quantum transaction does not make its eventual business inverse unconditional. Closing an account after deposits is a different operation with its own eligibility and processing rules, not a default undo of opening.

“Deterministic” means the inverse and its components are determined from original settlement facts, not that every reversal request succeeds. Current eligibility, resource availability, and the new transaction outcome still matter. None justify overwriting current state with original values.

## Candidate operations

| Update | Candidate inverse | Required evidence and constraints |
| --- | --- | --- |
| Explicitly quantum update | Apply the negative original actual delta to current state | Original delta, object identity, and reversal eligibility; do not recompute the original formula or require no subsequent settlements |
| Numeric value set from a plan | Determine quantum versus non-quantum semantics from the business contract | Counts, identifiers, and rates are not automatically quantum components |
| Non-quantum assignment, logical state, or object lifecycle | Establish that later operations will not be overwritten, then determine the inverse | Original business and subsequent dependencies; equal versions are insufficient |
| Detail insertion | Preserve the original detail and create linked cancellation/inverse details | Original identity, business audit, and subsequent dependencies; do not delete history by default |
| Irreversible business action | No generic default inverse | Declare a specific reversal or compensation service; the system schedules explicit bindings only |

Failure rollback is different. A quantum transaction eliminates its own effects; committed Saga nodes recover through explicitly bound business reversal or compensation. These default inverses do not give Saga quantum rollback guarantees.

## What the code inspection established at the time

| Code | Confirmed capability | Gap from complete business inversion |
| --- | --- | --- |
| `QuantumBoundaryPayloadCodec` | Exports committed boundaries, recording group, attribute, object, before/delta/after, and transaction/service identities; checks conservation when decoding | Lacks complete business dates, authorization, repayment semantics, fees, and detail relationships; covers only numeric properties included in the boundary |
| `QuantumEntanglementBoundary.negativeDeltaReversal` | Negates original delta and exchanges original after/before in the record | Its before is the original post-settlement value, not current state; it does not reconstruct eligibility or a current-state plan |
| `JournalQuantumAccessAdapter` | Sends inverse boundaries to journal storage; JDBC journals persist numeric values, while in-memory journals store records | An interface does not prove arbitrary business resources were updated; ROLLED_BACK state must distinguish failure rollback from business reversal after success |
| `JdbcQuantumJournalStore.verifyBeforeValue` | Locks/checks before values, persists after values, and checks existing records for repeated transactions | Rejects old inverse boundaries after later updates; cannot directly implement amount reversal that preserves subsequent changes |
| `JdbcTccObjectStoreQuantumCoordinator` | Derives inverse components from settled logs and checks commit evidence and repeated inversion | Still uses original after as the pre-update value; not connected to the selected XBOD eligibility/calendar policy |

These were source-inspection findings, not runtime acceptance of a new reversal. Do not bypass existing conflict rejection. A candidate improvement reads current state within a new reversal transaction, applies original components, revalidates and commits, and preserves original settlement references and independent reversal identity. Agree the business contract and execution plan before changing shared interfaces.

Confirmed quantum-inverse semantics require a new settlement record based on current values and original inverse components. Preserve the original record rather than reusing an old boundary with swapped before/after. Non-quantum operations additionally need recorded/queryable business dependencies; weaker version checks cannot simulate this.

## Proposed execution and acceptance

Query original settlement and current participants → classify and form inverse candidates → check reversal eligibility and subsequent non-quantum dependencies → revalidate and commit within the execution boundary → associate the new reversal with original settlement. Validate mixed operations together; do not reverse amounts first and only then discover an irreversible non-quantum operation.

Business defines relationships and cancellation eligibility. The framework may track dependencies using declared participants/relationships and settlement records. APP/PHY provides reliable queries, locks or version protection, and the chosen transaction mode. Automatic dependency identification remained open: not every read is an irreversible dependency, but tracking only later writes to the same field is also insufficient.

Minimum acceptance: a quantum inverse removes only its original component after subsequent updates; opening can be undone without later dependencies but is rejected after a deposit; dependencies across different objects are identified; dependencies created between check and execution prevent incorrect cancellation; rejecting mixed operations leaves no partial inverse updates. These mechanisms were not implemented at the time of this draft.

## Candidate XBOD path

Read original principal/interest repayment components and interest-bearing-amount changes from KBN records. Ordinary and non-performing-loan deposits use the same original-fact inversion mechanism without rerunning repayment order under current loan status. Query and recheck current balance, freezes, authorization, and reversal eligibility separately. Cross-day accrual, fees, and accounting adjustments need original transaction facts and agreed policies, not merely swapped old boundaries.

The suggestion “equal version means restore by default” was withdrawn: quantum operations invert original components; non-quantum operations first check subsequent-business interference. Distinguish original business facts from derived state. Interest-bearing amount and debt invert original settled components; available balance is derived again from current freeze/authorization facts after reversal, not independently decremented by an old delta for every derived field.

At this historical stage, the demo separately implemented the financial inverse plan and integrated original-settlement queries and financial preparation. Dependency expression, reversal identity, cross-day/refund policy, and compensation bindings still needed work. Complete reversal and generic non-quantum inversion were not yet implemented. Later implementation evidence belongs to the linked acceptance records.
