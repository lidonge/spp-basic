# Enum OTHER fallback, version 1

An SPP enumeration can declare an `OTHER` member for values outside its
declared alternatives. A missing scalar enum fact reads as `OTHER` in
SPP expressions and can be used directly as a switch selector. The
member can have a wire alias.

```spp
Enum AccountType { DEPOSIT, LOAN, OTHER }

Role Account { AccountType type; }
Role AccountRoute { boolean supported; }

atomic calculate selectAccountRoute(Account, AccountRoute) {
    switch (Account.type) {
        case DEPOSIT:
        case LOAN:
            AccountRoute.supported = true;
            break;
        case OTHER:
            AccountRoute.supported = false;
            break;
    }
}
```

The generated Java enum resolves known values before returning
`OTHER` for an unrecognized value. APP remains responsible for mapping
external code systems into the declared business alternatives. Enums
without `OTHER` retain their existing strict decoding behavior.

Acceptance covers generated fallback code for a named enum with a wire
alias, a strict enum in the same domain, and an omitted field in a
generated business switch. Role initialization and APP invocation
profiles are separate features.
