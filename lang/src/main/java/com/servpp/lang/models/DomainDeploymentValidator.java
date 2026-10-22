package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Validates the complete project, after all provider and consumer sources are parsed. */
public final class DomainDeploymentValidator {
    private DomainDeploymentValidator() { }
    public static List<String> validate(SppProject project) {
        List<String> errors = new ArrayList<>(DeploymentEnvironmentPlanner.prepare(project));
        var applications = new HashSet<String>();
        for (var domain : project.getSppDomainMap().values()) errors.addAll(domain.prepareRoleCompositions());
        // References retain the owning domain's APP identity, never a guessed first field.
        for (var domain : project.getSppDomainMap().values()) for (var unit : domain.getMapsOfClass().values()) {
            if (!(unit instanceof SppClass reference) || reference.getReferenceSource()==null) continue;
            String[] identity=reference.getReferenceSource().split("\\.");
            if (identity.length!=2) continue;
            var owner=project.getDomain(identity[0]);
            var original=owner==null?null:owner.getSppClass(identity[1]);
            if (original instanceof SppClass type && owner.getRuleBlock().getAppTables().stream()
                    .flatMap(group -> group.getAppTableList().stream()).anyMatch(table -> table.getEntity()==type
                        && (!table.getPrimaryKeys().isEmpty() || table.getAppColumns().stream()
                            .anyMatch(column -> column.isPrimaryKey() && column.isObjectBacked()))))
                reference.setIdentityPaths(ParticipantRoleBindings.primaryKeys(owner,type));
        }
        // All query destinations must be available before cross-domain signatures are resolved.
        for (var domain : project.getSppDomainMap().values()) errors.addAll(domain.prepareDefaultParticipantBindings());
        for (SppDomain domain : project.getSppDomainMap().values()) {
            for (SppCompilationUnit unit : domain.getMapsOfClass().values()) if (unit instanceof SppService service)
                service.setBusinessConstants(domain.getConstants());
            var locales = domain.getRuleBlock().getSupportedLocales();
            String defaultLocale = domain.getRuleBlock().getDefaultLocale();
            if (defaultLocale != null && (!locales.contains(defaultLocale) || new HashSet<>(locales).size() != locales.size()))
                errors.add(domain.getName() + ": i18n must declare a unique supported list containing its default locale");
            for (var locale : domain.getRuleBlock().getConstantTranslations().entrySet()) {
                if (!locales.contains(locale.getKey())) errors.add(domain.getName() + ": unsupported translation locale " + locale.getKey());
                for (var translation : locale.getValue().entrySet()) {
                    var constant = domain.getConstants().get(translation.getKey());
                    if (constant == null) errors.add(domain.getName() + ": undefined translation constant " + translation.getKey());
                    else if (!"String".equals(constant.getType().getName()) || !translation.getValue().startsWith("\""))
                        errors.add(domain.getName() + ": localized values must be String constants; numeric business constants retain their value");
                }
            }
            for (var constant : domain.getConstants().values()) {
                String type = constant.getType().getName(), value = constant.getLiteral();
                boolean valid = "String".equals(type) ? value.startsWith("\"")
                        : "boolean".equals(type) ? value.equals("true") || value.equals("false")
                        : "char".equals(type) ? value.startsWith("'")
                        : com.servpp.lang.models.app.NumericBinding.isNumeric(type) && value.matches("[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
                if (!valid) errors.add(domain.getName() + ": incompatible literal for constant " + constant.getName());
            }
            domain.getRuleBlock().getApplicationDeployments().forEach((name, deployed) -> {
                if (!applications.add(name)) errors.add("Duplicate application " + name);
                if (new HashSet<>(deployed).size() != deployed.size()) errors.add("Duplicate deployed domain in " + name);
                for (String member : deployed) if (project.getDomain(member) == null) errors.add("Application " + name + " deploys undefined domain " + member);
                if (!deployed.contains(domain.getName())) errors.add("Application " + name + " must deploy its declaring domain " + domain.getName());
            });
            for (SppCompilationUnit unit : domain.getMapsOfClass().values()) {
                if (!(unit instanceof SppClass reference) || reference.getReferenceSource() == null) continue;
                String[] source = reference.getReferenceSource().split("\\.");
                SppDomain owner = source.length == 2 ? project.getDomain(source[0]) : null;
                SppCompilationUnit target = owner == null ? null : owner.getSppClass(source[1]);
                if (!(target instanceof SppClass entity) || !java.util.Set.of(IConstance.CompilationUnitType.entity, IConstance.CompilationUnitType.role, IConstance.CompilationUnitType.contract).contains(entity.getType())) {
                    errors.add(domain.getName() + ": Reference " + reference.getName() + " has undefined provider participant " + reference.getReferenceSource());
                    continue;
                }
                reference.setReferenceSourceKind(entity.getType());
                for (SppField fact : reference.getSppFieldList()) {
                    SppField original = entity.getField(fact.getName());
                    if (original == null || !compatible(fact, original)) errors.add(domain.getName() + ": incompatible external fact " + reference.getName() + "." + fact.getName());
                }
            }
            inferReferenceBindings(project, domain, errors);
            errors.addAll(domain.prepareDefaultParticipantBindings());
            inferRoleHydrations(project, domain, errors);
            var bound = new HashSet<String>();
            for (var binding : domain.getRuleBlock().getLocalServiceBindings()) {
                String prefix = domain.getName() + ": binding " + binding.getServiceName() + " -> " + binding.getTarget();
                if (!bound.add(binding.getServiceName())) errors.add(prefix + " is duplicated");
                SppCompilationUnit local = domain.getMapsOfClass().values().stream()
                        .filter(candidate -> candidate instanceof SppService service && binding.getServiceName().equals(service.getFuncName())).findFirst().orElse(null);
                String[] name = binding.getTarget().split("\\.");
                SppDomain owner = name.length == 2 ? project.getDomain(name[0]) : null;
                SppCompilationUnit remote = owner == null ? null : owner.getMapsOfClass().values().stream()
                        .filter(candidate -> candidate instanceof SppService service && name[1].equals(service.getFuncName())).findFirst().orElse(null);
                if (!(local instanceof SppService caller) || !(remote instanceof SppService provider)) {
                    errors.add(prefix + " refers to an undefined service"); continue;
                }
                if (caller.getType() != IConstance.CompilationUnitType.atomicservice || provider.getType() != IConstance.CompilationUnitType.atomicservice
                        || caller.getServiceType() != provider.getServiceType()
                        || (caller.getServiceType() != IConstance.ServiceType.query && caller.getServiceType() != IConstance.ServiceType.update && caller.getServiceType() != IConstance.ServiceType.calculate && caller.getServiceType() != IConstance.ServiceType.check)) {
                    errors.add(prefix + " requires matching query, update, calculate or check atomic delegation"); continue;
                }
                if (caller.hasBusinessImplementation() || caller.isDeclaredQuantumService()) errors.add(prefix + " cannot replace an implemented business service");
                if (binding.isRoleBinding()) {
                    ParticipantRoleBindings.configure(project,binding,domain,owner,caller,provider,errors);
                    caller.setLocalServiceBinding(binding); caller.setLocalServiceProvider(provider);
                    continue;
                }
                var inputs = caller.getServiceBody().getSppLocalVarList();
                var outputs = provider.getServiceBody().getSppLocalVarList();
                if (inputs.size() != outputs.size() || binding.getParticipantFacts().size() != inputs.size()) {
                    errors.add(prefix + " must map every participant by position"); continue;
                }
                for (int index = 0; index < inputs.size(); index++) {
                    var from = inputs.get(index); var to = outputs.get(index);
                    var facts = binding.getParticipantFacts().get(index);
                    if (!(from.getType() instanceof SppClass fromType) || !(to.getType() instanceof SppClass toType)
                            || from.getArrayDimension() != to.getArrayDimension() || facts == null) {
                        errors.add(prefix + " participant " + index + " has incompatible type/dimensions"); continue;
                    }
                    if (new HashSet<>(facts).size() != facts.size()) errors.add(prefix + " repeats participant facts");
                    var keys = binding.getParticipantIdentities().getOrDefault(index, List.of());
                    if (from.getArrayDimension() > 0 && keys.isEmpty()) errors.add(prefix + " collection projection requires explicit identity facts");
                    if (!keys.stream().allMatch(key -> facts.contains(key.split("\\.")[0])) || new HashSet<>(keys).size() != keys.size()) errors.add(prefix + " identities must be unique selected facts");
                    for (String key : keys) {
                        SppField a = fieldPath(fromType, key), b = fieldPath(toType, key);
                        if (a == null || b == null || !compatible(a,b) || (a.getType().getType() != null && a.getType().getType() != IConstance.CompilationUnitType.Enum) || a.getArrayDimension() != 0)
                            errors.add(prefix + " collection identity requires compatible scalar fact paths: " + key);
                    }
                    if (binding.getExistingCollections().contains(index) && from.getArrayDimension() != 1) errors.add(prefix + " existing applies to participant collections");
                    for (String fact : facts) {
                        SppField a = fromType.getField(fact), b = toType.getField(fact);
                        if (a == null || b == null || !compatible(a,b)) errors.add(prefix + " has incompatible fact " + index + "." + fact);
                    }
                }
                // Physical delegation is generated only after validation succeeds.
                caller.setLocalServiceBinding(binding);
                caller.setLocalServiceProvider(provider);
            }
        }
        Map<String,String> delegates = new java.util.LinkedHashMap<>();
        for (SppDomain domain : project.getSppDomainMap().values()) {
            for (var binding : domain.getRuleBlock().getLocalServiceBindings())
                delegates.put(domain.getName() + "." + binding.getServiceName(), binding.getTarget());
            for (var application : domain.getRuleBlock().getApplicationDeployments().entrySet()) {
                for (String member : application.getValue()) {
                    SppDomain deployed = project.getDomain(member);
                    if (deployed == null) continue;
                    for (var binding : deployed.getRuleBlock().getLocalServiceBindings()) {
                        String owner = binding.getTarget().split("\\.")[0];
                        if (domain.getRuleBlock().getDeploymentEnvironment() == null && !application.getValue().contains(owner)) errors.add("Application " + application.getKey() + " omits local provider " + owner);
                    }
                    for (var unit : deployed.getMapsOfClass().values()) if (unit instanceof SppService service)
                        for (var hydration : service.getRoleHydrations()) {
                            String owner = hydration.binding().getTarget().split("\\.")[0];
                            if (domain.getRuleBlock().getDeploymentEnvironment() == null && !application.getValue().contains(owner)) errors.add("Application " + application.getKey() + " omits role entity provider " + owner);
                        }
                }
            }
        }
        for (String service : delegates.keySet()) {
            var visited = new HashSet<String>();
            String next = service;
            while (next != null) {
                if (!visited.add(next)) { errors.add("Cyclic local service binding: " + visited + " -> " + next); break; }
                next = delegates.get(next);
            }
        }
        prepareStorageOwnership(project, errors);
        for (var domain : project.getSppDomainMap().values())
            for (var group : domain.getRuleBlock().getAppTables()) for (var table : group.getAppTableList())
                for (var entry : table.getMappedStorageModes().entrySet()) {
                    var field = table.getEntity().getField(entry.getKey());
                    boolean participantSnapshot = "snapshot".equals(entry.getValue())
                            && (field.getType().getType() == IConstance.CompilationUnitType.entity
                            || field.getType().getType() == IConstance.CompilationUnitType.role
                            || field.getType().getType() == IConstance.CompilationUnitType.contract)
                            && !field.isPerformerRelation()
                            && !field.isPerformerStorageIdentity() && !field.isQuantum();
                    if (participantSnapshot) continue;
                    if (!field.isBorrowedStorageFact() || field.isQuantum() || field.isPerformerStorageIdentity()
                            || field.isPerformerRelation())
                        errors.add("APP mapped storage requires an ordinary non-identity performer attribute: "
                                + table.getEntity().getName() + "." + entry.getKey());
                }

        for (var domain : project.getSppDomainMap().values())
            if (domain.getRuleBlock().isParallelOrdinaryUpdates() && !domain.getRuleBlock().hasTransactionPolicy())
                errors.add("ordinaryUpdates PARALLEL requires a transaction mode/resource or a deployment environment in " + domain.getName());
        validateOrdinaryUpdateModes(project, errors);
        validateStoredRelationshipIdentities(project,errors);
        validateLocalJdbcSubmissionScope(project,errors);
        validateHydrationCycles(project, errors);
        validateDomainRoutes(project, errors);
        return errors;
    }
    private static void validateDomainRoutes(SppProject project, List<String> errors) {
        for (var declaring : project.getSppDomainMap().values()) {
            for (var application : declaring.getRuleBlock().getApplicationDeployments().entrySet()) {
                var deployed=application.getValue();
                var routes=new java.util.LinkedHashMap<String,com.servpp.lang.models.app.DomainRoute>();
                for (String name : deployed) {
                    var domain=project.getDomain(name); if (domain==null) continue;
                    for (var route : domain.getRuleBlock().getDomainRoutes().values()) {
                        var previous=routes.putIfAbsent(route.domain(),route);
                        if (previous!=null && !previous.equals(route)) errors.add("Application "+application.getKey()+" has conflicting domain routes for "+route.domain());
                        if (project.getDomain(route.domain())==null) errors.add("Undefined routed domain "+route.domain());
                        if (deployed.contains(route.domain())) errors.add("Application "+application.getKey()+" routes a locally deployed domain "+route.domain());
                        if (route.timeoutMillis()<=0) errors.add("Domain route requires positive timeout: "+route.domain());
                        if (!route.endpoint().contains("${")) {
                            try {
                                var uri=java.net.URI.create(route.endpoint());
                                if (!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null
                                        || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                                    errors.add("Invalid domain route endpoint: "+route.domain());
                            } catch (IllegalArgumentException failure) { errors.add("Invalid domain route endpoint: "+route.domain()); }
                        }
                    }
                }
                if (!routes.isEmpty() && deployed.stream().map(project::getDomain).filter(java.util.Objects::nonNull)
                        .noneMatch(domain -> domain.getRuleBlock().getPhysicalConfiguration().containsKey("domainRpcToken")))
                    errors.add("Application "+application.getKey()+" requires PHY domainRpcToken for remote domain calls");
                for (String name : deployed) {
                    var domain=project.getDomain(name); if (domain==null) continue;
                    var targets=new java.util.LinkedHashSet<String>();
                    for (var binding : domain.getRuleBlock().getLocalServiceBindings()) targets.add(binding.getTarget().split("\\.")[0]);
                    for (var unit : domain.getMapsOfClass().values()) if (unit instanceof SppService service)
                        for (var hydration : service.getRoleHydrations()) targets.add(hydration.binding().getTarget().split("\\.")[0]);
                    for (var unit : domain.getMapsOfClass().values()) if (unit instanceof SppService service)
                        validateRemoteCalls(service,service.getServiceBody().getSppServiceCallList(),deployed,application.getKey(),errors);
                    for (String target : targets) if (!deployed.contains(target) && !routes.containsKey(target))
                        errors.add("Application "+application.getKey()+" requires a PHY domainRoute for provider "+target);
                }
            }
        }
    }
    private static void validateRemoteCalls(SppService scene, List<IServiceBodyStatement> statements,
                                            List<String> deployed, String application, List<String> errors) {
        if (statements==null) return;
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call) {
                var provider=call.getCallee();
                var visitedProviders = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SppService,Boolean>());
                while (provider!=null && provider.getLocalServiceProvider()!=null && visitedProviders.add(provider)) provider=provider.getLocalServiceProvider();
                if (provider!=null && provider.getDomainName()!=null && !deployed.contains(provider.getDomainName())) {
                    if (provider.isDeclaredQuantumService()) errors.add("Application "+application+" requires remote quantum receipt/reversal protocol for "+provider.getFuncName());
                    if (scene.requiresLockedQuery(call)) errors.add("Application "+application+" requires remote transaction query lock protocol for "+provider.getFuncName());
                }
                if (provider!=null && provider.getDomainName()!=null && scene.requiresLockedQuery(call))
                    for (var hydration : provider.getRoleHydrations())
                        if (!deployed.contains(hydration.binding().getTarget().split("\\.")[0]))
                            errors.add("Application "+application+" requires remote transaction query lock protocol for role hydration "+hydration.binding().getTarget());
                validateRemoteCalls(scene,call.getGuardStatements(),deployed,application,errors);
            } else if (statement instanceof TransactionBlock block) {
                validateRemoteXaWrites(block.getTransactionCallList(),deployed,application,new HashSet<>(),errors);
                validateRemoteCalls(scene,block.getTransactionCallList(),deployed,application,errors);
            } else if (statement instanceof ExecuteBlock block)
                validateRemoteCalls(scene,block.getExecuteCallList(),deployed,application,errors);
        }
    }
    /** RPC calls currently prepare separate XA branches; repeated writes need a shared branch session. */
    private static void validateRemoteXaWrites(List<IServiceBodyStatement> statements, List<String> deployed,
                                               String application, java.util.Set<String> written, List<String> errors) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call) {
                var provider=call.getCallee();
                var visitedProviders = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SppService,Boolean>());
                while (provider!=null && provider.getLocalServiceProvider()!=null && visitedProviders.add(provider)) provider=provider.getLocalServiceProvider();
                if (provider!=null && provider.getServiceType()==IConstance.ServiceType.update
                        && "XA".equals(provider.getTransactionModeName()) && !deployed.contains(provider.getDomainName())) {
                    if (provider.getScopeItem()==null) continue;
                    for (var write : provider.getScopeItem().getPersistenceRules()) {
                        if (write.argumentIndex()<0 || write.argumentIndex()>=call.getSppParameterList().size()) continue;
                        var argument=call.getSppParameterList().get(write.argumentIndex());
                        String key=provider.getDomainName()+":"+write.objectType()+":"+argument.getName();
                        if (!written.add(key)) errors.add("Application "+application+" remote XA updates of participant "
                                +argument.getName()+" within one transaction require a shared remote XA branch session; "
                                +"the current atomic RPC protocol supports one update per participant.");
                    }
                }
                validateRemoteXaWrites(call.getGuardStatements(),deployed,application,written,errors);
            } else if (statement instanceof TransactionBlock block && "REQUIRED".equals(block.getPropagation()))
                validateRemoteXaWrites(block.getTransactionCallList(),deployed,application,written,errors);
            else if (statement instanceof ExecuteBlock block)
                validateRemoteXaWrites(block.getExecuteCallList(),deployed,application,written,errors);
        }
    }
    private static void prepareStorageOwnership(SppProject project, List<String> errors) {
        for (var domain : project.getSppDomainMap().values()) {
            if (!domain.getRuleBlock().hasTransactionPolicy() && domain.getRuleBlock().getAppTables().stream()
                    .flatMap(group -> group.getAppTableList().stream()).allMatch(table -> table.getMappedStorageModes().isEmpty() && !table.isIndependentIdentity() && table.getPerformerTable() == null)) continue;
            for (var unit : domain.getMapsOfClass().values()) {
                if (!(unit instanceof SppRoleMapper mapper) || mapper.getRole().getReferenceSource() != null
                        || mapper.getRole().getType() != IConstance.CompilationUnitType.role) continue;
                mapper.getRole().setStorageOwnershipResolved(true);
                for (var field : mapper.getRole().getSppFieldList())
                    if (field.isInheritedThroughTakeAll()) field.setBorrowedStorageFact(true);
                for (var performer : mapper.getEntities()) {
                    String[] identity = ParticipantRoleBindings.identity(domain,performer).split("\\.");
                    var owner = project.getDomain(identity[0]);
                    var root = owner == null ? null : owner.getSppClass(identity[1]);
                    var keys = root instanceof SppClass type ? ParticipantRoleBindings.primaryKeys(owner,type) : List.<String>of();
                    if (mapper.isMapSame()) for (var field : mapper.getRole().getSppFieldList()) {
                        var original = performer.getField(field.getName());
                        if (original == null || field.getArrayDimension() > 0
                                || !original.getType().equals(field.getType()) || original.isQuantum() != field.isQuantum()) continue;
                        field.setBorrowedStorageFact(true);
                        if (keys.contains(field.getName())) field.setPerformerStorageIdentity(true);
                    }
                    for (var field : mapper.getRole().getSppFieldList())
                        if (field.isBorrowedStorageFact() && keys.contains(field.getName())) field.setPerformerStorageIdentity(true);
                    for (var mapped : mapper.getSppFieldList()) {
                        if (!(mapped instanceof SppRoleField mapping)) continue;
                        String source = performer.getName().equals(mapping.getEntityName()) ? mapping.getName() : mapping.getEntityName();
                        var field = mapper.getRole().getField(mapping.getName());
                        if (field != null && field.getType().equals(performer) && mapping.getEntityName().equals(performer.getName()))
                            field.setPerformerRelation(true);
                        if (field != null && performer.getField(source) != null && field.getArrayDimension() == 0) {
                            field.setBorrowedStorageFact(true);
                            if (keys.contains(source)) field.setPerformerStorageIdentity(true);
                        }
                    }
                }
            }
        }
    }
    private static void validateLocalJdbcSubmissionScope(SppProject project, List<String> errors) {
        var scopes = new java.util.LinkedHashSet<String>();
        for (var domain : project.getSppDomainMap().values()) {
            var rules = domain.getRuleBlock();
            if (!rules.hasTransactionPolicy() || (rules.getDeploymentTransactionMode() != null && !"LOCAL".equals(rules.getDeploymentTransactionMode()))) continue;
            for (var backend : rules.getAppPhysicalBackends()) {
                if (!List.of("localJdbc","local-jdbc").contains(backend.getAdapterName())) continue;
                String datasource = backend.getConfiguration().getOrDefault("datasourceUrl",rules.getPhysicalConfiguration().get("datasourceUrl"));
                String schema = backend.getConfiguration().getOrDefault("schema",rules.getPhysicalConfiguration().getOrDefault("schema","<default>"));
                if (datasource != null) scopes.add(datasource + ":schema=" + schema);
                for (var repository : rules.getAppRepositoryAdapters()) {
                    String repositoryUrl = repository.getConfiguration().get("datasourceUrl");
                    if (datasource != null && repositoryUrl != null && !datasource.equals(repositoryUrl))
                        errors.add("Domain " + domain.getName() + " transaction storage v1 localJdbc repository must share its declared submission datasource; configure a supported distributed coordinator for separate stores.");
                }
                for (var quantum : rules.getAppQuantumStoreAdapters()) {
                    String quantumUrl = quantum.getConfiguration().get("datasourceUrl");
                    if (datasource != null && quantumUrl != null && !datasource.equals(quantumUrl))
                        errors.add("Domain " + domain.getName() + " transaction storage v1 default localJdbc plan requires a common physical datasource for quantum access and ordinary stage access; separate-store access needs an explicit protocol capability.");
                }
            }
        }
        if (scopes.size() > 1) errors.add("Transaction storage v1 localJdbc services declare different database/schema submission scopes; AT mode alone cannot make these resources share a local commit. Configure a supported global coordinator instead of the default localJdbc plan.");
    }

    private static void validateStoredRelationshipIdentities(SppProject project, List<String> errors) {
        var tables = new java.util.LinkedHashMap<String,com.servpp.lang.models.app.AppTable>();
        for (var domain : project.getSppDomainMap().values())
            for (var group : domain.getRuleBlock().getAppTables()) for (var table : group.getAppTableList())
                if (table.getEntity() != null) tables.put(ParticipantRoleBindings.identity(domain,table.getEntity()),table);
        for (var domain : project.getSppDomainMap().values()) {
            if (!domain.getRuleBlock().hasTransactionPolicy()) continue;
            for (var group : domain.getRuleBlock().getAppTables()) for (var table : group.getAppTableList()) {
                if (table.getEntity() == null) continue;
                for (var field : table.getEntity().getSppFieldList()) {
                    if (!field.isStoredHere() || field.isSnapshotStorageFact() || !(field.getType() instanceof SppClass nested)) continue;
                    var target = tables.get(ParticipantRoleBindings.identity(domain,nested));
                    if (target != null && target.getPrimaryKeys().stream().anyMatch(key -> key.getSppField() == null))
                        errors.add("Table " + table.getName() + " association " + field.getName()
                                + " uses an APP platform identity; transaction storage v1 requires a supported identity transport protocol before storing this association.");
                }
            }
        }
    }

    private static void validateOrdinaryUpdateModes(SppProject project, List<String> errors) {
        var modes = new java.util.LinkedHashMap<String,String>();
        var services = new java.util.LinkedHashMap<String,String>();
        for (var domain : project.getSppDomainMap().values()) {
            if (!domain.getRuleBlock().hasTransactionPolicy()) continue;
            for (var unit : domain.getMapsOfClass().values()) {
                if (!(unit instanceof SppService service) || service.getType() != IConstance.CompilationUnitType.atomicservice
                        || service.getServiceType() != IConstance.ServiceType.update || service.isDeclaredQuantumService()
                        || service.hasLocalServiceBinding() || service.getScopeItem() == null) continue;
                for (var write : service.getScopeItem().getPersistenceRules()) {
                    var target = domain.getSppClass(write.objectType());
                    if (!(target instanceof SppClass participant)) continue;
                    String identity = ParticipantRoleBindings.identity(domain,participant);
                    String mode = service.getTransactionModeName();
                    String previous = modes.putIfAbsent(identity,mode);
                    if (previous != null && !previous.equals(mode)) errors.add("Participant " + identity
                            + " ordinary updates must use one transaction mode: " + services.get(identity) + "=" + previous
                            + ", " + domain.getName() + "." + service.getFuncName() + "=" + mode);
                    services.putIfAbsent(identity,domain.getName() + "." + service.getFuncName());
                }
            }
        }
    }
    private static void validateHydrationCycles(SppProject project, List<String> errors) {
        var graph = new java.util.LinkedHashMap<String,List<String>>();
        for (var domain : project.getSppDomainMap().values()) {
            for (var binding : domain.getRuleBlock().getLocalServiceBindings())
                graph.computeIfAbsent(domain.getName() + "." + binding.getServiceName(), ignored -> new ArrayList<>()).add(binding.getTarget());
            for (var unit : domain.getMapsOfClass().values()) if (unit instanceof SppService service)
                for (var hydration : service.getRoleHydrations())
                    graph.computeIfAbsent(domain.getName() + "." + service.getFuncName(), ignored -> new ArrayList<>()).add(hydration.binding().getTarget());
        }
        var visited = new HashSet<String>();
        for (String service : graph.keySet()) hydrationCycle(service, graph, new java.util.LinkedHashSet<>(), visited, errors);
    }
    private static void hydrationCycle(String service, Map<String,List<String>> graph, java.util.Set<String> path,
                                       java.util.Set<String> visited, List<String> errors) {
        if (path.contains(service)) { errors.add("Cyclic participant binding: " + path + " -> " + service); return; }
        if (!visited.add(service)) return;
        path.add(service);
        for (String provider : graph.getOrDefault(service, List.of())) hydrationCycle(provider, graph, path, visited, errors);
        path.remove(service);
    }
    private static void inferReferenceBindings(SppProject project, SppDomain domain, List<String> errors) {
        for (var unit : new ArrayList<>(domain.getMapsOfClass().values())) {
            if (!(unit instanceof SppService caller) || caller.getType() != IConstance.CompilationUnitType.atomicservice
                    || caller.hasBusinessImplementation() || caller.isDeclaredQuantumService()
                    || domain.getRuleBlock().getLocalServiceBindings().stream().anyMatch(binding -> binding.getServiceName().equals(caller.getFuncName()))) continue;
            var configured = caller.getScopeItem();
            if (configured != null && (!configured.isLocal() || !configured.getRepositoryHydrationRules().isEmpty()
                    || !configured.getPersistenceRules().isEmpty() || configured.hasQuantumConservationAttributes())) continue;
            var parameters = caller.getServiceBody().getSppLocalVarList();
            var targets = caller.getImplicitCrudTarget() == null ? parameters.stream().map(SppLocalVar::getType).toList()
                    : java.util.List.<SppCompilationUnit>of(caller.getImplicitCrudTarget());
            var owners = targets.stream().filter(type -> type instanceof SppClass)
                    .map(type -> ((SppClass) type).getReferenceSource()).filter(java.util.Objects::nonNull)
                    .map(source -> source.split("\\.")[0]).filter(owner -> !owner.equals(domain.getName())).distinct().toList();
            if (caller.getImplicitCrudTarget() != null && parameters.size() == 1
                    && caller.getImplicitCrudTarget().getReferenceSource() == null
                    && caller.getImplicitCrudTarget() != parameters.get(0).getType()) owners = java.util.List.of(domain.getName());
            var candidates = new ArrayList<SppService>();
            for (String owner : owners) {
                var providerDomain = project.getDomain(owner);
                if (providerDomain == null) continue;
                if (caller.isImplicitParticipantCrud() && parameters.size() == 1
                        && caller.getImplicitCrudTarget() != null) {
                    var reference = caller.getImplicitCrudTarget();
                    var source = reference.getReferenceSource() == null ? null : reference.getReferenceSource().split("\\.");
                    var target = source == null ? reference : source.length == 2 ? providerDomain.getSppClass(source[1]) : null;
                    if (target instanceof SppClass participant) {
                        providerDomain.implicitCrudService(caller.getServiceType() == IConstance.ServiceType.query
                                ? "find" : caller.getPersistenceOperation(), participant, parameters.get(0).getArrayDimension());
                        errors.addAll(providerDomain.prepareDefaultParticipantBindings());
                    }
                }
                for (var providerUnit : providerDomain.getMapsOfClass().values()) {
                    if (!(providerUnit instanceof SppService provider) || provider == caller || provider.getType() != IConstance.CompilationUnitType.atomicservice
                            || provider.getServiceType() != caller.getServiceType()
                            || provider.getScopeItem() != null && provider.getScopeItem().isImplicitDefaultBinding()
                            || !java.util.Objects.equals(caller.getPersistenceOperation(), provider.getPersistenceOperation())) continue;
                    var inputs = provider.getServiceBody().getSppLocalVarList();
                    if (inputs.size() == parameters.size() && java.util.stream.IntStream.range(0, inputs.size()).allMatch(index ->
                            parameters.get(index).getType() instanceof SppClass && inputs.get(index).getType() instanceof SppClass
                                    && parameters.get(index).getArrayDimension() == inputs.get(index).getArrayDimension()
                                    && (caller.isImplicitParticipantCrud()
                                        ? (provider.isImplicitParticipantCrud() || provider.getFuncName().equals(
                                                (caller.getServiceType() == IConstance.ServiceType.query ? "find" : caller.getPersistenceOperation())
                                                    + inputs.get(index).getType().getName())) && ParticipantRoleBindings.identity(domain, caller.getImplicitCrudTarget()).equals(
                                                ParticipantRoleBindings.identity(providerDomain, (SppClass)inputs.get(index).getType()))
                                            && ParticipantRoleBindings.resolve(domain, providerDomain,
                                                (SppClass)parameters.get(index).getType(), (SppClass)inputs.get(index).getType()) != null
                                        : ParticipantRoleBindings.identity(domain,(SppClass)parameters.get(index).getType()).equals(
                                            ParticipantRoleBindings.identity(providerDomain,(SppClass)inputs.get(index).getType()))))) candidates.add(provider);
                }
            }
            if (candidates.size() > 1) { errors.add(domain.getName() + ": ambiguous Reference service " + caller.getFuncName() + "; use bind"); continue; }
            if (!owners.isEmpty() && candidates.isEmpty()) {
                errors.add(domain.getName() + ": missing compatible Reference provider for " + caller.getFuncName() + "; use bind");
                continue;
            }
            if (candidates.size() == 1) {
                var provider = candidates.get(0);
                var binding = new com.servpp.lang.models.app.LocalServiceBinding(caller.getFuncName(), providerDomain(project, provider).getName() + "." + provider.getFuncName());
                binding.setRoleBinding(true);
                domain.getRuleBlock().getLocalServiceBindings().add(binding);
            }
        }
    }
    private static SppDomain providerDomain(SppProject project, SppService provider) {
        return project.getSppDomainMap().values().stream().filter(domain -> domain.getMapsOfClass().values().stream().anyMatch(unit -> unit == provider)).findFirst().orElse(null);
    }
    private static List<String> primaryKeys(SppDomain domain, SppClass type) {
        if (domain == null) return List.of();
        for (var table : domain.getRuleBlock().getAppTables().stream().flatMap(group -> group.getAppTableList().stream()).toList()) {
            if (table.getEntity() != null && table.getEntity().getName().equals(type.getName()))
                return table.getPrimaryKeys().stream().map(key -> key.getObjectPath()).toList();
        }
        return List.of();
    }
    private static void inferRoleHydrations(SppProject project, SppDomain domain, List<String> errors) {
        for (var unit : domain.getMapsOfClass().values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null) continue;
            service.getRoleHydrations().clear(); service.getOwnedRoleWrites().clear();
            if (domain.getRuleBlock().getLocalServiceBindings().stream().anyMatch(binding -> binding.getServiceName().equals(service.getFuncName()))) continue;
            for (var mapperUnit : domain.getMapsOfClass().values()) {
                if (!(mapperUnit instanceof SppRoleMapper mapper) || mapper.getEntities() == null
                        || mapper.getRole().getType() == IConstance.CompilationUnitType.entity
                        || mapper.getRole().getReferenceSourceKind() == IConstance.CompilationUnitType.entity) continue;
                var inputs = service.getServiceBody().getSppLocalVarList();
                for (int index = 0; index < inputs.size(); index++) {
                    var parameter = inputs.get(index);
                    if (parameter.getType() != mapper.getRole()) continue;
                    boolean queried = service.getServiceType() == IConstance.ServiceType.query
                            && (service.isQueryFromPerformers() || service.getScopeItem().getRepositoryHydrationRules().stream()
                            .anyMatch(rule -> rule.responseMethodName().equals(parameter.getName())));
                    for (var performer : mapper.getEntities()) {
                        String source = ParticipantRoleBindings.identity(domain,performer);
                        var owner = project.getDomain(source.split("\\.")[0]);
                        if (owner == null) continue;
                        if (queried) {
                            var providers = owner.getMapsOfClass().values().stream().filter(candidate -> candidate instanceof SppService query
                                    && query.getType() == IConstance.CompilationUnitType.atomicservice
                                    && query.getServiceType() == IConstance.ServiceType.query && !query.hasBusinessImplementation()
                                    && query.getReturns().getSppLocalVarList().isEmpty() && query.getServiceBody().getSppLocalVarList().size() == 1
                                    && query.getServiceBody().getLocalVar(0).getArrayDimension() == 0
                                    && query.getServiceBody().getLocalVar(0).getType() instanceof SppClass target
                                    && source.equals(ParticipantRoleBindings.identity(owner,target))
                                    && query.getScopeItem() != null && (query.isQueryFromPerformers() || query.getScopeItem().getRepositoryHydrationRules().stream()
                                        .anyMatch(rule -> rule.appDdlInferred() || "@singleton".equals(rule.getObjectIdPath()))))
                                    .map(candidate -> (SppService)candidate).toList();
                            if (providers.size() != 1) {
                                errors.add(domain.getName() + ": recursive query of " + mapper.getRole().getName()
                                        + " requires a unique primary-key query for performer " + source); continue;
                            }
                            var provider = providers.get(0);
                            var binding = new com.servpp.lang.models.app.LocalServiceBinding(service.getFuncName(),owner.getName() + "." + provider.getFuncName());
                            binding.getParticipantFacts().put(0,performer.getSppFieldList().stream().map(SppField::getName).toList());
                            service.getRoleHydrations().add(new SppService.RoleHydration(index,binding,mapper,performer));
                        }
                        // Owned root entities are persisted with their role. References remain read-only here.
                        if (!domain.getRuleBlock().hasTransactionPolicy() && performer.getReferenceSource() == null && performer.getType() == IConstance.CompilationUnitType.entity) {
                            for (var write : service.getScopeItem().getPersistenceRules()) {
                                if (write.argumentIndex() != index) continue;
                                var keys = ParticipantRoleBindings.primaryKeys(domain,performer);
                                if (!keys.isEmpty()) service.getOwnedRoleWrites().add(new SppService.OwnedRoleWrite(index,write.operation(),mapper,performer,keys));
                            }
                        }
                    }
                }
            }
        }
    }
    private static SppField fieldPath(SppClass type, String path) {
        SppField result = null;
        for (String name : path.split("\\.")) {
            result = type.getField(name);
            if (result == null) return null;
            if (result.getType() instanceof SppClass nested) type = nested;
        }
        return result;
    }
    private static boolean compatible(SppField left, SppField right) {
        return left.getArrayDimension() == right.getArrayDimension() && left.getType().getName().equals(right.getType().getName());
    }
}
