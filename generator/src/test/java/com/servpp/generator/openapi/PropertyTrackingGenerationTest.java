package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.generator.GeneratorUtil;
import com.servpp.service.repository.*;
import com.servpp.service.runtime.*;
import com.servpp.service.transaction.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import static org.junit.Assert.*;

public class PropertyTrackingGenerationTest {
    @Test public void defaultPolicyWithoutProviderGeneratesPlainModels() throws Exception { verify(null); }
    @Test public void explicitOffModelsCompileAndKeepFullUpdateBehavior() throws Exception { verify(false); }
    @Test public void requestedTrackingWithoutProviderUsesBasicFullUpdates() throws Exception { verify(true); }
    private void verify(Boolean enabled) throws Exception {
        Path root=Files.createTempDirectory("spp-property-tracking"), source=Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("shop.spp"),"""
                domain shop;
                Entity Account { String accountNumber; String name; String note; boolean active; String[] tags; }
                Role Holder { String label; boolean active; }
                take Account as Holder { String label = name; boolean active = active; }
                """);
        Files.writeString(source.resolve("shop.app"),"domain shop; " + (enabled==null?"":"propertyTracking: "+enabled+"; ") + """
                tables entities { table ACCOUNTS(Account) { primarykey(Account.accountNumber); } }
                """);
        GeneratorUtil.execute(source.toFile(),root.resolve("yaml").toFile(),root.resolve("java").toFile(),"generated");
        Properties properties=new Properties();try(var in=Files.newInputStream(root.resolve("resources/META-INF/spp/app-ddl.properties"))){properties.load(in);}
        boolean tracking=false;
        assertFalse(PropertyChanges.isAvailable());
        assertEquals(tracking,AppDdlCatalog.fromProperties(properties).propertyTracking());
        var config=new CodegenConfigurator().setGeneratorName("spring").setInputSpec(root.resolve("yaml/shop.spp.yaml").toString())
                .setOutputDir(root.resolve("client").toString()).setModelPackage("generated.shop.model")
                .setTemplateDir(root.resolve("yaml/JavaSpring").toString()).setValidateSpec(false)
                .addAdditionalProperty("useSpringBoot3",true).addAdditionalProperty("useBeanValidation",false)
                .addAdditionalProperty("annotationLibrary","none").addAdditionalProperty("documentationProvider","none")
                .addAdditionalProperty("openApiNullable",false).addAdditionalProperty("hideGenerationTimestamp",true)
                .setGlobalProperties(Map.of("models","","modelTests","false","modelDocs","false"));
        new DefaultGenerator().opts(config.toClientOptInput()).generate();
        Path classes=Files.createDirectory(root.resolve("classes"));
        List<String> args=new ArrayList<>(List.of("-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var files=Files.walk(root.resolve("client/src/main/java"))){files.filter(path->path.toString().endsWith(".java")).forEach(path->args.add(path.toString()));}
        // Compile the real generated mapper too; renamed role fields must preserve write intent.
        try(var files=Files.walk(root.resolve("java/generated/shop/model"))){files.filter(path->path.toString().endsWith(".java")).forEach(path->args.add(path.toString()));}
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader())) {
            Class<?> type=loader.loadClass("generated.shop.model.Account");
            assertEquals(tracking,ChangeTrackedParticipant.class.isAssignableFrom(type));
            var mapper=new ObjectMapper(); var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
            var catalog=AppDdlCatalog.fromProperties(properties);new JdbcAppDdlSchemaManager(ds).ensureTables(catalog);
            var repository=new JdbcObjectRepository(new JdbcObjectSnapshotStore(ds,value->mapper.valueToTree(value).toString()),mapper).useAppDdlCatalog(catalog);
            Object account=type.getConstructor().newInstance();
            type.getMethod("setAccountNumber",String.class).invoke(account,"A");type.getMethod("name",String.class).invoke(account,"old");
            type.getMethod("setNote",String.class).invoke(account,"memo");type.getMethod("setActive",Boolean.class).invoke(account,true);
            transaction(repository,()->repository.insert("Account","A",account));
            if(tracking) assertTrue(PropertyChanges.paths(account).toString(),PropertyChanges.paths(account).isEmpty());
            Object loaded=repository.require("Account","A",type);
            if(tracking) assertTrue(PropertyChanges.paths(loaded).toString(),PropertyChanges.paths(loaded).isEmpty());
            type.getMethod("name",String.class).invoke(loaded,"new");
            type.getMethod("setNote",String.class).invoke(loaded,new Object[]{null});
            type.getMethod("addTagsItem",String.class).invoke(loaded,"one");
            transaction(repository,()->repository.update("Account","A",loaded));
            Object current=repository.require("Account","A",type);
            assertEquals("new",type.getMethod("getName").invoke(current));
            assertNull(type.getMethod("getNote").invoke(current));
            assertEquals(List.of("one"),type.getMethod("getTags").invoke(current));
            assertFalse(mapper.writeValueAsString(current).contains("__sppChanges"));
            assertFalse(repository.find("Account","A").orElseThrow().payloadJson().contains("__sppChanges"));
            if(tracking) {
                Class<?> role=loader.loadClass("generated.shop.model.Holder");
                Class<?> mapping=loader.loadClass("generated.shop.model.MapperAccountToHolder");
                Object holder=role.getConstructor().newInstance(), bridge=mapping.getConstructor().newInstance();
                Map<String,Object> actors=Map.of("Account",current);
                mapping.getMethod("entityToRole",Map.class,role).invoke(bridge,actors,holder);
                assertTrue(PropertyChanges.paths(holder).toString(),PropertyChanges.paths(holder).isEmpty());
                role.getMethod("setLabel",String.class).invoke(holder,new Object[]{null});
                mapping.getMethod("roleToEntity",role,Map.class).invoke(bridge,holder,actors);
                assertTrue(PropertyChanges.paths(current).contains("name"));
                assertFalse(PropertyChanges.paths(current).contains("active"));
                transaction(repository,()->repository.update("Account","A",current));
                assertNull(type.getMethod("getName").invoke(repository.require("Account","A",type)));
            }
        }
    }
    private static void transaction(JdbcObjectRepository repository,Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.LOCAL,()->repository.executeInTransaction(action));
    }
}
