# Repository query scope, revision 1

Requirement PERF-QUERY-1, 2026-11-04. Requested by the maintainer during the
servpp-platform/Petstore generated-service performance investigation.
Applicable contracts: EXTENSION-CONTRACTS.md revision 1, CORE-1 and EXT-3.

Current behavior: repository collection fallbacks fetch snapshots of every
business type and filter them in Java. Native identity queries select every
mapped column although they consume only primary keys.

Expected behavior: filter the current snapshot table by object_type in SQL;
select only mapped primary keys for native identity queries. Preserve object-ID
ordering, composite keys, parameter binding, empty results, native hydration,
transaction history APIs, revision evidence and existing transaction boundaries.
No APP syntax, business behavior, licensing or dependency boundary changes.

Acceptance: run real H2 queries with mixed types, zero matches and composite
keys; assert result equality and the SQL result columns/rows via H2 query
statistics. Re-run existing repository concurrency/transaction tests and compile
and execute generated application scenarios from their SPP/APP/PHY inputs.
Timing probes are opt-in and have no machine-dependent pass threshold.

Implementation evidence and measured results are recorded in the workspace's
performance discussion. This record travels with the basic distribution;
commercial source and private performance fixtures do not.

## PERF-REFLECT-1: structural service signatures

The same investigation captured repeated `Class.getDeclaredMethods/getMethods`
allocation during `LocalGeneratedServiceRuntime.install` metadata assembly.
Cache immutable method parameter names by Java class with ClassValue. Preserve
declared-method precedence, inherited fallback, synthetic-method exclusion and
ambiguous overload behavior. Resolve the current service name on every call;
do not cache mutable provider metadata, physical configuration or request policy.
ClassValue avoids a global strong reference map retaining discarded class loaders.
Acceptance includes inherited, overloaded, synthetic bridge and changing service
names, followed by existing runtime/deployment and generated application tests.
CPU samples motivate this change; they do not establish a universal speedup.

## Pre-submission verification, 2026-11-20

The maintainer authorized committing and pushing the existing performance work
on `perf/generated-service-field-analysis`. Baseline:
`a15fc42242f618f733d3613dea15c56144188558`; the proposed revision is the commit
containing this record. The earlier workspace measurement manifest matches all
12 implementation/probe source hashes and all ten retained build/measurement
log hashes. This check establishes consistency with that record; it is not
independent attestation of the original author or model. Tool: Codex; exact tool
build and model identifier are unavailable. No external patch was imported by
this submission session.

Applicable instructions: AGENTS.md SHA-256
`154294754343203af3189daba5ea21ce91e4082476d85cdf559f241f7fd73f36`,
CONTRIBUTING.md SHA-256
`a4bde5f8c63f847989eaac3dc0144cd0a377e5e8cbe441ee49a7147b9024d4ac`,
and EXTENSION-CONTRACTS.md revision 1 SHA-256
`dbe9c47975b337dce79e1176dd3cbd15ec999dc6839646f12e8e75699a520b9c`.

Executed from the parent workspace with OpenJDK 17:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -o \
  -Dmaven.repo.local=/tmp/spp-sdk-m2 -f basic/pom.xml -pl :appframe -am test
```

Result: BUILD SUCCESS; 672 tests reported, zero failures/errors and one skipped
opt-in performance probe (671 executed). The suite includes the new query-scope
and signature cases and existing repository concurrency/transaction coverage.
Execution log: `/tmp/spp-perf-submit-basic-20261120.log`, SHA-256
`abade896351a7f130461e14d026f548cd1b725c4ef72c9c343a4db277cb27f77`.

Historical timings are not remeasured by this regression command. Production
HTTP latency, real external channels, production database execution plans and
distributed recovery remain outside this validation. No dependency or license
was added or changed.

The opt-in repository probe was also executed explicitly from the workspace:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 python3 \
  scripts/performance/run-probes.py repository \
  --output /tmp/spp-perf-submit-repository-20261120.log
```

Result: exit 0, OK (1 test), with 10 warmups, 40 samples per operation and
2,000 unrelated synthetic snapshots. This is a single-version execution check,
not a new before/after comparison. Workspace application regeneration and the
57 selected application/provider acceptance tests also passed.
Repository probe log SHA-256: `b9faf7ff6229bb4cb2fc559cb8e0c3ca99273ded26e1e5e1adbbf2696d72db6a`.
