domain reserves;

tables entities {
    table RESERVES(Reserve) {
        primarykey(Reserve.denomination, Reserve.referenceCode);
    }
}
