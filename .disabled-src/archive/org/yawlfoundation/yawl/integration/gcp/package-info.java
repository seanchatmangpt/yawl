/**
 * GCP Marketplace EntitlementService integration for YAWL.
 *
 * <p>Provides connectors for Google Cloud Platform Marketplace:
 * <ul>
 *   <li>{@code auth} - GCP Service Account authentication (JWT-based OAuth2)</li>
 *   <li>{@code marketplace} - EntitlementServiceClient: entitlement lifecycle management</li>
 *   <li>{@code billing} - BillingConnector: Cloud Billing usage reporting</li>
 *   <li>{@code webhook} - Webhook handlers: provisioning and deprovisioning events</li>
 * </ul>
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code GCP_SERVICE_ACCOUNT_KEY_JSON} - Service account key JSON (required)</li>
 *   <li>{@code GCP_PROJECT_ID} - GCP project ID (required)</li>
 *   <li>{@code GCP_BILLING_ACCOUNT_ID} - Cloud Billing account ID (required)</li>
 *   <li>{@code GCP_MARKETPLACE_PRODUCT_NAME} - Marketplace product resource name (required)</li>
 * </ul>
 *
 * <p>All HTTP calls use OkHttp with exponential backoff retry (max 3 attempts).
 * All credentials are loaded from environment variables only - never hardcoded.
 *
 * @see org.yawlfoundation.yawl.integration.gcp.auth.GcpServiceAccountCredentials
 * @see org.yawlfoundation.yawl.integration.gcp.marketplace.EntitlementServiceClient
 * @see org.yawlfoundation.yawl.integration.gcp.billing.BillingConnector
 * @see org.yawlfoundation.yawl.integration.gcp.webhook.MarketplaceWebhookServer
 */
package org.yawlfoundation.yawl.integration.gcp;
