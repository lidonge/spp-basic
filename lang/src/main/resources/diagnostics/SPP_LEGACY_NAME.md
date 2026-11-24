# SPP_LEGACY_NAME

## Rule

A declaration ends in a numbered paragraph or field label such as usdHead02.

## Review rationale

A layout position can change independently of the business fact. Use the business name in SPP and express the physical mapping in APP when needed.

## Examples

Wrong

```spp
Role Transfer { String usdHead02; }
```

Right

```spp
Role Transfer { String settlementCurrency; }
```
