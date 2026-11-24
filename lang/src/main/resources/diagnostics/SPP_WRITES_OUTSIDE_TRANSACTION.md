# SPP_WRITES_OUTSIDE_TRANSACTION

## Rule

A serial sequence issues consecutive persistent writes outside an explicit SPP transaction block.

## Review rationale

An earlier write may commit even if a later write fails. Use a transaction when the business operation requires both writes to succeed or fail together. Keep independent boundaries when partial completion is intentional.

## Examples

### Wrong for an atomic posting

```spp
domain ledger;
Entity Entry { int postingNumber; }
Contract Posting { String reference; }
Scenario serial post(Posting request, Entry debit, Entry credit) {
    insertEntry(debit);
    insertEntry(credit);
}
```

### Right for an atomic posting

```spp
domain ledger;
Entity Entry { int postingNumber; }
Contract Posting { String reference; }
Scenario serial post(Posting request, Entry debit, Entry credit) {
    transaction {
        insertEntry(debit);
        insertEntry(credit);
    }
}
```
