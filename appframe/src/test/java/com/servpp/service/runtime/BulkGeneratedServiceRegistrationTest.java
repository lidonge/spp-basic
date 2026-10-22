package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class BulkGeneratedServiceRegistrationTest {
    private static int metadataReads;
    public static class Probe implements Service, ScopeMetadataProvider {
        public Object execute(Object... args) { return null; }
        public String getName() { return getClass().getSimpleName(); }
        public boolean isLocalScope() { return true; }
        public String getInputHeaderName() { return null; }
        public String getOutputHeaderName() { return null; }
        public List<String> getServiceParameterNames() { metadataReads++; return List.of(); }
    }
    public static class One extends Probe {}
    public static class Two extends Probe {}
    public static class Three extends Probe {}
    public static class Four extends Probe {}
    public static class Five extends Probe {}
    public static class Six extends Probe {}
    public static class Seven extends Probe {}
    public static class Eight extends Probe {}
    private static final List<Class<? extends Probe>> TYPES = List.of(
        One.class, Two.class, Three.class, Four.class, Five.class, Six.class, Seven.class, Eight.class);
    private ByteArrayInputStream registry() {
        StringBuilder text = new StringBuilder();
        for (Class<?> type : TYPES) text.append(type.getSimpleName()).append('=').append(type.getName()).append('\n');
        return new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Test public void bulkRegistrationReadsFinalMetadataOnceAndSingleRegistrationStillRefreshes() {
        DefaultServiceContainer container = DefaultServiceContainer.getInstance(); container.clear();
        JdbcDataSource source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, new ObjectMapper(), ignored -> null);
        try {
            metadataReads = 0;
            var services = runtime.registerGeneratedServices(container, registry());
            assertEquals(TYPES.size(), services.size());
            assertEquals("Metadata must not be rebuilt after each item in a batch", TYPES.size(), metadataReads);
            metadataReads = 0;
            var replacement = runtime.registerGeneratedService(container, registry(), "One");
            assertEquals("One", replacement.getName());
            assertTrue("Single registration must refresh the completed catalog", metadataReads >= TYPES.size());
            assertEquals(TYPES.size(), runtime.describeGeneratedServices(services).size());
        } finally { container.clear(); runtime.clear(); }
    }
}
