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

package org.yawlfoundation.yawl.graalwasm;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.ByteSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of parsed GraalWasm Source objects.
 *
 * <p>WASM binaries bundled on classpath never change at runtime; cache key is resource path string.
 * Thread-safe via ConcurrentHashMap.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WasmBinaryCache {

    private static final Logger log = LoggerFactory.getLogger(WasmBinaryCache.class);
    private static final String WASM_LANGUAGE_ID = "wasm";

    private final ConcurrentHashMap<String, Source> cache = new ConcurrentHashMap<>();

    /**
     * Loads a WASM module from a classpath resource and caches the parsed Source.
     *
     * @param resourcePath  the classpath resource path (e.g. {@code "wasm/module.wasm"})
     * @return a parsed Source object; never null
     * @throws WasmException  if the resource is not found or cannot be read
     */
    public Source loadFromClasspath(String resourcePath) {
        return cache.computeIfAbsent(resourcePath, path -> {
            try (InputStream is = WasmBinaryCache.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    throw new WasmException(
                            "WASM resource not found on classpath: " + path,
                            WasmException.ErrorKind.MODULE_LOAD_ERROR);
                }
                byte[] bytes = is.readAllBytes();
                return Source.newBuilder(WASM_LANGUAGE_ID, ByteSequence.create(bytes), path).build();
            } catch (IOException e) {
                throw new WasmException(
                        "Cannot read WASM resource: " + path + ": " + e.getMessage(),
                        WasmException.ErrorKind.MODULE_LOAD_ERROR, e);
            }
        });
    }

    /**
     * Loads a WASM module from a filesystem Path and caches the parsed Source.
     *
     * @param wasmPath  the path to a WASM file
     * @return a parsed Source object; never null
     * @throws WasmException  if the file cannot be read
     */
    public Source loadFromPath(Path wasmPath) {
        return cache.computeIfAbsent(wasmPath.toString(), path -> {
            try {
                byte[] bytes = Files.readAllBytes(wasmPath);
                return Source.newBuilder(WASM_LANGUAGE_ID, ByteSequence.create(bytes), wasmPath.getFileName().toString()).build();
            } catch (IOException e) {
                throw new WasmException(
                        "Cannot read WASM file: " + path + ": " + e.getMessage(),
                        WasmException.ErrorKind.MODULE_LOAD_ERROR, e);
            }
        });
    }

    /**
     * Clears all cached Source objects.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the number of cached Source objects.
     *
     * @return the cache size
     */
    public int size() {
        return cache.size();
    }
}
