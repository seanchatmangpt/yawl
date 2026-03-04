package org.yawlfoundation.yawl.bridge.processmining;

/**
 * JNI wrapper for conformance checking functionality.
 * Checks how well an event log fits a discovered Petri net.
 */
public class ConformanceChecker {

    private native double checkConformance(long eventLogHandle, String pnmlXml);

    /**
     * Performs conformance checking between an event log and Petri net.
     *
     * @param handle Event log handle obtained from XesImporter
     * @param net Petri net obtained from AlphaMiner.discover()
     * @return Conformance result with fitness score and details
     * @throws ProcessMiningException if conformance checking fails
     */
    public ConformanceResult check(EventLogHandle handle, PetriNet net) {
        if (handle == null) {
            throw new IllegalArgumentException("Event log handle cannot be null");
        }

        if (net == null) {
            throw new IllegalArgumentException("Petri net cannot be null");
        }

        validateEventLog(handle);
        validatePetriNet(net);

        double fitness;
        try {
            fitness = checkConformance(handle.getHandle(), net.getPnmlXml());
        } catch (Exception e) {
            throw new ProcessMiningException("Conformance checking failed: " + e.getMessage(), e);
        }

        if (fitness < 0 || fitness > 1) {
            throw new ProcessMiningException("Invalid fitness value returned: " + fitness);
        }

        String details = String.format("Conformance fitness: %.4f", fitness);
        return new ConformanceResult(fitness, details);
    }

    /**
     * Validates that the event log handle is valid.
     *
     * @param handle Event log handle to validate
     * @throws IllegalArgumentException if the handle is invalid
     */
    public void validateEventLog(EventLogHandle handle) {
        if (handle == null) {
            throw new IllegalArgumentException("Event log handle cannot be null");
        }

        if (handle.getHandle() <= 0) {
            throw new IllegalArgumentException("Invalid event log handle: " + handle.getHandle());
        }
    }

    /**
     * Validates that the Petri net is valid.
     *
     * @param net Petri net to validate
     * @throws IllegalArgumentException if the net is invalid
     */
    public void validatePetriNet(PetriNet net) {
        if (net == null) {
            throw new IllegalArgumentException("Petri net cannot be null");
        }

        String pnmlXml = net.getPnmlXml();
        if (pnmlXml == null || pnmlXml.trim().isEmpty()) {
            throw new IllegalArgumentException("Petri net PNML cannot be null or empty");
        }

        if (pnmlXml.length() < 100) {
            throw new IllegalArgumentException("Petri net PNML appears to be too short");
        }

        if (!pnmlXml.contains("<pnml")) {
            throw new IllegalArgumentException("PNML does not contain required <pnml> root element");
        }
    }

    /**
     * Checks if a Petri net is suitable for conformance checking.
     *
     * @param net Petri net to check
     * @throws ProcessMiningException if the net is not suitable
     */
    public void validateForConformance(PetriNet net) {
        validatePetriNet(net);

        String pnmlXml = net.getPnmlXml();
        if (!pnmlXml.contains("<net")) {
            throw new ProcessMiningException("Petri net PNML does not contain required <net> element");
        }

        if (!pnmlXml.contains("<place") || !pnmlXml.contains("<transition")) {
            throw new ProcessMiningException("Petri net must contain both places and transitions");
        }
    }
}