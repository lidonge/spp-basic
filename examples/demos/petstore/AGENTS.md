# Petstore demo rules

- Business implementation uses only SPP, APP and PHY, without handwritten
  production business Java. Java tests under src/test/java may compose generated
  scenarios, prepare input, assert and inject technical faults. Generated output
  stays under target.
- Java framework extensions provide generic capability, not hardcoded Petstore
  fields, states, formulas or flows.
- SPP explicitly declares consistency boundaries and nested propagation affecting
  commit/failure outcomes. Ordinary transaction defaults to REQUIRED; propagation
  names include REQUIRES_NEW and NESTED, checked against the capability matrix in
  https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md. Unsupported combinations are
  compile errors. APP/PHY choose realization/resources; quantum facts inside
  transactions retain quantum consistency. APP owns deployment/exposure/repository
  bindings; PHY owns representation/storage. SPP cannot choose quantum/AT/Saga modes.
- UI displays service facts/submits commands, not pricing, eligibility, inventory
  updates or state transitions.
- Maintain requirements, implementation path and comparisons first. Register gaps
  and complete generation instead of bypassing through scripts/Java.
- Do not change the pre-existing petstore, business-showcase or bank demo's business
  implementation as part of unrelated Petstore work.
- Scenarios call atomics only, never scenarios. Different business actions have
  separate scenarios.
- SPP defines no Verification/test fixtures. Test orchestration cannot register
  as production services or APP web entries. Ordinary atomics cannot call other
  services; calculate may call calculate only, without cycles. Shared validation
  composition belongs to scenario execution groups; reusable syntax remains
  subject to design.
- Do not replace explicit participants with ShopWork/global aggregates. Carry
  only actual roles/business contracts.
- Scenarios explicitly declare required participants. Inputs may contain limited
  facts, hydrated by queries/atomics. Only atomics create participants; scenarios
  neither construct nor implicitly introduce them. Input instance identity stays
  stable; queries hydrate in place without return assignment. SPP defines no
  exception-control syntax.
- Database-changing scenarios require real business Contracts, not renamed
  technical context. Production cross-scenario composition is frontend-only.
- Scenarios explicitly invoke checks. Shared checks are composed by scenario
  execution groups, not mutually calling atomic wrappers; calculate hides no
  checks. Technical idempotency/duplicate protection belongs to APP/PHY.
  APP declares keys/content comparison. Ordinary updates default to conditional
  commit on reliable read revisions, compatible with explicit advanceVersion.
  SPP neither performs replay/deduplication nor increments technical versions.
- Prefer enums for finite states. Business expressions reference enum members;
  enum/API values use English codes. Localized text belongs to presentation or
  APP/PHY localization, not business enum values.
- Entries contain participants only. Default instance names are role names;
  aliases remain allowed. Argument-free calls bind uniquely by type/collection
  dimensions. Query confirmation uses if(participant)/identify, not null tests.
- Ordinary check failure controls its lexical block and does not roll back by
  default. Explicit checkXXX,breakTransction rolls back the current transaction
  only on failure. Quantum services express increments/decrements; callers
  declare nullable bounds, automatically enforced at settlement with rollback
  of the bound transaction. Legacy built-in checks remain compatible, not for
  new examples. See https://github.com/lidonge/spp-examples/blob/main/book/references/spp-participant-execution.md.
- Contracts express a specific scenario/agreement, not generic commands,
  intermediate values, collections or result wrappers. No ShopCommand/action
  routing or action-match validation in place of distinct business processes.
  APP maps concrete scenario participants; frontend selects entries. Improve
  detail/value syntax/generation rather than mechanically converting them to roles.
- Multidomain demonstrations use real customer/product domains. Consumers use
  Person/Product References. Central APP deployment does not change ownership;
  mocks/direct cross-domain table access cannot replace service collaboration.
  Register and implement missing binding/application deployment syntax.
- Ordinary in-transaction queries default to locked reads. Mixed participants
  lock when ordinary fields change; quantum-only changes do not take ordinary
  read locks. SPP declares no database locks; no new forupdate configuration is
  needed. Quantum attributes belong to a logical quantum domain; APP chooses and
  PHY integrates database/TCC/dedicated realization, not a fixed physical layout.
- Ordinary read-modify-write outside transactions uses default optimistic
  protection without revisions in SPP. Quantum facts hydrate/settle in selected
  quantum storage, not copied ordinary snapshots. Scenario deduplication must not
  invisibly wrap multiple source transactions.
- Under the coordination protocol, remote updates deduplicate by caller identity
  and unique caller serial; local calls do not register requests. UI duplicate
  protection applies only at web scenario entries via APP duplicate, default
  five seconds; remote idempotency default retention is three days. Running/
  recovery-pending records never expire into automatic reexecution. See
  https://github.com/lidonge/spp-examples/blob/main/book/references/app-call-idempotency-and-duplicate-guard.md.
- Standard primary-key queries, ordinary insert/update/delete and unique Reference
  provider bindings are generated, without duplicate repository/persist/bind.
  Local complex non-key queries use APP repositories; custom external queries
  bind targets only and require identical participant identity or explicit take,
  never guessed field shapes. Role queries read local roles then recursively
  hydrate players through owning-domain services, never direct cross-domain tables.
- Do not redeclare standard CRUD in SPP: call find/insert/update/delete + type,
  including collections with the same name. Complex queries/business atomics
  stay explicit. See https://github.com/lidonge/spp-examples/blob/main/book/references/implicit-participant-crud.md.
- Reference plus unique explicit take binds cross-domain CRUD:
  insertPerson(customer)/updatePerson(customer) maintain the customer subject;
  insertBuyer(customer)/updateBuyer(customer) maintain the shop role.
  Do not retain duplicate registerPerson(Buyer) proxies.
- APP may add technical keys absent from SPP. Generated CRUD must use them.
  APP mappings/runtime own technical identity, not explicit SPP fields/operations.
  Single-player roles may derive added keys from the player's key; default CRUD
  is generated.
- Transaction/property ownership follows
  https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md. APP chooses ordinary atomic
  modes consistently for a role. Non-LOCAL settles quantum first, ordinary later,
  using Saga across modes. The LOCAL exception follows
  https://github.com/lidonge/spp-examples/blob/main/book/references/deployment-environments-and-physical-defaults.md:
  same JVM/database/schema/connection resources across participating domains allow
  joint quantum/ordinary commit. Ordinary role saves write only their own table,
  not implicit player saves. New/redefined fields belong to the role; inherited/
  mapped fields default to original ownership. APP snapshot copies persist
  independently in role tables without reverse-saving players; see
  https://github.com/lidonge/spp-examples/blob/main/book/references/role-mapped-storage.md. Business compensation still requires
  separate agreement.
