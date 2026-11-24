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

    private record QuantumPosition(SppDomain owner, String attributeId, List<String> keys, Map<String, PlayCodeBinding> codeTables) { }

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
                    && (participant.getReferenceSource() != null || !plays(domain, participant).isEmpty());
        })) return;
        attributes.addAll(service.getAssignedQuantumAttributeIds());
        if (service.getScopeItem() != null) attributes.addAll(service.getScopeItem().getQuantumConservationAttributeIds());

        String prefix = domain.getName() + "." + service.getFuncName() + ": ";
        Map<String, List<QuantumPosition>> positions = new LinkedHashMap<>();
        Set<QuantumStoreIdentity> stores = new HashSet<>();
        for (String attribute : attributes) {
            String[] parts = attribute.split("\\.");
            var type = domain.getSppClass(parts[0]);
            List<QuantumPosition> candidates = parts.length == 2 && type instanceof SppClass participant
                    ? quantumPositions(project, domain, participant, parts[1]) : List.of();
            if (candidates.isEmpty()) {
                errors.add(prefix + "Unmapped quantum transfer endpoint " + attribute
                        + " requires a unique provider quantum attribute and a complete play key");
                return;
            }
            for (var position : candidates) {
                String name = position.owner().quantumStoreNameForAttribute(position.attributeId());
                var adapter = position.owner().quantumStoreAdapter(name);
                if (adapter == null) {
                    errors.add(prefix + TCC_PENDING + ": missing quantum store for " + attribute);
                    return;
                }
                stores.add(QuantumStoreIdentity.of(adapter));
            }
            positions.put(attribute, candidates);
        }
        var environments = quantumEnvironments(project, domain);
        if (stores.size() != 1 || !"LOCAL".equals(domain.getRuleBlock().getDeploymentTransactionMode())
                || environments.isEmpty() || environments.stream().anyMatch(environment ->
                        positions.values().stream().flatMap(List::stream)
                                .anyMatch(position -> !environment.getDomains().contains(position.owner().getName())))) {
            errors.add(prefix + TCC_PENDING);
            return;
        }

        var store = stores.iterator().next();
        if (!"jdbcQuantum".equals(store.adapter())
                || environments.stream().anyMatch(environment -> !commonLocalStore(project, environment))
                || domain.getRuleBlock().getAppPhysicalBackends().stream().noneMatch(backend ->
                        localQuantumBackend(domain, backend, service.getTransactionResourceIdLiteral(), store))) {
            errors.add(prefix + TCC_PENDING);
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
        Map<String, List<SppService.QuantumEndpoint>> bindings = new LinkedHashMap<>();
        positions.forEach((logical, candidates) -> {
            if (!candidates.get(0).keys().isEmpty())
                bindings.put(logical, candidates.stream().map(position ->
                        new SppService.QuantumEndpoint(position.attributeId(), position.keys(), position.codeTables(),
                                position.owner().getName() + "." + position.attributeId().split("\\.", 2)[0])).toList());
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

    static List<RolePlay> plays(SppDomain domain, SppClass role) {
        return domain.getRuleBlock().getRolePlays().stream()
                .filter(play -> play.role().equals(role.getName()) && !isStoredRolePlay(domain, role, play)).toList();
    }

    static boolean isAppPlayedRole(SppDomain domain, SppClass role) {
        return role.getType() == CompilationUnitType.role && !plays(domain, role).isEmpty();
    }

    private static boolean isStoredRolePlay(SppDomain domain, SppClass role, RolePlay play) {
        return domain.getRuleBlock().getAppTables().stream().flatMap(group -> group.getAppTableList().stream())
                .anyMatch(table -> table.getEntity() == role)
                && play.fields().keySet().stream().map(role::getField).filter(Objects::nonNull)
                        .noneMatch(SppField::isQuantum);
    }

    /** Compile ordinary performer facts for a stored role into the shared role mapping model. */
    static List<String> bindStoredRolePlays(SppProject project) {
        List<String> errors = new ArrayList<>();
        for (var domain : project.getSppDomainMap().values()) {
            Set<String> bound = new HashSet<>();
            for (var play : domain.getRuleBlock().getRolePlays()) {
                if (!(domain.getSppClass(play.role()) instanceof SppClass role)
                        || !isStoredRolePlay(domain, role, play)) continue;
                String provider = play.providerDomain() + "." + play.providerType();
                String prefix = domain.getName() + ": play " + play.role() + " by " + provider + ": ";
                if (isAppPlayedRole(domain, role)) {
                    errors.add(prefix + "cannot mix stored role bindings and provider-owned quantum bindings");
                    continue;
                }
                var references = domain.getMapsOfClass().values().stream()
                        .filter(unit -> unit instanceof SppClass reference && provider.equals(reference.getReferenceSource()))
                        .map(unit -> (SppClass) unit).toList();
                if (references.size() != 1 || !play.fields().values().stream()
                        .allMatch(field -> references.get(0).getField(field) != null)) {
                    errors.add(prefix + "stored role bindings require one Reference declaring the mapped provider facts");
                    continue;
                }
                var reference = references.get(0);
                String name = "Mapper" + reference.getName() + "To" + role.getName();
                if (!bound.add(name)) {
                    errors.add(prefix + "duplicate stored role binding");
                    continue;
                }
                var existing = domain.getSppClass(name);
                if (existing instanceof SppRoleMapper existingMapper && existingMapper.isAppPlay()) continue;
                if (existing != null) {
                    errors.add(prefix + "conflicts with an existing role mapping");
                    continue;
                }
                var mapper = new SppRoleMapper(name);
                mapper.setAppPlay(play);
                mapper.setRole(role);
                mapper.setDomainName(domain.getName());
                mapper.setReal(true);
                mapper.addEntity(reference);
                play.fields().forEach((field, source) -> {
                    var fact = role.getField(field);
                    var mapping = new SppRoleField(fact.getType(), field, source);
                    mapping.setArrayDimension(fact.getArrayDimension());
                    mapper.addField(mapping);
                });
                domain.getMapsOfClass().put(name, mapper);
            }
        }
        return errors;
    }

    static List<String> validateRolePlays(SppProject project, boolean storesReady) {
        List<String> errors = new ArrayList<>();
        for (var domain : project.getSppDomainMap().values()) {
            if (!storesReady) for (var unit : domain.getMapsOfClass().values()) {
                if (!(unit instanceof SppRoleMapper mapper)) continue;
                if (mapper.isAppPlay()) continue;
                boolean crossDomain = mapper.getRole().getReferenceSource() != null
                        || mapper.getDeclaredPerformers().stream().anyMatch(performer -> performer.getReferenceSource() != null);
                if (crossDomain && mapper.getRole().getSppFieldList().stream().anyMatch(SppField::isQuantum))
                    errors.add(domain.getName() + ": cross-domain quantum roles require APP play: " + mapper.getRole().getName());
            }
            for (var play : domain.getRuleBlock().getRolePlays()) {
                String prefix = domain.getName() + ": play " + play.role() + " by "
                        + play.providerDomain() + "." + play.providerType() + ": ";
                if (!(domain.getSppClass(play.role()) instanceof SppClass role)
                        || role.getType() != CompilationUnitType.role) {
                    errors.add(prefix + "unknown consumer role");
                    continue;
                }
                var owner = project.getDomain(play.providerDomain());
                if (owner == null) {
                    errors.add(prefix + "unknown provider domain");
                    continue;
                }
                if (!(owner.getSppClass(play.providerType()) instanceof SppClass performer) || !performer.isReal()) {
                    errors.add(prefix + "unknown provider type");
                    continue;
                }
                if (!Set.of(CompilationUnitType.entity, CompilationUnitType.contract, CompilationUnitType.role)
                                .contains(performer.getType()) || performer.getReferenceSource() != null
                        || owner == domain || !plays(owner, performer).isEmpty()) {
                    errors.add(prefix + "invalid provider type");
                    continue;
                }
                if (!storesReady) {
                    for (var mapping : play.fields().entrySet()) {
                        var left = role.getField(mapping.getKey());
                        var right = performer.getField(mapping.getValue());
                        if (left == null || right == null) errors.add(prefix + "unknown field " + mapping);
                        else if (play.codeTables().containsKey(mapping.getKey()) && !SppEnum.bindsProviderString(left, right))
                            errors.add(prefix + "code table requires a scalar consumer enum and scalar provider String: " + mapping);
                        else if (left.getType() instanceof SppEnum enumeration && !play.codeTables().containsKey(mapping.getKey())
                                && enumeration.needsPlayCodeTable())
                            errors.add(prefix + "code table required for uncoded enum member: " + mapping);
                        else if (left.isQuantum() != right.isQuantum())
                            errors.add(prefix + "quantum/non-quantum field mismatch " + mapping);
                        else if ((!left.getType().getName().equals(right.getType().getName())
                                || left.getArrayDimension() != right.getArrayDimension())
                                && !SppEnum.bindsProviderString(left, right))
                            errors.add(prefix + "type mismatch " + mapping);
                        else if (left.isQuantum() && !Objects.equals(
                                scale(domain, role, left), scale(owner, performer, right)))
                            errors.add(prefix + "scale mismatch " + mapping);
                        if (left != null && left.getType() instanceof SppEnum enumeration && play.codeTables().containsKey(mapping.getKey()))
                            new PlayCodeBinding(enumeration, play.codeTables().get(mapping.getKey())).validate(prefix + mapping + ": ", errors);
                    }
                    for (var field : role.getSppFieldList()) if (!isStoredRolePlay(domain, role, play) && field.isQuantum()
                            && !play.fields().containsKey(field.getName()))
                        errors.add(prefix + "unmapped consumer quantum field " + field.getName());
                    if (playKeys(owner, performer, play).isEmpty())
                        errors.add(prefix + "unmapped provider primary-key field or missing provider primary key");
                } else if (!isStoredRolePlay(domain, role, play)) {
                    var environments = quantumEnvironments(project, domain);
                    var stores = performer.getSppFieldList().stream()
                            .filter(field -> field.isQuantum() && play.fields().containsValue(field.getName()))
                            .map(field -> owner.quantumStoreAdapter(owner.quantumStoreNameForAttribute(
                                    performer.getName() + "." + field.getName()))).toList();
                    if (environments.isEmpty() || !"LOCAL".equals(domain.getRuleBlock().getDeploymentTransactionMode())
                            || environments.stream().anyMatch(env -> !env.getDomains().contains(owner.getName())
                                    || !commonLocalStore(project, env))
                            || stores.stream().anyMatch(Objects::isNull)
                            || stores.stream().map(QuantumStoreIdentity::of).anyMatch(store ->
                                    !"jdbcQuantum".equals(store.adapter())
                                    || environments.stream().flatMap(env -> env.getDomains().stream()).distinct()
                                            .map(project::getDomain).filter(Objects::nonNull)
                                            .flatMap(member -> member.getRuleBlock().getAppQuantumStoreAdapters().stream())
                                            .map(QuantumStoreIdentity::of).anyMatch(candidate -> !store.equals(candidate))
                                    || domain.getRuleBlock().getAppPhysicalBackends().stream().noneMatch(backend ->
                                            localQuantumBackend(domain, backend, domain.getRuleBlock().getDefaultUpdateResource(), store))))
                        errors.add(prefix + TCC_PENDING);
                }
            }
        }
        return errors;
    }

    private static Integer scale(SppDomain domain, SppClass type, SppField field) {
        String original = field.getDeclaredNumericSpecification();
        String specification = original;
        for (var binding : domain.getRuleBlock().getNumericBindings())
            if (binding.matches(original)) specification = binding.representation();
        for (var binding : domain.getRuleBlock().getNumericBindings())
            if (binding.target().equals(type.getName() + "." + field.getName())) specification = binding.representation();
        try {
            var dimensions = NumericBinding.dimensions(specification);
            return dimensions == null ? null : dimensions[1];
        } catch (IllegalArgumentException invalid) {
            return null; // Reported by numeric declaration validation.
        }
    }

    static List<String> playKeys(SppDomain owner, SppClass performer, RolePlay play) {
        List<String> paths = new ArrayList<>();
        for (String key : ParticipantRoleBindings.primaryKeys(owner, performer)) {
            String[] path = key.split("\\.", 2);
            String source = play.fields().entrySet().stream().filter(mapping -> path[0].equals(mapping.getValue()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (source == null) return List.of();
            paths.add(source + (path.length == 2 ? "." + path[1] : ""));
        }
        return paths;
    }

    private static List<QuantumPosition> quantumPositions(SppProject project, SppDomain domain, SppClass type, String field) {
        if (type.getField(field) == null || !type.getField(field).isQuantum() || type.getReferenceSource() != null)
            return List.of();
        var plays = plays(domain, type);
        if (plays.isEmpty()) return List.of(new QuantumPosition(domain, type.getName() + "." + field, List.of(), Map.of()));
        List<QuantumPosition> positions = new ArrayList<>();
        for (var play : plays) {
            var owner = project.getDomain(play.providerDomain());
            var performer = (SppClass) owner.getSppClass(play.providerType());
            var keys = playKeys(owner, performer, play);
            var codeTables = new LinkedHashMap<String, PlayCodeBinding>();
            for (String key : keys) {
                if (play.codeTables().containsKey(key))
                    codeTables.put(key, new PlayCodeBinding((SppEnum) type.getField(key).getType(), play.codeTables().get(key)));
            }
            positions.add(new QuantumPosition(owner, performer.getName() + "." + play.fields().get(field),
                    keys, codeTables));
        }
        return positions;
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
