package com.servpp.service.idempotency;

import java.util.*;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.JsonNode;

/** Optional JDBC request persistence and authorized response-maintenance operations. */
public interface JdbcRequestStore extends CoordinatedRequestStore, RequestClaimStore {
    DataSource getDataSource();
    String getTableName();
    Optional<JdbcBusinessIdempotencyStore.BusinessIdempotencyRecord> findRecord(String key);
    int redactCompletedResponses(Collection<String> keys);
    List<String> requestKeysForPrivateParticipant(String type,String id);
    List<String> requestKeysForFailedFacts(Set<String> requestClasses,Map<String,JsonNode> expectedFacts);
}
