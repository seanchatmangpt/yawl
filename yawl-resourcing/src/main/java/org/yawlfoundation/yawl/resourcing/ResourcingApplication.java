/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or/modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.resourcing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for YAWL Resourcing System.
 *
 * <p>This application provides the core resourcing functionality for YAWL,
 * including participant management, work item allocation, external system
 * integration, and REST API endpoints.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Participant CRUD operations</li>
 *   <li>Work item allocation with various strategies</li>
 *   <li>LDAP integration for user synchronization</li>
 *   <li>HR system integration for employee data</li>
 *   <li>Delegation and escalation workflows</li>
 *   <li>REST API for external integrations</li>
 *   <li>Persistent work queues</li>
 *   <li>Multi-role support</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "org.yawlfoundation.yawl.resourcing")
@EnableTransactionManagement
@EnableAsync
public class ResourcingApplication {

    /**
     * Main entry point for the resourcing application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ResourcingApplication.class, args);
    }

    /**
     * Application-specific bean configurations can be added here.
     */
    // Additional configurations can be added as needed
}