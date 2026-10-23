# Refactoring: known issues and pending decisions

This ledger records the refactoring's historical evidence and remaining scope. Test counts below are historical, not new results of the English translation. Request coordination and invocation tracing require the optional enhanced providers; the basic profile does not promise those features.

## KI-R01: repayment internal accounts and non-cash quantum facts (closed)

On 2026-10-13, the user clarified that interest repayment must increase the bank's internal interest-income account. Reducing interest owed is not itself a cash destination. The previous explanation using a “negative contribution,” and the resulting decision to block the goal, were withdrawn.

For cash deposited of 100, interest repaid of 10, and balance increased by 90, actual cash movements are settlement account -100, customer balance +90, and bank interest-income account +10: total zero. Interest owed separately decreases by 10 as a discharge fact and must not be counted again in that cash-conservation group. Principal and collection-interest repayment also require real accounting counterparties, not merely journal entries without account updates.

The old implementation lacked real bank-internal-account updates and used the explicit conservation list to restrict quantum recording too. The refactoring added three internal-account Roles, independent zero-funded opening scenarios, actual deposit/reversal updates, and original settlement account references. The generator records all actual quantum updates. APP's property list selects only the cash-conservation group; debt and exposure outside that group retain quantum boundaries, persistence, backfill, and inverse transactions.

Implementation requirements:

1. Add actual internal-account participants, queries, storage, call-site bounds, and original transaction records. Reversal uses original settled accounts and amounts rather than selecting routes again.
2. Check actual cash flows in the conservation group. Debt and limits retain their defined quantum consistency, not ordinary-property semantics.
3. Generic semantics must cover every updated property for quantum persistence, boundaries, and backfill, while checking conservation by the business-declared group. Retain the existing explicit APP property list; all bank formulas remain in SPP.
4. Verify actual interest/principal/mixed repayment and reversal, internal-account bound failures, and system inverse transactions in both environments.

The historical combined LOCAL regression passed 1,166 tests, and final testing-profile XBOD passed 33. Coverage included mixed repayment, internal-account queries, cross-day reversal, internal-account bound failures, and inverse restoration of cash/debt after subsequent ordinary-save failure. This implementation gap was closed; ordinary Saga compensation remains KI-R04. The audit also fixed extraction of historical core-vector decimals such as .005 and changed PHY default capacity to BigDecimal(25, 6), preserving agreed exact business results. This issue no longer blocked the overall goal.

## KI-R02: migrating historical technical recovery coverage

The old 86 APP examples mixed production-scenario composition, fixtures, technical retries, and exception implementations. Retired source remains for comparison. The new version has independent Java acceptance tests; historical success counts cannot simply be carried forward. Unknown commits, recovery restart, remote faults, and high concurrency require migration against the new runtime protocol.

Progress: three independent JVMs covered normal exit/restart, original transaction queries, continued reversal, and persisted journal entries. Forced process exit after confirmed settlement followed by restart/reversal was also added. Crashes during commit, unknown commit outcomes, network faults, and high concurrency require separate verification. See the [historical entry ledger](legacy-entry-migration.md).

Concurrency progress: simultaneous deposits into independent accounts and competing new reversals of one original settlement passed in both configurations. Different requests competing on the same account, and reread/retry after stale-fact conflict, also passed in both. Remote same-serial concurrent waiting, heavy load, and actual network faults remain separate obligations.

Commit-fault progress: LOCAL deposit and reversal gained four-stage JDBC fault injection, reporting definite rollback, unknown commit, or definite settlement, with independent cash/contract/journal checks. Automatic outcome discovery and retry after unknown commit, actual disconnection/process interruption, and testing-profile multistage failures were not equivalently migrated.

## KI-R03: real external bank facts

Providers of product limits, rates, accounts, teller privileges, and authorization facts remain demonstration implementations. The basic eight-slot accounting scheme is the original demo's balanced-entry rule, not recovery of unavailable ART configuration. Production integration belongs in APP service bindings and PHY deployment. UI-supplied qualification data is not trusted production evidence.

## KI-R04: business compensation for committed ordinary Saga stages across stores

In develop's LOCAL deposit scenario, ordinary-save failure rolls back quantum and ordinary changes together. Under testing's unified quantum-store mode, the quantum stage commits first and some ordinary Saga stages may already have settled. The framework can inversely restore quantum cash, but cannot automatically undo committed accrual bases or account details.

When recovery is incomplete, this refactoring's fail path records RECONCILIATION_REQUIRED. The system returns RECOVERY_REQUIRED with handled=false and retains the original failure. Recording business disposition is not successful recovery. Tests check those actual outcomes. Ordinary-stage inverse transactions and compensation need independent business rules that preserve subsequent activity. This follows the project's standing requirement to discuss compensation separately.

Reversal evidence: after inverse-entry save failure, the system restored cross-store funds to original settled balances, but ordinary Saga stages had already saved “original transaction reversed” and a new reversal contract. The system explicitly requires reconciliation rather than allowing treatment as a completed reversal. Undoing those committed ordinary facts or arranging compensation remains to be agreed. The same LOCAL fault rolled back completely and was verified retryable.

Additional evidence: after reversal reads the original contract, another update changes it; the reversal's later update triggers PERSISTENCE_VERSION_CONFLICT. In testing, quantum inverse recovery restores cash and preserves the external contract update, but earlier ordinary stages may already have committed. Report RECOVERY_REQUIRED/handled=false rather than retrying as if everything rolled back. XbodStaleReversalTest covers this; acceptance documentation records the difference and logs.

## KI-R05: responses to running remote requests

The enhanced idempotency API returns REQUEST_IN_PROGRESS for RUNNING requests, preventing business reexecution. Retrying after completion returns the original result. This is not synchronous waiting for the original result. Whether to add bounded APP/PHY waiting and what to return on timeout remain undecided. This refactoring did not add waiting/retry logic to SPP or business Java. The test's single replay after completion verifies a technical protocol only.

## KI-R06: UI duplicate facts across participants (closed)

APP duplicate facts originally accepted only one participant or property path, insufficient for forms combining amounts, fees, and authorization. Support for `facts(path1, path2, ...)` was added; bank deposit checks five participants, while reversal continues to check execution arguments. The framework generates the technical fingerprint without a giant new SPP Contract.

Focused tests cover language validation, generation, nullable-fact content conflicts, single-path compatibility, and remote metadata. A real HTTP request changing only the fee participant returns a content conflict without updating funds again. Historical combined regression on 2026-10-13 passed 1,158 tests, closing this issue but not other bank/recovery limitations. This protocol requires the optional enhanced coordination provider.

## KI-R07: persisting and resuming pending authorization

Old authorizationCashExample / pendingCurrentAccountCashExample / pendingRestartSetupExampleRun checked authorization levels, persisted pending applications, resumed under the same application identity when new evidence arrived, and recorded full history. The new version retained authorization levels, PENDING_AUTHORIZATION evaluation, and explicit scenario checks, but had not introduced an independent authorization-application Contract and resumption scenario. The 49 admission vectors do not prove migration of that persistent lifecycle.

The pending boundary decision is whether a separate bank authorization domain stores/confirms applications or the demo supplies its own application scenario. Neither choice permits restoring business retry loops, technical id/attemptId, or try/catch to SPP. Changing authorization facts and reusing the same remote serial is not a legitimate idempotent replay. This remains a business-flow extension decision while other agreed migration continues.

## KI-R08: settlement-time snapshots in failure records (fixed for current JDBC)

Initially, default queries treated CashFailureRecord.deposit/reversal as live Contract references. An uncommitted deposit caused PARTICIPANT_ASSOCIATION_NOT_FOUND, while live enrichment of an existing reversal could overwrite the original failure code. Later findings included null snapshot columns falling back to old CLOB values and live quantum balances overwriting historical snapshot balances.

APP snapshot support was extended to explicit Entity/Role/Contract associations and collections. CASH_FAILURES deposit/reversal stores whole-participant CLOB JSON snapshots. Storage preserves complete facts; default association queries and quantum-fact enrichment do not enter snapshot subtrees. Updating to null clears old values. Ordinary live associations retain their original query behavior.

Historical evidence: develop clean combined regression passed 1,209 tests. Subsequent quantum-history, JDBC snapshot, and testing-profile transaction/accrual checks passed 15 focused tests. Reread failure disposition checked code, reason, serviceName, resourceName, phase, transactionOutcome, and recoveryStatus. Quantum-history coverage retained snapshot balance 10 while the live association read 99. See latest-refactoring-verification for the execution record.

Deployment limitation: old expanded-column schemas do not automatically migrate to CLOBs. Custom repositories/MyBatis implementations need JSON type handling; compatibility cannot be claimed for every third-party persistence layer. The demo uses generated JDBC and fresh test tables.

## KI-R09: persistent technical audit for all calls and business rejections

The old demo implemented success, precondition rejection, and replay audit through SPP updates and exception flows. The refactoring's APP invocationTrace records calls, and transaction fail saves CashFailureRecord, but a precondition check failure never enters transaction fail. Technical logs are not an independent persistent audit table. Preserving per-call audit requires an APP technical-service contract defining whether audit failure blocks business, storage location, and retention. Do not move old audit try/catch back into business scenarios.

Tests verify that failure-record persistence errors preserve the original transaction failure, secondary-fault information, and actual cash state. This does not implement persistent auditing of every call. The contract remains undecided and does not block other migration. Basic builds without the optional trace provider do not provide invocationTrace logging.

## KI-R10: old generic inverse candidates versus cash reversal rules

The added previewCashReversal supports read-only cash-reversal estimation; two tests passed in each environment. Old exploratory entries for optional partial fee refunds, generic exposure inversion, and business-negative-balance candidates are not all equivalent to cash reversal. Cash refunds original fees; exposure follows the agreed XBOD principalPaid rule. Retain retired source and entry-level differences rather than stretching cash scenarios to mimic other business operations.

Future generic inverse estimation needs separately agreed scenarios and fee-decision participants. The framework must not choose business refunds itself.
