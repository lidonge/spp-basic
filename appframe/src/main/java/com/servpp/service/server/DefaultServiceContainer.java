package com.servpp.service.server;

import com.servpp.ILogable;
import com.servpp.service.Service;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lidong@date 2023-11-16@version 1.0
 */
public class DefaultServiceContainer implements IServiceContainer {
    private static DefaultServiceContainer instance;
    private static ILogable logable = new ILogable() {
        @Override
        public Logger getLogger() {
            return ILogable.super.getLogger();
        }
    };

    public static DefaultServiceContainer getInstance(){
        synchronized (DefaultServiceContainer.class) {
            if (instance == null) {
                DefaultServiceContainer candidate = new DefaultServiceContainer();
                InputStream stream = DefaultServiceContainer.class.getResourceAsStream("/services.properties");
                if (stream == null) {
                    instance = candidate;
                    return instance;
                }
                try (InputStream services = stream) {
                    candidate.loadFromProperties(services);
                } catch (IOException | ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to load generated services from /services.properties.", e);
                }
                instance = candidate;
            }

        }
        return instance;
    }

    DefaultServiceContainer(){
    }
    private Map<String,Service> serviceMap = new ConcurrentHashMap<>();

    public void loadFromList(List<Service> services){
        for(Service service:services){
            serviceMap.put(service.getName(),service);
        }
    }

    void loadFromProperties(InputStream stream) throws IOException, ReflectiveOperationException {
        Properties properties = new Properties();
        properties.load(stream);
        List<LoadedService> loadedServices = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            String className = properties.getProperty(key);
            loadedServices.add(new LoadedService(key, className, instantiate(className)));
        }
        for (LoadedService loadedService : loadedServices) {
            registerService(loadedService.name(), loadedService.service());
            logable.getLogger().info("Service {} loaded,class is {}.",
                    loadedService.name(), loadedService.className());
        }
    }

    private Service instantiate(String className) throws ReflectiveOperationException {
        try {
            return (Service) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    @Override
    public Service getService(String name) {
        return serviceMap.get(name);
    }

    /** Explicit domain identity; the fallback keeps legacy single-domain artifacts working. */
    public Service getService(String domain, String name) {
        Service qualified = serviceMap.get(domain + "." + name);
        return qualified != null ? qualified : serviceMap.get(name);
    }

    public Service registerService(String name, Service service){
        return serviceMap.put(name,service);
    }

    public void clear() {
        serviceMap.clear();
    }

    private record LoadedService(String name, String className, Service service) {
    }
}
