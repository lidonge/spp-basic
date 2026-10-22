package com.servpp.service.technical;

import com.fasterxml.jackson.databind.JsonNode;
import com.servpp.service.runtime.PhysicalRuntimeCatalog;
import java.util.*;

/** Only APP-declared, PHY-bound implementations can be invoked by a trusted technical ingress. */
public final class TechnicalRequestServices implements AutoCloseable {
    private record Definition(String implementation,Map<String,String> configuration) {}
    private final Map<String,Definition> definitions;
    private final Map<String,TechnicalRequestService> instances=new java.util.concurrent.ConcurrentHashMap<>();
    private final ClassLoader loader;
    private TechnicalRequestServices(Properties catalog,ClassLoader loader) {
        this.loader=loader;var entries=new LinkedHashMap<String,Definition>();
        for(String name:catalog.getProperty("services","").split(",")) {
            if(name.isBlank())continue;
            String prefix="service."+name+".";
            String implementation=catalog.getProperty(prefix+"implementation");
            if(implementation==null||implementation.isBlank())throw new IllegalArgumentException("TECHNICAL_IMPLEMENTATION_REQUIRED: "+name);
            var config=new LinkedHashMap<String,String>();
            for(String key:catalog.stringPropertyNames())if(key.startsWith(prefix+"config.")) config.put(key.substring((prefix+"config.").length()),catalog.getProperty(key));
            if(entries.putIfAbsent(name,new Definition(implementation,Map.copyOf(config)))!=null)throw new IllegalArgumentException("DUPLICATE_TECHNICAL_SERVICE");
        }
        definitions=Map.copyOf(entries);
    }
    public static TechnicalRequestServices load(String resource,ClassLoader loader) {
        try(var input=loader.getResourceAsStream(resource.startsWith("/")?resource.substring(1):resource)) {
            if(input==null)throw new IllegalStateException("TECHNICAL_CATALOG_REQUIRED");
            var catalog=new Properties();catalog.load(input);return new TechnicalRequestServices(catalog,loader);
        } catch(java.io.IOException failure){throw new IllegalStateException("TECHNICAL_CATALOG_LOAD_FAILED",failure);}
    }
    public Set<String> names(){return definitions.keySet();}
    public JsonNode invoke(String name,JsonNode request) throws Exception {
        if(!definitions.containsKey(name))throw new IllegalArgumentException("UNDECLARED_TECHNICAL_SERVICE");
        return instances.computeIfAbsent(name,this::instantiate).invoke(request.deepCopy());
    }
    /** Release only already-created adapters; closing never initializes a new technical service. */
    @Override public void close()throws Exception {
        Exception problem=null;
        for(var service:List.copyOf(instances.values()))if(service instanceof AutoCloseable resource) {
            try{resource.close();}catch(Exception failure){if(problem==null)problem=failure;else problem.addSuppressed(failure);}
        }
        if(problem!=null)throw problem;
    }
    private TechnicalRequestService instantiate(String name) {
        var definition=definitions.get(name);
        try {
            var type=Class.forName(definition.implementation(),true,loader);
            if(!TechnicalRequestService.class.isAssignableFrom(type))throw new IllegalArgumentException("TECHNICAL_REQUEST_INTERFACE_REQUIRED: "+name);
            var service=(TechnicalRequestService)type.getConstructor().newInstance();
            var resolved=new LinkedHashMap<String,String>();
            definition.configuration().forEach((key,value)->resolved.put(key,PhysicalRuntimeCatalog.resolveConfigValue(value)));
            service.configure(Map.copyOf(resolved));return service;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("TECHNICAL_SERVICE_INITIALIZATION_FAILED: "+name,failure);}
    }
}
