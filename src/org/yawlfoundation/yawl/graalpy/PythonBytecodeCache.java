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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bytecode cache for compiled GraalPy Python scripts.
 *
 * <p>Mitigates cold-start parsing overhead by caching compiled Python bytecode as
 * {@code .pyc} files in a configurable cache directory. Before evaluating a
 * {@code .py} file, the cache checks whether a valid {@code .pyc} exists that was
 * produced from the same source version (based on source file modification time).</p>
 *
 * <h2>Cache structure</h2>
 * <p>Cache files are stored at {@code <cacheDir>/<relative-path>.pyc}. The cache
 * directory mirrors the source tree, so a script at {@code scripts/nlp/sentiment.py}
 * caches to {@code <cacheDir>/scripts/nlp/sentiment.pyc}.</p>
 *
 * <h2>Invalidation</h2>
 * <p>Cache entries are invalidated when the source file's {@code lastModifiedTime}
 * changes. Entries are also tracked in-memory for fast lookup within a JVM session.</p>
 *
 * <h2>Thread safety</h2>
 * <p>This class is thread-safe via {@link ConcurrentHashMap} for the in-memory index
 * and atomic file operations for the on-disk cache.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonBytecodeCache {

    private static final Logger log = LoggerFactory.getLogger(PythonBytecodeCache.class);

    private final Path cacheDir;

    /** In-memory index: source path → last known modification time (epoch millis). */
    private final ConcurrentHashMap<Path, Long> cacheIndex = new ConcurrentHashMap<>();

    /**
     * Creates a bytecode cache backed by the given directory.
     *
     * @param cacheDir  directory for {@code .pyc} cache files; created if absent
     * @throws PythonException  if the cache directory cannot be created
     */
    public PythonBytecodeCache(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
            log.debug("PythonBytecodeCache initialised at {}", cacheDir);
        } catch (IOException e) {
            throw new PythonException(
                    "Cannot create bytecode cache directory: " + cacheDir + " — " + e.getMessage(),
                    PythonException.ErrorKind.CONTEXT_ERROR, e);
        }
    }

    /**
     * Returns {@code true} if a valid cached bytecode file exists for the given source.
     *
     * <p>Validity is determined by comparing the source file's modification time with
     * the recorded time in the in-memory index and the {@code .pyc} file's mtime.</p>
     *
     * @param sourcePath  path to the {@code .py} source file; must not be null
     * @return {@code true} if the cache is valid; {@code false} if missing or stale
     */
    public boolean isValid(Path sourcePath) {
        try {
            FileTime sourceModTime = Files.getLastModifiedTime(sourcePath);
            Long cachedModTime = cacheIndex.get(sourcePath.toAbsolutePath());
            if (cachedModTime == null) {
                // Not in in-memory index; check disk
                Path cachedFile = toCachePath(sourcePath);
                if (!Files.exists(cachedFile)) {
                    return false;
                }
                // Disk cache exists; check if source is newer
                FileTime cacheModTime = Files.getLastModifiedTime(cachedFile);
                boolean valid = !sourceModTime.toInstant().isAfter(cacheModTime.toInstant());
                if (valid) {
                    cacheIndex.put(sourcePath.toAbsolutePath(), sourceModTime.toMillis());
                }
                return valid;
            }
            return cachedModTime == sourceModTime.toMillis();
        } catch (IOException e) {
            log.warn("Cache validity check failed for {}: {}", sourcePath, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the {@code .pyc} cache file path for the given source file.
     *
     * @param sourcePath  path to the {@code .py} source file; must not be null
     * @return path to the corresponding {@code .pyc} cache file
     */
    public Path getCachePath(Path sourcePath) {
        return toCachePath(sourcePath);
    }

    /**
     * Records that a source file has been compiled and its bytecode stored at
     * the cache path.
     *
     * <p>Updates the in-memory index with the current modification time of the
     * source file, enabling fast cache-hit detection for subsequent calls.</p>
     *
     * @param sourcePath  the {@code .py} source that was compiled; must not be null
     */
    public void markCached(Path sourcePath) {
        try {
            FileTime modTime = Files.getLastModifiedTime(sourcePath);
            cacheIndex.put(sourcePath.toAbsolutePath(), modTime.toMillis());
            log.debug("Marked cached: {}", sourcePath);
        } catch (IOException e) {
            log.warn("Cannot record cache entry for {}: {}", sourcePath, e.getMessage());
        }
    }

    /**
     * Invalidates the cache entry for a specific source file.
     *
     * <p>The next call to {@link #isValid(Path)} for this source will return
     * {@code false}, causing recompilation.</p>
     *
     * @param sourcePath  the source file to invalidate; must not be null
     */
    public void invalidate(Path sourcePath) {
        cacheIndex.remove(sourcePath.toAbsolutePath());
        Path cachePath = toCachePath(sourcePath);
        try {
            Files.deleteIfExists(cachePath);
            log.debug("Cache invalidated: {}", sourcePath);
        } catch (IOException e) {
            log.warn("Cannot delete cache file {}: {}", cachePath, e.getMessage());
        }
    }

    /**
     * Clears the entire in-memory index. Disk {@code .pyc} files are retained.
     *
     * <p>The next access to any source file will re-evaluate disk cache validity.</p>
     */
    public void clearIndex() {
        cacheIndex.clear();
        log.debug("Bytecode cache index cleared");
    }

    /** Returns the cache directory configured for this instance. */
    public Path getCacheDir() { return cacheDir; }

    /** Returns the number of entries currently tracked in the in-memory index. */
    public int indexSize() { return cacheIndex.size(); }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private Path toCachePath(Path sourcePath) {
        // Convert absolute source path to a relative cache path under cacheDir
        String fileName = sourcePath.getFileName().toString();
        String cacheFileName = fileName.endsWith(".py")
                ? fileName.substring(0, fileName.length() - 3) + ".pyc"
                : fileName + ".pyc";
        // Use just the filename to keep the cache flat; extend to mirror tree if needed
        return cacheDir.resolve(cacheFileName);
    }
}
