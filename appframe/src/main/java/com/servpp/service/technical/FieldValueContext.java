package com.servpp.service.technical;

import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.repository.ObjectPropertyAccess;
import com.servpp.service.runtime.InvocationContext;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What a {@link FieldValueInterceptor} may see when it produces one field value. */
public final class FieldValueContext {
    private final String entityName, fieldName;
    private final Class<?> fieldType;
    private final Object record;
    private final Map<String, String> configuration;
    private final java.util.function.Supplier<com.servpp.service.repository.ObjectRepository> repository;

    FieldValueContext(String entityName, String fieldName, Class<?> fieldType, Object record, Map<String, String> configuration,
                      java.util.function.Supplier<com.servpp.service.repository.ObjectRepository> repository) {
        this.entityName = entityName; this.fieldName = fieldName; this.fieldType = fieldType;
        this.record = record; this.configuration = Map.copyOf(configuration); this.repository = repository;
    }
    /** Test and embedding hook: a context over an arbitrary record object. */
    public static FieldValueContext of(String entityName, String fieldName, Class<?> fieldType, Object record,
                                       Map<String, String> configuration) {
        return new FieldValueContext(entityName, fieldName, fieldType, record, configuration, null);
    }
    public String entityName() { return entityName; }
    public String fieldName() { return fieldName; }
    /** The Java type of the target field. */
    public Class<?> fieldType() { return fieldType; }
    /** A field value of the record being inserted, by property path. */
    public Object value(String path) { return ObjectPropertyAccess.read(record, path); }
    /** The PHY configuration of this technical service, placeholders resolved. */
    public Map<String, String> configuration() { return configuration; }
    /** The trusted invocation context established by the ingress. */
    public InvocationContext.Header invocation() { return InvocationContext.current(); }
    /**
     * The JDBC connection of the business transaction in progress on the record's repository resource. Work done on
     * it commits and rolls back with the business transaction. Without a bound repository it is the only
     * connection of the current transaction scope, when exactly one is bound.
     */
    public Optional<Connection> connection() {
        if (repository != null && repository.get() != null) return repository.get().transactionConnection();
        List<Connection> current = JdbcTransactionScope.currentConnections();
        return current.size() == 1 ? Optional.of(current.get(0)) : Optional.empty();
    }
}
