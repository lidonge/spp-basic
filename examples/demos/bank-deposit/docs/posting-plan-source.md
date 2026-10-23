> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Deposit posting-plan sources and implementation preparation

2026-10-07 source tracing and P3 design record. Business amount classification and
the demonstration posting plan were implemented/verified at this stage;
complete transaction persistence was still in progress.

## Confirmed amount classification

Normal transactions at KSADEP0:3714 invoke sections 6100 amounts, 6200 conditions
and 6300 rules in order. Section 6100 passes GCCBAAMT the transaction amount,
interest-bearing amount increase, overdraft principal repayment, overdraft-interest
repayment, collection-overdraft-interest repayment and interest-bearing amount
decrease. The correction flag reverses these component signs.

The migrated balance formulas support this business relationship:

```text
cash deposit amount + interest-bearing amount decrease
    = interest-bearing amount increase + principal repayment
      + overdraft-interest repayment + collection-overdraft-interest repayment
```

Do not simply add the deposit amount to the deposit balance: existing
interest-bearing funds may also repay debt, reducing the deposit's interest base.
The zero-amount core repayment path may still yield nonzero repayment components.
Front-end acceptance of zero cash amounts is a separate business rule.

Section 6200 also passes product, currency ledger, transaction mode, loan status,
cash/remittance category, overdraft limit, correction flag and fund-flow
conditions. The amount identity cannot replace accounting rules. Fees have their
own entry and postings; do not invent a rate and deduct it from deposit amount.

## Actual source of accounting rules

GCCBAAR:150 onward calls GSYSSART to obtain ART using business system name,
transaction/core-program identity and condition codes. It then invokes GSYSSSMT
and, where required, GSYSSSMP to select accounting rules and institutions.
GSYSSART searches TSYSSART through CTQ-ART-ADDR. KSADEP0 call statements do not
reveal complete account mappings. Query code existed in the source directory,
but the target environment's loaded SA0/KSADEP0 condition-rule contents were
unavailable.

## Implementation principles

P3 first expresses confirmed amount classification/identities in SPP, then
queries accounting configuration to form concrete postings. APP/PHY provide
logging, idempotency, revision protection and joint commit. Demo configuration
must be labeled as such, with separate evidence levels for source amount
classification and demo account mapping. It cannot be claimed equivalent to
original bank configuration.

Acceptance checks account/debt changes, balanced postings, independent fees,
retained original trade components, failure recovery, duplicates and concurrency.
Missing original accounting configuration or COBOL golden values does not prevent
verification of SPP expressiveness and explicit demo behavior, but limits
equivalence claims.

## Executed demonstration plan

`prepareDepositPostings` maps six amount components into eight fixed slots:
cash debit, deposit-liability-decrease debit, deposit-liability-increase credit,
overdraft-principal credit, overdraft-interest credit, collection-overdraft-interest
credit, and fee cash debit/fee-income credit. The last two are independent of
deposit amount allocation. Zero-amount slots remain to expose components and
original facts.

SPP first checks component/total consistency and conserved fund allocation, then
slots/accounts/directions, and finally calculates/checks balanced debits and
credits. Account names use the DEMO prefix: an explicit demonstration contract,
not configuration obtained from bank ART. Six DepositPostingTest tests executed
the generated scenarios. The latest regression recorded then had all 57 demo
tests passing; see [implementation path](implementation-path.md).

## Reversal posting candidates

At that stage `prepareRecordedDepositReversalPostings` cross-checked the actual
demo original ledger and original trade payload, reversed the six core debit/
credit directions, and accepted explicit refund amounts for the two fee slots.
It neither recalculated original fees, guessed production ART nor updated the
ledger. SPP expressed eligibility, components and directions; APP/PHY only queried
and allocated results.

The historical design still awaited cross-date/refund-policy decisions before
connecting a complete new reversal joint commit; see
[financial reversal plan](reversal-financial-plan.md). Later decisions selected
XBOD cash rules, recorded in [cash reversal source contract](cash-reversal-source.md).
The earlier pending decision is not a renewed request to choose policy.

This English version preserves evidence from revision `417a41f`, not newly
executed tests or a promise that optional replay is supplied by the basic SDK.
