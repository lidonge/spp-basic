# SPP_UNUSED_ENUM

## Rule

An Enum declaration has no identifiable use elsewhere in the selected SPP, APP or PHY sources for its domain.

## Review rationale

Check consumers of the generated API. If the enum represents a business field, use that type on the field so the model carries its vocabulary.

## Examples

Wrong

```spp
Enum Priority { STANDARD, URGENT }
Role Payment { String priority; }
```

Right

```spp
Enum Priority { STANDARD, URGENT }
Role Payment { Priority priority; }
```
