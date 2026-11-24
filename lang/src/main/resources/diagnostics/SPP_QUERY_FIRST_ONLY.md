# SPP_QUERY_FIRST_ONLY

## Rule

A scenario fetches a collection and its only observed use reads the first element. Consider a scalar `unique find` when two matches would be a business error.

## Example

This calculation stops after copying the first posting number.

```spp
atomic calculate selectPosting(Posting[] rows, Receipt receipt) {
    for (Posting row : rows) {
        receipt.postingNumber = row.postingNumber;
        break;
    }
}
Scenario serial preview(Filter filter, Posting[] matches, Receipt receipt) {
    listPostings(filter, matches);
    selectPosting(matches, receipt);
}
```

If the selection is a batch number and each batch should match at most one posting, pass a scalar `Posting` participant and use `unique findPosting by Posting.batchNumber = REQUESTED_BATCH;`. Handle an absent posting if the scenario requires a match. A second match triggers a rejection. Keep the collection query when selecting the first of several legitimate matches is intentional or a caller needs the remaining rows.
