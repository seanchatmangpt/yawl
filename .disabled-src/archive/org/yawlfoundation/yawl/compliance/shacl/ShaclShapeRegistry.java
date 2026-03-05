/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.compliance.shacl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Registry for managing SHACL compliance shapes.
 *
 * <p>This class manages the loading and caching of SHACL shapes for
 * different compliance domains. It provides methods to load shapes from
 * the classpath and validates their structure.</p>
 */
public class ShaclShapeRegistry {

    private static final Logger _logger = LogManager.getLogger(ShaclShapeRegistry.class);

    private final Map<ComplianceDomain, String> shapeCache = new EnumMap<>(ComplianceDomain.class);
    private final Map<ComplianceDomain, Instant> lastLoaded = new EnumMap<>(ComplianceDomain.class);
    private final ClassLoader classLoader;

    /**
     * Creates a new SHACL shape registry with the default class loader.
     */
    public ShaclShapeRegistry() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a new SHACL shape registry with a specific class loader.
     *
     * @param classLoader The class loader to use for loading shapes
     */
    public ShaclShapeRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Gets the SHACL shapes for a compliance domain.
     *
     * <p>Loads and caches the shapes for the specified compliance domain.
     * Shapes are loaded from the classpath in the schema/shacl directory.</p>
     *
     * @param domain The compliance domain to get shapes for
     * @return The SHACL shapes as a string
     * @throws IOException If the shapes cannot be loaded
     */
    public String getShapes(ComplianceDomain domain) throws IOException {
        // Check cache first
        if (shapeCache.containsKey(domain)) {
            Instant last = lastLoaded.get(domain);
            if (last != null && System.currentTimeMillis() - last.toMillis() < 60000) {
                return shapeCache.get(domain);
            }
        }

        // Load from classpath
        String shapes = loadShapesFromDomain(domain);
        shapeCache.put(domain, shapes);
        lastLoaded.put(domain, Instant.now());

        _logger.debug("Loaded {} shapes from cache", domain);
        return shapes;
    }

    /**
     * Checks if shapes are available for a compliance domain.
     *
     * @param domain The compliance domain to check
     * @return true if shapes are available, false otherwise
     */
    public boolean hasShapes(ComplianceDomain domain) {
        try {
            getShapes(domain);
            return true;
        } catch (IOException e) {
            _logger.warn("Shapes not available for {}: {}", domain, e.getMessage());
            return false;
        }
    }

    /**
     * Gets all compliance domains with available shapes.
     *
     * @return List of available compliance domains
     */
    public List<ComplianceDomain> getAvailableDomains() {
        List<ComplianceDomain> available = new ArrayList<>();
        for (ComplianceDomain domain : ComplianceDomain.values()) {
            if (hasShapes(domain)) {
                available.add(domain);
            }
        }
        return available;
    }

    /**
     * Reloads shapes for all domains.
     */
    public void reloadAll() {
        _logger.info("Reloading all shapes");
        shapeCache.clear();
        lastLoaded.clear();
    }

    /**
     * Reloads shapes for a specific domain.
     *
     * @param domain The domain to reload
     * @throws IOException If the shapes cannot be reloaded
     */
    public void reloadDomain(ComplianceDomain domain) throws IOException {
        _logger.info("Reloading shapes for {}", domain);
        shapeCache.remove(domain);
        lastLoaded.remove(domain);
        getShapes(domain);
    }

    /**
     * Validates the shape syntax for a compliance domain.
     *
     * @param domain The domain to validate
     * @return List of validation errors, empty if valid
     */
    public List<String> validateShapes(ComplianceDomain domain) {
        try {
            String shapes = getShapes(domain);
            return validateShapeSyntax(shapes, domain);
        } catch (IOException e) {
            return Collections.singletonList("Failed to load shapes: " + e.getMessage());
        }
    }

    /**
     * Gets the shape file path for a compliance domain.
     *
     * @param domain The compliance domain
     * @return The path to the shape file
     */
    public String getShapeFilePath(ComplianceDomain domain) {
        return "schema/shacl/" + domain.getShapeFile();
    }

    /**
     * Loads shapes from the classpath for a compliance domain.
     *
     * @param domain The compliance domain to load shapes for
     * @return The shapes as a string
     * @throws IOException If the shapes cannot be loaded
     */
    private String loadShapesFromDomain(ComplianceDomain domain) throws IOException {
        String shapePath = getShapeFilePath(domain);
        URL shapeUrl = classLoader.getResource(shapePath);

        if (shapeUrl == null) {
            throw new IOException("Shape file not found: " + shapePath);
        }

        try (InputStream inputStream = shapeUrl.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Validates the syntax of SHACL shapes.
     *
     * @param shapes The shapes to validate
     * @param domain The compliance domain
     * @return List of validation errors
     */
    private List<String> validateShapeSyntax(String shapes, ComplianceDomain domain) {
        List<String> errors = new ArrayList<>();

        // Basic validation checks
        if (shapes == null || shapes.trim().isEmpty()) {
            errors.add("Empty shapes for " + domain);
            return errors;
        }

        // Check for required elements
        if (!shapes.contains("@prefix")) {
            errors.add("Missing @prefix declarations");
        }

        if (!shapes.contains("sh:Shape")) {
            errors.add("Missing sh:Shape definitions");
        }

        // Check for domain-specific requirements
        switch (domain) {
            case SOX:
                if (!shapes.contains("yawl:FinancialProcess")) {
                    errors.add("Missing SOX-specific financial process shape");
                }
                break;
            case GDPR:
                if (!shapes.contains("yawl:PersonalData")) {
                    errors.add("Missing GDPR-specific personal data shape");
                }
                break;
            case HIPAA:
                if (!shapes.contains("yawl:HealthcareData")) {
                    errors.add("Missing HIPAA-specific healthcare data shape");
                }
                break;
        }

        return errors;
    }

    /**
     * Gets cache statistics.
     *
     * @return Map of cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedDomains", shapeCache.size());
        stats.put("lastLoaded", new HashMap<>(lastLoaded));
        stats.put("availableDomains", getAvailableDomains());
        return stats;
    }

    /**
     * Clears the cache.
     */
    public void clearCache() {
        shapeCache.clear();
        lastLoaded.clear();
        _logger.info("Shape cache cleared");
    }
}