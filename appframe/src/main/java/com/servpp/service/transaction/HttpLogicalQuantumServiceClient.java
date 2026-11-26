package com.servpp.service.transaction;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpLogicalQuantumServiceClient implements LogicalQuantumServiceClient {
    private static final String LOGICAL_COMMAND_PATH = "logical/commands";

    private final URI commandUri;
    private final URI capabilitiesUri;
    private final long timeoutMillis;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final QServiceClientSecurity security;

    /** An https endpoint with the JVM default trust and no client certificate or token. */
    public HttpLogicalQuantumServiceClient(String endpoint, long timeoutMillis) {
        this(endpoint, timeoutMillis, QServiceClientSecurity.defaults());
    }

    /** Builds its own {@link HttpClient} from the TLS settings of {@code security}. */
    public HttpLogicalQuantumServiceClient(String endpoint, long timeoutMillis, QServiceClientSecurity security) {
        this(endpoint, timeoutMillis,
                new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                security.newHttpClient(), security);
    }

    /** Package-private test seam: plain http is allowed. */
    HttpLogicalQuantumServiceClient(String endpoint, long timeoutMillis,
                                    ObjectMapper objectMapper, HttpClient httpClient) {
        this(endpoint, timeoutMillis, objectMapper, httpClient, QServiceClientSecurity.insecure());
    }

    /**
     * Uses a caller-built {@link HttpClient}. Its TLS setup is the caller's business: {@code security} then
     * contributes only the endpoint rule and the bearer token.
     */
    public HttpLogicalQuantumServiceClient(String endpoint, long timeoutMillis, ObjectMapper objectMapper,
                                           HttpClient httpClient, QServiceClientSecurity security) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Quantum service timeoutMillis must be positive.");
        }
        this.security = security == null ? QServiceClientSecurity.defaults() : security;
        this.commandUri = commandUri(endpoint);
        this.capabilitiesUri = resolve(endpoint, "capabilities");
        this.security.requireEndpoint(commandUri);
        this.timeoutMillis = timeoutMillis;
        this.objectMapper = objectMapper == null
                ? new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                : objectMapper;
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    @Override
    public LogicalQuantumServiceResponse execute(LogicalQuantumServiceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("logical quantum command must not be null");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(command);
            HttpRequest.Builder request = HttpRequest.newBuilder(commandUri)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            security.authorize(request);
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            QServiceClientSecurity.requireAuthorized(response.statusCode(), "QService logical");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("QService logical HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            LogicalQuantumServiceResponse decoded =
                    objectMapper.readValue(response.body(), LogicalQuantumServiceResponse.class);
            decoded.requireIdentity(command);
            return decoded;
        } catch (IOException e) {
            throw new IllegalStateException("QService logical HTTP I/O failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QService logical HTTP interrupted.", e);
        }
    }

    @Override
    public LogicalQuantumServiceResponse query(String namespace, String requestKey) {
        URI uri = URI.create(commandUri + "/" + segment(namespace) + "/" + segment(requestKey));
        LogicalQuantumServiceResponse decoded = get(uri, LogicalQuantumServiceResponse.class);
        decoded.requireIdentity(namespace, requestKey);
        return decoded;
    }

    @Override
    public LogicalQuantumCapabilities capabilities() {
        return get(capabilitiesUri, LogicalQuantumCapabilities.class);
    }

    private <T> T get(URI uri, Class<T> type) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Accept", "application/json")
                    .GET();
            security.authorize(request);
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            QServiceClientSecurity.requireAuthorized(response.statusCode(), "QService logical");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("QService logical HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), type);
        } catch (IOException e) {
            throw new IllegalStateException("QService logical HTTP I/O failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QService logical HTTP interrupted.", e);
        }
    }

    /** Percent-encodes a path segment byte by byte; only the RFC 3986 unreserved bytes stay. */
    static String segment(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("path segment must not be blank");
        }
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~') {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    private static URI commandUri(String endpoint) {
        return resolve(endpoint, LOGICAL_COMMAND_PATH);
    }

    private static URI resolve(String endpoint, String path) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        URI base;
        try {
            base = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Quantum service endpoint is invalid: " + normalized + ".", e);
        }
        String scheme = base.getScheme();
        if (!base.isAbsolute() || base.getHost() == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Quantum service endpoint must be an absolute http or https URI without query or fragment.");
        }
        String value = base.toString();
        if (!value.endsWith("/")) {
            value += "/";
        }
        return URI.create(value + path);
    }

    public String getCommandUri() {
        return commandUri.toString();
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
