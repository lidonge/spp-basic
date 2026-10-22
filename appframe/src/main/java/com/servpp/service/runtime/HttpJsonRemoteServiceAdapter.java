package com.servpp.service.runtime;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpJsonRemoteServiceAdapter implements MethodAwareRemoteServiceAdapter {
    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private final String serviceName;
    private final Map<String, String> configuration;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpJsonRemoteServiceAdapter(PhysicalRuntimeCatalog.RemoteAdapter adapter) {
        this(adapter.serviceName(), adapter.configuration(), new ObjectMapper(), HttpClient.newHttpClient());
    }

    public HttpJsonRemoteServiceAdapter(String serviceName, Map<String, String> configuration,
                                        ObjectMapper objectMapper, HttpClient httpClient) {
        this.serviceName = serviceName;
        this.configuration = Map.copyOf(configuration);
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public Set<String> serviceNames() {
        return Set.of(serviceName);
    }

    @Override
    public String implementationType() {
        return "httpJson";
    }

    @Override
    public Map<String, String> configuration() {
        return configuration;
    }

    @Override
    public List<String> validationErrors(String serviceName) {
        List<String> errors = new ArrayList<>();
        String endpoint = configuration.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            errors.add("Remote service " + serviceName + " httpJson adapter must configure endpoint.");
        } else {
            try {
                validateEndpoint(serviceName, URI.create(endpoint));
            } catch (IllegalArgumentException e) {
                errors.add("Remote service " + serviceName + " httpJson adapter endpoint is invalid: "
                        + endpoint + ".");
            }
        }
        String timeoutMillis = configuration.get("timeoutMillis");
        if (timeoutMillis != null && !timeoutMillis.isBlank()) {
            try {
                parseTimeoutMillis(serviceName, timeoutMillis);
            } catch (IllegalStateException e) {
                errors.add(e.getMessage());
            }
        }
        if (Boolean.parseBoolean(configuration.getOrDefault("requestUpdateEntry", "false"))
                && configuration.getOrDefault("callerId", "").isBlank())
            errors.add("Remote update service " + serviceName + " must configure callerId for request idempotency.");
        return List.copyOf(errors);
    }

    @Override
    public Object execute(String serviceName, Object... arguments) {
        return invoke(serviceName, null, arguments);
    }

    @Override
    public Object execute(String serviceName, Method method, Object... arguments) {
        return invoke(serviceName, method, arguments);
    }

    private Object invoke(String serviceName, Method method, Object[] arguments) {
        if (!this.serviceName.equals(serviceName)) {
            throw new IllegalArgumentException("Adapter for remote service " + this.serviceName
                    + " cannot execute " + serviceName + ".");
        }
        try {
            HttpResponse<String> response = httpClient.send(createRequest(arguments),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Remote service " + serviceName + " HTTP "
                        + response.statusCode() + ": " + response.body());
            }
            if (method != null && method.getReturnType() == Void.TYPE) {
                hydrateParticipants(response.body(), arguments);
                return null;
            }
            return readResponse(response.body(), method);
        } catch (IOException e) {
            throw new IllegalStateException("Remote service " + serviceName + " HTTP call failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Remote service " + serviceName + " HTTP call interrupted.", e);
        }
    }

    private HttpRequest createRequest(Object[] arguments) throws IOException {
        String body = objectMapper.writeValueAsString(requestBody(arguments));
        var builder = HttpRequest.newBuilder(endpoint())
                .timeout(timeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        var identity = OutboundRequestContext.current().orElse(null);
        String caller = identity == null ? configuration.getOrDefault("callerId", "") : identity.callerId();
        if (caller.isBlank() && Boolean.parseBoolean(configuration.getOrDefault("requestUpdateEntry", "false")))
            throw new IllegalStateException("OUTBOUND_CALLER_ID_REQUIRED");
        if (!caller.isBlank()) {
            String serial = identity == null ? java.util.UUID.randomUUID().toString() : identity.requestSerialNumber();
            builder.header("X-SPP-Caller-Id", caller).header("X-SPP-Request-Serial", serial)
                    .header("X-SPP-Call-Mode", "REMOTE").header("X-SPP-Call-Origin", "SERVICE");
        }
        return builder.build();
    }

    private Object requestBody(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return Map.of();
        }
        if (arguments.length == 1) {
            String roleType = configuration.get("roleType");
            if ("true".equalsIgnoreCase(configuration.getOrDefault("roleAdapter", "false"))
                    && roleType != null && !roleType.isBlank()) {
                return RoleIdentityContext.performers(arguments[0], roleType)
                        .<Object>map(performers -> Map.of(
                                "roleType", roleType,
                                "role", arguments[0],
                                "performers", performers))
                        .orElse(arguments[0]);
            }
            return arguments[0];
        }
        return arguments;
    }

    /** Physical protocol: modified participants plus explicit confirmation paths. */
    private void hydrateParticipants(String body, Object[] arguments) throws IOException {
        var response = objectMapper.readTree(body);
        var participants = response.get("participants");
        if (participants == null || arguments == null)
            throw new IllegalStateException("Remote void service requires a participants response envelope.");
        if (arguments.length == 1 && !participants.isArray()) {
            ParticipantFacts.hydrate(arguments[0], objectMapper.treeToValue(participants, arguments[0].getClass()));
        } else {
            if (!participants.isArray() || participants.size() != arguments.length)
                throw new IllegalStateException("Remote participants response must match the supplied participant count.");
            for (int i = 0; i < arguments.length; i++)
                ParticipantFacts.hydrate(arguments[i], objectMapper.treeToValue(participants.get(i), arguments[i].getClass()));
        }
        var confirmations = response.get("confirmations");
        if (confirmations != null) {
            if (!confirmations.isArray() || confirmations.size() != arguments.length)
                throw new IllegalStateException("Remote confirmations must match the supplied participant count.");
            for (int i = 0; i < arguments.length; i++) {
                Map<String, Boolean> snapshot = objectMapper.convertValue(confirmations.get(i),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Boolean>>() { });
                ParticipantIdentity.restore(arguments[i], snapshot);
            }
        }
    }

    private Object readResponse(String body, Method method) throws IOException {
        if (method != null && method.getReturnType() == Void.TYPE) {
            return null;
        }
        if (method == null) {
            return objectMapper.readValue(body, Object.class);
        }
        JavaType returnType = objectMapper.getTypeFactory().constructType(method.getGenericReturnType());
        return objectMapper.readValue(body, returnType);
    }

    private URI endpoint() {
        String endpoint = configuration.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " httpJson adapter must configure endpoint.");
        }
        try {
            URI uri = URI.create(endpoint);
            validateEndpoint(serviceName, uri);
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " httpJson adapter endpoint is invalid: " + endpoint + ".", e);
        }
    }

    private void validateEndpoint(String serviceName, URI endpoint) {
        String scheme = endpoint.getScheme();
        if (scheme == null || endpoint.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Remote service " + serviceName
                    + " httpJson adapter endpoint must be an absolute http or https URI.");
        }
    }

    private Duration timeout() {
        String configured = configuration.get("timeoutMillis");
        if (configured == null || configured.isBlank()) {
            return Duration.ofMillis(DEFAULT_TIMEOUT_MILLIS);
        }
        return Duration.ofMillis(parseTimeoutMillis(serviceName, configured));
    }

    private static long parseTimeoutMillis(String serviceName, String configured) {
        try {
            long timeoutMillis = Long.parseLong(configured.trim());
            if (timeoutMillis <= 0) {
                throw new IllegalStateException("Remote service " + serviceName
                        + " httpJson adapter timeoutMillis must be positive.");
            }
            return timeoutMillis;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Remote service " + serviceName
                    + " httpJson adapter timeoutMillis must be a number.", e);
        }
    }
}
