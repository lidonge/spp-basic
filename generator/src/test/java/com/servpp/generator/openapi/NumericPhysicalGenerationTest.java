package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.lang.AppCompiler;
import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import java.math.BigDecimal;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class NumericPhysicalGenerationTest {
    private static final String BUSINESS = """
            domain commerce;
            Role Amount { double a; double b; int c; }
            atomic calculate calculateAmount(Amount amount);
            """;

    @Test
    public void physicalBindingGeneratesCompilableExactArithmeticWithoutChangingBusinessTypes() throws Exception {
        SppDomain domain = compile(BUSINESS, "amount.a = amount.b * amount.c + 5;", """
                numeric Amount.a as BigDecimal;
                numeric Amount.b as java.math.BigDecimal;
                """);
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        SppClass amount = (SppClass) domain.getMapsOfClass().get("Amount");
        assertEquals("double", amount.getField("b").getType().getName());
        assertEquals("java.math.BigDecimal", amount.getField("b").getJavaType());
        String statement = service(domain).getJavaStatements().get(0).getJavaStatement();
        assertTrue(statement, statement.contains(".multiply("));
        assertTrue(statement, statement.contains(".add("));
        assertEquals(new BigDecimal("24691357802469135785.2468"),
                execute(statement, "java.math.BigDecimal", "java.math.BigDecimal",
                        new BigDecimal("12345678901234567890.1234"), 2));
    }

    @Test
    public void comparisonsCompoundAssignmentAndParenthesesUseNumericSemantics() throws Exception {
        SppDomain domain = compile(BUSINESS,
                "amount.a += -(amount.b * amount.c) + 5;", "numeric double as BigDecimal;");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertEquals(new BigDecimal("0.50"), execute(service(domain).getJavaStatements().get(0).getJavaStatement(),
                "java.math.BigDecimal", "java.math.BigDecimal", new BigDecimal("2.25"), 2));
        SppDomain comparison = compile(BUSINESS.replace("atomic calculate", "atomic check"),
                "require(amount.b == 2.250, \"different numeric value\");", "numeric double as BigDecimal;");
        assertTrue(comparison.validateAppDeclarations().toString(), comparison.validateAppDeclarations().isEmpty());
        String statement = service(comparison).getJavaStatements().get(0).getJavaStatement();
        assertTrue(statement, statement.contains("compareTo"));
        execute(statement, "java.math.BigDecimal", "java.math.BigDecimal", new BigDecimal("2.25"), 2);
    }

    @Test
    public void defaultIntegerRepresentationRejectsOverflowAndFractionalResults() throws Exception {
        String business = BUSINESS.replace("double a", "int a").replace("double b", "int b");
        SppDomain overflow = compile(business, "amount.a = amount.b * amount.c + 5;", "");
        assertTrue(overflow.validateAppDeclarations().isEmpty());
        Throwable failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> execute(service(overflow).getJavaStatements().get(0).getJavaStatement(),
                        "int", "int", Integer.MAX_VALUE, 2)).getCause();
        assertTrue(failure instanceof ArithmeticException);
        SppDomain fraction = compile(business, "amount.a = amount.b / amount.c;", "");
        assertTrue(fraction.validateAppDeclarations().isEmpty());
        assertTrue(assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> execute(service(fraction).getJavaStatements().get(0).getJavaStatement(),
                        "int", "int", 1, 2)).getCause() instanceof ArithmeticException);
    }

    @Test
    public void exactDivisionReportsInsufficientRepresentationInsteadOfRounding() throws Exception {
        SppDomain domain = compile(BUSINESS, "amount.a = amount.b / amount.c;", "numeric double as BigDecimal;");
        assertTrue(domain.validateAppDeclarations().isEmpty());
        String statement = service(domain).getJavaStatements().get(0).getJavaStatement();
        assertEquals(new BigDecimal("0.5"), execute(statement, "java.math.BigDecimal", "java.math.BigDecimal", BigDecimal.ONE, 2));
        assertTrue(assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> execute(statement, "java.math.BigDecimal", "java.math.BigDecimal", BigDecimal.ONE, 3))
                .getCause() instanceof ArithmeticException);
    }

    @Test
    public void fieldBindingsOverrideTypeDefaultsRegardlessOfDeclarationOrder() throws Exception {
        SppDomain domain = compile(BUSINESS, "amount.a = amount.b * amount.c;",
                "numeric { override Amount.a with long; replace double with java.math.BigDecimal; }");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        assertEquals(12L, execute(service(domain).getJavaStatements().get(0).getJavaStatement(),
                "long", "java.math.BigDecimal", new BigDecimal("6"), 2));
    }

    @Test
    public void defaultFloatingCalculationUsesExactDecimalIntermediateValues() throws Exception {
        SppDomain domain = compile(BUSINESS, "amount.a = amount.b + 0.2;", "");
        assertTrue(domain.validateAppDeclarations().isEmpty());
        assertEquals(0.3d, execute(service(domain).getJavaStatements().get(0).getJavaStatement(),
                "double", "double", 0.1d, 1));
    }

    @Test
    public void inheritedRoleFieldsCanHaveIndependentPhysicalBindings() throws Exception {
        String business = """
                domain commerce;
                Entity Product { double price; }
                Role Goods { }
                take Product as Goods { takeall; mapsame; }
                atomic calculate calculateAmount(Goods amount);
                """;
        SppDomain domain = compile(business, "amount.price = amount.price + 5;", "numeric Goods.price as BigDecimal;");
        // Production generation resolves inherited fields before APP parsing.
        domain.dealEntityToRoleMaps();
        assertTrue(domain.validatePhysicalRuntimeDeclarations().toString(), domain.validatePhysicalRuntimeDeclarations().isEmpty());
        SppClass product = (SppClass) domain.getMapsOfClass().get("Product");
        SppClass goods = (SppClass) domain.getMapsOfClass().get("Goods");
        assertEquals("double", product.getField("price").getJavaType());
        assertEquals("java.math.BigDecimal", goods.getField("price").getJavaType());
        var mapper = (com.servpp.lang.models.SppRoleMapper) domain.getMapsOfClass().values().stream()
                .filter(unit -> unit instanceof com.servpp.lang.models.SppRoleMapper).findFirst().orElseThrow();
        Path output = Files.createTempDirectory("spp-numeric-role-mapper");
        new MustacheClassWriter(output.toFile(), mapper, "example", "commerce").generate();
        Path generated;
        try (var paths = Files.walk(output)) {
            generated = paths.filter(path -> path.toString().endsWith(mapper.getName() + ".java")).findFirst().orElseThrow();
        }
        String java = Files.readString(generated);
        assertTrue(java, java.contains("ExactNumbers.decimal"));
        assertTrue(java, java.contains("ExactNumbers.doubleExact"));
    }

    @Test
    public void bindingsRejectMissingNonNumericDuplicateAndUnsupportedTargets() throws Exception {
        for (String binding : List.of("numeric Amount.missing as BigDecimal;", "numeric Amount as BigDecimal;",
                "numeric Amount.b as Unknown;", "numeric Amount.b as BigDecimal; numeric Amount.b as double;")) {
            SppDomain domain = compile(BUSINESS, "amount.a = amount.b;", binding);
            assertFalse(binding, domain.validatePhysicalRuntimeDeclarations().isEmpty());
        }
        SppDomain nonNumeric = compile(BUSINESS.replace("double b", "String b"), "amount.a = 5;", "numeric Amount.b as BigDecimal;");
        assertFalse(nonNumeric.validatePhysicalRuntimeDeclarations().isEmpty());
    }

    @Test
    public void openApiAndPhysicalCatalogAgreeWithGeneratedServiceRepresentation() throws Exception {
        Path source = Files.createTempDirectory("spp-numeric-generator");
        Files.writeString(source.resolve("commerce.spp"), BUSINESS);
        Files.writeString(source.resolve("commerce.app"), app("amount.a = amount.b * amount.c + 5;"));
        Files.writeString(source.resolve("commerce.phy"), "domain commerce; phy { numeric double as BigDecimal; }");
        Path root = Files.createTempDirectory("spp-numeric-artifacts");
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(), "example");
        String yaml = Files.readString(root.resolve("yaml/commerce.spp.yaml"));
        assertTrue(yaml, yaml.contains("type: number"));
        assertFalse(yaml, yaml.contains("format: double"));
        var api = io.swagger.v3.core.util.Yaml.mapper().readValue(yaml, io.swagger.v3.oas.models.OpenAPI.class);
        var codegen = new org.openapitools.codegen.languages.SpringCodegen();
        var model = codegen.fromModel("Amount", api.getComponents().getSchemas().get("Amount"));
        assertEquals("BigDecimal", model.vars.stream().filter(property -> property.name.equals("a")).findFirst().orElseThrow().dataType);
        assertEquals("BigDecimal", model.vars.stream().filter(property -> property.name.equals("b")).findFirst().orElseThrow().dataType);
        String catalog = Files.readString(root.resolve("resources/META-INF/spp/physical.properties"));
        assertTrue(catalog, catalog.contains("numeric.double=java.math.BigDecimal"));
        assertEquals("java.math.BigDecimal", com.servpp.service.runtime.PhysicalRuntimeCatalog.fromProperties(
                new java.io.ByteArrayInputStream(catalog.getBytes(java.nio.charset.StandardCharsets.UTF_8))).numericBindings().get("double"));
        Path implementation;
        try (var paths = Files.walk(root)) {
            implementation = paths.filter(path -> path.getFileName().toString().equals("SimpleCalculateAmount.java"))
                    .findFirst().orElseThrow();
        }
        assertTrue(Files.readString(implementation).contains(".multiply("));
        // Compile and execute the actual generated service with model types checked above.
        Path modelRoot = root.resolve("java/example/commerce/model");
        Files.createDirectories(modelRoot);
        Files.writeString(modelRoot.resolve("Amount.java"), """
                package example.commerce.model;
                public class Amount {
                    private java.math.BigDecimal a, b; private Integer c;
                    public java.math.BigDecimal getA() { return a; }
                    public void setA(java.math.BigDecimal value) { a = value; }
                    public java.math.BigDecimal getB() { return b; }
                    public void setB(java.math.BigDecimal value) { b = value; }
                    public Integer getC() { return c; }
                    public void setC(Integer value) { c = value; }
                }
                """);
        Files.writeString(modelRoot.resolve("CalculateAmountRequest.java"), """
                package example.commerce.model;
                public class CalculateAmountRequest { public Amount getAmount() { return null; } }
                """);
        Files.writeString(modelRoot.resolve("CalculateAmountResponse.java"), """
                package example.commerce.model;
                public class CalculateAmountResponse { }
                """);
        List<String> compilerArguments = new java.util.ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"),
                "-d", root.resolve("classes").toString()));
        try (var paths = Files.walk(root.resolve("java"))) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> compilerArguments.add(path.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, compilerArguments.toArray(String[]::new)));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{root.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> amountClass = loader.loadClass("example.commerce.model.Amount");
            Object amount = amountClass.getConstructor().newInstance();
            amountClass.getMethod("setB", BigDecimal.class).invoke(amount, new BigDecimal("0.1"));
            amountClass.getMethod("setC", Integer.class).invoke(amount, 3);
            Class<?> implementationClass = loader.loadClass("example.commerce.service.SimpleCalculateAmount");
            implementationClass.getMethod("calculateAmount", amountClass).invoke(implementationClass.getConstructor().newInstance(), amount);
            assertEquals(new BigDecimal("5.3"), amountClass.getMethod("getA").invoke(amount));
        }
    }

    private SppService service(SppDomain domain) {
        return (SppService) domain.getMapsOfClass().values().stream()
                .filter(unit -> unit instanceof SppService).findFirst().orElseThrow();
    }

    private SppDomain compile(String business, String statements, String bindings) throws Exception {
        Path source = Files.createTempDirectory("spp-numeric-language");
        Path spp = source.resolve("commerce.spp"), app = source.resolve("commerce.app"), phy = source.resolve("commerce.phy");
        Files.writeString(spp, business);
        Files.writeString(app, app(statements));
        Files.writeString(phy, "domain commerce; phy { " + bindings + " }");
        SppProject project = new SppProject();
        new SppCompiler(spp.toFile(), project).compile();
        project.getSppDomainMap().values().iterator().next().dealEntityToRoleMaps();
        new AppCompiler(app.toFile(), project).compile();
        new AppCompiler(phy.toFile(), project).compile();
        return project.getSppDomainMap().values().iterator().next();
    }

    private String app(String statements) {
        return "domain commerce; scope { local { calculateAmount; } } services { calculateAmount { " + statements + " } }";
    }

    private Object execute(String statement, String aType, String bType, Object b, int c) throws Exception {
        Path output = Files.createTempDirectory("spp-numeric-javac");
        Path java = output.resolve("Fixture.java");
        Files.writeString(java, """
                public class Fixture {
                    public static class Amount {
                        private %s a; private %s b; private int c;
                        public %s getA() { return a; } public void setA(%s value) { a = value; }
                        public %s getB() { return b; } public int getC() { return c; }
                    }
                    private static void apply(Amount amount) {
                        %s
                    }
                    public static Object execute(Object b, int c) {
                        Amount amount = new Amount(); %s amount.b = (%s) b; amount.c = c;
                        apply(amount);
                        return amount.a;
                    }
                }
                """.formatted(aType, bType, aType, aType, bType, statement, "java.math.BigDecimal".equals(aType) ? "amount.a = java.math.BigDecimal.ZERO;" : "", bType));
        assertEquals("Generated Java must compile", 0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", System.getProperty("java.class.path"), "-d", output.toString(), java.toString()));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
            return loader.loadClass("Fixture").getMethod("execute", Object.class, int.class).invoke(null, b, c);
        }
    }
}
