domain ledger;

tables roles {
    table FUNDS(Funds) {
        primarykey(Funds.wallet);
    }
}

play ProviderPlayedRole by positions.Position {
    accountCode = account;
    currency = currency;
    claim = outstanding;
}

play ProviderPlayedRole by reserves.Reserve {
    accountCode = referenceCode;
    currency = denomination;
    claim = available;
}

play SettlementPosition by positions.Position {
    accountCode = account;
    unit = currency { LOCAL_UNIT: "UNIT"; };
    claim = outstanding;
}
