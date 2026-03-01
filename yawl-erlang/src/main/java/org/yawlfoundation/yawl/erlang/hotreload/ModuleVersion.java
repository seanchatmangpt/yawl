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
package org.yawlfoundation.yawl.erlang.hotreload;

import java.time.Instant;

/**
 * Immutable snapshot of one loaded version of an Erlang module.
 *
 * <p>The {@link HotReloadService} maintains a ring buffer of {@code ModuleVersion}
 * records for each module. The {@link #beamBytecode()} allows the service to
 * reload any prior version via {@link HotReloadService#rollback(String)}.</p>
 *
 * @param moduleName   the Erlang atom name of the module (e.g., {@code "yawl_order_routing"})
 * @param version      monotonically increasing version number (1-based)
 * @param loadedAt     instant when this version was loaded via {@link HotReloadService#loadModule}
 * @param beamBytecode the raw .beam bytecode for this version (retained for rollback)
 * @param loadedBy     optional identifier of who or what triggered the load (may be empty)
 */
public record ModuleVersion(
        String moduleName,
        int version,
        Instant loadedAt,
        byte[] beamBytecode,
        String loadedBy) {

    public ModuleVersion {
        if (moduleName == null || moduleName.isBlank())
            throw new IllegalArgumentException("moduleName must be non-blank");
        if (version < 1)
            throw new IllegalArgumentException("version must be >= 1");
        if (loadedAt == null)
            throw new IllegalArgumentException("loadedAt must be non-null");
        if (beamBytecode == null || beamBytecode.length == 0)
            throw new IllegalArgumentException("beamBytecode must be non-empty");
        if (loadedBy == null)
            throw new IllegalArgumentException("loadedBy must be non-null (use empty string for anonymous)");
        beamBytecode = beamBytecode.clone();  // defensive copy
    }

    /** Returns the size of the .beam bytecode in bytes. */
    public int beamSize() {
        return beamBytecode.length;
    }

    /** Returns a defensive copy of the .beam bytecode. */
    @Override
    public byte[] beamBytecode() {
        return beamBytecode.clone();
    }
}
