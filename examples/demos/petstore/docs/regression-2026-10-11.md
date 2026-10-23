# Petstore regression record (round completed, with known issues)

This historical round ran the full regression requested by the user, superseding the earlier generation/compilation-only restriction. Business implementation remained in SPP, APP and PHY; Java changes were limited to generic framework code and tests. A subsequent request to commit and push saved completed fixes while the full regression was still running.

This record preserves the previous round. Conservation fixes and evidence from the subsequent goal are in the [next regression record](regression-2026-10-12-conserved-transfers.md).

## Previous round status (October 12)

The sections below preserve the repair chronology; this table summarizes the latest evidence in that round. Under the user's agreement to record unresolved work as known issues and skip dependent steps, the round was completed. This does not mean every testing-environment business flow passed: KI-001 and its dependent steps remain explicit.

| Scope | Latest result | Limit |
| --- | --- | --- |
| Full lang suite | 340/340 passed | Existing storage identity supported; no new initial-input/numbering protocol for arbitrary technical keys |
| Full appframe suite | 578/578 passed, including technical initialization | Does not establish coordinated testing-environment split-database commits |
| All 17 generator classes | 105/105 passed | Missing split-database quantum initial-value protocol not covered |
| Regenerated Petstore develop business acceptance | 33/33 passed; complete browser flow passed | Isolated in-memory databases; actual Chrome interaction and assertions |
| appframe-seata | 20/20 passed with that toolchain | All three existing test classes executed |
| testing three-application startup and shop restart | Passed | Fresh isolated split databases; three JVMs actually reported readiness=true |
| testing three-application business/browser flow | Registration failed; dependent steps skipped | KI-001: resource coordination for a new buyer's quantum initial values; technical startup initialization fixed |

## Acceptance scope

1. Build the compiler, runtime and generator from current source; regenerate and compile Petstore.
2. Run the relevant lang, appframe, generator and appframe-seata suites completely; check older tests against current language contracts and preserve valid negative tests.
3. Cover registration, cart, quotation, ordering, payment, fulfillment, cancellation/refund, returns, reservations, multiple transactions, concurrency, faults and restart recovery.
4. Start centralized develop and three-application testing deployments separately; verify the Chinese-language UI, business endpoints, cross-domain calls and deployment compatibility checks.
5. Record actual results and unverified scope; complete the goal only after all acceptance work is resolved.

## Evidence obtained

The first full reactor test reported lang: 333 tests, 31 failures and 92 errors; appframe: 562 tests, 5 failures. Upstream failures skipped generator, Seata and Petstore. Initially, all 32 independent Petstore acceptance cases failed while creating the order association index.

Completed fixes:

- Expand APP participant association indexes to physical identity columns, including composite identities. Added H2 index-metadata assertions excluding unrelated properties with the same prefix.
- Query hydration and request replay support public fields while preserving existing nested participant instances. Eight tests across two request-protocol classes passed.
- APP transactions declarations no longer access a null annotation target.
- APP scopes can reference a business service named commit; PHY config accepts the endpoint key.
- Foreign-key column nullability follows the referencing configuration rather than inheriting the referenced primary key's non-null constraint. Generated-catalog tests passed.
- Corrected two obsolete runtime assertions: identical physical connection configurations share resources; conditional updates joining an outer transaction do not commit early.
- Pricing boundary tests distinguish valid two-decimal prices from inputs exceeding PHY capacity; the latter remain rejected.

Nineteen targeted request-protocol, resource-sharing and transaction-group tests passed, as did one association-index test. This did not establish a full-suite pass.

Further investigation found that the non-primary-key product active query lacked an APP repository implementation; it was added. The local Maven language artifact predated workspace source, so the current toolchain was installed and generation repeated. Cross-domain calculation bindings then appeared in generated code and the focused catalogue acceptance passed.

Cross-domain calculation collection hydration was subsequently fixed: calculations may create new members and cannot use query rules restricted to existing members. Focused product/four-inventory-record acceptance passed. Quantum services now prefer repository identities already confirmed during participant queries instead of treating physical foreign-key column names as business property paths; identity-transfer and ambiguity-rejection tests were added. Transaction snapshots gained Map handling to avoid reflective access to JDK internals, with cyclic-reference and rollback/member-instance-preservation tests. The combined regression for these fixes passed.

## Passing combined acceptance

Command:

```bash
mvn -pl :petstore-demo -am test \
  -Dtest=PetstoreScenarioTest,PetstoreCatalogueTest,ExampleServiceFaultsTest,ArraySnapshotTransactionTest,RepositoryParticipantIdentityTest,ExampleDeclarationTest,BusinessIdempotencyDeclarationTest,ImplicitParticipantCrudTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=develop
```

Result: BUILD SUCCESS. PetstoreScenarioTest **32/32**, PetstoreCatalogueTest **1/1**, selected lang tests **18/18**, and selected appframe tests **14/14** passed.

Business acceptance covered concurrent inventory, quotation boundaries, forged client prices, duplicate requests and request collisions, payment and cancellation/refund, returns, retry after rollback, querying uncertain commit outcomes, reservation acceptance followed by stock reservation with partial commit, and actual child-JVM restart recovery.

Two additional runtime fixes were made: fault injection recognizes the unqualified Java method even when a cross-domain service name has a domain prefix; transaction evidence must not count out-of-transaction queries or technical request-registration JDBC commits as committed business stages, which would prevent retries after definite rollback. Acceptance retained the assertion that partially committed reservations cannot be blindly replayed.

Fulfillment and cancellation/refund contracts hydrate current order facts while preparing receipts, avoiding transmission of empty pre-query order facts to inventory services. Business changes remained exclusively in SPP.

Fixtures were aligned with current contracts: caller serial-number technical headers, Merchant storage identity from the customer domain, four actual nonzero quantum entries, and request properties using argument-free instance names. Concurrent failure may occur at either the business capacity check or quantum settlement boundary; both explicit rejection codes are accepted while still asserting exactly one successful order and no overselling. Restart acceptance uses an isolated H2 file database; DB_CLOSE_DELAY=-1 avoids reopening after every connection closes. A second JVM opens it after the first exits to verify persistence.

## Work still incomplete at this stage

After commit `9753ad6c`, the third full reactor test (same command as above) reported lang: **338 tests, 14 failures, 0 errors**; appframe **567/567** and appframe-seata **20/20** passed. Generator and Petstore were skipped in that reactor because lang failed. All remaining 14 failures were APP semantic tests; the compiler could not be declared fully passing.

This stage fixed three compiler issues: PHY timeoutMillis colliding with a keyword, APP quantum implementations being mistaken for existing SPP implementations, and automatic persistence inference duplicating explicit transaction modes/resources. Focused APP/SPP implementation-conflict and duplicate-inference checks were added. Fixtures adopted current participant semantics: queries have no return values, ordinary checks and calculations are separate, obsolete context and SPP try/catch/throw are rejected, and local transactions need no idempotency header. Repository revision snapshots remain authoritative; directly changing native mirror tables does not override snapshots. Native-table reading and hydration remain tested when no snapshot exists.

Focused tests passed: transaction storage 5, JDBC repositories 255, query/quantum implementation 21, and exception-control/compensation 8. Temporary editor ANTLR files generated in source directories were moved under target; Maven continues using target/generated-sources/antlr4.

The develop command `mvn -pl :petstore-demo -am package -DskipTests -Dspp.environment=develop` passed. After actual startup, /spp/readiness returned ready=true; the deployment manifest listed customer/product/inventory/petshop and LOCAL mode, and the catalogue returned four products. Startup used an isolated in-memory database without modifying existing demo databases.

Chrome verification found that an unregistered-customer query failure interrupted the entire page refresh. The frontend now renders unregistered/empty-cart states only for unconfirmed participants and continues reporting other errors. The full browser flow passed: new customer registration, cart, quotation, order, payment, shipment, receipt, return/refund, a second order followed by payment and cancellation/refund, original-request replay, English catalogue, and reservation. Wallet restoration/order states were asserted; pageerror was empty. The reusable script src/test/browser/PetstoreBrowserRegression.cjs also passed an independent rerun.

Browser regression requires Chrome and Playwright, for example `npm install --prefix /tmp/spp-petstore-browser --no-audit --no-fund playwright`. Start an isolated demo and run:

```bash
NODE_PATH=/tmp/spp-petstore-browser/node_modules \
SPP_DEMO_URL=http://127.0.0.1:18886 \
node demos/petstore/src/test/browser/PetstoreBrowserRegression.cjs
```

SPP_CHROME_BIN selects Chrome; screenshots go to target/browser-regression. Use a new member number each time and an isolated regression database: this creates real demo transactions.

On October 12, testing generation and packaging completed, but customer/product startup failed with PERSISTENCE_REPOSITORY_NOT_ENLISTED. Two causes were identified: a pure Saga plan incorrectly used the joint-execution path; local Saga nodes and remote update entries opened only default quantum resources, not their ordinary repositories. Repository transaction APIs and generated interfaces were added so local nodes/remote entries execute within their own repository resources. Five H2 tests passed, including rejection of ordinary Saga nodes accessing quantum resources, separate node commits and reads of previously committed facts. Regeneration and deployment verification continued; focused tests did not establish testing-environment acceptance.

Independent execution of all generator classes reported **105 tests, 9 failures, 17 errors**. Several old fixtures used return values, context, SPP exception syntax or scenario-to-scenario calls; generated Java compilation failures also remained. Each required repair and regression rather than blanket dismissal as obsolete fixtures. The full goal remained incomplete.

Subsequent testing verification: customer and product applications started independently, reported ready=true and manifests with customer/product respectively, SAGA, UNIFIED_STORE and shopLedger. Missing repository transactions for same-JVM cross-domain Saga provider writes were fixed, along with incorrect merging of different domainRoutes in centralized deployment. Different endpoints for the same target remain rejected; two assertions were added. The full appframe suite then passed **570/570**.

The next actual shop startup failure was **QUANTUM_PROVISION_RESOURCE_NOT_ENLISTED**. Creating Merchant requires an ordinary role record and initial cash in a separate quantum database. The ordinary Saga node had a repository transaction, but cross-database quantum initialization configuration/coordination remained incomplete. Testing startup and browser business acceptance therefore had not passed. Failed startup committed some ordinary initialization nodes and reported COMMITTED_STAGE_COMPENSATION_REQUIRED. Subsequent tests used another isolated shop database and retained the failed database for analysis, without modifying the user's original database. Nested local commits across two databases must not be described as a joint atomic commit; initialization and recovery protocols still required work.

All develop/testing services started during this round were stopped. The batch was committed/pushed as an intermediate result at the user's request; the goal remained active. Fourteen compiler cases, 26 generator cases, testing initialization and complete business/browser verification remained. Earlier Petstore 33-test success established develop business behavior only; cross-database Saga changes required regeneration and repetition of that acceptance.

The second full reactor run ended with BUILD FAILURE:
`mvn -fae -pl petstore-demo,appframe-seata -am test -Dspp.environment=develop`.
Lang ran 334 tests with 28 failures and 70 errors; appframe ran 567 with 1 failure and 0 errors. Upstream failures skipped generator, appframe-seata and Petstore in this reactor. Petstore's passing evidence came from the independent combined acceptance above.

Remaining compiler cases, generator tests, and testing browser/cross-domain deployment verification still required completion. Targeted passes did not establish a full regression pass; the goal remained incomplete.

## October 12: numeric and calculation generation regression

After commit `e6b1be3a`, PHY parsing of qualified types such as java.math.BigDecimal was fixed: built-in numeric keywords no longer break qualified names. Legacy numeric configuration and newer numeric replace/override use the same type-name rules. Business number semantics and rounding contracts did not change.

Two generated-Java compilation failures came from obsolete fixtures: collection entry parameters are now List, while a deep-copy test still supplied arrays; check services generate void methods, while a numeric-comparison fixture embedded the body in a returning method. Corrected fixtures still compile and execute generated code and retain member deep-copy, exact arithmetic, overflow and non-integral-division rejection assertions. Pure-calculation short-circuit rules no longer call require. Primitive numeric arrays enter as participant properties; foreach physical conversion and short overflow rejection remain verified. Fact-comparison tests use two explicit participants and BusinessChecks.evaluate, testing nested equality and rejection of different values/details with ordinary numbers and custom adapters.

Focused commands:

```bash
mvn -pl generator -am test \
  -Dtest=NumericPhysicalGenerationTest,BusinessCalculationGenerationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl generator -am test \
  -Dtest=ParticipantFactsGenerationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl generator -am test \
  -Dtest=ExampleCatalogGenerationTest,ScenarioConditionGenerationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

All 24, 2 and 2 respective tests passed. An independent rerun of all 17 generator classes then reported **105 tests, 1 failure, 16 errors**, reducing unresolved cases from 26 to **17**. Remaining cases required individual investigation, not blanket dismissal as old fixtures. The local numeric override test was subsequently changed to `numeric { override Amount.a with long; replace double with java.math.BigDecimal; }`; its class passed all 9 tests again, covering qualified names in the newer global replacement rule.

The full compiler command `mvn -pl lang -am test` reported **338 tests, 14 failures, 0 errors**, with no new failures. Testing split-database quantum initialization coordination, remaining compiler/generator issues and final develop/testing combined acceptance were incomplete. No demo services were restarted or changes committed/pushed in this stage; the goal remained active.

## October 12: participant query and persistence generation regression

Remote-query fixtures now use void methods with explicit single/collection participants hydrated in place. They retain checks for remote-proxy markers, no local HTTP entry, explicit rejection without an adapter implementation, and remote compensation declarations. Deployment-catalog fixtures reflect automatic binding: explicit local services and unlisted default local services are registered; defaults also generate implementation classes and interface entries.

Persistence generation uses a single-participant update, store(Stored stored), removing technical SPP id/version and separate before/after instances. Actual generated services are compiled, loaded and executed. A repository test double checks read-revision tokens, pre-call facts and post-update facts: consecutive calls produce 20→30→40 and persistence always receives the same instance. Read revisions remain framework-owned. Local update and delete each execute twice, demonstrating no request deduplication registration. This verifies generation/repository protocol, not JDBC conditional-commit or transaction acceptance.

PHY property fixtures use void queries and explicit order contracts; all 7 passed. Remote generation passed 2 tests and single-participant persistence passed 1. Role-playing migration fixtures use business identifiers such as productCode/bundleCode/sku/membershipNumber for Product/Bundle/Goods/Customer, retaining composite/substitute-player plan assertions. Query-write rejection tests remove obsolete return syntax so they reach actual query semantic validation. Output-directory failure and quantum-field DDL-exclusion fixtures add default PHY repositories; quantum DDL uses the business amountNumber key to align model and APP identity.

All 17 generator classes were selected by name to avoid upstream compiler-suite failures: **105 tests, 0 failures, 6 errors**, down from 17 unresolved cases at this stage's start. Remaining cases were EmptyCatchGenerationTest, ScenarioCollectionGenerationTest, ScenarioFailureGenerationTest, ScenarioResultGenerationTest, and the APP-semantic migration/physical-storage separation cases in GeneratorUtilValidationTest. They involved obsolete exception/return syntax and scenario-to-scenario calls; replacement and regression remained incomplete.

Latest compiler evidence remained 338 tests/14 failures/0 errors; latest appframe was 570/570 and Seata 20/20. No runtime code changed or those two suites reran in this stage. Testing quantum initialization coordination and final deployment/browser acceptance remained incomplete; changes were not committed or uploaded and the full goal stayed active.

## October 12: full generator suite passed

After resolving all six remaining cases, all 17 generator classes passed independently: **105/105, 0 failures, 0 errors, 0 skipped**. Log: /tmp/spp-regression-generator-all-current.log. Selecting every class by name avoided blockage by the still-failing complete lang suite.

Query-result tests use readItem(Item key, Item item) and readItems(Item key, Item[] items), with scenarios checking confirmation through if(item). Compiled/executed generated bodies verify in-place success hydration, an existing but unconfirmed instance on no match, empty collections, serial groups in different branches, and transactional query execution. Collection-call scenarios directly call atomics, not nested scenarios; each input call produces a distinct response. Typed business methods return void, while repeated collection deep-copy and preservation of the original fault when fault recording fails remain tested.

The obsolete empty-catch test now rejects business try/catch during parsing, retaining verification that template rendering errors do not write corrupted files. Transaction-fault generated SPP contains only explicit transaction blocks. Technical Java test handlers verify completed rollback and attach rollback failures as suppressed details on the original technical failure. SPP exception control was not restored and Java tests did not implement Petstore business compensation.

`transaction { ... } fail { ... }` was absent from the grammar. Compensation syntax/protocol remained under discussion; technical fault tests did not establish implementation or acceptance of business fail blocks.

APP migration and physical-storage catalogue fixtures use void queries, business identifiers, explicit participant targets and default repositories. Migration catalogues also verify findProduct plans inferred from default CRUD, retaining explicit repository and remote-adapter migration exceptions.

`mvn -pl generator -am install -DskipTests` succeeded. Develop Petstore regeneration and business acceptance continued. Fourteen compiler failures, testing quantum initialization coordination and final deployment/browser acceptance remained; a full generator pass did not complete the goal.

Regenerated develop business acceptance completed:

```bash
mvn -pl :petstore-demo -am test \
  -Dtest=PetstoreScenarioTest,PetstoreCatalogueTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=develop
```

BUILD SUCCESS: PetstoreScenarioTest **32/32**, PetstoreCatalogueTest **1/1**. All 867 generated Java sources recompiled. This verified develop LOCAL joint commit, business concurrency and restart recovery after Saga repository-scope fixes, not testing split-database coordination. Target had switched back to develop; later testing runs must explicitly regenerate that environment. No web server was started or changes committed/uploaded in this stage; the goal remained active.

## October 12: remote repository query interface fix

PHY validation for objectRepositoryRemote still required one parameter, and runtime supported only legacy return values or single-participant in-place queries. Support was added for the current void interface: one scalar key participant plus one result participant or collection. Single results hydrate in place; no match changes only confirmation. Collection queries follow composite-query paths, refill the same List, preserve existing member instances, and clear/mark unconfirmed on empty results. Key participants remain unchanged. Explicit result types must match the configured repository object; runtime requires a void method and non-null result participant and explicitly rejects collections without a declared generic member type. Existing single-participant queries and compatible returning runtime interfaces remain supported.

Three ObjectRepositoryRemoteParticipantTest cases cover single success/missing, composite-key collection success/empty, and rejection of a null result participant. Full `mvn -pl appframe -am test` passed **573/573** (570 existing plus 3 new); existing remote-adapter/JDBC tests also passed.

Ordinary repository batch validation was completed: collection keys may hydrate only the same participant collection, not feed keys into one result participant or an unrelated collection. Complex collection conditions require APP repository implementation. Supported standard-identity batch positive/negative tests remain; the obsolete blanket prohibition on collection parameters was not restored.

The full `mvn -pl lang -am test` reported **338 tests, 10 failures, 0 errors**, down from 14 failures. The strictAppDdlReplacement fixture's repository target was corrected so it reaches explicit repository-path checks rather than failing on a nonexistent parameter. Remote-shape negatives check result/storage type matching; composite-query negatives omit the result collection, retaining rejection assertions.

Ten remaining compiler cases involved role-composite/player key inference, quantum-storage fixtures and obsolete strictRoleOnly/current-snapshot migration checks. All 17 generator classes again passed **105/105**, logged in /tmp/spp-regression-generator-query-adapter.log. Testing initialization coordination and final deployment/browser acceptance remained incomplete. These query changes were not reinstalled, no web service was started, and changes were not committed/uploaded; the full goal remained active.

## October 12: intermediate commit — participant boundaries and first-validation diagnostics

At the user's request to commit/upload first, regression progress was saved while the full goal remained active. Earlier statements that changes were uncommitted describe their respective stages; this section supersedes them for this checkpoint.

Contract is a valid business participant, not an invalid entry under obsolete strictRoleOnly checks. Validation accepts Role and Contract while retaining entity/reference migration diagnostics and actual storage checks. Tests follow current participant semantics; role composite-key validation was not removed.

Default-binding inference errors previously appeared only on the second validation. Default bindings are now prepared before collecting/deduplicating parse/inference errors. New tests verify errors on the first validation and no duplicated diagnostics on repetition; ImplicitParticipantCrudTest passed 10/10. Current-snapshot tests add an explicit repository queried by business order number to reach their intended strict validation.

Latest full lang regression: **339 tests, 7 failures, 0 errors, 0 skipped**, /tmp/spp-regression-lang-contract-final.log. Remaining cases involved non-inferable technical keys, player/composite-key write ownership and quantum physical storage. Correct first-pass diagnostics also exposed previously missed configuration issues; failure-count changes alone do not prove resolution. Role composite keys require both generation and runtime writing fixes, not relaxed validation alone.

All 17 generator classes passed **105/105**, /tmp/spp-regression-generator-contract-final.log. Latest full runtime was **573/573**, latest develop business acceptance **33/33**, but that develop run preceded final compiler/remote-query changes in this stage and needed repetition. Testing quantum initialization coordination and distributed business/browser acceptance remained incomplete. Independent database commits were not claimed to be jointly atomic.

## October 12: role composite-key write ownership fix

Default write rules explicitly retain the participant type owning the table instead of targeting the first player's type. Generated code preserves player types/key paths as repository metadata `@performer:Type.property`; SPP needs no technical fields. Cross-type keys are allowed only when they match the role's APP primary key and take mapping; other cross-type writes remain rejected. Default role queries retain APP_DDL_ROLE_TAKE_PRIMARY_KEY so runtime can resolve player identities.

RoleIdentityContext resolves each player's key and explicitly rejects missing identities or multiple same-type candidates. Composite identities use existing encoding; writes always target the role. Revision-guarded writes use the role identity saved at read time, not a subsequently changed player identity. Existing baseline validation for ordinary field keys is unchanged.

Two JDBC snapshot repository tests were added: composite-key insertion creates only the role, not Store/Goods records, and rejects missing/ambiguous identities; guarded role updates succeed normally but reject changed player identities while retaining the original record. Full `mvn -pl appframe -am test` passed **575/575**, /tmp/spp-regression-role-write-appframe-final.log. This establishes framework identity/JDBC snapshot protocols, not physical APP DDL writes or cross-domain end-to-end acceptance.

The quantum-role snapshot fixture gained its declared globalQuantum PHY JDBC implementation without changing the quantum model. Two composite-key compiler cases and the quantum-role case passed, checking generated ownership, player keys and default-query identity source. Full lang: **339 tests, 4 failures, 0 errors**, /tmp/spp-regression-role-write-lang-final.log. All four remaining failures concerned default queries unable to infer APP-only primary keys from models, including partially matched composite role keys. Actual identity protocol and fixtures still required work; diagnostics were retained. All 17 generator classes passed **105/105**, /tmp/spp-regression-role-write-generator-final.log.

The toolchain had not yet been reinstalled, Petstore develop rerun, or testing/browser services started in this stage. Testing quantum initialization coordination and final combined acceptance remained incomplete; changes were not committed/pushed and the goal stayed active.

## October 12: native role tables and current Petstore reacceptance

A native APP DDL table test uses the two player keys in generated framework rules and checks the inventory store_name/goods_sku primary key and available column. It inserts/deletes a role, injects a technical failure after deletion, and verifies restoration of both native table and snapshot while independent player audit records remain unchanged. No Petstore business Java was handwritten. RepositoryWriteSessionTest passed **15/15**, full appframe **576/576**; logs: /tmp/spp-regression-role-write-native-fixed.log and /tmp/spp-regression-role-write-native-appframe-all.log. Initial test compilation referenced a nonexistent repository getter; it was corrected to construct a JDBC repository from the same data source and reverified.

The current toolchain installed successfully with `mvn -pl generator -am install -DskipTests`. Develop Petstore was regenerated/compiled and PetstoreScenarioTest/PetstoreCatalogueTest passed **33/33**, BUILD SUCCESS, /tmp/spp-regression-role-write-petstore-develop.log. This is develop evidence after the latest role-identity and remote-query fixes, not reused target output or older installed artifacts.

Four compiler technical-key query cases, testing split-database quantum initialization coordination and final browser/Seata validation remained incomplete. Initialization spans ordinary records and quantum values in separate resources; its recovery protocol cannot be replaced by two nested local database commits. Changes remained uncommitted/unuploaded; the goal continued.

## October 12: final current browser and Seata regression

The regenerated develop target/current appframe classes started against isolated in-memory database petstore-final-browser, listening only on 127.0.0.1:18886 and actually returning readiness=true. The checked-in PetstoreBrowserRegression.cjs exited 0. Assertions passed for registration, cart, quotation, ordering, payment, shipment, receipt, return/refund, cancellation after a second payment, original-request replay, English catalogue and reservation. pageErrors was empty and the wallet was restored after refunds. Log: /tmp/spp-regression-final-develop-browser.log. The goal's process was identified by exact port/database/main class and stopped; other services/existing databases were untouched.

The first two selected Seata test names did not exist: Maven succeeded but ran no tests, so this was not acceptance evidence. After checking sources, all three actual classes—NativeSeataSagaStateMachineCompilerTest, NativeSeataSagaEngineTest and NativeSeataTransactionBridgeTest—ran and passed **20/20**, /tmp/spp-regression-final-seata-actual.log. BUILD SUCCESS with no selected tests was not counted as a pass.

Four lang APP-only-key identity cases, testing initialization coordination and testing business/browser end-to-end acceptance remained. LogicalQuantumProvisioningClient exposed only provision; this did not prove recovery between centralized quantum storage and ordinary role insert. JdbcQuantumJournalStore.insertInitialValue requires quantum resources already enlisted in a JDBC transaction, while ordinary Saga repository nodes enlist only their own resources. Recoverable staged initialization/failure handling still required design; resource checks could not be removed or nested local commits portrayed as joint atomicity. The goal remained incomplete; changes were not committed/uploaded.

## October 12: storage identity protocol and full combined regression passed

For APP-only keys not fully inferable from models, default primary-key queries use established framework storage identity, internally marked @storageIdentity. Ordinary updates/deletes use the same identity. Fully inferable single/multiple-player keys retain existing mappings. Incomplete configuration does not silently turn explicit business queries into other query forms; first-default-binding-error diagnostics still pass. Four snapshot/participant tests now pass through actual generated default bindings, without deleting assertions or ignoring errors.

When identity is unknown, default queries preserve the instance/facts and mark it unconfirmed; updates reject PERSISTENCE_READ_IDENTITY_REQUIRED. A new JDBC test uses two records with identical business properties to verify no accidental selection for unknown identity, in-place hydration for known identity, updates affecting only the correct record, and an existing but unconfirmed participant after record deletion. New compiler coverage checks that APP-only keys do not enter SPP models and default queries receive framework identity rules. This uses already-established storage identity only; it does not claim automatic insertion values or first external identity input for arbitrary APP-only keys. Initial non-key lookup still needs an APP query.

After installing the current toolchain, the full combined command ran:

```bash
mvn -fae -pl petstore-demo,appframe-seata -am test -Dspp.environment=develop
```

**All modules BUILD SUCCESS**: lang **340/340**, appframe **577/577**, generator **105/105**, appframe-seata **20/20**, Petstore **33/33**, with no failures/errors/skips. Logs: /tmp/spp-regression-storage-identity-reactor.log and installation /tmp/spp-regression-storage-identity-install.log. This was a full combined run, not a small selected subset.

Fresh develop generation/current runtime then started on an isolated in-memory database, readiness=true. Actual Chrome passed the complete Chinese-language business flow, English catalogue, replay and reservation, with no pageErrors. Log: /tmp/spp-regression-storage-identity-browser.log. The service was stopped; existing demo databases were unused.

Remaining blockage was testing split-database initialization and end-to-end acceptance. A technical recovery proposal awaited user confirmation: record initialization stages, inspect committed ordinary facts after failure/restart, then complete quantum initial values or revoke unused ones. This is not business compensation or a promise of joint cross-database atomic commit. No unconfirmed compensation rules were implemented; the goal remained active and this stage's changes were not committed/uploaded.

Testing regeneration, production/test compilation and packaging also completed:
`mvn -pl :petstore-demo -am package -DskipTests -Dspp.environment=testing`,
BUILD SUCCESS, /tmp/spp-regression-storage-identity-testing-package.log. Tests were skipped; this proves configuration generation/compilation, not testing transactions. Target switched to testing; develop must be regenerated before use, not launched with skip-build against testing artifacts.

## October 12: latest testing startup and split-database fact inspection

Three independent JVMs used latest testing artifacts and fresh isolated directory /tmp/spp-testing-current-5eggeusg. CustomerService and productService actually reported readiness=true; their manifests contained customer/product respectively, ordinary SAGA mode and quantum UNIFIED_STORE. The shop JVM exited 1 with QUANTUM_PROVISION_RESOURCE_NOT_ENLISTED and attached COMMITTED_STAGE_COMPENSATION_REQUIRED for petshop.insertStockLine / petshop.insertGoods. Evidence was saved in that directory's evidence.json. All three processes were terminated without touching other services/existing databases; readiness was not substituted for business acceptance.

After shutdown, the isolated H2 databases were opened read-only with IFEXISTS=TRUE and ACCESS_MODE_DATA=r. Ordinary shop storage contained four StockLine snapshots holding only Goods.sku, not available/reserved/sold. Quantum ledger storage held 12 inventory facts: available=10, reserved=0, sold=0 per product. Earlier inventory nodes had already produced facts in both stores; recovery must address those nodes, not just failed Merchant cash initialization, and cannot treat committed nodes as ordinary database rollback. Logs: shop-inspection.log and ledger-inspection.log.

The latest failure matched previous evidence. No test services or builds remained running. Independently actionable centralized-deployment regressions had passed; testing initialization recovery and subsequent end-to-end acceptance depended on the unanswered rule-selection question. No unconfirmed business compensation was implemented and partially committed initialization was not rerun to conceal failure. The goal was incomplete and changes remained uncommitted/unuploaded.

## Blockage audit: awaiting initialization recovery rules

The same initialization-recovery issue recurred during the proposal, latest three-JVM run and status review. Full develop combined/browser acceptance passed; testing generated/compiled, with customer/product ready. Shop startup still failed because quantum initial-value resources were not enlisted, while earlier nodes had committed. There were no active builds/tests to await or independent checks that could replace the rule decision.

The user was asked whether to adopt framework-recorded initialization stages, inspection of committed ordinary facts after failure/restart, then completion of quantum values or revocation of unused ones. This must include previously committed inventory nodes, not only Merchant. Business compensation remained separately discussable and was not added unilaterally. The full goal was marked blocked, not complete; changes were not committed/uploaded. Implementation/testing would resume after the decision.

## October 12: initialization reclassification and unresolved-work handling

The user clarified that database/quantum initialization is technical startup preparation, not a business transaction requiring business compensation. The earlier blocked assessment was withdrawn and regression resumed. APP web.start runs within a generic runtime initialization context; readiness is not published until initialization completes. Ordinary seed records insert only when absent; quantum initial values are filled in their own database transactions without overwriting existing values. Successful seed steps survive startup failure so restart can fill gaps. This does not promise atomic commits across databases or alter normal business insertion/Saga recovery rules.

Implementation/verification sequence: first test split-database initialization, retry after partial failure and preservation of balances; then generate testing and run three-JVM startup/business-browser acceptance. Unresolved issues are recorded with impact/unverified scope; affected acceptance is skipped while other work proceeds. Unresolved work alone is not marked blocked and unverified cases are not reported as passing. Further business compensation design remains separate unfinished work.

### Technical initialization fix: actual results

Current generated testing configuration and fixed runtime started customerService, productService and petstore as three JVMs in fresh isolated directory /tmp/spp-bootstrap-testing-m8r0jigq. All reported readiness=true. The shop was stopped and restarted on the same database, again readiness=true. Startup/operator-initial-value issues were resolved without business compensation. All test processes were stopped.

The same Chrome acceptance failed buyer registration with QUANTUM_PROVISION_RESOURCE_NOT_ENLISTED: quantum initial values for business-created roles still lacked resource coordination. This is business creation, not startup seeding, and was not hidden by broadening initialization context. Per user instruction it became [KI-001](known-issues.md); dependent testing browser steps were skipped and other regression continued, without marking the goal blocked. KI-002 records supported initialization retry scope and unverified cases.

### Develop browser verification after the fix

Current develop generation/fixed runtime started on an isolated in-memory database, readiness=true. Actual Chrome passed registration, cart, order, payment, fulfillment, return/refund, cancellation after payment, replay, English catalogue and reservation; pageErrors=[]. Log: /tmp/spp-bootstrap-develop-0vjuw_t_/browser.log. Browser exit code 0; service stopped.

The earlier independent appframe log /tmp/spp-bootstrap-appframe.log is invalid: another Maven combined build was started before it finished, sharing target/test-classes and causing NoClassDefFoundError during concurrent recompilation. Acceptance instead uses /tmp/spp-bootstrap-reactor.log, run without another concurrent build. It includes the new passing DatabaseInitializationTest, appframe 578/578, lang 340/340 and generator 105/105.

### Final results for this historical round

The combined command `mvn -fae -pl petstore-demo,appframe-seata -am test -Dspp.environment=develop` ended BUILD SUCCESS: lang 340/340, appframe 578/578, generator 105/105, appframe-seata 20/20 and Petstore 33/33, with no failures/errors/skips. Log: /tmp/spp-bootstrap-reactor.log. Actual develop browser acceptance passed; testing technical startup/restart passed. Testing business registration failure and dependent skipped steps remain known issues at the user's request and do not count as passing. Target was develop.

The round ended under that known-issue agreement; business compensation and coordination of business-created quantum facts were not claimed implemented. Documentation/source changes remained uncommitted/unuploaded at that checkpoint. Existing demo databases were not modified.
