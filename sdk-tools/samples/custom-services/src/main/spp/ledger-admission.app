domain ledger;

play ProviderResult by positions.Admission {
    accountNumber = account;
    code = returnCode;
}

play AdmissionLookup by positions.Admission {
    keyCode = account;
    code = returnCode;
}

tables roles {
    table STORED_RESULTS(StoredResult) { primarykey(StoredResult.receipt); }
}

play StoredResult by positions.Admission {
    accountNumber = account;
    code = returnCode;
}

play AdmissionText by positions.Admission {
    accountNumber = account;
    text = returnCode;
}

play CodedAdmission by positions.Admission {
    accountNumber = account;
    code = returnCode { NOT_FOUND: "   "; };
}
