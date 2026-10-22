package com.servpp.lang.models.app;

public record InferredObjectSnapshotRule(String objectType, String referenceType, String indexName,
                                         ObjectSnapshotRule.Timing timing, int insertionIndex,
                                         String repositoryName) {
    public InferredObjectSnapshotRule(String objectType, ObjectSnapshotRule.Timing timing) {
        this(objectType, null, null, timing, -1);
    }

    public InferredObjectSnapshotRule(String objectType, String referenceType, ObjectSnapshotRule.Timing timing) {
        this(objectType, referenceType, null, timing, -1);
    }

    public InferredObjectSnapshotRule(String objectType, String referenceType, String indexName,
                                      ObjectSnapshotRule.Timing timing) {
        this(objectType, referenceType, indexName, timing, -1);
    }

    public InferredObjectSnapshotRule(String objectType, String referenceType, String indexName,
                                      ObjectSnapshotRule.Timing timing, int insertionIndex) {
        this(objectType, referenceType, indexName, timing, insertionIndex, null);
    }
}
