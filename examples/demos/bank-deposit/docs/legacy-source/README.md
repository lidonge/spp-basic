# Legacy source-language reference material

These files are complete pre-refactoring source snapshots, retained only to
cross-check banking formulas, rule origins, historical acceptance expectations
and the migration ledger. They are outside Maven's SPP source directory and
neither generate nor deploy services.

The old APP's 86 examples, exception/idempotency/scenario orchestration and
technical storage mappings cannot be used directly as the new implementation.
Current acceptance uses only services generated from `src/main/spp` and results
from `src/test`. Historical pass counts apply only to their recorded versions.

The 15 recorded deposit-kernel expectation sets were extracted unchanged into
`src/test/resources/deposit-kernel-cases.json`. Tests invoke the new calculation
preview scenario and compare the original recorded expectations; they do not
reimplement the formulas.
