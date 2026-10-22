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
    private final long timeoutMillis;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpLogicalQuantumServiceClient(String endpoint, long timeoutMillis) {
        this(endpoint, timeoutMillis,
                new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                HttpClient.newHttpClient());
    }

    HttpLogicalQuantumServiceClient(String endpoint, long timeoutMillis,
                                    ObjectMapper objectMapper, HttpClient httpClient) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Quantum service timeoutMillis must be positive.");
        }
        this.commandUri = commandUri(endpoint);
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
            HttpRequest request = HttpRequest.newBuilder(commandUri)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

    private static URI commandUri(String endpoint) {
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
        return URI.create(value + LOGICAL_COMMAND_PATH);
    }

    public String getCommandUri() {
        return commandUri.toString();
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
