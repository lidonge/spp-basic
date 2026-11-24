# SPP_REDUNDANT_TAKE_MAP

## Rule

A `take Source as Target` block explicitly maps every shared same-name, same-type field to itself.

## Review rationale

Writing each identity mapping repeats the field names and can obscure the fact that the mapping is direct. Use `mapsame` for this complete identity mapping. This rule does not apply when only some shared fields are listed or when there is a renamed field.

## Examples

Wrong:

```spp
domain shop;
Entity Person { String name; int age; }
Role PersonView { String name; int age; }
take Person as PersonView { int age = age; String name = name; }
```

Right:

```spp
domain shop;
Entity Person { String name; int age; }
Role PersonView { String name; int age; }
take Person as PersonView { mapsame; }
```
