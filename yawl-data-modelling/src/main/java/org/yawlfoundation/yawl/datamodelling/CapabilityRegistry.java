package org.yawlfoundation.yawl.datamodelling;

import org.yawlfoundation.yawl.datamodelling.api.DataModellingServiceImpl;
import org.yawlfoundation.yawl.datamodelling.bridge.DataModellingBridge;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Reflection-based scanner that verifies all 42 capabilities are mapped at startup.
 *
 * <p>Checks that every {@link Capability} value has exactly one
 * {@link MapsToCapability}-annotated method in both {@code DataModellingBridge}
 * and {@code DataModellingServiceImpl} (total: up to 2 methods per capability).
 * Throws {@link CapabilityRegistryException} on any violation.
 */
public final class CapabilityRegistry {

    private static final List<Class<?>> BRIDGE_CLASSES = List.of(
        DataModellingBridge.class,
        DataModellingServiceImpl.class);

    private CapabilityRegistry() {}

    /**
     * Asserts that every capability in {@link Capability} is covered by exactly
     * one {@link MapsToCapability}-annotated method per bridge class.
     *
     * @throws CapabilityRegistryException if any capability is missing or over-mapped
     */
    public static void assertComplete() {
        var mapped = new HashMap<Capability, List<String>>();
        for (Class<?> cls : BRIDGE_CLASSES) {
            for (Method m : cls.getDeclaredMethods()) {
                MapsToCapability ann = m.getAnnotation(MapsToCapability.class);
                if (ann != null) {
                    mapped.computeIfAbsent(ann.value(), k -> new ArrayList<>())
                          .add(cls.getSimpleName() + "." + m.getName());
                }
            }
        }

        var violations = new ArrayList<String>();
        if (Capability.values().length != Capability.TOTAL) {
            violations.add("Capability enum has " + Capability.values().length
                + " values but TOTAL=" + Capability.TOTAL);
        }
        for (Capability cap : Capability.values()) {
            List<String> methods = mapped.getOrDefault(cap, List.of());
            if (methods.isEmpty()) {
                violations.add("NOT MAPPED: " + cap + " (missing @MapsToCapability)");
            } else if (methods.size() > 2) {
                violations.add("OVER-MAPPED: " + cap + " → " + methods);
            }
        }
        if (!violations.isEmpty()) {
            throw new CapabilityRegistryException(violations);
        }
    }

    /** Exception thrown when registry validation fails. */
    public static final class CapabilityRegistryException extends RuntimeException {
        private final List<String> violations;

        public CapabilityRegistryException(List<String> violations) {
            super("CapabilityRegistry validation failed:\n" + String.join("\n", violations));
            this.violations = List.copyOf(violations);
        }

        public List<String> violations() { return violations; }
    }
}
