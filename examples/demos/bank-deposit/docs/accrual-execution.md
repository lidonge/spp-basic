> Historical document: this describes implementation and acceptance before the
> refactoring, not current source or test results. See the [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Accrual-product calculation and joint commit

## Source paths and business inputs

KSADEP0:1802–1867 passes pre-trade SA-INTC-AMT, SA-LTM-PDTC-DT and the current
business date to CSAPRD2 to obtain ordinary and five-band agreement products.
Ordinary accumulation adds this ordinary product to the old SA-DDP-PDT. The cash
entry accepts only same-day value dates, so it does not execute the backdated
increment in section 4142.

KSADEP0:1892–1920 separately passes pre-trade principal, ordinary overdraft
interest and collection interest to CSAPRD4. CSAPRD4:146–151 always uses actual
days and function O; it cannot reuse the deposit product's 360-day calendar.
Active statements in section 5200 explicitly set unit .01. The PERFORM that
queries the currency unit is commented out. Unit rounding and workspace PIC
truncation have different origins.

`DepositAccrualInput` contains independent accrual state, product interest
policy, calendar, agreement activation/cancellation and mode facts. Five
`AgreementBand` values retain thresholds and accumulated products.
`lastProductDate` corresponds to SA-LTM-PDTC-DT, independently of the daily-total
date SA-LTM-TX-DT. These dates are not interchangeable.

`DepositInterestPolicy.normalUnit` and `overdraftUnit` are explicit business
quantization units, independent of PHY BigDecimal representation and database
persistence. The demo chooses deposit unit 1 and overdraft unit .01; policies
may declare other units. `normalEnabled` controls only ordinary/agreement
products, not whether existing overdrafts continue accruing.

## SPP scenarios

`prepareDepositAccrual` sets pre-trade amounts and periods, invokes
`calculateDatedAgreement` for the product calendar and agreement overlap, then
calculates the overdraft period with fixed ACT calendar. Finally it accumulates
ordinary, five-band agreement, overdraft-principal and overdraft-interest
products. At this historical stage the source used SPP `new`/`copy` to initialize
working participants and independent snapshots. APP bound services and PHY bound
repositories and quantum journals. Remaining old Java model allocators were
implementations awaiting migration, not a permitted permanent adapter layer.

CSAPRD4:165–181 explicitly quantizes principal and the two interest categories
separately. Positive principal increases overdraft days; positive ordinary
overdraft interest increases interest-receivable days. Positive collection
interest alone still contributes an interest product but does not increase that
day count, following the active section 5300 branch. Tests preserve this condition
instead of inferring a day increment from a positive product.

Accumulation follows KSADEP0:1946, 1973–1979 and 1992: add the current ordinary,
overdraft-principal and interest components to their respective totals; derive
actual product as ordinary total minus overdraft-principal total; advance the
product date to the business date. Actual product does not subtract the
overdraft-interest product, preserving the source formula.

`prepareCashDepositWithAccrual` combines the complete cash business plan and
accrual plan. Pending authorization stops at the authorization threshold without
calculating or updating accruals. Invalid accrual input rejects the entire trade;
already prepared amount and fee plans are scratch data, not database writes.

## Settlement and reversal evidence

In the historical source path, `CashAccountRecord.accrual/bands` committed with
amounts, account qualification, postings, trade and success audit in the explicit
`transaction quantum` of `commitCashDepositRecords`. APP declared old-version
CAS for `storeCashAccount`. `CashDepositTrade.accrual/increments` retained start
and end dates, ordinary/overdraft day counts, each product increment and actual
five-band components. `original/settled` retained before/after totals, thresholds
and rule facts. Old Java `AccountFacts`/`Prepared` implementations still awaited
deletion and could not determine the source path's transaction boundary.

Later same-day trades have a zero accrual period. Replay of the same request
returns the original trade without adding products again. A new request queries
the current product date and account amounts and calculates from the pre-trade
snapshot. These facts prepare evidence for cross-date reversal; they do not
establish that reversal was implemented. Restoring an old date must not overwrite
later transactions. Replay here describes the historical coordinated path, not
a guarantee of optional request coordination in the basic distribution.

Early amount/storage subsets without accrual facts retain compatible calls but
are not the complete banking path. At this stage all CLI demonstration accounts
had accrual facts and executed the generated scenarios that include them.

## Legacy differences and unresolved matters

The implementation follows the confirmed business-semantics-first requirement:
sum exactly, then quantize by explicit units. It does not inherit implicit
truncation from CSAPRD4's two-decimal parameters/intermediate workspaces. The
original program was not run; three-decimal interest inputs are not evidence of
digit-for-digit equivalence.

The selected business model adds each calculated agreement-band component to
its band. Original KSADEP0:1514 calls agreement persistence only when
WK-O-NGO-PRDT(1)>0, potentially omitting persistence when full-amount mode hits a
higher band. No banking explanation for this condition was obtained; it remains
a registered legacy difference. Full-amount persistence must not be claimed
equivalent to the original system. The first CLI configuration has no agreement;
agreement-persistence tests use mode 2 with components in every band.

`normalEnabled=false` preserves ordinary totals and stops new ordinary/agreement
components: an explicit demo contract. Combinations of the original two interest
flags, disabled interest types and initial working values still require complete
bank configuration. Agreement cursors, negative-threshold disabling flags,
backdated value dates and 24-hour paths were not migrated at this stage. Data
came from declared demo configuration and real demo database queries, not legacy
bank data interfaces.

## Historical executable evidence

Six `DepositAccrualTest` tests used real generated services and H2 to verify date
clipping/five-band accumulation, two calendars, explicit units 3 and .01,
collection-only day conditions, periods and original facts, same-day/repeated
requests, configuration rejection, and joint rollback when ledger writing failed
after account and accrual updates. `DepositDemoReportTest` additionally checked
that accruals survived file close/reopen. The cross-date demo calculated one
actual day from old balance 2200, not post-deposit balance 2300.

Source migration added four APP entries in 99z/99za covering those six
responsibilities. The actual case used old base 650, principal 100, interest 30,
collection interest 40 and net balance 480. A deposit of 50 produced base/balance
530 and ordinary product 1712. Funding comprised cash 50 plus an old-deposit
reduction of 120; posting debits and credits each totaled 170. Original period
facts retained 20261001–20261007, six days, ordinary increment 1700 and principal
product 600. Retry did not repeat the update; the second same-day trade had
ordinary increment 0. A technical fault was declared after account/accrual
updates but before posting writes. Source audit recorded RolledBack only for
measured `transactionOutcome=ROLLED_BACK`.

The historical clean verification reported all 951 JUnit tests and all 34 source
entries passing, including the added entries and audit-policy entry. No classes
remained for the removed accrual test or ten previously migrated tests. See
[source-only refactoring](source-only-refactoring.md) for final evidence. At this
stage the overall source-only refactoring remained incomplete with 14 main Java
and 10 test Java files. These are claims from revision `417a41f`, not results
rerun for this English translation.
