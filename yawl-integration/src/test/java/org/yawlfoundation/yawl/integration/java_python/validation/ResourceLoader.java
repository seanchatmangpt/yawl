/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL - Yet Another Workflow Language.
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.java_python.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Utility class for loading test resources
 */
public class ResourceLoader {

    private static final Logger logger = LoggerFactory.getLogger(ResourceLoader.class);

    /**
     * Load properties from the classpath
     */
    public static Properties loadProperties(String resourcePath) throws IOException {
        Properties props = new Properties();
        try (InputStream input = ResourceLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            props.load(input);
        }
        return props;
    }

    /**
     * Load workflow XML from resources
     */
    public static String loadWorkflowXml(String workflowName) throws IOException {
        String resourcePath = "chaos/workflows/" + workflowName + ".yawl";
        try (InputStream input = ResourceLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Workflow XML not found: " + resourcePath);
            }
            return new String(input.readAllBytes());
        }
    }

    /**
     * Load test data from resources
     */
    public static String loadTestData(String dataFile) throws IOException {
        String resourcePath = "chaos/data/" + dataFile;
        try (InputStream input = ResourceLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Test data not found: " + resourcePath);
            }
            return new String(input.readAllBytes());
        }
    }

    /**
     * Get URL for a resource
     */
    public static URL getResourceUrl(String resourcePath) throws IOException {
        URL url = ResourceLoader.class.getClassLoader().getResource(resourcePath);
        if (url == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return url;
    }

    /**
     * Check if a resource exists
     */
    public static boolean resourceExists(String resourcePath) {
        try {
            ResourceLoader.class.getClassLoader().getResource(resourcePath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}