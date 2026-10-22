package com.servpp.service.transaction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class TccRecoveryPayloadCodec {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TccRecoveryPayloadCodec() {
    }

    public static String encode(Object[] arguments) {
        Object[] values = arguments == null ? new Object[0] : arguments;
        List<ArgumentPayload> payload = new ArrayList<>(values.length);
        for (Object value : values) {
            payload.add(new ArgumentPayload(value == null ? null : value.getClass().getName(),
                    OBJECT_MAPPER.valueToTree(value)));
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize TCC recovery payload", e);
        }
    }

    public static Object[] decode(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new Object[0];
        }
        try {
            ArgumentPayload[] payload = OBJECT_MAPPER.readValue(payloadJson, ArgumentPayload[].class);
            Object[] arguments = new Object[payload.length];
            for (int i = 0; i < payload.length; i++) {
                ArgumentPayload item = payload[i];
                if (item.className() == null || item.value() == null || item.value().isNull()) {
                    arguments[i] = null;
                    continue;
                }
                arguments[i] = OBJECT_MAPPER.treeToValue(item.value(), Class.forName(item.className()));
            }
            return arguments;
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalStateException("Cannot deserialize TCC recovery payload", e);
        }
    }

    private record ArgumentPayload(String className, JsonNode value) {
        @JsonCreator
        private ArgumentPayload(@JsonProperty("className") String className,
                                @JsonProperty("value") JsonNode value) {
            this.className = className;
            this.value = value;
        }
    }
}
