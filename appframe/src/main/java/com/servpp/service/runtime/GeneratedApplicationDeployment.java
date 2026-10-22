package com.servpp.service.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Loads APP's declared local domains; no business orchestration is implemented here. */
public final class GeneratedApplicationDeployment {
    private GeneratedApplicationDeployment() { }
    public static Properties install(LocalGeneratedServiceRuntime runtime, String applicationName) {
        Properties applications = read("/META-INF/spp/applications.properties", true);
        String prefix = null;
        for (int index = 0; index < Integer.parseInt(applications.getProperty("application.count", "0")); index++) {
            String candidate = "application." + index + ".";
            if (applicationName == null || applicationName.equals(applications.getProperty(candidate + "name"))) {
                if (prefix != null) throw new IllegalStateException("APP_APPLICATION_NAME_REQUIRED");
                prefix = candidate;
            }
        }
        if (prefix == null) throw new IllegalStateException("APP_APPLICATION_NOT_DECLARED: " + applicationName);
        List<String> domains = List.of(applications.getProperty(prefix + "domains").split(","));
        var domainPhysical = new java.util.LinkedHashMap<String, Properties>();
        for (String domain : domains) domainPhysical.put(domain, read("/META-INF/spp/domains/" + domain + "/physical.properties", true));
        DeploymentContracts.validate(applications, prefix, domainPhysical);
        var allPhysical = new java.util.LinkedHashMap<String,Properties>(domainPhysical);
        for (String domain : applications.getProperty("sourceDomains",String.join(",",domains)).split(","))
            allPhysical.putIfAbsent(domain,read("/META-INF/spp/domains/"+domain+"/physical.properties",true));
        Properties ddl = new Properties(), physical = new Properties(), services = new Properties(), roles = new Properties(), web = new Properties();
        roles.setProperty("roleParticipant.count", "0");
        web.setProperty("expose.count", "0"); web.setProperty("start.count", "0");
        for (String domain : domains) {
            String root = "/META-INF/spp/domains/" + domain + "/";
            merge(ddl, read(root + "app-ddl.properties", true));
            merge(physical, domainPhysical.get(domain));
            merge(services, read(root + "services.properties", true));
            appendIndexed(roles, read(root + "role-participants.properties", true), "roleParticipant");
            Properties entries = read(root + "web.properties", true);
            appendIndexed(web, entries, "expose"); appendIndexed(web, entries, "start");
            InternationalizedConstants.configure(domain, read(root + "constants.properties", true));
        }
        runtime.loadAppDdlCatalog(stream(ddl));
        runtime.loadPhysicalRuntimeCatalog(stream(physical));
        runtime.loadRoleParticipantPlans(stream(roles));
        runtime.registerGeneratedServices(com.servpp.service.server.DefaultServiceContainer.getInstance(), stream(services));
        DomainServiceRouter.configure(applications,prefix,domains,allPhysical);
        web.setProperty("entryDomain", applications.getProperty(prefix + "entryDomain"));
        web.setProperty("domains", String.join(",", domains));
        return web;
    }
    public static boolean isPresent() { return GeneratedApplicationDeployment.class.getResource("/META-INF/spp/applications.properties") != null; }
    private static Properties read(String resource, boolean required) {
        Properties values = new Properties();
        try (var input = GeneratedApplicationDeployment.class.getResourceAsStream(resource)) {
            if (input == null) { if (required) throw new IllegalStateException("APP_DOMAIN_CATALOG_MISSING: " + resource); return values; }
            values.load(input); return values;
        } catch (IOException failure) { throw new IllegalStateException("APP_DOMAIN_CATALOG_READ_FAILED: " + resource, failure); }
    }
    private static ByteArrayInputStream stream(Properties values) {
        try {
            var output = new ByteArrayOutputStream(); values.store(output, "APP deployment aggregate");
            return new ByteArrayInputStream(output.toByteArray());
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    static void merge(Properties target, Properties source) {
        Set<String> lists = Set.of("tables", "backends", "repositories", "remoteServices", "quantumStores", "physicalSourceFiles", "domainRoutes");
        for (String name : source.stringPropertyNames()) {
            String incoming = source.getProperty(name), current = target.getProperty(name);
            if (current == null || current.equals(incoming)) target.setProperty(name, incoming);
            else if (lists.contains(name) || name.endsWith(".bindings") || name.endsWith(".attributes")) {
                var items = new LinkedHashSet<String>();
                for (String value : (current + "," + incoming).split(",")) if (!value.isBlank()) items.add(value);
                target.setProperty(name, String.join(",", items));
            } else throw new IllegalStateException("APP_SHARED_RESOURCE_CONFIGURATION_CONFLICT: " + name);
        }
    }
    private static void appendIndexed(Properties target, Properties source, String section) {
        int offset = Integer.parseInt(target.getProperty(section + ".count", "0"));
        int count = Integer.parseInt(source.getProperty(section + ".count", "0"));
        for (int index = 0; index < count; index++) {
            String from = section + "." + index + ".", to = section + "." + (offset + index) + ".";
            for (String key : source.stringPropertyNames()) if (key.startsWith(from)) target.setProperty(to + key.substring(from.length()), source.getProperty(key));
        }
        target.setProperty(section + ".count", Integer.toString(offset + count));
    }
}
