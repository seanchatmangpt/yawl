/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.Stoppable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * HikariCP Connection Provider for Hibernate.
 * Replaces c3p0 with modern HikariCP connection pooling optimized for Java 25 virtual threads.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 2026-02-15
 */
public class HikariCPConnectionProvider implements ConnectionProvider, Configurable, Stoppable {

    private static final long serialVersionUID = 1L;
    private static final Logger _log = LogManager.getLogger(HikariCPConnectionProvider.class);

    private static final String HIKARI_PREFIX = "hibernate.hikari.";
    private static final String CONNECTION_PREFIX = "hibernate.connection.";

    private HikariDataSource dataSource;
    private boolean autoCommit;
    private Integer isolationLevel;

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void configure(Map configValues) {
        try {
            _log.info("Configuring HikariCP connection pool");

            HikariConfig config = new HikariConfig();

            // Extract JDBC connection properties
            String jdbcUrl = getProperty(configValues, "url");
            String username = getProperty(configValues, "username");
            String password = getProperty(configValues, "password");
            String driverClassName = getProperty(configValues, "driver_class");

            if (jdbcUrl == null) {
                throw new IllegalArgumentException("JDBC URL is required (hibernate.connection.url)");
            }

            config.setJdbcUrl(jdbcUrl);

            if (username != null) {
                config.setUsername(username);
            }

            if (password != null) {
                config.setPassword(password);
            }

            if (driverClassName != null) {
                config.setDriverClassName(driverClassName);
            }

            // Pool name
            config.setPoolName("YAWL-HikariCP-Pool");

            // Configure pool size - optimized for virtual threads
            config.setMaximumPoolSize(getIntProperty(configValues, "maximumPoolSize", 20));
            config.setMinimumIdle(getIntProperty(configValues, "minimumIdle", 5));

            // Configure timeouts
            config.setConnectionTimeout(getLongProperty(configValues, "connectionTimeout", 30000L));
            config.setIdleTimeout(getLongProperty(configValues, "idleTimeout", 600000L));
            config.setMaxLifetime(getLongProperty(configValues, "maxLifetime", 1800000L));
            config.setValidationTimeout(getLongProperty(configValues, "validationTimeout", 5000L));

            // Connection test query
            String testQuery = getProperty(configValues, "connectionTestQuery");
            if (testQuery != null) {
                config.setConnectionTestQuery(testQuery);
            }

            // Leak detection
            long leakThreshold = getLongProperty(configValues, "leakDetectionThreshold", 0L);
            if (leakThreshold > 0) {
                config.setLeakDetectionThreshold(leakThreshold);
            }

            // Auto-commit
            String autoCommitStr = getProperty(configValues, "autocommit");
            this.autoCommit = autoCommitStr == null || Boolean.parseBoolean(autoCommitStr);
            config.setAutoCommit(this.autoCommit);

            // Isolation level
            String isolationStr = getProperty(configValues, "isolation");
            if (isolationStr != null) {
                this.isolationLevel = Integer.parseInt(isolationStr);
                config.setTransactionIsolation(getIsolationLevelName(this.isolationLevel));
            }

            // Performance optimizations
            config.setKeepaliveTime(getLongProperty(configValues, "keepaliveTime", 120000L));
            config.setInitializationFailTimeout(getLongProperty(configValues, "initializationFailTimeout", 1L));

            // Monitoring
            config.setRegisterMbeans(getBooleanProperty(configValues, "registerMbeans", true));

            // Apply any HikariCP-specific properties
            Properties hikariProps = new Properties();
            for (Object entryObj : configValues.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObj;
                String key = entry.getKey().toString();
                if (key.startsWith(HIKARI_PREFIX)) {
                    String propKey = key.substring(HIKARI_PREFIX.length());
                    hikariProps.setProperty(propKey, entry.getValue().toString());
                }
            }

            if (!hikariProps.isEmpty()) {
                config.setDataSourceProperties(hikariProps);
            }

            // Create the datasource
            this.dataSource = new HikariDataSource(config);

            _log.info("HikariCP connection pool configured successfully: pool={}, max={}, min={}",
                    config.getPoolName(), config.getMaximumPoolSize(), config.getMinimumIdle());

        } catch (Exception e) {
            _log.error("Failed to configure HikariCP connection pool", e);
            throw new RuntimeException("Failed to configure HikariCP connection pool", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("HikariCP DataSource is not initialized");
        }

        Connection connection = dataSource.getConnection();

        if (isolationLevel != null) {
            connection.setTransactionIsolation(isolationLevel);
        }

        if (connection.getAutoCommit() != autoCommit) {
            connection.setAutoCommit(autoCommit);
        }

        return connection;
    }

    @Override
    public void closeConnection(Connection connection) throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isUnwrappableAs(Class unwrapType) {
        return ConnectionProvider.class.equals(unwrapType) ||
               HikariCPConnectionProvider.class.isAssignableFrom(unwrapType) ||
               HikariDataSource.class.isAssignableFrom(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (ConnectionProvider.class.equals(unwrapType) ||
            HikariCPConnectionProvider.class.isAssignableFrom(unwrapType)) {
            return (T) this;
        } else if (HikariDataSource.class.isAssignableFrom(unwrapType)) {
            return (T) dataSource;
        } else {
            throw new UnknownUnwrapTypeException(unwrapType);
        }
    }

    @Override
    public void stop() {
        if (dataSource != null) {
            _log.info("Closing HikariCP connection pool");
            dataSource.close();
            dataSource = null;
        }
    }

    @SuppressWarnings("rawtypes")
    private String getProperty(Map props, String key) {
        Object value = props.get(CONNECTION_PREFIX + key);
        if (value == null) {
            value = props.get(HIKARI_PREFIX + key);
        }
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("rawtypes")
    private int getIntProperty(Map props, String key, int defaultValue) {
        String value = getProperty(props, key);
        if (value == null) {
            value = (String) props.get(HIKARI_PREFIX + key);
        }
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    @SuppressWarnings("rawtypes")
    private long getLongProperty(Map props, String key, long defaultValue) {
        String value = getProperty(props, key);
        if (value == null) {
            value = (String) props.get(HIKARI_PREFIX + key);
        }
        return value != null ? Long.parseLong(value) : defaultValue;
    }

    @SuppressWarnings("rawtypes")
    private boolean getBooleanProperty(Map props, String key, boolean defaultValue) {
        String value = getProperty(props, key);
        if (value == null) {
            value = (String) props.get(HIKARI_PREFIX + key);
        }
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    private String getIsolationLevelName(int level) {
        return switch (level) {
            case Connection.TRANSACTION_NONE -> "TRANSACTION_NONE";
            case Connection.TRANSACTION_READ_UNCOMMITTED -> "TRANSACTION_READ_UNCOMMITTED";
            case Connection.TRANSACTION_READ_COMMITTED -> "TRANSACTION_READ_COMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ -> "TRANSACTION_REPEATABLE_READ";
            case Connection.TRANSACTION_SERIALIZABLE -> "TRANSACTION_SERIALIZABLE";
            default -> throw new IllegalArgumentException("Unknown isolation level: " + level);
        };
    }
}
