# SPP_UNUSED_PARTICIPANT

## Rule

A participant declared by an implemented service has no use in its body.

## Review rationale

Review all callers before removing a parameter from its signature.

## Examples

Wrong

```spp
atomic calculate clear(Item item, Item spare) { item.quantity = 0; }
```

Right

```spp
atomic calculate clear(Item item) { item.quantity = 0; }
```
