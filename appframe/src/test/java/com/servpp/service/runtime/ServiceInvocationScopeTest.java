package com.servpp.service.runtime;

import com.servpp.service.transaction.BusinessTraceContext;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServiceInvocationScopeTest {
    public record Header(String getGlobalSerialNumber, String getMessageId) { }
    public record Request(Header getAppHeader) { }

    @Test public void localEntriesRetainTraceWithoutTheSystemIdempotencyService() {
        assertEquals("trace", ServiceInvocationScope.execute(new Request(new Header("trace", "message")),
                () -> BusinessTraceContext.currentGlobalSerialNumber().orElseThrow()));
        assertEquals("message", ServiceInvocationScope.execute(new Request(new Header("", "message")),
                () -> BusinessTraceContext.currentGlobalSerialNumber().orElseThrow()));
        assertEquals("parent", BusinessTraceContext.execute("parent", () -> ServiceInvocationScope.execute(
                () -> BusinessTraceContext.currentGlobalSerialNumber().orElseThrow())));
        assertTrue(BusinessTraceContext.currentGlobalSerialNumber().isEmpty());
    }

    @Test public void remoteEntriesGetIndependentTraceAndRestoreInvocationContextOnFailure() {
        InvocationContext.remote(InvocationContext.Origin.SERVICE, "caller", "same-serial", () -> {
            String first = ServiceInvocationScope.execute(() -> {
                assertEquals(InvocationContext.Mode.LOCAL, InvocationContext.current().mode());
                return BusinessTraceContext.currentGlobalSerialNumber().orElseThrow();
            });
            String second = ServiceInvocationScope.execute(() -> BusinessTraceContext.currentGlobalSerialNumber().orElseThrow());
            assertNotEquals(first, second);
            assertThrows(IllegalStateException.class, () -> ServiceInvocationScope.execute(() -> { throw new IllegalStateException("failure"); }));
            assertEquals(InvocationContext.Mode.REMOTE, InvocationContext.current().mode());
            assertTrue(BusinessTraceContext.currentGlobalSerialNumber().isEmpty());
            return null;
        });
        assertEquals(InvocationContext.Mode.LOCAL, InvocationContext.current().mode());
    }
}
