# SPP_DUPLICATE_ENUM_CODE

## Rule

Two enums in the same domain accept the same wire value under different member names. The warning points to the later declaration.

## Review rationale

Review the vocabulary with the producer of the code. Share an enum when the concepts and accepted codes agree. Independent code systems can legitimately reuse a wire value.

## Examples

Wrong

```spp
Enum InvoiceState { APPROVED:"OK" }
Enum InvoiceDecision { ACCEPTED:"OK" }
Role Invoice { InvoiceState state; }
Role InvoiceView { InvoiceDecision state; }
```

Right

```spp
Enum InvoiceState { APPROVED:"OK" }
Role Invoice { InvoiceState state; }
Role InvoiceView { InvoiceState state; }
```
