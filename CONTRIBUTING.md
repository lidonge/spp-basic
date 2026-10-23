# Contributing to S++

The official repository accepts requirements, specifications, acceptance cases,
bug reproductions and AI implementation contributions produced by an Agent that
follows the applicable AGENTS instructions. Direct human edits to implementation
code are not accepted. Humans may describe problems, constraints, expected
behavior, edge cases and test data; Agents implement the code, fixes and tests.

## Public language

English is the primary language for public documentation, tutorials, SDK manuals,
example explanations and release notes. New public material is authored in
English first. New public-release UI text, CLI help and user-facing diagnostics
should also default to English; preserve language keywords and API identifiers.
Translations may supplement it and must identify their source
revision; the English document remains authoritative. Existing historical notes
need not be translated as part of unrelated changes. This policy does not require
contributors or maintainers to communicate privately in English.

## Requirements and acceptance

Before replacing a technical implementation, consult the versioned
[public extension contracts](EXTENSION-CONTRACTS.md) and cite the relevant
revision and requirement identifiers in the implementation record.

Every implementation must reference a versioned requirement or defect record and
state current behavior, expected behavior, failure cases and executable acceptance
criteria. Establish the applicable specification before changing language
semantics, compatibility protocols, distribution boundaries or licensing. Passing
one test is not a substitute for these decisions.

Agents must read the applicable `AGENTS.md` and referenced specifications, work on
an independent branch, and run the necessary checks. On acceptance failure,
revise the understanding or implementation and verify again. Do not manually
patch implementation files and continue claiming the result was entirely
Agent-produced. Preserve the real upstream origin and licenses of third-party
code rather than presenting it as original AI work.

## Implementation evidence

An implementation PR must provide verifiable information without disclosing
secrets, complete private conversations or customer material:

- Requirement identifier/version, baseline commit and proposed commit.
- Applicable AGENTS files and their revision or hash.
- Agent tool, available version and actual model identifier; mark unavailable
  information as unknown.
- A controlled execution record or CI run associated with the implementation.
- Commands actually executed, their results, untested limits and relevant
  artifact digests.
- Sources and license notices for third-party code, templates and dependencies.

Author names, `Co-authored-by`, `Generated-by` and contributor-written JSON are
claims, not proof of provenance. The official repository directly merges only
verifiable Agent implementations that pass acceptance. For external patches with
unverifiable origin, retain the requirement/reproduction and have the official
controlled Agent workflow implement it. Verified provenance still does not prove
correctness; independent acceptance and compatibility review remain necessary.

## Licensing and maintenance

This is the official repository's contribution acceptance policy. It does not
restrict third parties from manually modifying, forking, redistributing or
commercially using code granted under Apache-2.0.

The project-owned code in this basic source distribution uses Apache-2.0;
third-party files and dependencies retain their own licenses. Commercial
implementations are distributed separately and are absent from this source tree.

Maintainers arrange community scheduling and maintenance containers separately.
These contribution rules do not create or enable any scheduled automation.
