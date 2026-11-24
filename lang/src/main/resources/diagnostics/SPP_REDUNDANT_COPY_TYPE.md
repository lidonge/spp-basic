# SPP_REDUNDANT_COPY_TYPE

## Rule

Two non-empty `Entity` or `Role` declarations in the same domain have the same field names and types.

## Review rationale

Copied field lists can drift as a model changes. Reuse the existing declaration when the concepts are genuinely the same. Keep separate types when they represent different concepts; matching structure alone does not establish that they should share an identity.

## Examples

Wrong:

```spp
domain shop;
Entity Person { String name; int age; }
Role Customer { String name; int age; }
```

Right, when `Customer` is intended to use the `Person` type:

```spp
domain shop;
Entity Person { String name; int age; }
Role Customer { Person; }
```

The diagnostic is not emitted for differing field types, such as `int age` versus `long age`.
