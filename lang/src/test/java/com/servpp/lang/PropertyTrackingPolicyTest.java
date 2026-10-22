package com.servpp.lang;

import com.servpp.lang.models.SppProject;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class PropertyTrackingPolicyTest {
    private SppProject parse(String policy) throws Exception {
        var file=Files.createTempFile("property-tracking-", ".app");
        Files.writeString(file,"domain sample; "+policy);
        var project=new SppProject(); new AppCompiler(file.toFile(),project).compile(); return project;
    }
    @Test public void appDefaultsOnAndAcceptsExplicitOnAndOff() throws Exception {
        assertTrue(parse("").getDomain("sample").getRuleBlock().isPropertyTracking());
        assertTrue(parse("propertyTracking: true;").getDomain("sample").getRuleBlock().isPropertyTracking());
        assertFalse(parse("propertyTracking: false;").getDomain("sample").getRuleBlock().isPropertyTracking());
    }
    @Test public void duplicatePolicyIsReportedWithoutChangingTheFirstPolicy() throws Exception {
        var domain=parse("propertyTracking: false; propertyTracking: true;").getDomain("sample");
        assertFalse(domain.getRuleBlock().isPropertyTracking());
        assertTrue(domain.getAppParseErrors().toString(),domain.getAppParseErrors().stream().anyMatch(message->message.contains("Duplicate propertyTracking")));
    }
}
