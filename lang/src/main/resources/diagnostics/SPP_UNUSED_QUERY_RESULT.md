# SPP_UNUSED_QUERY_RESULT

## Rule

An ordered scenario replaces the result of an earlier query before any step reads it.

## Example

Both queries fill the same collection. Only the second result reaches the caller.

```spp
atomic query listEntries(key Region selector, Entry[] rows);
Scenario serial review(Region east, Region west, Entry[] rows) {
    listEntries(east, rows);
    listEntries(west, rows);
}
```

Remove the first call if the review needs only the western entries. To compare the regions, keep the first result in a separate participant or consume it before issuing the second query. A final query can legitimately populate a participant for the caller.
