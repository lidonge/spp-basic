# SPP_FIND_MISSING_INDEX

## Rule

A find filters a mapped table by non-key fields and no declared index starts with a field constrained by the lookup.

## Example

```spp
readonly Scenario serial locate(Posting posting) {
    unique findPosting by Posting.batchNumber = REQUESTED_BATCH;
}
```

If the table is keyed by posting number, the database may scan rows to find a matching batch. When this lookup is frequent, declare the physical index in the APP table mapping.

```app
tables entities {
    table POSTINGS(Posting) {
        primarykey(Posting.postingNumber);
        index byBatch(Posting.batchNumber);
    }
}
```

An existing compound index starting with `Posting.batchNumber` can also serve this predicate. Check the deployed schema and query plan when indexes are managed outside the project. The lookup's `unique` modifier controls duplicate handling independently of whether the index enforces uniqueness.
