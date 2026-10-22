package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import com.servpp.lang.*;
import com.servpp.lang.models.*;
import com.servpp.service.numeric.NumericAdapter;
import java.math.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class BusinessCalculationGenerationTest {
    @Test public void participantSnapshotsCopyObjectsAndArrayElementsWithoutAliasing() throws Exception {
        Path source = Files.createTempFile("spp-copy", ".spp");
        Files.writeString(source, """
                domain rules;
                Role Item { double amount; }
                Role Batch { Item entry; Item[] items; }
                atomic calculate freeze(Batch original, Batch target, Item[] items) {
                    target.entry = copy(original.entry);
                    target.items = copy(items);
                }
                """);
        SppProject project=new SppProject();new SppCompiler(source.toFile(),project).compile();
        SppDomain domain=project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        SppService service=(SppService)domain.getSppClass("Freeze");
        String body=service.getPhysicalJavaStatements().stream().map(statement -> statement.getJavaStatement())
                .collect(java.util.stream.Collectors.joining("\n"));
        Path root=Files.createTempDirectory("spp-copy-compiled");
        Path java=root.resolve("Fixture.java");
        Files.writeString(java, """
                import java.util.*;
                public class Fixture {
                    public static class Item { public Double amount; public Double getAmount(){return amount;} }
                    public static class Batch {
                        public Item entry; public List<Item> items;
                        public Item getEntry(){return entry;} public void setEntry(Item value){entry=value;}
                        public List<Item> getItems(){return items;} public void setItems(List<Item> value){items=value;}
                    }
                    public static double run() {
                        Item item=new Item();item.amount=10d;
                        Batch original=new Batch();original.entry=item;Batch target=new Batch();List<Item> items=new ArrayList<>(List.of(item));
                        %s
                        item.amount=99d;
                        return target.entry.amount + target.items.get(0).amount;
                    }
                }
                """.formatted(body));
        compileJava(root,List.of(java.toString()));
        try(URLClassLoader loader=loader(root)) { assertEquals(20d,loader.loadClass("Fixture").getMethod("run").invoke(null)); }
    }

    private static final String ROLE = """
            domain rules;
            Role Values {
                double a; double b; double c; double total;
                boolean allowed; boolean exempt; String label;
            }
            """;
    private static final String FRACTION = BusinessCalculationGenerationTest.class.getName() + ".Fraction";
    private static final String ADAPTER = BusinessCalculationGenerationTest.class.getName() + ".FractionAdapter";

    @Test public void javaControlStatementsPreserveFallthroughAndLoopUpdates() throws Exception {
        SppDomain domain = compile("""
                v.a = 0;
                for (int i = 0; i < 5; i++) {
                    if (i == 1) continue;
                    switch (i) {
                        case 0: v.a += 1;
                        case 2: v.a += 10; break;
                        default: v.a += 100;
                    }
                }
                int n = 0;
                while (n < 3) { ++n; if (n == 2) break; v.a += 1; }
                if (false) v.a = 0; else if (true) v.allowed = true; else v.allowed = false;
                """, "numeric double as BigDecimal; numeric int as BigInteger;");
        Object[] result = execute(domain, "java.math.BigDecimal", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(new BigDecimal("222"), result[0]); assertEquals(true, result[1]);
    }

    @Test public void switchEvaluatesOnceAndDefaultsFallThrough() throws Exception {
        SppDomain domain = compile("""
                v.a = 0;
                switch (v.b) {
                    case 1: v.b = 2; v.a += 10;
                    default: v.a += 100;
                    case 2: v.a += 1000; break;
                }
                switch (v.label) { case "new": v.allowed = true; break; default: v.allowed = false; }
                """, "numeric double as BigDecimal;");
        assertEquals(new BigDecimal("1110"), execute(domain, "java.math.BigDecimal", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)[0]);
        assertEquals(new BigDecimal("1100"), execute(domain, "java.math.BigDecimal", new BigDecimal("9"), BigDecimal.ZERO, BigDecimal.ZERO)[0]);
        assertEquals(new BigDecimal("1000"), execute(domain, "java.math.BigDecimal", new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO)[0]);
    }

    @Test public void customNumericLocalsAndSwitchNeverRequireDecimalBridge() throws Exception {
        SppDomain domain = compile("""
                double fraction = v.b / v.c;
                v.a = 0;
                for (double i = 0; i < 3; ++i) { v.a += fraction; }
                switch (v.a) { case 1: v.allowed = true; break; default: v.allowed = false; }
                """, customBinding());
        Object[] result = execute(domain, FRACTION, new Fraction(BigInteger.ONE, BigInteger.ONE),
                new Fraction(BigInteger.valueOf(3), BigInteger.ONE), new Fraction(BigInteger.ZERO, BigInteger.ONE));
        assertEquals(new Fraction(BigInteger.ONE, BigInteger.ONE), result[0]); assertEquals(true, result[1]);
    }

    @Test public void enhancedForExecutesParticipantCollectionInBusinessBody() throws Exception {
        Path source = Files.createTempFile("spp-foreach", ".spp");
        Files.writeString(source, ROLE + "atomic calculate decide(Values v, Values[] items) { v.a = 0; for (Values item : items) { if (item.b < 0) continue; v.a += item.b; } }");
        SppProject project = new SppProject(); new SppCompiler(source.toFile(), project).compile();
        SppDomain domain = project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        Path root = Files.createTempDirectory("spp-foreach-java"); Path java = root.resolve("Fixture.java");
        Files.writeString(java, "public class Fixture { " + modelSource("double", "double").replace("public class Values", "public static class Values")
                + " public static double run() { Values v = new Values(); Values p = new Values(); p.b=2; Values q = new Values(); q.b=-3; Values[] items={p,q,p}; "
                + statements(domain) + " return v.a; } }");
        compileJava(root, List.of(java.toString()));
        try (URLClassLoader loader = loader(root)) { assertEquals(4d, loader.loadClass("Fixture").getMethod("run").invoke(null)); }
    }

    @Test public void javaForFormsAndDanglingElseExecute() throws Exception {
        SppDomain domain = compile("""
                v.a = 0;
                for (int i=0, j=4; i<j; i++, j--) v.a += i+j;
                int x=0, y=0;
                for (x=0,y=0; x<2; ++x,++y) v.a += 1;
                for (;;) { v.a += 1; break; }
                if (true) if (false) v.a=0; else v.a+=10;
                switch (v.c) { case -1: v.a+=100; break; default: ; }
                v.allowed = x==2 && y==2;
                """, "numeric double as BigDecimal;");
        Object[] result = execute(domain, "java.math.BigDecimal", BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("121"), result[0]); assertEquals(true, result[1]);
    }

    @Test public void foreachNumericElementsUsePhysicalConversions() throws Exception {
        Path source = Files.createTempFile("spp-numeric-foreach", ".spp");
        Files.writeString(source, ROLE + "Role Numbers { double[] items; } atomic calculate decide(Values v, Numbers numbers) { v.a=0; for (short item : numbers.items) v.a+=item; }");
        SppProject project = new SppProject(); new SppCompiler(source.toFile(), project).compile();
        Path phy = Files.createTempFile("spp-numeric-foreach", ".phy");
        Files.writeString(phy, "domain rules; phy { numeric double as BigDecimal; }"); new AppCompiler(phy.toFile(), project).compile();
        SppDomain domain = project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        Path root = Files.createTempDirectory("spp-numeric-foreach-java"); Path java = root.resolve("Fixture.java");
        Files.writeString(java, "public class Fixture { " + modelSource("java.math.BigDecimal", "java.math.BigDecimal").replace("public class Values", "public static class Values")
                + " public static class Numbers { private java.util.List<java.math.BigDecimal> items; public java.util.List<java.math.BigDecimal> getItems() { return items; } }"
                + " public static java.math.BigDecimal run(java.math.BigDecimal[] items) { Values v = new Values(); Numbers numbers = new Numbers(); numbers.items = java.util.Arrays.asList(items); " + statements(domain) + " return v.a; } }");
        compileJava(root, List.of(java.toString()));
        try (URLClassLoader loader = loader(root)) {
            var method = loader.loadClass("Fixture").getMethod("run", BigDecimal[].class);
            assertEquals(new BigDecimal("5"), method.invoke(null, (Object)new BigDecimal[]{new BigDecimal("2"), new BigDecimal("3")}));
            assertTrue(assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> method.invoke(null, (Object)new BigDecimal[]{new BigDecimal("32768")})).getCause() instanceof ArithmeticException);
        }
    }

    @Test public void bocDenominationAndTotalRulesExecuteAsBusinessExpressions() throws Exception {
        // MBD820 CHECK-INPUT: total equals summed detail amounts; CHECK-DENOM-MULTIPLE: REM = 0.
        SppDomain domain = compile("""
                v.a = v.b + v.c;
                v.allowed = v.a == v.total && v.b % v.c == 0;
                if (v.allowed) { v.a += 100; } else { v.label = "invalid"; }
                """, "numeric double as BigDecimal;");
        Object[] ok = execute(domain, "java.math.BigDecimal", new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("150"));
        assertEquals(new BigDecimal("250"), ok[0]); assertEquals(true, ok[1]);
        Object[] invalid = execute(domain, "java.math.BigDecimal", new BigDecimal("101"), new BigDecimal("50"), new BigDecimal("151"));
        assertEquals(new BigDecimal("151"), invalid[0]); assertEquals(false, invalid[1]); assertEquals("invalid", invalid[2]);
    }

    @Test public void nestedRulesShortCircuitAndConditionalValuesExecute() throws Exception {
        SppDomain domain = compile("""
                v.allowed = v.c != 0 && v.b / v.c > 0;
                v.a = v.c == 0 ? 5 : v.b / v.c;
                if (!v.allowed) {
                    if (v.label == "new") { v.a += 1; } else { v.a += 2; }
                } else { v.a = 100; }
                v.label = v.allowed ? "accepted" : "skipped";
                v.exempt = v.a > 0 || v.b / v.c > 0;
                """, "");
        Object[] result = execute(domain, "double", 1d, 0d, 0d);
        assertEquals(6d, result[0]); assertEquals(false, result[1]); assertEquals("skipped", result[2]);
    }

    @Test public void characterBusinessFlagsAgreeWithStringWireModels() throws Exception {
        Path source = Files.createTempFile("spp-character-rule", ".spp");
        Files.writeString(source, business("v.label = 'Y'; v.allowed = v.label == 'Y';").replace("String label", "char label"));
        SppProject project = new SppProject(); new SppCompiler(source.toFile(), project).compile();
        SppDomain domain = project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        Object[] result = execute(domain, "double", 0d, 0d, 0d);
        assertEquals("Y", result[2]); assertEquals(true, result[1]);
    }

    @Test public void explicitRoundingIncludesDivisionAndNegativeValues() throws Exception {
        String[] expressions = {"roundHalfUp(v.b / v.c, 2)", "roundHalfEven(v.b, 2)", "roundDown(v.b, 2)",
                "roundUp(v.b, 2)", "floor(v.b)", "ceil(v.b)", "max(abs(v.b), min(v.c, 2))"};
        String[] expected = {"-0.33", "-1.24", "-1.23", "-1.24", "-2", "-1", "2"};
        for (int i = 0; i < expressions.length; i++) {
            SppDomain domain = compile("v.a = " + expressions[i] + ";", "numeric double as BigDecimal;");
            Object[] result = execute(domain, "java.math.BigDecimal", i == 0 ? new BigDecimal("-1") : new BigDecimal("-1.235"),
                    new BigDecimal("3"), BigDecimal.ZERO);
            assertEquals(expressions[i], 0, new BigDecimal(expected[i]).compareTo((BigDecimal) result[0]));
        }
        SppDomain exact = compile("v.a = v.b / v.c;", "numeric double as BigDecimal;");
        assertTrue(assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> execute(exact, "java.math.BigDecimal", BigDecimal.ONE, new BigDecimal("3"), BigDecimal.ZERO))
                .getCause() instanceof ArithmeticException);
    }

    @Test public void bigIntegerBindingAvoidsMachineRangeAndRejectsFractionalAssignment() throws Exception {
        SppDomain domain = compile("v.a = v.b * v.c + 5;", "numeric double as BigInteger;");
        BigInteger input = new BigInteger("123456789012345678901234567890");
        assertEquals(input.multiply(BigInteger.valueOf(3)).add(BigInteger.valueOf(5)),
                execute(domain, "java.math.BigInteger", input, BigInteger.valueOf(3), BigInteger.ZERO)[0]);
        SppDomain fraction = compile("v.a = v.b / v.c;", "numeric double as BigInteger;");
        assertTrue(assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> execute(fraction, "java.math.BigInteger", BigInteger.ONE, BigInteger.TWO, BigInteger.ZERO))
                .getCause() instanceof ArithmeticException);
    }

    @Test public void userNumericTypeComputesOneThirdWithoutDecimalIntermediates() throws Exception {
        SppDomain domain = compile("""
                v.a = v.b / v.c;
                v.allowed = v.a * v.c == v.b;
                if (v.allowed) { v.a += 5; }
                """, customBinding());
        Object[] result = execute(domain, FRACTION, new Fraction(BigInteger.ONE, BigInteger.ONE),
                new Fraction(BigInteger.valueOf(3), BigInteger.ONE), new Fraction(BigInteger.ZERO, BigInteger.ONE));
        assertEquals(new Fraction(BigInteger.valueOf(16), BigInteger.valueOf(3)), result[0]);
        assertEquals(true, result[1]);
    }

    @Test public void customAndBuiltinFieldsConvertThroughExplicitPhysicalContract() throws Exception {
        SppDomain domain = compile("v.a = v.b / v.c; v.allowed = v.a == 0.5;",
                "numeric Values.a as " + FRACTION + " using " + ADAPTER + ";");
        String java = statements(domain);
        assertTrue(java, java.contains(".convert("));
        Object[] result = execute(domain, FRACTION, "double", 1d, 2d, 0d);
        assertEquals(new Fraction(BigInteger.ONE, BigInteger.TWO), result[0]); assertEquals(true, result[1]);
    }

    @Test public void completeGeneratedServiceAndOpenApiModelUseCustomPhysicalType() throws Exception {
        Path source = Files.createTempDirectory("spp-business-source");
        Files.writeString(source.resolve("rules.spp"), business("""
                v.a = v.b / v.c; v.allowed = v.a * v.c == v.b;
                if (v.allowed) { v.total = roundHalfUp(v.a, 2); } else { v.total = 0; }
                v.label = v.allowed ? "yes" : "no";
                """));
        Files.writeString(source.resolve("rules.app"), "domain rules; scope { local { decide; } }");
        Files.writeString(source.resolve("rules.phy"), "domain rules; phy { " + customBinding() + " }");
        Path root = Files.createTempDirectory("spp-business-generated");
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), root.resolve("java").toFile(), "example");
        var api = io.swagger.v3.core.util.Yaml.mapper().readValue(Files.readString(root.resolve("yaml/rules.spp.yaml")),
                io.swagger.v3.oas.models.OpenAPI.class);
        var config = io.swagger.v3.core.util.Json.mapper().readTree(root.resolve("yaml/rules.numeric-codegen.json").toFile());
        var codegen = new org.openapitools.codegen.languages.SpringCodegen();
        config.get("typeMappings").fields().forEachRemaining(entry -> codegen.typeMapping().put(entry.getKey(), entry.getValue().asText()));
        config.get("importMappings").fields().forEachRemaining(entry -> codegen.importMapping().put(entry.getKey(), entry.getValue().asText()));
        var model = codegen.fromModel("Values", api.getComponents().getSchemas().get("Values"));
        assertEquals(FRACTION, model.vars.stream().filter(property -> property.name.equals("a")).findFirst().orElseThrow().dataType);
        Path models = root.resolve("java/example/rules/model"); Files.createDirectories(models);
        Files.writeString(models.resolve("Values.java"), "package example.rules.model; " + modelSource(FRACTION, FRACTION));
        Files.writeString(models.resolve("DecideRequest.java"), "package example.rules.model; public class DecideRequest { public Values getV() { return null; } }");
        Files.writeString(models.resolve("DecideResponse.java"), "package example.rules.model; public class DecideResponse { }");
        List<String> args = new ArrayList<>();
        try (var paths = Files.walk(root.resolve("java"))) { paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> args.add(path.toString())); }
        compileJava(root, args);
        try (URLClassLoader loader = loader(root)) {
            Class<?> valuesClass = loader.loadClass("example.rules.model.Values"); Object v = valuesClass.getConstructor().newInstance();
            valuesClass.getMethod("setB", Fraction.class).invoke(v, new Fraction(BigInteger.ONE, BigInteger.ONE));
            valuesClass.getMethod("setC", Fraction.class).invoke(v, new Fraction(BigInteger.valueOf(3), BigInteger.ONE));
            Class<?> service = loader.loadClass("example.rules.service.SimpleDecide");
            service.getMethod("decide", valuesClass).invoke(service.getConstructor().newInstance(), v);
            assertEquals(new Fraction(BigInteger.ONE, BigInteger.valueOf(3)), valuesClass.getMethod("getA").invoke(v));
            assertEquals(true, valuesClass.getMethod("getAllowed").invoke(v));
            assertEquals(new Fraction(BigInteger.valueOf(33), BigInteger.valueOf(100)), valuesClass.getMethod("getTotal").invoke(v));
        }
        var catalog = com.servpp.service.runtime.PhysicalRuntimeCatalog.fromProperties(
                Files.newInputStream(root.resolve("resources/META-INF/spp/physical.properties")));
        assertEquals(FRACTION, catalog.numericBindings().get("double"));
        assertEquals(ADAPTER, catalog.numericAdapters().get("double"));
    }

    private static String customBinding() { return "numeric double as " + FRACTION + " using " + ADAPTER + ";"; }
    private static String business(String body) { return ROLE + "atomic calculate decide(Values v) { " + body + " }"; }
    private SppDomain compile(String body, String binding) throws Exception {
        Path root = Files.createTempDirectory("spp-business-language");
        Files.writeString(root.resolve("rules.spp"), business(body));
        Files.writeString(root.resolve("rules.phy"), "domain rules; phy { " + binding + " }");
        SppProject project = new SppProject(); new SppCompiler(root.resolve("rules.spp").toFile(), project).compile();
        SppDomain domain = project.getSppDomainMap().get("rules"); domain.dealEntityToRoleMaps();
        new AppCompiler(root.resolve("rules.phy").toFile(), project).compile();
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty()); return domain;
    }
    private String statements(SppDomain domain) {
        return ((SppService) domain.getSppClass("Decide")).getJavaStatements().stream()
                .map(statement -> statement.getJavaStatement()).collect(java.util.stream.Collectors.joining("\n"));
    }
    private Object[] execute(SppDomain domain, String type, Object b, Object c, Object total) throws Exception {
        return execute(domain, type, type, b, c, total);
    }
    private Object[] execute(SppDomain domain, String aType, String inputType, Object b, Object c, Object total) throws Exception {
        Path root = Files.createTempDirectory("spp-business-javac");
        Path java = root.resolve("Fixture.java");
        Files.writeString(java, "public class Fixture { " + modelSource(aType, inputType).replace("public class Values", "public static class Values")
                + " public static Object[] run(Object b, Object c, Object total) { Values v = new Values(); v.b = (" + inputType
                + ") b; v.c = (" + inputType + ") c; v.total = (" + inputType + ") total; v.label = new String(\"new\"); "
                + statements(domain) + " return new Object[]{v.a, v.allowed, v.label}; } }");
        compileJava(root, List.of(java.toString()));
        try (URLClassLoader loader = loader(root)) {
            return (Object[]) loader.loadClass("Fixture").getMethod("run", Object.class, Object.class, Object.class).invoke(null, b, c, total);
        }
    }
    private static String modelSource(String aType, String inputType) {
        StringBuilder source = new StringBuilder("public class Values {");
        for (String name : List.of("a", "b", "c", "total", "allowed", "exempt", "label")) {
            String type = name.equals("a") ? aType : List.of("allowed", "exempt").contains(name) ? "boolean" : name.equals("label") ? "String" : inputType;
            String upper = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            source.append("private ").append(type).append(' ').append(name).append("; public ").append(type).append(" get").append(upper)
                    .append("(){return ").append(name).append(";} public void set").append(upper).append('(').append(type)
                    .append(" value){").append(name).append("=value;}");
        }
        return source.append('}').toString();
    }
    private void compileJava(Path root, List<String> sources) {
        List<String> args = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"), "-d", root.resolve("classes").toString()));
        args.addAll(sources);
        assertEquals("Generated Java must compile", 0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    }
    private URLClassLoader loader(Path root) throws Exception {
        return new URLClassLoader(new java.net.URL[]{root.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
    }

    /** A PHY representation deliberately able to retain 1/3 without a decimal codec. */
    public record Fraction(BigInteger numerator, BigInteger denominator) {
        public Fraction {
            if (denominator.signum() == 0) { throw new ArithmeticException("zero denominator"); }
            BigInteger divisor = numerator.gcd(denominator);
            if (denominator.signum() < 0) { divisor = divisor.negate(); }
            numerator = numerator.divide(divisor); denominator = denominator.divide(divisor);
        }
    }
    public static final class FractionAdapter implements NumericAdapter<Fraction> {
        public FractionAdapter() { }
        public Fraction literal(String value) {
            BigDecimal number = new BigDecimal(value);
            return number.scale() >= 0 ? new Fraction(number.unscaledValue(), BigInteger.TEN.pow(number.scale()))
                    : new Fraction(number.toBigIntegerExact(), BigInteger.ONE);
        }
        public Fraction add(Fraction a, Fraction b) { return new Fraction(a.numerator.multiply(b.denominator).add(b.numerator.multiply(a.denominator)), a.denominator.multiply(b.denominator)); }
        public Fraction subtract(Fraction a, Fraction b) { return add(a, negate(b)); }
        public Fraction multiply(Fraction a, Fraction b) { return new Fraction(a.numerator.multiply(b.numerator), a.denominator.multiply(b.denominator)); }
        public Fraction divide(Fraction a, Fraction b) { return new Fraction(a.numerator.multiply(b.denominator), a.denominator.multiply(b.numerator)); }
        public Fraction remainder(Fraction a, Fraction b) { Fraction q = divide(a,b); return subtract(a,multiply(b,new Fraction(q.numerator.divide(q.denominator),BigInteger.ONE))); }
        public Fraction negate(Fraction value) { return new Fraction(value.numerator.negate(), value.denominator); }
        public int compare(Fraction a, Fraction b) { return a.numerator.multiply(b.denominator).compareTo(b.numerator.multiply(a.denominator)); }
        public Fraction round(Fraction value, int places, RoundingMode mode) {
            return literal(new BigDecimal(value.numerator).divide(new BigDecimal(value.denominator), places, mode).toPlainString());
        }
    }
}
