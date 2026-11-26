package com.servpp.service.serial;

import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import java.util.Map;
import javax.sql.DataSource;

/** Engines that allocate outside the business transaction open their own connections from PHY config. */
public final class SerialDataSources {
    private SerialDataSources() { }
    /** Reads {@code datasourceUrl}, {@code driverClassName}, {@code username} and {@code password}. */
    public static DataSource from(Map<String, String> configuration, String engine) {
        String url = configuration.get("datasourceUrl");
        if (url == null || url.isBlank()) throw new IllegalArgumentException("SERIAL_CONFIGURATION_REQUIRED: datasourceUrl of " + engine);
        return new DriverManagerJdbcDataSource(configuration.get("driverClassName"), url,
                configuration.get("username"), configuration.get("password"));
    }
    /** A SQL identifier, optionally schema-qualified; never spliced into SQL otherwise. */
    public static String identifier(String value, String what) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?"))
            throw new IllegalArgumentException("SERIAL_CONFIGURATION_INVALID: " + what + " must be a SQL identifier: " + value);
        return value;
    }
}
