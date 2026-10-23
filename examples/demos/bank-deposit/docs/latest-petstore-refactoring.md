# Deposit demo refactoring aligned with Petstore

## Goal and baseline

Refactor the deposit demo using Petstore's participants, default CRUD, transaction boundaries, quantum bounds, failure information, and technical-service generation. Bank rules remain the checked XBOD cash-deposit/cash-reversal rules. Generic inversion does not replace cash reversal's cross-day policy.

The 2026-10-13 baseline contained 102 old source files and approximately 11,031 SPP lines. Compilation initially failed at legacy query-return syntax in fees. Historical success counts apply only to those versions, not this refactoring's acceptance.

## Functional and business constraints

- Cash deposit: entry eligibility, same-day posting, authorization levels, fees, ordinary/non-performing/written-off overdraft repayment, accrual, balanced entries.
- Cash reversal: apply original settlement components to current accounts without overwriting later transactions; retain XBOD same/cross-day, Mode2, detail-count, accrual deduction, and institution-limit conditions.
- Opening/maintenance is separate from funding. Settlement counterparties receive opposite cash deltas; balances cannot appear from initialization.
- Retain bank business identifiers; keep technical keys, revisions, and idempotency records out of SPP. Contracts reference participants rather than technical foreign keys.
- Scenarios explicitly list participants and queries enrich in place. Calculations neither validate nor invoke ordinary atomic services; scenarios do not invoke scenarios.
- SPP declares consistency boundaries/propagation; APP selects deployment/mode; PHY supplies database/quantum implementations.
- Call sites declare settled-value bounds; quantum services express deltas only. The framework checks bounds, backfills facts, and handles technical inversion.
- fail reads system ResultInfo.failure and records business rejection facts; technical faults are not normal business eligibility branches.

## Implementation path

1. Preserve traceable old source and acceptance entries, separating production rules from fixtures; do not expose historical test scenarios as production services.
2. Organize cboddeposit into account, admission, calculation, fee, accrual, posting, deposit, and reversal directories, one file per scenario.
3. Rebuild explicit Roles/Contracts, retain formula provenance, and remove giant Work objects, return assignments, business idempotency, technical versions, and SPP exception handling.
4. Update default CRUD, APP tables/relationships/environments, numeric defaults, and PHY storage; demonstrate all-service technical tracing.
5. Align LOCAL joint commit and separate-store deployment; verify conservation, settled-value backfill, minimal boundaries, and real failure disposition.
6. Run generation, compilation, acceptance, and necessary generic regressions. Record coverage/issues individually; only actually passed new checks enter the refactored acceptance report.

## Comparison

| Aspect | Refactored SPP demo | Traditional/old demo |
| --- | --- | --- |
| Business scope | Explicit entry Roles/Contracts | Giant work object implicitly carrying all state |
| Queries | Default key CRUD, in-place enrichment | Custom returned objects, instance replacement |
| Validation | Explicit scenario checks/scopes | require inside calculations, exception branches |
| Updates | Ordinary facts in owning tables; quantum deltas/call bounds | Full before/after replacement and repeated persistence |
| Deployment | Replaceable APP environments/PHY storage | Business mixed with technical resources |
| Failure | Framework recovery separated from business fail | try/catch retry/manual recovery orchestration |
| Cost | Explicit Roles, keys, capability matrix required | General-language flexibility, but harder boundary audits |

## Migration ledger and decisions

- Testing follows the later user instruction to move tests out of SPP and compose scenarios in Java when needed, aligned with Petstore. Java tests do not implement business formulas. Historical fixtures remain in legacy-source.
- Real authorization, product limits, rates, and accounts require external configuration. Demo parameters are not production-bank equivalents.
- Complete business compensation remains a project-wide open contract. Use only verifiable disposition expressible by existing FailureInfo/fail.
- Map every historical acceptance obligation to new tests or explicitly mark it unmigrated. Do not obtain “complete success” by shrinking coverage.

## Implementation and behavioral differences

- Old key query/store declarations become default CRUD. Accounts, rates, settlement Contracts, and entries own separate tables. Personalized rates use customer business registration plus fee code.
- Production scenarios do not call scenarios. Intermediate mathematical objects are created only in calculate atomics; scenarios explicitly invoke date/unit/tier/entry checks.
- Loan/account status, authorization level, and admission result use English enums; old Normal account status maps to ACTIVE.
- Missing rates use default queries and main-level checkRequiredFeeIdentified, preserving DEPOSIT_FEE_RATE_NOT_FOUND via participant identification, not null returns/require inside calculations. Authorization assessment produces PENDING_AUTHORIZATION; actual deposit explicitly calls checkCashAuthorization.
- Daily cash accumulations are ordinary facts, not counted again as cash. Accrual uses pre-settlement accounts and persists at submission.
- Quantum calls declare bounds and backfill settled properties. Cash settlement counterparties may be negative rather than inventing opening funds.
- LOCAL and separate-store Saga failures are checked separately. Cash technical recovery is not compensation of ordinary business facts; see KI-R01/KI-R04.
- Generic generation was fixed so even a single-domain explicit application emits environment deployment and domain-qualified service catalogs, allowing actor-chain root queries to reach real providers. A focused generator test was added.

146 historical business vectors became independent test resources. They do not replace all 86 old end-to-end entries, especially restart, communication failure, and compensation. The ledger tracks coverage/limits; unresolved business decisions do not block unrelated implementation.

Early regression counts remain historical. The internal-recipient-account cash model is implemented per the user's contract. See [verification](latest-refactoring-verification.md) for actual evidence and compensation limits.

### Minimal opening inputs

Acceptance directly submits minimal JSON without test helpers filling numeric zeros. It exposed default insert requiring explicit quantum initial values. Generic repository handling now creates zero records for missing quantum numbers during ordinary opening; explicit nonzero values retain funding restrictions. Local insertion still precedes independent quantum-store zero creation after local commit. SPP calculate services explicitly establish ordinary opening facts—normal loan state, active account, zero details/accrual—not Java tests.

Separate-store tests also fixed isolation: previously ordinary stores were isolated but quantum stores were shared with repeated account numbers. Actual opening/deposit left balances that polluted later assertions. Tests now allocate independent ordinary and quantum datasources, never clearing production quantum facts to hide interference.

### Participant ownership

Successful account, qualification, and accrual queries do not prove common ownership. Deposit/reversal check all three account receipts before transactions; reversal also checks the original Contract belongs to the current account. This is business ownership, not technical idempotency/duplicate control.

Fifty-four historical branch-scope, teller-exception, and receipt-warning cases moved to independent JSON acceptance using assessBankQualification's shared checks/calculations. Tests do not implement region/warning rules.

### Reversal entry integrity

Cash reversal must persist inverse entries as well as cash/Contract state. Query eight original entries by Contract, check unique slots/accounts/directions/original components, reverse directions, fully refund cash-fee slots per XBOD, validate balance, then save with reversal Contract/account metadata. Calculate atomics construct entry participants, not scenarios. Contract-key/APP relationship inference supplies original-entry queries without restoring legacy repository-path configuration.

After reversal, derived ordinary net balance is recalculated from quantum-backfilled account components, avoiding stale displayed balances. readCashReversal queries the reversal Contract and persisted inverse entries.

### Inverse-entry save failure

Inject a technical fault before inverse-entry insertion. LOCAL must undo the entire reversal, preserving original cash/unreversed state and allowing retry. Unified quantum store plus Saga reverses quantum cash but cannot claim committed ordinary state/new reversal Contract was undone. fail records reconciliation; the system retains RECOVERY_REQUIRED. Tests query actual cash, original/reversal Contracts, and entries, not just in-memory results.

### Independent JVM restart

Three independent test JVMs perform deposit, query-original/reverse, then query-reversal/entries. Ordinary and quantum facts use temporary file databases. Restart neither reopens accounts nor resets funds and relies on no previous container, participant, or cache. Java probes only prepare inputs/assert outcomes and invoke generated services.

### Cross-day accrual and agreement windows

Actual storage tests deposit, then deposit on a later day. Accrual uses the prior settled balance and elapsed days, not the new post-deposit balance; rereads check persistence. The agreement example uses historical amount 650, ten ordinary days, four agreement-window days, and five thresholds, expecting ordinary accrual 4,300 and agreement accrual 2,200 without reimplementing formulas in tests.

The original 13 production/preview scenarios each had their own cboddeposit-scene-<name>.spp. Opening/shared calculations moved to account/cboddeposit-10-opening.spp, rate maintenance to fee/cboddeposit-50-fee-schedule.spp, shared reversal entries remained reversal/cboddeposit-10-postings.spp. Directory inspection found no multi-scenario file.

### Remote idempotency

Trusted RemoteServiceIngress supplies caller identity/unique serial. Tests verify deposit/reversal replay returns original Contract identities and persisted cash/inverse entries do not repeat; serials are isolated by caller. SPP adds no replay branches. This simulates the trusted runtime protocol, not actual timeout/packet-loss behavior. Current basic builds lack this optional enhanced coordination and explicitly warn; enhanced tests retain these assertions.

### Concurrent independent deposits

Advance two accounts' queries/pre-settlement calculations to the same technical barrier, then execute source scenarios concurrently. Account/settlement participants have distinct business numbers. Database queries verify independent funds of 100/200, Contract ownership, and detail counts. The barrier is outside transactions so test synchronization does not deadlock them.

### Concurrent duplicate remote request

Two threads simultaneously enter trusted ingress using the same caller/serial. A running duplicate may return REQUEST_IN_PROGRESS; replay after completion checks original Contract/system result and actual funds for single execution. Business source neither locks nor queries idempotency records. Thread-start synchronization is not controlled network retransmission; scope remains enhanced runtime-protocol verification.

### Actual loopback HTTP

Tests start generic GeneratedWebServer with APP-generated exposure metadata, then POST deposit/reversal over loopback. They check trusted entry overrides forged client call-mode/caller headers, repeated browser serials replay without repeated cash updates under enhanced duplicate protection, and unexposed services return 404. Random ports exist only for test lifetime; no handwritten bank HTTP business implementation.

Origin.USER uses APP duplicate protection, not RPC idempotency. Deposit/reversal have five-second windows. Deposit distinguishes account/settlement counterparty and checks DepositRequest amount/currency/date; reversal distinguishes account/original Contract and checks execution day/details/Mode2. Origin.SERVICE uses trusted caller/serial; LOCAL is unaffected by UI protection.

The initial form example did not prove combined protection across all external eligibility/fee/authorization facts. Single-path facts syntax was extended to multiple participants/property paths. Trusted business-provider assumptions remain KI-R03. Bank APP now checks DepositRequest, CashChannel, DepositFeeParty, DepositFeeContext, and AuthorizationEvidence without a giant technical-fingerprint SPP Contract.

### Multiple-participant request facts

APP supports `facts(DepositRequest, CashChannel, DepositFeeParty, DepositFeeContext, AuthorizationEvidence)`. The framework combines facts for content comparison; nullable optional facts are not business errors. SPP does not calculate fingerprints. Single-path configuration/metadata compatibility remains. Combined regression and real HTTP content-conflict acceptance passed for the enhanced protocol.

### Repayment counterparties — 2026-10-13 revision

Repaying interest 10 must increase the bank interest-income account by 10. Settlement -100, customer +90, internal account +10 forms the complete cash flow; reduced interest owed is a separate discharge fact. Eight-slot entries alone cannot replace quantum account updates. The earlier negative-debt-contribution-only assumption was withdrawn; debt quantum properties were not downgraded to ordinary facts.

Complete quantum recording is separated from conservation grouping. Existing explicit APP property lists select cash groups; other updated quantum properties retain settlement, bounds, backfill, and inversion. Automatic grouping applies only without explicit selection.

InterestIncomeAccount, CollectionInterestIncomeAccount, and PrincipalRecoveryAccount are Roles played by BankAccount. Independent openBankRepaymentAccounts opens only zero balances, with explicit names for three same-type Entity participants. Deposit updates recipient balances; reversal inverts original amounts. CashDepositAgreement retains original settlement/internal-account references, and scenarios check reversal inputs match. One-file-per-scenario remained, now 14 scenarios.

LOCAL combined regression covered actual repayment, internal-account rereads, cross-day reversal restoring debt, internal-account bounds, and joint cash rollback after ordinary failure. Final separate-store 33 tests passed, closing KI-R01; details remain in that issue.

### Historical rules and persistent recovery

Non-performing/written-off principal-first repayment and fractional debt passed actual deposit, DB query, and cash reversal, with assertions for original accrual, fees, detail counts, and business dates. Core reversal eligibility/exceptions/precondition independence also passed. With file-store WRITE_DELAY=0, forced JVM exit after confirmed settlement followed by query/reversal passed. Historical clean build passed 1,174 tests. Unmigrated old-entry obligations remain listed, especially pending-authorization resumption and compensation.

### Check scope after conditional lookup

SPP check/identify ends only its enclosing block by default. For fee lookup conditional on applicability, the if block only queries. Main-level checkRequiredFeeIdentified checks “inapplicable or rate identified,” exits the scenario on failure, then rate validation/calculation proceeds. identify inside the query block alone would not stop the whole transaction. Deposit/quote use this structure. Calculation previews may supply rate facts directly without persisted-query identity.

### Cash reversal versus generic exposure inversion

Archived cboddeposit-70-reversal-financial.spp negates original exposureDelta. cboddeposit-90-cash-reversal.spp explicitly uses principalPaid for cash compatibility. Example: original principal owed 20/exposure 5; repayment 10 leaves debt 10/exposure 0, delta -5. Generic inversion restores exposure 5; XBOD cash reversal restores 10.

This demo follows confirmed cash rules, not the generic candidate expectation of 5. Technical rollback of quantum stages still negates actual settled deltas, distinct from a requested business reversal. New persisted acceptance distinguishes delta -5 from cash restoration 10 and checks bank principal-recovery balance returns to zero.

### Settlement notices and handling facts

CashDepositAgreement stores this invocation's DepositNotices, DepositOperatorFacts, DepositBankProfile, and AdmissionDecision for later queries. SPP prepareDepositAgreement explicitly records these business inputs/evaluation facts; technical logs do not replace them. Later participant changes must not overwrite original saved facts. External-provider trust remains KI-R03.

## Original-entry account evidence

Old storedLedgerEvidenceExampleRun rejected tampered persisted entry accounts. Checking only nonempty accounts, amounts, directions, and ownership was insufficient. The deposit Contract stores independent snapshots of eight original entries, excluding reverse Contract references to avoid cycles. Reversal compares queried entries against snapshots before inversion. SPP calculate creates snapshots, APP snapshot persists them, scenario check validates them, and tests use SQL fault injection to alter stored accounts.

## Failure disposition failing again

If the original transaction fails and fail cannot save CashFailureRecord due to another DB fault, preserve the primary failure and keep handled=false. FailureInfo.suppressedCodes includes the secondary failure; primary code/reason/transaction outcome describe the original transaction. Tests fail original business persistence, then use an H2 trigger to reject failure recording, checking returned facts, actual cash, and no residual disposition record. This is generic runtime protocol, not bank Java business logic.

## Read-only cash-reversal preview

previewCashReversal explicitly declares current account, original settlement, eligibility, capacity, accrual, and candidate-entry participants. It reuses formal reversal's pretransaction queries/checks/calculations without calling scenarios, opening transactions, or updating quantum/ordinary facts. It shows current candidate facts, reserves no capacity, and promises no future settlement. Formal reversal rereads facts and applies revision/bounds protection.

Cash refunds original fees; generic partial-refund exploration is not mixed into this policy. Acceptance queries persisted account/original Contract/entries and proves both successful and rejected previews have no settlement effects.

Historical regression counts on this page retain their original scope; current profile results are recorded separately rather than inferred from translation.
