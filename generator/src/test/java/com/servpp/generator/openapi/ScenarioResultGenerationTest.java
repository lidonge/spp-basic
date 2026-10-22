package com.servpp.generator.openapi;
import com.servpp.lang.*;
import com.servpp.lang.models.*;
import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScenarioResultGenerationTest {
    @Test public void inPlaceQueriesPreserveParticipantsInGroupsAndIdentifiedBranches() throws Exception {
        Path root=Files.createTempDirectory("scene-result-java");Path source=root.resolve("rules.spp");
        Files.writeString(source,"""
            domain rules;
            Role Item { String code; }
            atomic query readItems(Item key, Item[] items);
            atomic query readItem(Item key, Item item);
            Scenario serial load(Item key, Item[] items, Item item) {
                serial { readItem(key, item); }
                if (item) { serial { readItems(key, items); } }
                else { transaction { readItems(key, items); } }
            }
            """);
        SppProject project=new SppProject();new SppCompiler(source.toFile(),project).compile();var domain=project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        String body=((SppService)domain.getSppClass("Load")).getScenarioBodyJava();
        Files.writeString(root.resolve("Fixture.java"),"""
            import com.servpp.service.*;
            import com.servpp.service.scenario.*;
            import java.util.*;
            public class Fixture {
                public static class Item { String code; }
                public static class Query implements AtomicService {
                    boolean found; public String getName(){return "query";} public Object execute(Object...args){return null;}
                    public void readItems(Item key,List<Item> items){
                        items.clear();
                        if(found){Item result=new Item();result.code=key.code;items.add(result);}
                        com.servpp.service.runtime.ParticipantIdentity.confirm(items,found);
                    }
                    public void readItem(Item key,Item item){
                        if(found)item.code=key.code;
                        com.servpp.service.runtime.ParticipantIdentity.confirm(item,found);
                    }
                }
                private static final Query readItems=new Query(),readItem=new Query();
                public static boolean run(boolean found){
                    Item key=new Item(),item=new Item();key.code="LOOKUP";
                    List<Item> items=new ArrayList<>();
                    readItems.found=found;readItem.found=found;
                    final boolean[] valid={false};
                    com.servpp.service.runtime.ParticipantIdentity.execute(() -> {
                        %s
                        valid[0]=item!=key && (found
                            ? items.size()==1 && items.get(0)!=key && "LOOKUP".equals(item.code) && "LOOKUP".equals(items.get(0).code)
                            : items.isEmpty() && item.code==null)
                            && com.servpp.service.runtime.ParticipantIdentity.isIdentified(item)==found
                            && com.servpp.service.runtime.ParticipantIdentity.isIdentified(items)==found;
                    });
                    return valid[0];
                }
            }
            """.formatted(body));
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"-classpath",System.getProperty("java.class.path"),"-d",root.toString(),root.resolve("Fixture.java").toString()));
        try(var loader=new URLClassLoader(new java.net.URL[]{root.toUri().toURL()},getClass().getClassLoader())) {
            var run=loader.loadClass("Fixture").getMethod("run",boolean.class);
            assertEquals(true,run.invoke(null,true));assertEquals(true,run.invoke(null,false));
        }
    }
}
