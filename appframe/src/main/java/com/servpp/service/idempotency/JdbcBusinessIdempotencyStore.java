package com.servpp.service.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** Compatibility handle for an optional JDBC provider; contains no request persistence implementation. */
public class JdbcBusinessIdempotencyStore implements JdbcRequestStore {
    public static final String DEFAULT_TABLE_NAME = "business_idempotency";
    private final DataSource source;
    private final String table;
    private final JdbcRequestStore delegate;

    public JdbcBusinessIdempotencyStore(DataSource source) { this(source,DEFAULT_TABLE_NAME); }
    public JdbcBusinessIdempotencyStore(DataSource source,String table) {
        this.source = Objects.requireNonNull(source);
        this.table = table == null || table.isBlank() ? DEFAULT_TABLE_NAME : table.trim();
        if (!this.table.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid idempotency table name " + this.table);
        delegate = RequestCoordinators.provider().map(provider -> provider.jdbcStore(source,this.table)).orElse(null);
    }
    public boolean isAvailable() { return delegate != null; }
    public DataSource getDataSource() { return source; }
    public String getTableName() { return table; }
    private JdbcRequestStore required() {
        if (delegate == null) throw new IllegalStateException("REQUEST_COORDINATION_PROVIDER_REQUIRED");
        return delegate;
    }
    public Object execute(String key,Supplier<Object> action) { return required().execute(key,action); }
    public Object executeScenario(String key,Supplier<Object> action) { return required().executeScenario(key,action); }
    public Object executeRequest(String key,Duration retention,Supplier<Object> action) { return required().executeRequest(key,retention,action); }
    public boolean supportsClaims() { return delegate != null && delegate.supportsClaims(); }
    public Claim claim(String key,Duration retention) { return required().claim(key,retention); }
    public void complete(Claim claim,Object response) { required().complete(claim,response); }
    public void failed(Claim claim,boolean safelyRolledBack) { required().failed(claim,safelyRolledBack); }
    public boolean contains(String key) { return delegate != null && delegate.contains(key); }
    public void clear() { if (delegate != null) delegate.clear(); }
    public int purgeExpired() { return delegate == null ? 0 : delegate.purgeExpired(); }
    public Optional<BusinessIdempotencyRecord> findRecord(String key) { return delegate == null ? Optional.empty() : delegate.findRecord(key); }
    public int redactCompletedResponses(Collection<String> keys) { return required().redactCompletedResponses(keys); }
    public List<String> requestKeysForPrivateParticipant(String type,String id) { return required().requestKeysForPrivateParticipant(type,id); }
    public List<String> requestKeysForFailedFacts(Set<String> classes,Map<String,JsonNode> facts) { return required().requestKeysForFailedFacts(classes,facts); }

    public record BusinessIdempotencyRecord(String key,String status,Instant completedAt,String transactionId,
                                            String quantumBoundaryServiceId,String responseType,String responseJson) { }
}
