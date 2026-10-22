package com.servpp.generator.openapi;
import com.servpp.lang.*;
import com.servpp.lang.models.*;
import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScenarioFailureGenerationTest {
    @Test public void rollbackPrecedesTechnicalFailureHandlingAndPreservesOriginal() throws Exception {
        Path root=Files.createTempDirectory("scenario-failure-java");Path source=root.resolve("rules.spp");
        Files.writeString(source,"""
            domain rules;
            Contract Values { int amount; }
            atomic update write(Values values);
            Scenario serial run(Values values) {
                transaction { write(values); }
            }
            """);
        var project=new SppProject();new SppCompiler(source.toFile(),project).compile();var domain=project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        String body=((SppService)domain.getSppClass("Run")).getScenarioBodyJava();
        Files.writeString(root.resolve("Fixture.java"),"""
            import com.servpp.service.*;
            import com.servpp.service.scenario.*;
            import com.servpp.service.transaction.*;
            import java.util.*;
            public class Fixture {
                public static class Values { }
                private static RuntimeException original;
                private static final RuntimeException secondary=new IllegalStateException("rollback failed");
                private static final List<String> events=new ArrayList<>();
                private static int writes; private static boolean rollbackFails;
                public static class Action implements AtomicService {
                    final String name;Action(String name){this.name=name;}
                    public String getName(){return name;}public Object execute(Object... args){return null;}
                    public void write(Values values){events.add("write");writes++;throw original;}
                    public TransactionParticipant getTransactionParticipant(){return new TransactionParticipant(){
                        int before;
                        public String getResourceId(){return name;}
                        public void begin(TransactionContext context){events.add(name+" begin");before=writes;}
                        public void commit(TransactionContext context){events.add(name+" commit");}
                        public void rollback(TransactionContext context,Throwable failure){events.add(name+" rollback");writes=before;if(rollbackFails)throw secondary;}
                    };}
                }
                private static final Action write=new Action("write");
                public static List<String> run(boolean fail){Values values=new Values();writes=0;events.clear();rollbackFails=fail;original=new IllegalStateException("original");
                    try { %s } catch(RuntimeException failure){events.add("technical handler");if(failure!=original)throw new AssertionError("lost original");if(fail && (failure.getSuppressed().length!=1 || failure.getSuppressed()[0]!=secondary))throw new AssertionError("lost rollback failure");}
                    if(writes!=0)throw new AssertionError("incorrect state");
                    return List.copyOf(events);
                }
                public static int suppressed(){return original.getSuppressed().length;}
            }
            """.formatted(body));
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"-classpath",System.getProperty("java.class.path"),"-d",root.toString(),root.resolve("Fixture.java").toString()));
        try(var loader=new URLClassLoader(new java.net.URL[]{root.toUri().toURL()},getClass().getClassLoader())) {
            var fixture=loader.loadClass("Fixture");var run=fixture.getMethod("run",boolean.class);
            assertEquals(java.util.List.of("write begin","write","write rollback","technical handler"),run.invoke(null,false));
            assertEquals(java.util.List.of("write begin","write","write rollback","technical handler"),run.invoke(null,true));
            assertEquals(1,fixture.getMethod("suppressed").invoke(null));
        }
    }
}
