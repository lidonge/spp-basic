# Enum rejection codes, version 1

An SPP `atomic check` may use a declared enum member as the second
argument of `require`. The generated rejection returns the member's
canonical wire value. That is its first alias, or the member name when
there is no alias.

```spp
Enum AccountRejection {
    ACCOUNT_NUMBER_REQUIRED:"EL024",
    ACCOUNT_NOT_FOUND:"EL025"
}

Role AccountInput { boolean provided; }

atomic check requireAccountNumber(AccountInput) {
    require(AccountInput.provided, AccountRejection.ACCOUNT_NUMBER_REQUIRED);
}
```

Existing string literal codes keep their behavior. An unknown enum or
member is a compile-time error. A runtime field and arbitrary expressions
are outside the syntax for a rejection code.

Acceptance verifies literal compatibility, the canonical alias for a
member with multiple aliases, a member without aliases, invalid references,
and the rejection result from a generated scenario.
