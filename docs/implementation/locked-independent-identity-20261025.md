# Locked queries on independent storage identity

Requirement `BASIC-LOCKED-IDENTITY-1`, revision 1, 2026-10-25.
Baseline: `c6d3a3feb9ca96f86788d729f034a594d2422e28`.
Contract: `EXTENSION-CONTRACTS.md`, revision 1, CORE-1 (read identity and
compatible persistence). This change does not alter the public contract.

A locked query that traverses an independently stored role with a private mapped
primary key could fail to match an existing row. After loading the candidate,
`JdbcObjectRepository` evaluated `@storageIdentity` as a Java bean property;
the mapped private key is held in repository participant identity evidence.

Resolve `@storageIdentity` and the independent role's mapped primary key from
`RepositoryParticipantSnapshots`. Keep ordinary business properties unchanged.
Missing storage identity fails explicitly with `PERSISTENCE_READ_IDENTITY_REQUIRED`.
Do not add technical identifiers to business participants or weaken revision checks.

Acceptance extends `IndependentRoleAssociationTest` with locked queries for a
nested private identity, a nonmatching identity, its mapped primary-key alias and
a root private identity. The real H2 repository is queried inside transactions.
Before the implementation change the test failed with expected 1 / actual 0;
afterwards all four test methods passed. The complete appframe suite then passed
666 tests, zero failures/errors/skips, in the integration reactor.

Commands used Java 17, Maven offline and an existing isolated dependency cache:

```
mvn -B -ntp -o -Dmaven.repo.local=/tmp/spp-sdk-m2 \
  -f appframe/pom.xml -Dtest=IndependentRoleAssociationTest test
```

The targeted command above produced the failing reproduction; the subsequent
reactor run executed the corrected test and all appframe tests. This is local H2
acceptance, not evidence for other drivers, production recovery or lock fairness.

Implementation and validation were performed by Codex in the controlled workspace
session on 2026-10-25; exact tool build/model identifier are unavailable. The
applicable `AGENTS.md` SHA-256 is
`154294754343203af3189daba5ea21ce91e4082476d85cdf559f241f7fd73f36`.
`CONTRIBUTING.md` and the public contract were read. No third-party source was added
or relicensed. No private implementation or deployment data belongs to this change.
The containing Git commit identifies the proposed revision.
