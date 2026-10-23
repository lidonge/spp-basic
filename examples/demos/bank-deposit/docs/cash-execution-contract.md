> Historical document: this describes implementation and acceptance before the
> refactoring, not current source or test results. See the [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# General cash-deposit execution contract

## Scope and inputs

At this historical stage the entry was APP class `NormalCashDeposit`, invoking
actual generated cash scenarios: `prepareQualifiedCashDeposit` when banking
facts were present, and the corresponding `WithAccrual` scenario for accrual
facts. It accepted only the teller channel, GSA01102 and a fee context consistent
with the cash channel. Channel exceptions and other transaction entries do not
automatically inherit this scenario. Requests contain account identity, amount,
currency, value date and channel/institution facts. Authorization credentials
are passed separately; elevated authorization does not change request identity.

One database query obtains the account version, amount snapshot and
`AccountFacts`: account eligibility, controls, product restrictions, fee-account
identity and optional accrual and banking-range/document facts. Both amounts
and facts are protected by conditional updates. Business date, authorization
policy, detail count and accounting subjects come from demo APP configuration,
not production bank configuration. Configuration must remain stable during an
execution; this version provided no joint locking or versioned-commit guarantee
for external configuration resources.

`DepositDemoServices` registers local implementations in the order atomic
services, query adapters, child scenarios, parent scenarios, because generated
scenario constructors immediately resolve child services. This assembly list
belongs to the demo APP and must track dependency changes. It is not evidence of
generic compiler dependency ordering or production container lifecycle management.

## Business scenario and physical commit

The SPP sequence is: selected entry scope checks → positive amount/business
date/account controls and product eligibility → authorization assessment →
authorization execution threshold → fee query and calculation → core repayment,
amount classification and posting checks → independent account/eligibility
update snapshots.

Warning F0554 permits execution. Pending authorization stops at a business
`check`; APP returns PendingAuthorization. Request identity and that attempt's
pending audit persist, but account, debt, postings and trade remain unchanged.
Resubmitting the same request with authorization evidence queries the current
account and reruns every check; it does not reuse the earlier amount plan.
Credential issuance, approval authority and authenticity belong to the external
application. The demo is not an approval system.

SPP `prepareDepositEligibilityUpdate` updates cash totals from
`admission.dayCashTotal`, retaining transfer totals on the same day and resetting
them on date change; it updates net account balance and last amount-transaction
date. `lastTransactionDate` corresponds to KSADEP0 SA-LTM-TX-DT, not SAACNACN
SA-LAST-TXN-DT. KSADEP0:689 detects date change, 1990–1991 saves/updates the amount
transaction date, 2029–2048 updates daily totals, and 2073 increases detail count
by WK-DET-ITEM. The demo explicitly configures `itemCount=1`; actual multi-detail
generation from WK-DET-ITEM and unprinted counts had not been migrated.

`JdbcDepositStore` jointly commits new amounts, new `AccountFacts`, eight display
postings and original trade payload in one local JDBC transaction. Conditional
updates match the old version, old amount snapshot and old `AccountFacts`.
Mismatch rejects the stale plan and rolls back the attempt's writes. APP/PHY
does not calculate repayment order, debit/credit amounts or daily-total policy.

The trade payload retains the original request, before/after snapshots, plan,
original repayment components, postings, authorization decision and actual fees.
Accounts with accrual facts additionally save ordinary, agreement and overdraft
products and the original period through `prepareCashDepositWithAccrual`; see
the [accrual execution contract](accrual-execution.md). These facts support later
reversal, not a claim that reversal was already implemented. Legacy agreement
query interfaces and unresolved branches still required work.

The banking-qualification scenario checks operation-context binding and deposit
range/document conditions before invoking the cash scenario. Successful trades
retain the attempt's account/operation facts and separate notices. Error
precedence for multiple invalid conditions was not proven equivalent to the
complete original path.

## Request identity and retry

On first admission, `deposit_request` stores the identifier and request contents.
Even pending authorization or business rejection does not permit reuse of that
identifier with changed contents. Amount comparison is numeric: 600 and 600.00
are equal. Other fields use explicit serialized-content comparison. The original
`Prepared` command-level replay API remained for physical-boundary testing.

For a completed trade, the entry first matches identifier and original contents
and returns the original `Receipt`. It does not recalculate using a new account
state, business date, rate or threshold. Concurrent executions of the same
request converge on one result through the jointly committed trade-identifier
unique constraint. Different requests using one identifier return
DEPOSIT_REQUEST_ID_REUSED. Independent trades racing on an old account version
return DEPOSIT_VERSION_CONFLICT; callers must requery and decide whether to
execute again. Conflicts are not silently ignored.

After a lost commit response, the same request can query/retry against persisted
trade evidence. An exception alone cannot prove no commit occurred and must not
trigger automatic compensation. There was no injected network-response-loss or
database-process-crash evidence. These replay claims describe this historical
implementation, not optional coordination guarantees of the basic distribution.

## Historical evidence boundary

`NormalCashDepositTest` exercised generated scenarios through real H2 commits.
`JdbcDepositStoreTest` separately injected failures after account updates, after
partial postings and before commit. Schema, accounting subjects and parameters
were demo configuration. Recreating an adapter and reading the same H2 data is
not a database-process restart test.

Audit persisted rejection, pending authorization, commit, rollback and replay
attempts. Success audit committed jointly; failure audit persisted independently.
See [audit and failure outcomes](audit-and-failure.md) for exceptions and fault
evidence. CLI reporting and joint persistence of the selected accrual model had
run. Deposit-range and lost-document notices were integrated; see
[banking qualification](bank-qualification.md) for sources and limits.

Real bank configuration, complete eligibility boundaries, remaining agreement/
accrual branches and cross-date reversal were still pending at this stage.
Identity and audit records are not a full approval system. Local transaction
guarantees are not quantum-coordinator or Saga acceptance; rollback, reversal
and compensation require separate verification under the confirmed definitions.
These claims are preserved from revision `417a41f`, not tests rerun for translation.
