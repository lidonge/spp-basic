# Current source navigation

Production source is under `src/main/spp/cboddeposit/`, with one file per independent scenario. Filenames in the historical sections below are found only in `docs/legacy-source/`.

| Capability | Current location |
| --- | --- |
| Account models/opening | account/ |
| Repayment allocation | calculation/cboddeposit-00-deposit-kernel.spp |
| Eligibility/authorization | admission/ |
| Fees/personalized rates | fee/ |
| Calendars/agreement accrual | accrual/ |
| Balanced debit/credit entries | posting/ |
| Cash deposit | deposit/cboddeposit-scene-depositCash.spp |
| Cash reversal | reversal/cboddeposit-scene-reverseCash.spp |
| Read-only cash-reversal preview | reversal/cboddeposit-scene-previewCashReversal.spp |
| Bank repayment internal accounts | account/cboddeposit-20-internal-accounts.spp; account/cboddeposit-scene-openBankRepaymentAccounts.spp |
| Environment/storage | cboddeposit.app; cboddeposit.phy |
| Technical interception of all services | technical-services.app; technical-services.phy |
| Chinese-language web workbench | src/main/resources/web/; docs/web-workbench.md |
| Current automated acceptance | src/test/java; src/test/resources |

See [refactoring design](latest-petstore-refactoring.md) and [known issues](known-issues-latest.md) for scope, decisions, and verification limits.

---

# Historical source and migration evidence

The remaining navigation predates refactoring. It preserves COBOL provenance and rule comparisons, not current implementation or fresh test results. No original bank source is included here.

The reference root was `<BANK_COBOL_ROOT>`, read as GB18030. Numbers below are physical lines. Historical change-tag comments must be identified using COBOL's comment column; header call lists do not establish effective execution paths.

| Function | Reference | Evidence/implementation at that stage |
| --- | --- | --- |
| Front-end amount admission | GSA01102:448 | checkCashInput verified ordinary positive amounts; core zero-amount handling does not replace this contract |
| Core amount admission | KSADEP0:389 | Negative E1095; SPP checkKernelInput includes zero |
| Yen granularity | KSADEP0:344 | Expressions/PIC semantics did not prove the comment's meaning; business granularity pending |
| Temporary-account expiry | KSADEP0:552 | checkDepositEligibility verified EDF19 and temporary-account exception |
| Closure/deposit eligibility | KSADEP0:603,614; CSAQRY1 | EO082, front-end controls, core closed/product subset verified; selected cross-institution scope/loss warnings integrated, see bank-qualification.md |
| Initial deposit bounds | KSADEP0:660–686 | checkDepositEligibility verified original detail count/existing-balance conditions |
| Balance ceiling | KSADEP0:688 | checkDepositEligibility checked ordinary amount plus original balance |
| Warnings/authorization | KSADEP0:750–860 onward; GSYSEHRT | assessCashAdmission verified accumulated warnings, general/USD levels, selected exceptions; complete authorization execution separate |
| Repayment order | KSADEP0:1481,1540–1768; PSACONST:1498 | calculateDepositPlan covers ordinary/non-performing/written-off debt |
| Balance/exposure | KSADEP0:1925–2067; SAACNAMT | Plans, independent update snapshots, demo JDBC joint commit verified |
| Interest units/dates | CSAPRD2:309,332,363 | Explicit units/calendar/agreement clipping/tiering/joint persistence verified; agreement retrieval/original call-condition differences pending |
| Actual day-count function | PCMCONST:1133,1176,1182; CCMDAT1:292,8100/8200/G000 | GT-PRDH=O, ACT=1, deposit days=4; start included/end excluded; 360 variants use min(day,30) at both ends |
| Fees | GSA01102:1413; KSAFEE0:194,267 | Ordinary recipient-account plan/demo independent debits/credits verified; original posting config/other branches absent; fixture parameters |
| Accounting | KSADEP0:3714 onward | Amount classification/demo entries/joint commit verified; real ART configuration unavailable, see posting-plan-source.md |
| Reversal | KSADEP0:637,2642–2732 onward,4640 | Original actual financial components and ES020/EN249/EN177 subset verified; complete new settlement/cross-day/fees not yet integrated at this checkpoint; not rollback |

P1's 14 expectations came from earlier source-formula derivation; the full list was in DepositKernelTest. Actual generated models/scenarios ran, but original COBOL did not. No golden outputs or bit-exact equivalence were claimed.

Two later three-decimal interest cases used exact business mathematics, preserving .005/.004 rather than inheriting implicit truncation from two-decimal repayment work variables. These reflect the agreed business-semantics-first direction, not original COBOL output equivalence.

Calendar migration checked constants first: CSAPRD2 uses O, not similarly named H. H excludes both ends/subtracts a day and is not interchangeable. The then-Java APP decoder only validated Gregorian dates; SPP selected ACT/360365/360360 business counting. EE374 invalid date, EEC28 missing date, and E1175 start-after-end came from active constants/paths, not obsolete header descriptions.

## Difference register

| Difference | Treatment | Recorded status |
| --- | --- | --- |
| Three-decimal original interest versus two-decimal repayment variables | Business semantics first; rounding needs business justification and original truncation impact remains documented | Three-decimal mathematics/storage cases exist; original PIC runtime differences/additional rounding unconfirmed |
| Yen checks may depend on intermediate representation | Explicit currency policy, not guessed machine arithmetic | Unit/rule confirmation pending |
| Total overdraft branches differ at equal/slightly greater principal | Preserve confirmed source branches and test separately; discuss policy rationale separately | Two calculation cases |
| Business reversal versus automatic rollback | Eligibility/original components belong in business; inverse execution may be automated | P4 first inspected inverse metadata/journals |

## Agreement tier mapping

CSAPRD2 section 5000 clips by effective/cancellation date; 5400 reads five thresholds; 5500/5550 distributes from slot 5 to 1 using strict greater-than; 5555/5556 selects one tier with greater-than-or-equal and applies the full amount. PSACONST:1853/1855 modes are 1/2: mode 2 tiered, mode 1 whole-amount, otherwise selected by RMB/other currency. calculateAgreementProduct migrated allocation, calculateDatedAgreement verified clipping; complete agreement lookup remained pending.

Per-tier rounding and ordinary accrual subtraction are separate computations. Total tier accrual need not equal original accrual for every threshold/unit. With amount=250, unit=100, lowest thresholds 50/125, ordinary accrual 200 subtracts 200 while tiers total 100. This is source-algorithm-derived expectation, not bank-policy endorsement or original runtime measurement.

## Ordinary recipient-account cash fees

GSA01102:1427 teller calls KSAFEE0 with recipient account and empty payer. KSAFEE0:194 onward invokes CSAFEE0, whose ordinary deposit-account branch queries institution and four-digit cross-bank regional code; APP decodes original field slices. Same institution/same region different institution/cross-region produce scope 0/1/2. Same-region cross-institution uses SAC00001; cross-region uses SAP00001. CCMFRT2 supplies rate, discount, fixed fee, and bounds by customer identity.

CSAFEE0:349 onward computes amount × rate × discount, rounds variable fee, adds fixed fee, then applies maximum before minimum. SPP explicitly uses roundHalfUp(...,2) in this nonnegative fee domain, preserving fixed-fee order and zero-maximum behavior. APP queries parameters; demo customer/fee-code fixtures are not actual bank rates. KSAFEE0 rejects negative amounts with EP192 before lookup.

Only the selected ordinary recipient-account cash-deposit branch migrated at that checkpoint. Multicurrency KCMFEE0 delegation, max(payer fee,recipient fee), vouchers, fee journals, and actual posting were not migrated. Unsupported teller account kinds return a demo error, not silent zero fees. prepareDepositFee invokes classification, customer-rate lookup, and calculation but did not deduct fees itself.

## Agreement date scenario

CSAPRD2:223–267 uses max(previous accrual date,effective date) through min(current accrual date,cancellation date), defaulting absent cancellation to current date. Missing effective date or no strictly positive interval means no agreement portion. prepareAgreementWindow selects dates; calculateDatedAgreement reuses calendar/tiering services. APP decodes dates; SPP decides interval/applicability. Negative-threshold disable flags/full retrieval remained unmigrated; no complete CSAPRD2/KSADEP0 equivalence claim.

## Ordinary cash eligibility increment

GSA01102:707 onward maps institution controls/pay-only/locked to EN432 and amount-record freeze state to EG877. CSAQRY1:988 closed records yield EN113; 9724/9725 require a specific business code for the housing-related product, so ordinary cash deposit yields EI090. SPP declares separate shared front/core checks, composed in ordinary cash scenarios. A positive frozen amount alone is not the frozen-status rejection; it still participates in available-balance formulas.

Selected cross-institution scope/loss warnings were integrated; see bank-qualification.md. W-class loss messages remain separately visible while allowing settlement. Complete eligibility/multiple-error priority was not proven.

## Local JDBC execution

prepareDepositAccountUpdate creates an independent DepositAccount from DepositPlan's new interest-bearing amount/exposure and original repayment components. Debt balances subtract planned repayment; freezes, authorization, and loan status are retained, following traced KSADEP0 formulas. It does not implement reversal.

JdbcDepositStore schema, JSON settlement payloads, version conditions, and Prepared replay were demo APP/PHY implementation, not legacy database migration or bank-default protocol. Its initial six tests obtained values from generated prepareDepositPostings/prepareDepositAccountUpdate and exercised real H2 joint commits/faults/concurrency. Evidence supported local plan execution, not full admission, bank-configuration equivalence, or COBOL runtime equivalence.

## Ordinary cash execution increment

Nine NormalCashDepositTest cases exercised prepareNormalCashDeposit through JDBC joint commit, pending-authorization recheck, and original-request replay, extending plan-only evidence. The path contained the migrated eligibility subset, not every CSAQRY1 rule. lastTransactionDate binds amount-record SA-LTM-TX-DT: KSADEP0:689 same-day check, 1990–1991 update, 2029–2048 accumulation—not general-account SA-LAST-TXN-DT. detailCount follows 2073 WK-DET-ITEM addition with explicit demo itemCount=1; real multiple-detail generation/unprinted counts remained absent. Configuration, source amount classification, and original runtime evidence stayed distinct.

## Audit evidence

deposit_audit/events were demo APP/PHY, not migration of a legacy bank audit interface. JdbcDepositStoreTest grew to ten tests, adding four for failure audit, outcome classification, and unavailable audit; NormalCashDepositTest added per-call audit assertions. Demo regression passed 76 tests. Success audit commits jointly; failure audit persists independently after rollback. Commit exceptions/post-success return failures were simulated, not actual COBOL/bank-network recovery evidence.

## Executable reports and file persistence

Ten DepositDemoMain cases used the same generated cash scenario/JDBC submission and exported independent expectations, actual DB snapshots, source identifiers, entries, and audit. DepositDemoReportTest checked report content and graceful file-store reopen; module regression passed 77 tests. Three-decimal cases used business mathematics; other parameters were explicit demo fixtures. This proved demo execution/file persistence, not golden COBOL equivalence or complete deposit migration.

## Ordinary and overdraft accrual persistence

KSADEP0:1802–1867 computes ordinary/agreement accrual from pretransaction interest-bearing amount and independent prior accrual date; 1892–1920 passes prior debt for overdraft accrual; 1946,1973–1979,1992 save accumulations/date. CSAPRD4:146–151 uses ACT function O, 165–181 explicitly quantizes by .01, and 184–191 increments counts separately for positive principal/ordinary overdraft interest. The active 5200 section did not query currency units. Six DepositAccrualTest cases ran generated scenarios/H2; module regression passed 83.

prepareCashDepositWithAccrual jointly commits accrual/accounting under one version/snapshot condition, storing original period/actual components. File reports check reopen. Exact business sums then explicit quantization are distinguished from PIC truncation. The original agreement-save entry ran only when first-tier accrual was positive; the demo accumulated each calculated tier. Mode-1 high-tier equivalence remained unconfirmed; see accrual-execution.md. Passing tests do not erase this difference or the lack of original execution.

## Financial inverse plan

KSADEP0:4610 subtracts original interest-bearing-amount change; 4640 restores original principal and two interest repayments. Role services verified insufficient capacity ES020, negative interest-bearing amount EN249, and amount/principal coexistence EN177. Seven DepositReversalAmountsTest cases ran generated scenarios, preserving later spending/deposits and not reallocating under changed loan status. Module regression passed 90.

Exposure inverts actual original delta, unlike the original statement adding original principal. This follows the agreed non-overwrite goal, not legacy equivalence. Dates/daily totals/new details in 4640 and KBN-ADD-PRDT in 4630 do not restore every snapshot/accrual by swapping old values. Cross-day/refund policy decisions were requested; complete reversal had not yet run.

## Cross-institution deposits and loss warnings

CSAQRY1:1738 onward 9704, 1874 onward 9709, and 2054 onward independent subtype checks entered Role services. Eight DepositBankQualificationTest cases ran generated services/cash submission, checking relationship/teller/transaction-exception scope, simultaneous WN200/WN201/F0554, original-warning replay, and rejection without settlement. Module regression passed 98. Roles are explicit decoded contracts, not original bank queries or proven source-runtime equivalence.

## Actual settlement query to financial preparation

DepositReversalPreparation reads successful deposit_trade, queries its current account, and invokes generated scenarios. Four initial tests covered actual cash/debt/three-decimal settlements, preservation of later transactions, rejection for absent settlement/capacity/inconsistent records, and read-only preparation. Module regression passed 102. New consistency errors are demo fact contracts; legacy KBN decoding, new reversal joint commit, and complete cross-day/refund policy were still absent.

## Inverse-entry candidates and evidence consistency

Six recorded deposit components still drive core inverse amounts. Actual demo ledger and settlement payload must agree on account, direction, and amount; all eight slots and debit/credit balance are checked separately. Two fee slots use explicit refund amounts. DepositReversalPreparationTest reached eight tests, four newly for entries; module regression passed 106. Five unexecuted candidates entered reports. Fixed DEMO accounts are not original ART/reversal-posting equivalence; no actual refund/new reversal was submitted at this stage.

## Physical financial-component settlement

prepareDepositReversalAccountUpdate builds an independent snapshot from current account/inverse amounts. JdbcDepositReversalStore saves unique original-settlement association, new account, inverse ledger, and success audit. Twelve tests covered later-component preservation, concurrency/repetition, version conflicts, and faults; demo regression passed 118. This is actual component commit under financial fixtures, not complete cash reversal, KBN/ART migration, cross-day policy, or Quantum/Saga runtime acceptance.

## Actual reversal eligibility path

CSAQRY1:235–247 branches on SYS-TX-TYP; DEP reversal from 2312 invokes loss, canceled-opening, and full-freeze checks, not ordinary 9709 cross-institution deposit. KSADEP0:603 still checks Closed first. Four DepositReversalEligibilityTest cases ran selected services with current controls/exception scope. JdbcDepositReversalStoreTest reached fourteen, adding graceful file reopen and identity-error audit classification; module regression passed 124. This did not prove complete front-end eligibility, cross-day policy, or crash recovery.

## Saga recovery scope

ExplicitSagaStep invoked native SAGA with explicit JdbcDepositStore settlement lookup and generated inverse/JdbcDepositReversalStore binding. Six DepositSagaRecoveryTest cases used persisted facts to verify original-component-only inversion, failed recovery, stable-identity retry, and settled inverse versus incomplete journal recording. Demo regression passed 130. This was framework binding of financial components, not original COBOL Saga migration, all-bank recovery, cross-process scanning, or a default compensation policy.

## Two outcomes around commit exceptions

JdbcDepositReversalStoreTest reached sixteen tests, adding real JDBC-state checks for pre/post-commit exceptions, both marked unknown and queried by original reversal identity. Complete run-demo.sh passed 132 demo/923 reactor tests. Faults were in-process proxy injection, not real bank network/COBOL measurements. Queries/retries proved one exception did not automatically cause another amount inversion.

## Historical scenario navigation

All belonged to cboddeposit: 00-deposit-kernel, 10-cash-admission, 20-bank-calendar, 30-agreement-product, 40-deposit-fee, 45-agreement-window, 50-cash-deposit, 60-deposit-accrual, 70-reversal-financial, 80-bank-qualification, 90-cash-reversal, 95-cash-transaction. Root cboddeposit.spp held navigation only; deployment/update mappings and physical settings were in cboddeposit.app/.phy.

## Source-only refactoring route addition

`cboddeposit-85-deposit-route.spp` selected four existing preparation scenarios in the same domain based on optional bank-qualification/accrual facts, with SPP-generated conditional execution. At that checkpoint the old Java entry had not switched and complete boundaries remained to migrate. See [implementation history](source-only-refactoring.md). These historical milestones are not current completion claims.
