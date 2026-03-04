/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.bridge.qlever.engine;

import org.yawlfoundation.yawl.bridge.qlever.QleverEngine;
import org.yawlfoundation.yawl.bridge.qlever.QleverStatus;
import org.yawlfoundation.yawl.bridge.qlever.engine.Triple;

import java.util.List;
import java.util.Map;

/**
 * Stub implementation of QLeverEngine.
 *
 * This class requires Panama FFI and jextract-generated native code to function properly.
 * The full implementation requires:
 * - Panama FFI for native interop (jdk.incubator.foreign)
 * - jextract-generated bindings for QLever C API
 * - Native QLever library
 *
 * @see IMPLEMENTATION_SUMMARY.md for build instructions
 */
public class QLeverEngineImpl implements QleverEngine {

    public QLeverEngineImpl(String indexPath) {
        throw new UnsupportedOperationException(
            "QLeverEngineImpl requires Panama FFI and jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    @Override
    public QleverStatus initialize() {
        throw new UnsupportedOperationException(
            "initialize() requires Panama FFI and native QLever library. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    @Override
    public QleverStatus executeQuery(String sparqlQuery) {
        throw new UnsupportedOperationException(
            "executeQuery() requires Panama FFI and native QLever library. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    @Override
    public List<Map<String, String>> getResults() {
        throw new UnsupportedOperationException(
            "getResults() requires Panama FFI and native QLever library. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    @Override
    public String getConstructQueryResults(String sparqlQuery) {
        throw new UnsupportedOperationException(
            "getConstructQueryResults() requires Panama FFI and native QLever library. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    @Override
    public QleverStatus close() {
        throw new UnsupportedOperationException(
            "close() requires Panama FFI and native QLever library. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    @Override
    public boolean isOpen() {
        return false;
    }
}