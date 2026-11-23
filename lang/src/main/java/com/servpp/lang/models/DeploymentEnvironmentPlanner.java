package com.servpp.lang.models;

import com.servpp.lang.general.IConstance.CompilationUnitType;
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
    private static final String TCC_PENDING =
            "distributed cross-domain quantum transfer not yet supported (APP TCC pending)";

    private record QuantumPosition(SppDomain owner, String attributeId, List<String> keys) { }

    private record QuantumStoreIdentity(String adapter, String jdbc, String valueTable,
                                        String journalTable, String decimalStorage) {
        static QuantumStoreIdentity of(AppQuantumStoreAdapter store) {
            var config = store.getConfiguration();
            String adapter = "jdbc-quantum".equals(store.getAdapterName()) ? "jdbcQuantum" : store.getAdapterName();
            return new QuantumStoreIdentity(adapter, localIdentity(config),
                    config.get("valueTable"), config.get("journalTable"),
                    config.getOrDefault("decimalStorage", "decimal"));
        }
    }

    static List<String> bindQuantumTransfers(SppProject project) {
        List<String> errors = new ArrayList<>();
        for (var domain : project.getSppDomainMap().values()) {
            for (var unit : domain.getMapsOfClass().values()) {
                if (unit instanceof SppService service && !service.getTransferPaths().isEmpty())
                    bindQuantumTransfer(project, domain, service, errors);
            }
        }
        return errors;
    }

    private static void bindQuantumTransfer(SppProject project, SppDomain domain, SppService service,
                                            List<String> errors) {
        service.setQuantumEndpoints(Map.of());
        Set<String> attributes = new LinkedHashSet<>();
        for (String path : service.getTransferPaths()) {
            String[] parts = path.split("\\.");
            var argument = service.getLocalVar(parts[0]);
            if (parts.length == 2 && argument != null && argument.getType() instanceof SppClass type)
                attributes.add(type.getName() + "." + parts[1]);
        }
        if (attributes.stream().noneMatch(attribute -> {
            var type = domain.getSppClass(attribute.split("\\.")[0]);
            return type instanceof SppClass participant
                    && (participant.getReferenceSource() != null || !externalQuantumMappers(domain, participant).isEmpty());
        })) return;
        attributes.addAll(service.getAssignedQuantumAttributeIds());
        if (service.getScopeItem() != null) attributes.addAll(service.getScopeItem().getQuantumConservationAttributeIds());

        String prefix = domain.getName() + "." + service.getFuncName() + ": ";
        Map<String, QuantumPosition> positions = new LinkedHashMap<>();
        Set<QuantumStoreIdentity> stores = new HashSet<>();
        Set<String> canonicalIds = new HashSet<>();
        for (String attribute : attributes) {
            String[] parts = attribute.split("\\.");
            var type = domain.getSppClass(parts[0]);
            QuantumPosition position = parts.length == 2 && type instanceof SppClass participant
                    ? quantumPosition(project, domain, participant, parts[1]) : null;
            if (position == null) {
                errors.add(prefix + "Unmapped quantum transfer endpoint " + attribute
                        + " requires a unique provider quantum attribute and a complete play key");
                return;
            }
            if (!canonicalIds.add(position.attributeId())) {
                errors.add(prefix + "Multiple quantum transfer attributes resolve to " + position.attributeId());
                return;
            }
            String name = position.owner().quantumStoreNameForAttribute(position.attributeId());
            var adapter = position.owner().quantumStoreAdapter(name);
            if (adapter == null) {
                errors.add(prefix + "Quantum transfer endpoint " + attribute + " has no APP/PHY quantum store");
                return;
            }
            stores.add(QuantumStoreIdentity.of(adapter));
            positions.put(attribute, position);
        }
        if (positions.size() < 2) return;
        var environments = quantumEnvironments(project, domain);
        if (stores.size() != 1 || !"LOCAL".equals(domain.getRuleBlock().getDeploymentTransactionMode())
                || environments.isEmpty() || environments.stream().anyMatch(environment ->
                positions.values().stream().anyMatch(position -> !environment.getDomains().contains(position.owner().getName())))) {
            errors.add(prefix + TCC_PENDING);
            return;
        }

        var store = stores.iterator().next();
        if (!"jdbcQuantum".equals(store.adapter())
                || environments.stream().anyMatch(environment -> !commonLocalStore(project, environment))
                || domain.getRuleBlock().getAppPhysicalBackends().stream().noneMatch(backend ->
                        localQuantumBackend(domain, backend, service.getTransactionResourceIdLiteral(), store))) {
            errors.add(prefix + "Cross-domain quantum transfer requires a unified quantum store enlisted in LOCAL");
            return;
        }
        if (environments.stream().flatMap(environment -> environment.getDomains().stream()).distinct()
                .map(project::getDomain).filter(Objects::nonNull)
                .flatMap(member -> member.getRuleBlock().getAppQuantumStoreAdapters().stream())
                .map(QuantumStoreIdentity::of).anyMatch(candidate -> !store.equals(candidate))) {
            errors.add(prefix + TCC_PENDING);
            return;
        }
        for (var environment : environments) {
            Set<String> ownedAttributes = new HashSet<>();
            for (String name : environment.getDomains()) {
                var member = project.getDomain(name);
                for (var assignment : member.getRuleBlock().getAppQuantumStores()) {
                    for (String attribute : new HashSet<>(assignment.getAttributeIds())) {
                        if (!ownedAttributes.add(attribute)) {
                            errors.add(prefix + "Unified quantum store has conflicting declarations for " + attribute);
                            return;
                        }
                    }
                }
            }
        }
        Map<String, SppService.QuantumEndpoint> bindings = new LinkedHashMap<>();
        positions.forEach((logical, position) -> {
            if (position.owner() != domain)
                bindings.put(logical, new SppService.QuantumEndpoint(position.attributeId(), position.keys()));
        });
        service.setQuantumEndpoints(bindings);
    }

    private static boolean localQuantumBackend(SppDomain domain, AppPhysicalBackend backend, String resource,
                                               QuantumStoreIdentity store) {
        if (!Set.of("localJdbc", "local-jdbc").contains(backend.getAdapterName())
                || !domain.backendSupportsTransaction(backend, "at")
                || !localIdentity(backend.getConfiguration()).equals(store.jdbc())) return false;
        if (!backend.getBindings().isEmpty() && backend.getBindings().stream().noneMatch(binding ->
                binding.getKind() == AppPhysicalBinding.Kind.at
                        && (binding.isAllTargets() || resource.equals(binding.getTargetName())))) return false;
        String storeName = backend.getQuantumStoreName();
        if (storeName == null) return true;
        var bound = domain.quantumStoreAdapter(storeName);
        return bound != null && store.equals(QuantumStoreIdentity.of(bound));
    }

    private static List<DeploymentEnvironment> quantumEnvironments(SppProject project, SppDomain domain) {
        String selected = System.getProperty("spp.environment", "develop");
        return project.getSppDomainMap().values().stream()
                .flatMap(member -> member.getRuleBlock().getDeploymentEnvironments().values().stream())
                .map(profiles -> profiles.get(selected)).filter(Objects::nonNull)
                .filter(environment -> environment.getDomains().contains(domain.getName())).toList();
    }

    record ProviderTake(SppDomain domain, SppRoleMapper mapper) { }

    static List<ProviderTake> providerTakes(SppDomain domain, SppClass role) {
        if (domain.getProject() == null || role.getType() != CompilationUnitType.role
                || role.getSppFieldList().stream().noneMatch(SppField::isQuantum)) return List.of();
        String identity = ParticipantRoleBindings.identity(domain, role);
        var candidates = domain.getProject().getSppDomainMap().values().stream()
                .filter(provider -> provider != domain)
                .flatMap(provider -> provider.getMapsOfClass().values().stream()
                        .filter(unit -> unit instanceof SppRoleMapper mapper
                                && identity.equals(mapper.getRole().getReferenceSource())
                                && mapper.getDeclaredPerformers().stream().anyMatch(performer ->
                                        performer.getReferenceSource() == null))
                        .map(unit -> new ProviderTake(provider, (SppRoleMapper) unit)))
                .toList();
        if (candidates.size() < 2) return candidates;
        Set<String> deployed = new HashSet<>();
        for (var environment : quantumEnvironments(domain.getProject(), domain))
            deployed.addAll(environment.getDomains());
        var selected = candidates.stream().filter(take -> deployed.contains(take.domain().getName())).toList();
        // Retain undeployed candidates if none are selected so the transfer still fails deployment validation.
        return selected.isEmpty() ? candidates : selected;
    }

    private static List<ProviderTake> externalQuantumMappers(SppDomain domain, SppClass type) {
        List<ProviderTake> takes = new ArrayList<>(providerTakes(domain, type));
        for (var unit : domain.getMapsOfClass().values())
            if (unit instanceof SppRoleMapper mapper && mapper.getRole() == type
                    && mapper.getDeclaredPerformers().stream().anyMatch(performer -> performer.getReferenceSource() != null))
                takes.add(new ProviderTake(domain, mapper));
        return takes;
    }

    static boolean isProviderQuantumField(SppDomain domain, SppClass role, String field) {
        return externalQuantumMappers(domain, role).stream().anyMatch(take -> {
            String source = mappedField(take.mapper(), field);
            return source != null && take.mapper().getRole().getField(field) != null
                    && take.mapper().getDeclaredPerformers().stream().anyMatch(performer ->
                            isExternalPerformer(domain, take, performer) && performer.getField(source) != null
                                    && performer.getField(source).isQuantum());
        });
    }

    private static boolean isExternalPerformer(SppDomain domain, ProviderTake take, SppClass performer) {
        return take.domain() == domain ? performer.getReferenceSource() != null
                : performer.getReferenceSource() == null;
    }

    private static QuantumPosition quantumPosition(SppProject project, SppDomain domain, SppClass type, String field) {
        if (type.getField(field) == null || !type.getField(field).isQuantum()) return null;
        if (type.getReferenceSource() != null) return referencedPosition(project, type, field, type, null);
        var takes = externalQuantumMappers(domain, type);
        if (takes.isEmpty()) return new QuantumPosition(domain, type.getName() + "." + field, List.of());
        List<QuantumPosition> candidates = new ArrayList<>();
        for (var take : takes) {
            var mapper = take.mapper();
            if (mapper.getRole().getField(field) == null) continue;
            String source = mappedField(mapper, field);
            if (source == null) continue;
            for (var performer : mapper.getDeclaredPerformers()) {
                if (!isExternalPerformer(domain, take, performer)) continue;
                QuantumPosition position = take.domain() == domain
                        ? referencedPosition(project, performer, source, type, mapper)
                        : providerPosition(take.domain(), performer, source, type, mapper);
                if (position != null) candidates.add(position);
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static QuantumPosition referencedPosition(SppProject project, SppClass reference, String field,
                                                      SppClass view, SppRoleMapper mapper) {
        String[] source = reference.getReferenceSource().split("\\.");
        if (source.length != 2 || reference.getField(field) == null || !reference.getField(field).isQuantum()) return null;
        SppDomain owner = project.getDomain(source[0]);
        return owner != null && owner.getSppClass(source[1]) instanceof SppClass performer
                ? providerPosition(owner, performer, field, view, mapper) : null;
    }

    private static QuantumPosition providerPosition(SppDomain owner, SppClass performer, String field,
                                                    SppClass view, SppRoleMapper mapper) {
        if (!Set.of(CompilationUnitType.entity, CompilationUnitType.contract).contains(performer.getType())
                || performer.getField(field) == null || !performer.getField(field).isQuantum()
                || !com.servpp.lang.models.app.NumericBinding.isNumeric(performer.getField(field).getType().getName())
                || performer.getField(field).getArrayDimension() != 0) return null;
        List<String> keys = new ArrayList<>();
        for (String key : ParticipantRoleBindings.primaryKeys(owner, performer)) {
            String[] path = key.split("\\.", 2);
            List<String> matches = view.getSppFieldList().stream().filter(property -> property.getArrayDimension() == 0
                    && (mapper == null || mapper.getRole().getField(property.getName()) != null)
                    && path[0].equals(mapper == null ? property.getName() : mappedField(mapper, property.getName())))
                    .map(property -> property.getName() + (path.length == 2 ? "." + path[1] : "")).toList();
            if (matches.size() != 1) return null;
            keys.add(matches.get(0));
        }
        return keys.isEmpty() ? null : new QuantumPosition(owner, performer.getName() + "." + field, keys);
    }

    private static String mappedField(SppRoleMapper mapper, String roleField) {
        if (mapper.getField(roleField) instanceof SppRoleField mapping) return mapping.getEntityName();
        return mapper.isMapSame() || mapper.isTakeAll() ? roleField : null;
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
