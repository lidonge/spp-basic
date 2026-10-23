# XBOD demo implementation rules

These explicit user requirements take precedence over implementation choices in
older design documents.

- Implement the demo in SPP, APP and PHY. No handwritten business Java, including
  business services, query/update implementations, transaction wrappers, deployment
  startup or demo entries. Under the later agreement moving tests out of SPP,
  Java tests may only prepare input, compose generated services, assert and inject
  technical faults.
- All business/deployment Java must come from the language compiler/generator
  under target. Do not edit generated output.
- SPP explicitly declares complete business transaction boundaries and propagation.
  APP/PHY configure realization modes. Java must neither introduce an invisible
  whole-business transaction nor change declared boundaries.
- APP owns implementation/deployment bindings; PHY owns numeric representation,
  storage and transaction guarantees. Neither may hide business branches,
  execution order or recovery policy that belongs in SPP.
- Prefer existing syntax. Register concrete expressiveness gaps, then complete
  grammar, semantics, generation and generic runtime before migrating the demo.
  Do not fill gaps with handwritten Java.
- Do not move bank-specific Java into public runtime/templates or embed it as
  strings/external scripts to bypass these rules. Runtime provides generic
  technical capabilities; bank semantics must trace to source language.
- Historical SPP test fixtures remain under docs/legacy-source for rule comparison,
  never re-registered as production services. Use the same Java-test boundaries
  as Petstore, without implementing banking business in tests.
- Compiler/runtime work supplies generic language generation/execution and differs
  from prohibited demo business Java. Extensions need generic semantics and
  verification, not hardcoded demo behavior.
- Align with current Petstore: do not redeclare default CRUD; scenarios cannot call
  scenarios; calculate cannot call check; quantum call sites declare bounds;
  failure disposition uses fail/system ResultInfo, not SPP try/catch/throw.
