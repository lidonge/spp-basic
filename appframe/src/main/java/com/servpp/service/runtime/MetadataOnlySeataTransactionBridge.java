package com.servpp.service.runtime;

public class MetadataOnlySeataTransactionBridge implements SeataTransactionBridge {
    @Override
    public String bridgeType() {
        return "metadataOnly";
    }
}
