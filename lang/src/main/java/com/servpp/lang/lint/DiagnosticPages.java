package com.servpp.lang.lint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DiagnosticPages {
    private DiagnosticPages() { }
    private static final ConcurrentHashMap<String,String> rules = new ConcurrentHashMap<>();

    public static Optional<String> explain(String code) {
        if (!code.matches("[A-Z][A-Z0-9_]{1,80}")) return Optional.empty();
        try (var stream = DiagnosticPages.class.getResourceAsStream("/diagnostics/" + code + ".md")) {
            return stream == null ? Optional.empty() : Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static String rule(String code, String fallback) {
        return rules.computeIfAbsent(code, key -> explain(key).stream().flatMap(String::lines)
                .filter(line -> !line.isBlank() && !line.startsWith("#")).findFirst().orElse(fallback));
    }
}
