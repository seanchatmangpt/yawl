/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches {@link RdrSet} instances from the filesystem.
 *
 * <p>Each specification has an RDR rule file named {@code <specId>.rdr.xml} stored in
 * a configurable directory. Files are parsed on first access and cached in a
 * thread-safe {@link ConcurrentHashMap}.
 *
 * <p>Cache invalidation is manual: call {@link #evict(String)} to force a reload
 * for a specific specification, or {@link #clear()} to flush all entries.
 *
 * <p>If no file exists for a specification, {@link #load(String)} returns an empty
 * {@link RdrSet} (not null) so callers can always call {@code rdrSet.select()} safely.
 */
public class RdrSetRepository {

    private static final String RDR_SUFFIX = ".rdr.xml";

    private final Path rdrDirectory;
    private final ConcurrentHashMap<String, RdrSet> cache;

    /**
     * Constructs a repository that loads rule files from the given directory.
     *
     * @param rdrDirectory the directory containing {@code <specId>.rdr.xml} files;
     *                     must not be null
     * @throws IllegalArgumentException if rdrDirectory is null
     */
    public RdrSetRepository(Path rdrDirectory) {
        if (rdrDirectory == null) {
            throw new IllegalArgumentException("rdrDirectory must not be null");
        }
        this.rdrDirectory = rdrDirectory;
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Returns the {@link RdrSet} for the given specification ID.
     *
     * <p>On first call for a given specId, attempts to load
     * {@code <rdrDirectory>/<specId>.rdr.xml}. Returns an empty {@link RdrSet} if the
     * file does not exist or cannot be parsed. Subsequent calls return the cached result.
     *
     * @param specId the YAWL specification identifier; must not be null or blank
     * @return the cached or freshly loaded RdrSet; never null
     * @throws IllegalArgumentException if specId is null or blank
     */
    public RdrSet load(String specId) {
        if (specId == null || specId.isBlank()) {
            throw new IllegalArgumentException("specId must not be null or blank");
        }
        return cache.computeIfAbsent(specId, this::loadFromDisk);
    }

    /**
     * Evicts the cached {@link RdrSet} for the given specId, forcing a reload on
     * the next call to {@link #load(String)}.
     *
     * @param specId the specification ID to evict
     */
    public void evict(String specId) {
        if (specId != null) {
            cache.remove(specId);
        }
    }

    /**
     * Clears the entire cache, forcing all entries to be reloaded from disk.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the number of currently cached {@link RdrSet} entries.
     */
    public int cacheSize() {
        return cache.size();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Attempts to load the RDR file for the given specId from the configured directory.
     * Returns an empty RdrSet if the file does not exist or cannot be parsed.
     */
    private RdrSet loadFromDisk(String specId) {
        Path rdrFile = rdrDirectory.resolve(specId + RDR_SUFFIX);
        if (!Files.exists(rdrFile)) {
            return new RdrSet(specId);
        }
        try (InputStream is = Files.newInputStream(rdrFile)) {
            return RdrXmlParser.parse(specId, is);
        } catch (IOException | WorkletServiceException e) {
            return new RdrSet(specId);
        }
    }

    /**
     * Returns the path to the rule file for the given specId.
     * Package-private for testing.
     */
    Path rdrFilePath(String specId) {
        return rdrDirectory.resolve(specId + RDR_SUFFIX);
    }

    /**
     * Returns the rule directory this repository reads from.
     */
    public Path getRdrDirectory() {
        return rdrDirectory;
    }

    /**
     * Returns the cached entry for specId, if present.
     * Package-private for testing.
     */
    Optional<RdrSet> getCached(String specId) {
        return Optional.ofNullable(cache.get(specId));
    }
}
