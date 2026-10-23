# Tutorial and manual validation

Documentation revision: 2026-10-23. Target: the `1.0-SNAPSHOT` SDK source layout
and 0.1.2 extension settings. The scope is English tutorials and SDK reference
material, including the configuration and boundaries of separately distributed
paid services. The implementation work was performed with Codex; an exact model
build identifier is not available in the execution record.

The documented input is checked in under `sdk-tools/samples/tutorial`. No
compiler/runtime behavior was changed for this documentation. The APP example
explicitly binds its local scope before declaring the runnable scenario.

## Initial documentation verification

- Built and installed the basic CLI and its reactor dependencies with
  `mvn -Pbasic -pl :spp-basic-cli -am install -DskipTests`. This setup command
  intentionally skips tests; it is not a test-suite result.
- Executed the tutorial's `check` and `generate` commands with the rebuilt basic
  CLI. The compiler reported one domain and one scenario.
- Built the generated project using `mvn verify`, resolved its classpath, added
  the documented H2 driver, and executed `GeneratedExampleRunner preview`.
  Result: `PASS preview`, one passed and zero failed. Then ran the checked-in
  `VerifyTutorial.java` acceptance adapter with the same classpath: exit 0 and
  `BUSINESS PASS preview`.
- Merged the manual's five system switches, request retention, duplicate rule,
  trace binding, MyBatis adapter selection and parallel scheduling into a
  temporary copy of the custom-service sample. Basic CLI `check` passed for
  one domain and four scenarios. This verifies declaration syntax and references;
  it does not execute the commercial implementations.

- Changed only the comparison to `Facts.amount == 15` in a temporary source copy,
  generated and built that copy, and ran the same acceptance adapter. It exited 1
  with `AssertionError: Business check rejected preview`. The generic runner's
  PASS line alone does not detect this business rejection; the tutorial now
  explains that distinction and checks the actual business status.
- Ran `mvn -Pbasic,basic-example -pl :spp-custom-services-example -am test` with
  local networking available. The reactor passed: language 392, basic runtime
  666, generator 128 and the custom sample 3 tests (1,189 total; zero failures,
  errors or skips). The sample checks real H2 full/null writes, conflicting
  revisions and explicit custom callbacks.
- Ran `python3 -m unittest discover -s sdk-tools/distribution -p 'test_*.py'`:
  18 tests passed.
- Exported the allowlisted source with `export-basic.py --output` and checked the
  new guides' relative links and anchors in both workspace and exported tree.
  The English guides and runnable tutorial are included; commercial source and
  private Git history remain excluded. Source export readiness is not a binary
  release or production-readiness certification.
- Compared the tutorial's complete SPP/APP listings with the checked-in inputs
  and ran `git diff --check`. No differences or whitespace errors were found.

## Environment and limits

The initial Maven install attempt could not write the restricted default local
repository; installation succeeded with the required filesystem access. The
first full basic-example reactor test attempt reached the runtime suite but ten
local HTTP fixture tests were blocked by sandbox socket restrictions. These are
environment failures, not a claimed passing run.

The initial minimal example lacked a local-scope declaration and failed during
APP compilation. The final input includes the explicit local binding; both
checking and generation were rerun successfully. Runtime sources were not edited
to hide the failure.

Paid-service descriptions were reviewed against the current provider, interceptor,
repository and transaction implementations and existing acceptance-test source.
The commercial test suites were not rerun for this documentation-only task.
This documentation task does not certify a production account flow, Marketplace
installation, real Seata TC, production database proxy, XA recovery or multi-node
failure recovery. No public repository, website or binary release was published.

## Integration with the current master

The master integration is based on `4780ff99e`, which already includes the
`com.servpp` Java package and Maven group migration. Only the documentation
changes from `c03e60e43` were ported; its earlier independent platform feature was
not included. The guides and `VerifyTutorial.java` now use `com.servpp`.

Integration verification on the migrated SDK:

- Rebuilt and installed the basic CLI reactor with
  `mvn -Pbasic -pl :spp-basic-cli -am clean install -DskipTests`.
- Reran tutorial checking, generation, generated Maven verification and the
  acceptance adapter. The valid example printed `BUSINESS PASS preview`.
  A separately generated comparison-to-15 variant exited 1 with the expected
  business rejection assertion.
- Reran the custom sample's three acceptance tests with
  `mvn -Pbasic,basic-example -pl :spp-custom-services-example -am test -Dtest=CustomServiceTest,BasicPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false`.
  All three passed; this targeted run does not rerun the full 1,189-test suite
  recorded above.
- Reran the 18 distribution-tool tests successfully. Checked that the changed
  files contain no old package/group references and that all public export
  manifest inputs exist under the migrated source layout.
- Exported the migrated source and verified the guides' local links and anchors
  in that export. No commercial source or Git history was included.
