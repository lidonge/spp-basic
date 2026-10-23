# From a deposit requirement to S++

## A. Describe the scenario in one sentence

> Accept a permitted cash deposit, allocate it according to the account's debt rules, and record a consistent settlement and receipt.

This is the deposit scenario. Reversing an accepted deposit is a second business
agreement, not an exception branch that overwrites the original account.

## B. Ask AI to expand the business process

A useful analysis prompt is:

> Expand this cash-deposit requirement into participants, normal and rejection
> paths, required facts, calculations, persisted evidence and acceptance cases.
> Identify unknown policy decisions. Do not choose a database or write code yet.

Review the result against [depositCash](../src/main/spp/cboddeposit/deposit/cboddeposit-scene-depositCash.spp):

| Phase | Business work | Review question |
| --- | --- | --- |
| Input | Validate amount and detail policy | Is the request admissible? |
| Identification | Load account, settlement recipients, eligibility and accrual | Do the facts refer to the same account? |
| Admission | Evaluate status, controls, operator, branch and authorization | May this customer perform this operation here? |
| Planning | Calculate fees, debt allocation, postings and accrual | Are values and recipients justified? |
| Settlement | Apply bounded changes and persist agreements/evidence | Do all required facts reflect the same outcome? |
| Failure | Record disposition based on recovery evidence | Is rollback confirmed or is recovery still uncertain? |

Do not invent approval thresholds, rates or calendar conventions. Ask the business
owner to resolve them; the demo models selected policies, not every bank's rules.

## C. Choose participant roles and contracts

- DepositRequest carries proposed amount, currency and value date.
- DepositAccount supplies account facts used for repayment and settlement.
- CashSettlementAccount and the income/recovery roles identify movement recipients.
- AccountEligibility and authorization participants supply permission facts.
- DepositPlan is a calculated proposal, not a completed agreement.
- CashDepositAgreement retains accepted settlement facts.
- CashReversalAgreement refers to the original agreement for a separate reversal.
- CashFailureRecord describes business handling evidence, not a generic exception.

Read [calculation roles](../src/main/spp/cboddeposit/calculation/cboddeposit-00-deposit-kernel.spp)
and [agreements](../src/main/spp/cboddeposit/deposit/cboddeposit-00-agreements.spp).
There is no need to turn every temporary value or collection into a Contract.

## D. Find entities and draw ownership boundaries

The [account model](../src/main/spp/cboddeposit/account/cboddeposit-00-model.spp)
declares BankAccount as an Entity. Deposit and settlement roles express how
accounts participate in this business process. Role mappings and tables are
visible in the source and APP, rather than inferred from similarly named fields.

The supplied demo uses the cboddeposit domain. Its account, admission, calculation,
fee, accrual, posting, deposit and reversal folders separate responsibilities;
they are not claims of separate deployed domains.

A future domain split should follow ownership of account facts, product policy
and settlement agreements. First specify the owning services and consistency
requirements; moving folders or adding HTTP calls is not a domain design.
Compare the [Petstore multidomain example](../../petstore/docs/domain-and-layers.md).

## E. Refine business logic, checks and calculations

Read the kernel's checkKernelInput, calculateDepositPlan and checkPlan as distinct
responsibilities. The calculation chooses repayment priority from loan state,
computes proposed values and leaves validation explicit in the scenario.
Posting and accrual calculations have their own checks before settlement.

Ask AI to enumerate boundary examples: zero versus positive input, insufficient
available value, interest/principal boundaries, frozen amounts, fees, invalid
dates and changed eligibility. Confirm expected outcomes independently of the
generated implementation.

The transaction combines bounded settlement with metadata, accrual, agreement
and posting persistence. Failure disposition uses ResultInfo recovery evidence;
it must not report no movement merely because execution failed.

## Add reversal as a new requirement

> Reverse an eligible original cash deposit using its recorded settlement components without erasing unrelated later activity.

Repeat A–E for [reverseCash](../src/main/spp/cboddeposit/reversal/cboddeposit-scene-reverseCash.spp):
identify original facts, check current eligibility and capacity, calculate inverse
postings, settle, and persist a new agreement. Reversal is not restoration of an
old whole-account snapshot.

Next: [language tour](language-tour.md) and [business rules](business-rules.md).
