# Mapped physical reads, version 1

Maintainer decision of 2026-11-24, implementing CORE-1 and EXT-3 in revision 1
of the [public extension contracts](../../EXTENSION-CONTRACTS.md).

A mapped participant's physical row supplies its existence and ordinary facts.
This applies to identity, keyed, unique, collection and play queries. A query
in a LOCAL transaction uses its enlisted connection and sees its own writes.
A collection lookup selects the matching rows and revision evidence in one
statement. Snapshot-only participants retain their existing repository behavior.

A row inserted through SQL has revision zero until its first ordinary SDK update.
The update locks the physical row, checks its ordinary columns against the
read baseline, and establishes snapshot revision evidence in the same transaction.
For a row with a snapshot, a difference between the physical ordinary columns
and the stored image causes PERSISTENCE_VERSION_CONFLICT even if the revision
token still matches. Reads continue to expose the SQL writer's values.
Independent platform and quantum facts retain their own update protocols.

## Regression

Lane A inserts one entry directly into PHYSICAL_ENTRIES and calls listEntries
with the same sector before and inside a LOCAL transaction. The baseline
returns one row before and zero inside. Both calls must return one.
Run tests/acceptance/physical-array-lane-a/run.sh with a configured Maven
repository. RepositoryDeleteDdlTest covers externally inserted, modified and
deleted rows, revision-zero updates, read-your-writes and conflict rollback.

Stored array fields use JSON CLOB columns on their owning physical table.
An array of mapped participants retains its projected references, including
private identity keys. The declared relationship query resolves those references.
When deploying over an existing schema that omitted a stored array, add and
populate its generated column before switching readers. Physical rows and
relationship keys must be present at cutover.

Composite snapshot identities have an indexed SQL lookup key. Store
initialization populates this key from existing composite IDs. The mapped
query projects the revision beside the physical row and defaults it to zero
when the snapshot is absent.
