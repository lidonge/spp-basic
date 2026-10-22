package com.servpp.service.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

public class DriverManagerJdbcDataSource implements DataSource {
    private final String url;
    private final String username;
    private final String password;
    private final String schema;
    private PrintWriter logWriter;
    private int loginTimeout;

    public DriverManagerJdbcDataSource(String driverClassName, String url, String username, String password) {
        this(driverClassName,url,username,password,null);
    }

    public DriverManagerJdbcDataSource(String driverClassName, String url, String username, String password, String schema) {
        if (driverClassName != null && !driverClassName.isBlank()) {
            try {
                Class.forName(driverClassName);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot load JDBC driver " + driverClassName, e);
            }
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("JDBC url must not be blank.");
        }
        this.url = url;
        this.username = username;
        this.password = password;
        this.schema = schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (username == null || username.isBlank()) {
            return configureConnection(DriverManager.getConnection(url));
        }
        return configureConnection(DriverManager.getConnection(url, username, password == null ? "" : password));
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return configureConnection(DriverManager.getConnection(url, username, password));
    }

    private Connection configureConnection(Connection connection) throws SQLException {
        if (schema==null || schema.isBlank()) return connection;
        try {
            connection.setSchema(schema);
            if (!schema.equals(connection.getSchema())) throw new SQLException("JDBC_SCHEMA_SELECTION_NOT_CONFIRMED");
            return connection;
        }
        catch (SQLException | RuntimeException failure) {
            try { connection.close(); } catch (SQLException closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    public String getUrl() {
        return url;
    }
}
