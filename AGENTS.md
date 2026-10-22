# S++ basic SDK contribution rules

Read CONTRIBUTING.md before implementation. Humans supply requirements,
constraints, acceptance criteria and reproductions; accepted implementation
changes are produced by an Agent following these rules. Record verifiable
provenance and actual tests. Do not invent model versions or execution evidence.

- Keep business behavior in SPP and deployment choices in APP/PHY.
- Keep basic optimistic concurrency independent of optional optimizations.
- Do not introduce a dependency on a closed implementation into the basic build.
- Preserve third-party attribution and licenses. Do not relicense external code
  on the strength of an author name or a local dependency cache.
- Verify actual database effects for transaction changes and compile generated
  code from its source/template inputs.
- Do not commit secrets, database contents or generated build outputs.
- Community scheduling and maintenance containers are arranged separately by
  the maintainer; do not implement or enable them as part of ordinary changes.

Run the applicable Maven/Node checks and describe failures and untested limits
honestly. A local H2 test is not proof of production distributed recovery.
