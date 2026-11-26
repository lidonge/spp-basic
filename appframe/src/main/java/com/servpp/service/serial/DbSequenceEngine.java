package com.servpp.service.serial;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Engine over a native database sequence: {@code config sequence = "GL_DAILY_SEQ";} plus
 * {@code datasourceUrl}/{@code driverClassName}/{@code username}/{@code password}. A native sequence has one
 * counter, so every scope shares it; it runs outside the business transaction and may leave gaps.
 */
public final class DbSequenceEngine implements SerialEngine {
    private DataSource dataSource;
    private String sequence;

    @Override public void configure(Map<String, String> configuration) {
        sequence = SerialDataSources.identifier(configuration.get("sequence"), "sequence");
        dataSource = SerialDataSources.from(configuration, "dbSequence");
    }

    @Override public long next(SerialRequest request) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
            String sql;
            if (product.contains("postgres")) sql = "select nextval('" + sequence + "')";
            else if (product.contains("oracle")) sql = "select " + sequence + ".nextval from dual";
            else if (product.contains("h2") || product.contains("db2") || product.contains("hsql")
                    || product.contains("sql server") || product.contains("mariadb")) sql = "select next value for " + sequence
                    + (product.contains("hsql") ? " from (values(0))" : "");
            else throw new IllegalStateException("SERIAL_SEQUENCE_UNSUPPORTED_DATABASE: " + product);
            try (ResultSet row = statement.executeQuery(sql)) {
                if (!row.next()) throw new IllegalStateException("SERIAL_SEQUENCE_EMPTY: " + sequence);
                return row.getLong(1);
            }
        } catch (SQLException failure) { throw new IllegalStateException("SERIAL_SEQUENCE_FAILED: " + sequence, failure); }
    }
}
