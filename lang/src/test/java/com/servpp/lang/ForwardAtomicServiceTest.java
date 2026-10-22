package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForwardAtomicServiceTest {
    @Test public void laterServiceRetainsParticipantSignatureAndImplementationInEarlierCalls() throws Exception {
        var first=Files.createTempFile("first", ".spp");var second=Files.createTempFile("second", ".spp");
        try {
            Files.writeString(first,"domain rules; Role Value { long n; } Scenario serial run(Value v) { updateValue(v); }");
            Files.writeString(second,"domain rules; atomic calculate updateValue(Value v) { v.n = v.n + 1; }");
            SppProject p=new SppProject();new SppCompiler(first.toFile(),p).compile();new SppCompiler(second.toFile(),p).compile();
            var d=p.getSppDomainMap().get("rules");var service=(SppService)d.getSppClass("UpdateValue");
            assertEquals(1,service.getServiceBody().getSppLocalVarList().size());
            assertTrue(service.getReturns().getSppLocalVarList().isEmpty());assertTrue(service.hasBusinessImplementation());
            var group=(ExecuteBlock)((SppService)d.getSppClass("Run")).getServiceBody().getSppServiceCallList().get(0);
            var call=(SppServiceCall)group.getExecuteCallList().get(0);
            assertSame(service,call.getCallee());assertTrue(((SppService)d.getSppClass("Run")).getScenarioBodyJava().contains("updateValue(v)"));
            assertTrue(d.validateBusinessCalculations().toString(),d.validateBusinessCalculations().isEmpty());
        } finally { Files.deleteIfExists(first);Files.deleteIfExists(second); }
    }
}
