/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTIES; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.economy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service contract system for the GregVerse marketplace.
 *
 * <p>Manages agreements between service providers (OTs) and consumers, including
 * contract terms, scheduling, deliverables, and compliance tracking. Supports
 * both standard and custom contract templates.</p>
 *
 * <h2>Contract Features</h2>
 * <ul>
 *   <li>Service scope and deliverables definition</li>
 *   <li>Scheduling and availability management</li>
 *   <li>Payment terms and scheduling</li>
 *   <li>Compliance and quality requirements</li>
 *   <li>Contract lifecycle management</li>
 * </ul>
 *
 * <h2>Contract Status</h2>
 * <ul>
 *   <li>DRAFT - Contract being negotiated</li>
 *   <li>PENDING - Accepted, awaiting start</li>
 *   <li>ACTIVE - Service delivery in progress</li>
 *   <li>COMPLETED - All deliverables fulfilled</li>
 *   <li>CANCELLED - Contract cancelled by either party</li>
 *   <li>TERMINATED - Contract terminated for breach</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ServiceContract.Template template = ServiceContract.Template.createStandardTherapyContract();
 * ServiceContract contract = new ServiceContract.Builder()
 *     .contractId("contract-001")
 *     .providerId("ot-001")
 *     .clientId("client-002")
 *     .serviceId("assessment")
 *     .template(template)
 *     .price(new MarketplaceCurrency(250.00))
 *     .scheduledStart(ZonedDateTime.now().plusDays(1))
 *     .scheduledDuration(Duration.ofHours(2))
 *     .build();
 *
 * // Accept the contract
 * contract.accept();
 *
 * // Complete deliverable
 * contract.markDeliverableCompleted("assessment-report");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ServiceContract {

    private static final Logger logger = LoggerFactory.getLogger(ServiceContract.class);

    // Contract status constants
    public enum Status {
        DRAFT,         // Contract being negotiated
        PENDING,       // Accepted, awaiting start
        ACTIVE,        // Service delivery in progress
        COMPLETED,     // All deliverables fulfilled
        CANCELLED,     // Contract cancelled by either party
        TERMINATED     // Contract terminated for breach
    }

    // Template types
    public enum TemplateType {
        STANDARD_THERAPY,     // Standard OT therapy contract
        CUSTOM,               // Custom contract terms
        PREMIUM,             // Premium service contract
        GROUP_SESSION,       // Group therapy session
        CONSULTATION         // Consultation session
    }

    // Contract data storage
    private final String contractId;
    private final String providerId;
    private final String clientId;
    private final String serviceId;
    private final Template template;
    private final MarketplaceCurrency price;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final ZonedDateTime scheduledStart;
    private final Duration scheduledDuration;
    private final Status status;

    // Contract terms
    private final Map<String, String> terms;
    private final List<String> deliverables;
    private final Map<String, DeliverableStatus> deliverableStatuses;
    private final Map<String, String> clientRequirements;
    private final List<PaymentSchedule> paymentSchedule;
    private final List<ContractEvent> eventHistory;

    // Performance metrics
    private final Instant actualStart;
    private final Instant actualCompletion;
    private final Duration actualDuration;
    private final ServiceRating finalRating;

    /**
     * Creates a new service contract.
     */
    private ServiceContract(Builder builder) {
        this.contractId = Objects.requireNonNull(builder.contractId);
        this.providerId = Objects.requireNonNull(builder.providerId);
        this.clientId = Objects.requireNonNull(builder.clientId);
        this.serviceId = Objects.requireNonNull(builder.serviceId);
        this.template = Objects.requireNonNull(builder.template);
        this.price = Objects.requireNonNull(builder.price);
        this.scheduledStart = Objects.requireNonNull(builder.scheduledStart);
        this.scheduledDuration = Objects.requireNonNull(builder.scheduledDuration);
        this.status = builder.status;
        this.terms = Objects.requireNonNull(builder.terms);
        this.deliverables = Objects.requireNonNull(builder.deliverables);
        this.deliverableStatuses = Objects.requireNonNull(builder.deliverableStatuses);
        this.clientRequirements = Objects.requireNonNull(builder.clientRequirements);
        this.paymentSchedule = Objects.requireNonNull(builder.paymentSchedule);
        this.eventHistory = Objects.requireNonNull(builder.eventHistory);
        this.actualStart = builder.actualStart;
        this.actualCompletion = builder.actualCompletion;
        this.actualDuration = builder.actualDuration;
        this.finalRating = builder.finalRating;
        this.createdAt = Objects.requireNonNull(builder.createdAt);
        this.updatedAt = Objects.requireNonNull(builder.updatedAt);
    }

    /**
     * Gets the contract ID.
     *
     * @return contract ID
     */
    public String getContractId() {
        return contractId;
    }

    /**
     * Gets the provider ID.
     *
     * @return provider ID
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * Gets the client ID.
     *
     * @return client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the service ID.
     *
     * @return service ID
     */
    public String getServiceId() {
        return serviceId;
    }

    /**
     * Gets the contract template.
     *
     * @return contract template
     */
    public Template getTemplate() {
        return template;
    }

    /**
     * Gets the contract price.
     *
     * @return contract price
     */
    public MarketplaceCurrency getPrice() {
        return price;
    }

    /**
     * Gets the creation timestamp.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the last updated timestamp.
     *
     * @return last updated timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Gets the scheduled start time.
     *
     * @return scheduled start time
     */
    public ZonedDateTime getScheduledStart() {
        return scheduledStart;
    }

    /**
     * Gets the scheduled duration.
     *
     * @return scheduled duration
     */
    public Duration getScheduledDuration() {
        return scheduledDuration;
    }

    /**
     * Gets the contract status.
     *
     * @return contract status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Gets the contract terms.
     *
     * @return contract terms
     */
    public Map<String, String> getTerms() {
        return Collections.unmodifiableMap(terms);
    }

    /**
     * Gets the deliverables list.
     *
     * @return list of deliverables
     */
    public List<String> getDeliverables() {
        return Collections.unmodifiableList(deliverables);
    }

    /**
     * Gets the deliverable statuses.
     *
     * @return map of deliverable to status
     */
    public Map<String, DeliverableStatus> getDeliverableStatuses() {
        return Collections.unmodifiableMap(deliverableStatuses);
    }

    /**
     * Gets client requirements.
     *
     * @return client requirements
     */
    public Map<String, String> getClientRequirements() {
        return Collections.unmodifiableMap(clientRequirements);
    }

    /**
     * Gets the payment schedule.
     *
     * @return payment schedule
     */
    public List<PaymentSchedule> getPaymentSchedule() {
        return Collections.unmodifiableList(paymentSchedule);
    }

    /**
     * Gets the event history.
     *
     * @return event history
     */
    public List<ContractEvent> getEventHistory() {
        return Collections.unmodifiableList(eventHistory);
    }

    /**
     * Gets the actual start time.
     *
     * @return actual start time
     */
    public Optional<Instant> getActualStart() {
        return Optional.ofNullable(actualStart);
    }

    /**
     * Gets the actual completion time.
     *
     * @return actual completion time
     */
    public Optional<Instant> getActualCompletion() {
        return Optional.ofNullable(actualCompletion);
    }

    /**
     * Gets the actual duration.
     *
     * @return actual duration
     */
    public Optional<Duration> getActualDuration() {
        return Optional.ofNullable(actualDuration);
    }

    /**
     * Gets the final rating.
     *
     * @return final rating
     */
    public Optional<ServiceRating> getFinalRating() {
        return Optional.ofNullable(finalRating);
    }

    /**
     * Accepts the contract.
     *
     * @return updated contract
     */
    public ServiceContract accept() {
        if (status != Status.DRAFT && status != Status.PENDING) {
            throw new IllegalStateException("Cannot accept contract in status: " + status);
        }

        ContractEvent event = new ContractEvent(
            "contract-accepted", "Contract accepted by both parties", Instant.now()
        );

        return updateStatus(Status.PENDING, event);
    }

    /**
     * Starts the contract service delivery.
     *
     * @return updated contract
     */
    public ServiceContract start() {
        if (status != Status.PENDING && status != Status.ACTIVE) {
            throw new IllegalStateException("Cannot start contract in status: " + status);
        }

        ContractEvent event = new ContractEvent(
            "service-started", "Service delivery started", Instant.now()
        );

        return updateStatus(Status.ACTIVE, event)
            .withActualStart(Instant.now());
    }

    /**
     * Marks a deliverable as completed.
     *
     * @param deliverableId the deliverable ID
     * @return updated contract
     */
    public ServiceContract markDeliverableCompleted(String deliverableId) {
        Objects.requireNonNull(deliverableId, "Deliverable ID must not be null");

        if (status != Status.ACTIVE) {
            throw new IllegalStateException("Cannot mark deliverable completed in status: " + status);
        }

        if (!deliverables.contains(deliverableId)) {
            throw new IllegalArgumentException("Unknown deliverable: " + deliverableId);
        }

        DeliverableStatus newStatus = DeliverableStatus.COMPLETED;
        Instant now = Instant.now();

        ContractEvent event = new ContractEvent(
            "deliverable-completed",
            "Deliverable '" + deliverableId + "' completed",
            now
        );

        Map<String, DeliverableStatus> newDeliverableStatuses = new HashMap<>(deliverableStatuses);
        newDeliverableStatuses.put(deliverableId, DeliverableStatus.COMPLETED);

        // Check if all deliverables are completed
        boolean allCompleted = newDeliverableStatuses.values().stream()
            .allMatch(s -> s == DeliverableStatus.COMPLETED);

        Status updatedStatus = allCompleted ? Status.COMPLETED : this.status;

        ServiceContract updated = new ServiceContract.Builder(this)
            .deliverableStatuses(newDeliverableStatuses)
            .status(updatedStatus)
            .eventHistory(addEvent(event))
            .updatedAt(now)
            .build();

        if (allCompleted) {
            updated = updated.withActualCompletion(now);
        }

        return updated;
    }

    /**
     * Completes the contract.
     *
     * @param finalRating the final service rating
     * @return updated contract
     */
    public ServiceContract complete(ServiceRating finalRating) {
        Objects.requireNonNull(finalRating, "Final rating must not be null");

        if (status != Status.ACTIVE && status != Status.COMPLETED) {
            throw new IllegalStateException("Cannot complete contract in status: " + status);
        }

        // Check if all deliverables are completed
        if (deliverableStatuses.values().stream().anyMatch(s -> s != DeliverableStatus.COMPLETED)) {
            throw new IllegalStateException("Cannot complete contract with incomplete deliverables");
        }

        ContractEvent event = new ContractEvent(
            "contract-completed", "Contract fully completed", Instant.now()
        );

        return updateStatus(Status.COMPLETED, event)
            .withActualCompletion(Instant.now())
            .withFinalRating(finalRating);
    }

    /**
     * Cancels the contract.
     *
     * @param reason cancellation reason
     * @return updated contract
     */
    public ServiceContract cancel(String reason) {
        Objects.requireNonNull(reason, "Cancellation reason must not be null");

        if (status == Status.COMPLETED || status == Status.TERMINATED || status == Status.CANCELLED) {
            throw new IllegalStateException("Cannot cancel contract in status: " + status);
        }

        ContractEvent event = new ContractEvent(
            "contract-cancelled", "Contract cancelled: " + reason, Instant.now()
        );

        return updateStatus(Status.CANCELLED, event);
    }

    /**
     * Terminates the contract for breach.
     *
     * @param reason termination reason
     * @return updated contract
     */
    public ServiceContract terminate(String reason) {
        Objects.requireNonNull(reason, "Termination reason must not be null");

        if (status == Status.COMPLETED || status == Status.CANCELLED) {
            throw new IllegalStateException("Cannot terminate contract in status: " + status);
        }

        ContractEvent event = new ContractEvent(
            "contract-terminated", "Contract terminated for breach: " + reason, Instant.now()
        );

        return updateStatus(Status.TERMINATED, event);
    }

    /**
     * Adds a payment to the payment schedule.
     *
     * @param payment the payment to add
     * @return updated contract
     */
    public ServiceContract addPayment(Payment payment) {
        Objects.requireNonNull(payment, "Payment must not be null");

        List<PaymentSchedule> newSchedule = new ArrayList<>(paymentSchedule);
        newSchedule.add(new PaymentSchedule(
            "payment-" + UUID.randomUUID().toString(),
            payment.getAmount(),
            payment.getDueDate(),
            payment.getStatus()
        ));

        return new ServiceContract.Builder(this)
            .paymentSchedule(newSchedule)
            .updatedAt(Instant.now())
            .build();
    }

    /**
     * Checks if the contract is overdue.
     *
     * @return true if contract is overdue
     */
    public boolean isOverdue() {
        if (status == Status.COMPLETED || status == Status.CANCELLED || status == Status.TERMINATED) {
            return false;
        }

        Instant now = Instant.now();
        ZonedDateTime scheduledEnd = scheduledStart.plus(scheduledDuration);
        return now.isAfter(scheduledEnd.toInstant());
    }

    /**
     * Gets days until scheduled start.
     *
     * @return days until start, or negative if already started
     */
    public long getDaysUntilStart() {
        Instant now = Instant.now();
        return Duration.between(now, scheduledStart.toInstant()).toDays();
    }

    /**
     * Calculates contract completion percentage.
     *
     * @return completion percentage (0-100)
     */
    public int getCompletionPercentage() {
        if (deliverables.isEmpty()) {
            return 0;
        }

        long completedCount = deliverableStatuses.values().stream()
            .filter(s -> s == DeliverableStatus.COMPLETED)
            .count();

        return (int) ((completedCount * 100) / deliverables.size());
    }

    /**
     * Validates contract integrity.
     *
     * @return true if contract is valid
     */
    public boolean validate() {
        try {
            // Check required fields
            if (contractId == null || providerId == null || clientId == null ||
                serviceId == null || template == null || price == null ||
                scheduledStart == null || scheduledDuration == null || status == null) {
                return false;
            }

            // Check deliverable status consistency
            for (String deliverable : deliverables) {
                if (!deliverableStatuses.containsKey(deliverable)) {
                    return false;
                }
            }

            // Check payment schedule totals
            MarketplaceCurrency totalScheduled = paymentSchedule.stream()
                .map(PaymentSchedule::getAmount)
                .reduce(MarketplaceCurrency.ZERO, MarketplaceCurrency::add);

            if (!totalScheduled.equals(price)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("Contract validation failed", e);
            return false;
        }
    }

    // Helper methods

    private ServiceContract updateStatus(Status newStatus, ContractEvent event) {
        ServiceContract updated = new ServiceContract.Builder(this)
            .status(newStatus)
            .eventHistory(addEvent(event))
            .updatedAt(Instant.now())
            .build();

        logger.info("Contract {} status changed to {} by event: {}",
            contractId, newStatus, event.getDescription());

        return updated;
    }

    private List<ContractEvent> addEvent(ContractEvent event) {
        List<ContractEvent> newHistory = new ArrayList<>(eventHistory);
        newHistory.add(event);
        return newHistory;
    }

    private ServiceContract withActualStart(Instant actualStart) {
        return new ServiceContract.Builder(this)
            .actualStart(actualStart)
            .updatedAt(Instant.now())
            .build();
    }

    private ServiceContract withActualCompletion(Instant actualCompletion) {
        Duration actualDuration = this.actualStart != null && actualCompletion != null ?
            Duration.between(this.actualStart, actualCompletion) : null;

        return new ServiceContract.Builder(this)
            .actualCompletion(actualCompletion)
            .actualDuration(actualDuration)
            .updatedAt(Instant.now())
            .build();
    }

    private ServiceContract withFinalRating(ServiceRating finalRating) {
        return new ServiceContract.Builder(this)
            .finalRating(finalRating)
            .updatedAt(Instant.now())
            .build();
    }

    // Builder pattern

    public static class Builder {
        private String contractId;
        private String providerId;
        private String clientId;
        private String serviceId;
        private Template template;
        private MarketplaceCurrency price;
        private Instant createdAt;
        private Instant updatedAt;
        private ZonedDateTime scheduledStart;
        private Duration scheduledDuration;
        private Status status = Status.DRAFT;

        private Map<String, String> terms = new HashMap<>();
        private List<String> deliverables = new ArrayList<>();
        private Map<String, DeliverableStatus> deliverableStatuses = new HashMap<>();
        private Map<String, String> clientRequirements = new HashMap<>();
        private List<PaymentSchedule> paymentSchedule = new ArrayList<>();
        private List<ContractEvent> eventHistory = new ArrayList<>();
        private Instant actualStart;
        private Instant actualCompletion;
        private Duration actualDuration;
        private ServiceRating finalRating;

        public Builder(String contractId, String providerId, String clientId,
                      String serviceId, Template template, MarketplaceCurrency price,
                      ZonedDateTime scheduledStart, Duration scheduledDuration) {
            this.contractId = contractId;
            this.providerId = providerId;
            this.clientId = clientId;
            this.serviceId = serviceId;
            this.template = template;
            this.price = price;
            this.scheduledStart = scheduledStart;
            this.scheduledDuration = scheduledDuration;
            this.createdAt = Instant.now();
            this.updatedAt = Instant.now();
        }

        public ServiceContract build() {
            return new ServiceContract(this);
        }

        public Builder(ServiceContract other) {
            this.contractId = other.getContractId();
            this.providerId = other.getProviderId();
            this.clientId = other.getClientId();
            this.serviceId = other.getServiceId();
            this.template = other.getTemplate();
            this.price = other.getPrice();
            this.createdAt = other.getCreatedAt();
            this.updatedAt = other.getUpdatedAt();
            this.scheduledStart = other.getScheduledStart();
            this.scheduledDuration = other.getScheduledDuration();
            this.status = other.getStatus();
            this.terms = new HashMap<>(other.getTerms());
            this.deliverables = new ArrayList<>(other.getDeliverables());
            this.deliverableStatuses = new HashMap<>(other.getDeliverableStatuses());
            this.clientRequirements = new HashMap<>(other.getClientRequirements());
            this.paymentSchedule = new ArrayList<>(other.getPaymentSchedule());
            this.eventHistory = new ArrayList<>(other.getEventHistory());
            this.actualStart = other.getActualStart().orElse(null);
            this.actualCompletion = other.getActualCompletion().orElse(null);
            this.actualDuration = other.getActualDuration().orElse(null);
            this.finalRating = other.getFinalRating().orElse(null);
        }

        // Setters
        public Builder status(Status status) { this.status = status; return this; }
        public Builder terms(Map<String, String> terms) { this.terms = terms; return this; }
        public Builder deliverables(List<String> deliverables) { this.deliverables = deliverables; return this; }
        public Builder deliverableStatuses(Map<String, DeliverableStatus> deliverableStatuses) {
            this.deliverableStatuses = deliverableStatuses;
            return this;
        }
        public Builder clientRequirements(Map<String, String> clientRequirements) {
            this.clientRequirements = clientRequirements;
            return this;
        }
        public Builder paymentSchedule(List<PaymentSchedule> paymentSchedule) {
            this.paymentSchedule = paymentSchedule;
            return this;
        }
        public Builder eventHistory(List<ContractEvent> eventHistory) {
            this.eventHistory = eventHistory;
            return this;
        }
        public Builder actualStart(Instant actualStart) { this.actualStart = actualStart; return this; }
        public Builder actualCompletion(Instant actualCompletion) {
            this.actualCompletion = actualCompletion;
            return this;
        }
        public Builder actualDuration(Duration actualDuration) { this.actualDuration = actualDuration; return this; }
        public Builder finalRating(ServiceRating finalRating) { this.finalRating = finalRating; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
    }

    // Contract template system

    public static final class Template {
        private final String templateId;
        private final TemplateType type;
        private final String name;
        private final String description;
        private final Map<String, String> defaultTerms;
        private final List<String> defaultDeliverables;

        public Template(String templateId, TemplateType type, String name, String description,
                      Map<String, String> defaultTerms, List<String> defaultDeliverables) {
            this.templateId = Objects.requireNonNull(templateId);
            this.type = Objects.requireNonNull(type);
            this.name = Objects.requireNonNull(name);
            this.description = Objects.requireNonNull(description);
            this.defaultTerms = Objects.requireNonNull(defaultTerms);
            this.defaultDeliverables = Objects.requireNonNull(defaultDeliverables);
        }

        // Getters
        public String getTemplateId() { return templateId; }
        public TemplateType getType() { return type; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, String> getDefaultTerms() { return defaultTerms; }
        public List<String> getDefaultDeliverables() { return defaultDeliverables; }

        public static Template createStandardTherapyContract() {
            Map<String, String> terms = Map.of(
                "cancellation-policy", "24 hours notice required",
                "confidentiality", "All sessions are confidential",
                "payment-terms", "Payment due within 30 days of invoice",
                "rescheduling", "Up to 2 reschedules allowed per contract"
            );

            List<String> deliverables = List.of(
                "initial-assessment", "treatment-plan", "progress-reports", "discharge-summary"
            );

            return new Template(
                "standard-therapy", TemplateType.STANDARD_THERAPY,
                "Standard Therapy Contract",
                "Standard contract for occupational therapy services",
                terms, deliverables
            );
        }

        public static Template createCustomContract(Map<String, String> customTerms, List<String> customDeliverables) {
            return new Template(
                "custom-" + UUID.randomUUID().toString(),
                TemplateType.CUSTOM,
                "Custom Service Contract",
                "Custom contract terms and deliverables",
                customTerms, customDeliverables
            );
        }
    }

    // Supporting classes

    public enum DeliverableStatus {
        PENDING,     // Deliverable not yet started
        IN_PROGRESS, // Deliverable in progress
        COMPLETED,   // Deliverable completed
        OVERDUE,     // Deliverable overdue
        NOT_REQUIRED // Deliverable not applicable
    }

    public static final class PaymentSchedule {
        private final String paymentId;
        private final MarketplaceCurrency amount;
        private final Instant dueDate;
        private final PaymentStatus status;

        public PaymentSchedule(String paymentId, MarketplaceCurrency amount, Instant dueDate, PaymentStatus status) {
            this.paymentId = Objects.requireNonNull(paymentId);
            this.amount = Objects.requireNonNull(amount);
            this.dueDate = Objects.requireNonNull(dueDate);
            this.status = Objects.requireNonNull(status);
        }

        // Getters
        public String getPaymentId() { return paymentId; }
        public MarketplaceCurrency getAmount() { return amount; }
        public Instant getDueDate() { return dueDate; }
        public PaymentStatus getStatus() { return status; }
    }

    public enum PaymentStatus {
        PENDING,   // Payment not yet due
        OVERDUE,   // Payment overdue
        PAID,      // Payment completed
        CANCELLED  // Payment cancelled
    }

    public static final class ContractEvent {
        private final String eventType;
        private final String description;
        private final Instant timestamp;

        public ContractEvent(String eventType, String description, Instant timestamp) {
            this.eventType = Objects.requireNonNull(eventType);
            this.description = Objects.requireNonNull(description);
            this.timestamp = Objects.requireNonNull(timestamp);
        }

        // Getters
        public String getEventType() { return eventType; }
        public String getDescription() { return description; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static final class ServiceRating {
        private final String ratingId;
        private final int qualityRating;
        private final int reliabilityRating;
        private final int communicationRating;
        private final int satisfactionRating;
        private final Instant timestamp;

        public ServiceRating(String ratingId, int qualityRating, int reliabilityRating,
                           int communicationRating, int satisfactionRating, Instant timestamp) {
            this.ratingId = Objects.requireNonNull(ratingId);
            this.qualityRating = qualityRating;
            this.reliabilityRating = reliabilityRating;
            this.communicationRating = communicationRating;
            this.satisfactionRating = satisfactionRating;
            this.timestamp = Objects.requireNonNull(timestamp);
        }

        // Getters
        public String getRatingId() { return ratingId; }
        public int getQualityRating() { return qualityRating; }
        public int getReliabilityRating() { return reliabilityRating; }
        public int getCommunicationRating() { return communicationRating; }
        public int getSatisfactionRating() { return satisfactionRating; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static final class Payment {
        private final String paymentId;
        private final MarketplaceCurrency amount;
        private final Instant dueDate;
        private final PaymentStatus status;

        public Payment(String paymentId, MarketplaceCurrency amount, Instant dueDate, PaymentStatus status) {
            this.paymentId = Objects.requireNonNull(paymentId);
            this.amount = Objects.requireNonNull(amount);
            this.dueDate = Objects.requireNonNull(dueDate);
            this.status = Objects.requireNonNull(status);
        }

        // Getters
        public String getPaymentId() { return paymentId; }
        public MarketplaceCurrency getAmount() { return amount; }
        public Instant getDueDate() { return dueDate; }
        public PaymentStatus getStatus() { return status; }
    }
}