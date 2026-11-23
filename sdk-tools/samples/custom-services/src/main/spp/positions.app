domain positions;

tables entities {
    table POSITIONS(Position) {
        primarykey(Position.account, Position.currency);
    }
}

tables entities {
    table ADMISSIONS(Admission) { primarykey(Admission.account); }
}
