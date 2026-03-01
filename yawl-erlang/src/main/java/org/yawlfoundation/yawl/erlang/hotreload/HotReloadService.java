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

import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages hot code loading of Erlang modules in a running OTP node.
 *
 * <p>Business rules implemented as Erlang modules can be updated at runtime via
 * {@link #loadModule(String, byte[])} without stopping the JVM or interrupting
 * in-flight workflow cases. The OTP BEAM VM automatically routes new calls to the
 * new version while completing in-progress calls on the old version.</p>
 *
 * <p>Each successful {@link #loadModule} call archives the previous version in a
 * bounded ring buffer. {@link #rollback(String)} loads the most recent previous
 * version when a newly deployed rule produces unexpected results.</p>
 *
 * <p>Version history is scoped per module name. Different modules have independent
 * version histories.</p>
 *
 * @see HotReloadServiceImpl
 * @see ErlangTaskModule
 */
public interface HotReloadService {

    /**
     * Loads an Erlang module from raw {@code .beam} bytecode into the running OTP node.
     *
     * <p>Calls {@code code:purge(Module)} (removes old version) then
     * {@code code:load_binary(Module, Filename, Binary)} on the OTP node via RPC.
     * The previous version is archived in the ring buffer before loading the new one.</p>
     *
     * @param moduleName   the Erlang module atom name (e.g., {@code "yawl_order_routing"})
     * @param beamBytecode the raw {@code .beam} file contents
     * @return the {@link ModuleVersion} record for the newly loaded version
     * @throws ErlangRpcException       if the OTP RPC call fails or the node returns an error
     * @throws IllegalArgumentException if moduleName is blank or beamBytecode is empty
     */
    ModuleVersion loadModule(String moduleName, byte[] beamBytecode) throws ErlangRpcException;

    /**
     * Returns the current version metadata for the specified module.
     *
     * @param moduleName the Erlang module atom name
     * @return the current version, or empty if the module has never been loaded via this service
     */
    Optional<ModuleVersion> currentVersion(String moduleName);

    /**
     * Returns the version history for the specified module, newest first.
     *
     * <p>The list contains at most {@code ringBufferDepth} entries (configurable
     * at construction time of {@link HotReloadServiceImpl}, default 5).</p>
     *
     * @param moduleName the Erlang module atom name
     * @return immutable list of versions, newest first; empty if no history
     */
    List<ModuleVersion> versionHistory(String moduleName);

    /**
     * Rolls back to the most recent previous version of the specified module.
     *
     * <p>Loads the second-most-recent version from the ring buffer. If there is
     * no previous version (only one version in history), throws
     * {@link IllegalStateException}.</p>
     *
     * @param moduleName the Erlang module atom name
     * @throws ErlangRpcException    if the OTP RPC call for the rollback fails
     * @throws IllegalStateException if there is no previous version to roll back to
     */
    void rollback(String moduleName) throws ErlangRpcException;

    /**
     * Returns the current version of all modules that have been loaded via this service.
     *
     * @return immutable map from module name to current version
     */
    Map<String, ModuleVersion> allCurrentVersions();
}
