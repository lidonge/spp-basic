package com.servpp.service.server;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DefaultServiceContainerTest {
    @Test
    public void loadFromPropertiesFailsFastOnMissingServiceClass() {
        DefaultServiceContainer container = new DefaultServiceContainer();
        ByteArrayInputStream stream = new ByteArrayInputStream(
                "missing=com.servpp.service.server.DoesNotExist\n"
                        .getBytes(StandardCharsets.UTF_8));

        ReflectiveOperationException error = assertThrows(ReflectiveOperationException.class,
                () -> container.loadFromProperties(stream));

        assertTrue(error.getMessage(), error.getMessage().contains("DoesNotExist"));
    }

    @Test
    public void loadFromPropertiesDoesNotPartiallyRegisterOnFailure() {
        DefaultServiceContainer container = new DefaultServiceContainer();
        ByteArrayInputStream stream = new ByteArrayInputStream(
                ("test=" + TestService.class.getName() + "\n"
                        + "missing=com.servpp.service.server.DoesNotExist\n")
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(ReflectiveOperationException.class, () -> container.loadFromProperties(stream));

        assertNull(container.getService("test"));
        assertNull(container.getService("missing"));
    }

    @Test
    public void loadFromPropertiesRegistersDeclaredService() throws Exception {
        DefaultServiceContainer container = new DefaultServiceContainer();
        ByteArrayInputStream stream = new ByteArrayInputStream(
                ("test=" + TestService.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));

        container.loadFromProperties(stream);

        assertSame(container.getService("test"), container.getService("test"));
        assertEquals("test", container.getService("test").getName());
    }

    public static class TestService implements com.servpp.service.Service {
        @Override
        public Object execute(Object... role) {
            return null;
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
