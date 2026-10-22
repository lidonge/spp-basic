package com.servpp.lang.general.app;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppField;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.models.SppRoleMapper;
import com.servpp.lang.models.SppService;
import com.servpp.lang.models.app.AppColumn;
import com.servpp.lang.models.app.AppForeign;
import com.servpp.lang.models.app.AppHeader;
import com.servpp.lang.models.app.AppIndex;
import com.servpp.lang.models.app.AppTable;
import com.servpp.lang.models.app.AppTables;
import com.servpp.lang.models.app.AnnotationDefine;
import com.servpp.lang.models.app.InferredObjectSnapshotRule;
import com.servpp.lang.models.app.ScopeDefine;
import com.servpp.lang.models.app.ScopeItem;
import com.servpp.lang.models.app.ObjectSnapshotRule;
import com.servpp.lang.models.app.RepositoryHydrationRule;
import com.servpp.lang.models.app.RepositoryHydrationRule.KeyTarget;
import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.models.app.SppFieldReference;
import com.servpp.lang.models.app.TccReservationRule;
import com.servpp.lang.antlr.AppParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public interface IScopeHandler extends IApplicationHandler {
    @Override
    default void enterScope(AppParser.ScopeContext ctx){
        RuleBlock currentRuleBlock = getCurrentRuleBlock();
        currentRuleBlock.setCurrentAnnotatable(currentRuleBlock.getAppScope());
    }

    @Override
    default void enterLocalBody(AppParser.LocalBodyContext ctx){
        getCurrentRuleBlock().getAppScope()._addLocalScope(new ScopeDefine());
    }
    @Override
    default void enterRemoteBody(AppParser.RemoteBodyContext ctx){
        getCurrentRuleBlock().getAppScope()._addRemoteScope(new ScopeDefine());
    }

    @Override
    default void enterInParameter(AppParser.InParameterContext ctx){
        String headerName = ctx.getChild(1).getText();
        RuleBlock ruleBlock = getCurrentRuleBlock();
        AppHeader appHeader = ruleBlock.newOrExistAppHeader(headerName);
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        scopeDefine.setIn(appHeader);
    }

    @Override
    default void enterOutParameter(AppParser.OutParameterContext ctx){
        String headerName = ctx.getChild(1).getText();
        RuleBlock ruleBlock = getCurrentRuleBlock();
        AppHeader appHeader = ruleBlock.newOrExistAppHeader(headerName);
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        scopeDefine.setOut(appHeader);
    }

    @Override
    default void enterScopeItem(AppParser.ScopeItemContext ctx){
        if (ctx.tccResourceBlock() != null) {
            return;
        }
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        scopeDefine.addScopeItem(new ScopeItem());
    }

    @Override
    default void exitScopeItem(AppParser.ScopeItemContext ctx) {
        ScopeDefine scope = getLastElement(getCurrentRuleBlock().getAppScope().getScopelist());
        ScopeItem item = getLastElement(scope.getScopeItems());
        if (item.getTransactionType() == IConstance.TransactionType.quantum && item.getResourceId() != null
                && item.getQuantumConservationAttributeIds().isEmpty() && item.getPersistenceRules().isEmpty()) {
            logSppError(ctx, "Quantum resource " + item.getResourceId()
                    + " must declare at least two conserved quantum attributes, or bind a persistence-only service.");
        }
    }

    @Override
    default void enterTccResourceBlock(AppParser.TccResourceBlockContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = new ScopeItem();
        scopeItem.setTransactionType(IConstance.TransactionType.tcc);
        scopeItem.setResourceId(ctx.Identifier().getText());
        scopeDefine.addScopeItem(scopeItem);
    }

    @Override
    default void exitTccResourceBlock(AppParser.TccResourceBlockContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        if (!scopeItem.getResourceServiceNames().isEmpty()
                && scopeItem.getResourceServiceNames().size() != 2) {
            logSppError(ctx, "TCC resource " + scopeItem.getResourceId()
                    + " must declare confirm and cancel services when lifecycle services are provided.");
        }
    }

    @Override
    default void enterTccResourceBlockReserve(AppParser.TccResourceBlockReserveContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        addTccReservationRule(ctx, scopeItem,
                ctx.qualifiedName(0).getText(), ctx.qualifiedName(1).getText());
    }

    @Override
    default void exitTccResourceBlockReserve(AppParser.TccResourceBlockReserveContext ctx) {
    }

    @Override
    default void enterTccResourceBlockConfirm(AppParser.TccResourceBlockConfirmContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.addResourceArgumentName(ctx.Identifier().getText());
        scopeItem.addResourceServiceName(ctx.Identifier().getText());
    }

    @Override
    default void exitTccResourceBlockConfirm(AppParser.TccResourceBlockConfirmContext ctx) {
    }

    @Override
    default void enterTccResourceBlockCancel(AppParser.TccResourceBlockCancelContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.addResourceArgumentName(ctx.Identifier().getText());
        scopeItem.addResourceServiceName(ctx.Identifier().getText());
    }

    @Override
    default void exitTccResourceBlockCancel(AppParser.TccResourceBlockCancelContext ctx) {
    }

    @Override
    default void enterTccResourceBlockSnapshot(AppParser.TccResourceBlockSnapshotContext ctx) {
    }

    @Override
    default void exitTccResourceBlockSnapshot(AppParser.TccResourceBlockSnapshotContext ctx) {
    }

    @Override
    default void enterAsycnModifier(AppParser.AsycnModifierContext ctx){
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.setAsync(true);
        scopeItem.addAsyncModifierName(ctx.getText());
    }

    @Override
    default void enterTransModifier(AppParser.TransModifierContext ctx){
        TransactionType type = IConstance.TransactionType.valueOf(ctx.getChild(0).getText());
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.setTransactionType(type);
        if (type == IConstance.TransactionType.quantum && scopeItem.getService() != null) {
            scopeItem.getService().setQuantumService(true);
        }
    }

    @Override
    default void enterResourceModifier(AppParser.ResourceModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.setResourceId(ctx.Identifier().getText());
        for (AppParser.QualifiedNameContext qualifiedName : ctx.qualifiedName()) {
            String value = qualifiedName.getText();
            scopeItem.addResourceArgumentName(value);
            if (scopeItem.getTransactionType() == IConstance.TransactionType.quantum) {
                validateQuantumResourceAttribute(ctx, value);
                scopeItem.addQuantumConservationAttributeId(value);
            } else if (scopeItem.getTransactionType() == IConstance.TransactionType.tcc) {
                scopeItem.addResourceServiceName(value);
            }
        }
        if (scopeItem.getTransactionType() == IConstance.TransactionType.quantum
                && !scopeItem.getQuantumConservationAttributeIds().isEmpty()
                && scopeItem.getQuantumConservationAttributeIds().size() < 2) {
            logSppError(ctx, "Quantum resource " + scopeItem.getResourceId()
                    + " must declare at least two conserved quantum attributes.");
        }
        if (scopeItem.getTransactionType() == IConstance.TransactionType.tcc
                && !scopeItem.getResourceServiceNames().isEmpty()
                && scopeItem.getResourceServiceNames().size() != 2) {
            logSppError(ctx, "TCC resource " + scopeItem.getResourceId()
                    + " must declare confirm and cancel services when lifecycle services are provided.");
        }
    }

    @Override
    default void exitResourceModifier(AppParser.ResourceModifierContext ctx) {
    }

    @Override
    default void enterTccReservationModifier(AppParser.TccReservationModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        String source = ctx.qualifiedName(0).getText();
        String target = ctx.qualifiedName(1).getText();
        addTccReservationRule(ctx, scopeItem, source, target);
    }

    @Override
    default void exitTccReservationModifier(AppParser.TccReservationModifierContext ctx) {
    }

    default void addTccReservationRule(ParserRuleContext ctx, ScopeItem scopeItem, String source, String target) {
        scopeItem.addTccReservationRule(new TccReservationRule(source, target));
        validateTccReservationSourceAttribute(ctx, source);
        validateTccReservationTargetAttribute(ctx, target);
        validateTccReservationBusinessType(ctx, source, target);
        validateTccReservationAttributeTypes(ctx, source, target);
        validateTccReservationTargetNotQuantum(ctx, target);
        if (scopeItem.getTransactionType() != IConstance.TransactionType.tcc) {
            logSppError(ctx, "TCC reservation " + source + " -> " + target
                    + " must be declared on a TCC resource service.");
        }
    }

    default void validateTccReservationSourceAttribute(ParserRuleContext ctx, String qualifiedName) {
        if (getQualifiedField(ctx, null, qualifiedName) == null) {
            logSppError(ctx, "TCC reservation source attribute " + qualifiedName
                    + " must refer to an existing field.");
        }
    }

    default void validateTccReservationTargetAttribute(ParserRuleContext ctx, String qualifiedName) {
        if (getQualifiedField(ctx, null, qualifiedName) != null) {
            logSppError(ctx, "TCC reservation target attribute " + qualifiedName
                    + " must be declared as an APP DDL platform column, not a .spp business field.");
            return;
        }
        AppColumn appColumn = getAppDdlColumn(ctx, qualifiedName);
        if (appColumn != null) {
            if (appColumn.isPrimaryKey()) {
                logSppError(ctx, "TCC reservation target attribute " + qualifiedName
                        + " must not be an APP DDL primary key column.");
            }
            if (appColumn.isObjectBacked()) {
                logSppError(ctx, "TCC reservation target attribute " + qualifiedName
                        + " must be an APP DDL platform column, but it is object-backed.");
            }
            return;
        }
        logSppError(ctx, "TCC reservation target attribute " + qualifiedName
                + " must refer to an APP DDL platform column.");
    }

    default void validateTccReservationBusinessType(ParserRuleContext ctx, String source,
                                                    String target) {
        String sourceRoot = qualifiedNameRoot(source);
        String targetRoot = qualifiedNameRoot(target);
        if (sourceRoot != null && targetRoot != null && !sourceRoot.equals(targetRoot)) {
            logSppError(ctx, "TCC reservation " + source + " -> " + target
                    + " must keep source and target on the same business type.");
        }
    }

    default void validateTccReservationAttributeTypes(ParserRuleContext ctx, String source,
                                                      String target) {
        String sourceType = tccReservationAttributeType(ctx, source);
        String targetType = tccReservationAttributeType(ctx, target);
        if (sourceType == null || targetType == null || sourceType.equals(targetType)) {
            return;
        }
        logSppError(ctx, "TCC reservation " + source + " -> " + target
                + " must use matching attribute types, but source is "
                + sourceType + " and target is " + targetType + ".");
    }

    default void validateTccReservationTargetNotQuantum(ParserRuleContext ctx, String target) {
        SppFieldReference fieldReference = getQualifiedField(ctx, null, target);
        if (fieldReference != null && fieldReference.getSppField() != null
                && fieldReference.getSppField().isQuantum()) {
            logSppError(ctx, "TCC reservation target attribute " + target
                    + " must not refer to a quantum field; reservation state belongs to APP DDL"
                    + " platform columns or TCC platform state.");
        }
    }

    default String tccReservationAttributeType(ParserRuleContext ctx, String qualifiedName) {
        SppFieldReference fieldReference = getQualifiedField(ctx, null, qualifiedName);
        if (fieldReference != null && fieldReference.getSppField() != null
                && fieldReference.getSppField().getType() != null) {
            return fieldReference.getSppField().getType().getName();
        }
        AppColumn appColumn = getAppDdlColumn(ctx, qualifiedName);
        if (appColumn != null && appColumn.getType() != null) {
            return appColumn.getType().getName();
        }
        return null;
    }

    default String qualifiedNameRoot(String qualifiedName) {
        int separator = qualifiedName.indexOf('.');
        if (separator <= 0) {
            return null;
        }
        return qualifiedName.substring(0, separator);
    }

    default AppColumn getAppDdlColumn(ParserRuleContext ctx, String qualifiedName) {
        int separator = qualifiedName.indexOf('.');
        if (separator <= 0 || separator == qualifiedName.length() - 1) {
            return null;
        }
        String businessType = qualifiedName.substring(0, separator);
        String columnName = qualifiedName.substring(separator + 1);
        if (columnName.contains(".")) {
            return null;
        }
        for (AppTables appTables : getCurrentRuleBlock().getAppTables()) {
            for (AppTable appTable : appTables.getAppTableList()) {
                if (appTable.getEntity() == null
                        || !businessType.equals(appTable.getEntity().getName())) {
                    continue;
                }
                for (AppColumn appColumn : appTable.getAppColumns()) {
                    if (columnName.equals(appColumn.getName())) {
                        return appColumn;
                    }
                }
            }
        }
        return null;
    }

    default void validateQuantumResourceAttribute(AppParser.ResourceModifierContext ctx, String qualifiedName) {
        SppFieldReference fieldReference = getQualifiedField(ctx, null, qualifiedName);
        if (fieldReference == null) {
            return;
        }
        SppField field = fieldReference.getSppField();
        if (!field.isQuantum()) {
            logSppError(ctx, "Quantum resource attribute " + qualifiedName
                    + " must refer to a quantum field.");
        }
    }

    @Override
    default void enterArgumentSnapshotTarget(AppParser.ArgumentSnapshotTargetContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        ObjectSnapshotRule rule = new ObjectSnapshotRule(ObjectSnapshotRule.Source.argument,
                Integer.parseInt(ctx.IntegerLiteral().getText()), ctx.qualifiedName().getText(),
                snapshotTiming(ctx), snapshotRepositoryName(ctx));
        scopeItem.addObjectSnapshotRule(rule);
        validateSnapshotObjectPath(ctx, rule);
    }

    @Override
    default void enterCurrentSnapshotTarget(AppParser.CurrentSnapshotTargetContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        ObjectSnapshotRule rule = new ObjectSnapshotRule(ObjectSnapshotRule.Source.current,
                -1, ctx.qualifiedName().getText(), snapshotTiming(ctx), snapshotRepositoryName(ctx));
        scopeItem.addObjectSnapshotRule(rule);
        validateSnapshotObjectPath(ctx, rule);
    }

    @Override
    default void enterTransactionSnapshotTarget(AppParser.TransactionSnapshotTargetContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.addObjectSnapshotRule(new ObjectSnapshotRule(ObjectSnapshotRule.Source.transaction,
                -1, "TransactionSnapshot.globalSerialNumber", List.of("globalSerialNumber"),
                snapshotTiming(ctx), snapshotRepositoryName(ctx)));
    }

    @Override
    default void enterInferredSnapshotTarget(AppParser.InferredSnapshotTargetContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.addInferredObjectSnapshotRule(new InferredObjectSnapshotRule(ctx.Identifier().getText(),
                null, null, snapshotTiming(ctx), scopeItem.getObjectSnapshotRules().size(),
                snapshotRepositoryName(ctx)));
    }

    @Override
    default void enterInferredSnapshotByTypeTarget(AppParser.InferredSnapshotByTypeTargetContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.addInferredObjectSnapshotRule(new InferredObjectSnapshotRule(ctx.Identifier(0).getText(),
                ctx.Identifier(1).getText(), null, snapshotTiming(ctx), scopeItem.getObjectSnapshotRules().size(),
                snapshotRepositoryName(ctx)));
    }

    @Override
    default void enterInferredSnapshotByIndexTarget(AppParser.InferredSnapshotByIndexTargetContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.addInferredObjectSnapshotRule(new InferredObjectSnapshotRule(ctx.Identifier(0).getText(),
                null, ctx.Identifier(1).getText(), snapshotTiming(ctx), scopeItem.getObjectSnapshotRules().size(),
                snapshotRepositoryName(ctx)));
    }

    default ObjectSnapshotRule.Timing snapshotTiming(AppParser.SnapshotTargetContext ctx) {
        if (ctx.getParent() instanceof AppParser.SnapshotModifierContext snapshotModifier
                && snapshotModifier.snapshotTiming() != null) {
            return ObjectSnapshotRule.Timing.valueOf(snapshotModifier.snapshotTiming().getText());
        }
        if (ctx.getParent() instanceof AppParser.TccResourceBlockSnapshotContext snapshotModifier
                && snapshotModifier.snapshotTiming() != null) {
            return ObjectSnapshotRule.Timing.valueOf(snapshotModifier.snapshotTiming().getText());
        }
        return ObjectSnapshotRule.Timing.afterCommit;
    }

    default String snapshotRepositoryName(AppParser.SnapshotTargetContext ctx) {
        AppParser.SnapshotUsingContext using = null;
        if (ctx.getParent() instanceof AppParser.SnapshotModifierContext snapshotModifier) {
            using = snapshotModifier.snapshotUsing();
        } else if (ctx.getParent() instanceof AppParser.TccResourceBlockSnapshotContext snapshotModifier) {
            using = snapshotModifier.snapshotUsing();
        }
        return using == null ? null : using.Identifier().getText();
    }

    @Override
    default void enterCompensateModifier(AppParser.CompensateModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        for (var identifier : ctx.Identifier()) {
            scopeItem.addCompensationServiceName(identifier.getText());
        }
    }

    @Override
    default void exitCompensateModifier(AppParser.CompensateModifierContext ctx) {
    }

    @Override
    default void enterRemoteCompensateModifier(AppParser.RemoteCompensateModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        for (var identifier : ctx.Identifier()) {
            scopeItem.addRemoteCompensationServiceName(identifier.getText());
        }
    }

    @Override
    default void exitRemoteCompensateModifier(AppParser.RemoteCompensateModifierContext ctx) {
    }

    @Override
    default void enterRepositoryModifier(AppParser.RepositoryModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        List<KeyTarget> targets = new ArrayList<>();
        for (var targetContext : ctx.repositoryTarget()) targets.add(persistenceTarget(targetContext));
        RepositoryHydrationRule rule = new RepositoryHydrationRule(
                targets,
                com.servpp.lang.general.NameUtil.normalizeParticipantReference(ctx.qualifiedName().getText()),
                ctx.repositoryUsing() == null ? null : ctx.repositoryUsing().Identifier().getText());
        scopeItem.addRepositoryHydrationRule(rule);
        if (ctx.children.stream().anyMatch(child -> "for".equals(child.getText()))) {
            scopeItem.declareRepositoryReadLock();
        }
        for (int i = 0; i < ctx.repositoryTarget().size(); i++) {
            validateRepositoryObjectPath(ctx.repositoryTarget(i), rule.keyTargets().get(i));
        }
        validateRepositoryKeyObjectTypes(ctx, rule);
    }

    @Override
    default void exitRepositoryModifier(AppParser.RepositoryModifierContext ctx) {
    }

    @Override
    default void enterBusinessIdempotencyModifier(AppParser.BusinessIdempotencyModifierContext ctx) {
        getLastElement(getLastElement(getCurrentRuleBlock().getAppScope().getScopelist()).getScopeItems()).declareBusinessIdempotency();
    }
    @Override default void exitBusinessIdempotencyModifier(AppParser.BusinessIdempotencyModifierContext ctx) { }

    @Override
    default void enterOptionalRepositoryModifier(AppParser.OptionalRepositoryModifierContext ctx) {
        getLastElement(getLastElement(getCurrentRuleBlock().getAppScope().getScopelist()).getScopeItems()).declareOptionalRepository();
    }
    @Override default void exitOptionalRepositoryModifier(AppParser.OptionalRepositoryModifierContext ctx) { }

    @Override
    default void enterPersistenceModifier(AppParser.PersistenceModifierContext ctx) {
        ScopeItem item = getLastElement(getLastElement(getCurrentRuleBlock().getAppScope().getScopelist()).getScopeItems());
        List<KeyTarget> keys = ctx.repositoryTarget().stream().map(this::persistenceTarget).toList();
        item.getPersistenceRules().add(new com.servpp.lang.models.app.PersistenceRule(
                ctx.persistenceOperation().getText(), keys));
        for (int i = 0; i < keys.size(); i++) {
            validateRepositoryObjectPath(ctx.repositoryTarget(i), keys.get(i));
        }
    }

    @Override default void exitPersistenceModifier(AppParser.PersistenceModifierContext ctx) { }
    @Override default void enterPersistenceOperation(AppParser.PersistenceOperationContext ctx) { }
    @Override default void exitPersistenceOperation(AppParser.PersistenceOperationContext ctx) { }

    @Override default void enterDeclaredIdempotencyModifier(AppParser.DeclaredIdempotencyModifierContext ctx) {
        ScopeItem item = getLastElement(getLastElement(getCurrentRuleBlock().getAppScope().getScopelist()).getScopeItems());
        var keys = ctx.keyPaths.stream().map(org.antlr.v4.runtime.ParserRuleContext::getText).toList();
        var facts = ctx.factPaths.stream().map(org.antlr.v4.runtime.ParserRuleContext::getText).toList();
        item.setDeclaredIdempotency(keys, facts);
        if (ctx.requestDuration() != null) {
            try {
                item.setDuplicateWindow(RequestDuration.value(ctx.requestDuration()));
            } catch (RuntimeException failure) { logSppError(ctx, "Duplicate guard window requires a positive duration with a supported unit (or a legacy positive ISO-8601 duration): " + ctx.requestDuration().getText()); }
        }
    }
    @Override default void exitDeclaredIdempotencyModifier(AppParser.DeclaredIdempotencyModifierContext ctx) { }

    @Override
    default void enterPersistenceVersionModifier(AppParser.PersistenceVersionModifierContext ctx) {
        ScopeItem item = getLastElement(getLastElement(getCurrentRuleBlock().getAppScope().getScopelist()).getScopeItems());
        KeyTarget expected = persistenceTarget(ctx.repositoryTarget(0));
        KeyTarget proposed = ctx.repositoryTarget().size() == 1 ? expected : persistenceTarget(ctx.repositoryTarget(1));
        item.getPersistenceVersions().add(new com.servpp.lang.models.app.PersistenceVersion(expected, proposed, ctx.getText().contains("advanceVersion")));
        if (!expected.isPlatform()) validateRepositoryObjectPath(ctx.repositoryTarget(0), expected);
        if (ctx.repositoryTarget().size() > 1 && !proposed.isPlatform()) validateRepositoryObjectPath(ctx.repositoryTarget(1), proposed);
    }

    @Override default void exitPersistenceVersionModifier(AppParser.PersistenceVersionModifierContext ctx) { }

    @Override
    default void enterBusinessNumberModifier(AppParser.BusinessNumberModifierContext ctx) {
        ScopeItem item = getLastElement(getLastElement(getCurrentRuleBlock().getAppScope().getScopelist()).getScopeItems());
        String literal = ctx.StringLiteral().getText();
        item.getBusinessNumbers().add(new ScopeItem.BusinessNumber(persistenceTarget(ctx.repositoryTarget()), literal.substring(1,literal.length()-1)));
    }

    private KeyTarget persistenceTarget(AppParser.RepositoryTargetContext context) {
        if (context instanceof AppParser.PlatformRepositoryTargetContext target) {
            String path = target.qualifiedName().getText(); int dot = path.indexOf('.');
            if (dot < 0) throw new IllegalArgumentException("Platform target requires Type.column");
            return new KeyTarget(Integer.parseInt(target.IntegerLiteral().getText()), path.substring(0,dot)+".@platform:"+path.substring(dot+1));
        }
        if (context instanceof AppParser.SingletonRepositoryTargetContext target)
            return new KeyTarget(Integer.parseInt(target.IntegerLiteral().getText()), target.Identifier().getText()+".@singleton");
        AppParser.ArgumentRepositoryTargetContext target = (AppParser.ArgumentRepositoryTargetContext) context;
        return new KeyTarget(Integer.parseInt(target.IntegerLiteral().getText()), target.qualifiedName().getText());
    }

    @Override
    default void enterRepositoryByTypeModifier(AppParser.RepositoryByTypeModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        String objectType = ctx.Identifier().getText();
        List<KeyTarget> targets = repositoryTargetsFromAppDdl(ctx, objectType);
        if (!targets.isEmpty()) {
            RepositoryHydrationRule rule = new RepositoryHydrationRule(
                    objectType,
                    targets,
                    NameUtil.firstToLowerCase(objectType, true),
                    ctx.repositoryUsing() == null ? null : ctx.repositoryUsing().Identifier().getText(),
                    true,
                    repositoryPrimaryKeySourceHint(objectType, targets));
            scopeItem.addRepositoryHydrationRule(rule);
            validateRepositoryKeyObjectTypes(ctx, rule);
        }
    }

    @Override
    default void exitRepositoryByTypeModifier(AppParser.RepositoryByTypeModifierContext ctx) {
    }

    @Override
    default void enterRepositoryByForeignTypeModifier(AppParser.RepositoryByForeignTypeModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        String objectType = ctx.Identifier(0).getText();
        String foreignType = ctx.Identifier(1).getText();
        List<KeyTarget> targets = repositoryTargetsFromAppDdlForeignKey(ctx, objectType, foreignType);
        if (!targets.isEmpty()) {
            RepositoryHydrationRule rule = new RepositoryHydrationRule(
                    objectType,
                    targets,
                    NameUtil.firstToLowerCase(objectType, true),
                    ctx.repositoryUsing() == null ? null : ctx.repositoryUsing().Identifier().getText(),
                    true,
                    repositoryForeignTypeSourceHint(objectType, targets));
            scopeItem.addRepositoryHydrationRule(rule);
        }
    }

    @Override
    default void exitRepositoryByForeignTypeModifier(AppParser.RepositoryByForeignTypeModifierContext ctx) {
    }

    @Override
    default void enterRepositoryByIndexModifier(AppParser.RepositoryByIndexModifierContext ctx) {
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        String objectType = ctx.Identifier(0).getText();
        String indexName = ctx.Identifier(1).getText();
        List<KeyTarget> targets = repositoryTargetsFromAppDdlIndex(ctx, objectType, indexName);
        if (!targets.isEmpty()) {
            RepositoryHydrationRule rule = new RepositoryHydrationRule(
                    objectType,
                    targets,
                    NameUtil.firstToLowerCase(objectType, true),
                    ctx.repositoryUsing() == null ? null : ctx.repositoryUsing().Identifier().getText(),
                    true,
                    repositoryIndexSourceHint(objectType, targets));
            scopeItem.addRepositoryHydrationRule(rule);
            validateRepositoryKeyObjectTypes(ctx, rule);
        }
    }

    @Override
    default void exitRepositoryByIndexModifier(AppParser.RepositoryByIndexModifierContext ctx) {
    }

    @Override
    default void enterRepositoryUsing(AppParser.RepositoryUsingContext ctx) {
    }

    @Override
    default void exitRepositoryUsing(AppParser.RepositoryUsingContext ctx) {
    }

    @Override
    default void enterSnapshotUsing(AppParser.SnapshotUsingContext ctx) {
    }

    @Override
    default void exitSnapshotUsing(AppParser.SnapshotUsingContext ctx) {
    }

    @Override
    default void enterPlatformRepositoryTarget(AppParser.PlatformRepositoryTargetContext ctx) {}
    @Override
    default void exitPlatformRepositoryTarget(AppParser.PlatformRepositoryTargetContext ctx) {}
    @Override
    default void enterSingletonRepositoryTarget(AppParser.SingletonRepositoryTargetContext ctx) {}
    @Override
    default void exitSingletonRepositoryTarget(AppParser.SingletonRepositoryTargetContext ctx) {}
    @Override
    default void exitBusinessNumberModifier(AppParser.BusinessNumberModifierContext ctx) {}

    @Override
    default void enterArgumentRepositoryTarget(AppParser.ArgumentRepositoryTargetContext ctx) {
    }

    @Override
    default void exitArgumentRepositoryTarget(AppParser.ArgumentRepositoryTargetContext ctx) {
    }

    @Override
    default void enterScopeItemIdentifier(AppParser.ScopeItemIdentifierContext ctx){
        String clsName = ctx.getChild(0).getText();
        RuleBlock ruleBlock = getCurrentRuleBlock();
        ScopeDefine scopeDefine = getLastElement(ruleBlock.getAppScope().getScopelist());
        ScopeItem scopeItem = getLastElement(scopeDefine.getScopeItems());
        scopeItem.setDeclaredServiceName(clsName);
        if("_default".equals(clsName)){
            List<SppService> defaultServices = new ArrayList<>();
            for(SppCompilationUnit sppClass:getSppDomian().getMapsOfClass().values()){
                if(sppClass instanceof SppService service && service.getScopeItem() != null){
                    ScopeItem theItem = service.getScopeItem();
                    if(theItem.isLocal() &&
                            theItem.getScopeDefine().getIn() == null &&
                            theItem.getScopeDefine().getOut() == null){//default entity services
                        defaultServices.add(service);
                    }
                }
            }
            for (int i = 0; i < defaultServices.size(); i++) {
                SppService defaultService = defaultServices.get(i);
                if (i == 0) {
                    scopeItem.setService(defaultService);
                } else {
                    scopeDefine.addScopeItem(scopeItem.copyForService(defaultService));
                }
            }
        }else {
            getSppDomian().resolveImplicitCrudService(clsName);
            SppCompilationUnit sppClass = getSppDomian().getSppClass(NameUtil.firstToLowerCase(clsName, false));
            if (sppClass instanceof SppService) {
                scopeItem.setService((SppService) sppClass);
                if (scopeItem.getTransactionType() == IConstance.TransactionType.quantum) {
                    ((SppService) sppClass).setQuantumService(true);
                }
                expandInferredSnapshotRules(ctx, scopeItem);
                validateArgumentSnapshotRules(ctx, scopeItem);
                normalizeRepositoryArgumentRules(ctx, scopeItem);
                validateArgumentRepositoryRules(ctx, scopeItem);
            } else {
                logSppError(ctx, clsName + " should be a service.");
            }
        }
    }

    default void expandInferredSnapshotRules(AppParser.ScopeItemIdentifierContext ctx, ScopeItem scopeItem) {
        if (scopeItem.getService() == null || scopeItem.getInferredObjectSnapshotRules().isEmpty()) {
            return;
        }
        int insertedRules = 0;
        for (InferredObjectSnapshotRule inferredRule : scopeItem.getInferredObjectSnapshotRules()) {
            List<SnapshotArgumentCandidate> argumentCandidates = argumentCandidatesForObjectType(
                    scopeItem.getService(), inferredRule.objectType());
            if (argumentCandidates.isEmpty()) {
                logSppError(ctx, "Snapshot " + inferredRule.objectType()
                        + " must match exactly one scalar service parameter or member on "
                        + scopeItem.getService().getName() + ".");
                continue;
            }
            if (argumentCandidates.size() > 1) {
                logSppError(ctx, "Snapshot " + inferredRule.objectType()
                        + " is ambiguous on service " + scopeItem.getService().getName()
                        + "; declare a named APP DDL index or use snapshot(argument(index, path))"
                        + " as a compatibility escape hatch.");
                continue;
            }
            SnapshotArgumentCandidate argumentCandidate = argumentCandidates.get(0);
            List<String> keyPaths = inferredRule.indexName() != null
                    ? snapshotPathsFromNamedAppDdlIndex(ctx, inferredRule.objectType(), inferredRule.indexName())
                    : inferredRule.referenceType() == null
                    ? snapshotPathsFromAppDdl(ctx, inferredRule.objectType())
                    : snapshotPathsFromAppDdlIndex(ctx, inferredRule.objectType(), inferredRule.referenceType());
            if (keyPaths.isEmpty()) {
                continue;
            }
            ObjectSnapshotRule rule;
            if (keyPaths.size() == 1) {
                String objectIdPath = argumentCandidate.argumentObjectIdPath(
                        RepositoryHydrationRule.objectIdPath(keyPaths.get(0)));
                rule = new ObjectSnapshotRule(ObjectSnapshotRule.Source.argument,
                        argumentCandidate.argumentIndex(), inferredRule.objectType() + "." + objectIdPath,
                        List.of(objectIdPath), inferredRule.timing(),
                        inferredRule.repositoryName(), true);
            } else {
                if (inferredRule.indexName() == null && inferredRule.referenceType() != null) {
                    logSppError(ctx, "Snapshot " + inferredRule.objectType() + " by "
                            + inferredRule.referenceType()
                            + " APP DDL index must resolve to exactly one object id path; use "
                            + "snapshot(" + inferredRule.objectType()
                            + " by index <name>) or snapshot(argument(index, path)) as a compatibility"
                            + " escape hatch for ambiguous indexes.");
                    continue;
                }
                List<String> objectIdPaths = keyPaths.stream()
                        .map(RepositoryHydrationRule::objectIdPath)
                        .map(argumentCandidate::argumentObjectIdPath)
                        .toList();
                rule = new ObjectSnapshotRule(ObjectSnapshotRule.Source.argument,
                        argumentCandidate.argumentIndex(), inferredRule.objectType() + "." + objectIdPaths.get(0),
                        objectIdPaths, inferredRule.timing(), inferredRule.repositoryName(), true);
            }
            int insertionIndex = inferredRule.insertionIndex() < 0
                    ? scopeItem.getObjectSnapshotRules().size()
                    : Math.min(inferredRule.insertionIndex() + insertedRules,
                    scopeItem.getObjectSnapshotRules().size());
            scopeItem.getObjectSnapshotRules().add(insertionIndex, rule);
            insertedRules++;
            validateSnapshotObjectPath(ctx, scopeItem.getService(), rule);
        }
    }

    default List<Integer> argumentIndexesForObjectType(SppService service, String objectType) {
        List<Integer> indexes = new ArrayList<>();
        List<SppLocalVar> arguments = service.getServiceBody().getSppLocalVarList();
        for (int i = 0; i < arguments.size(); i++) {
            SppLocalVar argument = arguments.get(i);
            if (argument.getType() != null
                    && objectType.equals(argument.getType().getName())
                    && argument.getArrayDimension() == 0) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    default List<SnapshotArgumentCandidate> argumentCandidatesForObjectType(SppService service, String objectType) {
        List<SnapshotArgumentCandidate> directCandidates = new ArrayList<>();
        List<SnapshotArgumentCandidate> memberCandidates = new ArrayList<>();
        List<SppLocalVar> arguments = service.getServiceBody().getSppLocalVarList();
        for (int i = 0; i < arguments.size(); i++) {
            SppLocalVar argument = arguments.get(i);
            if (argument.getType() == null || argument.getArrayDimension() != 0) {
                continue;
            }
            if (objectType.equals(argument.getType().getName())) {
                directCandidates.add(new SnapshotArgumentCandidate(i, ""));
                continue;
            }
            if (argument.getType() instanceof SppClass argumentClass) {
                for (String memberPath : memberPathsForObjectType(argumentClass, objectType, new HashSet<>())) {
                    memberCandidates.add(new SnapshotArgumentCandidate(i, memberPath));
                }
            }
        }
        return directCandidates.isEmpty() ? memberCandidates : directCandidates;
    }

    default List<String> memberPathsForObjectType(SppClass rootClass, String objectType, Set<String> visitingTypes) {
        if (rootClass == null || !visitingTypes.add(rootClass.getName())) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (SppField field : rootClass.getSppFieldList()) {
            if (field.getArrayDimension() != 0 || !(field.getType() instanceof SppClass fieldClass)) {
                continue;
            }
            if (objectType.equals(fieldClass.getName())) {
                paths.add(field.getName());
                continue;
            }
            for (String nestedPath : memberPathsForObjectType(fieldClass, objectType, visitingTypes)) {
                paths.add(field.getName() + "." + nestedPath);
            }
        }
        visitingTypes.remove(rootClass.getName());
        return paths;
    }

    record SnapshotArgumentCandidate(int argumentIndex, String memberPath) {
        String argumentObjectIdPath(String objectIdPath) {
            if (memberPath == null || memberPath.isBlank()) {
                return objectIdPath;
            }
            return memberPath + "." + objectIdPath;
        }
    }

    default void normalizeRepositoryArgumentRules(AppParser.ScopeItemIdentifierContext ctx, ScopeItem scopeItem) {
        if (scopeItem.getService() == null || scopeItem.getRepositoryHydrationRules().isEmpty()) {
            return;
        }
        List<RepositoryHydrationRule> rules = scopeItem.getRepositoryHydrationRules();
        for (int i = 0; i < rules.size(); i++) {
            RepositoryHydrationRule rule = rules.get(i);
            String responseMethodName = inferRepositoryResponseMethodName(scopeItem.getService(), rule);
            List<KeyTarget> normalizedTargets = new ArrayList<>();
            boolean changed = false;
            for (KeyTarget target : rule.keyTargets()) {
                KeyTarget normalized = normalizeRepositoryArgumentTarget(ctx, scopeItem.getService(), rule, target);
                normalizedTargets.add(normalized);
                changed = changed || !normalized.equals(target);
            }
            changed = changed || !responseMethodName.equals(rule.responseMethodName());
            if (changed) {
                rules.set(i, new RepositoryHydrationRule(rule.objectType(), normalizedTargets,
                        responseMethodName, rule.repositoryName(), rule.appDdlInferred(), rule.sourceHint()));
            }
        }
    }

    default String inferRepositoryResponseMethodName(SppService service, RepositoryHydrationRule rule) {
        if (service == null || rule == null || !rule.appDdlInferred()) return rule == null ? "" : rule.responseMethodName();
        if (service.getServiceBody().getQualifieField(rule.responseMethodName()) != null) return rule.responseMethodName();
        List<String> candidates = new ArrayList<>();
        for (var parameter : service.getServiceBody().getSppLocalVarList())
            if (parameter.getType() != null && rule.getObjectType().equals(parameter.getType().getName())
                    && parameter.getArrayDimension() == 0) candidates.add(parameter.getName());
        return candidates.size() == 1 ? candidates.get(0) : rule.responseMethodName();
    }

    default boolean hasRepositoryResponseField(SppService service, String responseMethodName) {
        if (service == null || responseMethodName == null) {
            return false;
        }
        for (SppLocalVar returnValue : service.getReturns().getSppLocalVarList()) {
            if (responseMethodName.equals(returnValue.getName())) {
                return true;
            }
        }
        return false;
    }

    default KeyTarget normalizeRepositoryArgumentTarget(AppParser.ScopeItemIdentifierContext ctx,
                                                       SppService service,
                                                       RepositoryHydrationRule rule,
                                                       KeyTarget target) {
        if (matchesRepositoryArgument(service, target.argumentIndex(), target.objectPath())) {
            return target;
        }
        if (!isRepositoryAppDdlIndexPath(rule.getObjectType(), target.repositoryObjectPath())) {
            return target;
        }
        List<KeyTarget> candidates = new ArrayList<>();
        List<SppLocalVar> arguments = service.getServiceBody().getSppLocalVarList();
        for (int i = 0; i < arguments.size(); i++) {
            SppLocalVar argument = arguments.get(i);
            if (argument.getType() == null || argument.getArrayDimension() != 0) {
                continue;
            }
            String argumentType = argument.getType().getName();
            if (argumentType.equals(target.getObjectType())
                    && argument.getType() instanceof SppClass argumentClass
                    && hasFieldPath(argumentClass, target.getObjectIdPath())) {
                candidates.add(new KeyTarget(i, target.objectPath(), target.repositoryObjectPath()));
            }
            if (argumentType.equals(RepositoryHydrationRule.objectType(target.repositoryObjectPath()))
                    && argument.getType() instanceof SppClass argumentClass
                    && hasFieldPath(argumentClass, RepositoryHydrationRule.objectIdPath(target.repositoryObjectPath()))) {
                candidates.add(new KeyTarget(i, target.repositoryObjectPath(), target.repositoryObjectPath()));
            }
            String relativePath = relativeRepositoryArgumentPath(target.repositoryObjectPath(), argumentType);
            if (relativePath != null) {
                candidates.add(new KeyTarget(i, argumentType + "." + relativePath, target.repositoryObjectPath()));
            }
            if (target.getObjectType().equals(rule.getObjectType())) {
                KeyTarget appDdlPrimaryKeyTarget = repositoryArgumentTargetFromAppDdlPrimaryKey(argumentType,
                        rule.getObjectType(), target.repositoryObjectPath(), i);
                if (appDdlPrimaryKeyTarget != null) {
                    candidates.add(appDdlPrimaryKeyTarget);
                }
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.size() > 1) {
            logSppError(ctx, "Repository rule for target " + rule.responseMethodName()
                    + " is ambiguous on service " + service.getName()
                    + "; declare a named APP DDL index or use repository(argument(...), ...)"
                    + " as a compatibility escape hatch.");
        }
        return target;
    }

    default KeyTarget repositoryArgumentTargetFromAppDdlPrimaryKey(String argumentType,
                                                                  String repositoryObjectType,
                                                                  String repositoryObjectPath,
                                                                  int argumentIndex) {
        String repositoryPlatformColumn = RepositoryHydrationRule.objectIdPath(repositoryObjectPath);
        if (argumentType == null || repositoryObjectType == null || repositoryObjectPath == null
                || repositoryPlatformColumn == null || repositoryPlatformColumn.isBlank()
                || repositoryPlatformColumn.contains(".")) {
            return null;
        }
        AppTable argumentTable = findAppTableByBusinessType(argumentType);
        if (argumentTable == null) {
            return null;
        }
        List<String> matchingPrimaryKeyPaths = new ArrayList<>();
        for (String primaryKeyPath : appDdlPrimaryKeyPaths(null, argumentTable, argumentType,
                new HashSet<>(), "Repository")) {
            String primaryKeyObjectPath = RepositoryHydrationRule.objectIdPath(primaryKeyPath);
            if (repositoryPlatformColumnMatchesObjectPath(repositoryPlatformColumn, primaryKeyObjectPath)) {
                matchingPrimaryKeyPaths.add(primaryKeyPath);
            }
        }
        if (matchingPrimaryKeyPaths.size() != 1) {
            return null;
        }
        return new KeyTarget(argumentIndex, matchingPrimaryKeyPaths.get(0), repositoryObjectPath);
    }

    default boolean repositoryPlatformColumnMatchesObjectPath(String platformColumn, String objectPath) {
        if (platformColumn == null || objectPath == null || objectPath.isBlank()) {
            return false;
        }
        String leafName = objectPath.contains(".")
                ? objectPath.substring(objectPath.lastIndexOf('.') + 1)
                : objectPath;
        return repositoryColumnCanonicalName(platformColumn).equals(repositoryColumnCanonicalName(leafName))
                || repositoryColumnCanonicalName(platformColumn).equals(repositoryColumnCanonicalName(objectPath));
    }

    default String repositoryColumnCanonicalName(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    default boolean isRepositoryAppDdlIndexPath(String objectType, String repositoryObjectPath) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            return false;
        }
        for (AppIndex appIndex : appTable.getAppIndexes()) {
            for (String indexPath : appIndex.getPaths()) {
                if (indexPath.equals(repositoryObjectPath)
                        || repositoryObjectPath.equals(appDdlIndexObjectPath(appTable, objectType, indexPath))) {
                    return true;
                }
            }
        }
        return appTable.getIndexes().contains(repositoryObjectPath);
    }

    default boolean matchesRepositoryArgument(SppService service, int argumentIndex, String objectPath) {
        if (argumentIndex < 0 || argumentIndex >= service.getServiceBody().getSppLocalVarList().size()) {
            return false;
        }
        SppLocalVar argument = service.getServiceBody().getLocalVar(argumentIndex);
        return argument.getType() != null
                && argument.getArrayDimension() == 0
                && argument.getType().getName().equals(RepositoryHydrationRule.objectType(objectPath));
    }

    default String relativeRepositoryArgumentPath(String repositoryObjectPath, String argumentType) {
        String objectType = RepositoryHydrationRule.objectType(repositoryObjectPath);
        SppCompilationUnit objectUnit = getSppDomian().getSppClass(objectType);
        if (!(objectUnit instanceof SppClass currentClass)) {
            return null;
        }
        String[] parts = RepositoryHydrationRule.objectIdPath(repositoryObjectPath).split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            SppField field = currentClass.getField(parts[i]);
            if (field == null || !(field.getType() instanceof SppClass nestedClass)) {
                return null;
            }
            if (argumentType.equals(nestedClass.getName())) {
                String relativePath = String.join(".", java.util.Arrays.copyOfRange(parts, i + 1, parts.length));
                if (hasFieldPath(nestedClass, relativePath)) {
                    return relativePath;
                }
                return null;
            }
            currentClass = nestedClass;
        }
        return null;
    }

    default void validateArgumentRepositoryRules(AppParser.ScopeItemIdentifierContext ctx, ScopeItem scopeItem) {
        if (scopeItem.getService() == null) {
            return;
        }
        for (RepositoryHydrationRule rule : scopeItem.getRepositoryHydrationRules()) {
            for (KeyTarget target : rule.keyTargets()) {
                int argumentIndex = target.argumentIndex();
                SppService service = scopeItem.getService();
                if (argumentIndex < 0 || argumentIndex >= service.getServiceBody().getSppLocalVarList().size()) {
                    logSppError(ctx, "Repository argument index " + argumentIndex
                            + " is outside service " + service.getName() + " parameter list.");
                    continue;
                }
                SppLocalVar argument = service.getServiceBody().getLocalVar(argumentIndex);
                if (argument.getType() == null) {
                    logSppError(ctx, "Repository argument(" + argumentIndex + ", "
                            + target.objectPath() + ") object type must match service parameter type <unknown>.");
                    continue;
                }
                if (!repositoryArgumentMatchesParameter(argument, target)) {
                    logSppError(ctx, "Repository argument(" + argumentIndex + ", "
                            + target.objectPath() + ") object type must match service parameter type "
                            + argument.getType().getName() + ".");
                }
            }
        }
    }

    default boolean repositoryArgumentMatchesParameter(SppLocalVar argument, KeyTarget target) {
        if (argument == null || argument.getType() == null || target == null || argument.getArrayDimension() > 1) {
            return false;
        }
        String argumentType = argument.getType().getName();
        String objectType = target.getObjectType();
        return argumentType.equals(objectType)
                || argumentType.equals(RepositoryHydrationRule.objectType(target.repositoryObjectPath()))
                || roleTakesEntity(argumentType, objectType);
    }

    default void validateArgumentSnapshotRules(AppParser.ScopeItemIdentifierContext ctx, ScopeItem scopeItem) {
        if (scopeItem.getService() == null) {
            return;
        }
        for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
            if (rule.source() != ObjectSnapshotRule.Source.argument) {
                continue;
            }
            int argumentIndex = rule.argumentIndex();
            SppService service = scopeItem.getService();
            if (argumentIndex < 0 || argumentIndex >= service.getServiceBody().getSppLocalVarList().size()) {
                logSppError(ctx, "Snapshot argument index " + argumentIndex
                        + " is outside service " + service.getName() + " parameter list.");
                continue;
            }
            SppLocalVar argument = service.getServiceBody().getLocalVar(argumentIndex);
            if (argument.getType() == null) {
                logSppError(ctx, "Snapshot argument(" + argumentIndex + ", "
                        + rule.objectPath() + ") object type must match service parameter type <unknown>.");
                continue;
            }
            if (argument.getType().getName().equals(rule.getObjectType())) {
                continue;
            }
            if (rule.appDdlInferred() && argument.getArrayDimension() == 0
                    && argument.getType() instanceof SppClass argumentClass
                    && rule.objectIdPaths().stream().allMatch(path -> hasFieldPath(argumentClass, path))) {
                continue;
            }
            if (!argument.getType().getName().equals(rule.getObjectType())) {
                logSppError(ctx, "Snapshot argument(" + argumentIndex + ", "
                        + rule.objectPath() + ") object type must match service parameter type "
                        + argument.getType().getName() + ".");
            }
        }
    }

    default void validateSnapshotObjectPath(AppParser.SnapshotTargetContext ctx, ObjectSnapshotRule rule) {
        validateSnapshotObjectPath((org.antlr.v4.runtime.ParserRuleContext) ctx, rule);
    }

    default void validateSnapshotObjectPath(org.antlr.v4.runtime.ParserRuleContext ctx, ObjectSnapshotRule rule) {
        SppCompilationUnit objectType = getSppDomian().getSppClass(rule.getObjectType());
        if (!(objectType instanceof SppClass objectClass)) {
            logSppError(ctx, "Snapshot object type " + rule.getObjectType() + " not defined.");
            return;
        }
        for (String objectIdPath : rule.objectIdPaths()) {
            String objectPath = rule.getObjectType() + "." + objectIdPath;
            if (objectIdPath.isBlank()) {
                logSppError(ctx, "Snapshot " + rule.objectPath()
                        + " must include an object id field path.");
                continue;
            }
            if (!hasFieldPath(objectClass, objectIdPath)) {
                if (isAppDdlPlatformSnapshotPath(rule.getObjectType(), objectIdPath)
                        || isRoleTakeSnapshotPath(rule.getObjectType(), objectIdPath)) {
                    continue;
                }
                logSppError(ctx, "Snapshot object id path " + objectPath
                        + " not found.");
            }
        }
    }

    default void validateSnapshotObjectPath(org.antlr.v4.runtime.ParserRuleContext ctx,
                                            SppService service,
                                            ObjectSnapshotRule rule) {
        if (service == null || rule.source() != ObjectSnapshotRule.Source.argument
                || rule.argumentIndex() < 0
                || rule.argumentIndex() >= service.getServiceBody().getSppLocalVarList().size()) {
            validateSnapshotObjectPath(ctx, rule);
            return;
        }
        SppLocalVar argument = service.getServiceBody().getLocalVar(rule.argumentIndex());
        if (argument.getType() == null || argument.getArrayDimension() != 0
                || rule.getObjectType().equals(argument.getType().getName())) {
            validateSnapshotObjectPath(ctx, rule);
            return;
        }
        SppCompilationUnit snapshotObjectType = getSppDomian().getSppClass(rule.getObjectType());
        if (!(snapshotObjectType instanceof SppClass)) {
            logSppError(ctx, "Snapshot object type " + rule.getObjectType() + " not defined.");
            return;
        }
        if (!(argument.getType() instanceof SppClass argumentClass)) {
            validateSnapshotObjectPath(ctx, rule);
            return;
        }
        for (String objectIdPath : rule.objectIdPaths()) {
            String objectPath = argument.getType().getName() + "." + objectIdPath;
            if (objectIdPath.isBlank()) {
                logSppError(ctx, "Snapshot " + rule.objectPath()
                        + " must include an object id field path.");
                continue;
            }
            if (!hasFieldPath(argumentClass, objectIdPath)) {
                if (isAppDdlPlatformSnapshotPath(rule.getObjectType(), objectIdPath)
                        || isRoleTakeSnapshotPath(rule.getObjectType(), objectIdPath)) {
                    continue;
                }
                logSppError(ctx, "Snapshot object id path " + objectPath
                        + " not found.");
            }
        }
    }

    default boolean isAppDdlPlatformSnapshotPath(String objectType, String objectIdPath) {
        if (objectType == null || objectIdPath == null || objectIdPath.isBlank() || objectIdPath.contains(".")) {
            return false;
        }
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            return false;
        }
        return appTable.getAppColumns().stream()
                .anyMatch(column -> objectIdPath.equals(column.getName()) && !column.isObjectBacked());
    }

    default boolean isRoleTakeSnapshotPath(String objectType, String objectIdPath) {
        if (objectType == null || objectIdPath == null || objectIdPath.isBlank()) {
            return false;
        }
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            return false;
        }
        List<RoleTakeKeyTarget> targets = roleTakeKeyTargets(objectType, platformPrimaryKeys(appTable));
        for (RoleTakeKeyTarget target : targets) {
            String roleTakePath = roleTakeRepositoryObjectPath(target);
            if (objectIdPath.equals(roleTakePath)
                    || objectIdPath.equals(RepositoryHydrationRule.objectIdPath(roleTakePath))
                    || objectIdPath.equals(RepositoryHydrationRule.objectIdPath(target.argumentPath()))) {
                return true;
            }
        }
        return false;
    }

    default void validateRepositoryObjectPath(AppParser.RepositoryTargetContext ctx, KeyTarget target) {
        if (target.isSingleton() || target.isPlatform()) return;
        validateRepositoryObjectPath((org.antlr.v4.runtime.ParserRuleContext) ctx, target);
    }

    default void validateRepositoryObjectPath(org.antlr.v4.runtime.ParserRuleContext ctx, KeyTarget target) {
        SppCompilationUnit objectType = getSppDomian().getSppClass(target.getObjectType());
        if (!(objectType instanceof SppClass objectClass)) {
            logSppError(ctx, "Repository object type " + target.getObjectType() + " not defined.");
            return;
        }
        if (target.getObjectIdPath().isBlank()) {
            logSppError(ctx, "Repository " + target.objectPath()
                    + " must include an object id field path.");
            return;
        }
        if (com.servpp.lang.models.ParticipantRoleBindings.isStorageIdentityPath(getSppDomian(),objectClass,target.getObjectIdPath())) return;
        if (!hasFieldPath(objectClass, target.getObjectIdPath())) {
            if (isAppDdlPlatformRepositoryTarget(target)) {
                return;
            }
            logSppError(ctx, "Repository object id path " + target.objectPath()
                    + " not found.");
        }
    }

    default boolean isAppDdlPlatformRepositoryTarget(KeyTarget target) {
        AppTable appTable = findAppTableByBusinessType(target.getObjectType());
        if (appTable == null) {
            return false;
        }
        String repositoryPath = target.getRepositoryObjectIdPath();
        if (repositoryPath == null || repositoryPath.isBlank() || repositoryPath.contains(".")) {
            return false;
        }
        return appTable.getAppColumns().stream()
                .anyMatch(column -> repositoryPath.equals(column.getName()) && !column.isObjectBacked());
    }

    default void validateRepositoryKeyObjectTypes(AppParser.RepositoryModifierContext ctx,
                                                  RepositoryHydrationRule rule) {
        validateRepositoryKeyObjectTypes((org.antlr.v4.runtime.ParserRuleContext) ctx, rule);
    }

    default void validateRepositoryKeyObjectTypes(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                  RepositoryHydrationRule rule) {
        if (rule.keyTargets().isEmpty()) {
            return;
        }
        String objectType = rule.getObjectType();
        for (KeyTarget target : rule.keyTargets()) {
            String repositoryObjectType = RepositoryHydrationRule.objectType(target.repositoryObjectPath());
            if (!objectType.equals(repositoryObjectType)) {
                logSppError(ctx, "Repository rule for target " + rule.responseMethodName()
                        + " must use one object type, but found " + objectType
                        + " and " + repositoryObjectType + ".");
            }
        }
    }

    default List<KeyTarget> repositoryTargetsFromAppDdl(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                        String objectType) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            logSppError(ctx, "Repository " + objectType
                    + " must map to an APP DDL table with primary keys.");
            return List.of();
        }
        List<KeyTarget> roleTakePrimaryKeyTargets = repositoryTargetsFromRoleTakePrimaryKeys(ctx, appTable,
                objectType);
        if (!roleTakePrimaryKeyTargets.isEmpty()) {
            return roleTakePrimaryKeyTargets;
        }
        List<String> keyPaths = appDdlPrimaryKeyPaths(ctx, appTable, objectType, new HashSet<>(), "Repository");
        List<KeyTarget> targets = new ArrayList<>();
        for (String keyPath : keyPaths) {
            targets.add(new KeyTarget(0, repositoryIdentityObjectPath(keyPath),keyPath));
            validateRepositoryObjectPath(ctx, targets.get(targets.size() - 1));
        }
        if (targets.isEmpty()) {
            logSppError(ctx, "Repository " + objectType
                    + " must map to APP DDL primary keys that can be read from argument(0).");
        }
        return targets;
    }

    default String repositoryIdentityObjectPath(String path) {
        String[] parts=path.split("\\.");
        var root=getSppDomian().getSppClass(parts[0]);
        if (!(root instanceof SppClass current)) return path;
        for(int i=1;i<parts.length;i++) {
            var field=current.getField(parts[i]);
            if(field==null && i==parts.length-1) {
                var table=findAppTableByBusinessType(current.getName());
                if(table!=null && table.isIndependentIdentity() && platformPrimaryKeys(table).equals(List.of(parts[i]))) {
                    parts[i]="@storageIdentity";return String.join(".",parts);
                }
            }
            if(field==null || field.getArrayDimension()!=0 || !(field.getType() instanceof SppClass nested)) return path;
            current=nested;
        }
        return path;
    }

    default List<KeyTarget> repositoryTargetsFromRoleTakePrimaryKeys(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                                     AppTable appTable,
                                                                     String objectType) {
        if (appTable.isIndependentIdentity()) return List.of();
        List<String> platformPrimaryKeys = platformPrimaryKeys(appTable);
        if (platformPrimaryKeys.isEmpty()) {
            return List.of();
        }
        List<RoleTakeKeyTarget> roleTakeKeys = roleTakeKeyTargets(objectType, platformPrimaryKeys);
        if (roleTakeKeys.isEmpty()) {
            return List.of();
        }
        if (roleTakeKeys.size() != platformPrimaryKeys.size()) {
            logSppError(ctx, "Repository " + objectType
                    + " has APP DDL platform primary keys that only partially match take(...) performers.");
            return List.of();
        }
        List<KeyTarget> targets = new ArrayList<>();
        for (RoleTakeKeyTarget roleTakeKey : roleTakeKeys) {
            KeyTarget target = new KeyTarget(0, roleTakeRepositoryObjectPath(roleTakeKey),
                    objectType + "." + roleTakeKey.platformColumn());
            validateRepositoryObjectPath(ctx, target);
            targets.add(target);
        }
        return targets;
    }

    default String repositoryPrimaryKeySourceHint(String objectType, List<KeyTarget> targets) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null || targets == null || targets.isEmpty()) {
            return null;
        }
        List<String> platformPrimaryKeys = platformPrimaryKeys(appTable);
        if (platformPrimaryKeys.isEmpty() || platformPrimaryKeys.size() != targets.size()) {
            return null;
        }
        List<RoleTakeKeyTarget> roleTakeKeys = roleTakeKeyTargets(objectType, platformPrimaryKeys);
        if (roleTakeKeys.size() != platformPrimaryKeys.size()) {
            return repositoryPrimaryKeyTargetsMatch(objectType, platformPrimaryKeys, targets)
                    ? "APP_DDL_PRIMARY_KEY"
                    : null;
        }
        for (int i = 0; i < targets.size(); i++) {
            KeyTarget target = targets.get(i);
            String roleTakePath = roleTakeRepositoryObjectPath(roleTakeKeys.get(i));
            String platformPath = objectType + "." + platformPrimaryKeys.get(i);
            if (!roleTakePath.equals(target.objectPath()) || !platformPath.equals(target.repositoryObjectPath())) {
                return repositoryPrimaryKeyTargetsMatch(objectType, platformPrimaryKeys, targets)
                        ? "APP_DDL_PRIMARY_KEY"
                        : null;
            }
        }
        return "APP_DDL_ROLE_TAKE_PRIMARY_KEY";
    }

    default String roleTakeRepositoryObjectPath(RoleTakeKeyTarget roleTakeKey) {
        return roleTakeKey.performerType() + "."
                + RepositoryHydrationRule.objectIdPath(roleTakeKey.argumentPath());
    }

    default String repositoryForeignTypeSourceHint(String objectType, List<KeyTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        return targets.stream().anyMatch(target -> !objectType.equals(target.getObjectType())
                        && repositoryTargetUsesPlatformColumn(target))
                ? "APP_DDL_ROLE_TAKE"
                : "APP_DDL_FOREIGN_KEY";
    }

    default String repositoryIndexSourceHint(String objectType, List<KeyTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        return targets.stream().anyMatch(target -> !objectType.equals(target.getObjectType())
                        && repositoryTargetUsesPlatformColumn(target))
                ? "APP_DDL_ROLE_TAKE"
                : "APP_DDL_NAMED_INDEX";
    }

    default boolean repositoryTargetUsesPlatformColumn(KeyTarget target) {
        String repositoryPath = RepositoryHydrationRule.objectIdPath(target.repositoryObjectPath());
        return repositoryPath != null && !repositoryPath.contains(".");
    }

    default boolean repositoryPrimaryKeyTargetsMatch(String objectType, List<String> platformPrimaryKeys,
                                                     List<KeyTarget> targets) {
        if (platformPrimaryKeys == null || targets == null || platformPrimaryKeys.size() != targets.size()) {
            return false;
        }
        for (int i = 0; i < targets.size(); i++) {
            KeyTarget target = targets.get(i);
            String platformPath = objectType + "." + platformPrimaryKeys.get(i);
            if (!platformPath.equals(target.objectPath()) || !platformPath.equals(target.repositoryObjectPath())) {
                return false;
            }
        }
        return true;
    }

    default List<String> platformPrimaryKeys(AppTable appTable) {
        if (appTable == null) {
            return List.of();
        }
        return appTable.getAppColumns().stream()
                .filter(AppColumn::isPrimaryKey)
                .filter(column -> !column.isObjectBacked())
                .map(AppColumn::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    default List<KeyTarget> repositoryTargetsFromAppDdlForeignKey(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                                  String objectType,
                                                                  String foreignType) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            logSppError(ctx, "Repository " + objectType
                    + " must map to an APP DDL table with foreign keys.");
            return List.of();
        }
        List<String> indexPaths = repositoryIndexPathsForType(appTable, objectType, foreignType);
        if (indexPaths.size() == 1) {
            KeyTarget target = new KeyTarget(0, indexPaths.get(0));
            validateRepositoryObjectPath(ctx, target);
            return List.of(target);
        }
        if (indexPaths.size() > 1) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " is ambiguous across APP DDL indexes; use repository(" + objectType
                    + " by index <name>) or repository(argument(...), ...) as a compatibility"
                    + " escape hatch.");
            return List.of();
        }
        List<KeyTarget> roleTakeTargets = repositoryTargetsFromRoleTakeIndex(ctx, appTable, objectType, foreignType);
        if (!roleTakeTargets.isEmpty()) {
            return roleTakeTargets;
        }
        AppTable foreignTable = findAppTableByBusinessType(foreignType);
        List<SppField> foreignFields = repositoryForeignKeyFields(appTable, foreignType);
        if (foreignFields.isEmpty()) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " must match one APP DDL index or foreign key on " + appTable.getName() + ".");
            return List.of();
        }
        if (foreignFields.size() > 1) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " is ambiguous; declare a named APP DDL index and use repository(" + objectType
                    + " by index <name>), or use repository(argument(...), ...) as a compatibility"
                    + " escape hatch.");
            return List.of();
        }
        if (foreignTable == null) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " must reference an APP DDL table with primary keys or declare an APP DDL index.");
            return List.of();
        }
        SppField foreignField = foreignFields.get(0);
        List<String> foreignKeyPaths = appDdlPrimaryKeyPaths(ctx, foreignTable, foreignType, new HashSet<>(),
                "Repository");
        List<KeyTarget> targets = new ArrayList<>();
        for (String foreignKeyPath : foreignKeyPaths) {
            String repositoryObjectPath = objectType + "." + foreignField.getName() + "."
                    + RepositoryHydrationRule.objectIdPath(foreignKeyPath);
            targets.add(new KeyTarget(0, foreignKeyPath, repositoryObjectPath));
            validateRepositoryObjectPath(ctx, targets.get(targets.size() - 1));
        }
        if (targets.isEmpty()) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " must map to APP DDL foreign key target primary keys that can be read from argument(0).");
        }
        return targets;
    }

    default List<KeyTarget> repositoryTargetsFromRoleTakeIndex(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                               AppTable appTable,
                                                               String objectType,
                                                               String foreignType) {
        if (!roleTakesEntity(objectType, foreignType)) {
            return List.of();
        }
        AppTable foreignTable = findAppTableByBusinessType(foreignType);
        if (foreignTable == null) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " must reference an APP DDL table with primary keys or declare an APP DDL index.");
            return List.of();
        }
        List<String> foreignKeyPaths = appDdlPrimaryKeyPaths(ctx, foreignTable, foreignType, new HashSet<>(),
                "Repository");
        if (foreignKeyPaths.isEmpty()) {
            return List.of();
        }
        List<String> roleTakeIndexPaths = repositoryRoleTakeIndexPaths(appTable, foreignType, foreignKeyPaths);
        if (roleTakeIndexPaths.isEmpty()) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " is declared by role take but must match one APP DDL index column on "
                    + appTable.getName() + ".");
            return List.of();
        }
        if (roleTakeIndexPaths.size() != foreignKeyPaths.size()) {
            logSppError(ctx, "Repository " + objectType + " by " + foreignType
                    + " role take index key count does not match " + foreignType + " primary keys.");
            return List.of();
        }
        List<KeyTarget> targets = new ArrayList<>();
        for (int i = 0; i < foreignKeyPaths.size(); i++) {
            targets.add(new KeyTarget(0, foreignKeyPaths.get(i), objectType + "." + roleTakeIndexPaths.get(i)));
        }
        return targets;
    }

    default boolean roleTakesEntity(String roleType, String entityType) {
        for (SppCompilationUnit unit : getSppDomian().getMapsOfClass().values()) {
            if (!(unit instanceof SppRoleMapper mapper) || mapper.getRole() == null
                    || !roleType.equals(mapper.getRole().getName()) || mapper.getEntities() == null) {
                continue;
            }
            for (SppClass entity : mapper.getEntities()) {
                if (entity != null && entityType.equals(entity.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    default List<String> repositoryRoleTakeIndexPaths(AppTable appTable, String foreignType,
                                                      List<String> foreignKeyPaths) {
        List<String> paths = new ArrayList<>();
        for (String foreignKeyPath : foreignKeyPaths) {
            String primaryKeyName = RepositoryHydrationRule.objectIdPath(foreignKeyPath);
            String matchedPath = repositoryRoleTakeIndexPath(appTable, foreignType, primaryKeyName);
            if (matchedPath == null) {
                return List.of();
            }
            paths.add(matchedPath);
        }
        return paths;
    }

    default String repositoryRoleTakeIndexPath(AppTable appTable, String foreignType, String primaryKeyName) {
        String expected = normalizeRepositoryColumn(foreignType + "_" + primaryKeyName);
        for (AppIndex appIndex : appTable.getAppIndexes()) {
            for (String path : appIndex.getPaths()) {
                String platformColumn = appDdlPlatformColumnPath(appTable, path);
                if (platformColumn != null && normalizeRepositoryColumn(platformColumn).equals(expected)) {
                    return platformColumn;
                }
            }
        }
        return null;
    }

    default String normalizeRepositoryColumn(String value) {
        return value == null ? "" : value.replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }

    default List<RoleTakeKeyTarget> roleTakeKeyTargets(String roleType, List<String> platformColumns) {
        List<RoleTakeKeyTarget> targets = new ArrayList<>();
        for (String platformColumn : platformColumns) {
            RoleTakeKeyTarget target = roleTakeKeyTarget(roleType, platformColumn);
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    default RoleTakeKeyTarget roleTakeKeyTarget(String roleType, String platformColumn) {
        for (SppCompilationUnit unit : getSppDomian().getMapsOfClass().values()) {
            if (!(unit instanceof SppRoleMapper mapper) || mapper.getRole() == null
                    || !roleType.equals(mapper.getRole().getName()) || mapper.getEntities() == null) {
                continue;
            }
            for (SppClass performer : mapper.getEntities()) {
                RoleTakeKeyTarget target = roleTakeKeyTarget(roleType, performer, platformColumn);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    default RoleTakeKeyTarget roleTakeKeyTarget(String roleType, SppClass performer, String platformColumn) {
        if (performer == null || performer.getName() == null || platformColumn == null) {
            return null;
        }
        platformColumn = appDdlPlatformColumnPath(findAppTableByBusinessType(roleType), platformColumn);
        if (platformColumn == null) {
            return null;
        }
        AppTable performerTable = findAppTableByBusinessType(performer.getName());
        List<String> performerPrimaryKeys = performerTable == null
                ? performerReferenceIdentityPaths(performer)
                : appDdlPrimaryKeyPaths(null, performerTable, performer.getName(), new HashSet<>(), "Repository");
        for (String performerPrimaryKey : performerPrimaryKeys) {
            String primaryKeyName = RepositoryHydrationRule.objectIdPath(performerPrimaryKey);
            String expected = normalizeRepositoryColumn(performer.getName() + "_" + primaryKeyName);
            if (!normalizeRepositoryColumn(platformColumn).equals(expected)) {
                continue;
            }
            String performerRuntimePath = rolePerformerRuntimePath(roleType, performer.getName());
            if (performerRuntimePath == null) {
                return null;
            }
            return new RoleTakeKeyTarget(performerRuntimePath + "." + primaryKeyName, platformColumn,
                    performer.getName());
        }
        return null;
    }

    default List<String> performerReferenceIdentityPaths(SppClass performer) {
        if (performer == null || performer.getName() == null) {
            return List.of();
        }
        List<String> candidateNames = List.of(
                Character.toLowerCase(performer.getName().charAt(0)) + performer.getName().substring(1) + "Id",
                "id");
        for (String candidateName : candidateNames) {
            SppField field = performer.getField(candidateName);
            if (field != null) {
                return List.of(performer.getName() + "." + candidateName);
            }
        }
        return performer.getSppFieldList().isEmpty()
                ? List.of()
                : List.of(performer.getName() + "." + performer.getSppFieldList().get(0).getName());
    }

    default String appDdlPlatformColumnPath(AppTable appTable, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String columnName = path;
        if (path.contains(".")) {
            if (appTable == null || appTable.getEntity() == null) {
                return null;
            }
            String prefix = path.substring(0, path.indexOf('.'));
            if (!appTable.getEntity().getName().equals(prefix)) {
                return null;
            }
            columnName = path.substring(path.indexOf('.') + 1);
        }
        String resolvedColumnName = columnName;
        return appTable == null || appTable.getAppColumns() == null
                ? null
                : appTable.getAppColumns().stream()
                        .filter(column -> resolvedColumnName.equals(column.getName()) && !column.isObjectBacked())
                        .map(AppColumn::getName)
                        .findFirst()
                        .orElse(null);
    }

    default String rolePerformerRuntimePath(String roleType, String performerType) {
        String performerFieldName = rolePerformerFieldName(roleType, performerType);
        if (performerFieldName != null) {
            return performerFieldName;
        }
        if (performerType == null || performerType.isBlank()) {
            return null;
        }
        return Character.toLowerCase(performerType.charAt(0)) + performerType.substring(1);
    }

    default String rolePerformerFieldName(String roleType, String performerType) {
        SppCompilationUnit roleUnit = getSppDomian().getSppClass(roleType);
        if (!(roleUnit instanceof SppClass roleClass)) {
            return null;
        }
        for (SppField field : roleClass.getSppFieldList()) {
            if (field != null && field.getType() != null && performerType.equals(field.getType().getName())) {
                return field.getName();
            }
        }
        return null;
    }

    record RoleTakeKeyTarget(String argumentPath, String platformColumn, String performerType) {
    }

    default List<KeyTarget> repositoryTargetsFromAppDdlIndex(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                             String objectType,
                                                             String indexName) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            logSppError(ctx, "Repository " + objectType
                    + " must map to an APP DDL table with indexes.");
            return List.of();
        }
        List<String> indexPaths = repositoryIndexPathsByName(appTable, indexName);
        if (indexPaths.isEmpty()) {
            logSppError(ctx, "Repository " + objectType + " by index " + indexName
                    + " must match one named APP DDL index on " + appTable.getName() + ".");
            return List.of();
        }
        List<KeyTarget> targets = new ArrayList<>();
        for (String indexPath : indexPaths) {
            RoleTakeKeyTarget roleTakeKey = roleTakeKeyTarget(objectType, indexPath);
            KeyTarget target = roleTakeKey == null
                    ? new KeyTarget(0, appDdlIndexObjectPath(appTable, objectType, indexPath))
                    : new KeyTarget(0, roleTakeKey.performerType() + "."
                    + RepositoryHydrationRule.objectIdPath(roleTakeKey.argumentPath()),
                    objectType + "." + roleTakeKey.platformColumn());
            validateRepositoryObjectPath(ctx, target);
            targets.add(target);
        }
        return targets;
    }

    default List<String> repositoryIndexPathsForType(AppTable appTable, String objectType, String foreignType) {
        List<String> indexPaths = new ArrayList<>();
        for (AppIndex appIndex : appTable.getAppIndexes()) {
            for (String indexPath : appIndex.getPaths()) {
                if (repositoryIndexPathMatches(indexPath, objectType, foreignType)) {
                    indexPaths.add(indexPath);
                }
            }
        }
        return indexPaths;
    }

    default List<String> repositoryIndexPathsByName(AppTable appTable, String indexName) {
        for (AppIndex appIndex : appTable.getAppIndexes()) {
            if (indexName.equals(appIndex.getName())) {
                return appIndex.getPaths();
            }
        }
        return List.of();
    }

    default boolean repositoryIndexPathMatches(String indexPath, String objectType, String foreignType) {
        if (indexPath == null || !indexPath.startsWith(objectType + ".")) {
            return false;
        }
        SppCompilationUnit unit = getSppDomian().getSppClass(objectType);
        if (!(unit instanceof SppClass currentClass)) {
            return false;
        }
        boolean matchedForeignType = false;
        String[] parts = indexPath.split("\\.");
        for (int i = 1; i < parts.length; i++) {
            SppField field = currentClass.getField(parts[i]);
            if (field == null || field.getArrayDimension() != 0) {
                return false;
            }
            boolean last = i == parts.length - 1;
            if (last) {
                return matchedForeignType && isScalarRepositoryField(field);
            }
            if (!(field.getType() instanceof SppClass nextClass)) {
                return false;
            }
            if (foreignType.equals(nextClass.getName())) {
                matchedForeignType = true;
            }
            currentClass = nextClass;
        }
        return false;
    }

    default List<SppField> repositoryForeignKeyFields(AppTable appTable, String foreignType) {
        List<SppField> fields = new ArrayList<>();
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field == null || field.getType() == null) {
                    continue;
                }
                boolean declaredMatch = appForeign.getForeignReference() != null
                        && foreignType.equals(appForeign.getForeignReference().getName());
                boolean fieldTypeMatch = foreignType.equals(field.getType().getName());
                if (declaredMatch || fieldTypeMatch) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    default List<String> snapshotPathsFromAppDdl(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                 String objectType) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            logSppError(ctx, "Snapshot " + objectType
                    + " must map to an APP DDL table with primary keys.");
            return List.of();
        }
        List<String> roleTakePrimaryKeys = snapshotPathsFromRoleTakePrimaryKeys(ctx, appTable, objectType);
        if (!roleTakePrimaryKeys.isEmpty()) {
            return roleTakePrimaryKeys;
        }
        return appDdlPrimaryKeyPaths(ctx, appTable, objectType, new HashSet<>(), "Snapshot");
    }

    default List<String> snapshotPathsFromRoleTakePrimaryKeys(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                              AppTable appTable,
                                                              String objectType) {
        List<String> platformPrimaryKeys = platformPrimaryKeys(appTable);
        if (platformPrimaryKeys.isEmpty()) {
            return List.of();
        }
        List<RoleTakeKeyTarget> roleTakeKeys = roleTakeKeyTargets(objectType, platformPrimaryKeys);
        if (roleTakeKeys.isEmpty()) {
            return List.of();
        }
        if (roleTakeKeys.size() != platformPrimaryKeys.size()) {
            logSppError(ctx, "Snapshot " + objectType
                    + " has APP DDL platform primary keys that only partially match take(...) performers.");
            return List.of();
        }
        return roleTakeKeys.stream()
                .map(this::roleTakeRepositoryObjectPath)
                .toList();
    }

    default List<String> snapshotPathsFromAppDdlIndex(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                      String objectType,
                                                      String referenceType) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            logSppError(ctx, "Snapshot " + objectType
                    + " must map to an APP DDL table with indexes.");
            return List.of();
        }
        List<String> indexPaths = repositoryIndexPathsForType(appTable, objectType, referenceType);
        if (indexPaths.isEmpty()) {
            logSppError(ctx, "Snapshot " + objectType + " by " + referenceType
                    + " must match one APP DDL index on " + appTable.getName() + ".");
        }
        return indexPaths;
    }

    default List<String> snapshotPathsFromNamedAppDdlIndex(org.antlr.v4.runtime.ParserRuleContext ctx,
                                                           String objectType,
                                                           String indexName) {
        AppTable appTable = findAppTableByBusinessType(objectType);
        if (appTable == null) {
            logSppError(ctx, "Snapshot " + objectType
                    + " must map to an APP DDL table with indexes.");
            return List.of();
        }
        List<String> indexPaths = repositoryIndexPathsByName(appTable, indexName);
        if (indexPaths.isEmpty()) {
            logSppError(ctx, "Snapshot " + objectType + " by index " + indexName
                    + " must match one named APP DDL index on " + appTable.getName() + ".");
        }
        return indexPaths.stream()
                .map(indexPath -> appDdlIndexObjectPath(appTable, objectType, indexPath))
                .toList();
    }

    default String appDdlIndexObjectPath(AppTable appTable, String objectType, String indexPath) {
        if (indexPath == null || indexPath.contains(".")) {
            return indexPath;
        }
        if (appTable == null || appTable.getEntity() == null || !objectType.equals(appTable.getEntity().getName())) {
            return indexPath;
        }
        if (appTable.getEntity().getField(indexPath) != null) {
            return objectType + "." + indexPath;
        }
        String objectPath = objectPathForPlatformIndexColumn(appTable, indexPath);
        if (objectPath != null) {
            return objectType + "." + objectPath;
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (indexPath.equals(column.getName()) && !column.isObjectBacked()) {
                return objectType + "." + indexPath;
            }
        }
        return indexPath;
    }

    default String objectPathForPlatformIndexColumn(AppTable appTable, String columnName) {
        if (appTable == null || appTable.getEntity() == null || columnName == null || columnName.isBlank()) {
            return null;
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (columnName.equals(column.getName()) && !column.isObjectBacked()) {
                return objectPathForGeneratedColumn(appTable, appTable.getEntity(), "", columnName,
                        false, new HashSet<>());
            }
        }
        return null;
    }

    default String objectPathForGeneratedColumn(AppTable appTable, SppClass currentClass, String pathPrefix,
                                                String columnName, boolean duplicateTypeField,
                                                Set<String> visitingTypes) {
        if (appTable == null || currentClass == null || columnName == null
                || !visitingTypes.add(currentClass.getName())) {
            return null;
        }
        for (SppLocalVar field : currentClass.getSppFieldList()) {
            if (!(field instanceof SppField sppField) || field.getArrayDimension() != 0) {
                continue;
            }
            String objectPath = pathPrefix.isBlank() ? field.getName() : pathPrefix + "." + field.getName();
            if (isScalarRepositoryField(sppField)) {
                String generatedColumn = generatedAppIndexColumnName(appTable, pathPrefix, field.getName(),
                        duplicateTypeField);
                if (columnName.equals(generatedColumn)) {
                    visitingTypes.remove(currentClass.getName());
                    return objectPath;
                }
                continue;
            }
            if (field.getType() instanceof SppClass nestedClass) {
                String generatedPrefix = generatedAppIndexColumnName(appTable, pathPrefix, field.getName(),
                        duplicateTypeField);
                String nestedPath = objectPathForGeneratedColumn(appTable, nestedClass, objectPath, columnName,
                        containsSameType(currentClass, field), visitingTypes);
                if (nestedPath != null) {
                    visitingTypes.remove(currentClass.getName());
                    return nestedPath;
                }
                String prefixMatchedPath = objectPathForGeneratedColumn(appTable, nestedClass, generatedPrefix,
                        columnName, containsSameType(currentClass, field), visitingTypes);
                if (prefixMatchedPath != null) {
                    visitingTypes.remove(currentClass.getName());
                    return prefixMatchedPath;
                }
            }
        }
        visitingTypes.remove(currentClass.getName());
        return null;
    }

    default String generatedAppIndexColumnName(AppTable appTable, String prefix, String fieldName,
                                               boolean duplicateTypeField) {
        if (prefix == null || prefix.isBlank()) {
            return fieldName;
        }
        String pascalName = NameUtil.firstToLowerCase(prefix, false)
                + NameUtil.firstToLowerCase(fieldName, false);
        return switch (appTableNamingType(appTable, duplicateTypeField)) {
            case "lowercase" -> fieldName.toLowerCase(Locale.ROOT);
            case "UPPERCASE" -> fieldName.toUpperCase(Locale.ROOT);
            case "camelCase" -> NameUtil.firstToLowerCase(pascalName, true);
            case "PascalCase" -> pascalName;
            case "pascallowercase" -> pascalName.toLowerCase(Locale.ROOT);
            case "PASCALUPPERCASE" -> pascalName.toUpperCase(Locale.ROOT);
            case "snake_case" -> (prefix + "_" + fieldName).toLowerCase(Locale.ROOT);
            default -> (prefix + "_" + fieldName).toLowerCase(Locale.ROOT);
        };
    }

    default String appTableNamingType(AppTable appTable, boolean duplicateTypeField) {
        AppIndexNamingConfig namingConfig = new AppIndexNamingConfig();
        if (appTable != null) {
            for (AppTables appTables : getCurrentRuleBlock().getAppTables()) {
                if (appTables.getAppTableList().contains(appTable)) {
                    applyInsideEntityNamingConfig(namingConfig, appTables.getAnnotations());
                    break;
                }
            }
            applyInsideEntityNamingConfig(namingConfig, appTable.getAnnotations());
        }
        return duplicateTypeField ? namingConfig.dupNamingType() : namingConfig.namingType();
    }

    default void applyInsideEntityNamingConfig(AppIndexNamingConfig namingConfig,
                                               List<AnnotationDefine> annotations) {
        if (namingConfig == null || annotations == null) {
            return;
        }
        for (AnnotationDefine annotation : annotations) {
            if (!"insideEntity".equals(annotation.getName())) {
                continue;
            }
            String namingType = annotation.getParameters().get("namingType");
            if (namingType != null && !namingType.isBlank()) {
                namingConfig.namingType = namingType;
            }
            String dupNamingType = annotation.getParameters().get("dupNamingType");
            if (dupNamingType != null && !dupNamingType.isBlank()) {
                namingConfig.dupNamingType = dupNamingType;
            }
        }
    }

    class AppIndexNamingConfig {
        private String namingType = "snake_case";
        private String dupNamingType;

        private String namingType() {
            return namingType;
        }

        private String dupNamingType() {
            return dupNamingType == null ? namingType : dupNamingType;
        }
    }

    default boolean containsSameType(SppClass sppClass, SppLocalVar localVar) {
        if (sppClass == null || localVar == null || localVar.getType() == null) {
            return false;
        }
        int count = 0;
        for (SppLocalVar field : sppClass.getSppFieldList()) {
            if (field.getType() != null
                    && field.getType().getName().equals(localVar.getType().getName())
                    && field.getArrayDimension() == localVar.getArrayDimension()) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    default List<String> appDdlPrimaryKeyPaths(org.antlr.v4.runtime.ParserRuleContext ctx,
                                               AppTable appTable,
                                               String rootType,
                                               Set<String> visitingTypes) {
        return appDdlPrimaryKeyPaths(ctx, appTable, rootType, visitingTypes, "Repository");
    }

    default List<String> appDdlPrimaryKeyPaths(org.antlr.v4.runtime.ParserRuleContext ctx,
                                               AppTable appTable,
                                               String rootType,
                                               Set<String> visitingTypes,
                                               String ruleName) {
        if (appTable == null || appTable.getEntity() == null
                || !visitingTypes.add(appTable.getEntity().getName())) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        if ("Repository".equals(ruleName) && visitingTypes.size()>1 && appTable.isIndependentIdentity()
                && platformPrimaryKeys(appTable).size()==1) {
            visitingTypes.remove(appTable.getEntity().getName());
            return List.of(rootType+"."+platformPrimaryKeys(appTable).get(0));
        }
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField field = primaryKey.getSppField();
            if (field == null) {
                continue;
            }
            String primaryKeyPath = primaryKeyObjectPath(primaryKey);
            String roleTakePrimaryKeyPath = roleTakePrimaryKeyPath(rootType, appTable, primaryKeyPath);
            if (roleTakePrimaryKeyPath != null) {
                paths.add(roleTakePrimaryKeyPath);
                continue;
            }
            appendPrimaryKeyFieldPaths(ctx, paths, rootType, primaryKeyPath, field,
                    visitingTypes, ruleName);
        }
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (!appColumn.isPrimaryKey()) {
                continue;
            }
            if (!appColumn.isObjectBacked()) {
                if (appTable.getEntity().getField(appColumn.getName()) != null) {
                    paths.add(rootType + "." + appColumn.getName());
                    continue;
                }
                RoleTakeKeyTarget roleTakeKey = roleTakeKeyTarget(rootType, appColumn.getName());
                if (roleTakeKey != null) {
                    paths.add(rootType + "." + roleTakeKey.argumentPath());
                    continue;
                }
                logSppError(ctx, ruleName + " " + rootType + " cannot infer APP DDL primary key "
                        + appColumn.getName() + " because it is not a business object field; declare a business "
                        + "primarykey(...), APP DDL index, or use " + ruleName.toLowerCase(Locale.ROOT)
                        + "(argument(index, path)) as a compatibility escape hatch.");
                continue;
            }
            paths.add(rootType + "." + appColumn.getName());
        }
        visitingTypes.remove(appTable.getEntity().getName());
        return paths;
    }

    default String roleTakePrimaryKeyPath(String rootType, AppTable appTable, String primaryKeyPath) {
        if (rootType == null || appTable == null || primaryKeyPath == null || primaryKeyPath.isBlank()) {
            return null;
        }
        for (String platformColumn : platformPrimaryKeys(appTable)) {
            RoleTakeKeyTarget roleTakeKey = roleTakeKeyTarget(rootType, platformColumn);
            if (roleTakeKey == null) {
                continue;
            }
            String performerObjectIdPath = RepositoryHydrationRule.objectIdPath(roleTakeKey.argumentPath());
            if (primaryKeyPath.equals(platformColumn)
                    || primaryKeyPath.equals(roleTakeKey.argumentPath())
                    || primaryKeyPath.equals(performerObjectIdPath)) {
                return rootType + "." + roleTakeKey.argumentPath();
            }
        }
        return null;
    }

    default void appendPrimaryKeyFieldPaths(org.antlr.v4.runtime.ParserRuleContext ctx,
                                            List<String> paths,
                                            String rootType,
                                            String prefix,
                                            SppField field,
                                            Set<String> visitingTypes,
                                            String ruleName) {
        if (field.getArrayDimension() != 0) {
            logSppError(ctx, ruleName + " " + rootType + " cannot infer array primary key " + prefix + ".");
            return;
        }
        if (isScalarRepositoryField(field)) {
            paths.add(rootType + "." + prefix);
            return;
        }
        AppTable targetTable = field.getType() == null ? null : findAppTableByBusinessType(field.getType().getName());
        if (targetTable == null) {
            logSppError(ctx, ruleName + " " + rootType + " primary key " + prefix
                    + " must reference an APP DDL table.");
            return;
        }
        for (String nestedPath : appDdlPrimaryKeyPaths(ctx, targetTable, field.getType().getName(), visitingTypes,
                ruleName)) {
            paths.add(rootType + "." + prefix + "." + RepositoryHydrationRule.objectIdPath(nestedPath));
        }
    }

    default boolean isScalarRepositoryField(SppField field) {
        return field.getType() == null
                || field.getType().getType() == null
                || field.getType().getType() == IConstance.CompilationUnitType.Enum;
    }

    default String primaryKeyObjectPath(SppFieldReference primaryKey) {
        if (primaryKey == null) {
            return "";
        }
        String objectPath = primaryKey.getObjectPath();
        if (objectPath != null && !objectPath.isBlank()) {
            return objectPath;
        }
        return primaryKey.getSppField() == null ? "" : primaryKey.getSppField().getName();
    }

    default AppTable findAppTableByBusinessType(String businessType) {
        return getSppDomian().resolveAppTable(businessType);
    }

    default boolean hasFieldPath(SppClass objectClass, String fieldPath) {
        SppClass currentClass = objectClass;
        String[] parts = fieldPath.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            SppField field = currentClass.getField(parts[i]);
            if (field == null) {
                return false;
            }
            if (i < parts.length - 1) {
                if (!(field.getType() instanceof SppClass nestedClass)) {
                    return false;
                }
                currentClass = nestedClass;
            }
        }
        return true;
    }
}
