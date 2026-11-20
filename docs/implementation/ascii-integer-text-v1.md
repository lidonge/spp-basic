# TEXT-1 v1 implementation and verification

Requirement: [exact unsigned integer text v1](../requirements/ascii-integer-text-v1.md).
Applicable public extension contract: revision 1, CORE-1. Baseline:
`7b6d9f8e1f1de08b3e8952a2cc6ab9979cc36684`. Codex implemented this change
on independent branch `feat/ascii-integer-text`; Qwen did not edit the SDK.
The exact Codex model/tool version is unavailable. This is a local controlled
Agent run on 2026-11-20, not a CI attestation.

The baseline compiler rejected both functions as unknown. The additive change
validates scalar String arguments, distinguishes the boolean predicate from
numeric expressions, and renders calls to the generic `AsciiIntegerText`
runtime helper. Numeric conversion uses the selected existing numeric adapter;
there is no binary floating-point parsing or silent default on invalid input.
No application-specific rules or source data enter the SDK. Existing copyright
and third-party license notices are unchanged.

## Actual verification

The commands below normalize the local checkout prefix; they were run with
`-f <checkout>/pom.xml` (or `<checkout>/lang/pom.xml`).

```sh
mvn -B -pl lang,appframe -am test -Dtest=BusinessCalculationSemanticsTest,AsciiIntegerTextTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -B -f lang/pom.xml test
mvn -B -pl generator -am test -Dtest=AsciiIntegerTextGenerationTest,AsciiIntegerTextTest,BusinessCalculationSemanticsTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -B -pl :spp-basic-cli -am package -DskipTests
```

All final commands exited 0. The full language suite ran 394 tests, zero
failures/errors/skips. The targeted reactor ran 17 language tests, two runtime
tests and one generation test, all passing with no skips. The generation test
compiles and executes the renderer's Java output with a BigInteger physical
binding, including a 30-digit value and invalid text that preserves the output
sentinel. Runtime tests cover null, whitespace, signs, decimal/exponent notation,
Unicode digits and exact scale-zero large values. Semantic tests cover invalid
arity, participants, non-String scalars and arrays. Empty argument lists are
rejected by the existing grammar before semantic validation.

A separate local CLI probe generated a complete project containing an atomic
check, atomic calculation and serial scenario. All generated Java files compiled
with javac against the newly built runtime classes and existing dependencies.
Direct generated-atom execution passed three exact conversions and eight lexical
rejections. This check initially omitted the required BusinessChecks evaluation
scope; the harness was corrected to use `evaluate`. The earlier failure is not
a runtime defect. Initial test-fixture failures (a Java variable shadowing a
package name and expecting a zero-argument call to parse) were also corrected.

The CLI packaging command skips tests; its success is build evidence only.
The targeted generator run was performed after packaging, with tests enabled.
The feature changes no persistence or transaction implementation. No database,
network topology, locale-specific parsing, wire encoding or unrelated optional
enhancement is certified by these checks. Invalid conversion may throw; callers
must use the predicate when their business contract requires a rejection code.
