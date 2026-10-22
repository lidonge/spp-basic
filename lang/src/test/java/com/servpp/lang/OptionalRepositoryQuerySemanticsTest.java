package com.servpp.lang;
import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class OptionalRepositoryQuerySemanticsTest {
    private SppDomain compile(String modifier,String service) throws Exception {
        SppProject project=new SppProject();
        Path spp=Files.createTempFile("optional-query", ".spp"), app=Files.createTempFile("optional-query", ".app");
        try {
            Files.writeString(spp,"domain rules; Role Stored { business(\"Stored business credential\") String id; String value; } "+service);
            Files.writeString(app,"domain rules; scope { local { "+modifier+" queryStored; } }");
            new SppCompiler(spp.toFile(),project).compile();new AppCompiler(app.toFile(),project).compile();return project.getSppDomainMap().get("rules");
        } finally { Files.deleteIfExists(spp);Files.deleteIfExists(app); }
    }
    @Test public void identityBatchParticipantsSupportOptionalLockedReads() throws Exception {
        var domain = compile("repository(argument(0, Stored.id), stored) for update optional",
                "atomic query queryStored(Stored[] stored);");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        var code = ((SppService)domain.getSppClass("QueryStored")).getObjectSnapshotRulesMethod();
        assertTrue(code, code.contains(".asOptional().transactionRead(true).forUpdate()"));
    }
    @Test public void scalarOptionalRepositoryMetadataIsGenerated() throws Exception {
        var domain=compile("repository(argument(0, Stored.id), stored) optional", "atomic query queryStored(Stored key, Stored stored);");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        assertTrue(((SppService)domain.getSppClass("QueryStored")).getObjectSnapshotRulesMethod().contains(".asOptional()"));
    }
    @Test public void optionalRequiresOneBindingAndRejectsNonIdentityCollections() throws Exception {
        assertTrue(compile("optional","atomic query queryStored(Stored key, Stored stored);").validateAppDeclarations().stream().anyMatch(message->message.contains("requires exactly one repository binding")));
        assertTrue(compile("repository(argument(0, Stored.id), stored) optional optional","atomic query queryStored(Stored key, Stored stored);").validateAppDeclarations().stream().anyMatch(message->message.contains("one optional modifier")));
        assertTrue(compile("repository(argument(0, Stored.id), stored) optional","atomic query queryStored(Stored key, Stored[] stored);").validateAppDeclarations().stream().anyMatch(message -> message.contains("requires a scalar participant")));
    }
    @Test public void lockedQueriesGenerateMetadataAndRejectNonIdentityCollectionsAndAsyncBindings() throws Exception {
        var domain = compile("repository(argument(0, Stored.id), stored) for update optional",
                "atomic query queryStored(Stored key, Stored stored);");
        assertTrue(domain.validateAppDeclarations().toString(), domain.validateAppDeclarations().isEmpty());
        var service = (SppService) domain.getSppClass("QueryStored");
        assertTrue(service.getObjectSnapshotRulesMethod().contains(".asOptional().transactionRead(true).forUpdate()"));
        assertTrue(service.getScopeItem().copyForService(service).isRepositoryReadLocked());
        assertTrue(compile("repository(argument(0, Stored.id), stored) for update", "atomic query queryStored(Stored key, Stored[] stored);")
                .validateAppDeclarations().stream().anyMatch(message -> message.contains("requires a scalar participant")));
        assertTrue(compile("async repository(argument(0, Stored.id), stored) for update", "atomic query queryStored(Stored key, Stored stored);")
                .validateAppDeclarations().stream().anyMatch(message -> message.contains("one synchronous repository")));
    }
}
