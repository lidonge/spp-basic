package com.servpp.service.idempotency;

import java.time.Clock;
import java.util.function.Supplier;
import javax.sql.DataSource;
import com.servpp.service.runtime.ScopeMetadataProvider;

/** Optional request strategy and storage factory. The basic runtime supplies no implementation. */
public interface RequestCoordinationProvider {
    CoordinatedRequestStore memoryStore(Clock clock);
    JdbcRequestStore jdbcStore(DataSource source,String table);
    Object execute(String service,Object request,ScopeMetadataProvider metadata,boolean scenario,
                   Supplier<RequestIdempotencyStore> store,Supplier<Object> action);
    boolean wasReplay();
    void resetReplay();
}
