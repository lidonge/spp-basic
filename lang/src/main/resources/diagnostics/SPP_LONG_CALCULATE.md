# SPP_LONG_CALCULATE

## Rule

An atomic calculate contains more executable statements than the configured limit. Each control statement and each statement in its body counts once. Braces, comments, formatting and empty statements add nothing.

## Review rationale

The default limit is 40. Set a positive integer with `spp.lint.maxCalculateStatements` in the compiler JVM. For the CLI, use e.g. `JAVA_TOOL_OPTIONS=-Dspp.lint.maxCalculateStatements=2`. The example uses a limit of 2 and extracts the fee calculation.

## Examples

Wrong

```spp
atomic calculate price(Invoice bill) {
    bill.subtotal = bill.quantity * bill.unitPrice;
    bill.fee = bill.subtotal * bill.feeRate;
    bill.total = bill.subtotal + bill.fee;
}
```

Right

```spp
atomic calculate addFee(Invoice bill) {
    bill.fee = bill.subtotal * bill.feeRate;
    bill.total = bill.subtotal + bill.fee;
}
atomic calculate price(Invoice bill) {
    bill.subtotal = bill.quantity * bill.unitPrice;
    addFee(bill);
}
```
