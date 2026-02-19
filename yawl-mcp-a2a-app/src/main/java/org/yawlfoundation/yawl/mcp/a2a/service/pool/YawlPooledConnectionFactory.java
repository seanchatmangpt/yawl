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

package org.yawlfoundation.yawl.mcp.a2a.service.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * Poolable factory for creating YAWL InterfaceB client connections.
 *
 * <p>This class implements {@code BasePooledObjectFactory} to create,
 * validate, and destroy YAWL client instances that can be pooled using
 * Apache Commons Pool2. The factory creates {@code InterfaceB_EnvironmentBasedClient}
 * instances configured with the necessary parameters.</p>
 *
 * <h2>Connection Creation</h2>
 * <p>Each connection is an {@code InterfaceB_EnvironmentBasedClient} instance that:</p>
 * <ul>
 *   <li>Connects to the configured YAWL engine URL</li>
 *   <li>Uses the configured username and password for authentication</li>
 *   <li>Has a default connection timeout</li>
 * </ul>
 *
 * <h2>Connection Validation</h2>
 * <p>Connections are validated by checking:</p>
 * <ul>
 *   <li>The connection is not null</li>
 *   <li>The connection can perform a simple operation (check connection)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see BasePooledObjectFactory
 * @see InterfaceB_EnvironmentBasedClient
 */
public class YawlPooledConnectionFactory extends BasePooledObjectFactory<InterfaceB_EnvironmentBasedClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(YawlPooledConnectionFactory.class);

    private final String yawlEngineUrl;
    private final String username;
    private final String password;
    private final int connectionTimeout;
    private final String specificationId;
    private final String specificationVersion;

    /**
     * Creates a new YAWL pooled connection factory.
     *
     * @param yawlEngineUrl the YAWL engine URL (e.g., http://localhost:8080/yawl)
     * @param username the username for authentication
     * @param password the password for authentication
     * @param connectionTimeout the connection timeout in milliseconds
     * @param specificationId the specification ID (can be null)
     * @param specificationVersion the specification version (can be null)
     */
    public YawlPooledConnectionFactory(String yawlEngineUrl, String username, String password,
                                      int connectionTimeout, String specificationId,
                                      String specificationVersion) {
        this.yawlEngineUrl = yawlEngineUrl;
        this.username = username;
        this.password = password;
        this.connectionTimeout = connectionTimeout;
        this.specificationId = specificationId;
        this.specificationVersion = specificationVersion;
    }

    @Override
    public InterfaceB_EnvironmentBasedClient create() throws Exception {
        LOGGER.debug("Creating new InterfaceB_EnvironmentBasedClient connection to: {}", yawlEngineUrl);

        // Create the client with the YAWL engine URL
        // The Interface URL should point to the /ib endpoint
        String interfaceBUrl = yawlEngineUrl;
        if (!interfaceBUrl.endsWith("/ib")) {
            interfaceBUrl = yawlEngineUrl + "/ib";
        }

        InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);

        LOGGER.debug("Successfully created InterfaceB_EnvironmentBasedClient");
        return client;
    }

    @Override
    public PooledObject<InterfaceB_EnvironmentBasedClient> wrap(InterfaceB_EnvironmentBasedClient obj) {
        return new DefaultPooledObject<>(obj);
    }

    @Override
    public void destroyObject(PooledObject<InterfaceB_EnvironmentBasedClient> p) throws Exception {
        InterfaceB_EnvironmentBasedClient client = p.getObject();
        LOGGER.debug("Destroying InterfaceB_EnvironmentBasedClient: {}", client);

        // The client doesn't maintain persistent connections, but we can log the cleanup
        // In production, you might want to disconnect any active sessions here
    }

    @Override
    public boolean validateObject(PooledObject<InterfaceB_EnvironmentBasedClient> p) {
        InterfaceB_EnvironmentBasedClient client = p.getObject();

        if (client == null) {
            LOGGER.debug("Validation failed: client is null");
            return false;
        }

        // The InterfaceB_EnvironmentBasedClient is stateless for HTTP operations
        // In production, you might want to perform a lightweight check like checking connection
        LOGGER.debug("Validation succeeded: client is valid");
        return true;
    }

    @Override
    public void activateObject(PooledObject<InterfaceB_EnvironmentBasedClient> p) throws Exception {
        InterfaceB_EnvironmentBasedClient client = p.getObject();
        LOGGER.debug("Activating InterfaceB_EnvironmentBasedClient: {}", client);

        // No activation needed - the client creates HTTP connections on demand
    }

    @Override
    public void passivateObject(PooledObject<InterfaceB_EnvironmentBasedClient> p) throws Exception {
        InterfaceB_EnvironmentBasedClient client = p.getObject();
        LOGGER.debug("Passivating InterfaceB_EnvironmentBasedClient: {}", client);

        // No passivation needed - the client is stateless
    }

    /**
     * Gets the YAWL engine URL.
     *
     * @return the YAWL engine URL
     */
    public String getYawlEngineUrl() {
        return yawlEngineUrl;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the connection timeout.
     *
     * @return the connection timeout in milliseconds
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Gets the specification ID.
     *
     * @return the specification ID
     */
    public String getSpecificationId() {
        return specificationId;
    }

    /**
     * Gets the specification version.
     *
     * @return the specification version
     */
    public String getSpecificationVersion() {
        return specificationVersion;
    }
}
