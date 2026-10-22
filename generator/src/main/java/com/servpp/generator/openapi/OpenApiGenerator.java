package com.servpp.generator.openapi;

import com.servpp.ILogable;
import com.servpp.generator.db.DDLGenerator;
import com.servpp.generator.db.DbColumn;
import com.servpp.generator.db.DbTable;
import com.servpp.lang.general.BaseClassGenerator;
import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.IFileGenerator;
import com.servpp.lang.models.*;
import com.servpp.lang.models.app.AnnotationDefine;
import com.servpp.lang.models.app.AppHeader;
import com.servpp.lang.models.app.AppIndex;
import com.servpp.lang.models.app.AppColumn;
import com.servpp.lang.models.app.AppPhysicalBackend;
import com.servpp.lang.models.app.AppPhysicalBinding;
import com.servpp.lang.models.app.AppQuantumDefinition;
import com.servpp.lang.models.app.AppQuantumStore;
import com.servpp.lang.models.app.AppQuantumStoreAdapter;
import com.servpp.lang.models.app.AppRepositoryAdapter;
import com.servpp.lang.models.app.AppRemoteAdapter;
import com.servpp.lang.models.app.AppSemanticMigrationPlan;
import com.servpp.lang.models.app.AppTable;
import com.servpp.lang.models.app.AppTables;
import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.models.app.SppExtendField;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author lidong@date 2023-11-13@version 1.0
 */
public class OpenApiGenerator extends BaseClassGenerator implements ILogable {
    private String catalogNamespace;
    private boolean roleStoragePlan;

    public void setCatalogNamespace(String domainName) {
        if (domainName == null || !domainName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid catalog domain: " + domainName);
        }
        catalogNamespace = domainName;
    }

    private File catalogFile(File resources, String name) throws IOException {
        String directory = "META-INF/spp/" + (catalogNamespace == null ? "" : "domains/" + catalogNamespace + "/");
        File file = new File(resources, directory + name);
        ensureDirectory(file.getParentFile());
        return file;
    }

    List<SppService> services = new ArrayList<>();
    List<SppService> scenarios = new ArrayList<>();
    private Map<String, List<RolePerformerMetadataField>> rolePerformerMetadataFieldsByRole = Map.of();
    private Set<SppService> appScopedDeploymentServices = Set.of();
    private boolean appScopeDeploymentMode;
    private java.util.Map<String, com.servpp.lang.models.app.ExampleFault> exampleFaults = java.util.Map.of();
    private List<String> exampleServiceNames = List.of();
    private List<String> webServiceNames = List.of(), webStartupNames = List.of();
    private java.util.Map<String, com.servpp.lang.models.app.ExampleBarrier> exampleBarriers = java.util.Map.of();
    private java.util.Map<String, com.servpp.lang.models.app.ExampleRestart> exampleRestarts = java.util.Map.of();
    private java.util.Map<String, com.servpp.lang.models.app.ExampleJdbcFault> exampleJdbcFaults = java.util.Map.of();

    private final com.servpp.generator.spi.ModelGenerationExtension trackingExtension =
            com.servpp.generator.spi.ModelGenerationExtension.find("propertyTracking");
    private boolean propertyTracking;

    public OpenAPI generate(SppDomain sppDomain, File domainPath, String basePackage)
            throws IOException, URISyntaxException {
        String domainName = sppDomain.getName();
        propertyTracking = sppDomain.getRuleBlock().isPropertyTracking() && trackingExtension != null;
        roleStoragePlan = sppDomain.getRuleBlock().hasTransactionPolicy();
        exampleFaults = java.util.Map.copyOf(sppDomain.getRuleBlock().getExampleFaults());
        exampleServiceNames = List.copyOf(sppDomain.getRuleBlock().getExampleServiceNames());
        webServiceNames = List.copyOf(sppDomain.getRuleBlock().getWebServiceNames());
        webStartupNames = List.copyOf(sppDomain.getRuleBlock().getWebStartupNames());
        exampleBarriers = java.util.Map.copyOf(sppDomain.getRuleBlock().getExampleBarriers());
        exampleRestarts = java.util.Map.copyOf(sppDomain.getRuleBlock().getExampleRestarts());
        exampleJdbcFaults = java.util.Map.copyOf(sppDomain.getRuleBlock().getExampleJdbcFaults());
//        dealMaps(sppDomain);
        OpenAPI openAPI = new OpenAPI();
        openAPI.setInfo(new Info().description("S++ API").version("1.0.0").title("OpenAPI "+domainName));
        Map<String, SppCompilationUnit> sppClassMap = sppDomain.getMapsOfClass();
        for (var tables : sppDomain.getRuleBlock().getAppTables()) for (var table : tables.getAppTableList()) {
            if (table.getEntity() != null) {
                var direct = table.getPrimaryKeys().stream().filter(key -> key.getSppField() != null)
                        .map(key -> key.getObjectPath()).filter(path -> !path.contains(".") && table.getEntity().getField(path) != null
                            && table.getEntity().getField(path).getType().getType() == null).toList();
                if (!direct.isEmpty()) {
                    table.getEntity().setIdentityPaths(direct);
                    // DDL instantiates a storage copy; expressions retain the original business type.
                    if (sppClassMap.get(table.getEntity().getName()) instanceof SppClass businessClass)
                        businessClass.setIdentityPaths(direct);
                }
            }
        }

        rolePerformerMetadataFieldsByRole = rolePerformerMetadataFieldsByRole(sppDomain);
        appScopedDeploymentServices = appScopedDeploymentServices(sppDomain);
        appScopeDeploymentMode = !sppDomain.getRuleBlock().getAppScope().getScopelist().isEmpty();
        openAPI.addTagsItem(new Tag().name(domainName).description(""));
        setAdditionalPackage(basePackage, domainName, sppClassMap);
        genAppHeaders(sppDomain, openAPI);
        genSppClasses(domainPath, basePackage, sppClassMap, openAPI, domainName);
        services.addAll(scenarios);

        return openAPI;
    }

    private void genAppHeaders(SppDomain sppDomain, OpenAPI openAPI) {
        RuleBlock ruleBlock = sppDomain.getRuleBlock();
        for (AppHeader appHeader : ruleBlock.getHeaders()) {
            createASchema(appHeader, openAPI);
        }
    }

    private void genSppClasses(File domainPath, String basePackage, Map<String, SppCompilationUnit> sppClassMap,
                               OpenAPI openAPI, String domainName) throws IOException, URISyntaxException {
        var roleBindings = sppClassMap.values().stream().filter(unit -> unit instanceof SppRoleMapper).map(unit -> (SppRoleMapper)unit).toList();
        for (SppCompilationUnit sppClass : sppClassMap.values()) {
            if (sppClass instanceof SppService service) {
                service.setRoleBindings(roleBindings);
                service.setDomainQualifiedDeployment(catalogNamespace != null);
            }
//            if(sppClass.getType() == IConstance.CompilationUnitType.scenario)
//                System.out.println(sppClass);
            IConstance.CompilationUnitType type = sppClass.getType();
            if (type == IConstance.CompilationUnitType.entity
                    || type == IConstance.CompilationUnitType.role
                    || type == IConstance.CompilationUnitType.contract
                    || type == IConstance.CompilationUnitType.reference) {
                createASchema((SppClass) sppClass, openAPI);
            } else if (type == IConstance.CompilationUnitType.atomicservice) {
                SppService sppService = (SppService) sppClass;
                boolean deploymentService = isDeploymentService(sppService);
                if (deploymentService && sppService.isLocalService()) {
                    createAPath(domainName, (SppClass) sppClass, openAPI);
                }
                if (deploymentService) {
                    services.add((SppService) sppClass);
                }
            } else if (type == IConstance.CompilationUnitType.scenario) {
                SppService sppService = (SppService) sppClass;
                boolean deploymentService = isDeploymentService(sppService);
                if (deploymentService && sppService.isLocalService()) {
                    createAPath(domainName, (SppClass) sppClass, openAPI);
                }
                if (deploymentService) {
                    scenarios.add(sppService);
                }
            }
//            new OpenAPIClassWriter(domainPath,sppClass, basePackage, domainName).generate();
            if(sppClass instanceof SppClass)
                new MustacheClassWriter(domainPath, (SppClass) sppClass, basePackage, domainName,
                        !(sppClass instanceof SppService service) || isDeploymentService(service)).generate();
            else if(sppClass instanceof SppEnum){
                createAEnum((SppEnum) sppClass,openAPI);
            }
        }
    }

    private boolean isDeploymentService(SppService service) {
        return !appScopeDeploymentMode || appScopedDeploymentServices.contains(service);
    }

    private Set<SppService> appScopedDeploymentServices(SppDomain sppDomain) {
        Set<SppService> result = new LinkedHashSet<>();
        sppDomain.getRuleBlock().getAppScope().getScopelist().forEach(scopeDefine ->
                scopeDefine.getScopeItems().forEach(scopeItem -> {
                    if (scopeItem.getService() != null) {
                        result.add(scopeItem.getService());
                    }
                }));
        return result;
    }

    public void createJavaSpringMustache(File yamlOutPath) throws IOException {
        try (InputStream template = org.openapitools.codegen.DefaultGenerator.class.getResourceAsStream("/JavaSpring/pojo.mustache")) {
            if (template == null) throw new IOException("Missing pinned OpenAPI JavaSpring pojo template");
            String pojo = new String(template.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (trackingExtension != null) pojo = trackingExtension.decorateJavaSpringTemplate(pojo);
            java.nio.file.Path templates = yamlOutPath.toPath().resolve("JavaSpring");
            java.nio.file.Files.createDirectories(templates);
            java.nio.file.Files.writeString(templates.resolve("pojo.mustache"),pojo);
        }
        File dir = new File(yamlOutPath, "JavaSpring");
        ensureDirectory(dir);
        InputStream in = OpenApiGenerator.class.getResourceAsStream("/JavaSpring/methodBody.mustache");
        if (in == null) {
            throw new FileNotFoundException("Cannot find /JavaSpring/methodBody.mustache resource.");
        }
        File mustache = new File(dir, "methodBody.mustache");

        try (PrintWriter out = new PrintWriter(new FileOutputStream(mustache));
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
        }
    }

    public void createServicesProperties(File genRoot) throws IOException {
        File resources = new File(genRoot.getParent(), "resources");
        ensureDirectory(resources);
        File servicesFile = catalogFile(resources, "services.properties");
        ensureDirectory(servicesFile.getParentFile());

        try (PrintWriter out = new PrintWriter(new FileOutputStream(servicesFile))) {
            for (SppService service : services) {
                out.println(service.getDeploymentServiceName() + "="
                        + service.getBasePackage() + "."
                        + service.getDomainName() + ".service.Simple"
                        + service.getName());
            }
        }
        java.util.Properties web = new java.util.Properties();
        for (String kind : List.of("expose", "start")) {
            var names = kind.equals("expose") ? webServiceNames : webStartupNames;
            int n = 0;
            for (String name : names) {
                SppService service = services.stream().filter(candidate -> name.equals(candidate.getFuncName())).findFirst().orElseThrow();
                String prefix = kind + "." + n++ + ".";
                web.setProperty(prefix + "service", service.getDeploymentServiceName());
                web.setProperty(prefix + "request", service.getBasePackage() + "." + service.getDomainName() + ".model." + service.getName() + service.getRequestSuffix());
                web.setProperty(prefix + "participants", service.getServiceBody().getSppLocalVarList().stream().map(SppLocalVar::getName).collect(java.util.stream.Collectors.joining(",")));
            }
            web.setProperty(kind + ".count", Integer.toString(n));
        }
        try (var out = new FileOutputStream(catalogFile(resources, "web.properties"))) { web.store(out, "Generated APP web deployment"); }
        java.util.Properties examples = new java.util.Properties();
        int index = 0;
        for (String exampleName : exampleServiceNames) {
            SppService service = services.stream().filter(candidate -> exampleName.equals(candidate.getFuncName()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("APP example service was not generated: " + exampleName));
            String prefix = "example." + index++ + ".";
            examples.setProperty(prefix + "service", service.getFuncName());
            examples.setProperty(prefix + "request", service.getBasePackage() + "." + service.getDomainName()
                    + ".model." + service.getName() + service.getRequestSuffix());
            var fault = exampleFaults.get(exampleName);
            var barrier = exampleBarriers.get(exampleName);
            var restart = exampleRestarts.get(exampleName);
            var jdbcFault = exampleJdbcFaults.get(exampleName);
            if (jdbcFault != null) {
                examples.setProperty(prefix + "jdbcFault.service", jdbcFault.serviceName());
                examples.setProperty(prefix + "jdbcFault.point", jdbcFault.point());
            }
            if (restart != null) {
                SppService next = services.stream().filter(candidate -> restart.serviceName().equals(candidate.getFuncName()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("APP restart service was not generated: " + restart.serviceName()));
                examples.setProperty(prefix + "restart.service", next.getFuncName());
                examples.setProperty(prefix + "restart.request", next.getBasePackage() + "." + next.getDomainName() + ".model." + next.getName() + "Request");
                examples.setProperty(prefix + "restart.participants", next.getServiceBody().getSppLocalVarList().stream()
                    .map(value -> value.getName()).collect(java.util.stream.Collectors.joining(",")));
                examples.setProperty(prefix + "restart.datasourceProperty", restart.datasourceProperty());
            }
            if (barrier != null) {
                examples.setProperty(prefix + "barrier.service", barrier.serviceName());
                examples.setProperty(prefix + "barrier.parties", Integer.toString(barrier.parties()));
            }
            if (fault != null) {
                examples.setProperty(prefix + "fault.service", fault.serviceName());
                examples.setProperty(prefix + "fault.timing", fault.timing());
                examples.setProperty(prefix + "fault.times", Integer.toString(fault.times()));
                if (fault.afterServiceName() != null) {
                    examples.setProperty(prefix + "fault.afterService", fault.afterServiceName());
                }
            }
            examples.setProperty(prefix + "participants", service.getServiceBody().getSppLocalVarList().stream()
                    .map(SppLocalVar::getName).collect(java.util.stream.Collectors.joining(",")));
        }
        examples.setProperty("example.count", Integer.toString(index));
        try (var out = new FileOutputStream(catalogFile(resources, "examples.properties"))) {
            examples.store(out, "Generated APP example entrypoints");
        }
    }

    public void createConstantProperties(SppDomain domain, File genRoot) throws IOException {
        var properties = new java.util.Properties();
        properties.setProperty("domain", domain.getName());
        var rules = domain.getRuleBlock();
        if (rules.getDefaultLocale() != null) {
            properties.setProperty("defaultLocale", rules.getDefaultLocale());
            properties.setProperty("supportedLocales", String.join(",", rules.getSupportedLocales()));
        }
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (var locale : rules.getConstantTranslations().entrySet()) for (var translation : locale.getValue().entrySet())
            properties.setProperty("locale." + locale.getKey() + "." + translation.getKey(), json.readValue(translation.getValue(), String.class));
        try (var output = new FileOutputStream(catalogFile(new File(genRoot.getParent(), "resources"), "constants.properties"))) {
            properties.store(output, "Generated APP/PHY constant localization for " + domain.getName());
        }
    }

    public void createPhysicalRuntimeProperties(SppDomain sppDomain, File genRoot) throws IOException {
        createPhysicalRuntimeProperties(sppDomain, genRoot, null, List.of());
    }

    public void createPhysicalRuntimeProperties(SppDomain sppDomain, File genRoot,
                                                String physicalProfile,
                                                List<String> physicalSourceFileNames) throws IOException {
        List<String> validationErrors = new ArrayList<>();
        validationErrors.addAll(sppDomain.validatePhysicalRuntimeDeclarations());
        validationErrors.addAll(sppDomain.validatePhysicalRuntimeCatalogGeneration());
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException("S++ physical runtime catalog validation failed:\n"
                    + String.join("\n", validationErrors));
        }
        File resources = new File(genRoot.getParent(), "resources");
        ensureDirectory(resources);
        // All APP-declared technical services, including explicit request/response adapters.
        // Interception remains a separate, read-only protocol.
        java.util.Properties technical = new java.util.Properties();
        var technicalModel = sppDomain.getRuleBlock().getTechnicalServices();
        technical.setProperty("services", String.join(",", technicalModel.names()));
        for (String name : technicalModel.names()) {
            var implementation = technicalModel.implementations().get(name);
            technical.setProperty("service." + name + ".implementation", implementation.className());
            implementation.configuration().forEach((key, value) -> technical.setProperty("service." + name + ".config." + key, value));
        }
        File technicalFile = catalogFile(resources, "technical-services.properties");
        ensureDirectory(technicalFile.getParentFile());
        try (var output = new FileOutputStream(technicalFile)) { technical.store(output, "APP declarations with PHY technical implementations"); }
        File physicalFile = catalogFile(resources, "physical.properties");
        ensureDirectory(physicalFile.getParentFile());

        try (PrintWriter out = new PrintWriter(new FileOutputStream(physicalFile))) {
            String normalizedProfile = physicalProfile == null ? "" : physicalProfile.trim();
            List<String> sourceFiles = physicalSourceFileNames == null ? List.of() : List.copyOf(physicalSourceFileNames);
            for (var entry : sppDomain.getRuleBlock().getRequestPolicies().entrySet())
                out.println("config." + entry.getKey() + "=" + entry.getValue());
            var deployment = sppDomain.getRuleBlock();
            if (deployment.getDeploymentEnvironment() != null) {
                out.println("deployment.environment=" + deployment.getDeploymentEnvironment());
                out.println("deployment.transactionMode=" + deployment.getDeploymentTransactionMode());
                if (deployment.getDeploymentQuantumSystem() != null) out.println("deployment.quantumSystem=" + deployment.getDeploymentQuantumSystem());
                if (deployment.getDeploymentQuantumResource() != null) out.println("deployment.quantumResource=" + deployment.getDeploymentQuantumResource());
            }
            out.println("domainRoutes=" + String.join(",", deployment.getDomainRoutes().keySet()));
            for (var route : deployment.getDomainRoutes().values()) {
                out.println("domainRoute." + route.domain() + ".endpoint=" + escapePhysicalPropertyValue(route.endpoint()));
                out.println("domainRoute." + route.domain() + ".timeoutMillis=" + route.timeoutMillis());
            }
            out.println("physicalProfile=" + escapePhysicalPropertyValue(normalizedProfile));
            out.println("physicalProfileExplicit=" + (!normalizedProfile.isBlank()));
            out.println("physicalSourceFiles=" + String.join(",", sourceFiles));
            for (Map.Entry<String, String> entry : sppDomain.getRuleBlock().getPhysicalConfiguration().entrySet()) {
                out.println("config." + entry.getKey() + "=" + escapePhysicalPropertyValue(entry.getValue()));
            }
            for (var binding : sppDomain.getRuleBlock().getNumericBindings()) {
                out.println("numeric." + binding.target() + "=" + binding.javaType());
                if (binding.adapter() != null) { out.println("numericAdapter." + binding.target() + "=" + binding.adapter()); }
            }
            List<AppPhysicalBackend> backends = sppDomain.getRuleBlock().getAppPhysicalBackends();
            boolean storageRequired = !backends.isEmpty()
                    || deployment.getAppTables().stream().anyMatch(group -> !group.getAppTableList().isEmpty())
                    || !deployment.getAppQuantumStoreAdapters().isEmpty()
                    || sppDomain.getMapsOfClass().values().stream().anyMatch(unit -> unit instanceof com.servpp.lang.models.SppService service
                        && service.getServiceType() == com.servpp.lang.general.IConstance.ServiceType.update
                        && !service.hasLocalServiceBinding());
            out.println("domain." + sppDomain.getName() + ".requiresLocalStorage=" + storageRequired);
            out.println("backends=" + joinBackendNames(backends));
            for (AppPhysicalBackend backend : backends) {
                if (backend.getAdapterName() != null && !backend.getAdapterName().isBlank()) {
                    out.println("backend." + backend.getName() + ".adapter=" + backend.getAdapterName());
                }
                out.println("backend." + backend.getName() + ".bindings=" + joinPhysicalBindings(backend));
                String quantumStoreName = backendQuantumStoreName(backend);
                if (quantumStoreName != null && !quantumStoreName.isBlank()) {
                    out.println("backend." + backend.getName() + ".quantumStore="
                            + escapePhysicalPropertyValue(quantumStoreName));
                }
                for (Map.Entry<String, String> entry : backend.getConfiguration().entrySet()) {
                    if ("quantumStore".equals(entry.getKey())) {
                        continue;
                    }
                    out.println("backend." + backend.getName() + ".config." + entry.getKey()
                            + "=" + escapePhysicalPropertyValue(entry.getValue()));
                }
            }
            List<AppRemoteAdapter> remoteAdapters = sppDomain.getRuleBlock().getAppRemoteAdapters();
            out.println("remoteServices=" + joinRemoteServiceNames(remoteAdapters));
            for (AppRemoteAdapter adapter : remoteAdapters) {
                out.println("remote." + adapter.getName() + ".adapter=" + adapter.getAdapterName());
                if ("httpJson".equals(adapter.getAdapterName())) {
                    boolean updateEntry = sppDomain.getMapsOfClass().values().stream()
                            .filter(unit -> unit instanceof SppService).map(unit -> (SppService)unit)
                            .anyMatch(service -> (adapter.getName().equals(service.getFuncName())
                                    || adapter.getName().equals(service.getDeploymentServiceName())) && service.isUpdateEntry());
                    out.println("remote." + adapter.getName() + ".config.requestUpdateEntry=" + updateEntry);
                }
                for (Map.Entry<String, String> entry : adapter.getConfiguration().entrySet()) {
                    out.println("remote." + adapter.getName() + ".config." + entry.getKey()
                            + "=" + escapePhysicalPropertyValue(entry.getValue()));
                }
            }
            List<AppRepositoryAdapter> repositoryAdapters = sppDomain.getRuleBlock().getAppRepositoryAdapters();
            out.println("repositories=" + joinRepositoryNames(repositoryAdapters));
            for (AppRepositoryAdapter adapter : repositoryAdapters) {
                out.println("repository." + adapter.getName() + ".adapter=" + adapter.getAdapterName());
                for (Map.Entry<String, String> entry : adapter.getConfiguration().entrySet()) {
                    out.println("repository." + adapter.getName() + ".config." + entry.getKey()
                            + "=" + escapePhysicalPropertyValue(entry.getValue()));
                }
            }
            List<AppQuantumStore> quantumStores = sppDomain.getRuleBlock().getAppQuantumStores();
            out.println("quantumStores=" + joinQuantumStoreNames(quantumStores));
            for (AppQuantumStore store : quantumStores) {
                out.println("quantumStore." + store.getName() + ".attributes="
                        + String.join(",", store.getAttributeIds()));
                AppQuantumStoreAdapter adapter = findQuantumStoreAdapter(
                        sppDomain.getRuleBlock().getAppQuantumStoreAdapters(), store.getName());
                if (adapter != null) {
                    out.println("quantumStore." + store.getName() + ".adapter=" + adapter.getAdapterName());
                    for (Map.Entry<String, String> entry : adapter.getConfiguration().entrySet()) {
                        out.println("quantumStore." + store.getName() + ".config." + entry.getKey()
                                + "=" + escapePhysicalPropertyValue(entry.getValue()));
                    }
                    List<AppQuantumDefinition> definitions = adapter.getQuantumDefinitions();
                    out.println("quantumStore." + store.getName() + ".definitionCount=" + definitions.size());
                    for (int i = 0; i < definitions.size(); i++) {
                        AppQuantumDefinition definition = definitions.get(i);
                        String prefix = "quantumStore." + store.getName() + ".definition." + i + ".";
                        out.println(prefix + "attributeId="
                                + escapePhysicalPropertyValue(definition.getAttributeId()));
                        out.println(prefix + "numberType=" + definition.getNumberType());
                        out.println(prefix + "minValue="
                                + escapePhysicalPropertyValue(definition.getMinValue()));
                        out.println(prefix + "maxValue="
                                + escapePhysicalPropertyValue(definition.getMaxValue()));
                        out.println(prefix + "scale=" + definition.getScale());
                    }
                }
            }
        }
    }

    public void createAppDdlProperties(DDLGenerator ddlGenerator, File genRoot) throws IOException {
        File resources = new File(genRoot.getParent(), "resources");
        ensureDirectory(resources);
        File ddlFile = catalogFile(resources, "app-ddl.properties");
        ensureDirectory(ddlFile.getParentFile());

        try (PrintWriter out = new PrintWriter(new FileOutputStream(ddlFile))) {
            List<DbTable> tables = ddlGenerator.getTableList().getArrayList();
            out.println("propertyTracking=" + (ddlGenerator.getSppDomain().getRuleBlock().isPropertyTracking() && trackingExtension != null));
            out.println("optimisticConcurrency=" + ddlGenerator.getSppDomain().getRuleBlock().isSystemServiceEnabled("optimisticConcurrency"));
            out.println("tables=" + joinDbTableNames(tables));
            for (DbTable table : tables) {
                out.println("table." + table.getName() + ".category=" + table.getCategoryName());
                out.println("table." + table.getName() + ".businessType="
                        + table.getAppTable().getEntity().getName());
                var roleTable = table.getAppTable();
                if (roleTable.isIndependentIdentity() || roleTable.getPerformerTable() != null) {
                    String prefix = "table." + table.getName() + ".roleStorage.";
                    out.println(prefix + "enabled=true");
                    out.println(prefix + "independent=" + roleTable.isIndependentIdentity());
                    out.println(prefix + "performerTable=" + (roleTable.getPerformerTable() == null ? "" : roleTable.getPerformerTable()));
                    var domain = ddlGenerator.getSppDomain();
                    var performers = domain.roleStoragePerformers(roleTable.getEntity());
                    out.println(prefix + "performers=" + performers.stream().map(SppDomain.RoleStoragePerformer::slot).collect(java.util.stream.Collectors.joining(",")));
                    var branches = domain.getMapsOfClass().values().stream()
                            .filter(unit -> unit instanceof SppRoleMapper mapping && mapping.getRole() != null && mapping.getRole().getName().equals(roleTable.getEntity().getName()))
                            .map(unit -> (SppRoleMapper)unit).toList();
                    var variants = new java.util.LinkedHashSet<String>();
                    for (var branch : branches) variants.add(performers.stream()
                            .filter(actor -> !actor.member().isBlank() || branch.getEntities().stream().anyMatch(type -> type.getName().equals(actor.type().getName())))
                            .map(SppDomain.RoleStoragePerformer::slot).collect(java.util.stream.Collectors.joining(",")));
                    if (branches.isEmpty() && !performers.isEmpty()) variants.add(performers.stream()
                            .map(SppDomain.RoleStoragePerformer::slot).collect(java.util.stream.Collectors.joining(",")));
                    out.println(prefix + "variants=" + String.join(";",variants));
                    for (var performer : performers) {
                        String actor = prefix + "performer." + performer.slot() + ".";
                        out.println(actor + "type=" + performer.type().getName());
                        out.println(actor + "javaType=" + performer.type().getPackageName() + "." + performer.type().getName());
                        var actorTable = domain.getRuleBlock().getAppTables().stream().flatMap(group -> group.getAppTableList().stream())
                                .filter(mapping -> mapping.getEntity() != null && mapping.getEntity().getName().equals(performer.type().getName())).findFirst();
                        var keys = actorTable.isPresent() && actorTable.get().isIndependentIdentity() ? List.of("@storageIdentity")
                                : com.servpp.lang.models.ParticipantRoleBindings.primaryKeys(domain,performer.type());
                        if (keys.isEmpty()) keys = performer.type().getIdentityPaths();
                        out.println(actor + "keys=" + String.join(",",keys));
                        out.println(actor + "member=" + performer.member());
                    }
                }
                if (roleStoragePlan) {
                    var queries = services.stream().filter(service -> service.getServiceType() == IConstance.ServiceType.query
                            && service.getServiceBody().getSppLocalVarList().size() == 1
                            && service.getServiceBody().getLocalVar(0).getArrayDimension() == 0
                            && service.getServiceBody().getLocalVar(0).getType().getName().equals(table.getAppTable().getEntity().getName())).toList();
                    var defaults = queries.stream().filter(service -> !service.hasLocalServiceBinding()
                            && service.getScopeItem() != null && (service.getScopeItem().isImplicitDefaultBinding() || service.isImplicitParticipantCrud()))
                            .sorted(java.util.Comparator.comparing(SppService::getFuncName)).toList();
                    var query = !defaults.isEmpty() ? defaults.get(0) : queries.size() == 1 ? queries.get(0) : null;
                    if (query != null) out.println("table." + table.getName() + ".queryService=" + query.getDomainName() + "." + query.getFuncName());
                }
                out.println("table." + table.getName() + ".excludedFacts=" + table.getAppTable().getEntity().getSppFieldList().stream()
                        .filter(field -> !field.isStoredHere()).map(com.servpp.lang.models.SppField::getName)
                        .collect(java.util.stream.Collectors.joining(",")));
                out.println("table." + table.getName() + ".snapshotFacts=" + table.getAppTable().getEntity().getSppFieldList().stream()
                        .filter(com.servpp.lang.models.SppField::isSnapshotStorageFact)
                        .map(com.servpp.lang.models.SppField::getName).collect(java.util.stream.Collectors.joining(",")));
                out.println("table." + table.getName() + ".columns=" + joinDbColumnNames(table.getColumns().getArrayList()));
                out.println("table." + table.getName() + ".primaryKeys="
                        + joinDbColumnNames(table.getPrimaryKeys() == null
                        ? List.of()
                        : table.getPrimaryKeys().getArrayList()));
                out.println("table." + table.getName() + ".indexes="
                        + joinUnique(table.getAppTable().getIndexes()));
                out.println("table." + table.getName() + ".indexNames="
                        + joinAppIndexNames(table.getAppTable().getAppIndexes()));
                out.println("table." + table.getName() + ".businessCredentialColumns="
                        + String.join(",", appTableBusinessCredentialFields(table.getAppTable())));
                for (AppIndex index : table.getAppTable().getAppIndexes()) {
                    if (index.getName() == null) {
                        continue;
                    }
                    out.println("table." + table.getName() + ".index." + index.getName() + ".unique=" + index.isUnique());
                    out.println("table." + table.getName() + ".index." + index.getName() + ".paths="
                            + String.join(",", index.getPaths()));
                }
                for (DbColumn column : table.getColumns().getArrayList()) {
                    out.println("table." + table.getName() + ".column." + column.getName()
                            + ".type=" + column.getJdbcType().getName());
                    out.println("table." + table.getName() + ".column." + column.getName()
                            + ".precision=" + column.getPrecision());
                    out.println("table." + table.getName() + ".column." + column.getName()
                            + ".scale=" + column.getScale());
                    out.println("table." + table.getName() + ".column." + column.getName()
                            + ".nullable=" + column.isNullable());
                    out.println("table." + table.getName() + ".column." + column.getName()
                            + ".objectBacked=" + column.isObjectBacked());
                }
            }
        }
    }

    private List<String> appTableBusinessCredentialFields(AppTable appTable) {
        Set<String> fields = new LinkedHashSet<>();
        if (appTable == null || appTable.getAnnotations() == null) {
            return List.of();
        }
        for (AnnotationDefine annotation : appTable.getAnnotations()) {
            if (annotation == null || !"businessCredential".equals(annotation.getName())) {
                continue;
            }
            String value = annotation.getParameters().get("fields");
            if (value == null || value.isBlank()) {
                value = annotation.getParameters().get("field");
            }
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String field : value.split(",", -1)) {
                String trimmed = field.trim();
                if (!trimmed.isBlank()) {
                    fields.add(trimmed);
                }
            }
        }
        return List.copyOf(fields);
    }

    public void createAppSemanticMigrationProperties(SppDomain sppDomain, File genRoot) throws IOException {
        File resources = new File(genRoot.getParent(), "resources");
        ensureDirectory(resources);
        File migrationFile = catalogFile(resources, "app-semantic-migration.properties");
        ensureDirectory(migrationFile.getParentFile());

        try (PrintWriter out = new PrintWriter(new FileOutputStream(migrationFile))) {
            List<String> warnings = sppDomain.appSemanticMigrationWarnings();
            out.println("warning.count=" + warnings.size());
            for (int index = 0; index < warnings.size(); index++) {
                out.println("warning." + index + "=" + escapePhysicalPropertyValue(warnings.get(index)));
            }
            int repositoryQueryPlanCount = sppDomain.appSemanticRepositoryQueryPlanCount();
            int repositoryReplacementReadyCount = sppDomain.appSemanticRepositoryReplacementReadyCount();
            out.println("summary.repositoryQueryPlanCount=" + repositoryQueryPlanCount);
            out.println("summary.repositoryReplacementReadyCount=" + repositoryReplacementReadyCount);
            out.println("summary.missingRepositoryReplacementReadyCount="
                    + (repositoryQueryPlanCount - repositoryReplacementReadyCount));
            out.println("summary.repositoryReplacementSources=" + escapePhysicalPropertyValue(
                    joinStrings(sppDomain.appSemanticRepositoryReplacementSources())));
            out.println("summary.repositoryReplacementReadyServiceNames=" + escapePhysicalPropertyValue(
                    joinStrings(sppDomain.appSemanticRepositoryReplacementReadyServiceNames())));
            out.println("summary.missingRepositoryReplacementReadyServiceNames=" + escapePhysicalPropertyValue(
                    joinStrings(sppDomain.appSemanticMissingRepositoryReplacementReadyServiceNames())));
            List<AppSemanticMigrationPlan> plans = sppDomain.appSemanticMigrationPlans();
            out.println("plan.count=" + plans.size());
            for (int index = 0; index < plans.size(); index++) {
                writeAppSemanticMigrationPlan(out, index, plans.get(index));
            }
        }
    }

    public void createRoleParticipantProperties(SppDomain sppDomain, File genRoot) throws IOException {
        File resources = new File(genRoot.getParent(), "resources");
        ensureDirectory(resources);
        File roleParticipantFile = catalogFile(resources, "role-participants.properties");
        ensureDirectory(roleParticipantFile.getParentFile());

        try (PrintWriter out = new PrintWriter(new FileOutputStream(roleParticipantFile))) {
            List<GeneratedRoleParticipantPlan> plans = roleParticipantPlans(sppDomain);
            out.println("roleParticipant.count=" + plans.size());
            for (int index = 0; index < plans.size(); index++) {
                writeRoleParticipantPlan(out, index, plans.get(index));
            }
        }
    }

    private void writeRoleParticipantPlan(PrintWriter out, int index, GeneratedRoleParticipantPlan plan) {
        String prefix = "roleParticipant." + index + ".";
        out.println(prefix + "roleType=" + escapePhysicalPropertyValue(plan.roleType()));
        out.println(prefix + "roleTableName=" + escapePhysicalPropertyValue(plan.roleTableName()));
        out.println(prefix + "mapperName=" + escapePhysicalPropertyValue(plan.mapperName()));
        out.println(prefix + "performerTypes=" + escapePhysicalPropertyValue(String.join(",", plan.performerTypes())));
        out.println(prefix + "performerCount=" + plan.performerCount());
        out.println(prefix + "jointPerformer=" + plan.jointPerformer());
        out.println(prefix + "participationKind=" + escapePhysicalPropertyValue(plan.participationKind()));
        out.println(prefix + "performerKeyPaths=" + escapePhysicalPropertyValue(
                String.join(",", plan.performerKeyPaths())));
        out.println(prefix + "roleKeyPaths=" + escapePhysicalPropertyValue(String.join(",", plan.roleKeyPaths())));
        out.println(prefix + "roleSourceKeyPaths=" + escapePhysicalPropertyValue(
                String.join(",", plan.roleSourceKeyPaths())));
        out.println(prefix + "compatRoleKeyPaths=" + escapePhysicalPropertyValue(
                String.join(",", plan.compatRoleKeyPaths())));
        out.println(prefix + "platformColumns=" + escapePhysicalPropertyValue(
                String.join(",", plan.platformColumns())));
        out.println(prefix + "performerKeyComplete=" + plan.performerKeyComplete());
        out.println(prefix + "roleKeyComplete=" + plan.roleKeyComplete());
        out.println(prefix + "complete=" + plan.complete());
        out.println(prefix + "binding=" + escapePhysicalPropertyValue(plan.binding()));
    }

    private List<GeneratedRoleParticipantPlan> roleParticipantPlans(SppDomain sppDomain) {
        Map<String, AppTable> tableByBusinessType = appTableByBusinessType(sppDomain);
        List<GeneratedRoleParticipantPlan> plans = new ArrayList<>();
        for (SppCompilationUnit unit : sppDomain.getMapsOfClass().values()) {
            if (!(unit instanceof SppRoleMapper mapper) || mapper.getRole() == null
                    || mapper.getEntities() == null || mapper.getEntities().isEmpty()) {
                continue;
            }
            AppTable roleTable = tableByBusinessType.get(mapper.getRole().getName());
            List<String> performerTypes = new ArrayList<>();
            List<String> performerKeyPaths = new ArrayList<>();
            List<String> roleKeyPaths = new ArrayList<>();
            List<String> roleSourceKeyPaths = new ArrayList<>();
            List<String> platformColumns = new ArrayList<>();
            boolean complete = roleTable != null;
            boolean roleKeyComplete = roleTable != null;
            for (SppClass performer : mapper.getEntities()) {
                if (performer == null || performer.getName() == null) {
                    complete = false;
                    roleKeyComplete = false;
                    continue;
                }
                performerTypes.add(performer.getName());
                AppTable performerTable = tableByBusinessType.get(performer.getName());
                List<String> performerPrimaryKeys = performerIdentityKeys(performer, performerTable);
                if (performerPrimaryKeys.isEmpty()) {
                    complete = false;
                    roleKeyComplete = false;
                    continue;
                }
                for (String performerPrimaryKey : performerPrimaryKeys) {
                    String platformColumn = roleParticipantPlatformColumn(roleTable, performer, performerPrimaryKey);
                    if (platformColumn == null) {
                        complete = false;
                        roleKeyComplete = false;
                        continue;
                    }
                    performerKeyPaths.add(performer.getName() + "." + performerPrimaryKey);
                    String roleKeyPath = lowerFirst(performer.getName()) + "." + performerPrimaryKey;
                    roleKeyPaths.add(roleKeyPath);
                    if (fieldPathExists(mapper.getRole(), roleKeyPath)) {
                        roleSourceKeyPaths.add(roleKeyPath);
                    }
                    platformColumns.add(platformColumn);
                }
            }
            plans.add(new GeneratedRoleParticipantPlan(mapper.getRole().getName(),
                    roleTable == null ? null : roleTable.getName(),
                    mapper.getName(),
                    performerTypes, performerTypes.size(), performerTypes.size() > 1,
                    performerTypes.size() > 1 ? "JOINT_PERFORMER" : "SINGLE_PERFORMER",
                    performerKeyPaths, roleSourceKeyPaths, roleSourceKeyPaths,
                    List.copyOf(roleKeyPaths), platformColumns,
                    complete, roleKeyComplete, complete,
                    roleParticipantBinding(mapper.getRole().getName(), performerTypes, performerKeyPaths,
                            roleSourceKeyPaths, roleKeyPaths, platformColumns, complete, roleKeyComplete)));
        }
        return List.copyOf(plans);
    }

    private boolean fieldPathExists(SppClass root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return false;
        }
        SppClass current = root;
        for (String segment : path.split("\\.")) {
            SppField field = current.getField(segment);
            if (field == null || field.isSyntheticRolePerformer()) {
                return false;
            }
            if (field.getType() instanceof SppClass nested) {
                current = nested;
            } else if (!segment.equals(path.substring(path.lastIndexOf('.') + 1))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, AppTable> appTableByBusinessType(SppDomain sppDomain) {
        Map<String, AppTable> tables = new java.util.LinkedHashMap<>();
        if (sppDomain.getRuleBlock() == null || sppDomain.getRuleBlock().getAppTables() == null) {
            return tables;
        }
        for (AppTables appTables : sppDomain.getRuleBlock().getAppTables()) {
            for (AppTable table : appTables.getAppTableList()) {
                if (table.getEntity() != null && table.getEntity().getName() != null) {
                    tables.put(table.getEntity().getName(), table);
                }
            }
        }
        return tables;
    }

    private String firstPlatformPrimaryKey(AppTable appTable) {
        if (appTable == null) {
            return null;
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (column.isPrimaryKey() && !column.isObjectBacked()
                    && column.getName() != null && !column.getName().isBlank()) {
                return column.getName();
            }
        }
        return null;
    }

    private List<String> performerIdentityKeys(SppClass performer, AppTable performerTable) {
        String platformPrimaryKey = firstPlatformPrimaryKey(performerTable);
        if (platformPrimaryKey != null) {
            return List.of(platformPrimaryKey);
        }
        if (performer == null || performer.getName() == null || performer.getName().isBlank()) {
            return List.of();
        }
        List<String> candidateNames = List.of(lowerFirst(performer.getName()) + "Id", "id");
        for (String candidateName : candidateNames) {
            SppField field = performer.getField(candidateName);
            if (field != null) {
                return List.of(candidateName);
            }
        }
        return performer.getSppFieldList().isEmpty()
                ? List.of()
                : List.of(performer.getSppFieldList().get(0).getName());
    }

    private String roleParticipantPlatformColumn(AppTable roleTable, SppClass performer, String performerPrimaryKey) {
        if (roleTable == null || performer == null || performerPrimaryKey == null) {
            return null;
        }
        String expected = normalizeRoleParticipantColumn(performer.getName() + "_" + performerPrimaryKey);
        for (AppColumn column : roleTable.getAppColumns()) {
            if (!column.isObjectBacked()
                    && column.getName() != null
                    && expected.equals(normalizeRoleParticipantColumn(column.getName()))) {
                return column.getName();
            }
        }
        return null;
    }

    private String normalizeRoleParticipantColumn(String value) {
        return value == null ? "" : value.replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }

    private String lowerFirst(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private String roleParticipantBinding(String roleType, List<String> performerTypes,
                                          List<String> performerKeyPaths, List<String> roleSourceKeyPaths,
                                          List<String> compatRoleKeyPaths, List<String> platformColumns,
                                          boolean complete, boolean roleKeyComplete) {
        return roleType
                + ":participation=" + (performerTypes.size() > 1 ? "JOINT_PERFORMER" : "SINGLE_PERFORMER")
                + ":performerCount=" + performerTypes.size()
                + ":joint=" + (performerTypes.size() > 1)
                + ":performers=" + String.join("+", performerTypes)
                + ":performerKeys=" + String.join("+", performerKeyPaths)
                + ":roleKeys=" + String.join("+", roleSourceKeyPaths)
                + ":compatRoleKeys=" + String.join("+", compatRoleKeyPaths)
                + ":columns=" + String.join("+", platformColumns)
                + ":performerKeyComplete=" + complete
                + ":roleKeyComplete=" + roleKeyComplete
                + ":complete=" + complete;
    }

    private record GeneratedRoleParticipantPlan(String roleType,
                                                String roleTableName,
                                                String mapperName,
                                                List<String> performerTypes,
                                                int performerCount,
                                                boolean jointPerformer,
                                                String participationKind,
                                                List<String> performerKeyPaths,
                                                List<String> roleKeyPaths,
                                                List<String> roleSourceKeyPaths,
                                                List<String> compatRoleKeyPaths,
                                                List<String> platformColumns,
                                                boolean performerKeyComplete,
                                                boolean roleKeyComplete,
                                                boolean complete,
                                                String binding) {
    }

    private void writeAppSemanticMigrationPlan(PrintWriter out, int index, AppSemanticMigrationPlan plan) {
        String prefix = "plan." + index + ".";
        out.println(prefix + "migrationType=" + escapePhysicalPropertyValue(plan.migrationType()));
        out.println(prefix + "targetKind=" + escapePhysicalPropertyValue(plan.targetKind()));
        out.println(prefix + "targetName=" + escapePhysicalPropertyValue(plan.targetName()));
        out.println(prefix + "tableName=" + escapePhysicalPropertyValue(plan.tableName()));
        out.println(prefix + "serviceName=" + escapePhysicalPropertyValue(plan.serviceName()));
        out.println(prefix + "objectType=" + escapePhysicalPropertyValue(plan.objectType()));
        out.println(prefix + "adapterName=" + escapePhysicalPropertyValue(plan.adapterName()));
        out.println(prefix + "adapterRole=" + escapePhysicalPropertyValue(plan.adapterRole()));
        out.println(prefix + "referenceImplementation=" + plan.referenceImplementation());
        out.println(prefix + "externalImplementation=" + plan.externalImplementation());
        out.println(prefix + "paths=" + escapePhysicalPropertyValue(String.join(",", plan.paths())));
        out.println(prefix + "participantCategories="
                + escapePhysicalPropertyValue(String.join(",", plan.participantCategories())));
        out.println(prefix + "snapshotBoundary=" + escapePhysicalPropertyValue(plan.snapshotBoundary()));
        out.println(prefix + "recoveryRole=" + escapePhysicalPropertyValue(plan.recoveryRole()));
        out.println(prefix + "objectKeySource=" + escapePhysicalPropertyValue(plan.objectKeySource()));
        out.println(prefix + "objectKeyAppDdlIndexName="
                + escapePhysicalPropertyValue(plan.objectKeyAppDdlIndexName()));
        out.println(prefix + "objectKeyAppDdlDerived=" + plan.objectKeyAppDdlDerived());
        out.println(prefix + "objectKeyEscapeHatch=" + plan.objectKeyEscapeHatch());
        out.println(prefix + "appDdlObjectKeyPaths="
                + escapePhysicalPropertyValue(String.join(",", plan.appDdlObjectKeyPaths())));
        out.println(prefix + "objectKeyColumns="
                + escapePhysicalPropertyValue(String.join(",", plan.objectKeyColumns())));
        out.println(prefix + "appDdlObjectKeyPushdown=" + plan.appDdlObjectKeyPushdown());
        out.println(prefix + "externalObjectKeyRoots="
                + escapePhysicalPropertyValue(String.join(",", plan.externalObjectKeyRoots())));
        out.println(prefix + "identityKind=" + escapePhysicalPropertyValue(plan.identityKind()));
        out.println(prefix + "migrationAction=" + escapePhysicalPropertyValue(plan.migrationAction()));
        out.println(prefix + "recommendation=" + escapePhysicalPropertyValue(plan.recommendation()));
    }

    private String joinStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    private void ensureDirectory(File directory) throws IOException {
        if (directory == null) {
            return;
        }
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Output path is not a directory: " + directory);
            }
            return;
        }
        if (!directory.mkdirs()) {
            throw new IOException("Cannot create output directory " + directory);
        }
    }

    private String joinBackendNames(List<AppPhysicalBackend> backends) {
        List<String> names = new ArrayList<>();
        for (AppPhysicalBackend backend : backends) {
            names.add(backend.getName());
        }
        return String.join(",", names);
    }

    private String backendQuantumStoreName(AppPhysicalBackend backend) {
        if (backend.getQuantumStoreName() != null) {
            return backend.getQuantumStoreName();
        }
        return backend.getConfiguration().get("quantumStore");
    }

    private String joinDbTableNames(List<DbTable> tables) {
        List<String> names = new ArrayList<>();
        for (DbTable table : tables) {
            names.add(table.getName());
        }
        return String.join(",", names);
    }

    private String joinDbColumnNames(List<DbColumn> columns) {
        List<String> names = new ArrayList<>();
        for (DbColumn column : columns) {
            names.add(column.getName());
        }
        return String.join(",", names);
    }

    private String joinAppIndexNames(List<AppIndex> indexes) {
        List<String> names = new ArrayList<>();
        for (AppIndex index : indexes) {
            if (index.getName() != null) {
                names.add(index.getName());
            }
        }
        return String.join(",", names);
    }

    private String joinUnique(List<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value);
            }
        }
        return String.join(",", unique);
    }

    private String joinRemoteServiceNames(List<AppRemoteAdapter> adapters) {
        List<String> names = new ArrayList<>();
        for (AppRemoteAdapter adapter : adapters) {
            names.add(adapter.getName());
        }
        return String.join(",", names);
    }

    private String joinRepositoryNames(List<AppRepositoryAdapter> adapters) {
        List<String> names = new ArrayList<>();
        for (AppRepositoryAdapter adapter : adapters) {
            names.add(adapter.getName());
        }
        return String.join(",", names);
    }

    private String joinQuantumStoreNames(List<AppQuantumStore> stores) {
        List<String> names = new ArrayList<>();
        for (AppQuantumStore store : stores) {
            names.add(store.getName());
        }
        return String.join(",", names);
    }

    private AppQuantumStoreAdapter findQuantumStoreAdapter(List<AppQuantumStoreAdapter> adapters, String name) {
        for (AppQuantumStoreAdapter adapter : adapters) {
            if (adapter.getName().equals(name)) {
                return adapter;
            }
        }
        return null;
    }

    private String joinPhysicalBindings(AppPhysicalBackend backend) {
        List<String> values = new ArrayList<>();
        for (AppPhysicalBinding binding : backend.getBindings()) {
            values.add(binding.getKind().name() + ":" + binding.getTargetName());
        }
        return String.join(",", values);
    }

    private String escapePhysicalPropertyValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ' ' && i == 0) {
                escaped.append("\\ ");
                continue;
            }
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '\t' -> escaped.append("\\t");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (ch < 0x20 || ch > 0x7e) {
                        escaped.append("\\u");
                        String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                        for (int pad = hex.length(); pad < 4; pad++) {
                            escaped.append('0');
                        }
                        escaped.append(hex);
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static void setAdditionalPackage(String basePacakge, String javaPackage, Map<String, SppCompilationUnit> sppClassMap) {
        for (SppCompilationUnit sppClass : sppClassMap.values()) {
            String additional = null;
            IConstance.CompilationUnitType type = sppClass.getType();
            if (type == IConstance.CompilationUnitType.role ||
                    type == IConstance.CompilationUnitType.rolemapper ||
                    type == IConstance.CompilationUnitType.entity ||
                    type == IConstance.CompilationUnitType.contract ||
                    type == IConstance.CompilationUnitType.reference ||
                    type == IConstance.CompilationUnitType.Enum) {
                additional = "model";
            } else if (type == IConstance.CompilationUnitType.scenario ||
                    type == IConstance.CompilationUnitType.atomicservice) {
                additional = "handler";
            }
            sppClass.setBasePackage(basePacakge);
            sppClass.setDomainName(javaPackage);
            sppClass.setAdditionalPackage(additional);
        }
    }

    private void createAPath(String domainName, SppClass sppClass, OpenAPI openAPI) {
        SppService sppService = (SppService) sppClass;
        AnnotationDefine param = null;
        for(AnnotationDefine annotation : sppService.getAnnotations()){
            if("parameter".equals(annotation.getName())){
                param = annotation;
                break;
            }
        }
        IConstance.ServiceType serviceType = sppService.getServiceType();
        PathItem pathItem = new PathItem();
        Operation operation = findMethod(serviceType, sppService, pathItem).addTagsItem(domainName);
        operation.setOperationId(sppService.getFuncName());
        addARequestBody(operation, sppService, openAPI);
        addResponsesBody(openAPI,sppService, operation);

        String path = null;
        if(param != null && (path =param.getParameters().get("path") )!= null){
            String[] params = path.split("\\{");
            if(params.length >1) {
                for (String s : params) {
                    int idx = s.indexOf('}');
                    if (idx != -1){
                        String sparam = s.substring(0,idx);
                        operation.addParametersItem(new Parameter().$ref("#/components/parameters/" +sparam));
                        createAParameter(openAPI,sparam);
                    }
                }
            }
            openAPI.path("/" + domainName + "/" + path, pathItem);
        }else {
            openAPI.path("/" + domainName + "/" + sppService.getFuncName(), pathItem);
        }
    }

    private void createAParameter(OpenAPI openAPI, String sparam) {
        openAPI.getComponents().addParameters(sparam,new Parameter().name(sparam).in("path").required(true).
                style(Parameter.StyleEnum.SIMPLE).schema(new Schema<>().type("string")));
    }

    private void addARequestBody(Operation operation, SppService sppService, OpenAPI openAPI) {
        List<? extends SppLocalVar> sppFieldList = sppService.getServiceBody().getSppLocalVarList();
        String schemaName = sppService.getName() + sppService.getRequestSuffix();
        SppClass cls = getRequestResponseClass(sppService, schemaName, sppFieldList, true);
        schemaName = createRequestResponseSchema(sppService, openAPI, sppFieldList, schemaName, cls, true);
        {
            operation.requestBody(new RequestBody().$ref(schemaName));
            if (openAPI.getComponents() == null)
                openAPI.components(new Components());
            openAPI.getComponents().addRequestBodies(schemaName, new RequestBody().content(
                    new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema().$ref(schemaName)))).required(true));
        }
    }

    private void addResponsesBody(OpenAPI openAPI, SppService sppService, Operation operation) {
        List<? extends SppLocalVar> sppFieldList = sppService.getReturns().getSppLocalVarList();

        String schemaName = sppService.getName() + sppService.getResponseSuffix();
        SppClass cls = getRequestResponseClass(sppService, schemaName, sppFieldList,false);
        schemaName = createRequestResponseSchema(sppService, openAPI, sppFieldList, schemaName, cls, false);
        {
            operation.responses(new ApiResponses().addApiResponse("200", new ApiResponse().$ref(schemaName)));
            if (openAPI.getComponents() == null)
                openAPI.components(new Components());
            ApiResponse apiResponse = new ApiResponse().description(schemaName).content(
                    new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref(schemaName))));
            openAPI.getComponents().addResponses(schemaName, apiResponse);
        }
    }

    private String createRequestResponseSchema(SppService sppService, OpenAPI openAPI, List<? extends SppLocalVar> sppFieldList,
                                               String schemaName, SppClass cls, boolean isRequest) {
        // APP envelopes exist even for a single participant or an empty technical response.
        // They do not change the void, participant-only SPP service signature.
        createASchema(cls, openAPI);
        if (!isRequest && cls.getSppFieldList().isEmpty()) {
            // Keep a named technical envelope: OpenAPI drops completely free-form models.
            openAPI.getComponents().getSchemas().get(schemaName).addProperty("technicalStatus",
                    new io.swagger.v3.oas.models.media.StringSchema());
        }
        return schemaName;
    }

    private SppClass getRequestResponseClass(SppService sppService, String schemaName, List<? extends SppLocalVar> sppFieldList, boolean isRequest) {
        SppClass cls = new SppClass(schemaName, IConstance.CompilationUnitType.role);
        if(sppService.getScopeItem() == null){
            getLogger().warn("Service {}'s scope is not defined!", sppService.getName());
        }else {
            AppHeader appHeader = isRequest ? sppService.getScopeItem().getScopeDefine().getIn()
                    : sppService.getScopeItem().getScopeDefine().getOut();
            if(appHeader != null) {
                SppClass headerCls = new SppClass(appHeader.getName(), IConstance.CompilationUnitType.role);
                for (SppExtendField sppExtendField : appHeader.getSppExtendFields()) {
                    headerCls.addField(new SppField(sppExtendField.getType(), sppExtendField.getName()));
                }
                cls.addField(new SppField(headerCls, "AppHeader"));
            }
        }
        for (SppLocalVar field : sppFieldList) {
            SppField schemaField = (SppField) new SppField(field.getType(), field.getName()).setArrayDimension(field.getArrayDimension());
            schemaField.setPhysicalJavaType(field.getPhysicalJavaType());
            cls.addField(schemaField);
            if (isRequest) {
                addRoleIdentityMetadataFields(cls, field);
            }
        }
        return cls;
    }

    private void addRoleIdentityMetadataFields(SppClass requestClass, SppLocalVar roleVar) {
        if (rolePerformerMetadataFieldsByRole.isEmpty()) { return; }
        if (!(roleVar.getType() instanceof SppClass roleClass)) {
            return;
        }
        addRoleIdentityMetadataFields(requestClass, roleVar.getName(), roleClass, 0, new LinkedHashSet<>());
    }

    private void addRoleIdentityMetadataFields(SppClass requestClass, String rolePath,
                                               SppClass roleClass, int arrayDimension, Set<String> visiting) {
        List<RolePerformerMetadataField> performerFields = rolePerformerMetadataFieldsByRole.get(roleClass.getName());
        if (performerFields != null && !performerFields.isEmpty()) {
            for (RolePerformerMetadataField performerField : performerFields) {
                String metadataFieldName = rolePath + toJavaTypeName(performerField.fieldName());
                if (requestClass.getField(metadataFieldName) != null) {
                    continue;
                }
                requestClass.addField((SppField) new SppField(performerField.type(), metadataFieldName)
                        .setArrayDimension(arrayDimension));
            }
        }
        if (!visiting.add(roleClass.getName())) {
            return;
        }
        for (SppField field : roleClass.getSppFieldList()) {
            if (!(field.getType() instanceof SppClass nestedRoleClass)) {
                continue;
            }
            addRoleIdentityMetadataFields(requestClass, rolePath + toJavaTypeName(field.getName()),
                    nestedRoleClass, arrayDimension + field.getArrayDimension(), visiting);
        }
        visiting.remove(roleClass.getName());
    }

    private String toJavaTypeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Map<String, List<RolePerformerMetadataField>> rolePerformerMetadataFieldsByRole(SppDomain sppDomain) {
        Map<String, List<RolePerformerMetadataField>> fieldsByRole = new LinkedHashMap<>();
        for (SppCompilationUnit unit : sppDomain.getMapsOfClass().values()) {
            if (!(unit instanceof SppRoleMapper mapper)
                    || mapper.getRole() == null
                    || mapper.getEntities() == null
                    || mapper.getEntities().isEmpty()) {
                continue;
            }
            List<RolePerformerMetadataField> performerFields = new ArrayList<>();
            for (SppClass performer : mapper.getEntities()) {
                if (performer == null || performer.getName() == null || performer.getName().isBlank()) {
                    continue;
                }
                performerFields.add(new RolePerformerMetadataField(lowerFirst(performer.getName()), performer));
            }
            if (!performerFields.isEmpty()) {
                List<RolePerformerMetadataField> existing = fieldsByRole.computeIfAbsent(mapper.getRole().getName(),
                        key -> new ArrayList<>());
                for (RolePerformerMetadataField performerField : performerFields) {
                    if (!existing.contains(performerField)) {
                        existing.add(performerField);
                    }
                }
            }
        }
        Map<String, List<RolePerformerMetadataField>> immutableFieldsByRole = new LinkedHashMap<>();
        fieldsByRole.forEach((role, fields) -> immutableFieldsByRole.put(role, List.copyOf(fields)));
        return Map.copyOf(immutableFieldsByRole);
    }

    private record RolePerformerMetadataField(String fieldName, SppClass type) {
    }

    private Operation findMethod(IConstance.ServiceType serviceType, SppService sppService, PathItem pathItem) {
        Operation operation = new Operation();
        if (serviceType != null) {
            switch (sppService.getServiceType()) {
                case check:
                case query:
                case calculate: {
                    pathItem.setGet(operation);
                    break;
                }
                case update:
                case unknown: {
                    pathItem.setPost(operation);
                    break;
                }
            }
        } else {
            pathItem.setPut(operation);
        }
        return operation;
    }

    private void createAEnum(SppEnum sppEnum, OpenAPI openAPI) {
        Schema schema = new Schema();
        schema.setName(sppEnum.getName());
        schema.setTitle(sppEnum.getName());
        schema.setType("string");
        List<SppEnumItem> fields = sppEnum.getItems();
        schema.addExtension("x-enum-varnames", fields.stream().map(SppEnumItem::getName).toList());
        for (SppEnumItem field : fields) {
            if(field.getValue() != null)
                schema.addEnumItemObject(field.getValue());
            else
                schema.addEnumItemObject(field.getName());
        }
        openAPI.schema(sppEnum.getName(), schema);
    }

    private void createASchema(SppClass sppClass, OpenAPI openAPI) {
        Schema schema = new Schema();
        schema.setName(sppClass.getName());
        schema.setType("object");
        if (propertyTracking) {
            trackingExtension.modelExtensions().forEach(schema::addExtension);
        }
        List<SppField> fields = sppClass.getSppFieldList();
        for (SppField field : fields) {
            if (field.isSyntheticRolePerformer()) {
                continue;
            }
            createSchemaField(field, schema);
        }
        openAPI.schema(sppClass.getName(), schema);
    }

    private void createSchemaField(SppField field, Schema schema) {
        boolean isArray = field.getArrayDimension() != 0;
        Schema prop = isArray ? new ArraySchema() : new Schema();
        prop.setName(field.getName());
        if (propertyTracking) trackingExtension.propertyExtensions().forEach(prop::addExtension);
        if (field instanceof SppExtendField sppExtendField
                && sppExtendField.getDefaultValue() != null
                && !sppExtendField.getDefaultValue().isBlank()) {
            prop.setDefault(toOpenApiDefaultValue(sppExtendField));
        }
        SppCompilationUnit type = field.getType();
        if (type.getType() == null) {
            Schema value = isArray ? new Schema() : prop;
            String physicalType = field.getPhysicalJavaType();
            if (field.getNumericAdapter() != null || "java.math.BigInteger".equals(physicalType)) {
                value.setType("number");
                value.setFormat(numericFormat(field.getJavaType()));
                value.addExtension("x-spp-java-type", field.getJavaType());
                if ("java.math.BigInteger".equals(physicalType))
                    prop.addExtension("x-field-extra-annotation", "@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.servpp.service.numeric.ExactIntegerDeserializer.class)");
            } else if ("java.math.BigDecimal".equals(physicalType) || "decimal".equals(type.getName()) && physicalType == null) {
                value.setType("number");
            } else {
                String effectiveType = physicalType == null ? type.getName() : physicalType;
                value.setType(IFileGenerator.toOpenAPIPrimaryType(effectiveType));
                value.setFormat("float".equals(effectiveType) ? "float" : IFileGenerator.toOpenAPITypeFormat(effectiveType));
            }
            if (isArray) { ((ArraySchema) prop).setItems(value); }
        } else if (isArray) {
            ((ArraySchema) prop).setItems(new Schema<>().$ref("#/components/schemas/" + type.getName()));
        } else {
            prop.setType("object");
            prop.set$ref("#/components/schemas/" + type.getName());
        }
        if (field.getPhysicalNumericSpecification() != null) {
            prop.addExtension("x-spp-numeric-default", field.getDeclaredNumericSpecification());
            prop.addExtension("x-spp-numeric-representation", field.getPhysicalNumericSpecification());
        }
        schema.addProperty(field.getName(), prop);
    }

    private static String numericFormat(String javaType) {
        return "spp-numeric-" + javaType.replace('.', '-');
    }

    /** OpenAPI numeric wire semantics and Java physical model types are configured separately. */
    public static void createNumericCodegenConfiguration(SppDomain domain, File yamlRoot) throws IOException {
        Map<String, String> types = new java.util.LinkedHashMap<>();
        for (var binding : domain.getRuleBlock().getNumericBindings()) {
            if (binding.adapter() != null || "java.math.BigInteger".equals(binding.javaType())) {
                types.put("number+" + numericFormat(binding.javaType()), binding.javaType());
            }
        }
        for (String type : List.copyOf(types.values())) { types.put(type, type); }
        java.nio.file.Files.createDirectories(yamlRoot.toPath());
        Map<String, Object> configuration = new java.util.LinkedHashMap<>();
        configuration.put("typeMappings", types);
        Map<String, String> imports = new java.util.LinkedHashMap<>();
        types.values().forEach(type -> imports.put(type, type));
        configuration.put("importMappings", imports);
        io.swagger.v3.core.util.Json.mapper().writerWithDefaultPrettyPrinter().writeValue(
                new File(yamlRoot, domain.getName() + ".numeric-codegen.json"), configuration);
    }

    private Object toOpenApiDefaultValue(SppExtendField field) {
        String defaultValue = field.getDefaultValue();
        String typeName = field.getType() == null ? null : field.getType().getName();
        if ("String".equals(typeName) && defaultValue.length() >= 2
                && defaultValue.startsWith("\"") && defaultValue.endsWith("\"")) {
            return defaultValue.substring(1, defaultValue.length() - 1);
        }
        if ("int".equals(typeName) || "Integer".equals(typeName)) {
            return Integer.valueOf(defaultValue);
        }
        if ("long".equals(typeName) || "Long".equals(typeName)) {
            return Long.valueOf(defaultValue);
        }
        if ("double".equals(typeName) || "Double".equals(typeName)) {
            return Double.valueOf(defaultValue);
        }
        if ("boolean".equals(typeName) || "Boolean".equals(typeName)) {
            return Boolean.valueOf(defaultValue);
        }
        return defaultValue;
    }

    private void createASchema(AppHeader appHeader, OpenAPI openAPI) {
        Schema schema = new Schema();
        schema.setName(appHeader.getName());
        schema.setTitle(appHeader.getName());
        schema.setType("object");
        List<SppExtendField> fields = appHeader.getSppExtendFields();
        for (SppExtendField field : fields) {
            createSchemaField(field, schema);
        }
        openAPI.schema(appHeader.getName(), schema);
    }

}
