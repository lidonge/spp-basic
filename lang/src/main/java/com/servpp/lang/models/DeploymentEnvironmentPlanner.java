package com.servpp.lang.models;

import com.servpp.lang.models.app.*;
import java.util.*;

/** Resolves APP environment contracts against the selected PHY, before service binding. */
public final class DeploymentEnvironmentPlanner {
    private DeploymentEnvironmentPlanner() { }
    public static List<String> prepare(SppProject project) {
        List<String> errors = new ArrayList<>();
        String selected = System.getProperty("spp.environment", "develop");
        boolean hasProfiles = false, selectedAny = false;
        boolean distributedEnvironment = project.getSppDomainMap().values().stream().flatMap(domain ->
                domain.getRuleBlock().getDeploymentEnvironments().values().stream()).map(environments -> environments.get(selected))
                .filter(Objects::nonNull).anyMatch(environment -> hasExternalBindings(project,environment));
        // Ordinary resource aliases belong to the deployed application, unlike the shared quantum identity.
        Map<String,String> domainResources=new LinkedHashMap<>();
        for (var source : project.getSppDomainMap().values())
            for (var profiles : source.getRuleBlock().getDeploymentEnvironments().values()) {
                var environment=profiles.get(selected); if (environment==null) continue;
                String mode=environment.getTransactionMode();
                if (mode==null) mode=!distributedEnvironment && !hasExternalBindings(project,environment)
                        && commonLocalStore(project,environment)?"LOCAL":"SAGA";
                String resource=environment.getTransactionResource();
                if (resource==null) resource=inferResource(project,environment,mode);
                if (resource==null) continue;
                for (String name : environment.getDomains()) {
                    String previous=domainResources.putIfAbsent(name,resource);
                    if (previous!=null && !previous.equals(resource))
                        errors.add("Domain "+name+" has conflicting application transaction resources");
                }
            }
        for (var declaring : project.getSppDomainMap().values()) {
            var rules = declaring.getRuleBlock();
            for (var application : rules.getDeploymentEnvironments().entrySet()) {
                if (application.getValue().isEmpty()) continue; // historical application syntax
                hasProfiles = true;
                var environment = application.getValue().get(selected);
                if (environment == null) continue; // Application is not deployed in this environment.
                selectedAny = true;
                for (var candidate : application.getValue().values()) {
                    if (candidate.getDomains().isEmpty() || new HashSet<>(candidate.getDomains()).size() != candidate.getDomains().size())
                        errors.add("Environment " + candidate.getName() + " requires unique deployed domains");
                    for (String member : candidate.getDomains()) if (project.getDomain(member) == null)
                        errors.add("Environment " + candidate.getName() + " deploys undefined domain " + member);
                    if (!candidate.getDomains().contains(declaring.getName()))
                        errors.add("Environment " + candidate.getName() + " must deploy application entry domain " + declaring.getName());
                    if (candidate.getQuantumSystem() != null && !Set.of("UNIFIED_STORE", "UNIFIED_SERVICE", "TCC").contains(candidate.getQuantumSystem()))
                        errors.add("Unknown quantum system " + candidate.getQuantumSystem());
                    if ("LOCAL".equals(candidate.getTransactionMode()) && hasExternalBindings(project,candidate))
                        errors.add("LOCAL environment " + candidate.getName() + " cannot invoke undeployed domain services");
                }
                boolean local = !hasExternalBindings(project, environment) && commonLocalStore(project,environment);
                String mode = environment.getTransactionMode() == null ? local && !distributedEnvironment ? "LOCAL" : "SAGA" : environment.getTransactionMode();
                if ("LOCAL".equals(mode) && !local)
                    errors.add("LOCAL environment " + selected + " requires same JVM, database, schema and shared JDBC resource");
                String resource = environment.getTransactionResource();
                if (resource == null) resource = inferResource(project, environment, mode);
                if (resource == null) errors.add("Environment " + selected + " needs an unambiguous transaction resource");
                if ("LOCAL".equals(mode) && environment.getQuantumSystem() != null)
                    errors.add("LOCAL uses the shared database transaction; do not select a separately committed quantum system");
                rules.getApplicationDeployments().put(application.getKey(), List.copyOf(environment.getDomains()));
                // All source domains get the environment contract, including independently deployed providers.
                for (var domain : project.getSppDomainMap().values()) {
                    var member = domain.getRuleBlock();
                    String storageMode = mode.equals("LOCAL") || mode.equals("SEATA_AT") ? "AT" : mode;
                    if (member.hasTransactionPolicy() && !Objects.equals(member.getDefaultUpdateMode(), storageMode)
                            && !Objects.equals(member.getDefaultUpdateMode(), mode))
                        errors.add("Domain " + domain.getName() + " conflicts with environment transaction mode " + mode);
                    try { member.selectDeployment(selected, mode, environment.getQuantumSystem(), environment.getQuantumResource()); }
                    catch (IllegalArgumentException conflict) { errors.add(domain.getName() + ": " + conflict.getMessage()); }
                    if (environment.getOrdinaryUpdates() != null) {
                        try { member.selectOrdinaryUpdates(environment.getOrdinaryUpdates()); }
                        catch (IllegalArgumentException conflict) { errors.add(domain.getName() + ": " + conflict.getMessage()); }
                    }
                    String domainResource=domainResources.getOrDefault(domain.getName(),resource);
                    member.setDefaultUpdateTransaction(storageMode, domainResource);
                    validateCoordinator(domain, mode, domainResource, errors);
                }
                validateQuantumSystem(project, environment, errors);
                if (Set.of("XA", "SEATA_AT").contains(mode)) {
                    Set<String> coordinators = new HashSet<>();
                    for (var domain : project.getSppDomainMap().values())
                        for (var backend : domain.getRuleBlock().getAppPhysicalBackends())
                            if ("seata".equals(backend.getAdapterName())) coordinators.add(backend.getConfiguration().get("transactionServiceGroup"));
                    if (coordinators.size() != 1 || coordinators.contains(null))
                        errors.add("Environment " + selected + " requires one shared distributed transaction coordination identity");
                }
            }
        }
        if (hasProfiles && !selectedAny) errors.add("No application is declared for environment " + selected);
        return errors;
    }
    private static void validateQuantumSystem(SppProject project, DeploymentEnvironment environment, List<String> errors) {
        if (environment.getQuantumSystem() == null) return;
        Set<String> identities = new LinkedHashSet<>();
        for (var domain : project.getSppDomainMap().values()) {
            boolean quantum = domain.getMapsOfClass().values().stream().anyMatch(unit -> unit instanceof SppClass type
                    && !(type instanceof SppService) && type.getReferenceSource() == null
                    && type.getSppFieldList().stream().anyMatch(SppField::isQuantum));
            if (!quantum) continue;
            var stores = domain.getRuleBlock().getAppQuantumStoreAdapters().stream()
                    .filter(store -> Objects.equals(store.getName(), environment.getQuantumResource())).toList();
            if (stores.size() != 1) { errors.add("Domain " + domain.getName() + " needs the environment quantum resource " + environment.getQuantumResource()); continue; }
            var store = stores.get(0);
            var config = store.getConfiguration();
            switch (environment.getQuantumSystem()) {
                case "UNIFIED_STORE" -> {
                    if (!Set.of("jdbcQuantum", "jdbc-quantum").contains(store.getAdapterName())) errors.add("UNIFIED_STORE requires a JDBC quantum adapter in " + domain.getName());
                    identities.add(config.get("datasourceUrl") + "|" + config.getOrDefault("schema", "<default>") + "|"
                            + config.getOrDefault("valueTable", "spp_quantum_value") + "|" + config.getOrDefault("journalTable", "spp_quantum_journal"));
                }
                case "UNIFIED_SERVICE" -> {
                    if (!"quantumService".equals(store.getAdapterName())) errors.add("UNIFIED_SERVICE requires quantumService in " + domain.getName());
                    identities.add(config.get("endpoint") + "|" + config.get("namespace") + "|" + config.get("serviceProtocol"));
                }
                case "TCC" -> {
                    if (!"tccObjectStore".equals(store.getAdapterName())) errors.add("TCC requires tccObjectStore in " + domain.getName());
                    var coordinators = domain.getRuleBlock().getAppPhysicalBackends().stream().filter(backend -> "seata".equals(backend.getAdapterName())).toList();
                    if (coordinators.size() != 1) errors.add("TCC needs one quantum coordinator backend in " + domain.getName());
                    else identities.add(coordinators.get(0).getConfiguration().get("transactionServiceGroup"));
                }
            }
        }
        if (identities.size() > 1) errors.add("Environment " + environment.getName() + " has incompatible quantum coordination identities across domains");
    }

    private static boolean hasExternalBindings(SppProject project, DeploymentEnvironment environment) {
        for (String name : environment.getDomains()) {
            var domain = project.getDomain(name); if (domain == null) continue;
            for (var binding : domain.getRuleBlock().getLocalServiceBindings())
                if (!environment.getDomains().contains(binding.getTarget().split("\\.")[0])) return true;
            for (var unit : domain.getMapsOfClass().values()) if (unit instanceof SppClass type && type.getReferenceSource() != null
                    && !environment.getDomains().contains(type.getReferenceSource().split("\\.")[0])) return true;
        }
        return false;
    }
    private static boolean commonLocalStore(SppProject project, DeploymentEnvironment environment) {
        Set<String> identities = new HashSet<>();
        for (String name : environment.getDomains()) {
            var domain = project.getDomain(name); if (domain == null) return false;
            var rules = domain.getRuleBlock();
            for (var backend : rules.getAppPhysicalBackends()) {
                if (!List.of("localJdbc", "local-jdbc").contains(backend.getAdapterName())) return false;
                String url = backend.getConfiguration().get("datasourceUrl");
                if (url == null || url.isBlank()) return false;
                String schema = backend.getConfiguration().getOrDefault("schema", "<default>");
                String identity = localIdentity(backend.getConfiguration());
                identities.add(identity);
                for (var repository : rules.getAppRepositoryAdapters())
                    if (!Objects.equals(url,repository.getConfiguration().get("datasourceUrl"))
                            || !Objects.equals(schema,repository.getConfiguration().getOrDefault("schema", "<default>"))
                            || !localIdentity(backend.getConfiguration()).equals(localIdentity(repository.getConfiguration()))) return false;
                for (var quantum : rules.getAppQuantumStoreAdapters())
                    if (!Objects.equals(url,quantum.getConfiguration().get("datasourceUrl"))
                            || !Objects.equals(schema,quantum.getConfiguration().getOrDefault("schema", "<default>"))
                            || !localIdentity(backend.getConfiguration()).equals(localIdentity(quantum.getConfiguration()))) return false;
            }
        }
        return identities.size() == 1;
    }
    private static String localIdentity(Map<String,String> config) {
        String user=config.getOrDefault("username",config.getOrDefault("user",""));
        return Arrays.asList(config.get("datasourceUrl"),config.getOrDefault("schema","<default>"),
                config.getOrDefault("driverClassName",""),user,config.getOrDefault("password","")).stream()
                .map(value -> value==null?"-1:":value.length()+":"+value).collect(java.util.stream.Collectors.joining());
    }
    private static String inferResource(SppProject project, DeploymentEnvironment environment, String mode) {
        Set<String> resources = new LinkedHashSet<>();
        for (String name : environment.getDomains()) {
            var domain = project.getDomain(name); if (domain == null) continue;
            for (var backend : domain.getRuleBlock().getAppPhysicalBackends()) for (var binding : backend.getBindings())
                if (Set.of(AppPhysicalBinding.Kind.at,AppPhysicalBinding.Kind.xa,AppPhysicalBinding.Kind.saga).contains(binding.getKind()) && !binding.isAllTargets()) resources.add(binding.getTargetName());
        }
        return resources.size() == 1 ? resources.iterator().next() : "SAGA".equals(mode) ? "saga" : null;
    }
    private static void validateCoordinator(SppDomain domain, String mode, String resource, List<String> errors) {
        if (!Set.of("XA", "SEATA_AT").contains(mode)) return;
        var matches = domain.getRuleBlock().getAppPhysicalBackends().stream().filter(backend ->
                "seata".equals(backend.getAdapterName()) && backend.getBindings().stream().anyMatch(binding ->
                        (mode.equals("XA") ? binding.getKind().name().equals("xa") : binding.getKind() == AppPhysicalBinding.Kind.at)
                                && (binding.isAllTargets() || Objects.equals(resource,binding.getTargetName())))).toList();
        if (matches.size() != 1) errors.add("Domain " + domain.getName() + " " + mode + " needs one matching distributed coordinator backend");
        else if (mode.equals("XA") && !"XA".equals(matches.get(0).getConfiguration().get("datasourceProxyMode")))
            errors.add("XA requires PHY datasourceProxyMode = XA and XA-capable resources");
    }
}
