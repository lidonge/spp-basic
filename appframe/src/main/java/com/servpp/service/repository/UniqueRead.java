package com.servpp.service.repository;

import java.util.List;
import java.util.Objects;

/** Call-site predicates for the scalar participant being hydrated. */
public final class UniqueRead {
    private final Object target;
    final List<String> paths;
    final List<?> values;
    final boolean primaryKeyCovered;
    final String rejectionCode;
    private Boolean identified;

    private UniqueRead(Object target, List<String> paths, List<?> values, boolean primaryKeyCovered, String rejectionCode) {
        this.target = target;
        this.paths = paths;
        this.values = values;
        this.primaryKeyCovered = primaryKeyCovered;
        this.rejectionCode = rejectionCode;
    }

    void recordMatch(boolean matched) { identified = matched; }
    private static final ThreadLocal<UniqueRead> CURRENT = new ThreadLocal<>();

    public static void execute(Object target, List<String> paths, List<?> values, boolean primaryKeyCovered,
                               String rejectionCode, Runnable action) {
        var previous = CURRENT.get();
        var read = new UniqueRead(target, paths, values, primaryKeyCovered, rejectionCode);
        CURRENT.set(read);
        try {
            action.run();
            if (read.identified == null) throw new IllegalStateException("Unique read did not reach its repository");
            // Completing take performers can update identity independently of the selected row.
            com.servpp.service.runtime.ParticipantIdentity.confirm(target, read.identified);
        }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    static UniqueRead forTarget(Object target) {
        var read = CURRENT.get();
        return read != null && read.target == target ? read : null;
    }

    boolean matches(Object row) {
        for (int i = 0; i < paths.size(); i++) {
            if (!Objects.equals(ObjectPropertyAccess.read(row, paths.get(i)), values.get(i))) return false;
        }
        return true;
    }
}
