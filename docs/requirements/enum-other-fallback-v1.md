# Enum fallbacks, version 1

Use a colon for a wire alias or a fallback marker.

```spp
Enum AccountType {
    DEPOSIT:"deposit",
    LOAN:"loan",
    PUBLIC:"02","04","06","25","42","62",
    UNRECOGNIZED:"unrecognized":other,
    NULL_VALUE:null
}

Role Account { AccountType type; }
Role AccountRoute { boolean supported; }

atomic calculate selectAccountRoute(Account, AccountRoute) {
    switch (Account.type) {
        case DEPOSIT:
        case LOAN:
        case PUBLIC:
            AccountRoute.supported = true;
            break;
        case UNRECOGNIZED:
        case NULL_VALUE:
            AccountRoute.supported = false;
            break;
    }
}
```

An enum can declare one `other` member and one `null` member. Both
markers are reserved language keywords. A quoted string is a wire
alias. A member can accept multiple quoted aliases separated by commas.
When a member also declares a fallback marker, the aliases come first.
Every alias must identify a single member. Generated JSON and typed JDBC
writes use the first alias, or the member name when no alias is declared.
Typed JDBC reads accept every declared alias. Applications that pass a
physical code through unchanged keep the original input alongside the
classified business value.
The OpenAPI enum lists canonical output values. Its
`x-spp-enum-input-aliases` extension records additional accepted input
values for the Java generator.

- SPP scalar enum reads use the `null` member for an absent value when
  declared. Otherwise they use the `other` member when present.
- Generated Java decoding matches a declared wire value first. An
  unrecognized non-null value uses the `other` member. Java and JSON
  null inputs use the explicit `null` member, or the explicit `other`
  member when declared alone.
- A business switch uses the enum member name in SPP. The generated
  Java switch expands its label to all accepted wire aliases.
- An unmarked member named `OTHER` keeps its historical fallback
  behavior when no explicit `other` marker is present.
- Enums without either fallback keep their existing decoding and
  SPP expression behavior.

These markers are enum resolution rules. APP mappings still bind
external code systems to business alternatives.

Acceptance exercises both markers in the same enum, explicit precedence over
the legacy `OTHER` convention, alias resolution, null-only strict decoding,
generated business switches, and compiler rejection of repeated markers.
