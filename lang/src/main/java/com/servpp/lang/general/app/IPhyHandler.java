package com.servpp.lang.general.app;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.models.app.AppPhysicalBackend;
import com.servpp.lang.models.app.AppPhysicalBinding;
import com.servpp.lang.models.app.AppQuantumDefinition;
import com.servpp.lang.models.app.AppQuantumStoreAdapter;
import com.servpp.lang.models.app.AppRepositoryAdapter;
import com.servpp.lang.models.app.AppRemoteAdapter;

public interface IPhyHandler extends IApplicationHandler {
    @Override default void enterPhyDomainRoute(AppParser.PhyDomainRouteContext ctx) {
        String literal = ctx.StringLiteral().getText();
        var route = new com.servpp.lang.models.app.DomainRoute(ctx.Identifier().getText(),
                literal.substring(1, literal.length() - 1), ctx.IntegerLiteral() == null ? 5000 : Integer.parseInt(ctx.IntegerLiteral().getText()));
        if (route.timeoutMillis() <= 0) logSppError(ctx, "Domain route timeoutMillis must be positive");
        if (getCurrentRuleBlock().getDomainRoutes().putIfAbsent(route.domain(), route) != null)
            logSppError(ctx, "Duplicate domain route " + route.domain());
    }
    @Override default void exitPhyDomainRoute(AppParser.PhyDomainRouteContext ctx) { }

    @Override default void enterPhyNumericRules(AppParser.PhyNumericRulesContext ctx) { }
    @Override default void exitPhyNumericRules(AppParser.PhyNumericRulesContext ctx) { }
    @Override default void enterNumericTypeName(AppParser.NumericTypeNameContext ctx) { }
    @Override default void exitNumericTypeName(AppParser.NumericTypeNameContext ctx) { }
    @Override default void enterNumericSpecification(AppParser.NumericSpecificationContext ctx) { }
    @Override default void exitNumericSpecification(AppParser.NumericSpecificationContext ctx) { }
    @Override default void enterPhyNumericRule(AppParser.PhyNumericRuleContext ctx) {
        boolean global = "replace".equals(ctx.getChild(0).getText());
        var specs = ctx.numericSpecification();
        String target = global ? specs.get(0).getText() : ctx.qualifiedName(0).getText();
        String implementation = specs.get(specs.size() - 1).getText();
        String adapter = ctx.getText().contains("using") ? ctx.qualifiedName(ctx.qualifiedName().size() - 1).getText() : null;
        getCurrentRuleBlock().getNumericBindings().add(new com.servpp.lang.models.app.NumericBinding(target, implementation, adapter));
    }
    @Override default void exitPhyNumericRule(AppParser.PhyNumericRuleContext ctx) { }

    @Override
    default void enterPhyNumericBinding(AppParser.PhyNumericBindingContext ctx) {
        getCurrentRuleBlock().getNumericBindings().add(new com.servpp.lang.models.app.NumericBinding(
                ctx.getChild(1).getText(), ctx.getChild(3).getText(),
                ctx.getChildCount() > 5 ? ctx.getChild(5).getText() : null));
    }

    @Override
    default void exitPhyNumericBinding(AppParser.PhyNumericBindingContext ctx) {
    }

    @Override
    default void enterPhy(AppParser.PhyContext ctx) {
    }

    @Override
    default void exitPhy(AppParser.PhyContext ctx) {
    }

    @Override
    default void enterPhyItem(AppParser.PhyItemContext ctx) {
    }

    @Override
    default void exitPhyItem(AppParser.PhyItemContext ctx) {
    }

    @Override
    default void enterPhyGlobalConfig(AppParser.PhyGlobalConfigContext ctx) {
        getCurrentRuleBlock().putPhysicalConfiguration(ctx.phyConfigName().getText(),
                normalizeConfigValue(ctx.phyConfigValue().getText()));
    }

    @Override
    default void exitPhyGlobalConfig(AppParser.PhyGlobalConfigContext ctx) {
    }

    @Override
    default void enterPhyBackend(AppParser.PhyBackendContext ctx) {
        AppPhysicalBackend backend = new AppPhysicalBackend();
        backend.setName(normalizeBackendName(ctx.phyBackendName().getText()));
        getCurrentRuleBlock().addPhysicalBackend(backend);
    }

    @Override
    default void exitPhyBackend(AppParser.PhyBackendContext ctx) {
    }

    @Override
    default void enterPhyBackendName(AppParser.PhyBackendNameContext ctx) {
    }

    @Override
    default void exitPhyBackendName(AppParser.PhyBackendNameContext ctx) {
    }

    @Override
    default void enterPhyBackendItem(AppParser.PhyBackendItemContext ctx) {
    }

    @Override
    default void exitPhyBackendItem(AppParser.PhyBackendItemContext ctx) {
    }

    @Override
    default void enterPhyBackendAdapter(AppParser.PhyBackendAdapterContext ctx) {
        AppPhysicalBackend backend = getLastElement(getCurrentRuleBlock().getAppPhysicalBackends());
        backend.setAdapterName(normalizeBackendName(ctx.phyAdapterName().getText()));
    }

    @Override
    default void exitPhyBackendAdapter(AppParser.PhyBackendAdapterContext ctx) {
    }

    @Override
    default void enterPhyBinding(AppParser.PhyBindingContext ctx) {
        AppPhysicalBackend backend = getLastElement(getCurrentRuleBlock().getAppPhysicalBackends());
        AppPhysicalBinding.Kind kind = AppPhysicalBinding.Kind.valueOf(ctx.phyBindingKind().getText());
        String targetName = ctx.Identifier() == null ? AppPhysicalBinding.ALL_TARGETS : ctx.Identifier().getText();
        backend.addBinding(new AppPhysicalBinding(kind, targetName));
    }

    @Override
    default void exitPhyBinding(AppParser.PhyBindingContext ctx) {
    }

    @Override
    default void enterPhyBindingKind(AppParser.PhyBindingKindContext ctx) {
    }

    @Override
    default void exitPhyBindingKind(AppParser.PhyBindingKindContext ctx) {
    }

    @Override
    default void enterPhyBackendQuantumStore(AppParser.PhyBackendQuantumStoreContext ctx) {
        AppPhysicalBackend backend = getLastElement(getCurrentRuleBlock().getAppPhysicalBackends());
        backend.setQuantumStoreName(ctx.Identifier().getText());
    }

    @Override
    default void exitPhyBackendQuantumStore(AppParser.PhyBackendQuantumStoreContext ctx) {
    }

    @Override
    default void enterPhyConfig(AppParser.PhyConfigContext ctx) {
        if (ctx.getParent() instanceof AppParser.PhyTechnicalServiceContext) return;
        AppPhysicalBackend backend = getLastElement(getCurrentRuleBlock().getAppPhysicalBackends());
        backend.putConfiguration(ctx.phyConfigName().getText(), normalizeConfigValue(ctx.phyConfigValue().getText()));
    }

    @Override
    default void exitPhyConfig(AppParser.PhyConfigContext ctx) {
    }

    @Override
    default void enterPhyConfigName(AppParser.PhyConfigNameContext ctx) {
    }

    @Override
    default void exitPhyConfigName(AppParser.PhyConfigNameContext ctx) {
    }

    @Override
    default void enterPhyConfigValue(AppParser.PhyConfigValueContext ctx) {
    }

    @Override
    default void exitPhyConfigValue(AppParser.PhyConfigValueContext ctx) {
    }

    @Override
    default void enterPhyRemoteAdapter(AppParser.PhyRemoteAdapterContext ctx) {
        AppRemoteAdapter adapter = new AppRemoteAdapter();
        adapter.setName(ctx.Identifier().getText());
        getCurrentRuleBlock().addRemoteAdapter(adapter);
    }

    @Override
    default void exitPhyRemoteAdapter(AppParser.PhyRemoteAdapterContext ctx) {
    }

    @Override
    default void enterPhyRemoteAdapterName(AppParser.PhyRemoteAdapterNameContext ctx) {
        AppRemoteAdapter adapter = getLastElement(getCurrentRuleBlock().getAppRemoteAdapters());
        adapter.setAdapterName(normalizeBackendName(ctx.phyAdapterName().getText()));
    }

    @Override
    default void exitPhyRemoteAdapterName(AppParser.PhyRemoteAdapterNameContext ctx) {
    }

    @Override
    default void enterPhyAdapterName(AppParser.PhyAdapterNameContext ctx) {
    }

    @Override
    default void exitPhyAdapterName(AppParser.PhyAdapterNameContext ctx) {
    }

    @Override
    default void enterPhyRemoteConfig(AppParser.PhyRemoteConfigContext ctx) {
        AppRemoteAdapter adapter = getLastElement(getCurrentRuleBlock().getAppRemoteAdapters());
        adapter.putConfiguration(ctx.phyConfigName().getText(), normalizeConfigValue(ctx.phyConfigValue().getText()));
    }

    @Override
    default void exitPhyRemoteConfig(AppParser.PhyRemoteConfigContext ctx) {
    }

    @Override
    default void enterPhyRepositoryAdapter(AppParser.PhyRepositoryAdapterContext ctx) {
        AppRepositoryAdapter adapter = new AppRepositoryAdapter();
        adapter.setName(ctx.Identifier().getText());
        getCurrentRuleBlock().addRepositoryAdapter(adapter);
    }

    @Override
    default void exitPhyRepositoryAdapter(AppParser.PhyRepositoryAdapterContext ctx) {
    }

    @Override
    default void enterPhyRepositoryAdapterName(AppParser.PhyRepositoryAdapterNameContext ctx) {
        AppRepositoryAdapter adapter = getLastElement(getCurrentRuleBlock().getAppRepositoryAdapters());
        adapter.setAdapterName(normalizeBackendName(ctx.phyAdapterName().getText()));
    }

    @Override
    default void exitPhyRepositoryAdapterName(AppParser.PhyRepositoryAdapterNameContext ctx) {
    }

    @Override
    default void enterPhyRepositoryConfig(AppParser.PhyRepositoryConfigContext ctx) {
        AppRepositoryAdapter adapter = getLastElement(getCurrentRuleBlock().getAppRepositoryAdapters());
        adapter.putConfiguration(ctx.phyConfigName().getText(), normalizeConfigValue(ctx.phyConfigValue().getText()));
    }

    @Override
    default void exitPhyRepositoryConfig(AppParser.PhyRepositoryConfigContext ctx) {
    }

    @Override
    default void enterPhyQuantumStoreAdapter(AppParser.PhyQuantumStoreAdapterContext ctx) {
        AppQuantumStoreAdapter adapter = new AppQuantumStoreAdapter();
        adapter.setName(ctx.Identifier().getText());
        getCurrentRuleBlock().addQuantumStoreAdapter(adapter);
    }

    @Override
    default void exitPhyQuantumStoreAdapter(AppParser.PhyQuantumStoreAdapterContext ctx) {
    }

    @Override
    default void enterPhyQuantumStoreAdapterName(AppParser.PhyQuantumStoreAdapterNameContext ctx) {
        AppQuantumStoreAdapter adapter = getLastElement(getCurrentRuleBlock().getAppQuantumStoreAdapters());
        adapter.setAdapterName(normalizeBackendName(ctx.phyAdapterName().getText()));
    }

    @Override
    default void exitPhyQuantumStoreAdapterName(AppParser.PhyQuantumStoreAdapterNameContext ctx) {
    }

    @Override
    default void enterPhyQuantumStoreItem(AppParser.PhyQuantumStoreItemContext ctx) {
    }

    @Override
    default void exitPhyQuantumStoreItem(AppParser.PhyQuantumStoreItemContext ctx) {
    }

    @Override
    default void enterPhyQuantumStoreConfig(AppParser.PhyQuantumStoreConfigContext ctx) {
        AppQuantumStoreAdapter adapter = getLastElement(getCurrentRuleBlock().getAppQuantumStoreAdapters());
        adapter.putConfiguration(ctx.phyConfigName().getText(), normalizeConfigValue(ctx.phyConfigValue().getText()));
    }

    @Override
    default void exitPhyQuantumStoreConfig(AppParser.PhyQuantumStoreConfigContext ctx) {
    }

    @Override
    default void enterPhyQuantumDefinition(AppParser.PhyQuantumDefinitionContext ctx) {
        AppQuantumStoreAdapter adapter = getLastElement(getCurrentRuleBlock().getAppQuantumStoreAdapters());
        adapter.addQuantumDefinition(new AppQuantumDefinition(normalizeConfigValue(ctx.StringLiteral().getText())));
    }

    @Override
    default void exitPhyQuantumDefinition(AppParser.PhyQuantumDefinitionContext ctx) {
    }

    @Override
    default void enterPhyQuantumDefinitionItem(AppParser.PhyQuantumDefinitionItemContext ctx) {
    }

    @Override
    default void exitPhyQuantumDefinitionItem(AppParser.PhyQuantumDefinitionItemContext ctx) {
    }

    @Override
    default void enterPhyQuantumNumberType(AppParser.PhyQuantumNumberTypeContext ctx) {
        currentQuantumDefinition().setNumberType(ctx.Identifier().getText());
    }

    @Override
    default void exitPhyQuantumNumberType(AppParser.PhyQuantumNumberTypeContext ctx) {
    }

    @Override
    default void enterPhyQuantumMinValue(AppParser.PhyQuantumMinValueContext ctx) {
        currentQuantumDefinition().setMinValue(normalizeConfigValue(ctx.StringLiteral().getText()));
    }

    @Override
    default void exitPhyQuantumMinValue(AppParser.PhyQuantumMinValueContext ctx) {
    }

    @Override
    default void enterPhyQuantumMaxValue(AppParser.PhyQuantumMaxValueContext ctx) {
        currentQuantumDefinition().setMaxValue(normalizeConfigValue(ctx.StringLiteral().getText()));
    }

    @Override
    default void exitPhyQuantumMaxValue(AppParser.PhyQuantumMaxValueContext ctx) {
    }

    @Override
    default void enterPhyQuantumScale(AppParser.PhyQuantumScaleContext ctx) {
        currentQuantumDefinition().setScale(ctx.IntegerLiteral().getText());
    }

    @Override
    default void exitPhyQuantumScale(AppParser.PhyQuantumScaleContext ctx) {
    }

    default AppQuantumDefinition currentQuantumDefinition() {
        AppQuantumStoreAdapter adapter = getLastElement(getCurrentRuleBlock().getAppQuantumStoreAdapters());
        return getLastElement(adapter.getQuantumDefinitions());
    }

    default String normalizeBackendName(String name) {
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    default String normalizeConfigValue(String value) {
        return normalizeBackendName(value);
    }
}
