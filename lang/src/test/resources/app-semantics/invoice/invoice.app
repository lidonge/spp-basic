domain invoice;

header baseIn{
    notnull String caller;
    unique String globalSerialNumber;
    unique String messageId;
}

header baseOut{
    notnull String appId(Default = "invoice");
    notnull String status(Default = "OK");
}

header stockHeaderIn extends baseIn{
    notnull String channelId = caller;
}

@column(varchar_precision="80",nullable="true",
    decimal_precision="19",decimal_scale="2",
    type_boolean="INTEGER",type_String="VARCHAR")
@insideEntity(namingType="snake_case")
tables entities{
    @insideEntity(namingType="lowercase",dupNamingType="pascallowercase")
    table BASE_ACCOUNT(BaseAccount){
        primarykey String accountId;
    }
}

@column(varchar_precision="80",nullable="true",
    decimal_precision="19",decimal_scale="2",
    type_boolean="INTEGER",type_String="VARCHAR")
@insideEntity(namingType="snake_case")
tables roles{
    table ACCOUNT(Account){
        primarykey String accountId;
    }
    table GOODS_STOCK(GoodsStock){
        primarykey String stockId;
    }
    table GOODS(Goods){
        primarykey String skuId;
    }
    table AMOUNT(Amount){
        primarykey String amountId;
    }
}

@column(varchar_precision="80",nullable="true",
    decimal_precision="19",decimal_scale="2",
    type_boolean="INTEGER",type_String="VARCHAR")
@insideEntity(namingType="snake_case")
tables contracts{
    table INVOICE(Invoice){
        primarykey int invoiceId;
        foreignkey(Invoice.buyer);
        foreignkey(Invoice.seller);
        foreignkey(Invoice.goods);
        foreignkey(Invoice.stock);
        foreignkey(Invoice.amount);
    }
}

scope{
    local (in baseIn, out baseOut){
        _default;
        queryAccount;
        decAccount;
        incAccount;
        makeInvoice;
    }
    remote (in baseIn, out baseOut){
        calculateAmount;
    }
    remote (in stockHeaderIn, out baseOut){
        async decStock;
        checkStock;
        updateStocks;
    }
}

services{
    decAccount{
        account.balance -= amount.amount;
    }
}

@parameter(path="{accountno}/queryAccount",mapper="accountno:Account.id"){queryAccount;}

@Bian{
    Customer;
    @CR{Invoice;}
    @BQ{
        Stock;
        Amount;
    }
}
