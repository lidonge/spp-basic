package com.servpp.lang.models.app;

/** The expected version comes from a separate pre-update argument. */
public record PersistenceVersion(RepositoryHydrationRule.KeyTarget expected,
                                 RepositoryHydrationRule.KeyTarget proposed, boolean advanceVersion) {
    public PersistenceVersion(RepositoryHydrationRule.KeyTarget expected, RepositoryHydrationRule.KeyTarget proposed) {
        this(expected, proposed, false);
    }
}
