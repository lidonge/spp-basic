package com.servpp.lang.models.app;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.models.SppService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public class ScopeItem {
    SppService service;
    private String declaredServiceName;
    boolean isAsync;

    ScopeDefine scopeDefine;
    IConstance.TransactionType transactionType = IConstance.TransactionType.db;
    private String resourceId;
    private boolean implicitDefaultBinding;
    private boolean quantumResourceInferred;
    public boolean isQuantumResourceInferred() { return quantumResourceInferred; }
    public void setQuantumResourceInferred(boolean value) { quantumResourceInferred = value; }
    private boolean resourceDeclaredBeforeTransactionMode;
    private final List<String> asyncModifierNames = new ArrayList<>();
    private final List<String> transactionModifierNames = new ArrayList<>();
    private final List<String> resourceModifierIds = new ArrayList<>();
    private final List<String> resourceArgumentNames = new ArrayList<>();
    private final List<String> quantumConservationAttributeIds = new ArrayList<>();
    private final List<String> resourceServiceNames = new ArrayList<>();
    private final List<ObjectSnapshotRule> objectSnapshotRules = new ArrayList<>();
    private final List<InferredObjectSnapshotRule> inferredObjectSnapshotRules = new ArrayList<>();
    private final List<RepositoryHydrationRule> repositoryHydrationRules = new ArrayList<>();
    private final List<TccReservationRule> tccReservationRules = new ArrayList<>();
    private final List<String> compensationServiceNames = new ArrayList<>();
    private final List<String> remoteCompensationServiceNames = new ArrayList<>();
    private final List<PersistenceRule> persistenceRules = new ArrayList<>();
    private final List<PersistenceVersion> persistenceVersions = new ArrayList<>();

    private int businessIdempotencyModifiers;
    private java.util.List<String> idempotencyKeyPaths = java.util.List.of();
    private String idempotencyFactsPath;
    private java.util.List<String> idempotencyFactsPaths = java.util.List.of();
    public java.util.List<String> getIdempotencyFactsPaths() { return idempotencyFactsPaths; }
    private String duplicateWindow;
    public String getDuplicateWindow() { return duplicateWindow; }
    public void setDuplicateWindow(String value) { duplicateWindow = value; }
    public java.util.List<String> getIdempotencyKeyPaths() { return idempotencyKeyPaths; }
    public String getIdempotencyFactsPath() { return idempotencyFactsPath; }
    public void setDeclaredIdempotency(java.util.List<String> keys, String facts) {
        setDeclaredIdempotency(keys, java.util.List.of(facts));
    }
    public void setDeclaredIdempotency(java.util.List<String> keys, java.util.List<String> facts) {
        if (!idempotencyKeyPaths.isEmpty()) throw new IllegalArgumentException("Duplicate declared idempotency binding");
        idempotencyKeyPaths = java.util.List.copyOf(keys); idempotencyFactsPaths = java.util.List.copyOf(facts);
        idempotencyFactsPath = facts.size() == 1 ? facts.get(0) : null;
    }
    public void declareBusinessIdempotency() { businessIdempotencyModifiers++; }
    public int getBusinessIdempotencyModifiers() { return businessIdempotencyModifiers; }
    public boolean isBusinessManagedIdempotency() { return businessIdempotencyModifiers > 0; }

    public record BusinessNumber(RepositoryHydrationRule.KeyTarget target, String prefix) { }
    private final List<BusinessNumber> businessNumbers = new java.util.ArrayList<>();
    public List<BusinessNumber> getBusinessNumbers() { return businessNumbers; }
    private int optionalRepositoryModifiers;
    private int repositoryReadLocks;
    public void declareRepositoryReadLock() { repositoryReadLocks++; }
    public int getRepositoryReadLocks() { return repositoryReadLocks; }
    public boolean isRepositoryReadLocked() { return repositoryReadLocks > 0; }
    public void declareOptionalRepository() { optionalRepositoryModifiers++; }
    public int getOptionalRepositoryModifiers() { return optionalRepositoryModifiers; }
    public boolean isOptionalRepository() { return optionalRepositoryModifiers > 0; }

    public List<PersistenceRule> getPersistenceRules() { return persistenceRules; }
    public List<PersistenceVersion> getPersistenceVersions() { return persistenceVersions; }

    public ScopeDefine getScopeDefine() {
        return scopeDefine;
    }

    public void setScopeDefine(ScopeDefine scopeDefine) {
        this.scopeDefine = scopeDefine;
    }

    public boolean isLocal() {
        return scopeDefine.isLocal();
    }

    public SppService getService() {
        return service;
    }

    public void setService(SppService service) {
        this.service = service;
        if (service.getScopeItem() == null || service.getScopeItem().isImplicitDefaultBinding()) {
            service.setScopeItem(this);
        }
    }

    public String getDeclaredServiceName() {
        return declaredServiceName;
    }

    public void setDeclaredServiceName(String declaredServiceName) {
        this.declaredServiceName = declaredServiceName;
    }

    public boolean isImplicitDefaultBinding() {
        return implicitDefaultBinding;
    }

    public void setImplicitDefaultBinding(boolean implicitDefaultBinding) {
        this.implicitDefaultBinding = implicitDefaultBinding;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public void setAsync(boolean async) {
        isAsync = async;
    }

    public void addAsyncModifierName(String asyncModifierName) {
        asyncModifierNames.add(asyncModifierName);
    }

    public List<String> getAsyncModifierNames() {
        return asyncModifierNames;
    }

    public boolean hasMultipleAsyncModifiers() {
        return asyncModifierNames.size() > 1;
    }

    public IConstance.TransactionType getTransactionType() {
        return transactionType;
    }

    public String getTransactionModeName() {
        if (service != null && service.getServiceType() == IConstance.ServiceType.update
                && "LOCAL".equals(service.getDeploymentTransactionMode())) return "LOCAL";
        if (service != null && !service.isDeclaredQuantumService() && service.getServiceType() == IConstance.ServiceType.update
                && "XA".equals(service.getDeploymentTransactionMode())) return "XA";
        if (transactionType == IConstance.TransactionType.db) {
            return "AT";
        }
        return transactionType.name().toUpperCase(Locale.ROOT);
    }

    public void setTransactionType(IConstance.TransactionType transactionType) {
        this.transactionType = transactionType;
        transactionModifierNames.add(transactionType.name().toUpperCase(Locale.ROOT));
    }

    public List<String> getTransactionModifierNames() {
        return transactionModifierNames;
    }

    public boolean hasMultipleTransactionModifiers() {
        return transactionModifierNames.size() > 1;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
        resourceModifierIds.add(resourceId);
        if (transactionModifierNames.isEmpty()) {
            resourceDeclaredBeforeTransactionMode = true;
        }
    }

    public List<String> getResourceModifierIds() {
        return resourceModifierIds;
    }

    public boolean hasMultipleResourceModifiers() {
        return resourceModifierIds.size() > 1;
    }

    public void addResourceArgumentName(String resourceArgumentName) {
        resourceArgumentNames.add(resourceArgumentName);
    }

    public List<String> getResourceArgumentNames() {
        return resourceArgumentNames;
    }

    public boolean hasResourceArguments() {
        return !resourceArgumentNames.isEmpty();
    }

    public boolean isResourceDeclaredBeforeTransactionMode() {
        return resourceDeclaredBeforeTransactionMode;
    }

    public List<String> getQuantumConservationAttributeIds() {
        return quantumConservationAttributeIds;
    }

    public void addQuantumConservationAttributeId(String attributeId) {
        quantumConservationAttributeIds.add(attributeId);
    }

    public void addResourceServiceName(String serviceName) {
        resourceServiceNames.add(serviceName);
    }

    public List<String> getResourceServiceNames() {
        return resourceServiceNames;
    }

    public boolean hasQuantumConservationAttributes() {
        return transactionType == IConstance.TransactionType.quantum && !quantumConservationAttributeIds.isEmpty();
    }

    public String getTccConfirmServiceName() {
        if (transactionType != IConstance.TransactionType.tcc || resourceServiceNames.isEmpty()) {
            return null;
        }
        return resourceServiceNames.get(0);
    }

    public String getTccCancelServiceName() {
        if (transactionType != IConstance.TransactionType.tcc || resourceServiceNames.size() < 2) {
            return null;
        }
        return resourceServiceNames.get(1);
    }

    public boolean hasTccLifecycleServices() {
        return getTccConfirmServiceName() != null && getTccCancelServiceName() != null;
    }

    public List<ObjectSnapshotRule> getObjectSnapshotRules() {
        return objectSnapshotRules;
    }

    public void addObjectSnapshotRule(ObjectSnapshotRule objectSnapshotRule) {
        objectSnapshotRules.add(objectSnapshotRule);
    }

    public boolean hasObjectSnapshotRules() {
        return !objectSnapshotRules.isEmpty();
    }

    public boolean hasArgumentObjectSnapshotRules() {
        return objectSnapshotRules.stream()
                .anyMatch(rule -> rule.source() == ObjectSnapshotRule.Source.argument);
    }

    public boolean hasCurrentObjectSnapshotRules() {
        return objectSnapshotRules.stream()
                .anyMatch(rule -> rule.source() == ObjectSnapshotRule.Source.current);
    }

    public List<InferredObjectSnapshotRule> getInferredObjectSnapshotRules() {
        return inferredObjectSnapshotRules;
    }

    public void addInferredObjectSnapshotRule(InferredObjectSnapshotRule inferredObjectSnapshotRule) {
        inferredObjectSnapshotRules.add(inferredObjectSnapshotRule);
    }

    public List<RepositoryHydrationRule> getRepositoryHydrationRules() {
        return repositoryHydrationRules;
    }

    public void addRepositoryHydrationRule(RepositoryHydrationRule repositoryHydrationRule) {
        repositoryHydrationRules.add(repositoryHydrationRule);
    }

    public boolean hasRepositoryHydrationRules() {
        return !repositoryHydrationRules.isEmpty();
    }

    public List<TccReservationRule> getTccReservationRules() {
        return tccReservationRules;
    }

    public void addTccReservationRule(TccReservationRule tccReservationRule) {
        tccReservationRules.add(tccReservationRule);
    }

    public boolean hasTccReservationRules() {
        return !tccReservationRules.isEmpty();
    }

    public List<String> getCompensationServiceNames() {
        return compensationServiceNames;
    }

    public void addCompensationServiceName(String compensationServiceName) {
        compensationServiceNames.add(compensationServiceName);
    }

    public boolean hasCompensationServices() {
        return !compensationServiceNames.isEmpty();
    }

    public List<String> getRemoteCompensationServiceNames() {
        return remoteCompensationServiceNames;
    }

    public void addRemoteCompensationServiceName(String remoteCompensationServiceName) {
        remoteCompensationServiceNames.add(remoteCompensationServiceName);
    }

    public boolean hasRemoteCompensationServices() {
        return !remoteCompensationServiceNames.isEmpty();
    }

    public ScopeItem copyForService(SppService service) {
        ScopeItem copy = new ScopeItem();
        copy.declaredServiceName = declaredServiceName;
        copy.isAsync = isAsync;
        copy.scopeDefine = scopeDefine;
        copy.transactionType = transactionType;
        copy.resourceId = resourceId;
        copy.implicitDefaultBinding = implicitDefaultBinding;
        copy.quantumResourceInferred = quantumResourceInferred;
        copy.duplicateWindow = duplicateWindow;
        copy.resourceDeclaredBeforeTransactionMode = resourceDeclaredBeforeTransactionMode;
        copy.asyncModifierNames.addAll(asyncModifierNames);
        copy.transactionModifierNames.addAll(transactionModifierNames);
        copy.resourceModifierIds.addAll(resourceModifierIds);
        copy.resourceArgumentNames.addAll(resourceArgumentNames);
        copy.quantumConservationAttributeIds.addAll(quantumConservationAttributeIds);
        copy.resourceServiceNames.addAll(resourceServiceNames);
        copy.objectSnapshotRules.addAll(objectSnapshotRules);
        copy.inferredObjectSnapshotRules.addAll(inferredObjectSnapshotRules);
        copy.repositoryHydrationRules.addAll(repositoryHydrationRules);
        copy.repositoryReadLocks = repositoryReadLocks;
        copy.optionalRepositoryModifiers = optionalRepositoryModifiers;
        copy.businessIdempotencyModifiers = businessIdempotencyModifiers;
        copy.idempotencyFactsPaths = idempotencyFactsPaths; copy.idempotencyKeyPaths = idempotencyKeyPaths; copy.idempotencyFactsPath = idempotencyFactsPath;
        copy.tccReservationRules.addAll(tccReservationRules);
        copy.compensationServiceNames.addAll(compensationServiceNames);
        copy.remoteCompensationServiceNames.addAll(remoteCompensationServiceNames);
        copy.persistenceRules.addAll(persistenceRules);
        copy.persistenceVersions.addAll(persistenceVersions);
        copy.setService(service);
        return copy;
    }

    @Override
    public String toString() {
        return "ScopeItem{" +
                "service=" + service +
                ", declaredServiceName='" + declaredServiceName + '\'' +
                ", isAsync=" + isAsync +
                ", asyncModifierNames=" + asyncModifierNames +
                ", transactionType=" + transactionType +
                ", resourceId='" + resourceId + '\'' +
                ", transactionModifierNames=" + transactionModifierNames +
                ", resourceModifierIds=" + resourceModifierIds +
                ", resourceArgumentNames=" + resourceArgumentNames +
                ", quantumConservationAttributeIds=" + quantumConservationAttributeIds +
                ", resourceServiceNames=" + resourceServiceNames +
                ", objectSnapshotRules=" + objectSnapshotRules +
                ", repositoryHydrationRules=" + repositoryHydrationRules +
                ", tccReservationRules=" + tccReservationRules +
                ", compensationServiceNames=" + compensationServiceNames +
                ", remoteCompensationServiceNames=" + remoteCompensationServiceNames +
                '}';
    }
}
