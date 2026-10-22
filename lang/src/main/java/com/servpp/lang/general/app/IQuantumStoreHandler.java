package com.servpp.lang.general.app;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.models.app.AppQuantumStore;

public interface IQuantumStoreHandler extends IApplicationHandler {
    @Override
    default void enterQuantumStore(AppParser.QuantumStoreContext ctx) {
        AppQuantumStore store = new AppQuantumStore(ctx.Identifier().getText());
        getCurrentRuleBlock().addQuantumStore(store);
        getCurrentRuleBlock().setCurrentAnnotatable(store);
    }

    @Override
    default void exitQuantumStore(AppParser.QuantumStoreContext ctx) {
    }

    @Override
    default void enterQuantumStoreItem(AppParser.QuantumStoreItemContext ctx) {
        AppQuantumStore store = getLastElement(getCurrentRuleBlock().getAppQuantumStores());
        store.addAttributeId(ctx.qualifiedName().getText());
    }

    @Override
    default void exitQuantumStoreItem(AppParser.QuantumStoreItemContext ctx) {
    }
}
