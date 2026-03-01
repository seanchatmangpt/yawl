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

package org.yawlfoundation.yawl.schema;

import org.yawlfoundation.yawl.datamodelling.DataModellingModule;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads, caches and serves ODCS schema contracts for YAWL task boundary validation.
 *
 * <p>Contracts are ODCS YAML files on the classpath. Each unique classpath path
 * is loaded once and its parsed {@link WorkspaceModel} cached for the JVM lifetime.
 * Thread-safe: the underlying {@link ConcurrentHashMap} ensures safe concurrent
 * access from virtual threads processing parallel task firings.</p>
 *
 * <p>The registry uses {@link DataModellingModule#create()} to obtain a
 * {@link DataModellingService} for native ODCS parsing. If the native library is
 * absent, any attempt to load a contract will throw {@link UnsupportedOperationException}
 * with a human-readable build instruction.</p>
 *
 * <h2>Contract declaration</h2>
 * <p>Contracts are declared as YAWL task decomposition attributes:</p>
 * <ul>
 *   <li>{@code schema.input}  — classpath path to input ODCS contract</li>
 *   <li>{@code schema.output} — classpath path to output ODCS contract</li>
 * </ul>
 *
 * @see DataModellingService#parseOdcsYaml(String)
 * @see TaskSchemaContract
 * @since 6.0.0
 */
public final class SchemaContractRegistry implements AutoCloseable {

    /** Attribute key for the input schema contract path in task decomposition attributes. */
    public static final String ATTR_SCHEMA_INPUT  = "schema.input";

    /** Attribute key for the output schema contract path in task decomposition attributes. */
    public static final String ATTR_SCHEMA_OUTPUT = "schema.output";

    private final DataModellingService _service;
    private final ConcurrentHashMap<String, WorkspaceModel> _cache = new ConcurrentHashMap<>();

    /**
     * Creates a new registry, initialising the underlying data-modelling service.
     *
     * @throws UnsupportedOperationException if the native data-modelling library is absent
     */
    public SchemaContractRegistry() {
        _service = DataModellingModule.create();
    }

    /**
     * Returns the cached {@link WorkspaceModel} for the given classpath path, loading it
     * on first access.
     *
     * @param classpathPath classpath-relative path (e.g. {@code "contracts/orders-v2.yaml"})
     * @return the parsed ODCS workspace model
     * @throws SchemaContractException if the file cannot be found or parsed
     */
    public WorkspaceModel loadContract(String classpathPath) {
        return _cache.computeIfAbsent(classpathPath, this::parseContract);
    }

    /**
     * Returns the input schema contract for a task's decomposition attributes, or empty
     * if no {@code schema.input} attribute is declared.
     *
     * @param attributes the task decomposition attribute map (from {@code YWorkItem.getAttributes()})
     * @return the input contract model, or empty
     * @throws SchemaContractException if the declared contract file cannot be loaded
     */
    public Optional<WorkspaceModel> inputContract(Map<String, String> attributes) {
        return contractFor(attributes, ATTR_SCHEMA_INPUT);
    }

    /**
     * Returns the output schema contract for a task's decomposition attributes, or empty
     * if no {@code schema.output} attribute is declared.
     *
     * @param attributes the task decomposition attribute map (from {@code YWorkItem.getAttributes()})
     * @return the output contract model, or empty
     * @throws SchemaContractException if the declared contract file cannot be loaded
     */
    public Optional<WorkspaceModel> outputContract(Map<String, String> attributes) {
        return contractFor(attributes, ATTR_SCHEMA_OUTPUT);
    }

    /**
     * Eagerly loads and caches the given contract paths. Call at application startup to
     * detect missing contract files before any task execution.
     *
     * @param classpathPaths the paths to preload
     * @throws SchemaContractException if any path cannot be found or parsed
     */
    public void preload(Collection<String> classpathPaths) {
        for (String path : classpathPaths) {
            loadContract(path);
        }
    }

    /** Returns the number of contracts currently cached. */
    public int size() {
        return _cache.size();
    }

    /** Closes the underlying data-modelling service, releasing native resources. */
    @Override
    public void close() throws Exception {
        _service.close();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Optional<WorkspaceModel> contractFor(Map<String, String> attributes, String key) {
        if (attributes == null) return Optional.empty();
        String path = attributes.get(key);
        if (path == null || path.isBlank()) return Optional.empty();
        return Optional.of(loadContract(path));
    }

    private WorkspaceModel parseContract(String classpathPath) {
        String yaml = readClasspathResource(classpathPath);
        try {
            return _service.parseOdcsYaml(yaml);
        } catch (Exception e) {
            throw new SchemaContractException(
                "Failed to parse ODCS contract at classpath path '" + classpathPath +
                "': " + e.getMessage(), e);
        }
    }

    private static String readClasspathResource(String path) {
        String normalised = path.startsWith("/") ? path.substring(1) : path;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(normalised)) {
            if (is == null) {
                throw new SchemaContractException(
                    "ODCS contract not found on classpath: '" + path + "'. " +
                    "Ensure the YAML file is in src/main/resources (or src/test/resources for tests).");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SchemaContractException(
                "IO error reading ODCS contract '" + path + "': " + e.getMessage(), e);
        }
    }
}
