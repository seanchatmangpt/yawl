package org.yawlfoundation.yawl.bridge.processmining;

/**
 * Represents a discovered Petri net structure.
 * Contains the PNML XML representation of the net.
 */
public class PetriNet {

    private final String pnmlXml;

    public PetriNet(String pnmlXml) {
        if (pnmlXml == null || pnmlXml.trim().isEmpty()) {
            throw new IllegalArgumentException("PNML XML cannot be null or empty");
        }
        this.pnmlXml = pnmlXml;
    }

    public String getPnmlXml() {
        return pnmlXml;
    }

    @Override
    public String toString() {
        return "PetriNet{pnmlLength=" + pnmlXml.length() + "}";
    }
}