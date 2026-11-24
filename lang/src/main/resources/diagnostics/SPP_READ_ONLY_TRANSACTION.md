# SPP_READ_ONLY_TRANSACTION

## Rule

An explicit transaction contains a query and all its calls are reads or checks.

## Example

```spp
Scenario serial preview(Posting posting) {
    transaction {
        findPosting(posting);
        identify(posting);
    }
}
```

If the inquiry works with an ordinary read, move the calls into the surrounding serial scenario.

```spp
readonly Scenario serial preview(Posting posting) {
    findPosting(posting);
    identify(posting);
}
```

Keep the boundary when the reads need the transaction's isolation or a shared snapshot. The check skips locking reads, unknown providers, explicit propagation and failure handlers.
