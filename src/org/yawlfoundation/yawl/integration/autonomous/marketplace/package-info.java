/**
 * GCP Marketplace autonomous agent integration for YAWL.
 *
 * <h2>Overview</h2>
 * This package provides event schemas and autonomous agent integration for
 * GCP Cloud Marketplace, enabling YAWL to coordinate with vendor, fulfillment,
 * and payment processing agents.
 *
 * <h2>Event Types</h2>
 * <ul>
 *   <li><b>Order Events:</b> OrderCreated, OrderConfirmed, OrderShipped,
 *       OrderDelivered, OrderReturned</li>
 *   <li><b>Vendor Events:</b> VendorOnboarded, VendorVerified, VendorSuspended</li>
 *   <li><b>Payment Events:</b> PaymentAuthorized, PaymentCaptured, PaymentFailed,
 *       PayoutInitiated</li>
 * </ul>
 *
 * <h2>Message Ordering & Idempotency</h2>
 * All events include:
 * <ul>
 *   <li><b>sequenceNumber:</b> Monotonic counter per stream (ensures ordering)</li>
 *   <li><b>idempotencyKey:</b> Unique identifier (deduplicates replays)</li>
 *   <li><b>timestamp:</b> UTC timestamp for causality tracking</li>
 * </ul>
 *
 * <h2>Workflow Integration</h2>
 * Events trigger corresponding YAWL workflow cases:
 * <ul>
 *   <li>OrderCreatedEvent → launches "ProcessOrder" case</li>
 *   <li>VendorOnboardedEvent → launches "OnboardVendor" case</li>
 *   <li>PaymentFailedEvent → launches "HandlePaymentFailure" case</li>
 * </ul>
 *
 * @since 6.0.0
 * @author YAWL Marketplace Integration
 */
package org.yawlfoundation.yawl.integration.autonomous.marketplace;
