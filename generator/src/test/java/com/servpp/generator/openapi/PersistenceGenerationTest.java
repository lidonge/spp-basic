package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.service.Service;
import com.servpp.service.repository.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersistenceGenerationTest {
    @Test public void generatedUpdateUsesReadRevisionAndPersistsStableParticipantAfterBody() throws Exception {
        Path source=Files.createTempDirectory("spp-write-source"), root=Files.createTempDirectory("spp-write-generated");
        Files.writeString(source.resolve("rules.spp"),"""
                domain rules;
                Contract Stored short stored { String code; long value; }
                atomic query loadStored(Stored);
                atomic update store(Stored);
                atomic update erase(Stored);
                Scenario serial saveRecord(Stored) { loadStored(stored); store(stored); store(stored); }
                Scenario serial eraseRecord(Stored) { erase(stored); }
                """);
        Files.writeString(source.resolve("rules.app"),"""
                domain rules;
                scope { local { loadStored; saveRecord; eraseRecord; at resource(records)
                    persist(update, argument(0, Stored.code)) store;
                    at resource(records) persist(delete, argument(0, Stored.code)) erase;
                } }
                services { store { stored.value += 10; } }
                """);
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(),"example");
        Path models=root.resolve("java/example/rules/model");Files.createDirectories(models);
        Files.writeString(models.resolve("Stored.java"),"""
                package example.rules.model;
                public class Stored {
                    private String code; private Long value;
                    public String getCode() { return code; } public void setCode(String v) { code=v; }
                    public Long getValue() { return value; } public void setValue(Long v) { value=v; }
                }
                """);
        Files.writeString(models.resolve("StoreRequest.java"), """
                package example.rules.model;
                public class StoreRequest {
                    private final Stored stored;
                    public StoreRequest(Stored stored) { this.stored=stored; }
                    public Stored getStored() { return stored; }
                }
                """);
        Files.writeString(models.resolve("StoreResponse.java"),"package example.rules.model; public class StoreResponse { }");
        Files.writeString(models.resolve("EraseRequest.java"), """
                package example.rules.model;
                public class EraseRequest {
                    private final Stored stored;
                    public EraseRequest(Stored key) { this.stored=key; }
                    public Stored getStored() { return stored; }
                }
                """);
        Files.writeString(models.resolve("EraseResponse.java"),"package example.rules.model; public class EraseResponse { }");
        for (String[] pair : List.of(new String[]{"LoadStored","Store"}, new String[]{"SaveRecord","Store"}, new String[]{"EraseRecord","Erase"})) {
            Files.writeString(models.resolve(pair[0]+"Request.java"), "package example.rules.model; public class "+pair[0]+"Request extends "+pair[1]+"Request { public "+pair[0]+"Request(Stored value) { super(value); } }");
            Files.writeString(models.resolve(pair[0]+"Response.java"), "package example.rules.model; public class "+pair[0]+"Response { }");
        }
        Path classes=Files.createDirectory(root.resolve("classes"));
        List<String> args=new ArrayList<>(List.of("-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var paths=Files.walk(root.resolve("java"))) { paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString())); }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader())) {
            Class<?> model=loader.loadClass("example.rules.model.Stored");
            Object participant=model.getConstructor().newInstance();
            model.getMethod("setCode",String.class).invoke(participant,"a");
            model.getMethod("setValue",Long.class).invoke(participant,20L);
            Object service=loader.loadClass("example.rules.service.SimpleStore").getConstructor().newInstance();
            List<Object> writes=new ArrayList<>();
            List<Object> deletes=new ArrayList<>();
            ObjectRepository repository=new ObjectRepository() {
                public void verifyWriteTransaction() { }
                public void delete(String type,String id) { deletes.add(List.of(type,id)); }
                public void save(String type,String id,Object value) { throw new AssertionError("unexpected upsert"); }
                public Optional<?> find(String type,String id) { return Optional.empty(); }
                public <T> T hydrate(String type,String id,T target) { return target; }
                public void updateFromRead(String type,String id,Object value) {
                    assertSame("The service must persist its original participant instance", participant, value);
                    String revision=RepositoryParticipantSnapshots.requireRevision(value,this,type,id);
                    Object baseline=RepositoryParticipantSnapshots.require(value);
                    try {
                        writes.add(List.of(type,id,revision,model.getMethod("getValue").invoke(baseline),
                                model.getMethod("getValue").invoke(value)));
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                    RepositoryParticipantSnapshots.advanced(value,this,type,id,"r"+writes.size());
                }
            };
            new GeneratedServiceRuntime(null,repository,null).decorate((Service)service);
            Object erase=loader.loadClass("example.rules.service.SimpleErase").getConstructor().newInstance();
            new GeneratedServiceRuntime(null,repository,null).decorate((Service)erase);
            var container = com.servpp.service.server.DefaultServiceContainer.getInstance();
            var queryType = loader.loadClass("example.rules.handler.LoadStored");
            Service load = (Service) java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{queryType},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getName")) return "loadStored";
                        if (method.getName().equals("loadStored")) RepositoryParticipantSnapshots.loaded(arguments[0], repository,
                                new ObjectSnapshot("Stored","a","{}",null,"r0"));
                        return null;
                    });
            Service previousLoad = container.registerService("loadStored", load);
            Service previousStore = container.registerService("store", (Service)service);
            Service previousErase = container.registerService("erase", (Service)erase);
            Service saveRecord = (Service)loader.loadClass("example.rules.service.SimpleSaveRecord").getConstructor().newInstance();
            Service eraseRecord = (Service)loader.loadClass("example.rules.service.SimpleEraseRecord").getConstructor().newInstance();
            var previousSnapshots=RepositoryParticipantSnapshots.checkpoint();
            try {
                Object request=loader.loadClass("example.rules.model.SaveRecordRequest").getConstructor(model).newInstance(participant);
                com.servpp.service.runtime.RemoteServiceIngress.execute(saveRecord, request, "fixture", "save");
                Object eraseRequest=loader.loadClass("example.rules.model.EraseRecordRequest").getConstructor(model).newInstance(participant);
                com.servpp.service.runtime.RemoteServiceIngress.execute(eraseRecord, eraseRequest, "fixture", "erase");
                com.servpp.service.runtime.RemoteServiceIngress.execute(eraseRecord, eraseRequest, "fixture", "erase");
                assertEquals("Local calls are not deduplicated",List.of(List.of("Stored","a"),List.of("Stored","a")),deletes);
                assertEquals(List.of(List.of("Stored","a","r0",20L,30L),List.of("Stored","a","r1",30L,40L)),writes);
                assertEquals(40L,model.getMethod("getValue").invoke(participant));
            } finally { RepositoryParticipantSnapshots.restore(previousSnapshots); ServiceRegistryFixture.restore("loadStored", previousLoad); ServiceRegistryFixture.restore("store", previousStore); ServiceRegistryFixture.restore("erase", previousErase); }

        }
    }
}
