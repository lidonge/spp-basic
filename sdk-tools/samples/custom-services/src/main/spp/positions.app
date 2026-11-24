domain positions;

tables entities {
    table POSITIONS(Position) {
        primarykey(Position.account, Position.currency);
    }
}

tables entities {
    table ADMISSIONS(Admission) { primarykey(Admission.account); }
}

blank ADMISSIONS.returnCode { read: null, "", spaces; write: spaces; }
blank ADMISSIONS.account { read: spaces; write: spaces; }
