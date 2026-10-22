package com.servpp.lang.models.app;

import java.util.List;

/** A repository write owns its participant table even when keys come from performers. */
public record PersistenceRule(String operation, List<RepositoryHydrationRule.KeyTarget> keys, String objectType) {
    public PersistenceRule { keys = List.copyOf(keys); }
    public PersistenceRule(String operation, List<RepositoryHydrationRule.KeyTarget> keys) {
        this(operation, keys, keys.get(0).getObjectType());
    }
    public int argumentIndex() { return keys.get(0).argumentIndex(); }
}
