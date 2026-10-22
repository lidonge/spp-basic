package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScenarioCollectionGenerationTest {
    @Test public void optionalCollectionPropertiesCallAtomicServicesWithPerCallResponses() throws Exception {
        Path source=Files.createTempDirectory("scenario-collection-source"), root=Files.createTempDirectory("scenario-collection-java");
        Files.writeString(source.resolve("rules.spp"), """
                domain rules;
                Role Item { int value; }
                Role Holder { Item[] items; }
                atomic calculate count(Item[] items, Holder holder) { holder.items = copy(items); }
                Scenario serial relay(Item[] items, Holder holder) { count(items, holder); }
                Scenario serial nested(Holder holder) { count(holder.items, holder); }
                """);
        Files.writeString(source.resolve("rules.app"),"domain rules; header requestContext { unique String globalSerialNumber; } scope { local(in requestContext, out requestContext) { count; relay; idempotency business nested; } }");
        GeneratorUtil.execute(source.toFile(),root.resolve("yaml").toFile(),root.resolve("java").toFile(),"example");
        Path models=root.resolve("java/example/rules/model");Files.createDirectories(models);
        Files.writeString(models.resolve("Item.java"), "package example.rules.model; public class Item { private Integer value; public Integer getValue(){return value;} public void setValue(Integer x){value=x;} }");
        Files.writeString(models.resolve("Holder.java"), "package example.rules.model; public class Holder { public static final RuntimeException failure=new IllegalStateException(\"business failure\"); private java.util.List<Item> items; public java.util.List<Item> getItems(){return items;} public void setItems(java.util.List<Item> x){items=x;} }");
        for(String name:List.of("Count","Relay")) {
            Files.writeString(models.resolve(name+"Request.java"), "package example.rules.model; public class "+name+"Request { private java.util.List<Item> items; private Holder holder; public java.util.List<Item> getItems(){return items;} public void setItems(java.util.List<Item> x){items=x;} public Holder getHolder(){return holder;} public void setHolder(Holder x){holder=x;} }");
            Files.writeString(models.resolve(name+"Response.java"),"package example.rules.model; public class "+name+"Response { }");
        }
        Files.writeString(models.resolve("NestedRequest.java"), "package example.rules.model; public class NestedRequest { public Header getAppHeader(){return new Header();} public static class Header { public String getGlobalSerialNumber(){return \"collection-same-request\";} } private Holder holder; public Holder getHolder(){return holder;} public void setHolder(Holder x){holder=x;} }");
        Files.writeString(models.resolve("NestedResponse.java"),"package example.rules.model; public class NestedResponse { }");
        Path classes=Files.createDirectory(root.resolve("classes"));List<String> args=new ArrayList<>(List.of("-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var paths=Files.walk(root.resolve("java"))) { paths.filter(p->p.toString().endsWith(".java")).forEach(p->args.add(p.toString())); }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader())) {
            var container=DefaultServiceContainer.getInstance();
            var previousStore=com.servpp.service.idempotency.BusinessIdempotencyManager.getStore();
            var previousBypass=com.servpp.service.idempotency.BusinessIdempotencyManager.frameworkBypassServices();
            com.servpp.service.idempotency.BusinessIdempotencyManager.setStore(null);
            Service previousCount=container.getService("count"), previousRelay=container.getService("relay");
            try {
            Service count=(Service)loader.loadClass("example.rules.service.SimpleCount").getConstructor().newInstance();
            container.registerService(count.getName(),count);
            Service relay=(Service)loader.loadClass("example.rules.service.SimpleRelay").getConstructor().newInstance();
            container.registerService(relay.getName(),relay);
            Service nested=(Service)loader.loadClass("example.rules.service.SimpleNested").getConstructor().newInstance();
            Class<?> holderClass=loader.loadClass("example.rules.model.Holder"), requestClass=loader.loadClass("example.rules.model.NestedRequest");
            Object holder=holderClass.getConstructor().newInstance(), request=requestClass.getConstructor().newInstance();requestClass.getMethod("setHolder",holderClass).invoke(request,holder);
            Object first=nested.execute(request);assertNotNull(first);assertNull(holderClass.getMethod("getItems").invoke(holder));
            Object item=loader.loadClass("example.rules.model.Item").getConstructor().newInstance();item.getClass().getMethod("setValue",Integer.class).invoke(item,7);
            holderClass.getMethod("setItems",List.class).invoke(holder,List.of(item));
            Object second=nested.execute(request);assertNotSame(first,second);
            List<?> copied=(List<?>)holderClass.getMethod("getItems").invoke(holder);assertEquals(1,copied.size());assertNotSame(item,copied.get(0));assertEquals(7,copied.get(0).getClass().getMethod("getValue").invoke(copied.get(0)));
            Object typed=nested.getClass().getMethod("nested",holderClass).invoke(nested,holder);assertNull("Typed scenarios have no business return value",typed);
            List<?> copiedAgain=(List<?>)holderClass.getMethod("getItems").invoke(holder);
            assertNotSame(copied,copiedAgain);assertNotSame(copied.get(0),copiedAgain.get(0));
            assertEquals(7,copiedAgain.get(0).getClass().getMethod("getValue").invoke(copiedAgain.get(0)));
            var previousLog=com.servpp.service.transaction.SagaLogManager.getStore();
            RuntimeException logFailure=new IllegalStateException("trace offline");
            try {
                com.servpp.service.transaction.SagaLogManager.setStore(new com.servpp.service.transaction.SagaLogStore() {
                    public void append(com.servpp.service.transaction.SagaLogEntry entry){throw logFailure;}
                    public List<com.servpp.service.transaction.SagaLogEntry> findAll(){return List.of();}
                    public void clear(){ }
                });
                Throwable original=(Throwable)holderClass.getField("failure").get(null);
                Object failingCount=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{loader.loadClass("example.rules.handler.Count")},
                        (proxy,method,arguments)->{if(method.getName().equals("count")){throw original;}return method.invoke(count,arguments);});
                container.registerService("count",(Service)failingCount);
                container.registerService("relay",(Service)loader.loadClass("example.rules.service.SimpleRelay").getConstructor().newInstance());
                Service failingNested=(Service)loader.loadClass("example.rules.service.SimpleNested").getConstructor().newInstance();
                RuntimeException actual=assertThrows(RuntimeException.class,()->failingNested.execute(request));
                assertSame(original,actual);assertEquals(1,actual.getSuppressed().length);assertSame(logFailure,actual.getSuppressed()[0]);
            } finally { com.servpp.service.transaction.SagaLogManager.setStore(previousLog); }

            } finally {
                com.servpp.service.idempotency.BusinessIdempotencyManager.setStore(previousStore);
                com.servpp.service.idempotency.BusinessIdempotencyManager.setFrameworkBypassServices(previousBypass);
                var field=DefaultServiceContainer.class.getDeclaredField("serviceMap");field.setAccessible(true);
                @SuppressWarnings("unchecked") var services=(Map<String,Service>)field.get(container);
                if(previousCount==null) services.remove("count"); else services.put("count",previousCount);
                if(previousRelay==null) services.remove("relay"); else services.put("relay",previousRelay);
            }
        }
    }
}
