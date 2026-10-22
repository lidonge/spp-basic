package com.servpp.service.repository;

import java.util.List;
import java.util.function.Supplier;

public interface RepositoryWriteRuleProvider {
    List<RepositoryWriteRule> getRepositoryWriteRules();
    void configureRepositoryWrites(Supplier<ObjectRepository> repository);

    default Object getRepositoryTransactionResource() { return null; }

    /** Resolve local delegates, but never merge two remote calls by peer or resource name. */
    static Object transactionResource(com.servpp.service.Service service) {
        var visited = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<com.servpp.service.Service, Boolean>());
        while (service != null) {
            if (!visited.add(service)) throw new IllegalStateException("LOCAL_SERVICE_BINDING_CYCLE");
            if (service instanceof com.servpp.service.runtime.RemoteGeneratedService) return null;
            if (service instanceof com.servpp.service.runtime.LocalServiceDelegateProvider delegate) {
                String name = delegate.getLocalDelegateServiceName();
                if (com.servpp.service.runtime.DomainServiceRouter.isRemote(name)) return null;
                service = com.servpp.service.server.DefaultServiceContainer.getInstance().getService(name);
                if (service == null) throw new IllegalStateException("LOCAL_PROVIDER_NOT_DEPLOYED: " + name);
            } else return service instanceof RepositoryWriteRuleProvider writer
                    ? writer.getRepositoryTransactionResource() : null;
        }
        return null;
    }

    default void executeInRepositoryScope(Runnable action) {
        com.servpp.service.transaction.DefaultTransactionManager.getInstance()
                .getQuantumAccessAdapter().executeInAccessScope(action);
    }

    static void executeInRepositoryScope(com.servpp.service.Service service, Runnable action) {
        executeInRepositoryScope(service, action, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static void executeInRepositoryScope(com.servpp.service.Service service, Runnable action,
                                                  java.util.Set<com.servpp.service.Service> visited) {
        if (service != null && !visited.add(service)) throw new IllegalStateException("LOCAL_SERVICE_BINDING_CYCLE");
        if (service instanceof RepositoryWriteRuleProvider writer) writer.executeInRepositoryScope(action);
        else if (service instanceof com.servpp.service.runtime.LocalServiceDelegateProvider delegate
                && !com.servpp.service.runtime.DomainServiceRouter.isRemote(delegate.getLocalDelegateServiceName())) {
            var provider = com.servpp.service.server.DefaultServiceContainer.getInstance().getService(delegate.getLocalDelegateServiceName());
            if (provider == null) throw new IllegalStateException("LOCAL_PROVIDER_NOT_DEPLOYED: " + delegate.getLocalDelegateServiceName());
            executeInRepositoryScope(provider, action, visited);
        } else com.servpp.service.transaction.DefaultTransactionManager.getInstance()
                .getQuantumAccessAdapter().executeInAccessScope(action);
    }
}
