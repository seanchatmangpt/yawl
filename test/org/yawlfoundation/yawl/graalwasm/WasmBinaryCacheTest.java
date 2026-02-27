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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WasmBinaryCacheTest {

    @Test
    void loadFromClasspath_nonExistentResource_throwsModuleLoadError() {
        WasmBinaryCache cache = new WasmBinaryCache();
        WasmException ex = assertThrows(WasmException.class,
                () -> cache.loadFromClasspath("nonexistent/missing.wasm"));

        assertEquals(WasmException.ErrorKind.MODULE_LOAD_ERROR, ex.getErrorKind());
    }

    @Test
    void loadFromClasspath_errorMessageContainsResourcePath() {
        WasmBinaryCache cache = new WasmBinaryCache();
        WasmException ex = assertThrows(WasmException.class,
                () -> cache.loadFromClasspath("missing/resource/path.wasm"));

        assertThat(ex.getMessage(),
                org.hamcrest.Matchers.containsString("missing/resource/path.wasm"));
    }

    @Test
    void loadFromPath_nonExistentFile_throwsModuleLoadError(@TempDir Path tempDir) {
        WasmBinaryCache cache = new WasmBinaryCache();
        Path nonExistent = tempDir.resolve("does_not_exist.wasm");

        WasmException ex = assertThrows(WasmException.class,
                () -> cache.loadFromPath(nonExistent));

        assertEquals(WasmException.ErrorKind.MODULE_LOAD_ERROR, ex.getErrorKind());
    }

    @Test
    void loadFromPath_errorMessageContainsFilePath(@TempDir Path tempDir) {
        WasmBinaryCache cache = new WasmBinaryCache();
        Path nonExistent = tempDir.resolve("missing.wasm");

        WasmException ex = assertThrows(WasmException.class,
                () -> cache.loadFromPath(nonExistent));

        assertThat(ex.getMessage(),
                org.hamcrest.Matchers.containsString(nonExistent.toString()));
    }

    @Test
    void size_isZeroOnNewCache() {
        WasmBinaryCache cache = new WasmBinaryCache();
        assertEquals(0, cache.size());
    }

    @Test
    void clear_emptiesCache(@TempDir Path tempDir) throws Exception {
        // Create a minimal WASM file (just some bytes; won't be executed)
        Path wasmFile = tempDir.resolve("test.wasm");
        Files.write(wasmFile, new byte[]{0x00, 0x61, 0x73, 0x6d}); // WASM magic

        WasmBinaryCache cache = new WasmBinaryCache();
        try {
            // Try to load; this will fail at Source.newBuilder stage if not on GraalVM,
            // but the cache entry might still be recorded
            cache.loadFromPath(wasmFile);
        } catch (WasmException ignored) {
            // Expected on non-GraalVM; the cache should still be empty or cleared
        }

        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void loadFromClasspath_throwsWasmException_notOtherException() {
        WasmBinaryCache cache = new WasmBinaryCache();
        assertThrows(WasmException.class, () -> cache.loadFromClasspath("missing.wasm"));
    }
}
