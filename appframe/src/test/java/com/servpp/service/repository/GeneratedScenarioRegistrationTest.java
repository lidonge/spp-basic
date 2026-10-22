package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Scenario;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.server.DefaultServiceContainer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratedScenarioRegistrationTest {
    public abstract static class Base implements Scenario {
        public String startTransaction() { return ""; }
        public void endTransaction(String id) {}
    }
    public static class Child extends Base {
        public String getName() { return "child"; }
        public Object execute(Object... arguments) { return "child-called"; }
    }
    public static class Parent extends Base {
        protected Child child;
        public Parent() { child = (Child) DefaultServiceContainer.getInstance().getService("child"); }
        public String getName() { return "parent"; }
        public Object execute(Object... arguments) { return child.execute(arguments); }
    }
    public static class Cycle extends Base {
        protected Cycle cycle;
        public String getName() { return "cycle"; }
        public Object execute(Object... arguments) { return null; }
    }
    @Before public void before() { DefaultServiceContainer.getInstance().clear(); }
    @After public void after() { DefaultServiceContainer.getInstance().clear(); }
    private GeneratedServiceRuntime runtime() {
        var source = new DriverManagerJdbcDataSource("org.h2.Driver", "jdbc:h2:mem:registration_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", null, null);
        return GeneratedServiceRuntime.jsonSnapshots(source, new ObjectMapper(), ignored -> null);
    }
    @Test public void typedScenarioDependenciesRegisterBeforeParentsWithoutIdentityProxy() {
        String catalog = "parent=" + Parent.class.getName() + "\nchild=" + Child.class.getName() + "\n";
        runtime().registerFromProperties(DefaultServiceContainer.getInstance(), new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8)));
        assertEquals("child-called", DefaultServiceContainer.getInstance().getService("parent").execute());
        assertTrue(DefaultServiceContainer.getInstance().getService("child") instanceof Child);
    }
    @Test public void cyclicScenariosAreRejectedBeforeInstantiation() {
        String catalog = "cycle=" + Cycle.class.getName() + "\n";
        var error = assertThrows(IllegalStateException.class, () -> runtime().registerFromProperties(DefaultServiceContainer.getInstance(), new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8))));
        assertTrue(error.getMessage(), error.getMessage().contains("Cyclic generated scenario dependencies"));
    }
}
