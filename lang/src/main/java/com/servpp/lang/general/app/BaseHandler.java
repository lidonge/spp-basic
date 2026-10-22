package com.servpp.lang.general.app;

import com.servpp.lang.antlr.AppParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;


/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public abstract class BaseHandler implements IHeaderHandler, IPrimaryHandler,
        IMapperHandler, IScopeHandler, IServicesHandler, IAnnotateHandler, ITablesHandler, IPhyHandler,
        IQuantumStoreHandler {
    @Override public void enterTechnicalServices(AppParser.TechnicalServicesContext ctx) {
        var names = getCurrentRuleBlock().getTechnicalServices().names();
        // The first Identifier is the contextual technical keyword.
        for (int index = 1; index < ctx.Identifier().size(); index++) {
            String name = ctx.Identifier(index).getText();
            if (!names.add(name)) logSppError(ctx, "Duplicate technical service " + name);
        }
    }
    @Override public void exitTechnicalServices(AppParser.TechnicalServicesContext ctx) { }
    @Override public void enterServiceInterception(AppParser.ServiceInterceptionContext ctx) {
        var targets = ctx.interceptionTargets().getText().equals("services") ? java.util.List.of("services")
                : ctx.interceptionTargets().Identifier().stream().map(node -> node.getText()).toList();
        for (var hook : ctx.interceptionHook()) getCurrentRuleBlock().getTechnicalServices().hooks().add(
                new com.servpp.lang.models.app.TechnicalServices.Hook(targets, hook.interceptionPhase().getText(),
                        hook.Identifier().stream().map(node -> node.getText()).toList()));
    }
    @Override public void exitServiceInterception(AppParser.ServiceInterceptionContext ctx) { }
    @Override public void enterInterceptionTargets(AppParser.InterceptionTargetsContext ctx) { }
    @Override public void exitInterceptionTargets(AppParser.InterceptionTargetsContext ctx) { }
    @Override public void enterInterceptionHook(AppParser.InterceptionHookContext ctx) { }
    @Override public void exitInterceptionHook(AppParser.InterceptionHookContext ctx) { }
    @Override public void enterInterceptionPhase(AppParser.InterceptionPhaseContext ctx) { }
    @Override public void exitInterceptionPhase(AppParser.InterceptionPhaseContext ctx) { }
    @Override public void enterPhyTechnicalService(AppParser.PhyTechnicalServiceContext ctx) {
        String name = ctx.Identifier(2).getText();
        var config = new java.util.LinkedHashMap<String,String>();
        for (var item : ctx.phyConfig()) {
            String key = item.phyConfigName().getText();
            if (config.putIfAbsent(key, normalizeConfigValue(item.phyConfigValue().getText())) != null)
                logSppError(ctx, "Duplicate technical service config " + name + "." + key);
        }
        var implementation = new com.servpp.lang.models.app.TechnicalServices.Implementation(
                normalizeConfigValue(ctx.phyTechnicalImplementation().StringLiteral().getText()), java.util.Map.copyOf(config));
        if (getCurrentRuleBlock().getTechnicalServices().implementations().putIfAbsent(name, implementation) != null)
            logSppError(ctx, "Duplicate technical implementation " + name);
    }
    @Override public void exitPhyTechnicalService(AppParser.PhyTechnicalServiceContext ctx) { }
    @Override public void enterPhyTechnicalImplementation(AppParser.PhyTechnicalImplementationContext ctx) { }
    @Override public void exitPhyTechnicalImplementation(AppParser.PhyTechnicalImplementationContext ctx) { }
    @Override public void enterInternationalization(AppParser.InternationalizationContext ctx) {
        var locales = ctx.StringLiteral().stream().map(node -> normalizeConfigValue(node.getText())).toList();
        if (getCurrentRuleBlock().getDefaultLocale() != null) logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), "Duplicate i18n declaration");
        getCurrentRuleBlock().setDefaultLocale(locales.get(0));
        getCurrentRuleBlock().setSupportedLocales(locales.subList(1, locales.size()));
    }
    @Override public void exitInternationalization(AppParser.InternationalizationContext ctx) { }
    @Override public void enterPhyTranslations(AppParser.PhyTranslationsContext ctx) {
        String locale = normalizeConfigValue(ctx.StringLiteral().getText());
        var translations = getCurrentRuleBlock().getConstantTranslations().computeIfAbsent(locale, key -> new java.util.LinkedHashMap<>());
        for (int index = 0; index < ctx.Identifier().size(); index++) {
            String name = ctx.Identifier(index).getText();
            if (translations.putIfAbsent(name, ctx.phyConfigValue(index).getText()) != null)
                logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), "Duplicate constant translation " + locale + "." + name);
        }
    }
    @Override public void exitPhyTranslations(AppParser.PhyTranslationsContext ctx) { }

    @Override public void enterPhyI18nResources(AppParser.PhyI18nResourcesContext ctx) {
        String location = normalizeConfigValue(ctx.StringLiteral(0).getText());
        try {
            var directory = com.servpp.lang.CompilationResources.resolve(getAntlrFile(),location);
            if (!java.nio.file.Files.isDirectory(directory)) throw new IllegalArgumentException("Language resource directory does not exist: " + location);
            java.util.List<java.nio.file.Path> files;
            try (var paths = java.nio.file.Files.list(directory)) {
                files = paths.filter(path -> path.getFileName().toString().endsWith(".properties")).sorted().toList();
            }
            if (files.isEmpty()) throw new IllegalArgumentException("Language resource directory is empty: " + location);
            var locales = new java.util.ArrayList<String>();
            for (var file : files) {
                String filename = file.getFileName().toString();
                String locale = filename.substring(0, filename.length() - ".properties".length());
                locales.add(locale);
                var values = new java.util.Properties() {
                    @Override public synchronized Object put(Object key, Object value) {
                        if (containsKey(key)) throw new IllegalArgumentException("Duplicate translation " + locale + "." + key);
                        return super.put(key, value);
                    }
                };
                try (var reader = java.nio.file.Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) { values.load(reader); }
                var target = getCurrentRuleBlock().getConstantTranslations().computeIfAbsent(locale, key -> new java.util.LinkedHashMap<>());
                for (String name : values.stringPropertyNames()) {
                    String value = quoteTranslation(values.getProperty(name));
                    if (target.putIfAbsent(name, value) != null) throw new IllegalArgumentException("Duplicate translation " + locale + "." + name);
                }
            }
            if (getCurrentRuleBlock().getSupportedLocales().isEmpty()) getCurrentRuleBlock().setSupportedLocales(locales);
            if (ctx.StringLiteral().size() > 1) {
                String locale = normalizeConfigValue(ctx.StringLiteral(1).getText());
                if (getCurrentRuleBlock().getDefaultLocale() != null && !locale.equals(getCurrentRuleBlock().getDefaultLocale()))
                    throw new IllegalArgumentException("Conflicting APP/PHY default locale");
                getCurrentRuleBlock().setDefaultLocale(locale);
            }
        } catch (java.io.IOException | IllegalArgumentException failure) { logSppError(ctx, failure.getMessage()); }
    }
    @Override public void exitPhyI18nResources(AppParser.PhyI18nResourcesContext ctx) { }
    private String quoteTranslation(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            if (character == '\\' || character == '"') result.append('\\').append(character);
            else if (character < 32) result.append(String.format("\\u%04x", (int)character));
            else result.append(character);
        }
        return result.append('"').toString();
    }

    @Override public void enterPropertyTrackingPolicy(AppParser.PropertyTrackingPolicyContext ctx) {
        try { getCurrentRuleBlock().setPropertyTracking(Boolean.parseBoolean(ctx.BooleanLiteral().getText())); }
        catch (IllegalArgumentException failure) { logSppError(ctx, failure.getMessage()); }
    }
    @Override public void exitPropertyTrackingPolicy(AppParser.PropertyTrackingPolicyContext ctx) { }
    @Override public void enterSystemServices(AppParser.SystemServicesContext ctx) { }
    @Override public void exitSystemServices(AppParser.SystemServicesContext ctx) { }
    @Override public void enterSystemServiceSwitch(AppParser.SystemServiceSwitchContext ctx) {
        try { getCurrentRuleBlock().setSystemServiceEnabled(ctx.getChild(0).getText(), Boolean.parseBoolean(ctx.BooleanLiteral().getText())); }
        catch (IllegalArgumentException failure) { logSppError(ctx, failure.getMessage()); }
    }
    @Override public void exitSystemServiceSwitch(AppParser.SystemServiceSwitchContext ctx) { }
    private com.servpp.lang.models.app.DeploymentEnvironment currentEnvironment;
    private String currentApplication;
    @Override public void enterTransactionMode(AppParser.TransactionModeContext ctx) { }
    @Override public void exitTransactionMode(AppParser.TransactionModeContext ctx) { }
    @Override public void enterTransactionPolicy(AppParser.TransactionPolicyContext ctx) {
        if (ctx.Identifier() == null) return;
        String mode = ctx.transactionMode() == null ? ctx.getChild(3).getText().toUpperCase(java.util.Locale.ROOT)
                : normalizeConfigValue(ctx.transactionMode().getText());
        if (!java.util.Set.of("LOCAL", "XA", "SEATA_AT", "SAGA", "AT").contains(mode)) {
            logSppError(ctx, "Unknown transaction mode " + mode + "; use LOCAL, XA, SEATA_AT or SAGA."); return;
        }
        String resource = ctx.Identifier().getText();
        try {
            if (currentEnvironment != null) currentEnvironment.transactions(mode, resource);
            else {
                if (getCurrentRuleBlock().hasTransactionPolicy()) logSppError(ctx, "Duplicate transactions policy");
                getCurrentRuleBlock().setDefaultUpdateTransaction(mode, resource);
            }
        } catch (IllegalArgumentException failure) { logSppError(ctx, failure.getMessage()); }
    }
    @Override public void exitTransactionPolicy(AppParser.TransactionPolicyContext ctx) { }
    @Override public void enterOrdinaryUpdateScheduling(AppParser.OrdinaryUpdateSchedulingContext ctx) { }
    @Override public void exitOrdinaryUpdateScheduling(AppParser.OrdinaryUpdateSchedulingContext ctx) {
        String value = ctx.Identifier().getText();
        if (!java.util.Set.of("SERIAL", "PARALLEL").contains(value)) {
            logSppError(ctx, "ordinaryUpdates requires SERIAL or PARALLEL"); return;
        }
        try {
            if (currentEnvironment != null) currentEnvironment.ordinaryUpdates(value);
            else getCurrentRuleBlock().setOrdinaryUpdates(value);
        } catch (IllegalArgumentException failure) { logSppError(ctx, failure.getMessage()); }
    }
    @Override public void enterApplicationDeployment(AppParser.ApplicationDeploymentContext ctx) {
        currentApplication = ctx.Identifier().getText();
        if (getCurrentRuleBlock().getDeploymentEnvironments().putIfAbsent(currentApplication, new java.util.LinkedHashMap<>()) != null)
            logSppError(ctx, "Duplicate application " + currentApplication);
        if (ctx.deploymentEnvironment().isEmpty()) getCurrentRuleBlock().getApplicationDeployments().put(currentApplication, new java.util.ArrayList<>());
    }
    @Override public void enterDeployDomain(AppParser.DeployDomainContext ctx) {
        if (currentEnvironment == null) {
            var domains = getCurrentRuleBlock().getApplicationDeployments().get(currentApplication);
            if (domains == null) logSppError(ctx, "Put deploy declarations inside an environment when using environment profiles");
            else domains.add(ctx.Identifier().getText());
        }
        else currentEnvironment.getDomains().add(ctx.Identifier().getText());
    }
    @Override public void exitDeployDomain(AppParser.DeployDomainContext ctx) { }
    @Override public void enterDeploymentEnvironment(AppParser.DeploymentEnvironmentContext ctx) {
        currentEnvironment = new com.servpp.lang.models.app.DeploymentEnvironment(ctx.Identifier().getText());
        if (getCurrentRuleBlock().getDeploymentEnvironments().get(currentApplication).putIfAbsent(currentEnvironment.getName(), currentEnvironment) != null)
            logSppError(ctx, "Duplicate deployment environment " + currentEnvironment.getName());
    }
    @Override public void exitDeploymentEnvironment(AppParser.DeploymentEnvironmentContext ctx) { currentEnvironment = null; }
    @Override public void enterDeploymentQuantum(AppParser.DeploymentQuantumContext ctx) {
        try { currentEnvironment.quantum(ctx.Identifier(0).getText(), ctx.Identifier(1).getText()); }
        catch (IllegalArgumentException failure) { logSppError(ctx, failure.getMessage()); }
    }
    @Override public void exitDeploymentQuantum(AppParser.DeploymentQuantumContext ctx) { }
    @Override public void enterPhyEnvironment(AppParser.PhyEnvironmentContext ctx) { }
    @Override public void exitPhyEnvironment(AppParser.PhyEnvironmentContext ctx) { }
    @Override public void exitApplicationDeployment(AppParser.ApplicationDeploymentContext ctx) { }
    @Override public void exitLocalServiceBinding(AppParser.LocalServiceBindingContext ctx) { }
    @Override public void enterLocalParticipantBinding(AppParser.LocalParticipantBindingContext ctx) { }
    @Override public void exitLocalParticipantBinding(AppParser.LocalParticipantBindingContext ctx) { }

    @Override public void enterLocalServiceBinding(AppParser.LocalServiceBindingContext ctx) {
        var binding = new com.servpp.lang.models.app.LocalServiceBinding(ctx.Identifier().getText(), ctx.qualifiedName().getText());
        binding.setRoleBinding(ctx.localParticipantBinding().isEmpty());
        for (var participant : ctx.localParticipantBinding()) {
            int index = Integer.parseInt(participant.IntegerLiteral().getText());
            var facts = new java.util.ArrayList<String>();
            var keys = new java.util.ArrayList<String>();
            boolean identity = false;
            for (int child = 0; child < participant.getChildCount(); child++) {
                var node = participant.getChild(child);
                if ("identity".equals(node.getText())) identity = true;
                if (identity && node instanceof AppParser.QualifiedNameContext) keys.add(node.getText());
                else if (!identity && node instanceof org.antlr.v4.runtime.tree.TerminalNode terminal && terminal.getSymbol().getType() == AppParser.Identifier)
                    facts.add(node.getText());
            }
            binding.getParticipantIdentities().put(index, java.util.List.copyOf(keys));
            if (participant.getText().endsWith("existing;")) binding.getExistingCollections().add(index);
            if (binding.getParticipantFacts().putIfAbsent(index, facts) != null)
                logSppError(participant.getStart().getLine(), participant.getStart().getCharPositionInLine(), "Duplicate binding participant " + index);
        }
        getCurrentRuleBlock().getLocalServiceBindings().add(binding);
    }

    @Override public void enterRequestPolicy(AppParser.RequestPolicyContext ctx) {}
    @Override public void enterRequestDuration(AppParser.RequestDurationContext ctx) {}
    @Override public void exitRequestDuration(AppParser.RequestDurationContext ctx) {}
    @Override public void exitRequestPolicy(AppParser.RequestPolicyContext ctx) {
        for (int i = 2; i < ctx.getChildCount() - 1;) {
            boolean retention = "idempotency".equals(ctx.getChild(i).getText());
            String key = retention ? "requestIdempotencyRetention" : "requestDuplicateWindow";
            var declaration = (AppParser.RequestDurationContext) ctx.getChild(i + (retention ? 2 : 1));
            try {
                String value = RequestDuration.value(declaration);
                if (getCurrentRuleBlock().getRequestPolicies().putIfAbsent(key, value) != null)
                    logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), "Duplicate APP request policy " + key);
            } catch (RuntimeException failure) {
                logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), "APP request policy requires a positive duration with a supported unit (or a legacy positive ISO-8601 duration): " + declaration.getText());
            }
            i += retention ? 4 : 3;
        }
    }
    @Override public void enterWeb(AppParser.WebContext ctx) {}
    @Override public void exitWeb(AppParser.WebContext ctx) {}
    @Override public void enterWebEntry(AppParser.WebEntryContext ctx) {
        String action = ctx.Identifier(0).getText(), name = ctx.Identifier(1).getText();
        if ("expose".equals(action)) getCurrentRuleBlock().getWebServiceNames().add(name);
        else if ("start".equals(action)) getCurrentRuleBlock().getWebStartupNames().add(name);
        else getCurrentRuleBlock().getInvalidWebActions().add(action);
    }
    @Override public void exitWebEntry(AppParser.WebEntryContext ctx) {}

    @Override
    public void enterExamples(AppParser.ExamplesContext ctx) {}
    @Override
    public void exitExamples(AppParser.ExamplesContext ctx) {}
    @Override
    public void enterExampleRun(AppParser.ExampleRunContext ctx) {
        if (!"run".equals(ctx.exampleAction().getText())) {
            logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), "APP example action must be run.");
        }
        getCurrentRuleBlock().getExampleServiceNames().add(ctx.Identifier().getText());
        if (ctx.exampleJdbcFault() != null) {
            var jdbc = ctx.exampleJdbcFault();
            if (!"jdbcFault".equals(jdbc.jdbcFaultAction().getText())) {
                logSppError(jdbc.getStart().getLine(), jdbc.getStart().getCharPositionInLine(), "APP example modifier must be jdbcFault.");
            }
            getCurrentRuleBlock().getExampleJdbcFaults().put(ctx.Identifier().getText(),
                new com.servpp.lang.models.app.ExampleJdbcFault(jdbc.Identifier().getText(), jdbc.jdbcFaultPoint().getText()));
        }
        if (ctx.exampleRestart() != null) {
            var restart = ctx.exampleRestart();
            if (!"restart".equals(restart.restartAction().getText())) {
                logSppError(restart.getStart().getLine(), restart.getStart().getCharPositionInLine(), "APP example modifier must be restart.");
            }
            getCurrentRuleBlock().getExampleRestarts().put(ctx.Identifier().getText(),
                new com.servpp.lang.models.app.ExampleRestart(restart.Identifier().getText(),
                    restart.qualifiedName() != null ? restart.qualifiedName().getText() : normalizeConfigValue(restart.StringLiteral().getText())));
        }
        if (ctx.exampleBarrier() != null) {
            var barrier = ctx.exampleBarrier();
            if (!"barrier".equals(barrier.barrierAction().getText())) {
                logSppError(barrier.getStart().getLine(), barrier.getStart().getCharPositionInLine(), "APP example modifier must be barrier.");
            }
            int parties;
            try { parties = Integer.parseInt(barrier.IntegerLiteral().getText()); }
            catch (NumberFormatException invalid) { parties = -1; }
            getCurrentRuleBlock().getExampleBarriers().put(ctx.Identifier().getText(),
                new com.servpp.lang.models.app.ExampleBarrier(barrier.Identifier().getText(), parties));
        }
        if (ctx.exampleFault() != null) {
            var fault = ctx.exampleFault();
            if (!"fault".equals(fault.faultAction().getText())) {
                logSppError(fault.getStart().getLine(), fault.getStart().getCharPositionInLine(), "APP example modifier must be fault.");
            }
            int times = 1;
            if (fault.faultRepetition() != null) {
                if (!"times".equals(fault.faultRepetition().Identifier().getText())) {
                    logSppError(fault.getStart().getLine(), fault.getStart().getCharPositionInLine(), "APP example fault repetition must use times.");
                }
                try { times = Integer.parseInt(fault.faultRepetition().IntegerLiteral().getText()); }
                catch (NumberFormatException invalid) { times = -1; }
            }
            getCurrentRuleBlock().getExampleFaults().put(ctx.Identifier().getText(),
                    new com.servpp.lang.models.app.ExampleFault(fault.Identifier(0).getText(), fault.faultTiming().getText(),
                        times, fault.Identifier().size() > 1 ? fault.Identifier(1).getText() : null));
        }
    }
    @Override
    public void exitExampleRun(AppParser.ExampleRunContext ctx) {}
    @Override
    public void enterExampleAction(AppParser.ExampleActionContext ctx) {}
    @Override
    public void exitExampleAction(AppParser.ExampleActionContext ctx) {}

    @Override public void enterExampleFault(AppParser.ExampleFaultContext ctx) {}
    @Override public void exitExampleFault(AppParser.ExampleFaultContext ctx) {}
    @Override public void enterFaultRepetition(AppParser.FaultRepetitionContext ctx) {}
    @Override public void exitFaultRepetition(AppParser.FaultRepetitionContext ctx) {}
    @Override public void enterFaultAction(AppParser.FaultActionContext ctx) {}
    @Override public void exitFaultAction(AppParser.FaultActionContext ctx) {}
    @Override public void enterFaultTiming(AppParser.FaultTimingContext ctx) {}
    @Override public void exitFaultTiming(AppParser.FaultTimingContext ctx) {}
    @Override public void enterExampleBarrier(AppParser.ExampleBarrierContext ctx) {}
    @Override public void exitExampleBarrier(AppParser.ExampleBarrierContext ctx) {}
    @Override public void enterBarrierAction(AppParser.BarrierActionContext ctx) {}
    @Override public void exitBarrierAction(AppParser.BarrierActionContext ctx) {}
    @Override public void enterExampleRestart(AppParser.ExampleRestartContext ctx) {}
    @Override public void exitExampleRestart(AppParser.ExampleRestartContext ctx) {}
    @Override public void enterRestartAction(AppParser.RestartActionContext ctx) {}
    @Override public void exitRestartAction(AppParser.RestartActionContext ctx) {}
    @Override public void enterExampleJdbcFault(AppParser.ExampleJdbcFaultContext ctx) {}
    @Override public void exitExampleJdbcFault(AppParser.ExampleJdbcFaultContext ctx) {}
    @Override public void enterJdbcFaultAction(AppParser.JdbcFaultActionContext ctx) {}
    @Override public void exitJdbcFaultAction(AppParser.JdbcFaultActionContext ctx) {}
    @Override public void enterJdbcFaultPoint(AppParser.JdbcFaultPointContext ctx) {}
    @Override public void exitJdbcFaultPoint(AppParser.JdbcFaultPointContext ctx) {}

    @Override
    public void exitMapParameterIdentifier(AppParser.MapParameterIdentifierContext ctx) {

    }

    @Override
    public void enterQualifiedName(AppParser.QualifiedNameContext ctx) {

    }

    @Override
    public void exitQualifiedName(AppParser.QualifiedNameContext ctx) {

    }

    @Override
    public void exitRemoteBody(AppParser.RemoteBodyContext ctx) {

    }

    @Override
    public void enterDefaultValue(AppParser.DefaultValueContext ctx) {

    }

    @Override
    public void exitDefaultValue(AppParser.DefaultValueContext ctx) {

    }

    @Override
    public void exitTransModifier(AppParser.TransModifierContext ctx) {

    }

    @Override
    public void exitPrecision(AppParser.PrecisionContext ctx) {

    }

    @Override
    public void exitHeaderFieldDefine(AppParser.HeaderFieldDefineContext ctx) {

    }

    @Override
    public void enterProgram(AppParser.ProgramContext ctx) {

    }

    @Override
    public void exitProgram(AppParser.ProgramContext ctx) {

    }

    @Override
    public void exitLocalBody(AppParser.LocalBodyContext ctx) {

    }

    @Override
    public void exitHeaderFieldDefineIdentifier(AppParser.HeaderFieldDefineIdentifierContext ctx) {

    }

    @Override
    public void exitPrimaryQualified(AppParser.PrimaryQualifiedContext ctx) {

    }

    @Override
    public void exitExtendsHeaders(AppParser.ExtendsHeadersContext ctx) {

    }

    @Override
    public void exitSqlTypePrimitiveType(AppParser.SqlTypePrimitiveTypeContext ctx) {

    }

    @Override
    public void exitAsycnModifier(AppParser.AsycnModifierContext ctx) {

    }

    @Override
    public void enterServiceDomain(AppParser.ServiceDomainContext ctx) {

    }

    @Override
    public void exitServiceDomain(AppParser.ServiceDomainContext ctx) {

    }

    @Override
    public void enterServiceStatement(AppParser.ServiceStatementContext ctx) {

    }

    @Override
    public void exitServiceStatement(AppParser.ServiceStatementContext ctx) {

    }

    @Override
    public void enterDomainName(AppParser.DomainNameContext ctx) {

    }

    @Override
    public void exitDomainName(AppParser.DomainNameContext ctx) {

    }

    @Override
    public void enterMapParameters(AppParser.MapParametersContext ctx) {

    }

    @Override
    public void exitMapParameters(AppParser.MapParametersContext ctx) {

    }

    @Override
    public void exitHeader(AppParser.HeaderContext ctx) {

    }

    @Override
    public void exitHeaderFieldModifierNotNull(AppParser.HeaderFieldModifierNotNullContext ctx) {

    }

    @Override
    public void enterPrimary(AppParser.PrimaryContext ctx) {

    }

    @Override
    public void exitPrimary(AppParser.PrimaryContext ctx) {

    }

    @Override
    public void exitHeaderFieldModifierUnique(AppParser.HeaderFieldModifierUniqueContext ctx) {

    }

    @Override
    public void exitOutParameter(AppParser.OutParameterContext ctx) {

    }

    @Override
    public void enterRuleBlock(AppParser.RuleBlockContext ctx) {

    }

    @Override
    public void exitRuleBlock(AppParser.RuleBlockContext ctx) {

    }

    @Override
    public void exitInParameter(AppParser.InParameterContext ctx) {

    }

    @Override
    public void exitScale(AppParser.ScaleContext ctx) {

    }

    @Override
    public void enterLiteral(AppParser.LiteralContext ctx) {

    }

    @Override
    public void exitLiteral(AppParser.LiteralContext ctx) {

    }

    @Override
    public void enterSqlTypeLength(AppParser.SqlTypeLengthContext ctx) {

    }

    @Override
    public void exitSqlTypeLength(AppParser.SqlTypeLengthContext ctx) {

    }

    @Override
    public void enterPrimaryExpr(AppParser.PrimaryExprContext ctx) {

    }

    @Override
    public void exitPrimaryExpr(AppParser.PrimaryExprContext ctx) {

    }

    @Override
    public void exitMapItem(AppParser.MapItemContext ctx) {

    }

    @Override
    public void exitScope(AppParser.ScopeContext ctx) {

    }

    @Override
    public void exitOverride(AppParser.OverrideContext ctx) {

    }

    @Override
    public void exitMap(AppParser.MapContext ctx) {

    }

    @Override
    public void enterPrimitiveType(AppParser.PrimitiveTypeContext ctx) {

    }

    @Override
    public void exitPrimitiveType(AppParser.PrimitiveTypeContext ctx) {

    }

    @Override
    public void enterNumberType(AppParser.NumberTypeContext ctx) {

    }

    @Override
    public void exitNumberType(AppParser.NumberTypeContext ctx) {

    }

    @Override
    public void enterScopeDefine(AppParser.ScopeDefineContext ctx) {

    }

    @Override
    public void exitScopeDefine(AppParser.ScopeDefineContext ctx) {

    }

    @Override
    public void enterPrimExpr(AppParser.PrimExprContext ctx) {

    }

    @Override
    public void exitPrimExpr(AppParser.PrimExprContext ctx) {

    }

    @Override
    public void enterTransactionModifier(AppParser.TransactionModifierContext ctx) {

    }

    @Override
    public void exitTransactionModifier(AppParser.TransactionModifierContext ctx) {

    }

    @Override
    public void exitDefaultValueliteral(AppParser.DefaultValueliteralContext ctx) {

    }

    @Override
    public void enterServiceBody(AppParser.ServiceBodyContext ctx) {

    }

    @Override
    public void exitServiceBody(AppParser.ServiceBodyContext ctx) {

    }

    @Override
    public void exitScopeItem(AppParser.ScopeItemContext ctx) {

    }

    @Override
    public void enterSqlType(AppParser.SqlTypeContext ctx) {

    }

    @Override
    public void exitSqlType(AppParser.SqlTypeContext ctx) {

    }

    @Override
    public void exitService(AppParser.ServiceContext ctx) {

    }

    @Override
    public void enterScopeParameter(AppParser.ScopeParameterContext ctx) {

    }

    @Override
    public void exitScopeParameter(AppParser.ScopeParameterContext ctx) {

    }

    @Override
    public void exitScopeItemIdentifier(AppParser.ScopeItemIdentifierContext ctx) {

    }

    @Override
    public void exitHeaderFieldDefinePrimitiveType(AppParser.HeaderFieldDefinePrimitiveTypeContext ctx) {

    }

    @Override
    public void enterSnapshotModifier(AppParser.SnapshotModifierContext ctx) {

    }

    @Override
    public void exitSnapshotModifier(AppParser.SnapshotModifierContext ctx) {

    }

    @Override
    public void enterSnapshotTiming(AppParser.SnapshotTimingContext ctx) {

    }

    @Override
    public void exitSnapshotTiming(AppParser.SnapshotTimingContext ctx) {

    }

    @Override
    public void exitArgumentSnapshotTarget(AppParser.ArgumentSnapshotTargetContext ctx) {

    }

    @Override
    public void exitCurrentSnapshotTarget(AppParser.CurrentSnapshotTargetContext ctx) {

    }

    @Override
    public void exitTransactionSnapshotTarget(AppParser.TransactionSnapshotTargetContext ctx) {

    }

    @Override
    public void exitInferredSnapshotTarget(AppParser.InferredSnapshotTargetContext ctx) {

    }

    @Override
    public void exitInferredSnapshotByTypeTarget(AppParser.InferredSnapshotByTypeTargetContext ctx) {

    }

    @Override
    public void exitInferredSnapshotByIndexTarget(AppParser.InferredSnapshotByIndexTargetContext ctx) {

    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {

    }

    @Override
    public void visitErrorNode(ErrorNode errorNode) {

    }

    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {

    }

    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {

    }
}
