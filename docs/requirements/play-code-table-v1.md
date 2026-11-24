# APP play code tables, version 1

Approved 2026-11-24 under CORE-1. A scalar provider String can represent a
consumer enum through an optional APP field table. Each listed member replaces
all its SPP aliases on that binding. Omitted members retain declared codes.
Ordinary members without codes require a table at every play binding and have
no representation when omitted. The first effective code is written.
Unknown input uses the explicit other member and preserves its raw code,
including strings recognized only by the default vocabulary. An enum without
other rejects unknown input. Existing SQL null resolution applies.

Compiler acceptance covers field types, member names, conflicting effective
codes and per-binding table requirements. Generated provider queries and
stored role mappings must agree on decoding and canonical writes. H2 scenarios
exercise default and alternate provider vocabularies against the same SPP.
