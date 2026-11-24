package com.servpp.service.runtime;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Bidirectional provider vocabulary for one APP play field. */
public final class PlayCodeTable<T extends WireMappedEnum> {
    private final Function<String, T> fallback;
    private final Map<String, T> members = new HashMap<>();
    // An open enum with an unknown raw code can compare equal to a different singleton.
    private final Map<WireMappedEnum, String> canonical = new IdentityHashMap<>();

    public PlayCodeTable(Function<String, T> fallback, Map<T, List<String>> codes) {
        this.fallback = fallback;
        codes.forEach((member, aliases) -> {
            canonical.put(member, aliases.isEmpty() ? null : aliases.get(0));
            for (String alias : aliases) members.put(alias, member);
        });
    }

    public T decode(String code) {
        T member = members.get(code);
        return member == null ? fallback.apply(code) : member;
    }

    public static Object encodeField(Map<String, PlayCodeTable<?>> tables, String field, Object value) {
        var table = tables.get(field);
        return table == null ? WireMappedEnum.wireValue(value) : table.encode((WireMappedEnum) value);
    }

    public String encode(WireMappedEnum member) {
        if (member == null) return null;
        if (!canonical.containsKey(member)) return member.getValue();
        String code = canonical.get(member);
        if (code == null) throw new IllegalArgumentException("No provider code for " + member);
        return code;
    }
}
