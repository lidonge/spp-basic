domain positions;

tables entities {
    table POSITIONS(Position) {
        primarykey(Position.account, Position.currency);
    }
}
