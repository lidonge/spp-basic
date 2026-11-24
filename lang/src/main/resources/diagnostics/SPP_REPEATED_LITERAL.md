# SPP_REPEATED_LITERAL

## Rule

The same literal spelling appears at least three times in executable SPP in one domain. The third occurrence receives one warning.

## Review rationale

Give a shared value a business name where the occurrences represent the same fact. Counts include read-key values and distinguish signed numbers. Enum declarations, constant initializers, require rejection codes, booleans, null, blank strings and common decimal spellings of zero and one are excluded.

## Examples

Wrong

```spp
atomic calculate setFees(Fees fees) {
    fees.issue = 25; fees.renew = 25; fees.replace = 25;
}
```

Right

```spp
const int STANDARD_FEE = 25;
atomic calculate setFees(Fees fees) {
    fees.issue = STANDARD_FEE; fees.renew = STANDARD_FEE; fees.replace = STANDARD_FEE;
}
```
