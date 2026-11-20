# Default String storage lengths

Requirement SPP-STR-1, revision 1. Applies the storage ownership boundary in
[CORE-1, revision 1](../EXTENSION-CONTRACTS.md).

A scalar participant field can declare a default storage length.

```spp
Entity CustomerAddress {
    String(20) customerNumber;
    String(80) address;
}
```

The positive decimal integer supplies the default width of the generated
character column, including key columns. An explicit width on the APP column
or in its `map` entry takes precedence. A mapping that only renames a
column retains the width. A field declared as `String` uses the existing
database column defaults.

`String(n)` has the same SPP value and Java representation as `String`.
The declaration adds no value check, truncation, or API length restriction.
Database operations follow the effective storage column definition.

Acceptance includes APP override, inherited key fields, generated SQL and
runtime table metadata, invalid lengths, and an SPP calculation whose text
exceeds the declared default.

The Basic app semantics, role composition, cross-domain storage generation
and CLI regressions passed. A freshly packaged CLI generated and compiled a
calculation with `String(3)` that accepted `LONG` and returned `LONGlong`.
An H2 2.2.224 check executed the generated table definition and stored a
120-character value under an APP override of the SPP default of 80. H2
rejected 121 characters with SQLState `22001`.
