package org.yawlfoundation.yawl.datamodelling;

import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingServiceImpl;
import org.yawlfoundation.yawl.datamodelling.bridge.DataModellingBridge;

/**
 * Module entry point for the data-modelling native FFM bridge.
 *
 * <p>Loads the native library and validates capability coverage at startup.
 * Use {@link #create()} to obtain a new {@link DataModellingService} instance.
 *
 * <p>The native library path is controlled by the system property
 * {@code data_modelling_ffi.library.path}. If absent, the default
 * {@code target/release/libdata_modelling_ffi.so} is used.
 */
public final class DataModellingModule {

    private DataModellingModule() {}

    /**
     * Create a new {@link DataModellingService} backed by the native bridge.
     *
     * <p>Validates capability registry on first call. If the native library
     * is absent, the returned service throws {@link UnsupportedOperationException}
     * on every method call.
     *
     * @throws CapabilityRegistry.CapabilityRegistryException if any capability is unmapped
     */
    public static DataModellingService create() {
        CapabilityRegistry.assertComplete();
        return new DataModellingServiceImpl(new DataModellingBridge());
    }
}
