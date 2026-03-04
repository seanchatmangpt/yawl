package org.yawlfoundation.yawl.bridge.processmining;

/**
 * JNI wrapper for Alpha++ process discovery algorithm.
 * Discovers Petri nets from event logs using the Alpha++ algorithm.
 */
public class AlphaMiner {

    private native String discoverAlphaPlusPlus(long eventLogHandle);

    /**
     * Discovers a Petri net from an event log using Alpha++ algorithm.
     *
     * @param handle Event log handle obtained from XesImporter
     * @return Discovered Petri net in PNML format
     * @throws ProcessMiningException if discovery fails
     */
    public PetriNet discover(EventLogHandle handle) {
        if (handle == null) {
            throw new IllegalArgumentException("Event log handle cannot be null");
        }

        String pnmlXml = discoverAlphaPlusPlus(handle.getHandle());
        if (pnmlXml == null) {
            throw new ProcessMiningException("Alpha++ discovery failed for event log handle: " + handle.getHandle());
        }

        if (pnmlXml.trim().isEmpty()) {
            throw new ProcessMiningException("Alpha++ discovery returned empty PNML");
        }

        return new PetriNet(pnmlXml);
    }

    /**
     * Validates that the event log handle is valid before discovery.
     *
     * @param handle Event log handle to validate
     * @throws IllegalArgumentException if the handle is invalid
     */
    public void validateEventLog(EventLogHandle handle) {
        if (handle == null) {
            throw new IllegalArgumentException("Event log handle cannot be null");
        }

        // Note: Could add additional validation if the native library supports it
        // For now, just check the handle value
        if (handle.getHandle() <= 0) {
            throw new IllegalArgumentException("Invalid event log handle: " + handle.getHandle());
        }
    }
}