domain petshop;

application petstore {
    environment develop {
        deploy customer;
        deploy product;
        deploy inventory;
        deploy petshop;
        transactions {
            mode: LOCAL;
            resource: shopRecordsResource;
            ordinaryUpdates: PARALLEL;
        }
    }
    environment testing {
        deploy inventory;
        deploy petshop;
        quantum {
            system: UNIFIED_STORE;
            resource: shopLedger;
        }
    }
}

requests {
    idempotency retention 3 days;
    duplicateWindow 5 seconds;
}

bind importCatalogue to product.listCatalogue;
bind localizeCatalog to product.localizeProductLabels;
bind initializeInventory to inventory.initializeInventory;
i18n {
    defaultLocale "en-US";
    supportedLocales "en-US", "zh-CN";
}

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

tables entities {
    table SHOP_RECEIPTS(Receipt) {
        primarykey(Receipt.receiptNumber);
        auto Receipt.receiptNumber prefix "RECEIPT";
        foreignkey(Receipt.buyer) references Buyer;
        foreignkey(Receipt.order) references ShopOrder;
    }
}

tables roles {
    table SHOP_CART_LINES(CartLine) {
        primarykey notnull String cartLineId;
        identity independent;
        performers in SHOP_CART_LINE_PLAYERS;
    }
    table SHOP_ORDER_LINES(OrderLine) {
        primarykey(OrderLine.lineNumber);
        auto OrderLine.lineNumber prefix "ORDER-LINE";
        foreignkey(OrderLine.goods) references Goods;
        snapshot(OrderLine.name, OrderLine.unitPrice);
    }

    table SHOP_CARTS(Cart) {
        primarykey(Cart.buyer);
        foreignkey(Cart.buyer) references Buyer;
    }
    table SHOP_SETTLEMENT_ACCOUNTS(SettlementAccount) {
        primarykey(SettlementAccount.accountNumber);
    }
    table SHOP_GOODS(Goods) {
        primarykey(Goods.sku);
        current(Goods.name);
    }
    table SHOP_BUYERS(Buyer) {
        primarykey(Buyer.membershipNumber);
    }

    table SHOP_MERCHANTS(Merchant) {
        primarykey notnull String merchantRowId;
    }
}

tables contracts {
    table SHOP_RESERVATIONS(PetReservationAgreement) {
        primarykey(PetReservationAgreement.reservationNumber);
        auto PetReservationAgreement.reservationNumber prefix "RESERVATION";
        foreignkey(PetReservationAgreement.buyer) references Buyer;
        foreignkey(PetReservationAgreement.goods) references Goods;
    }
    table SHOP_ORDERS(ShopOrder) {
        primarykey(ShopOrder.orderNumber);
        auto ShopOrder.orderNumber prefix "ORDER";
        foreignkey(ShopOrder.buyer) references Buyer;
        index buyerOrders(ShopOrder.buyer);
    }
}

scope {
    local(in requestContext, out responseContext) {
        repository(argument(0, Goods.active), Goods[]) using shopRecords listProducts;
        duplicate key(
            Buyer.membershipNumber,
            Goods.sku
        ) facts(PetReservationAgreement.quantity) window 10 seconds reserveShopPet;

        duplicate key(Buyer.membershipNumber, Goods.sku) facts(CartAdditionAgreement.quantity) addCartItem;
        duplicate key(Buyer.membershipNumber) facts(ProfileAgreement) updateShopCustomer;
        duplicate key(
            Buyer.membershipNumber,
            ShopOrder.orderNumber
        ) facts(ShopOrder.orderNumber) deliverShopOrder;
        duplicate key(Buyer.membershipNumber) facts(Buyer.membershipNumber) clearShopCart;

        duplicate key(
            Buyer.membershipNumber,
            ShopOrder.orderNumber
        ) facts(ReturnRequestAgreement.reason) requestShopReturn;
        duplicate key(
            Buyer.membershipNumber,
            ShopOrder.orderNumber
        ) facts(PaidCancellationAgreement.reason) cancelPaidShopOrder;
        duplicate key(
            Buyer.membershipNumber,
            ShopOrder.orderNumber
        ) facts(ShopOrder.orderNumber) approveShopReturn;
        duplicate key(Buyer.membershipNumber, Goods.sku) facts(Goods.sku) removeCartItem;
        duplicate key(
            Buyer.membershipNumber,
            ShopOrder.orderNumber
        ) facts(ShipmentAgreement.tracking) shipShopOrder;
        duplicate key(
            Buyer.membershipNumber,
            Goods.sku
        ) facts(CartQuantityAgreement.quantity) setCartQuantity;
        duplicate key(Buyer.membershipNumber) facts(Buyer) registerShopCustomer;
        duplicate key(Buyer.membershipNumber) facts(WalletFundingAgreement.amount) fundShopWallet;
        duplicate key(Goods.sku) facts(StockReceiptAgreement.quantity) receiveShopStock;

        duplicate key(
            Buyer.membershipNumber,
            ShopOrder.orderNumber
        ) facts(UnpaidCancellationAgreement.reason) cancelUnpaidShopOrder;
        duplicate key(
            Buyer.membershipNumber,
            ShopOrder.orderNumber
        ) facts(ShopOrder.orderNumber) payShopOrder;

        duplicate key(Buyer.membershipNumber) facts(Buyer.membershipNumber) placeShopOrder;
    }
}

web {
    start initializeStore;
    expose browseShopProducts;
    expose readShopCustomer;
    expose readShopCart;
    expose readShopOrders;
    expose readShopInventory;
    expose readShopMerchant;
    expose registerShopCustomer;
    expose fundShopWallet;
    expose receiveShopStock;
    expose updateShopCustomer;
    expose addCartItem;
    expose setCartQuantity;
    expose removeCartItem;
    expose clearShopCart;
    expose placeShopOrder;
    expose payShopOrder;
    expose shipShopOrder;
    expose deliverShopOrder;
    expose cancelUnpaidShopOrder;
    expose cancelPaidShopOrder;
    expose requestShopReturn;
    expose approveShopReturn;
    expose previewShopOrder;
    expose reserveShopPet;
    expose readShopReservation;
}
