package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.time.Instant;
import java.util.List;

/**
 * Vendor lifecycle events from the marketplace.
 *
 * <p><b>Lifecycle</b>:
 * <ul>
 *   <li>VendorOnboarded: New vendor registered</li>
 *   <li>VendorVerified: Vendor passed verification (KYC/compliance)</li>
 *   <li>VendorSuspended: Vendor account suspended</li>
 *   <li>VendorOffboarded: Vendor exited marketplace</li>
 * </ul>
 *
 * <p><b>Idempotency</b>: The combination of (agentId, idempotencyToken) uniquely
 * identifies a logical event. Each vendor ID + sequence number is atomic.
 *
 * @param eventType MUST be "VendorOnboarded", "VendorVerified", "VendorSuspended", or "VendorOffboarded"
 * @param agentId unique identifier of the marketplace admin agent
 * @param idempotencyToken deduplication key (e.g. vendor_id:event_number)
 * @param timestamp when the event occurred
 * @param version semantic version
 * @param metadata optional tracing data
 * @param vendorId GCP vendor identifier
 * @param vendorName human-readable vendor name
 * @param status enum-like string: ONBOARDED, VERIFIED, SUSPENDED, OFFBOARDED
 * @param verificationDetails compliance and KYC information (nullable)
 * @param suspensionReason reason if suspended (nullable)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record VendorEvent(
    String eventType,
    String agentId,
    String idempotencyToken,
    Instant timestamp,
    String version,
    EventMetadata metadata,
    String vendorId,
    String vendorName,
    String status,
    VendorVerificationDetails verificationDetails,
    String suspensionReason
) implements MarketplaceEvent {

    /**
     * Canonical event type constants.
     */
    public static class EventTypes {
        public static final String VENDOR_ONBOARDED = "VendorOnboarded";
        public static final String VENDOR_VERIFIED = "VendorVerified";
        public static final String VENDOR_SUSPENDED = "VendorSuspended";
        public static final String VENDOR_OFFBOARDED = "VendorOffboarded";
    }

    /**
     * Vendor status constants.
     */
    public static class Status {
        public static final String ONBOARDED = "ONBOARDED";
        public static final String VERIFIED = "VERIFIED";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String OFFBOARDED = "OFFBOARDED";
    }

    /**
     * Construct a VendorEvent with comprehensive validation.
     *
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public VendorEvent {
        // Validate base fields (delegated to sealed interface static helper)
        MarketplaceEvent.validateBaseFields(eventType, agentId, idempotencyToken, timestamp, version);

        // Validate vendor-specific fields
        if (vendorId == null || vendorId.isBlank()) {
            throw new IllegalArgumentException("vendorId is required");
        }
        if (vendorName == null || vendorName.isBlank()) {
            throw new IllegalArgumentException("vendorName is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }

        // Validate consistency: status and optional fields
        if (Status.SUSPENDED.equals(status)
                && (suspensionReason == null || suspensionReason.isBlank())) {
            throw new IllegalArgumentException(
                "suspensionReason is required for status " + status);
        }
    }

    /**
     * Builder for fluent VendorEvent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for VendorEvent.
     */
    public static class Builder {
        private String eventType = EventTypes.VENDOR_ONBOARDED;
        private String agentId;
        private String idempotencyToken;
        private Instant timestamp = Instant.now();
        private String version = "1.0";
        private EventMetadata metadata;
        private String vendorId;
        private String vendorName;
        private String status = Status.ONBOARDED;
        private VendorVerificationDetails verificationDetails;
        private String suspensionReason;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder idempotencyToken(String idempotencyToken) {
            this.idempotencyToken = idempotencyToken;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder metadata(EventMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder vendorId(String vendorId) {
            this.vendorId = vendorId;
            return this;
        }

        public Builder vendorName(String vendorName) {
            this.vendorName = vendorName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder verificationDetails(VendorVerificationDetails verificationDetails) {
            this.verificationDetails = verificationDetails;
            return this;
        }

        public Builder suspensionReason(String suspensionReason) {
            this.suspensionReason = suspensionReason;
            return this;
        }

        public VendorEvent build() {
            return new VendorEvent(
                eventType, agentId, idempotencyToken, timestamp, version, metadata,
                vendorId, vendorName, status, verificationDetails, suspensionReason);
        }
    }

    /**
     * Check if this vendor is active in the marketplace.
     */
    public boolean isActive() {
        return Status.VERIFIED.equals(status);
    }
}
