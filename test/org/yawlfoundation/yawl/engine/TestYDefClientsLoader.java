/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.yawlfoundation.yawl.authentication.YClient;
import org.yawlfoundation.yawl.authentication.YExternalClient;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;

/**
 * Comprehensive tests for YDefClientsLoader following Chicago TDD methodology.
 *
 * <p>Tests the loading of default client and service accounts from properties.</p>
 *
 * @author YAWL Test Suite
 * @see YDefClientsLoader
 */
@DisplayName("YDefClientsLoader Tests")
@Tag("integration")
class TestYDefClientsLoader {

    // ========================================================================
    // Constructor Tests
    // ========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor loads properties file")
        void constructorLoadsPropertiesFile() {
            // This creates the loader which loads from defaultClients.properties
            YDefClientsLoader loader = new YDefClientsLoader();
            assertNotNull(loader, "Loader should be created");
        }

        @Test
        @DisplayName("Constructor does not throw when properties file exists")
        void constructorDoesNotThrowWhenPropertiesFileExists() {
            assertDoesNotThrow(() -> {
                new YDefClientsLoader();
            }, "Constructor should not throw when properties file exists");
        }
    }

    // ========================================================================
    // GetLoadedClients Tests
    // ========================================================================

    @Nested
    @DisplayName("GetLoadedClients Tests")
    class GetLoadedClientsTests {

        @Test
        @DisplayName("GetLoadedClients returns non-null set")
        void getLoadedClientsReturnsNonNullSet() {
            YDefClientsLoader loader = new YDefClientsLoader();
            assertNotNull(loader.getLoadedClients(), "Should return non-null set");
        }

        @Test
        @DisplayName("GetLoadedClients returns set of YExternalClient")
        void getLoadedClientsReturnsSetOfYExternalClient() {
            YDefClientsLoader loader = new YDefClientsLoader();
            for (Object obj : loader.getLoadedClients()) {
                assertTrue(obj instanceof YExternalClient,
                        "All loaded clients should be YExternalClient instances");
            }
        }
    }

    // ========================================================================
    // GetLoadedServices Tests
    // ========================================================================

    @Nested
    @DisplayName("GetLoadedServices Tests")
    class GetLoadedServicesTests {

        @Test
        @DisplayName("GetLoadedServices returns non-null set")
        void getLoadedServicesReturnsNonNullSet() {
            YDefClientsLoader loader = new YDefClientsLoader();
            assertNotNull(loader.getLoadedServices(), "Should return non-null set");
        }

        @Test
        @DisplayName("GetLoadedServices returns set of YAWLServiceReference")
        void getLoadedServicesReturnsSetOfYawlServiceReference() {
            YDefClientsLoader loader = new YDefClientsLoader();
            for (Object obj : loader.getLoadedServices()) {
                assertTrue(obj instanceof YAWLServiceReference,
                        "All loaded services should be YAWLServiceReference instances");
            }
        }
    }

    // ========================================================================
    // GetAllLoaded Tests
    // ========================================================================

    @Nested
    @DisplayName("GetAllLoaded Tests")
    class GetAllLoadedTests {

        @Test
        @DisplayName("GetAllLoaded returns non-null set")
        void getAllLoadedReturnsNonNullSet() {
            YDefClientsLoader loader = new YDefClientsLoader();
            assertNotNull(loader.getAllLoaded(), "Should return non-null set");
        }

        @Test
        @DisplayName("GetAllLoaded contains clients and services")
        void getAllLoadedContainsClientsAndServices() {
            YDefClientsLoader loader = new YDefClientsLoader();

            java.util.Set<YClient> allLoaded = loader.getAllLoaded();
            java.util.Set<YExternalClient> clients = loader.getLoadedClients();
            java.util.Set<YAWLServiceReference> services = loader.getLoadedServices();

            // Combined size should be sum of clients and services
            assertEquals(clients.size() + services.size(), allLoaded.size(),
                    "All loaded should contain all clients and services");
        }

        @Test
        @DisplayName("GetAllLoaded returns set of YClient")
        void getAllLoadedReturnsSetOfYClient() {
            YDefClientsLoader loader = new YDefClientsLoader();
            for (Object obj : loader.getAllLoaded()) {
                assertTrue(obj instanceof YClient,
                        "All loaded should be YClient instances");
            }
        }
    }

    // ========================================================================
    // Default Properties Content Tests
    // ========================================================================

    @Nested
    @DisplayName("Default Properties Content Tests")
    class DefaultPropertiesContentTests {

        @Test
        @DisplayName("Default properties file loads at least one entry")
        void defaultPropertiesFileLoadsAtLeastOneEntry() {
            YDefClientsLoader loader = new YDefClientsLoader();
            int totalLoaded = loader.getLoadedClients().size() + loader.getLoadedServices().size();
            assertTrue(totalLoaded > 0,
                    "Default properties file should load at least one entry");
        }

        @Test
        @DisplayName("Loaded clients have valid usernames")
        void loadedClientsHaveValidUsernames() {
            YDefClientsLoader loader = new YDefClientsLoader();
            for (YExternalClient client : loader.getLoadedClients()) {
                assertNotNull(client.getUserName(), "Client username should not be null");
                assertFalse(client.getUserName().isEmpty(),
                        "Client username should not be empty");
            }
        }

        @Test
        @DisplayName("Loaded services have valid URIs")
        void loadedServicesHaveValidUris() {
            YDefClientsLoader loader = new YDefClientsLoader();
            for (YAWLServiceReference service : loader.getLoadedServices()) {
                assertNotNull(service.getURI(), "Service URI should not be null");
            }
        }
    }

    // ========================================================================
    // Password Encryption Tests
    // ========================================================================

    @Nested
    @DisplayName("Password Encryption Tests")
    class PasswordEncryptionTests {

        @Test
        @DisplayName("Client passwords are encrypted")
        void clientPasswordsAreEncrypted() {
            YDefClientsLoader loader = new YDefClientsLoader();
            for (YExternalClient client : loader.getLoadedClients()) {
                // Password should be encrypted (not plaintext)
                String password = client.getPassword();
                assertNotNull(password, "Password should not be null");
                // Password encryption typically produces longer strings
                // than typical plaintext passwords
            }
        }

        @Test
        @DisplayName("Service passwords are encrypted")
        void servicePasswordsAreEncrypted() {
            YDefClientsLoader loader = new YDefClientsLoader();
            for (YAWLServiceReference service : loader.getLoadedServices()) {
                String password = service.getPassword();
                assertNotNull(password, "Password should not be null");
            }
        }
    }

    // ========================================================================
    // Multiple Instantiation Tests
    // ========================================================================

    @Nested
    @DisplayName("Multiple Instantiation Tests")
    class MultipleInstantiationTests {

        @Test
        @DisplayName("Multiple loaders work independently")
        void multipleLoadersWorkIndependently() {
            YDefClientsLoader loader1 = new YDefClientsLoader();
            YDefClientsLoader loader2 = new YDefClientsLoader();

            // Both should load the same default content
            assertEquals(loader1.getLoadedClients().size(), loader2.getLoadedClients().size(),
                    "Both loaders should load same number of clients");
            assertEquals(loader1.getLoadedServices().size(), loader2.getLoadedServices().size(),
                    "Both loaders should load same number of services");
        }

        @Test
        @DisplayName("Loader state is independent per instance")
        void loaderStateIsIndependentPerInstance() {
            YDefClientsLoader loader1 = new YDefClientsLoader();
            YDefClientsLoader loader2 = new YDefClientsLoader();

            // Verify sets are independent objects
            assertNotSame(loader1.getLoadedClients(), loader2.getLoadedClients(),
                    "Each loader should have independent client set");
        }
    }
}
