package com.servpp.service.repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class CompositeObjectId {
    private static final String PREFIX = "spp-composite:";

    private CompositeObjectId() {
    }

    public static String encode(List<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Composite object id requires at least one value.");
        }
        List<String> encodedValues = new ArrayList<>();
        for (Object value : values) {
            if (value == null || value.toString().isBlank()) {
                throw new IllegalArgumentException("Composite object id values must be non-empty.");
            }
            encodedValues.add(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(value.toString().getBytes(StandardCharsets.UTF_8)));
        }
        return PREFIX + String.join(".", encodedValues);
    }

    public static List<String> decode(String objectId, int expectedParts) {
        if (objectId == null || !objectId.startsWith(PREFIX) || expectedParts <= 0) {
            return List.of();
        }
        String payload = objectId.substring(PREFIX.length());
        if (payload.isBlank()) {
            return List.of();
        }
        String[] parts = payload.split("\\.", -1);
        if (parts.length != expectedParts) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                return List.of();
            }
            try {
                values.add(new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        return List.copyOf(values);
    }
}
