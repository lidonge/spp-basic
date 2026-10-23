> Historical document: this records pre-refactoring implementation and acceptance, not current test results. See [the current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Original transaction facts: business comparison and locked reads

The original deposit settlement read while preparing a reversal candidate must be checked again inside the complete SPP submission transaction. Account-version CAS prevents submitting a stale balance plan; it does not prove that the original settlement receipt is unchanged. These checks have different responsibilities.

## Business expression

`sameFacts(expected, actual)` accepts two Roles or Contracts with matching declared types and collection dimensions, returning boolean. It traverses only SPP-declared properties, including nested participants and ordered collections, ignoring generator-added technical properties. Two nulls match; one null does not. Arrays and collections compare in element order; differing lengths or element facts do not match. Cycle handling prevents infinite recursion.

Built-in numbers compare by mathematical value; BigDecimal scale is not a business difference. Properties selecting a PHY numeric adapter use its compare operation and chosen type semantics, without requiring conversion to BigDecimal. Arbitrary Java objects lacking declared model metadata do not receive speculative bean comparison. This predicate does not change the existing reference semantics of object `==`.

The generator emits comparison metadata for declared models and getters; the public runtime performs generic recursive comparison. Bank-specific JSON text comparison must not enter the runtime. Bank source check services choose the business errors for missing or changed original settlement facts.

Historical focused verification passed BusinessCalculationSemanticsTest, ParticipantFactsTest, and ParticipantFactsGenerationTest. Generated Java was compiled and executed, covering nested properties, collections, nulls, cycles, BigDecimal scale, and a custom adapter without decimal conversion. Log: `/tmp/spp-participant-facts-framework-validation.log`. That result alone did not establish integration into bank submission.

## APP read policy

APP uses `repository(argument(0, Receipt.id), receipt) using records for update optional lookupReceiptForCommit;`. `for update` declares a synchronous query-lock policy for one identity; `optional` allows business checks to handle absence. Collection locking queries, asynchronous calls, and omitted explicit returns are prohibited. Missing collections retain the existing query semantics.

A locked read uses only the caller's declared transaction and its registered repository connection. It neither opens a separate transaction nor commits or closes the connection. Execution is rejected without a declared business transaction or registered connection. Unsupported repositories report that locking reads are unsupported instead of silently falling back to ordinary reads.

The JDBC snapshot implementation locks the exact `(objectType, objectId)` and returns the complete declared facts from the persisted snapshot. It does not fall back to unlocked APP native tables or fuzzy property matching. External raw SQL changes to mirrored APP tables are outside this snapshot lock's protection and require a storage strategy providing complete consistency. Demo source updates write synchronously through the same repository. An absent row does not imply a range lock protecting a missing identity.

The lock remains until the outer transaction finishes. SPP comparison rejects changed original receipts and the entire reversal rolls back. The historical focused reactor passed 27 JUnit tests with no failures, errors, or skips, covering participant comparison and generated execution, query-binding semantics, ordinary optional-query compatibility, rejection outside transactions or without registered connections, visibility of same-transaction uncommitted data, actual timeout of competing updates, and lock release after outer rollback. Log: `/tmp/spp-locked-repository-framework-validation.log`.

Bank source 93 added original-settlement evidence, locked reads, and checks; source 91 archived the original deposit snapshot in reversal records; source 99 split prepareCashReversalAttempt / submitPreparedCashReversal while retaining the composed legacy entry. The complete SPP quantum boundary remained explicit. Historical incremental verify passed 975 JUnit tests and all 71 existing APP source entries, recorded in `/tmp/spp-original-evidence-binding-source-validation.log`. New scenarios were generated, compiled, and executed. Independent source cases for changed receipts, stale candidates, and prepared-result replay were still pending at that stage; passing existing entries did not discharge those responsibilities.

## Independent expectations for subsequent source cases

After the integration baseline, submission of prepared results was to be checked against these exact states, with fixtures, updates, and assertions expressed in SPP/APP/PHY.

1. Original deposit 600, fee 2, subsequent deposit 100; current account version 2/balance 800. After preparing reversal, a declarative update changes the original persisted receipt's settled.version to 99 without changing the account. Submission must report ORIGINAL_DEPOSIT_RECEIPT_CHANGED/ROLLED_BACK. The account remains version 2/800, with no new reversal settlement or entries, an empty original link, and only the four quantum log records from the two deposits. Restore the fixture facts and prepare again under the same reversal identity. Success leaves version 3/200, preserves both deposits, and produces six quantum log records in total.
2. After the same preparation, actually deposit another 5, leaving version 3/805. Version protection must reject the old candidate while preserving that deposit. Reread and reprepare; reversal subtracts only the original deposit components, yielding version 4/205 rather than restoring an old account snapshot. The rejected candidate must leave no reversal settlement, link, or quantum log.
3. Replaying a prepared result for an already settled reversal must first read the settlement by stable reversal identity and compare its archived original deposit facts. Matching facts may only confirm settlement and record replay, never execute again. Reusing the reversal identity after changing the original receipt must report DEPOSIT_REQUEST_ID_REUSED. Original-request and prepared-result entries should share success/failure audit rules and concurrent-result queries; an atomic commit's uniqueness conflict is not a substitute for business identity validation.

At that stage these were pending acceptance obligations, not part of the 71 passed source entries, and did not justify deleting old tests that still covered them.

The historical follow-up completed and passed all three prepared-result cases: 99zu, 99zua, and 99zub. Incremental verify then passed 978 JUnit tests and all 74 entries; log: `/tmp/spp-prepared-reversal-corrected-source-validation.log`. This still did not establish migration of all Java or completion of final source-only acceptance. These are historical results, not fresh runs of this document translation.
