package com.servpp.lang;
import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class BusinessIdempotencyDeclarationTest {
    private SppDomain compile(String service,String scope) throws Exception {
        var spp=Files.createTempFile("spp-idempotency", ".spp");var app=Files.createTempFile("spp-idempotency", ".app");
        try {
            Files.writeString(spp,"domain rules; Role Item { business(\"Business item credential\") String id; } atomic calculate step(Item item) {} "+service);
            Files.writeString(app,"domain rules; scope { "+scope+" }");
            SppProject project=new SppProject();new SppCompiler(spp.toFile(),project).compile();new AppCompiler(app.toFile(),project).compile();return project.getSppDomainMap().get("rules");
        } finally { Files.deleteIfExists(spp);Files.deleteIfExists(app); }
    }
    @Test public void localSceneOwnsBusinessLifecycle() throws Exception {
        var domain=compile("Scenario serial run(Item item) { step(item); }","local { step; idempotency business run; }");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        assertTrue(((SppService)domain.getSppClass("Run")).isBusinessManagedIdempotency());
    }
    @Test public void atomicAndDuplicateDeclarationsAreRejected() throws Exception {
        assertTrue(compile("","local { idempotency business step; }").validateAppDeclarations().stream().anyMatch(message->message.contains("Business-managed idempotency")));
        assertTrue(compile("Scenario serial run(Item item) { step(item); }","local { step; idempotency business idempotency business run; }").validateAppDeclarations().stream().anyMatch(message->message.contains("Business-managed idempotency")));
    }
}
