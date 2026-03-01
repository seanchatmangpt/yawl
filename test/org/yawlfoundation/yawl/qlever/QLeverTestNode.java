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

package org.yawlfoundation.yawl.qlever;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared test node for QLever capability tests.
 * Provides a single live engine instance guarded by availability checks.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public final class QLeverTestNode {

    private static final AtomicReference<QLeverEmbeddedSparqlEngine> ENGINE = new AtomicReference<>();
    private static final String INDEX_DIR = System.getProperty("qlever.test.index",
            System.getenv() != null && System.getenv("QLEVER_TEST_INDEX") != null
                    ? System.getenv("QLEVER_TEST_INDEX")
                    : "/tmp/qlever-test-index");

    private QLeverTestNode() {
        throw new UnsupportedOperationException("Instantiation not allowed");
    }

    /**
     * Returns true if the native QLever library is loadable and a test index directory exists.
     */
    public static boolean isAvailable() {
        try {
            System.loadLibrary("qlever_ffi");
            return Files.isDirectory(Path.of(INDEX_DIR));
        } catch (UnsatisfiedLinkError | SecurityException e) {
            return false;
        }
    }

    /**
     * Returns true if a text index is available (for EXT_CONTAINS_WORD tests).
     */
    public static boolean hasTextIndex() {
        return isAvailable() && Files.exists(Path.of(INDEX_DIR + ".text.index"));
    }

    /**
     * Returns false — no read-only engine in standard test environments.
     */
    public static boolean hasReadOnlyEngine() {
        return false;
    }

    /**
     * Returns the shared engine instance, creating it on first call.
     */
    public static QLeverEmbeddedSparqlEngine engine() {
        QLeverEmbeddedSparqlEngine existing = ENGINE.get();
        if (existing != null) {
            return existing;
        }
        try {
            QLeverEmbeddedSparqlEngine created = new QLeverEmbeddedSparqlEngine(Path.of(INDEX_DIR));
            ENGINE.compareAndSet(null, created);
            return ENGINE.get();
        } catch (SparqlEngineException e) {
            throw new RuntimeException("Failed to create QLever test engine at " + INDEX_DIR, e);
        }
    }

    /**
     * Throws since no read-only engine is available in this environment.
     */
    public static QLeverEmbeddedSparqlEngine readOnlyEngine() {
        throw new UnsupportedOperationException("No read-only engine available in test environment");
    }

    /**
     * Returns the index base name (directory path).
     */
    public static String indexBasename() {
        return INDEX_DIR;
    }
}
