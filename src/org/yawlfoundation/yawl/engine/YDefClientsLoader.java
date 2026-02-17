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

package org.yawlfoundation.yawl.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.authentication.YClient;
import org.yawlfoundation.yawl.authentication.YExternalClient;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.util.Argon2PasswordEncryptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;


/**
 * Registers the default service and external client accounts when they have not been
 * previously persisted (ie. on first startup) or when persistence is disabled.
 *
 * <p>SOC2 CRITICAL#4 remediation (2026-02-17): password hashing migrated from the
 * cryptographically broken SHA-1 ({@code PasswordEncryptor}) to Argon2id
 * ({@link Argon2PasswordEncryptor}) with OWASP-recommended cost factors
 * (memory=19 MiB, iterations=2, parallelism=1). The deprecated
 * {@code PasswordEncryptor} is no longer imported or used here.
 *
 * @author Michael Adams
 * @date 27/01/2011
 */
public class YDefClientsLoader {

    private final String _propsFile = "defaultClients.properties";
    private Logger _log;
    private Set<YExternalClient> _clients;
    private Set<YAWLServiceReference> _services;

    public YDefClientsLoader() {
        _log = LogManager.getLogger(this.getClass());
        _clients = new HashSet<YExternalClient>();
        _services = new HashSet<YAWLServiceReference>();
        load();
    }


    public Set<YExternalClient> getLoadedClients() { return _clients; }

    public Set<YAWLServiceReference> getLoadedServices() { return _services; }

    public Set<YClient> getAllLoaded() {
        Set<YClient> allLoaded = new HashSet<YClient>();
        allLoaded.addAll(_clients);
        allLoaded.addAll(_services);
        return allLoaded;
    }
   

    private void load() {
        _log.info("Loading default client and service account details - Starts");
        InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(_propsFile);
        if (in != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            try {
                String line = reader.readLine();
                while (line != null) {
                    processLine(line);
                    line = reader.readLine();
                }
            }
            catch (IOException ioe) {
                _log.warn("Error reading file '{}'.", _propsFile);
            }
        }
        else _log.warn("Error opening file '{}'.", _propsFile);

        _log.info("Loading default client and service account details - Ends");
    }


    private void processLine(String rawLine) {
        rawLine = rawLine.trim();

        // ignore comments and empty lines
        if ((rawLine.length() == 0) || rawLine.startsWith("#")) return;

        int headerEnd = rawLine.indexOf(':');
        if (headerEnd > -1) {
            String[] parts = rawLine.substring(headerEnd + 1).split(",");
            if (rawLine.startsWith("extClient:") && (parts.length == 3)) {
                _clients.add(new YExternalClient(parts[0].trim(),
                        hashPassword(parts[1].trim()),
                        parts[2].trim()));
                return;
            }
            else if (rawLine.startsWith("service:") && (parts.length == 5)) {
                YAWLServiceReference service = new YAWLServiceReference(
                        parts[3].trim(), null, parts[0].trim(),
                        hashPassword(parts[1].trim()),
                        parts[2].trim());
                service.setAssignable(parts[4].trim().equalsIgnoreCase("true"));
                _services.add(service);
                return;
            }
        }
        _log.warn("Could not load default external client - malformed entry: {}", rawLine);
    }


    /**
     * Hashes a plaintext password using Argon2id (OWASP 2024 recommended algorithm).
     *
     * <p>SOC2 CRITICAL#4: Replaces the deprecated SHA-1 PasswordEncryptor.encrypt().
     * Argon2id parameters: memory=19 MiB, iterations=2, parallelism=1 (OWASP minimum).
     *
     * @param plaintext the plaintext password from defaultClients.properties
     * @return the Argon2id PHC string, safe for storage in the credential store
     * @throws UnsupportedOperationException if the argon2-jvm library is not on the classpath
     */
    private String hashPassword(String plaintext) {
        return Argon2PasswordEncryptor.hash(plaintext);
    }
    
}
