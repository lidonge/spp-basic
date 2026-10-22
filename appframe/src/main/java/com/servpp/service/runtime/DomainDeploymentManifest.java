package com.servpp.service.runtime;

import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.*;

/** A process advertises its environment, coordination contract and actual atomic signatures. */
public record DomainDeploymentManifest(String environment, String transactionMode, String quantumSystem,
                                       String quantumResource, String quantumIdentity, String transactionIdentity, List<String> domains,
                                       Map<String,String> services) {
    static DomainDeploymentManifest create(Properties application, String prefix, List<String> domains,
                                            Map<String,Properties> physical) {
        Map<String,String> services = new TreeMap<>();
        for (String domain : domains) for (String name : DomainServiceRouter.catalog(domain).stringPropertyNames()) {
            Service service = DefaultServiceContainer.getInstance().getService(name);
            if (service instanceof GeneratedServiceKindProvider kind && kind.getGeneratedServiceKind() != GeneratedServiceKind.SCENARIO)
                services.put(name,DomainServiceProtocol.signature(service));
        }
        String system = application.getProperty(prefix+"quantumSystem");
        String resource = application.getProperty(prefix+"quantumResource");
        Set<String> identities = new TreeSet<>();
        if (system != null) for (Properties values : physical.values()) {
            String root = "quantumStore."+resource+".";
            if (values.getProperty(root+"adapter") == null) continue;
            if (system.equals("UNIFIED_STORE")) identities.add(value(values,root+"config.datasourceUrl")+"|"
                    + value(values,root+"config.schema")+"|"+value(values,root+"config.valueTable")+"|"+value(values,root+"config.journalTable"));
            else if (system.equals("UNIFIED_SERVICE")) identities.add(value(values,root+"config.endpoint")+"|"
                    + value(values,root+"config.namespace")+"|"+value(values,root+"config.serviceProtocol"));
            else for (String backend : values.getProperty("backends","").split(","))
                if ("seata".equals(values.getProperty("backend."+backend+".adapter"))) identities.add(value(values,"backend."+backend+".config.transactionServiceGroup"));
        }
        if (system != null && identities.size() != 1) throw new IllegalStateException("DOMAIN_QUANTUM_COORDINATION_IDENTITY_REQUIRED");
        String mode=application.getProperty(prefix+"transactionMode");
        boolean distributed="XA".equals(mode) || "SEATA_AT".equals(mode);
        Set<String> coordinators=new TreeSet<>();
        if (distributed) for (String domain : domains) {
            Properties values=physical.get(domain);
            for (String backend : values.getProperty("backends","").split(","))
                if ("seata".equals(values.getProperty("backend."+backend+".adapter"))) {
                    String identity=value(values,"backend."+backend+".config.transactionServiceGroup");
                    if (identity.isBlank()) throw new IllegalStateException("DOMAIN_TRANSACTION_COORDINATION_IDENTITY_REQUIRED");
                    coordinators.add(identity);
                }
        }
        if (distributed && coordinators.size()!=1)
            throw new IllegalStateException("DOMAIN_TRANSACTION_COORDINATION_IDENTITY_MISMATCH");
        return new DomainDeploymentManifest(application.getProperty(prefix+"environment"),application.getProperty(prefix+"transactionMode"),
                system,resource,identities.isEmpty()?null:identities.iterator().next(),
                coordinators.isEmpty()?null:coordinators.iterator().next(),List.copyOf(domains),Map.copyOf(services));
    }
    private static String value(Properties values,String key) {
        return PhysicalRuntimeCatalog.resolveConfigValue(values.getProperty(key,""));
    }
    public void requireCompatible(DomainDeploymentManifest peer,String domain) {
        if (!Objects.equals(environment,peer.environment) || !Objects.equals(transactionMode,peer.transactionMode)
                || !Objects.equals(quantumSystem,peer.quantumSystem) || !Objects.equals(quantumResource,peer.quantumResource)
                || !Objects.equals(quantumIdentity,peer.quantumIdentity) || !Objects.equals(transactionIdentity,peer.transactionIdentity)
                || !peer.domains.contains(domain))
            throw new IllegalStateException("REMOTE_DOMAIN_DEPLOYMENT_CONTRACT_MISMATCH: "+domain);
        for (String service : DomainServiceRouter.catalog(domain).stringPropertyNames()) {
            Service expected = DomainServiceRouter.prototype(service);
            if (expected instanceof GeneratedServiceKindProvider kind && kind.getGeneratedServiceKind() != GeneratedServiceKind.SCENARIO
                    && !Objects.equals(DomainServiceProtocol.signature(expected),peer.services.get(service)))
                throw new IllegalStateException("REMOTE_DOMAIN_SERVICE_SIGNATURE_MISMATCH: "+service);
        }
    }
}
