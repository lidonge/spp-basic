package com.servpp.service.technical;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Explicit technical request/response protocol. Never receives mutable SPP participants. */
public interface TechnicalRequestService {
    default void configure(Map<String,String> configuration) {}
    JsonNode invoke(JsonNode request) throws Exception;
}
