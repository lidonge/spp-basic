package com.servpp.lang.models.app;

import com.servpp.lang.models.IAnnotation;
import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.IContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public class RuleBlock {
    private final Map<String, Boolean> systemServiceSwitches = new java.util.LinkedHashMap<>();
    public boolean isSystemServiceEnabled(String name) { return systemServiceSwitches.getOrDefault(name, true); }
    public void setSystemServiceEnabled(String name, boolean enabled) {
        if (!Set.of("idempotency", "duplicateGuard", "propertyTracking", "partialUpdate", "optimisticConcurrency").contains(name))
            throw new IllegalArgumentException("Unknown system service " + name);
        if (systemServiceSwitches.putIfAbsent(name, enabled) != null)
            throw new IllegalArgumentException("Duplicate " + name + " policy");
    }
    public boolean isPropertyTracking() { return isSystemServiceEnabled("propertyTracking"); }
    public void setPropertyTracking(boolean enabled) {
        setSystemServiceEnabled("propertyTracking", enabled);
    }

    private final TechnicalServices technicalServices = new TechnicalServices();
    public TechnicalServices getTechnicalServices() { return technicalServices; }
    private final Map<String, DomainRoute> domainRoutes = new java.util.LinkedHashMap<>();
    public Map<String, DomainRoute> getDomainRoutes() { return domainRoutes; }

    private final Map<String, Map<String, DeploymentEnvironment>> deploymentEnvironments = new java.util.LinkedHashMap<>();
    public Map<String, Map<String, DeploymentEnvironment>> getDeploymentEnvironments() { return deploymentEnvironments; }
    private String deploymentEnvironment;
    private String deploymentTransactionMode;
    private String deploymentQuantumSystem;
    private String deploymentQuantumResource;
    public String getDeploymentEnvironment() { return deploymentEnvironment; }
    public String getDeploymentTransactionMode() { return deploymentTransactionMode; }
    public String getDeploymentQuantumSystem() { return deploymentQuantumSystem; }
    public String getDeploymentQuantumResource() { return deploymentQuantumResource; }
    public void selectDeployment(String environment, String mode, String quantumSystem, String quantumResource) {
        if (deploymentEnvironment != null && (!deploymentEnvironment.equals(environment)
                || !java.util.Objects.equals(deploymentTransactionMode, mode)
                || !java.util.Objects.equals(deploymentQuantumSystem, quantumSystem)
                || !java.util.Objects.equals(deploymentQuantumResource, quantumResource)))
            throw new IllegalArgumentException("Conflicting deployment contract");
        deploymentEnvironment = environment; deploymentTransactionMode = mode;
        deploymentQuantumSystem = quantumSystem; deploymentQuantumResource = quantumResource;
    }
    private String ordinaryUpdates;
    private String deploymentOrdinaryUpdates;
    public boolean isParallelOrdinaryUpdates() { return "PARALLEL".equals(deploymentOrdinaryUpdates == null ? ordinaryUpdates : deploymentOrdinaryUpdates); }
    public void setOrdinaryUpdates(String value) {
        if (ordinaryUpdates != null) throw new IllegalArgumentException("Duplicate ordinaryUpdates policy");
        ordinaryUpdates = value;
    }
    public void selectOrdinaryUpdates(String value) {
        if (deploymentOrdinaryUpdates != null && !deploymentOrdinaryUpdates.equals(value))
            throw new IllegalArgumentException("Conflicting deployment ordinaryUpdates policy");
        deploymentOrdinaryUpdates = value;
    }
    private String defaultUpdateMode;
    private String defaultUpdateResource;
    public boolean hasTransactionPolicy() { return defaultUpdateMode != null; }
    public String getDefaultUpdateMode() { return defaultUpdateMode; }
    public String getDefaultUpdateResource() { return defaultUpdateResource; }
    public void setDefaultUpdateTransaction(String mode, String resource) {
        defaultUpdateMode = mode; defaultUpdateResource = resource;
    }
    private final Map<String,String> requestPolicies = new java.util.LinkedHashMap<>();
    public Map<String,String> getRequestPolicies() { return requestPolicies; }
    private String defaultLocale;
    private List<String> supportedLocales = List.of();
    private final Map<String, Map<String,String>> constantTranslations = new java.util.LinkedHashMap<>();
    public String getDefaultLocale() { return defaultLocale; }
    public void setDefaultLocale(String locale) { defaultLocale = locale; }
    public List<String> getSupportedLocales() { return supportedLocales; }
    public void setSupportedLocales(List<String> locales) { supportedLocales = List.copyOf(locales); }
    public Map<String, Map<String,String>> getConstantTranslations() { return constantTranslations; }
    private final Map<String, List<String>> applicationDeployments = new java.util.LinkedHashMap<>();
    private final List<LocalServiceBinding> localServiceBindings = new ArrayList<>();
    public Map<String, List<String>> getApplicationDeployments() { return applicationDeployments; }
    public List<LocalServiceBinding> getLocalServiceBindings() { return localServiceBindings; }
    private final Map<String, ExampleFault> exampleFaults = new HashMap<>();
    public Map<String, ExampleFault> getExampleFaults() { return exampleFaults; }
    private final Map<String, ExampleBarrier> exampleBarriers = new HashMap<>();
    public Map<String, ExampleBarrier> getExampleBarriers() { return exampleBarriers; }
    private final Map<String, ExampleRestart> exampleRestarts = new HashMap<>();
    public Map<String, ExampleRestart> getExampleRestarts() { return exampleRestarts; }
    private final Map<String, ExampleJdbcFault> exampleJdbcFaults = new HashMap<>();
    public Map<String, ExampleJdbcFault> getExampleJdbcFaults() { return exampleJdbcFaults; }

    private final List<String> invalidWebActions = new ArrayList<>();
    public List<String> getInvalidWebActions() { return invalidWebActions; }
    private final List<String> webServiceNames = new ArrayList<>();
    private final List<String> webStartupNames = new ArrayList<>();
    public List<String> getWebServiceNames() { return webServiceNames; }
    public List<String> getWebStartupNames() { return webStartupNames; }
    private final List<String> exampleServiceNames = new ArrayList<>();

    public List<String> getExampleServiceNames() { return exampleServiceNames; }

    List<AppHeader> headers = new ArrayList<>();
    List<AppMapper> appMappers = new ArrayList<>();
    AppPrimaryKeys appPrimaryKeys = new AppPrimaryKeys();
    AppScope appScope = new AppScope();
    AppServices appServices = new AppServices();
    List<AppTables> appTables = new ArrayList<>();
    List<AppPhysicalBackend> appPhysicalBackends = new ArrayList<>();
    List<AppRemoteAdapter> appRemoteAdapters = new ArrayList<>();
    List<AppRepositoryAdapter> appRepositoryAdapters = new ArrayList<>();
    List<AppQuantumStore> appQuantumStores = new ArrayList<>();
    List<AppQuantumStoreAdapter> appQuantumStoreAdapters = new ArrayList<>();
    List<NumericBinding> numericBindings = new ArrayList<>();

    public List<NumericBinding> getNumericBindings() { return numericBindings; }

    Map<String, String> physicalConfiguration = new HashMap<>();
    Map<String, AppTable> allTables = new HashMap();
    Set<String> duplicateTableNames = new LinkedHashSet<>();
    Set<String> duplicateMapperNames = new LinkedHashSet<>();
    Set<String> duplicatePhysicalBackendNames = new LinkedHashSet<>();
    Set<String> duplicateRemoteAdapterNames = new LinkedHashSet<>();
    Set<String> duplicateRepositoryAdapterNames = new LinkedHashSet<>();
    Set<String> duplicateQuantumStoreNames = new LinkedHashSet<>();
    Set<String> duplicateQuantumStoreAdapterNames = new LinkedHashSet<>();
    Set<String> duplicatePhysicalConfigurationKeys = new LinkedHashSet<>();

    List<AnnotationDefine> currentLineAnns;
    IAnnotation currentAnnotatable;
    IContainer appAnnotations = new IContainer() {
        @Override
        public void _add(IComponent component) {
            appAnnotationList.add((AppAnnotation) component);
        }

        @Override
        public boolean isContains(IComponent component) {
            return appAnnotationList.contains(component);
        }

        @Override
        public IContainer getParent() {
            return null;
        }

        @Override
        public void setParent(IContainer parent) {

        }
    };
    List<AppAnnotation> appAnnotationList = new ArrayList<>();

    public Map<String, AppTable> getAllTables() {
        return allTables;
    }

    public void addDuplicateTableName(String tableName) {
        duplicateTableNames.add(tableName);
    }

    public Set<String> getDuplicateTableNames() {
        return duplicateTableNames;
    }

    public void setAllTables(Map<String, AppTable> allTables) {
        this.allTables = allTables;
    }

    public IAnnotation getCurrentAnnotatable() {
        return currentAnnotatable;
    }

    public void setCurrentAnnotatable(IAnnotation currentAnnotatable) {
        this.currentAnnotatable = currentAnnotatable;
    }

    public void addLineAnn(AnnotationDefine lineAnn) {
        if(currentLineAnns == null)
            currentLineAnns = new ArrayList<>();
        currentLineAnns.add(lineAnn);
    }

    public void clearLineAnn(){
        currentLineAnns = null;
    }

    public List<AnnotationDefine> getCurrentLineAnns() {
        return currentLineAnns;
    }

    public void setCurrentLineAnns(List<AnnotationDefine> currentLineAnns) {
        this.currentLineAnns = currentLineAnns;
    }

    public List<AppTables> getAppTables() {
        return appTables;
    }

    public List<AppAnnotation> getAppAnnotationList() {
        return appAnnotationList;
    }

    public IContainer getAppAnnotations() {
        return appAnnotations;
    }

    public AppServices getAppServices() {
        return appServices;
    }

    public List<AppMapper> getAppMappers() {
        return appMappers;
    }

    public List<AppPhysicalBackend> getAppPhysicalBackends() {
        return appPhysicalBackends;
    }

    public void addPhysicalBackend(AppPhysicalBackend backend) {
        for (AppPhysicalBackend existing : appPhysicalBackends) {
            if (existing.getName().equals(backend.getName())) {
                duplicatePhysicalBackendNames.add(backend.getName());
                break;
            }
        }
        appPhysicalBackends.add(backend);
    }

    public Set<String> getDuplicatePhysicalBackendNames() {
        return duplicatePhysicalBackendNames;
    }

    public Map<String, String> getPhysicalConfiguration() {
        return physicalConfiguration;
    }

    public void putPhysicalConfiguration(String key, String value) {
        if (physicalConfiguration.containsKey(key)) {
            duplicatePhysicalConfigurationKeys.add(key);
        }
        physicalConfiguration.put(key, value);
    }

    public Set<String> getDuplicatePhysicalConfigurationKeys() {
        return duplicatePhysicalConfigurationKeys;
    }

    public List<AppRemoteAdapter> getAppRemoteAdapters() {
        return appRemoteAdapters;
    }

    public void addRemoteAdapter(AppRemoteAdapter adapter) {
        for (AppRemoteAdapter existing : appRemoteAdapters) {
            if (existing.getName().equals(adapter.getName())) {
                duplicateRemoteAdapterNames.add(adapter.getName());
                break;
            }
        }
        appRemoteAdapters.add(adapter);
    }

    public Set<String> getDuplicateRemoteAdapterNames() {
        return duplicateRemoteAdapterNames;
    }

    public List<AppRepositoryAdapter> getAppRepositoryAdapters() {
        return appRepositoryAdapters;
    }

    public void addRepositoryAdapter(AppRepositoryAdapter adapter) {
        for (AppRepositoryAdapter existing : appRepositoryAdapters) {
            if (existing.getName().equals(adapter.getName())) {
                duplicateRepositoryAdapterNames.add(adapter.getName());
                break;
            }
        }
        appRepositoryAdapters.add(adapter);
    }

    public Set<String> getDuplicateRepositoryAdapterNames() {
        return duplicateRepositoryAdapterNames;
    }

    public List<AppQuantumStore> getAppQuantumStores() {
        return appQuantumStores;
    }

    public void addQuantumStore(AppQuantumStore store) {
        for (AppQuantumStore existing : appQuantumStores) {
            if (existing.getName().equals(store.getName())) {
                duplicateQuantumStoreNames.add(store.getName());
                break;
            }
        }
        appQuantumStores.add(store);
    }

    public Set<String> getDuplicateQuantumStoreNames() {
        return duplicateQuantumStoreNames;
    }

    public List<AppQuantumStoreAdapter> getAppQuantumStoreAdapters() {
        return appQuantumStoreAdapters;
    }

    public void addQuantumStoreAdapter(AppQuantumStoreAdapter adapter) {
        for (AppQuantumStoreAdapter existing : appQuantumStoreAdapters) {
            if (existing.getName().equals(adapter.getName())) {
                duplicateQuantumStoreAdapterNames.add(adapter.getName());
                break;
            }
        }
        appQuantumStoreAdapters.add(adapter);
    }

    public Set<String> getDuplicateQuantumStoreAdapterNames() {
        return duplicateQuantumStoreAdapterNames;
    }

    public String addMapper(AppMapper appMapper){
        for (AppMapper existing : appMappers) {
            if (existing.getName().equals(appMapper.getName())) {
                duplicateMapperNames.add(appMapper.getName());
                appMappers.add(appMapper);
                return "Duplicate mapper " + appMapper.getName();
            }
        }
        appMappers.add(appMapper);
        return null;
    }

    public Set<String> getDuplicateMapperNames() {
        return duplicateMapperNames;
    }

    public String addAppHeader(AppHeader appHeader){
        AppHeader header = searchAppHeader(appHeader);
        if(header != null) {
            if(!header.isTemp()){
                return "Duplicate header " + appHeader.getName();
            }
            headers.remove(header);
        }
        headers.add(appHeader);
        return null;
    }
    public AppHeader newOrExistAppHeader(String name) {
        AppHeader header = new AppHeader();
        header.setName(name);
        AppHeader exist = searchAppHeader(header);
        if(exist != null){
            header = exist;
        }else{
            header.setTemp(true);
            addAppHeader(header);
        }
        return header;
    }

    public AppHeader searchAppHeader(AppHeader appHeader) {
        return (AppHeader) appHeader.searchAppHeader(headers, appHeader);
    }

    public String addPrimaryKey(String qualifiedName, SppFieldReference key){
        return appPrimaryKeys.addKey(qualifiedName,key);
    }
    public List<AppHeader> getHeaders() {
        return headers;
    }

    public AppPrimaryKeys getPrimaryKeys() {
        return appPrimaryKeys;
    }

    public AppScope getAppScope() {
        return appScope;
    }

    @Override
    public String toString() {
        return "RuleBlock{" +
                "headers=" + headers +
                ", appMappers=" + appMappers +
                ", primaryKeys=" + appPrimaryKeys +
                ", appScope=" + appScope +
                '}';
    }
}
