> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Joint JDBC commit of a reversal plan

> The handwritten Java store/Prepared/Receipt below are historical implementations
> awaiting retirement, not currently permitted production business code. At the
> recorded migration stage, source file 93 expressed full cash commit through SPP
> transaction quantum, APP declared persistence/version conditions and PHY bound
> shared resources. See [original-fact revalidation](participant-facts-and-locked-read.md)
> for preparation/commit layering and verification; final source-only acceptance
> was still pending at that checkpoint.

> Cash policy was subsequently confirmed to follow XBOD; see the
> [source contract](cash-reversal-source.md). The earlier generic financial
> component and explicit refund input below are not the formal cash entry's defaults.

## Implementation boundary

JdbcDepositReversalStore executes a business-supplied plan, not eligibility,
cross-date accrual, daily-total, refund or compensation policy. Prepared contains
the successful original Receipt, current revision/amount/fact snapshots, proposed
snapshot, original components, inverse amounts, explicit refund decision, inverse
postings and business evidence. At this stage the full cash reversal entry was
not connected. Financial-component persistence was not complete bank reversal.

SPP prepareDepositReversalAccountUpdate turns inverse amounts into an independent
account snapshot, retaining current frozen/authorized amounts and loan status.
JDBC SET persists the snapshot, but the business meaning is reversal of original
delta against current amounts. Conditional updates protect that current snapshot;
they do not restore pre-trade account state over later business.

The explicit financial-component fixture has no product/accrual facts: initial
200, original deposit 10, later deposit 20, current 230, original inverse yields
220. Original/refund fees are zero. This does not silently choose retained accrual
or zero refund for real cash transactions.

## Records and identities

Original deposit_trade and deposit_ledger remain. New trades go to
deposit_reversal; an original-trade foreign key and unique constraint permit at
most one committed reversal per original. Inverse postings go to
deposit_reversal_ledger. Separate log formats avoid forcing forward DepositPlan
semantics onto inverses. Actual accounting queries must include both ledgers,
not infer missing reversal postings from the original ledger alone.

Reversal requests have independent IDs retained in shared deposit_request.
Identity contents encode operation kind, original trade ID and stable request
contents. Identity retention is separate from business commit; failed retries
cannot change contents. APP must derive business keys from the full request;
fixed keys in physical tests are only test contracts. Repeat submission uses
stored identity/original Receipt, not a requirement that the current account
still have the reversal's settled revision.

Receipt and reversalOf(originalId) query committed results. Different reversal
IDs racing on one original permit only one commit; the other returns
ORIGINAL_DEPOSIT_ALREADY_REVERSED. Preserving original records does not erase
business facts; original and inverse remain auditable through their link.

## Execution boundary

1. Retain request identity and start a local JDBC transaction.
2. If the reversal ID already settled, validate identity, return original result
   and save replay audit.
3. Lock successful original trade; verify account identity, original version and
   payload against preparation evidence; check whether already reversed.
4. Save reversal/link and conditionally update the account against current
   version, amounts and AccountFacts snapshot.
5. Save inverse postings and success audit in the same commit.

Original-trade locking serializes competing reversals, with unique constraints
as additional protection. It does not lock every later business dependency.
Versions/snapshot conditions detect competing ordinary account updates; after
failure, query again and rerun business checks. Non-quantum dependencies, external
policy-resource revisions and complete eligibility belong to the formal entry,
not this original-trade lock alone.

## Failures and evidence

Pre-commit faults roll back this account update, reversal link and inverse
postings, retaining only request identity and independent failure audit.
Original/later trades remain. A commit exception does not establish absence of
settlement; query by independent reversal ID. Response failure after known
successful commit is CommittedResponseFailed, not automatic compensation.

JdbcDepositReversalStoreTest used actual generated forward/inverse plans and H2
for joint commit, replay after later business, different-ID rejection, stale-plan
repreparation, link/account/partial-posting/pre-commit faults, same/different-ID
concurrency, post-success response failure and changed-original-Receipt rejection.
Recreating an adapter proves reads from the same database, not database-crash recovery.

This phase did not establish complete cash reversal, cross-date policy, quantum
coordination or Saga recovery. Recovery, reversal and compensation remain separate
acceptance responsibilities.

A known settled reversal remains known if replay audit fails. Do not reclassify
it as commit-uncertain or repeat the inverse. Physical layer ReversalReplayAuditFailed
distinguishes this attempt. Unavailable success audit prevents a new inverse
commit. If failure audit is also unavailable, retain the primary exception and
attach the audit exception; persistence of audit cannot be promised. Deposit commit
has the same limitation.

Added tests covered changed contents on a settled reversal ID, full rollback for
missing success audit, and queryable original results despite missing replay
audit. DROP TABLE explicitly injected unavailable demo audit storage; restoring
the table permitted same-request retry. This is not production audit maintenance
or a real-network fault.

## File persistence and actual inverse ledger

ledger(reversalId) reads actual persisted slots, accounts, directions and exact
amounts from deposit_reversal_ledger, not reconstructed trade payload.
A file-database test used normal SHUTDOWN/reopen to check original Receipt,
inverse/link, current account, eight actual postings matching payload, balanced
debits/credits, success audit and stable request identity. A later deposit followed
by reversal replay retained later business. This is orderly reopen, not crash testing.

Changing original Receipt on the same request is a Rejected identity error,
distinct from replay-audit failure. Neither repeats monetary updates.
See [bank qualification](bank-qualification.md) for independent core rules/current
facts; the full entry still needed pre-commit integration and version revalidation.

## Two commit-uncertain outcomes

JDBC proxies separately injected SQLException before actual commit and after the
database had committed. Both saved CommitOutcomeUnknown and required querying
the independent reversal ID. In the first case no trade/inverse ledger existed
and the account stayed 230; one same-ID resubmission produced 220.
In the second, success audit, trade and eight inverse postings already existed;
retry returned the original Receipt and the account stayed 220. Failure events
did not overwrite persisted success events from the same attempt.

These are in-process injections, not actual network/database crashes. Error code
08006 does not raise the evidence level. Original and later deposits survived
both cases. All claims here are preserved from revision `417a41f`, not rerun
for this English translation or guarantees of optional basic-SDK replay.
