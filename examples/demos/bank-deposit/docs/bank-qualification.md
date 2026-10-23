> Historical document: this describes implementation and acceptance before the
> refactoring, not current source or test results. See the [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Cross-branch deposit range and lost-document notices

## Business services and roles

The DEP path in CSAQRY1 invokes lost-document notices in 9704 and deposit-range
checks in 9709. The demo implements these as shared services
`assessDepositDocumentNotices` and `checkDepositBranchRange`, combined by
`assessDepositBankQualification`. All conditions and exceptions are in SPP;
APP supplies already decoded account and operation facts.

`DepositBankProfile` retains ordinary-savings/restricted-subtype classification,
opening institution, province, subbranch relationship, interbranch withdrawal
area, deposit range, card association and card/passbook loss facts.
`DepositOperatorFacts` retains current institution and relationships, transaction
prefix, operation code, teller, operating mode, agency agreement and settlement
channel. `DepositNotices` returns card and passbook notices separately so that
combining them with cash-total warnings does not lose information.

The subbranch relationship is the result of source CCMRELO type 0300 queries,
not an institution-number prefix. Four-digit area and two-digit provincial area
are also supplied separately. `DepositBankModels` only allocates explicit demo
facts; it does not implement bank queries or decoding: City range, opening
institution 001001001, operating institution 001001002, common area 0001 and
subbranch relationship SB001. The full cash entry requires operation code 1102
and prefix SA01102, checking institution/area consistency with the fee context.
Entry program name GSA01102 and decoded prefix are different fields.

## Sources and rules

| Source (physical line numbers) | Migrated behavior |
| --- | --- |
| CSAQRY1:291 onward | Specific permissions for FL06002, AG02891, SA01120, FL06615 and FL06618 |
| CSAQRY1:1738 onward, 9704 | WN200 for card series 49927, card association and successful query, not cancelled, oral/written loss and applicable business/operation conditions; independent WN201 passbook-loss notice |
| CSAQRY1:1874 onward, 9709 | Ordinary savings SameBranch/Subbranch/City/Province/All ranges and institution, business-code, teller and channel exceptions; EN134 for institution mismatch and EN470 for subbranch/area mismatch |
| Non-ordinary-savings branch of CSAQRY1:9709 | Restricted SameBranch/WithdrawOnly ranges and corresponding agency-transaction exceptions; not all ordinary-savings exemptions apply |
| CSAQRY1:2054 onward | Cross-institution check for DEP_TYP=03 sits outside the outer range condition, separately checking agency and designated-transaction permissions |

WN200/WN201 are not unconditional terminating errors. Notices apply to
non-agency agreements, numeric tellers and Online/Mode8 operation. CR0 exempts
only card notices, not passbook notices. `mergeDepositNotices` marks executable
admission as Warning, preserving original F0554 and both document notices.
Pending authorization remains pending; notices do not make it executable.

Outer deposit-range applicability involves card association, FL0/RE0/AG0, RMB
accounting center and province, designated transactions and tellers. Exemptions
for UH0–9, and O56–99 or ZD0–9 under specific operation codes, cannot be broadened
to every teller or operation. Batch, corporate transfer and combined subaccounts
such as HVP/BEP/NXY have their own scopes. Restricted-subtype checks cannot
always be skipped because of card association or batch mode.

Missing subbranch/area relationships return demo contract errors
DEPOSIT_SUBBRANCH_RELATION_NOT_FOUND or DEPOSIT_AREA_NOT_FOUND, rather than
allowing two unknown values to compare equal. Unknown ordinary-savings range
returns INVALID_DEPOSIT_RANGE. These are not claimed to be original bank codes.

## Execution and settlement evidence

`AccountFacts.bankProfile` is read with the account snapshot and protected by
joint version/fact conditions. When that role is present, `NormalCashDeposit`
invokes `prepareQualifiedCashDeposit` or
`prepareQualifiedCashDepositWithAccrual`: first operation-fact binding and
banking qualification, then existing admission, fees, amounts, accruals and
posting scenarios. Rejection does not write a business trade.

A successful trade's `Prepared.bankEligibility` retains account facts, the
attempt's operation facts and notices. Replay of the same request returns
original trade evidence and notices without reexecuting settled business, even
if current agency or other operation facts differ. Authorization continuation
for an unsettled request rechecks current facts. Operation facts do not become
part of the original amount-request identity; like authorization evidence, they
are inputs to that attempt's checks. These are historical coordinated-path
semantics, not a promise of replay in the basic distribution.

Successful notices persist jointly with the trade. Rejected/pending audit still
uses existing attempt payloads; not every intermediate notice is claimed to
persist. The new scenario placed banking qualification before original admission
checks. Error precedence when multiple conditions are invalid was not checked
against all original COBOL paths.

## Historical verification and limits

Eight `DepositBankQualificationTest` tests covered relationship comparison,
outer permissions, teller boundaries, exemption scope for different ranges,
both notices, missing relationships, actual joint cash/accrual/posting commit
and original-notice replay, rejection and context mismatch. Tests exercised real
generated scenarios and H2. The successful case retained F0554, WN200 and WN201
together.

This supports migration and integration of selected source conditions, not
runtime equivalence to original COBOL. Real bank account/card/institution
queries, production permissions, complete multicurrency rules and golden run
results remained unavailable or unimplemented. Static fixtures cannot replace
that evidence. Full reversal and cross-date/refund policies are not determined
automatically by deposit-range checks.

## Independent core eligibility for deposit reversal

CSAQRY1:235–247 selects normal checks or section 5000 reversal checks using
SYS-TX-TYP. The deposit-reversal path from 2312 invokes lost-document notices,
9751 account-opening cancellation and 9753 full-freeze checks, not normal
deposit-range check 9709. Before querying, KSADEP0:603 still rejects Closed with
EO082. Cancelled account opening yields EN113; Frozen yields EN111. Transaction
prefix SA01116 is a designated freeze-check exception, but does not exempt
cancelled account opening.

`checkCoreDepositReversalEligibility` and `assessCoreDepositReversalEligibility`
migrate that subset and reuse both lost-document notices. They do not incorporate
normal deposit ranges or front-end institution controls into core reversal.
The formal entry must apply front-end controls according to its actual call
path. Core permission does not imply permission for the entire transaction.
Message priority for multiple invalid states was not tested against original COBOL.

`DepositReversalPreparation.prepareCoreQualified` reads the original trade and
current `AccountFacts`, checks core eligibility, then prepares reverse amounts.
It returns a snapshot of this attempt's operation/channel facts and separate
notices. It uses current freeze/cancellation facts, not restored original trade
state. Missing banking facts on the current role cause explicit rejection,
not implicit permission. This API still does not commit, refund or modify
accruals; it is not a complete reversal entry.

Four `DepositReversalEligibilityTest` tests verified the three errors and the
designated freeze exception, reversal notices versus normal ranges, eligibility
changes after an actual original trade, and separation of front-end controls
from core eligibility. Direct updates to versioned demo facts simulated later
freeze/cancellation. This proves rule behavior after querying current facts,
not migration of the bank's freeze/cancellation business implementations.

This English version preserves historical evidence from revision `417a41f`;
the cited test runs were not repeated as part of translation.
