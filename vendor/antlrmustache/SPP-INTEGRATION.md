# Template engine source integration

This directory contains a fixed source snapshot of the maintainer-owned
`antlrmustache` project. Both `mustache` and `multiexpr` were authorized for
Apache-2.0 distribution on 2026-10-22. Original author notices are preserved;
this authorization does not relicense their third-party dependencies.

`ORIGIN.json` records the upstream URL, full commit and the SHA-256 of every
imported original file. Git history, IDE settings and build outputs were omitted.
The original repository has not been modified by this integration. Imported
historical code is identified as upstream code, not newly AI-authored code.

Changes from that snapshot:

- Added LICENSE, NOTICE, this integration note and the origin inventory.
- Added the Apache-2.0 license declaration to the parent POM and included LICENSE
  and NOTICE in both module JARs.
- Migrated local Java/ANTLR packages and Maven coordinates from `free.servpp` to
  `com.servpp` at the maintainer's request on 2026-10-22. Original paths and hashes
  in `ORIGIN.json` still describe the upstream snapshot. To locate its migrated
  local files, replace the path segment `free/servpp` with `com/servpp`; the
  upstream inventory is not rewritten to imply those modified bytes are original.
- Replaced personal machine paths in the parser/formatter command-line examples
  with explicit input arguments, and in the recursion example with a classpath
  resource. No rendering or expression algorithm was changed.
- Corrected README links to relative paths and documented the Java 17 / Maven
  3.6.3 baseline used by this source distribution.

The S++ root reactor builds these modules from source before the generator; no
preinstalled `com.servpp:mustache` or `com.servpp:multiexpr` JAR is required.
The parent is also independently buildable with `mvn -f vendor/antlrmustache/pom.xml
clean package` from the S++ source root.
