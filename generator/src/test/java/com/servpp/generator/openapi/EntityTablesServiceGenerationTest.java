package com.servpp.generator.openapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.servpp.generator.GeneratorUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.Test;

/** APP {@code tables entities} without a query generates compilable Get/Search services. */
public class EntityTablesServiceGenerationTest {
    @Test
    public void getAndSearchServicesCompileAndDeliverTheMapperResultInTheResponse() throws Exception {
        Path source = Files.createTempDirectory("spp-entity-tables-source");
        Path root = Files.createTempDirectory("spp-entity-tables-generated");
        Files.writeString(source.resolve("shop.spp"), """
                domain shop;

                Entity Item{
                    String code;
                    String name;
                }
                """);
        Files.writeString(source.resolve("shop.app"), """
                domain shop;

                tables entities {
                    table ITEMS(Item) { primarykey(Item.code); }
                }
                """);
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(), "example");

        String get = Files.readString(root.resolve("java/example/shop/service/SimpleGetItem.java"));
        assertFalse(get, get.matches("(?s).*void getItem\\(Item item\\)\\{[^}]*ret\\.set.*"));
        assertTrue(get, get.contains("ret.setItem(  mapperItem.getItem(arg.getItem()) );"));

        Path models = root.resolve("java/example/shop/model");
        Files.createDirectories(models);
        Files.writeString(models.resolve("Item.java"), "package example.shop.model; public class Item { }");
        for (String name : List.of("Get", "Search", "Create", "Update", "Find")) {
            Files.writeString(models.resolve(name + "ItemRequest.java"), """
                    package example.shop.model;
                    public class %sItemRequest {
                        private final Item item;
                        public %sItemRequest(Item item) { this.item = item; }
                        public Item getItem() { return item; }
                    }
                    """.formatted(name, name));
        }
        Files.writeString(models.resolve("GetItemResponse.java"), """
                package example.shop.model;
                public class GetItemResponse {
                    private Item item;
                    public Item getItem() { return item; }
                    public void setItem(Item item) { this.item = item; }
                }
                """);
        Files.writeString(models.resolve("SearchItemResponse.java"), """
                package example.shop.model;
                public class SearchItemResponse {
                    private java.util.List<Item> item;
                    public java.util.List<Item> getItem() { return item; }
                    public void setItem(java.util.List<Item> item) { this.item = item; }
                }
                """);
        for (String name : List.of("Create", "Update", "Find")) {
            Files.writeString(models.resolve(name + "ItemResponse.java"),
                    "package example.shop.model; public class " + name + "ItemResponse { }");
        }
        Path classes = Files.createDirectory(root.resolve("classes"));
        List<String> args = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        try (var paths = Files.walk(root.resolve("java"))) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));

        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},
                getClass().getClassLoader())) {
            Class<?> item = loader.loadClass("example.shop.model.Item");
            Object stored = item.getConstructor().newInstance();
            Class<?> mapperType = loader.loadClass("example.shop.mapper.ItemMapper");
            Object mapper = Proxy.newProxyInstance(loader, new Class<?>[]{mapperType}, (proxy, method, arguments) ->
                    switch (method.getName()) {
                        case "getItem" -> stored;
                        case "searchItem" -> List.of(stored);
                        default -> null;
                    });
            for (String name : List.of("Get", "Search")) {
                Object service = loader.loadClass("example.shop.service.Simple" + name + "Item")
                        .getConstructor().newInstance();
                Field field = service.getClass().getDeclaredField("mapperItem");
                field.setAccessible(true);
                field.set(service, mapper);
                Object request = loader.loadClass("example.shop.model." + name + "ItemRequest")
                        .getConstructor(item).newInstance(item.getConstructor().newInstance());
                Object response = invokeAtomic(service, request);
                Object delivered = response.getClass().getMethod("getItem").invoke(response);
                if (name.equals("Get")) {
                    assertSame(stored, delivered);
                } else {
                    assertEquals(List.of(stored), delivered);
                }
            }
        }
    }

    /** The generated atomic service only runs inside a scenario's call stack. */
    @SuppressWarnings("unchecked")
    private static Object invokeAtomic(Object service, Object request) throws Exception {
        java.lang.reflect.Method execute = service.getClass().getMethod("execute", Object[].class);
        java.lang.reflect.Method inService = com.servpp.service.runtime.GeneratedServiceEntry.class
                .getDeclaredMethod("inService", java.util.function.Supplier.class);
        inService.setAccessible(true);
        java.util.function.Supplier<Object> action = () -> {
            try {
                return execute.invoke(service, (Object) new Object[]{request});
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        };
        return inService.invoke(null, action);
    }
}
