package com.servpp.lang;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefaultLocalBindingTest {
    private SppDomain compile(String scopes) throws Exception {
        var spp = Files.createTempFile("spp-default-local", ".spp");
        var app = Files.createTempFile("spp-default-local", ".app");
        try {
            Files.writeString(spp, "domain rules; Role Item { int quantity; } "
                    + "atomic calculate step(Item item) { item.quantity += 1; } "
                    + "atomic calculate other(Item item) { item.quantity += 2; } "
                    + "Scenario serial run(Item item) { step(item); }");
            Files.writeString(app, "domain rules; header request { String language; } "
                    + "header response { String status; } scope { " + scopes + " }");
            var project = new SppProject();
            new SppCompiler(spp.toFile(), project).compile();
            new AppCompiler(app.toFile(), project).compile();
            return project.getSppDomainMap().get("rules");
        } finally {
            Files.deleteIfExists(spp);
            Files.deleteIfExists(app);
        }
    }

    @Test public void unlistedServicesInheritHeadersWithoutDuplicateBindings() throws Exception {
        var domain = compile("local(in request, out response) {}");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        for (String name : new String[] {"Step", "Other", "Run"}) {
            var item = ((SppService) domain.getSppClass(name)).getScopeItem();
            assertTrue(item.isLocal());
            assertEquals("request", item.getScopeDefine().getIn().getName());
            assertEquals("response", item.getScopeDefine().getOut().getName());
        }
        assertTrue(domain.validateUniqueScopeServiceDeclarations().isEmpty());
    }

    @Test public void explicitScopeTakesPrecedenceOverAutomaticBinding() throws Exception {
        var domain = compile("local(in request, out response) {} local { other; }");
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.stream().anyMatch(message -> message.contains("ambiguous default local scopes")));
        var other = ((SppService) domain.getSppClass("Other")).getScopeItem();
        assertNotNull(other);
        assertNull(other.getScopeDefine().getIn());
        assertNull(((SppService) domain.getSppClass("Step")).getScopeItem());
    }

    @Test public void multipleScopesRequireExplicitChoiceOnlyForUnlistedServices() throws Exception {
        var domain = compile("local(in request, out response) { step; run; } local { other; }");
        assertFalse(domain.validateAppDeclarations().stream()
                .anyMatch(message -> message.contains("ambiguous default local scopes")));
    }
}
