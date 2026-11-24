# SPP_UNUSED_ENTITY

## Rule

An Entity declaration has no identifiable use elsewhere in the selected SPP, APP or PHY sources for its domain.

## Review rationale

Check for external consumers and tables before removing the model. A field that refers back to its own entity does not establish an external use.

## Examples

Wrong

```spp
Entity ArchiveEntry { int amount; }
Role Batch { int size; }
```

Right

```spp
Entity ArchiveEntry { int amount; }
Role Batch { int size; ArchiveEntry[] entries; }
```
