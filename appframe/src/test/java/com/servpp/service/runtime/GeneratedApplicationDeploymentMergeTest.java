package com.servpp.service.runtime;

import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratedApplicationDeploymentMergeTest {
    @Test public void colocatedDomainsMergeDistinctRoutesAndDeduplicateSharedTargets() {
        var target = new Properties();
        target.setProperty("domainRoutes", "customer");
        target.setProperty("domainRoute.customer.endpoint", "http://customer:8087");
        var source = new Properties();
        source.setProperty("domainRoutes", "product,customer");
        source.setProperty("domainRoute.customer.endpoint", "http://customer:8087");
        source.setProperty("domainRoute.product.endpoint", "http://product:8088");
        GeneratedApplicationDeployment.merge(target, source);
        assertEquals("customer,product", target.getProperty("domainRoutes"));
        assertEquals("http://customer:8087", target.getProperty("domainRoute.customer.endpoint"));
        assertEquals("http://product:8088", target.getProperty("domainRoute.product.endpoint"));
    }
    @Test public void sameTargetWithDifferentEndpointsStillRejectsDeployment() {
        var target = new Properties(); target.setProperty("domainRoute.customer.endpoint", "http://first:8087");
        var source = new Properties(); source.setProperty("domainRoute.customer.endpoint", "http://second:8087");
        var failure = assertThrows(IllegalStateException.class, () -> GeneratedApplicationDeployment.merge(target, source));
        assertEquals("APP_SHARED_RESOURCE_CONFIGURATION_CONFLICT: domainRoute.customer.endpoint", failure.getMessage());
    }
}
