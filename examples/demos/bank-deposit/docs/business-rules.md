# Bank deposit business rules

## Allocation is not simple balance addition

The [deposit kernel](../src/main/spp/cboddeposit/calculation/cboddeposit-00-deposit-kernel.spp)
uses existing interest base and frozen funds when computing available repayment.
Normal debt pays interest before principal; bad-debt/written-off states choose
principal first. Collection interest is a separately recorded component.

A small teaching input illustrates why the branch matters: with zero initial
interest base/frozen amount, a deposit of 100, interest debt of 10, collection
interest of 0 and principal debt of 100, normal allocation pays 10 interest and
90 principal; principal-first allocation pays 100 principal and no interest.
This illustrates the kernel only, not a complete admissible cash request.

Check the exact source for exposure, authorized amounts and remaining-balance
formulas. Do not infer every balance from a single total.

## Admission and calculation answer different questions

[Admission rules](../src/main/spp/cboddeposit/admission/) decide whether the operation
may proceed, including account, operator and authorization facts.
[Fees](../src/main/spp/cboddeposit/fee/) decide applicability and amounts.
[Accrual](../src/main/spp/cboddeposit/accrual/) supplies date/product calculations.

A valid mathematical plan does not prove permission to execute it. Conversely,
a permitted operation still needs a valid posting and settlement plan.

## Preserve posting evidence

[Posting rules](../src/main/spp/cboddeposit/posting/) produce explainable movement
components. The accepted deposit retains evidence for later inquiry and reversal.
Compare component totals, recipient identities and actual settled balances,
rather than only comparing a receipt's final status.

## Reverse an agreement, not all later account activity

[Reversal](../src/main/spp/cboddeposit/reversal/) loads the original deposit,
checks account/recipient identity and eligibility, checks current capacity,
builds inverse postings and records a distinct CashReversalAgreement.

Later deposits or unrelated activity must not disappear. Do not restore an old
whole-account snapshot. Business reversal after acceptance is different from
rollback of an uncommitted operation; technical compensation/recovery is also
not a substitute for a customer's reversal agreement.

## Treat uncertain recovery honestly

A failed call does not prove no movement. The source uses recovery facts to
distinguish confirmed rollback/reversal from unresolved outcomes. Test stored
failure evidence as well as account and posting values.

These rules demonstrate selected financial modeling capabilities. They are not
an assertion of complete regulatory, accounting or production-bank coverage.

## Signed overdraft claims

A negative overdraft principal, overdraft interest or collection interest is an
amount the customer owes. The account balance adds these signed positions to
the interest base. Overdraft exposure is the positive magnitude of outstanding
principal and is derived on inquiry, repayment and reversal.

The daily accrual calculation accumulates amount-days. To book a monetary charge,
`bookOverdraftClaims` takes assessed interest, collection interest and principal
as non-negative amounts. It decreases each customer claim and increases the
corresponding income balance for interest or the settlement cash position for a
principal advance. For example, charging 10 interest posts −10 on the customer
claim and +10 on interest income. Repaying 4 transfers 4 from settlement cash to
the claim, leaving −6 owed and 10 income. Reversing that repayment restores the
−10 claim and returns 4 to cash. All three movements conserve the cash ledger.
