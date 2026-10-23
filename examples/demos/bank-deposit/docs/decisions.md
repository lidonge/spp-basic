> Historical document: this describes implementation and acceptance before the
> refactoring, not current source or test results. See the [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Decision record

## Established decisions

- Create an independent demo; complete documentation before phased implementation.
- Validate four areas: calculation boundaries, banking rule library, end-to-end
  posting consistency, and reversal including duplicates/cross-date handling.
- Business numbers do not choose storage precision. Express business rounding
  explicitly; common numeric types have built-in adaptations and PHY allows
  explicit extensions.
- APP owns shared-service deployment choices. Original bank source is read-only.

## Decisions confirmed for this phase

| Question | Selected recommendation | Implementation impact |
| --- | --- | --- |
| Migration target for legacy numeric behavior | Prioritize business semantics; record unconfirmed truncation differences | Determines digit-for-digit compatibility for boundaries such as three-decimal interest; implement only confirmed calculation domains until clarified |
| First transactions | General cash deposits, ordinary/bad-debt overdraft deposits and corresponding reversals | Adding transfers would require source-account identity/eligibility and a joint transaction contract |

The user selected both recommendations. Specific rounding and currency rules
still require source traceability; this choice does not imply arbitrary policy.

## Topics to revisit with source evidence

Whether authorization retries the entire request or resumes a plan first
requires inspection of the runtime and a version-revalidation design. Product,
fee and accounting configuration, and obtaining original COBOL golden results,
are to be discussed with concrete source and cases at the relevant phase.
Source-derived expectations and explicit demo configuration permit progress
without the original runtime, but do not increase the evidence level.

## Direction for reversal automation

The confirmed conceptual split is: rollback is technical recovery; quantum
transactions provide deterministic rollback within their supported boundary.
Committed Saga stages recover through business reversal/compensation. Reversal
is a new business transaction, often using a default inverse transaction.
Compensation must be explicitly supplied by business policy and cannot be
inferred automatically. See the [semantics discussion](rollback-reversal-compensation.md).
The implementation contract was not settled at this stage, so generalized
inverse-transaction work was deferred. The demo separately verified financial
inverse components, postings and JDBC component commits; non-quantum dependencies
and unconfirmed policy were not inferred. The historical goal remained active.

The user asked whether the technical layer could automate reversal. The design
direction is to record and execute inverse changes where possible, while
distinguishing failed-transaction rollback from reversal after success. The
former restores an unsuccessful transaction; the latter is a new operation with
eligibility, dates and original-trade evidence.

SPP may declare reversible update relationships, original settled components,
reversal conditions and special calculations. The framework records, schedules,
coordinates idempotency and executes inverses. Whether the runtime could fully
support these declarations had not been confirmed. P4 must inspect capability
first; a design direction is not an implemented guarantee.

Existing technical groundwork included
`DefaultTransactionManager.committedQuantumBoundaryPayload()` to export committed
quantum boundaries and `reverseCommittedQuantumBoundary(payload)` to execute
inverses through physical adapters. This established partial framework support,
not coverage of this demo's debt components, interest, fees, authorization or
reversal eligibility. P3/P4 required concrete verification.

For XBOD, first validate whether original KBN repayment components can drive
recovery. Do not restore old account snapshots over later transactions. Apply
inverse components to current state and verify eligibility, concurrency and
duplicate reversal. Cross-date interest, fees and external effects require
explicit business contracts; their inverses cannot be inferred from one assignment.

## Default inverse for ordinary assignments/state: discussion pending

The user did not approve restoring an old value merely because the current
version still matches the original trade and eligibility passes. Version checks
prevent overwriting later changes but do not define business cancellation.
Further implementation awaited discussion. Candidate operation types, settled
facts and adapter differences are in the [default reversal draft](default-reversal-design.md).

## Quantum versus non-quantum inverse: confirmed classification

The user clarified that quantum inverses do not overwrite later changes and can
provide deterministic inverse transactions: spending after a deposit does not
prevent reversing only that deposit component. Non-quantum inverses must establish
that they do not overwrite other completed operations. An account with subsequent
deposits cannot have its opening reversed. This replaces suggestions based only
on assignment/increment distinctions or matching versions.

Non-quantum decisions require cross-operation and cross-object business
dependencies, not just checking whether the original field changed. The existing
JDBC inverse boundary's original-after-value check could not support independent
later quantum updates. That difference and concrete acceptance were added to the
draft. Classification was settled; dependency recording/execution still needed
discussion.

## Cash reversal rules: follow XBOD

The user first required the original XBOD cross-date reversal rules, then
confirmed that cash reversal as a whole follows XBOD. Dates, daily totals,
accrual products, eligibility, fees and original-record handling follow active
source. Do not ask the user to choose newly invented cross-date/refund policies.
Comments in original code are not active rules.

KSAFEE0 section 5500 restores actual original fees from KBN instead of repricing
on the reversal date. The selected teller cash-fee path therefore refunds the
actual original charge. See the [cash reversal source contract](cash-reversal-source.md)
for cross-date account handling. The formal cash entry also prioritizes XBOD's
overdraft-exposure restoration formula. Existing generic non-overwriting inverse
component tests remain independent capability checks, not bank-rule equivalence.

Keep explicit the bank-configuration fixtures, unexecuted original COBOL and
unintegrated peripheral limits/registration. These limitations do not require
the user to redesign already existing cash business rules.

## Historical 2026-10-08 source-only requirement

At that stage the user required only SPP, APP and PHY, with all Java generated:
no handwritten Java. Complete transaction boundaries and mode had to be visible
in SPP, not hidden in Java commit code. Syntax gaps had to be identified and
fixed rather than bypassed with handwritten implementations. See the
[source-only audit](source-language-only.md) for the historical noncompliance
inventory and migration conditions. This superseded earlier handwritten
deployment, query, commit and demo-entry choices.

The later applicable demo instructions permit Java tests to prepare inputs,
compose generated services, assert results and inject technical faults.
Handwritten production business Java remains prohibited. The historical blanket
test prohibition above is not the current contribution rule.
