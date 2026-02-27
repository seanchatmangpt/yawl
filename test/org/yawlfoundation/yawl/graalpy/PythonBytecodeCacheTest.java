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

package org.yawlfoundation.yawl.graalpy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link PythonBytecodeCache}.
 *
 * <p>Tests bytecode cache behaviour using real file system operations via
 * JUnit 5 {@code @TempDir} without requiring GraalPy at runtime.</p>
 */
@DisplayName("PythonBytecodeCache")
class PythonBytecodeCacheTest {

    @TempDir
    Path tempDir;

    // ── Initialisation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("creates cache directory if absent")
    void createsCacheDirectoryIfAbsent() {
        Path cacheDir = tempDir.resolve("graalpy-cache");
        assertFalse(Files.exists(cacheDir));

        new PythonBytecodeCache(cacheDir);

        assertTrue(Files.exists(cacheDir), "Cache directory should be created");
    }

    @Test
    @DisplayName("accepts existing cache directory")
    void acceptsExistingCacheDirectory() throws IOException {
        Path cacheDir = tempDir.resolve("existing-cache");
        Files.createDirectories(cacheDir);

        assertDoesNotThrow(() -> new PythonBytecodeCache(cacheDir));
    }

    // ── Cache validity ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("returns false for uncached source file")
    void returnsFalseForUncachedSource() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        Path sourceFile = tempDir.resolve("script.py");
        Files.writeString(sourceFile, "print('hello')", StandardCharsets.UTF_8);

        assertFalse(cache.isValid(sourceFile));
    }

    @Test
    @DisplayName("returns true after source is marked as cached")
    void returnsTrueAfterMarkCached() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        Path sourceFile = tempDir.resolve("script.py");
        Files.writeString(sourceFile, "print('hello')", StandardCharsets.UTF_8);

        // Simulate that compiled .pyc was stored
        Path compiledFile = cache.getCachePath(sourceFile);
        Files.writeString(compiledFile, "compiled", StandardCharsets.UTF_8);
        cache.markCached(sourceFile);

        assertTrue(cache.isValid(sourceFile));
    }

    @Test
    @DisplayName("returns false after cache invalidation")
    void returnsFalseAfterInvalidation() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        Path sourceFile = tempDir.resolve("analysis.py");
        Files.writeString(sourceFile, "x = 1", StandardCharsets.UTF_8);

        Path compiledFile = cache.getCachePath(sourceFile);
        Files.writeString(compiledFile, "compiled", StandardCharsets.UTF_8);
        cache.markCached(sourceFile);

        assertTrue(cache.isValid(sourceFile));

        cache.invalidate(sourceFile);

        assertFalse(cache.isValid(sourceFile));
    }

    // ── Cache path generation ───────────────────────────────────────────────────

    @Test
    @DisplayName("getCachePath returns .pyc path for .py source")
    void getCachePathReturnsPycForPy() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        Path sourceFile = tempDir.resolve("model.py");
        Path cachePath = cache.getCachePath(sourceFile);

        assertThat(cachePath.getFileName().toString(), is("model.pyc"));
        assertThat(cachePath.getParent(), is(cacheDir));
    }

    @Test
    @DisplayName("getCachePath appends .pyc for non-.py extension")
    void getCachePathAppendsForNonPyExtension() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        Path sourceFile = tempDir.resolve("model.python");
        Path cachePath = cache.getCachePath(sourceFile);

        assertThat(cachePath.getFileName().toString(), is("model.python.pyc"));
    }

    // ── Index management ────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearIndex resets in-memory tracking")
    void clearIndexResetsTracking() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        Path sourceFile = tempDir.resolve("clear_test.py");
        Files.writeString(sourceFile, "pass", StandardCharsets.UTF_8);

        Path compiledFile = cache.getCachePath(sourceFile);
        Files.writeString(compiledFile, "compiled", StandardCharsets.UTF_8);
        cache.markCached(sourceFile);

        assertThat(cache.indexSize(), is(greaterThan(0)));

        cache.clearIndex();

        assertThat(cache.indexSize(), is(0));
    }

    @Test
    @DisplayName("getCacheDir returns the configured directory")
    void getCacheDirReturnsConfiguredDirectory() {
        Path cacheDir = tempDir.resolve("my-cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        assertThat(cache.getCacheDir(), is(cacheDir));
    }

    // ── invalidate cleans up disk ───────────────────────────────────────────────

    @Test
    @DisplayName("invalidate removes .pyc file from disk")
    void invalidateRemovesPycFromDisk() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        PythonBytecodeCache cache = new PythonBytecodeCache(cacheDir);

        Path sourceFile = tempDir.resolve("evict.py");
        Files.writeString(sourceFile, "x = 42", StandardCharsets.UTF_8);

        Path compiledFile = cache.getCachePath(sourceFile);
        Files.writeString(compiledFile, "bytecode", StandardCharsets.UTF_8);
        cache.markCached(sourceFile);

        assertTrue(Files.exists(compiledFile));

        cache.invalidate(sourceFile);

        assertFalse(Files.exists(compiledFile), ".pyc file should be deleted on invalidation");
    }
}
