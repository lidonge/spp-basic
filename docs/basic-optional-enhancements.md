# Optional enhancements in basic generation

Requirement BASIC-EXAMPLES-20261023-v1, confirmed by the maintainer on 2026-10-23.
This refines EXT-1 and CORE-1 in `EXTENSION-CONTRACTS.md`; it does not relax CORE-1's
revision checks, explicit-null writes or business transaction boundaries.

## Required behavior

The same public example sources should build with the basic SDK. Missing optional
official enhancements must produce an English compilation warning and select a
documented basic path, rather than requiring an enhanced dependency merely to
read the configuration. Generated code must not reference absent optional classes.
The enhanced profile may opt into separately supplied implementations.

For property tracking, an absent model-generation provider means plain models and
full ordinary-fact updates. Emit `SPP_OPTIONAL_ENHANCEMENT_UNAVAILABLE`, including
the domain, feature and fallback. An explicit disabled setting needs no warning.
Basic optimistic concurrency remains enabled independently. Generation of Java,
compilation and actual database effects must be verified, not only warning text.

Other optional configurations have explicit basic behavior:

- Missing request-coordination provider: no replay or automatic duplicate guard;
  each invocation executes normally, with business checks still active. The
  built-in optional memory request store is not provisioned. Unknown custom
  adapters are not substituted.
- Missing automatic partial-update provider: retain basic full-fact updates and
  revision protection. Explicit custom technical bindings remain authoritative.
- Missing automatic update scheduler: serial automatic updates; explicit SPP
  parallel semantics are unchanged.
- Missing official `InvocationTraceService` or `PartialUpdateService`: warn and
  omit only these allowlisted bindings and catalog entries. Present compatible
  services remain bound. Unknown/custom implementations are not ignored.

The basic request catalog uses the inert `jdbc` selection when optional `memory`
was requested without a coordination provider. This creates no coordination
store and provides no replay guarantee. Explicit calls requiring coordination
remain errors; this is not a claim of equivalent retry semantics.

Warnings are not a substitute for a required implementation. An unresolved
persistence binding, unsupported transaction guarantee, quantum-store contract or
remote-call correctness requirement still needs a compatible basic/custom adapter
or a compile-time error. Do not silently discard business checks or transaction
boundaries, or implement missing bank/Petstore business logic in Java.

## Acceptance and remaining scope

- Property tracking requested/default-on, provider absent: warning, plain generated
  models, full updates, explicit null persistence and independent concurrency.
- Property tracking explicitly off: no missing-tracking warning.
- Provider available: preserve its existing SPI behavior; no false absence warning.
- Complete examples: default basic build has no enhanced Maven dependency; enhanced
  profile is opt-in. Actual example build and execution remain separate acceptance
  gates; a property-tracking test alone does not establish these gates.
- Public examples live directly under `examples/`, retain their MIT notices, and
  contain English public documentation rather than Chinese original drafts.
- Homepage Source points to the published basic repository's examples directory.

Implementation and verification are in progress. This document does not claim
the complete example migration or website publication has passed.
