# SPP_MODEL_WARNING

## Rule

The selected domain has a configuration or transaction-plan warning.

## Review rationale

Check the model and deployment detail in the diagnostic. For a missing translation, supply the localized value.

## Examples

Wrong

```spp
// A supported locale has no translation for GREETING.
```

Right

```spp
// Add the localized GREETING entry to the deployment translation table.
```
