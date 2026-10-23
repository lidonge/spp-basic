# Validate the bank deposit model

Use expected business outcomes to review AI-generated implementations.

| Exercise | What to inspect |
| --- | --- |
| Normal versus bad-debt allocation | Separate interest, collection-interest and principal components |
| Zero kernel input versus cash admission | Distinct reusable calculation and front-end acceptance rules |
| Frozen funds and repayment boundaries | Proposed balances, exposure and non-negative settlement limits |
| Invalid account, operator or authorization | No accepted settlement merely because a formula is valid |
| Deposit success | Actual account movements, postings, accrual and persisted agreement |
| Reversal with later account activity | Only the original agreed components are reversed |
| Wrong original recipient or repeated reversal | Business eligibility rejects invalid transitions |
| Stale ordinary metadata | Lost updates are prevented independently of optional enhancements |
| Failure during persistence | Committed values and recovery evidence agree; no invented success |
| Restart/retry with enhanced coordination | Inspect real provider state, not only invocation counts |

Read [Java tests](../src/test/java/) for executable cases and
[web acceptance](../tools/web-acceptance.cjs) for the workbench journey.
Tests prepare input, compose generated services, assert results and inject faults;
they do not implement banking formulas.

Run [basic acceptance](running-demo.md) first. Enhanced replay/coordination requires
the installed provider and enhanced profile. Never interpret a basic fallback
warning as successful testing of a missing feature.

The table defines validation tasks, not test results for this documentation edit.
Local H2 and controlled restart tests do not certify arbitrary distributed
topologies or production banking safety.
