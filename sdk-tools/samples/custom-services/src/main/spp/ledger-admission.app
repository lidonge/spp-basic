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
    table ADMISSION_TICKETS(AdmissionTicket) { primarykey(AdmissionTicket.receipt); }
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

blank AdmissionTicket.batch { read: "", spaces; write: spaces; }
blank AdmissionTicket.note { read: "", spaces; write: ""; }
