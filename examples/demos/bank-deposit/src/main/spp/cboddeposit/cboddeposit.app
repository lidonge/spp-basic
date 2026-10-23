domain cboddeposit;

application xbodDeposit {
    environment develop {
        deploy cboddeposit;
        transactions {
            mode: LOCAL;
            resource: cashRecordsResource;
            ordinaryUpdates: PARALLEL;
        }
    }
    environment testing {
        deploy cboddeposit;
        quantum {
            system: UNIFIED_STORE;
            resource: cashLedger;
        }
    }
}

requests {
    idempotency retention 3 days;
    duplicateWindow 5 seconds;
}

header requestContext {
    unique String globalSerialNumber;
    String callerId;
    String requestSerialNumber;
    String callMode;
    String callOrigin;
}

header responseContext {
    String status(Default = "OK");
}

tables entities {
    table BANK_ACCOUNTS(BankAccount) {
        primarykey(BankAccount.accountNumber);
    }
}

tables roles {
    table INTEREST_INCOME_ACCOUNTS(InterestIncomeAccount) {
        primarykey(InterestIncomeAccount.accountNumber);
    }
    table COLLECTION_INCOME_ACCOUNTS(CollectionInterestIncomeAccount) {
        primarykey(CollectionInterestIncomeAccount.accountNumber);
    }
    table PRINCIPAL_RECOVERY_ACCOUNTS(PrincipalRecoveryAccount) {
        primarykey(PrincipalRecoveryAccount.accountNumber);
    }
    table CASH_REVERSAL_POSTINGS(CashReversalPostingLine) {
        primarykey(CashReversalPostingLine.reversal, CashReversalPostingLine.slot);
        foreignkey(CashReversalPostingLine.reversal) references CashReversalAgreement;
    }
    table ACCOUNT_ACCRUAL(DepositAccrualState) {
        primarykey(DepositAccrualState.accountNumber);
    }
    table CASH_POSTINGS(DepositPostingLine) {
        primarykey(DepositPostingLine.deposit, DepositPostingLine.slot);
        foreignkey(DepositPostingLine.deposit) references CashDepositAgreement;
    }
    table DEPOSIT_ACCOUNTS(DepositAccount) {
        primarykey(DepositAccount.accountNumber);
    }
    table CASH_SETTLEMENT_ACCOUNTS(CashSettlementAccount) {
        primarykey(CashSettlementAccount.accountNumber);
    }
    table ACCOUNT_ELIGIBILITY(AccountEligibility) {
        primarykey(AccountEligibility.accountNumber);
    }
    table FEE_RATES(DepositFeeRate) {
        primarykey(DepositFeeRate.customerNumber, DepositFeeRate.tariff);
    }
    table CASH_FAILURES(CashFailureRecord) {
        primarykey(CashFailureRecord.handlingNumber);
        auto CashFailureRecord.handlingNumber prefix "FAILURE";
        snapshot(CashFailureRecord.deposit, CashFailureRecord.reversal);
    }
}

tables contracts {
    table CASH_DEPOSITS(CashDepositAgreement) {
        snapshot(CashDepositAgreement.postingEvidence);
        primarykey(CashDepositAgreement.depositNumber);
        auto CashDepositAgreement.depositNumber prefix "DEPOSIT";
        foreignkey(CashDepositAgreement.account) references DepositAccount;
        foreignkey(CashDepositAgreement.settlement) references CashSettlementAccount;
        foreignkey(CashDepositAgreement.interestIncome) references InterestIncomeAccount;
        foreignkey(CashDepositAgreement.collectionIncome) references CollectionInterestIncomeAccount;
        foreignkey(CashDepositAgreement.principalRecovery) references PrincipalRecoveryAccount;
    }
    table CASH_REVERSALS(CashReversalAgreement) {
        primarykey(CashReversalAgreement.reversalNumber);
        auto CashReversalAgreement.reversalNumber prefix "REVERSAL";
        foreignkey(CashReversalAgreement.original) references CashDepositAgreement;
    }
}

scope {
    local(in requestContext, out responseContext) {
        quantum resource(
            cashLedger,
            DepositAccount.interestBase,
            CashSettlementAccount.balance,
            InterestIncomeAccount.balance,
            CollectionInterestIncomeAccount.balance,
            PrincipalRecoveryAccount.balance
        ) settleCashDeposit;
        quantum resource(
            cashLedger,
            DepositAccount.interestBase,
            CashSettlementAccount.balance,
            InterestIncomeAccount.balance,
            CollectionInterestIncomeAccount.balance,
            PrincipalRecoveryAccount.balance
        ) reverseCashDeposit;
        duplicate key(
            DepositAccount.accountNumber,
            CashSettlementAccount.accountNumber
        ) facts(
            DepositRequest,
            CashChannel,
            DepositFeeParty,
            DepositFeeContext,
            AuthorizationEvidence,
            InterestIncomeAccount,
            CollectionInterestIncomeAccount,
            PrincipalRecoveryAccount
        ) window 5 seconds depositCash;
        duplicate key(
            DepositAccount.accountNumber,
            CashDepositAgreement.depositNumber
        ) facts(CashReversalAgreement) window 5 seconds reverseCash;
    }
}

web {
    expose openBankRepaymentAccounts;
    expose openDepositAccount;
    expose depositCash;
    expose reverseCash;
    expose previewCashReversal;
    expose readDepositAccount;
    expose readCashDeposit;
    expose readCashReversal;
}
