package com.servpp.service.runtime;

import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantRoleSnapshotTest {
    public static class Product {
        private String code, name;
        public String getCode() { return code; }
        public void setCode(String value) { code = value; }
        public String getName() { return name; }
        public void setName(String value) { name = value; }
    }
    public static class Line {
        private String code, title, currentName;
        public String getCode() { return code; }
        public void setCode(String value) { code = value; }
        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public String getCurrentName() { return currentName; }
        public void setCurrentName(String value) { currentName = value; }
    }
    public static class Mapper {
        public void roleToEntity(Line line, Map<String,Object> entities) {
            ((Product)entities.get("Product")).setCode(line.getCode());
        }
        public void entityToRole(Map<String,Object> entities, Line line) {
            Product product = (Product)entities.get("Product");
            line.setTitle(product.getName()); line.setCurrentName(product.getName());
        }
    }
    public interface Query extends Service { void findProduct(Product product); }
    public static class Provider implements Query {
        public String getName() { return "catalog.findProduct"; }
        public Object execute(Object... arguments) { findProduct((Product)arguments[0]); return null; }
        public void findProduct(Product product) {
            product.setName("Current name"); ParticipantIdentity.confirm(product, true);
        }
    }
    private void verify(String snapshot) {
        var container = DefaultServiceContainer.getInstance(); container.clear();
        container.registerService("catalog.findProduct", new Provider());
        try {
            Line line = new Line(); line.setCode("P1"); line.setTitle(snapshot);
            ParticipantIdentity.confirm(line, true);
            var definition = new MaterializedRolePerformers.Definition(Line.class, List.of(Product.class), new Mapper(), List.of("title"));
            var step = new ParticipantRoleQueries.Step("catalog.findProduct", Product.class, new String[]{"code", "name"}, definition);
            LocalDomainServices.executeScope(() -> ParticipantRoleQueries.complete(line, step));
            assertTrue(ParticipantIdentity.isIdentified(line));
            assertEquals(snapshot, line.getTitle());
            assertEquals("Current name", line.getCurrentName());
        } finally { container.clear(); }
    }
    @Test public void historicalValueSurvivesPerformerRefresh() { verify("Agreed name"); }
    @Test public void nullSnapshotIsNotSilentlyReconstructedFromToday() { verify(null); }
}
