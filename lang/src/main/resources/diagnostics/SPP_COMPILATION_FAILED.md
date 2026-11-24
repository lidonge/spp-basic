# SPP_COMPILATION_FAILED

## Rule

The selected project failed a compiler validation step.

## Review rationale

Read the validation detail before applying suggestions from other files.

## Examples

Wrong

```spp
Role Item { MissingType detail; }
```

Right

```spp
Role Item { String detail; }
```
