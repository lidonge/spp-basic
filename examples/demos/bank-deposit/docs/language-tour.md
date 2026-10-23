# Bank deposit language tour

Excerpts below are taken from the linked sources; use the complete demo for compilation.

## Entity and role separate identity from participation

From [account model](../src/main/spp/cboddeposit/account/cboddeposit-00-model.spp):

```spp
Entity BankAccount {
    String accountNumber;
}
```

The entity names the enduring subject. DepositAccount, CashSettlementAccount and
the income/recovery roles describe different business responsibilities. Their
mappings must preserve account identity; coincidentally equal field names are
not sufficient evidence of ownership.

## Typed proposals and explicit validation

From [deposit kernel](../src/main/spp/cboddeposit/calculation/cboddeposit-00-deposit-kernel.spp):

```spp
Role DepositRequest {
    BigDecimal(19, 2) amount;
    String currency;
    int valueDate;
}
```

```spp
atomic check checkKernelInput(DepositRequest) {
    require(DepositRequest.amount >= 0, "E1095");
}
```

Decimal intent is visible in SPP; physical numeric representation is configured
in PHY. The reusable kernel admits zero, while the front-end cash admission rules
may be stricter. This distinction is business semantics, not a type-system bug.

From calculateDepositPlan in the same file:

```spp
    boolean principalFirst = DepositAccount.loanState == LoanState.BAD_DEBT
        || DepositAccount.loanState == LoanState.WRITTEN_OFF;
```

LoanState expresses a business classification. A calculation selects the formula;
it does not hide eligibility checks or choose a technical transaction mode.

## Scenario: business order is explicit

From [depositCash](../src/main/spp/cboddeposit/deposit/cboddeposit-scene-depositCash.spp):

```spp
    checkKernelInput;
    calculateDepositPlan;
    checkPlan;
    classifyDepositAccounting;
    checkDepositAccounting;
    checkPostingRules;
    calculateDepositPostings;
    checkDepositPostings;
    checkAccrualPolicy;
    checkAccrualDates;
    calculateDepositAccrual;
```

Calls bind the declared participants by compatible type and dimensions. Checks,
calculations and persistence are distinct operations. The scenario coordinates
atomics; it does not call other scenarios. Queries hydrate existing participants,
and identify confirms successful identification.

## Quantum quantities and settlement limits

The same scenario contains a transaction with settleCashDeposit bounds. For
example, DepositAccount.overdraftPrincipal has [0, null] limits; the settlement
account balance uses [null, null]. These are caller-specified bounds, not hidden
hardcoded limits in a generic runtime. Null means an unbounded side.

A pre-check alone cannot protect a shared quantity from a concurrent change.
Quantum limits are enforced on settlement; ordinary metadata still needs its
own revision and transaction protection.

## Failure is an explicit business disposition

The transaction's fail block records failure and inspects
ResultInfo.failure.recoveryStatus. It confirms no cash movement only for
ROLLED_BACK or REVERSED, then stores the failure record in another transaction.
An uncertain recovery result is not automatically a clean rejection.

See [layers](domain-and-layers.md) for realization constraints and
[validation](validation-guide.md) for observable acceptance.
