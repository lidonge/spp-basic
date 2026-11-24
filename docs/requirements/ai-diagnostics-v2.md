# AI-facing diagnostics, revision 2

Maintainer requirement of 2026-11-24, baseline Basic 9900b8f. Extension contract revision 1, CORE-1 and EXT-2 apply. Phase 1 emits text warnings for redundant code. Revision 2 adds source-located review warnings, a shared editor and batch diagnostic format, explain pages, and compiler-checked mechanical repairs. Business semantics remain in SPP and deployment policy in APP/PHY.

Acceptance includes positive and negative cases for each heuristic, grouped machine-readable errors and warnings, equivalent edits, idempotent re-checks, and a full basic and basic-example Maven reactor run. Compare the same spplang revision with both compiler pins. Record baseline migration failures, per-code counts, reviewed-lane recall and precision, post-fix suite outcomes, and changes in tracked SPP line count.
