package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteScopeGeneratorTest {
    @Test
    public void appScopeIncludesExplicitAndDefaultLocalServicesInDeploymentRegistry() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-app-scope-deployment");
        Path yamlDir = Files.createTempDirectory("spp-app-scope-deployment-yaml");
        Path javaDir = Files.createTempDirectory("spp-app-scope-deployment-java");
        Files.writeString(sourceDir.resolve("commerce.spp"), """
                domain commerce;

                Entity Customer{
                    String membershipNumber;
                }

                atomic query queryCustomer(Customer customer);
                atomic query helperCustomer(Customer customer);
                """);
        Files.writeString(sourceDir.resolve("commerce.app"), """
                domain commerce;

                header baseIn{ notnull String messageId; }
                header baseOut{ notnull String appId(Default = "commerce"); }

                scope{
                    local (in baseIn, out baseOut){
                        queryCustomer;
                    }
                }

                services{
                    queryCustomer { }
                    helperCustomer{
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        Path servicesProperties = javaDir.getParent().resolve("resources/META-INF/spp/services.properties");
        String services = Files.readString(servicesProperties);
        assertTrue(services, services.contains(
                "queryCustomer=com.servpp.openapi.commerce.service.SimpleQueryCustomer"));
        assertTrue(services, services.contains("helperCustomer=com.servpp.openapi.commerce.service.SimpleHelperCustomer"));

        String yaml = Files.readString(yamlDir.resolve("commerce.spp.yaml"));
        assertTrue(yaml, yaml.contains("/commerce/queryCustomer"));
        assertTrue(yaml, yaml.contains("/commerce/helperCustomer"));

        assertTrue(new File(javaDir.toFile(),
                "com/servpp/openapi/commerce/handler/HelperCustomer.java").isFile());
        assertTrue(new File(javaDir.toFile(),
                "com/servpp/openapi/commerce/service/SimpleHelperCustomer.java").isFile());
    }

    @Test
    public void remoteQueryServiceGeneratesVoidProxyWithExplicitParticipants() throws Exception {
        Path sourceDir = Files.createTempDirectory("spp-remote-scope");
        Path yamlDir = Files.createTempDirectory("spp-remote-yaml");
        Path javaDir = Files.createTempDirectory("spp-remote-java");
        Files.writeString(sourceDir.resolve("pricing.spp"), """
                domain pricing;

                Entity Product{
                    String productCode;
                }

                Entity PriceQuote{
                    String quoteNumber;
                    double amount;
                }

                atomic query queryPricing(Product product, PriceQuote quote);
                atomic query searchPricing(Product product, PriceQuote[] quotes);
                atomic update updatePricing(Product product);
                atomic update releasePricing(Product product);
                Scenario serial quotePricing(Product product, PriceQuote quote){
                    queryPricing(product, quote);
                }
                """);
        Files.writeString(sourceDir.resolve("pricing.app"), """
                domain pricing;

                header baseIn{ notnull String caller; }
                header baseOut{ notnull String appId(Default = "pricing"); }

                scope{
                    remote (in baseIn, out baseOut){
                        queryPricing;
                        searchPricing;
                        remoteCompensate(releasePricing) updatePricing;
                        releasePricing;
                        quotePricing;
                    }
                }
                """);

        GeneratorUtil.execute(sourceDir.toFile(), yamlDir.toFile(), javaDir.toFile(),
                "com.servpp.openapi");

        Path proxy = javaDir.resolve(
                "com/servpp/openapi/pricing/service/SimpleQueryPricing.java");
        String proxyJava = Files.readString(proxy);
        assertFalse(proxyJava, proxyJava.contains(
                "import com.servpp.openapi.pricing.model.QueryPricingResponse;"));
        assertTrue(proxyJava, proxyJava.contains(
                "import com.servpp.openapi.pricing.model.PriceQuote;"));
        assertTrue(proxyJava, proxyJava.contains(
                "public void queryPricing(Product product,PriceQuote quote)"));
        assertTrue(proxyJava, proxyJava.contains(
                "public class SimpleQueryPricing implements QueryPricing, RemoteGeneratedService"));
        assertTrue(proxyJava, proxyJava.contains(
                "throw new UnsupportedOperationException(\"Remote service queryPricing requires an adapter implementation.\");"));
        assertFalse(proxyJava, proxyJava.contains(
                "public void queryPricing(Product product,PriceQuote quote){\n"
                        + "        return null;\n"
                        + "\t}"));

        Path scenarioProxy = javaDir.resolve(
                "com/servpp/openapi/pricing/service/SimpleQuotePricing.java");
        String scenarioProxyJava = Files.readString(scenarioProxy);
        assertTrue(scenarioProxyJava, scenarioProxyJava.contains(
                "public class SimpleQuotePricing extends QuotePricing implements RemoteGeneratedService"));
        assertTrue(scenarioProxyJava, scenarioProxyJava.contains(
                "throw new UnsupportedOperationException(\"Remote scenario quotePricing requires an adapter implementation.\");"));
        assertFalse(scenarioProxyJava, scenarioProxyJava.contains("SagaLogManager"));

        Path openApi = yamlDir.resolve("pricing.spp.yaml");
        String yaml = Files.readString(openApi);
        assertFalse(yaml, yaml.contains("/pricing/queryPricing"));
        assertFalse(yaml, yaml.contains("/pricing/quotePricing"));

        assertTrue(new File(javaDir.toFile(),
                "com/servpp/openapi/pricing/handler/QueryPricing.java").isFile());
        String handlerJava = Files.readString(javaDir.resolve(
                "com/servpp/openapi/pricing/handler/QueryPricing.java"));
        assertFalse(handlerJava, handlerJava.contains(
                "import com.servpp.openapi.pricing.model.QueryPricingResponse;"));
        assertTrue(handlerJava, handlerJava.contains(
                "import com.servpp.openapi.pricing.model.PriceQuote;"));
        assertTrue(handlerJava, handlerJava.contains(
                "public void queryPricing(Product product,PriceQuote quote)"));

        Path searchProxy = javaDir.resolve(
                "com/servpp/openapi/pricing/service/SimpleSearchPricing.java");
        String searchProxyJava = Files.readString(searchProxy);
        assertFalse(searchProxyJava, searchProxyJava.contains(
                "import com.servpp.openapi.pricing.model.SearchPricingResponse;"));
        assertTrue(searchProxyJava, searchProxyJava.contains(
                "import com.servpp.openapi.pricing.model.PriceQuote;"));
        assertTrue(searchProxyJava, searchProxyJava.contains(
                "public void searchPricing(Product product,java.util.List<PriceQuote> quotes)"));

        Path updateProxy = javaDir.resolve(
                "com/servpp/openapi/pricing/service/SimpleUpdatePricing.java");
        String updateProxyJava = Files.readString(updateProxy);
        assertFalse(updateProxyJava, updateProxyJava.contains(
                "import com.servpp.openapi.pricing.model.UpdatePricingResponse;"));
        assertTrue(updateProxyJava, updateProxyJava.contains(
                "public void updatePricing(Product product)"));
        assertTrue(updateProxyJava, updateProxyJava.contains(
                "import com.servpp.service.runtime.RemoteCompensationRuleProvider;"));
        assertTrue(updateProxyJava, updateProxyJava.contains(
                "public class SimpleUpdatePricing implements UpdatePricing, RemoteGeneratedService,"
                        + " ScopeMetadataProvider, GeneratedServiceKindProvider, RemoteCompensationRuleProvider"));
        assertTrue(updateProxyJava, updateProxyJava.contains(
                "public List<String> getRemoteCompensationServiceNames()"));
        assertTrue(updateProxyJava, updateProxyJava.contains(
                "return List.of(\"releasePricing\");"));
        assertTrue(new File(javaDir.toFile(),
                "com/servpp/openapi/pricing/handler/QuotePricing.java").isFile());
    }
}
