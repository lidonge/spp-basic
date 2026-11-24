domain reserves;

tables entities {
    table RESERVES(Reserve) {
        primarykey(Reserve.denomination, Reserve.referenceCode);
    }
}

formHeader SlipHeader {
    length width 2;
    code width 8;
}
form ReserveSlip as "PZZZ7602" length 63 {
    header SlipHeader;
    referenceCode width 12;
    amount width 17 decimal(15,2);
    postingDate width 8 date;
    entries occurs 2 { label width 5; units width 3 decimal(2,0); }
}
blank ReserveSlip.referenceCode { read: null, "", spaces; write: null; }
