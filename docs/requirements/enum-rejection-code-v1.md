# Enum rejection codes, version 1

An SPP `atomic check` may use a declared enum member or scalar enum
field as the second argument of `require`. The generated rejection
returns the member's canonical wire value. That is its first alias,
or the member name when there is no alias.

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
member is a compile-time error. A named argument must resolve to a scalar
enum value. The runtime reads the code only when the condition fails.
A missing code on that path raises `REJECTION_CODE_REQUIRED`.

```spp
Role ValidationResult { AccountRejection returnCode; }

atomic check requireValidAccount(ValidationResult) {
    require(ValidationResult.returnCode == null, ValidationResult.returnCode);
}
```

Use an enum without a null or catch-all fallback when a null field means
success. The ordinary enum resolution rules still apply when a fallback
is declared.

Acceptance verifies literal compatibility, the canonical alias for a
member with multiple aliases, a member without aliases, invalid references,
and the rejection result from a generated scenario. The scenario also
accepts a null error field when the condition passes.
