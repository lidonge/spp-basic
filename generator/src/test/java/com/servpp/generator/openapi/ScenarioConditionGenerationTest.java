package com.servpp.generator.openapi;

import com.servpp.lang.SppCompiler;
import com.servpp.lang.models.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScenarioConditionGenerationTest {
    @Test public void generatedBranchesSkipUnselectedTransactionsAndKeepFailureRecovery() throws Exception {
        Path root = Files.createTempDirectory("spp-scenario-compiled");
        Files.writeString(root.resolve("rules.spp"), """
                domain rules;
                Contract Values { boolean allowed; double amount; boolean fail; }
                atomic update write(Values v);
                atomic calculate pending(Values v) { }
                atomic calculate rejected(Values v) { }
                Scenario serial save(Values v) {
                    if (v.allowed && v.amount > 0) {
                        transaction { write(v); }
                    } else if (!v.allowed) { pending(v); }
                    else { rejected(v); }
                }
                """);
        SppProject project = new SppProject(); new SppCompiler(root.resolve("rules.spp").toFile(), project).compile();
        SppDomain domain = project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        String body = ((SppService)domain.getSppClass("Save")).getScenarioBodyJava();
        Files.writeString(root.resolve("Fixture.java"), """
                import com.servpp.service.*;
                import com.servpp.service.scenario.*;
                import com.servpp.service.transaction.*;
                import java.util.*;
                public class Fixture {
                    private static final List<String> events = new ArrayList<>();
                    private static final Action write = new Action("write"), pending = new Action("pending"), rejected = new Action("rejected");
                    public static class Values {
                        boolean allowed, fail; double amount;
                        public Boolean getAllowed() { return allowed; }
                        public Double getAmount() { return amount; }
                    }
                    public static class Action implements AtomicService {
                        final String name;
                        Action(String name) { this.name=name; }
                        public String getName() { return name; }
                        public Object execute(Object... args) { return null; }
                        public void pending(Values v) { events.add("pending"); }
                        public void rejected(Values v) { events.add("rejected"); }
                        public void write(Values v) { events.add("write"); if(v.fail)throw new IllegalStateException("injected"); }
                        public TransactionParticipant getTransactionParticipant() {
                            return new TransactionParticipant() {
                                public String getResourceId() { return name; }
                                public void begin(TransactionContext c) { events.add("begin"); }
                                public void commit(TransactionContext c) { events.add("commit"); }
                                public void rollback(TransactionContext c,Throwable e) { events.add("rollback"); }
                            };
                        }
                    }
                    public static List<String> run(boolean allowed,double amount,boolean fail) {
                        events.clear(); Values v=new Values();v.allowed=allowed;v.amount=amount;v.fail=fail;
                        %s
                        return List.copyOf(events);
                    }
                    public static List<String> events() { return List.copyOf(events); }
                }
                """.formatted(body));
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertEquals("Generated branch and transaction code must compile", 0,
                ToolProvider.getSystemJavaCompiler().run(null,null,null,"-classpath",System.getProperty("java.class.path"),
                        "-d",classes.toString(),root.resolve("Fixture.java").toString()));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader())) {
            Class<?> fixture = loader.loadClass("Fixture");
            var run = fixture.getMethod("run",boolean.class,double.class,boolean.class);
            assertEquals(List.of("begin","write","commit"),run.invoke(null,true,10d,false));
            assertEquals(List.of("pending"),run.invoke(null,false,10d,false));
            assertEquals(List.of("rejected"),run.invoke(null,true,0d,false));
            var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> run.invoke(null,true,10d,true));
            assertEquals("injected",failure.getCause().getMessage());
            assertEquals(List.of("begin","write","rollback"),fixture.getMethod("events").invoke(null));
        } finally { com.servpp.service.transaction.DefaultTransactionManager.clearLastContext(); }
    }
}
