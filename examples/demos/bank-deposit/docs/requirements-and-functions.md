> Historical requirements: this records the pre-refactoring scope and rules.
> See [current refactoring](latest-petstore-refactoring.md); old sources are in
> `legacy-source/`.

# Requirements and functions

## Implementation language and transaction declarations

The historical source-only rule required this demo to use SPP, APP and PHY with
all Java generated, no handwritten Java. Business rules, orchestration and full
transaction boundaries/modes had to be explicit in SPP; APP bound implementations/
deployment and PHY supplied physical resources/guarantees. Syntax gaps required
registration and completion, not handwritten bypasses. See the
[source-only audit](source-language-only.md). This superseded earlier Java entry/
transaction-wrapper choices.

Later applicable instructions allow Java tests solely for input preparation,
composition of generated services, assertions and technical fault injection.
Handwritten production business Java remains prohibited. Current transaction
realization belongs to APP/PHY under the applicable transaction/storage rules;
historical mode wording does not override that separation.

## Objective and business scope

Create an independently buildable, repeatable SPP demo based on GSA01102 general
deposit and KSADEP0 deposit core. Verify expression of real business rules,
generated execution, data updates and recovery. Compilable syntax is not an
executable transaction.

The confirmed first batch includes ordinary cash deposits, deposits with ordinary/
bad-debt overdraft debt, and reversals of their original trades. Transfers,
collections, clearing and batch paths are registered extensions. Core zero-amount
debt repayment and front-end positive-amount admission are modeled separately,
not conflated into one transaction.

## Four priorities and acceptance

| ID | Function | Required business evidence |
| --- | --- | --- |
| P1 | Repayment allocation, balance calculation, physical numeric binding | Ordinary interest-before-principal versus bad-debt principal-before-interest; frozen/authorized distinctions; equal-principal boundary; separate provenance for three-decimal interest, currency and interest-unit boundaries |
| P2 | Shared banking-rule library | Account admission, minimum opening amount and balance ceiling; rejection/warning/pending authorization; revalidation after authorization; business dates; accrual products/bands; fee applicability |
| P3 | End-to-end transaction and business accounting | Query → check → calculate → validate plan → update; consistent account/funding/posting/log commit; no partial business outcome after failure; no duplicate posting under coordinated retry/concurrency |
| P4 | Original-trade-driven reversal | Query original trade, check eligibility, restore debt/balance/accrual/postings from original repayment components; duplicate reversal, cross-date behavior and post-trade state changes |

P2 does not imply a complete bank rule library. Trace the subset required by
selected paths and configuration. Unavailable product, rate and accounting
configuration uses explicitly labeled demo fixtures, not claimed actual bank
parameters. Warnings and authorization are not generic exceptions: business
calculates authorization requirements, APP obtains authorization facts, and
execution revalidates them.

Optional coordination requirements are accepted only with the relevant provider.
The basic example explicitly warns when replay/duplicate suppression is absent;
business checks and optimistic database protection remain mandatory.

## Business contracts and layering

Principal participants are request, account snapshot, debt, product policy,
currency policy, business calendar, authorization facts, processing plan,
original trade and postings. Preserve distinctions among interest base, account
balance, available balance and total overdraft. Separate plans from input
snapshots for rejection, authorization revalidation and version-conflict tests.

SPP check/calculate services own rules and formulas. Shared services expose role
contracts; APP chooses local/remote deployment. SPP declares complete business
transaction boundaries; applicable APP/PHY declarations and generation implement
resource modes, database access, conditional versioned updates, actual guarantees
and call codecs. APP does not rewrite repayment order, admission or amount
classification.

Quantum transactions require deterministic technical rollback within supported
resource contracts. Business recovery of committed Saga stages cannot claim the
same rollback guarantee. Reversal is a new trade, preferably an inverse driven
by original settled facts. Compensation requires explicit business logic, not
automatic technical inference. Verify technical rollback, business reversal and
compensation separately; see the [semantics discussion](rollback-reversal-compensation.md).

Business declarations such as short/double only hint at defaults; PHY chooses
actual representation. The demo initially uses built-in BigDecimal. Business
quantization/rounding requires explicit provenance. Do not guess rounding rules
from unconfirmed legacy PIC truncation.

## Evidence and boundaries

Each case records input, output, error/authorization outcome, source program/
section, expected-value provenance and execution level. Distinguish source
derivation, bank-confirmed golden values and actual original-COBOL execution.
A same-formula Java comparison checks generation consistency, not COBOL equivalence.

Tests cover normal/rejected paths, amount/date boundaries, fault injection,
duplicates and concurrency. Before P3 completion, claim only calculation/rule
subsets. After P4, still identify excluded channels/products. Production deployment,
real customer data and integration with external banking systems are out of scope.
