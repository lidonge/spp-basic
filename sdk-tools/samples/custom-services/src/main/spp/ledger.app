domain ledger;

application basicLedger {
    environment develop {
        deploy ledger;
        transactions {
            mode: LOCAL;
            resource: ledgerResource;
        }
    }
}

tables entities {
    table LEDGER_ENTRIES(Entry) {
        primarykey(Entry.code);
    }
}

transactions {
    default at resource ledgerResource;
}

scope {
    local {
        repository(argument(0, Entry.code), Entry) using records findEntry;
    }
}

technical services {
    callCounter;
}

intercept services {
    before: callCounter;
    after: callCounter;
    failure: callCounter;
}
