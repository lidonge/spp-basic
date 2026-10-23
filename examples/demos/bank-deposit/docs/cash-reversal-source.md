> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# XBOD cash-reversal source contract

The user selected XBOD cash reversal. Active source paths take precedence over
earlier proposed cross-date/refund policies. Line numbers refer to GB18030-decoded
source with original line ordering preserved; the original repository is read-only.

| Operation | Original program/lines | Migrated rule |
| --- | --- | --- |
| Last transaction date | KSADEP0 1990, 2812–2823 | Save the pre-trade date. Restore it if the current detail sequence still equals the original trade sequence; otherwise use reversal business date. 24-hour mode `2` always uses reversal business date |
| Detail sequence | KSADEP0 2745–2748 | Increase current sequence by newly added reversal details; do not restore the original sequence |
| Daily cash total | KSADEP0 2864–2872 | Subtract original amount from current daily total, flooring at zero. No extra same-original-date condition; also runs across dates |
| Ordinary product | KSADEP0 2724–2731 | Subtract original KBN-ADD-PRDT from current ordinary product; insufficient product gives EN097. Neither undo all original accrual nor recompute later history |
| Actual product | KSADEP0 2780–2784 | Adjusted ordinary product minus current overdraft product |
| Overdraft days | KSADEP0 2803–2810 | Original-day restoration code is commented out; retain current days. Presence of a log field does not authorize automatic restoration |
| Institution limit | KSADEP0 2644–2648 | Reverse only when original trade date equals current business date and current detail sequence equals original trade sequence |
| Fees | KSAFEE0 129–135, 324–330, 332–354 | Non-normal branch reads actual original fee from KBN before shared posting. Selected teller cash path refunds the actual charge without repricing |
| Overdraft exposure | KSADEP0 2800–2802 | Current exposure plus original principal repaid. Keep separate from the generic financial inverse's actual exposure-delta formula; formal cash entry uses the source formula |

SPP `selectXbodCashReversalFee` and `calculateXbodCashReversalMetadata` were added.
Independent plans cover fee, dates, details, totals, accrual products and limit
conditions without database writes. Ordinary cash inputs allow only same-day
value dates, so original backdated-product adjustment is zero. Future backdated
value-date support must record actual KBN adjustment, not substitute the entire
original ordinary-product increment.

At this recorded stage the new `CashDepositReversal` entry connected current
eligibility, original fees, metadata and inverse postings in a local joint commit;
end-to-end verification was in progress. The account model covered selected
deposit amounts, eligibility, daily totals and accrual products. Capital-specific
limits, minimum balance, same-day overdraft cash amount and original document/
detail-print fields were excluded, not claimed migrated.

Peripheral institution-limit adapters, original documents and actual bank ART
configuration were not integrated. Source derivation and demo tests do not mean
original COBOL was executed or bank golden results were obtained. This English
edition preserves the historical scope from revision `417a41f`.
