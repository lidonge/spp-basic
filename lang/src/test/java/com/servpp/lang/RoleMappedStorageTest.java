package com.servpp.lang;

import com.servpp.lang.models.*;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoleMappedStorageTest {
    private void parse(SppProject project, String source, String suffix) throws Exception {
        Path file = Files.createTempFile("role-storage", suffix);
        try {
            Files.writeString(file, source);
            if (suffix.equals(".spp")) new SppCompiler(file.toFile(), project).compile();
            else new AppCompiler(file.toFile(), project).compile();
        } finally { Files.deleteIfExists(file); }
    }
    private SppProject project(String configuration) throws Exception {
        var project = new SppProject();
        parse(project, "domain shop; Entity Product { String code; String name; double price; } "
                + "Role Item { String code; String name; double price; boolean active; } "
                + "take Product as Item { mapsame; }", ".spp");
        project.getSppDomainMap().values().forEach(SppDomain::dealEntityToRoleMaps);
        parse(project, "domain shop; tables entities { table PRODUCTS(Product) { primarykey(Product.code); } } "
                + "tables roles { table ITEMS(Item) { primarykey(Item.code); " + configuration + " } }", ".app");
        return project;
    }
    @Test public void participantAssociationsAndCollectionsCanBeSnapshots() throws Exception {
        var project = new SppProject();
        parse(project,"domain shop; Entity Product { String code; } Contract Receipt { String receiptCode; Product product; } "
                + "Role FailureRecord { String receiptCode; Receipt original; Receipt[] attempts; }", ".spp");
        parse(project,"domain shop; tables roles { table FAILURES(FailureRecord) { primarykey(FailureRecord.receiptCode); "
                + "snapshot(FailureRecord.original, FailureRecord.attempts); } } "
                + "tables contracts { table RECEIPTS(Receipt) { primarykey(Receipt.receiptCode); snapshot(Receipt.product); } }", ".app");
        assertTrue(project.getDomain("shop").getAppParseErrors().toString(),project.getDomain("shop").getAppParseErrors().isEmpty());
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(),errors.isEmpty());
        var type = (SppClass) project.getDomain("shop").getSppClass("FailureRecord");
        assertTrue(type.getField("original").isSnapshotStorageFact());
        assertTrue(type.getField("attempts").isSnapshotStorageFact());
    }
    @Test public void snapshotIsStoredLocallyAndCurrentStaysWithPerformer() throws Exception {
        var project = project("snapshot(Item.price); current(Item.name);");
        var errors = DomainDeploymentValidator.validate(project);
        assertTrue(errors.toString(), errors.isEmpty());
        var role = (SppClass) project.getDomain("shop").getSppClass("Item");
        assertTrue(role.getField("price").isBorrowedStorageFact());
        assertTrue(role.getField("price").isStoredHere());
        assertTrue(role.getField("price").isSnapshotStorageFact());
        assertFalse(role.getField("name").isStoredHere());
        assertTrue(role.getField("active").isStoredHere());
    }
    @Test public void ownAttributeCannotBeConfiguredAsPerformerSnapshot() throws Exception {
        var errors = DomainDeploymentValidator.validate(project("snapshot(Item.active);"));
        assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.contains("ordinary non-identity performer attribute")));
    }
    @Test public void performerIdentityCannotBecomeSnapshot() throws Exception {
        var errors = DomainDeploymentValidator.validate(project("snapshot(Item.code);"));
        assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.contains("ordinary non-identity performer attribute")));
    }
    @Test public void conflictingModesAreRejected() throws Exception {
        assertFalse(project("snapshot(Item.price); current(Item.price);").getDomain("shop").getAppParseErrors().isEmpty());
    }
    @Test public void unknownPropertyIsRejected() throws Exception {
        assertFalse(project("snapshot(Item.missing);").getDomain("shop").getAppParseErrors().isEmpty());
    }
}
