package com.servpp.lang.models;

import org.apache.commons.lang3.StringEscapeUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Resolves APP entries and inherited SPP defaults once for validation and generation. */
record PlayCodeBinding(SppEnum enumeration, Map<String, List<String>> overrides) {
    private List<String> codes(SppEnumItem item) {
        return overrides.getOrDefault(item.getName(), item.getAliases());
    }

    void validate(String prefix, List<String> errors) {
        for (String member : overrides.keySet()) {
            if (enumeration.getItems().stream().noneMatch(item -> item.getName().equals(member)))
                errors.add(prefix + "unknown code table member " + member);
        }
        var owners = new HashMap<String, String>();
        for (var item : enumeration.getItems()) {
            for (String code : codes(item)) {
                String previous = owners.putIfAbsent(code, item.getName());
                if (previous != null && !previous.equals(item.getName()))
                    errors.add(prefix + "code '" + code + "' maps to both " + previous + " and " + item.getName());
            }
        }
    }

    String declaration(String field) {
        return "    private static final com.servpp.service.runtime.PlayCodeTable<" + enumeration.getPackageName()
                + "." + enumeration.getName() + "> " + field + " = " + expression() + ";\n";
    }

    String expression() {
        String type = enumeration.getPackageName() + "." + enumeration.getName();
        String entries = overrides == null ? "" : enumeration.getItems().stream()
                .filter(item -> item.getFallback() == null || !codes(item).isEmpty())
                .map(item -> "java.util.Map.entry(" + type + "." + item.getName() + ", java.util.List.of("
                        + codes(item).stream().map(PlayCodeBinding::literal).collect(Collectors.joining(", ")) + "))")
                .collect(Collectors.joining(", "));
        String factory = overrides == null ? enumeration.codeFactory() : "fromUnmappedCode";
        return "new com.servpp.service.runtime.PlayCodeTable<>(" + type + "::" + factory
                + ", java.util.Map.ofEntries(" + entries + "))";
    }

    private static String literal(String value) {
        return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
    }
}
