domain client;
application compose { environment testing { deploy client; deploy provider; transactions { mode: LOCAL; resource: records; ordinaryUpdates: PARALLEL; } } }
play Handover by provider.CustodyHandover { receiptNumber = receiptNumber; cash = cash; }
play Till by provider.CustodyTill { receiptNumber = receiptNumber; cash = cash; }
bind insertIntake to provider.insertCustodyHandover;
transactions { default at resource records; }
tables entities { table ACCEPTED(Accepted) { primarykey(Accepted.receiptNumber); } }
scope { local { at resource(records) insertIntake; at resource(records) persist(insert, argument(0, Accepted.receiptNumber)) insertAccepted; quantum resource(custody,Handover.cash,Till.cash) receive; quantum resource(custody,Handover.cash,Till.cash) skipReceive; } }
quantumStore valuesStore { attribute Accepted.funds; }
