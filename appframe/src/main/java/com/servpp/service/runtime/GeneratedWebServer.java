package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.servpp.service.Service;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.server.DefaultServiceContainer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;

/** HTTP transport for APP-declared generated services and packaged static resources. */
public final class GeneratedWebServer implements AutoCloseable {
    private final HttpServer server;
    private final ObjectMapper mapper;
    private final Map<String, String> requests = new LinkedHashMap<>();
    private final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(8);

    public GeneratedWebServer(Properties catalog, ObjectMapper mapper, InetSocketAddress address) throws IOException {
        this.mapper = mapper;
        for (int i = 0; i < Integer.parseInt(catalog.getProperty("expose.count", "0")); i++) {
            String prefix = "expose." + i + ".";
            requests.put(catalog.getProperty(prefix + "service"), catalog.getProperty(prefix + "request"));
        }
        server = HttpServer.create(address, 0);
        server.setExecutor(executor);
        server.createContext("/api/", this::execute);
        server.createContext("/spp/deployment", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) { json(exchange,405,Map.of("error","GET_REQUIRED")); return; }
            if (DomainServiceRouter.manifest()==null) { json(exchange,404,Map.of("error","APP_DEPLOYMENT_CONTRACT_REQUIRED")); return; }
            json(exchange,200,DomainServiceRouter.manifest());
        });
        server.createContext("/spp/readiness", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) { json(exchange,405,Map.of("error","GET_REQUIRED")); return; }
            json(exchange,DomainServiceRouter.isReady()?200:503,Map.of("ready",DomainServiceRouter.isReady()));
        });
        server.createContext("/spp/atomic", this::atomic);
        server.createContext("/", this::staticResource);
    }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }

    private void atomic(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { json(exchange,405,Map.of("error","POST_REQUIRED")); return; }
        if (!DomainServiceRouter.isReady()) { json(exchange,503,Map.of("error","APP_DEPLOYMENT_NOT_READY")); return; }
        if (!DomainServiceRouter.accepts(exchange.getRequestHeaders().getFirst("X-SPP-Domain-Token"))) {
            json(exchange,403,Map.of("error","DOMAIN_CALLER_NOT_AUTHORIZED")); return;
        }
        try {
            byte[] body=exchange.getRequestBody().readNBytes(1024*1024+1);
            if (body.length>1024*1024) { json(exchange,413,Map.of("error","REQUEST_TOO_LARGE")); return; }
            var call=mapper.readValue(body,DomainServiceProtocol.Call.class);
            var result=DomainServiceProtocol.execute(call,exchange.getRequestHeaders().getFirst("X-SPP-Caller"),
                    exchange.getRequestHeaders().getFirst("X-SPP-Request-Serial"));
            json(exchange,200,result);
        } catch (Exception failure) {
            json(exchange,400,Map.of("error",failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage(),
                    "outcome",com.servpp.service.transaction.TransactionOutcomes.outcome(failure).name()));
        }
    }
    private void execute(HttpExchange exchange) throws IOException {
        if (!DomainServiceRouter.isReady()) { json(exchange,503,Map.of("error","APP_DEPLOYMENT_NOT_READY")); return; }
        if (!"POST".equals(exchange.getRequestMethod())) { json(exchange, 405, Map.of("error", "POST_REQUIRED")); return; }
        String name = exchange.getRequestURI().getPath().substring("/api/".length());
        String requestClass = requests.get(name);
        if (requestClass == null) { json(exchange, 404, Map.of("error", "UNDECLARED_WEB_SERVICE")); return; }
        try {
            byte[] body = exchange.getRequestBody().readNBytes(1024 * 1024 + 1);
            if (body.length > 1024 * 1024) { json(exchange, 413, Map.of("error", "REQUEST_TOO_LARGE")); return; }
            Object request = mapper.readValue(body, Class.forName(requestClass));
            String caller = browserCaller(exchange);
            String serial = exchange.getRequestHeaders().getFirst("X-SPP-Request-Serial");
            if (serial == null || serial.isBlank()) serial = UUID.randomUUID().toString();
            InvocationContext.Header invocation = new InvocationContext.Header(InvocationContext.Mode.REMOTE,
                    InvocationContext.Origin.USER, caller, serial);
            attachHeader(name, request, invocation);
            Service service = DefaultServiceContainer.getInstance().getService(name);
            if (service == null) throw new IllegalStateException("GENERATED_SERVICE_MISSING");
            BusinessChecks.restoreScenarioResult(null);
            com.servpp.service.idempotency.BusinessIdempotencyManager.resetReplay();
            String requestedLocale = exchange.getRequestHeaders().getFirst("Accept-Language");
            if (requestedLocale != null) requestedLocale = requestedLocale.split("[,;]", 2)[0].trim();
            Object result = InternationalizedConstants.withLocale(requestedLocale,
                    () -> InvocationContext.with(invocation, () -> service.execute(request)));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("participants", request); response.put("result", result);
            response.put("replayed", com.servpp.service.idempotency.BusinessIdempotencyManager.wasReplay());
            BusinessChecks.Result checked = BusinessChecks.scenarioResult();
            if (!checked.passed()) response.put("error", checked.code());
            json(exchange, checked.passed() ? 200 : 400, response);
        } catch (Exception failure) {
            Throwable cause = failure;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            json(exchange, 400, Map.of("error", cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
        }
    }
    private String browserCaller(HttpExchange exchange) {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) for (String cookie : cookies.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "spp-client".equals(parts[0]) && parts[1].matches("[a-fA-F0-9-]{36}"))
                return "browser:" + parts[1];
        }
        String client = UUID.randomUUID().toString();
        exchange.getResponseHeaders().add("Set-Cookie", "spp-client=" + client + "; Path=/; HttpOnly; SameSite=Lax");
        return "browser:" + client;
    }
    private void attachHeader(String name, Object request) throws Exception {
        attachHeader(name, request, InvocationContext.current());
    }
    private void attachHeader(String name, Object request, InvocationContext.Header invocation) throws Exception {
        Service service = DefaultServiceContainer.getInstance().getService(name);
        if (service instanceof ScopeMetadataProvider metadata && metadata.getInputHeaderName() != null) {
            var setter = java.util.Arrays.stream(request.getClass().getMethods())
                    .filter(m -> m.getName().equalsIgnoreCase("setAppHeader") && m.getParameterCount() == 1).findFirst().orElseThrow();
            var getter = java.util.Arrays.stream(request.getClass().getMethods()).filter(m -> m.getName().equalsIgnoreCase("getAppHeader") && m.getParameterCount()==0).findFirst().orElseThrow();
            Object header = getter.invoke(request);
            if(header==null) header=setter.getParameterTypes()[0].getConstructor().newInstance();
            if (metadata.hasIdempotencyKeyField()) {
                String field = "set" + metadata.getIdempotencyKeyFieldName();
                var key = java.util.Arrays.stream(header.getClass().getMethods())
                        .filter(m -> m.getName().equalsIgnoreCase(field) && m.getParameterCount() == 1).findFirst().orElseThrow();
                String accessor="get"+metadata.getIdempotencyKeyFieldName();
                var reader=java.util.Arrays.stream(header.getClass().getMethods()).filter(m -> m.getName().equalsIgnoreCase(accessor) && m.getParameterCount()==0).findFirst().orElseThrow();
                Object value=reader.invoke(header);
                if(value==null || value.toString().isBlank()) key.invoke(header,name+":"+UUID.randomUUID());
            }
            TechnicalCallHeaders.set(header, "callerId", invocation.callerId());
            TechnicalCallHeaders.set(header, "requestSerialNumber", invocation.requestSerialNumber());
            TechnicalCallHeaders.set(header, "callMode", invocation.mode().name());
            TechnicalCallHeaders.set(header, "callOrigin", invocation.origin().name());
            setter.invoke(request, header);
        }
    }
    private void staticResource(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { json(exchange, 405, Map.of("error", "GET_REQUIRED")); return; }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..") || !path.matches("/[a-zA-Z0-9_./-]+")) { json(exchange, 404, Map.of("error", "NOT_FOUND")); return; }
        try (var stream = getClass().getResourceAsStream("/web" + path)) {
            if (stream == null) { json(exchange, 404, Map.of("error", "NOT_FOUND")); return; }
            String type = path.endsWith(".html") ? "text/html" : path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "application/octet-stream";
            byte[] content = stream.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
            exchange.sendResponseHeaders(200, content.length); exchange.getResponseBody().write(content); exchange.close();
        }
    }
    private void json(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] data = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, data.length); exchange.getResponseBody().write(data); exchange.close();
    }
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var source = new DriverManagerJdbcDataSource(System.getProperty("spp.runtime.jdbc.driver", "org.h2.Driver"),
                System.getProperty("spp.runtime.jdbc.url", "jdbc:h2:mem:spp_web;DB_CLOSE_DELAY=-1"),
                System.getProperty("spp.runtime.jdbc.username"), System.getProperty("spp.runtime.jdbc.password"));
        var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(source, mapper, ignored -> null);
        Properties catalog;
        if (GeneratedApplicationDeployment.isPresent()) {
            catalog = GeneratedApplicationDeployment.install(runtime, System.getProperty("spp.application"));
        } else {
            try (var input = GeneratedWebServer.class.getResourceAsStream("/META-INF/spp/constants.properties")) {
                if (input != null) { Properties constants = new Properties(); constants.load(input); InternationalizedConstants.configure(constants.getProperty("domain"), constants); }
            }
            runtime.loadPhysicalRuntimeCatalog("/META-INF/spp/physical.properties");
            runtime.registerGeneratedServices("/META-INF/spp/services.properties");
            catalog = new Properties();
            try (var input = GeneratedWebServer.class.getResourceAsStream("/META-INF/spp/web.properties")) {
                if (input == null) throw new IllegalStateException("Generated APP web catalog missing");
                catalog.load(input);
            }
        }
        GeneratedWebServer host = new GeneratedWebServer(catalog, mapper,
                new InetSocketAddress(System.getProperty("spp.web.host", "127.0.0.1"), Integer.getInteger("spp.web.port", 8086)));
        host.start();
        try {
        DomainServiceRouter.verifyPeers();
        for (int i = 0; i < Integer.parseInt(catalog.getProperty("start.count", "0")); i++) {
            String prefix = "start." + i + ".";
            Object request = Class.forName(catalog.getProperty(prefix + "request")).getConstructor().newInstance();
            for (String participant : catalog.getProperty(prefix + "participants", "").split(",")) {
                if (participant.isBlank()) continue;
                var setter = java.util.Arrays.stream(request.getClass().getMethods())
                        .filter(m -> m.getName().equalsIgnoreCase("set" + participant) && m.getParameterCount() == 1).findFirst().orElseThrow();
                setter.invoke(request, java.util.List.class.isAssignableFrom(setter.getParameterTypes()[0])
                        ? new java.util.ArrayList<>() : setter.getParameterTypes()[0].getConstructor().newInstance());
            }
            String name = catalog.getProperty(prefix + "service"); host.attachHeader(name, request);
            DatabaseInitializationContext.execute(() -> DefaultServiceContainer.getInstance().getService(name).execute(request));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(host::close));
        DomainServiceRouter.markReady();
        } catch (RuntimeException | Error failure) { host.close(); throw failure; }
        System.out.println("SPP web: http://" + System.getProperty("spp.web.host", "127.0.0.1") + ":" + host.port());
    }
}
