# SPP_OPTIONAL_ENHANCEMENT_UNAVAILABLE

## Rule

An optional deployment feature has no implementation in the selected edition.

## Review rationale

Check whether the deployment requires the requested feature. The diagnostic describes which basic behavior the generated application uses.

## Examples

Wrong

```spp
propertyTracking: true;
```

Right

```spp
propertyTracking: false;
```
