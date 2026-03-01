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

/**
 * Layer 2 — typed Java bridge to {@code libdata_modelling_ffi.so}.
 *
 * <p>Wraps the raw Panama {@link org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h}
 * handles in a type-safe, {@link AutoCloseable} API. Each method allocates its
 * arguments in a per-call {@link java.lang.foreign.Arena#ofConfined()} and reads
 * the returned {@code DmResult} / {@code DmVoidResult} struct via the accessor
 * classes in the {@code generated} package.
 *
 * @see org.yawlfoundation.yawl.datamodelling.DataModellingModule
 */
package org.yawlfoundation.yawl.datamodelling.bridge;
