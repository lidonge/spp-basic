domain positions;

tables entities {
    table POSITIONS(Position) {
        primarykey(Position.account, Position.currency);
    }
}

scope {
    local {
        repository(argument(0, Position.account), argument(0, Position.currency), Position) using records readPosition;
    }
}
