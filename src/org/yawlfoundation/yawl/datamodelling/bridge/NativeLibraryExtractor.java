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

package org.yawlfoundation.yawl.datamodelling.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;
import org.yawlfoundation.yawl.datamodelling.DataModellingException.ErrorKind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts the bundled {@code libdata_modelling_ffi} native library from the classpath
 * to a temporary file, enabling zero-configuration library loading.
 *
 * <p>Resources are expected at {@code native/<os-arch>/<libname>} on the classpath:
 * <ul>
 *   <li>{@code native/linux-x86_64/libdata_modelling_ffi.so}</li>
 *   <li>{@code native/linux-aarch64/libdata_modelling_ffi.so}</li>
 *   <li>{@code native/darwin-x86_64/libdata_modelling_ffi.dylib}</li>
 *   <li>{@code native/darwin-aarch64/libdata_modelling_ffi.dylib}</li>
 * </ul>
 *
 * <p>The extracted temp file is deleted on {@link #close()}.
 */
final class NativeLibraryExtractor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NativeLibraryExtractor.class);

    private final Path tempFile;

    private NativeLibraryExtractor(Path tempFile) {
        this.tempFile = tempFile;
    }

    /**
     * Detects OS/arch, finds the bundled native library resource, copies it to a temp
     * file, and returns the extractor.
     *
     * @return extractor holding the path to the extracted file
     * @throws DataModellingException if the resource is not found or extraction fails
     */
    static NativeLibraryExtractor extract() {
        String resource = classpathResource();
        log.debug("Extracting native library from classpath: {}", resource);

        try (InputStream in = NativeLibraryExtractor.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new DataModellingException(
                        "Native library not found on classpath: " + resource
                        + ". Ensure the yawl-data-modelling JAR was built with cargo "
                        + "(skipRustBuild=false), or set -D"
                        + DataModellingL2.LIB_PATH_PROP + "=<path>.",
                        ErrorKind.MODULE_LOAD_ERROR);
            }
            String suffix = resource.substring(resource.lastIndexOf('.'));
            Path tmp = Files.createTempFile("libdm_ffi_", suffix);
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Native library extracted to: {}", tmp);
            return new NativeLibraryExtractor(tmp);
        } catch (IOException e) {
            throw new DataModellingException(
                    "Failed to extract native library from classpath: " + e.getMessage(),
                    ErrorKind.MODULE_LOAD_ERROR, e);
        }
    }

    /** Returns the absolute path to the extracted temp file. */
    String absolutePath() {
        return tempFile.toAbsolutePath().toString();
    }

    /** Deletes the extracted temp file. */
    @Override
    public void close() {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.debug("Could not delete native library temp file {}: {}", tempFile, e.getMessage());
        }
    }

    // ── Classpath resource resolution ─────────────────────────────────────────

    static String classpathResource() {
        return "native/" + osArchClassifier() + "/" + libFileName();
    }

    private static String osArchClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osToken = os.contains("linux") ? "linux"
                : (os.contains("mac") || os.contains("darwin")) ? "darwin"
                : null;
        if (osToken == null) {
            throw new UnsupportedOperationException(
                    "Unsupported OS for native library extraction: " + os);
        }
        String archToken = (arch.contains("aarch64") || arch.contains("arm64"))
                ? "aarch64" : "x86_64";
        return osToken + "-" + archToken;
    }

    private static String libFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return (os.contains("mac") || os.contains("darwin"))
                ? "libdata_modelling_ffi.dylib"
                : "libdata_modelling_ffi.so";
    }
}
