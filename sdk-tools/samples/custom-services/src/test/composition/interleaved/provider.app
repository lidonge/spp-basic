domain provider;
tables entities {
    table HANDOVERS(CustodyHandover) { primarykey(CustodyHandover.receiptNumber); }
    table TILLS(CustodyTill) { primarykey(CustodyTill.receiptNumber); }
}

scope { local { at resource(records) persist(insert, argument(0, CustodyHandover.receiptNumber)) insertCustodyHandover; at resource(records) persist(insert, argument(0, CustodyTill.receiptNumber)) openTill; } }
quantumStore valuesStore { attribute CustodyHandover.cash; attribute CustodyTill.cash; }
