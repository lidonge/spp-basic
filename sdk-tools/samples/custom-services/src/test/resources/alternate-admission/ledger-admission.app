domain ledger;

play ProviderResult by positions.Admission {
    accountNumber = account;
    code = returnCode { NOT_FOUND: "404", "MISS"; SUCCESS: "OK", "200"; };
}

play AdmissionLookup by positions.Admission {
    keyCode = account { FIRST: "K1"; };
    code = returnCode { NOT_FOUND: "404"; };
}

tables roles {
    table STORED_RESULTS(StoredResult) { primarykey(StoredResult.receipt); }
}

play StoredResult by positions.Admission {
    accountNumber = account;
    code = returnCode { SUCCESS: "OK", "200"; };
}

play AdmissionText by positions.Admission {
    accountNumber = account;
    text = returnCode;
}

play CodedAdmission by positions.Admission {
    accountNumber = account;
    code = returnCode { NOT_FOUND: "   "; };
}
