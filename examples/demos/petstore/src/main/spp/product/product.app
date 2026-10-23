domain product;

application productService {
    environment testing {
        deploy product;
        quantum {
            system: UNIFIED_STORE;
            resource: shopLedger;
        }
    }
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

i18n {
    defaultLocale "en-US";
    supportedLocales "en-US", "zh-CN";
}

tables entities {
    table PRODUCT_FACTS(Product) {
        primarykey(Product.productCode);
    }
}

map PRODUCT_FACTS {
    description = String(200) description;
}

tables roles {
    table PRODUCT_CATALOGUE(CatalogProduct) {
        primarykey(CatalogProduct.sku);
    }
}

scope {
    local(in requestContext, out responseContext) {
        repository(argument(0, CatalogProduct.active), CatalogProduct[]) using shopRecords listCatalogue;
    }
}

web {
    start initializeProductDomain;
    expose reviseProduct;
}
