domain physicalarray;
tables entities { table PHYSICAL_ENTRIES(Entry) { primarykey(Entry.itemCode); } }
transactions { default at resource cashVoucherLaneA; }
application arrayExample {
    environment testing {
        deploy physicalarray;
        transactions { mode: LOCAL; resource: cashVoucherLaneA; }
    }
}
scope { local {
    at resource(cashVoucherLaneA) persist(insert, argument(0, Entry.itemCode)) insertEntry;
    repository(argument(0, Entry.sector), rows) using cashVoucherLaneARecords listEntries;
} }
