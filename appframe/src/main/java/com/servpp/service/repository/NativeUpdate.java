package com.servpp.service.repository;

import java.util.*;

/** APP-mapped native columns, after ownership filtering and snapshot revision CAS. */
public record NativeUpdate(String table, List<Value> assignments, List<Value> keys, Set<String> selectedPaths) {
    public record Value(String column, Object value) {
        public Value { identifier(column); }
    }
    public NativeUpdate {
        identifier(table);
        assignments = List.copyOf(assignments); keys = List.copyOf(keys); selectedPaths = Set.copyOf(selectedPaths);
        if (assignments.isEmpty() || keys.isEmpty()) throw new IllegalArgumentException("NATIVE_UPDATE_COLUMNS_REQUIRED");
        var names = new HashSet<String>();
        for (Value key : keys) {
            if (key.value() == null || !names.add(key.column().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("NATIVE_UPDATE_KEY_INVALID");
        }
        for (Value assignment : assignments) if (!names.add(assignment.column().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("NATIVE_UPDATE_COLUMN_CONFLICT");
    }
    private static void identifier(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("NATIVE_UPDATE_IDENTIFIER_INVALID");
    }
    public List<Value> parameters() {
        var values = new ArrayList<>(assignments); values.addAll(keys); return List.copyOf(values);
    }
    /** Identifiers are APP-mapped; each fact remains a separately bound parameter. */
    public String sql() {
        return "update " + table + " set " + assignments.stream().map(value -> value.column()+"=?")
                .collect(java.util.stream.Collectors.joining(",")) + " where "
                + keys.stream().map(value -> value.column()+"=?").collect(java.util.stream.Collectors.joining(" and "));
    }
}
