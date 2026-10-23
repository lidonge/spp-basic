# S++ toolchain requirements

This directory contains demonstration sources and tests. The parent SDK contains
the public compiler/runtime implementation; no commercial implementation is bundled.

## Website and reading

The [website and book](https://github.com/lidonge/spp-examples) are maintained
separately. Their Python-only build requires no SDK credentials. They are not
copied into this source integration.

## Generated-service demonstrations

JDK 17+, Maven and these Maven artifacts are required:

- `com.servpp:generator:${spp.sdk.version}` — source-language Maven plugin and generator.
- `com.servpp:appframe:${spp.sdk.version}` — generic service/runtime support.
- Their transitive toolchain dependencies, including the language artifact and its parent POM.

The default SDK version is `1.0-SNAPSHOT`. Override it with `-Dspp.sdk.version=...` when a compatible distribution is supplied. These coordinates are prerequisites, not a claim that the artifacts are publicly downloadable from Maven Central.

Build the public [spp-basic source](https://github.com/lidonge/spp-basic) and
install its SDK into the same local Maven repository used for the demos:

```bash
./tools/install-local-sdk.sh /path/to/spp-basic
mvn -pl :petstore-demo,:xbod-deposit-demo -am test
```

The default build has no enhanced artifact dependency. Missing optional official
providers produce `SPP_OPTIONAL_ENHANCEMENT_UNAVAILABLE` warnings. Generated basic
models use ordinary full-field updates with optimistic concurrency; absent request
coordination does not provide replay or duplicate suppression. Tests explicitly
check the selected profile's behavior, including actual persisted balances.

With separately supplied matching enhanced artifacts installed, opt in using:

```bash
mvn -Penhanced clean test
```

Do not combine this with manually overriding `spp.examples.enhanced`: that flag
is set by the Maven profile and the tests verify provider availability. Enhanced
implementation sources are not included. A missing SDK must not be bypassed by
adding handwritten business Java.

## Public references and historical evidence

Companion references are linked to their public book repository rather than
bundled. They remain reserved-rights book material, not MIT or Apache-2.0.
References to unrelated private implementation files remain maintainer references.

Historical verification records retain their source revision and original observed behavior. They are not evidence of new test runs after arbitrary subsequent changes. Current startup and build commands are in the repository README and the two demo READMEs.
