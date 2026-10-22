package com.servpp.service.transaction;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpJsonQuantumServiceClient implements QuantumServiceClient {
    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private final String endpoint;
    private final long timeoutMillis;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpJsonQuantumServiceClient(String endpoint) {
        this(endpoint, DEFAULT_TIMEOUT_MILLIS);
    }

    public HttpJsonQuantumServiceClient(String endpoint, long timeoutMillis) {
        this(endpoint, timeoutMillis, new ObjectMapper(), HttpClient.newHttpClient());
    }

    public HttpJsonQuantumServiceClient(String endpoint, long timeoutMillis,
                                        ObjectMapper objectMapper, HttpClient httpClient) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Quantum service timeoutMillis must be positive.");
        }
        this.timeoutMillis = timeoutMillis;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    @Override
    public void commit(QuantumServiceCommitRequest request) {
        post(request);
    }

    @Override
    public void reverse(QuantumServiceReversalRequest request) {
        post(request);
    }

    private void post(Object request) {
        try {
            HttpResponse<String> response = httpClient.send(createRequest(request),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Quantum service HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            validateAcceptedResponse(response.body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Quantum service response is not valid JSON.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Quantum service HTTP call failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Quantum service HTTP call interrupted.", e);
        }
    }

    private void validateAcceptedResponse(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return;
        }
        JsonNode response = objectMapper.readTree(body);
        if (response == null || response.isMissingNode() || response.isNull()) {
            return;
        }
        if (!response.isObject()) {
            throw new IllegalStateException("Quantum service response must be a JSON object: " + body);
        }
        JsonNode accepted = response.get("accepted");
        if (accepted != null && accepted.isBoolean() && !accepted.booleanValue()) {
            throw rejectedResponse(body);
        }
        String status = text(response, "status");
        if ("REJECTED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "ERROR".equalsIgnoreCase(status)) {
            throw rejectedResponse(body);
        }
        String error = text(response, "error");
        if (!error.isBlank()) {
            throw rejectedResponse(body);
        }
        JsonNode errors = response.get("errors");
        if (errors != null && errors.isArray() && !errors.isEmpty()) {
            throw rejectedResponse(body);
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? "" : field.asText("").trim();
    }

    private static IllegalStateException rejectedResponse(String body) {
        return new IllegalStateException("Quantum service rejected boundary: " + body);
    }

    private HttpRequest createRequest(Object request) throws IOException {
        String body = objectMapper.writeValueAsString(request);
        return HttpRequest.newBuilder(endpoint())
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private URI endpoint() {
        if (endpoint.isBlank()) {
            throw new IllegalStateException("Quantum service endpoint must not be blank.");
        }
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException("Quantum service endpoint must be an absolute http or https URI.");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Quantum service endpoint is invalid: " + endpoint + ".", e);
        }
    }

    public String getEndpoint() {
        return endpoint;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
