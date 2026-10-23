domain inventory;

header requestContext {
    unique String globalSerialNumber;
    String callerId;
    String requestSerialNumber;
    String callMode;
    String callOrigin;
}

header responseContext {
    String status(Default = "OK");
}

tables roles {
    table SHOP_STOCK_SUPPLY(StockSupply) {
        primarykey(StockSupply.goods.sku);
    }
    table SHOP_STOCK(StockLine) {
        primarykey(StockLine.goods.sku);
        performers inline;
    }
}

scope {
    local(in requestContext, out responseContext) {
    }
}
