package com.servpp.service.runtime;

import com.servpp.service.Service;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteFactsMetadataTest {
    public static class Entry implements Service, RemoteGeneratedService, ScopeMetadataProvider {
        public Object execute(Object... values) { return null; }
        public String getName() { return "entry"; }
        public boolean isLocalScope() { return false; }
        public boolean isUpdateEntry() { return true; }
        public String getInputHeaderName() { return null; }
        public String getOutputHeaderName() { return null; }
        public List<String> getDeclaredIdempotencyKeyPaths() { return List.of("owner.number"); }
        public List<String> getDeclaredIdempotencyFactsPaths() { return List.of("agreement","choice.option"); }
    }
    @Test public void factMetadataNeverInvokesRemoteBusinessAdapter() {
        var proxy = RemoteServiceProxy.decorate(new Entry(),name -> { throw new AssertionError("Metadata must stay local"); });
        var metadata = (ScopeMetadataProvider)proxy;
        assertTrue(metadata.isUpdateEntry());
        assertEquals(List.of("owner.number"),metadata.getDeclaredIdempotencyKeyPaths());
        assertEquals(List.of("agreement","choice.option"),metadata.getDeclaredIdempotencyFactsPaths());
        assertNull(metadata.getDeclaredIdempotencyFactsPath());
    }
}
