> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Financial components and business policy for deposit reversal

> Cash policy was confirmed to follow XBOD; see the [source contract](cash-reversal-source.md).
> Earlier generic financial components and explicit refund inputs below are not
> formal cash-entry defaults. Pending-policy statements record earlier stages,
> not renewed requests to choose policy.

## Amount plans under the confirmed classification

Reversal after successful settlement is a new operation against current accounts
and original settled components. describeOriginalDepositMovement derives actual
interest-base delta, principal/two interest repayment components and exposure
delta from original request, account snapshot and settled plan. It checks original
amount equals interest-base delta plus the three repayments, without rerunning
the original allocation formula.

calculateDepositReversalAmounts applies opposite actual components to current
amounts: subtract original interest-base delta, add repaid debt and subtract
original exposure delta. Current frozen/authorized amounts and loan status are
not restored to old values. Available balance derives from current freezes/
authorizations; net balance derives from reversed amounts and must equal current
net balance minus original deposit.

This implements the confirmed non-overwriting quantum semantics. Initial balance
200, deposit 10 and later spending 100 leave 110; reversing the deposit yields
100, not 200. Later deposits/spending/status changes survive. The component does
not automatically classify every numeric field as quantum or supply default
account-opening/closure or lifecycle inverses.

## Source eligibility and differences

KSADEP0:637 requires current available balance to cover original amount, or allowed
overdraft with sufficient limit for the shortfall; otherwise ES020.
Section 4610 rejects negative new interest base with EN249. After restoring debt,
4640 rejects simultaneous positive interest base/principal when interest base
exceeds frozen amount with EN177. An algebraic inverse does not imply permission.

Shared checkDepositReversalCapacity/checkDepositReversalAccount use role-based
current overdraft policy independently of deployment. prepareDepositReversalAmounts
checks original components/capacity, calculates amounts, then checks net-balance
relationships and result eligibility. These are plans, not database updates.

Original 4640 adds original KBN principal repaid to SA-OD-AMT. The generic quantum
inverse instead reverses actual original exposure delta. They differ when
max(0,...) clipped exposure: original exposure 5, principal repayment 10 and
settled exposure 0 yield generic restoration 5, versus original-source addition
10. The component targets reversal of actual components without overwriting
later operations, explicitly recording this legacy difference rather than
claiming bank equivalence. Recomputing exposure as a derived business amount
requires separate policy; do not silently overwrite it with an old value.

## Cross-date, fees and non-quantum state

KSADEP0 section 4630 subtracts only KBN-ADD-PRDT from accumulated deposit product.
For ordinary same-day-value deposits, this field originates in backdated-value
adjustment; the entire original period product is not the deposit's reversible
effect. Section 4640 also does not recompute all later ordinary/agreement/overdraft
products. Monetary reversal and retroactively reversing all historical interest
effects are different business policies.

Earlier proposals asked whether cross-date reversal should preserve accumulated
products or retroactively undo post-trade interest effects, and whether refunds
should follow an explicit decision or return the full original charge. Retroactive
handling under unit quantization/agreement bands cannot simply use amount × days;
it requires period-by-period difference calculation. Fees are independent
components, not implied refundable by an invertible deposit core. Later cash
decisions follow XBOD as stated above.

Dates/details are not restored by swapping before/after. Original records remain,
inverse details are added, and daily totals follow explicit original/current
business-date rules. Non-quantum updates still require dependency checks.
Matching versions do not alone permit reversing account opening; invertible
amounts do not imply an entirely reversible trade.

## Boundary of the original component

The independent financial plan can verify preservation of later operations,
normal/bad-debt component restoration, exact three-decimal amounts, capacity/result
rejection and forged-component rejection. Original-trade lookup was added through
DepositReversalPreparation below. At that checkpoint independent reversal
identity, mutually exclusive repeated reversal, joint posting/audit commit,
cross-date/fee policy and compensation were still pending. Amount-plan tests were
not full P4 acceptance.

## Preparing an inverse from actual settlement

APP DepositReversalPreparation.prepare(originalTransactionId, capacity) reads
successful deposit_trade and current snapshot by its account identity.
Absent IDs or rejected-but-unsettled requests return ORIGINAL_DEPOSIT_TRADE_NOT_FOUND.
Callers do not upload original repayments, and failed audit does not establish
settlement. Account-identity/payload mismatch is rejected.

Generated prepareRecordedDepositReversal first uses checkOriginalDepositSettlement
to compare original before/after amounts, stored plan, principal/two interest
repayments and original net/available-balance relationships. It then extracts
actual components and invokes the inverse plan. This is demo trade-contract
consistency, not a digital signature, KBN-format adapter or tamper-proof system,
and does not rerun original allocation.

The result includes original Receipt, current Snapshot/version, this attempt's
capacity-policy snapshot, original components and inverse amounts. Capacity roles
are copied so later caller changes do not alter returned evidence. Trade/current
snapshot reads are not a locked commit transaction; these are candidate plans.
Formal execution must recheck current version and full eligibility.

Preparation writes no account, postings, request identity or audit; it neither
refunds, changes accruals nor creates settlement. Repeated preparation reads
current facts, not idempotent reversal. Cross-date originals may yield monetary
candidates without permitting complete cross-date reversal. Original fee/date
facts stay in Receipt for the applicable policy.

DepositReversalPreparationTest used generated scenarios and actual JDBC-persisted
cash, normal/bad-debt and three-decimal trades. It checked original lookup, later
trade preservation, debt restoration, capacity rejection, unsettled-request
rejection, inconsistent stored-trade rejection and unchanged business/audit
records after preparation. This extends evidence from settlement to plan, not
complete new inverse settlement.

## Actual postings to inverse posting plan

DepositReversalPreparation.preparePostings added explicit DepositReversalFeeDecision.
Callers must provide refundAmount between zero and actual original fee.
Zero, partial and full amounts are expressible decisions, not framework-selected
refund policy. A shared business service or explicit request may supply the role;
APP passes it without a technical refund default. Test amounts were explicit
fixtures. Bank applicability/default policy remained pending in that earlier stage.

APP reads actual deposit_ledger ordered by slot. prepareRecordedDepositReversalPostings
checks actual accounts/directions/amounts against original trade payload, then
eight-slot completeness, six core amounts, two original fee slots and debit/credit
balance. Balanced totals alone cannot excuse wrong accounts or missing zero slots.
This is a DEMO mapping, not original ART or arbitrary bank posting format.

Core slots keep original accounts/amounts and reverse directions. Fee slots keep
accounts and reverse explicit refund amounts. Zero slots remain.
checkDepositFeeRefund rejects negative/excess refunds; after
calculateDepositReversalPostings, balance is checked again. Business source owns
decisions/math; JDBC reads actual data and the historical Java layer allocates
independent output roles.

PostingResult contains financial candidate, copied refund decision, actual
original postings, new inverse postings and totals. Outputs do not mutate
original input/ledger or execute a refund. Full reversal still required cross-date
accrual/daily-total policy, complete eligibility, independent identity and joint
commit. Old physical Prepared records missing fee facts do not imply zero fee.

Four added DepositReversalPreparationTest cases checked explicit zero/partial/full
plans and core reversed directions, normal/bad-debt/three-decimal amounts, missing/
negative/excess refund rejection and altered/missing actual postings. Preparation
still wrote no account/trade/posting/audit. These tests did not establish that the
user selected partial refunds or cross-date eligibility.

## Incremental commit capability

Later, prepareDepositReversalAccountUpdate produced a current-account update and
JdbcDepositReversalStore persisted new inverse, original link, inverse postings
and success audit. Physical tests executed this financial component; read-only
preparation APIs still did not commit. Full cash entry and product/accrual/daily
facts/policies were not connected at that stage. See [reversal submission](reversal-submission.md);
component settlement is not full P4.

prepareCoreQualified additionally reads current closed/cancelled/full-freeze/
document facts and applies separate reversal rules with notices, not normal
deposit-range rules. It still produces only a candidate. See
[bank qualification](bank-qualification.md).

## Source-language query and candidate entries

cboddeposit-99ca-reversal-preparation.spp added DepositReversalPreparationRequest
and DepositReversalPreparationFacts. Requests supply original identity, capacity
and, by entry, operator/channel or explicit refund decision. APP-bound repository
services query original/current account. financial retains original trade,
current version, original components and candidate. SPP copy makes independent
operator/channel, capacity and refund snapshots.

- prepareFinancialDepositReversal only queries facts and prepares a monetary inverse.
- prepareCoreQualifiedDepositReversal first validates current core eligibility
  with current account/operator, retaining document notices; it does not import
  normal deposit-range or front-end cash controls.
- prepareFinancialDepositReversalPostings requires feeDecision, queries actual
  original postings, compares stored settlement evidence and builds opposite
  directions. Missing, negative or excessive decisions cannot produce a refund plan.

These scenarios have no updates or transaction: they create no reversal, change
no account/accrual/daily/audit facts and do not lock future eligibility.
Actual new trades must declare full commit boundaries and protect current
versions again. executeCashReversal separately retains cash eligibility, XBOD
date/fee policy and quantum commit. A successful read-only candidate is neither
permission nor completed cash reversal.

New core-eligibility examples passed historical clean verify: all 947 JUnit tests
and 36 source entries, with four old eligibility tests/classes removed.
Complete independent financial/posting-preparation responsibilities still awaited
source acceptance. Handwritten DepositReversalPreparation was a retirement
obligation, not a dependency of the new source path.

All results and staged pending statements here come from revision `417a41f`,
not tests rerun during translation.
