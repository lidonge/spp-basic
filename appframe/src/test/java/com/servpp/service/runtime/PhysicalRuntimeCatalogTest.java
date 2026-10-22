package com.servpp.service.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class PhysicalRuntimeCatalogTest {
    @Test
    public void physicalProfileExplicitDefaultsToFalse() {
        PhysicalRuntimeCatalog catalog = PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                "physicalProfile=local\n".getBytes(StandardCharsets.UTF_8)));

        assertFalse(catalog.physicalProfileExplicit());
    }

    @Test
    public void physicalProfileExplicitParsesTrue() {
        PhysicalRuntimeCatalog catalog = PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                ("physicalProfile=seata\n"
                        + "physicalProfileExplicit=true\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertTrue(catalog.physicalProfileExplicit());
    }

    @Test
    public void physicalProfileExplicitFailsFastOnInvalidBoolean() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        ("physicalProfile=seata\n"
                                + "physicalProfileExplicit=yes\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "Generated physical runtime catalog physicalProfileExplicit"
                        + " must be true or false, but was yes"));
    }

    @Test
    public void rejectsBlankPhysicalSourceFileEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "physicalSourceFiles=creditcommerce.phy,\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog source file contains blank value"));
    }

    @Test
    public void rejectsDuplicatePhysicalSourceFiles() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        ("physicalSourceFiles=creditcommerce.phy, creditcommerce.phy\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog source file"
                        + " declares duplicate value creditcommerce.phy"));
    }

    @Test
    public void rejectsNullInputStreamWithStartupCatalogMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties((java.io.InputStream) null));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Cannot load generated physical runtime catalog: input stream is null"));
    }

    @Test
    public void rejectsNullPropertiesWithStartupCatalogMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties((java.util.Properties) null));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Cannot load generated physical runtime catalog: properties are null"));
    }

    @Test
    public void rejectsBlankResourceNameWithStartupCatalogMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromResource(" "));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Cannot find generated physical runtime catalog: resource name is required"));
    }

    @Test
    public void rejectsDuplicateBackendNames() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "backends=localJdbc,localJdbc\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog backend declares duplicate value localJdbc"));
    }

    @Test
    public void rejectsBlankBackendNameEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "backends=localJdbc,\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog backend contains blank value"));
    }

    @Test
    public void rejectsBlankBackendBindingEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        ("backends=localJdbc\n"
                                + "backend.localJdbc.bindings=at:*,\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog backend binding contains blank value"));
    }

    @Test
    public void rejectsDuplicateBackendBindings() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        ("backends=localJdbc\n"
                                + "backend.localJdbc.bindings=at:*, at:*\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog backend binding declares duplicate value at:*"));
    }

    @Test
    public void rejectsDuplicateRemoteServiceNames() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "remoteServices=queryCustomer,queryCustomer\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog remote service declares duplicate value queryCustomer"));
    }

    @Test
    public void trimsPhysicalAdapterNames() {
        PhysicalRuntimeCatalog catalog = PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                ("remoteServices=queryCustomer\n"
                        + "remote.queryCustomer.adapter= httpJson \n"
                        + "repositories=objectStore\n"
                        + "repository.objectStore.adapter= jdbcObjectRepository \n"
                        + "quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.adapter= jdbcQuantum \n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("httpJson", catalog.remoteAdapters().get(0).adapterName());
        assertEquals("jdbcObjectRepository", catalog.repositoryAdapters().get(0).adapterName());
        assertEquals("jdbcQuantum", catalog.quantumStores().get(0).adapterName());
    }

    @Test
    public void parsesLogicalQuantumDefinitionsByStableOrdinal() {
        PhysicalRuntimeCatalog catalog = PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                ("quantumStores=globalQuantum\n"
                        + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit,Inventory.availableQty\n"
                        + "quantumStore.globalQuantum.adapter=quantumService\n"
                        + "quantumStore.globalQuantum.definitionCount=2\n"
                        + "quantumStore.globalQuantum.definition.0.attributeId=CreditAccount.availableCredit\n"
                        + "quantumStore.globalQuantum.definition.0.numberType=LONG\n"
                        + "quantumStore.globalQuantum.definition.0.minValue=0\n"
                        + "quantumStore.globalQuantum.definition.0.maxValue=9223372036854775807\n"
                        + "quantumStore.globalQuantum.definition.0.scale=2\n"
                        + "quantumStore.globalQuantum.definition.1.attributeId=Inventory.availableQty\n"
                        + "quantumStore.globalQuantum.definition.1.numberType=DOUBLE\n"
                        + "quantumStore.globalQuantum.definition.1.minValue=0\n"
                        + "quantumStore.globalQuantum.definition.1.maxValue=1000000\n"
                        + "quantumStore.globalQuantum.definition.1.scale=0\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals(List.of(
                        new PhysicalRuntimeCatalog.QuantumDefinition(
                                "CreditAccount.availableCredit", "LONG", "0",
                                "9223372036854775807", 2),
                        new PhysicalRuntimeCatalog.QuantumDefinition(
                                "Inventory.availableQty", "DOUBLE", "0", "1000000", 0)),
                catalog.quantumStores().get(0).definitions());
    }

    @Test
    public void rejectsDuplicateLogicalQuantumDefinitionAttributes() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        ("quantumStores=globalQuantum\n"
                                + "quantumStore.globalQuantum.definitionCount=2\n"
                                + "quantumStore.globalQuantum.definition.0.attributeId=Inventory.availableQty\n"
                                + "quantumStore.globalQuantum.definition.0.numberType=LONG\n"
                                + "quantumStore.globalQuantum.definition.0.minValue=0\n"
                                + "quantumStore.globalQuantum.definition.0.maxValue=100\n"
                                + "quantumStore.globalQuantum.definition.0.scale=0\n"
                                + "quantumStore.globalQuantum.definition.1.attributeId=Inventory.availableQty\n"
                                + "quantumStore.globalQuantum.definition.1.numberType=LONG\n"
                                + "quantumStore.globalQuantum.definition.1.minValue=0\n"
                                + "quantumStore.globalQuantum.definition.1.maxValue=100\n"
                                + "quantumStore.globalQuantum.definition.1.scale=0\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage().contains(
                "physical runtime catalog quantum definition declares duplicate value Inventory.availableQty"));
    }

    @Test
    public void catalogModelConstructorsNormalizeRuntimeKeys() {
        List<PhysicalRuntimeCatalog.PhysicalBinding> bindings = new ArrayList<>();
        bindings.add(new PhysicalRuntimeCatalog.PhysicalBinding(" at ", " * "));
        List<String> sourceFiles = new ArrayList<>();
        sourceFiles.add(" creditcommerce.phy ");
        List<String> attributes = new ArrayList<>();
        attributes.add(" CreditAccount.availableCredit ");
        PhysicalRuntimeCatalog catalog = new PhysicalRuntimeCatalog(
                List.of(new PhysicalRuntimeCatalog.PhysicalBackend(
                        " localJdbc ", " ", bindings, " globalQuantum ", null)),
                List.of(new PhysicalRuntimeCatalog.RemoteAdapter(" queryCustomer ", " httpJson ", null)),
                List.of(new PhysicalRuntimeCatalog.RepositoryAdapter(
                        " objectStore ", " jdbcObjectRepository ", null)),
                List.of(new PhysicalRuntimeCatalog.QuantumStore(
                        " globalQuantum ", attributes, " jdbcQuantum ", null)),
                " local ", true, sourceFiles, null);

        bindings.add(new PhysicalRuntimeCatalog.PhysicalBinding("saga", "submitOrder"));
        sourceFiles.add("ignored.phy");
        attributes.add("ignored.attribute");

        PhysicalRuntimeCatalog.PhysicalBackend backend = catalog.backends().get(0);
        assertEquals("localJdbc", backend.name());
        assertEquals("localJdbc", backend.adapterName());
        assertEquals("at", backend.bindings().get(0).kind());
        assertEquals("*", backend.bindings().get(0).targetName());
        assertEquals("globalQuantum", backend.quantumStoreName());
        assertEquals(Map.of(), backend.configuration());
        assertEquals("queryCustomer", catalog.remoteAdapters().get(0).serviceName());
        assertEquals("httpJson", catalog.remoteAdapters().get(0).adapterName());
        assertEquals("objectStore", catalog.repositoryAdapters().get(0).repositoryName());
        assertEquals("jdbcObjectRepository", catalog.repositoryAdapters().get(0).adapterName());
        assertEquals("globalQuantum", catalog.quantumStores().get(0).name());
        assertEquals(List.of("CreditAccount.availableCredit"), catalog.quantumStores().get(0).attributeIds());
        assertEquals("jdbcQuantum", catalog.quantumStores().get(0).adapterName());
        assertEquals("local", catalog.physicalProfile());
        assertEquals(List.of("creditcommerce.phy"), catalog.physicalSourceFiles());
        assertEquals(Map.of(), catalog.configuration());
    }

    @Test
    public void rejectsBlankRemoteServiceNameEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "remoteServices=,queryCustomer\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog remote service contains blank value"));
    }

    @Test
    public void rejectsDuplicateRepositoryNames() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "repositories=objectStore,objectStore\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog repository declares duplicate value objectStore"));
    }

    @Test
    public void defaultRepositoryAdapterAcceptsTrimmedBooleanFlag() {
        PhysicalRuntimeCatalog catalog = PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                ("repositories=objectStore,auditStore\n"
                        + "repository.objectStore.adapter=jdbcObjectRepository\n"
                        + "repository.objectStore.config.default= true \n"
                        + "repository.auditStore.adapter=jdbcObjectRepository\n")
                        .getBytes(StandardCharsets.UTF_8)));

        assertTrue(catalog.defaultRepositoryAdapter().isPresent());
        assertTrue(catalog.defaultRepositoryAdapter().get().repositoryName().equals("objectStore"));
        assertFalse(catalog.hasAmbiguousDefaultRepositoryAdapters());
    }

    @Test
    public void rejectsBlankRepositoryNameEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "repositories=objectStore,,auditStore\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog repository contains blank value"));
    }

    @Test
    public void rejectsDuplicateQuantumStoreNames() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "quantumStores=globalQuantum,globalQuantum\n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog quantum store declares duplicate value globalQuantum"));
    }

    @Test
    public void rejectsBlankQuantumStoreNameEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        "quantumStores=globalQuantum, \n".getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog quantum store contains blank value"));
    }

    @Test
    public void rejectsBlankQuantumStoreAttributeEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        ("quantumStores=globalQuantum\n"
                                + "quantumStore.globalQuantum.attributes=CreditAccount.availableCredit,\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog quantum store attribute contains blank value"));
    }

    @Test
    public void rejectsDuplicateQuantumStoreAttributes() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PhysicalRuntimeCatalog.fromProperties(new ByteArrayInputStream(
                        ("quantumStores=globalQuantum\n"
                                + "quantumStore.globalQuantum.attributes="
                                + "CreditAccount.availableCredit,CreditAccount.availableCredit\n")
                                .getBytes(StandardCharsets.UTF_8))));

        assertTrue(error.getMessage(), error.getMessage()
                .contains("Generated physical runtime catalog quantum store attribute"
                        + " declares duplicate value CreditAccount.availableCredit"));
    }
}
