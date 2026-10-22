package com.servpp.service.transaction;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpLogicalQuantumProvisioningClient
        implements LogicalQuantumProvisioningClient {
    private static final String LOGICAL_PROVISIONING_PATH = "logical/provisions";

    private final URI provisioningUri;
    private final long timeoutMillis;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpLogicalQuantumProvisioningClient(String endpoint, long timeoutMillis) {
        this(endpoint, timeoutMillis,
                new ObjectMapper().enable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                HttpClient.newHttpClient());
    }

    HttpLogicalQuantumProvisioningClient(
            String endpoint, long timeoutMillis,
            ObjectMapper objectMapper, HttpClient httpClient) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "Quantum service timeoutMillis must be positive.");
        }
        this.provisioningUri = provisioningUri(endpoint);
        this.timeoutMillis = timeoutMillis;
        this.objectMapper = (objectMapper == null
                ? new ObjectMapper() : objectMapper).copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.httpClient = httpClient == null
                ? HttpClient.newHttpClient() : httpClient;
    }

    @Override
    public LogicalQuantumProvisioningResponse provision(
            LogicalQuantumProvisioningRequest command) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "logical quantum provisioning request must not be null");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(command);
            HttpRequest request = HttpRequest.newBuilder(provisioningUri)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("QService provisioning HTTP "
                        + response.statusCode() + ": " + response.body());
            }
            LogicalQuantumProvisioningResponse decoded = objectMapper.readValue(
                    response.body(), LogicalQuantumProvisioningResponse.class);
            decoded.requireIdentity(command);
            return decoded;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "QService provisioning HTTP I/O failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "QService provisioning HTTP interrupted.", e);
        }
    }

    private static URI provisioningUri(String endpoint) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        URI base;
        try {
            base = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Quantum service endpoint is invalid: " + normalized + ".", e);
        }
        String scheme = base.getScheme();
        if (!base.isAbsolute() || base.getHost() == null
                || (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme))
                || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Quantum service endpoint must be an absolute http or https URI"
                            + " without query or fragment.");
        }
        String value = base.toString();
        if (!value.endsWith("/")) {
            value += "/";
        }
        return URI.create(value + LOGICAL_PROVISIONING_PATH);
    }

    public String getProvisioningUri() {
        return provisioningUri.toString();
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
