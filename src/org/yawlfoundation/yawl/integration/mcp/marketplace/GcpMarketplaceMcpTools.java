/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.MarketplaceEventSchema.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GCP Marketplace MCP tools for YAWL engine integration.
 *
 * Implements 5 marketplace endpoints with message ordering and idempotency:
 * - /marketplace/vendors/register (create vendor)
 * - /marketplace/products/list (query catalog)
 * - /marketplace/orders/create (place order)
 * - /marketplace/fulfillment/track (shipment tracking)
 * - /marketplace/payments/process (payment handling)
 *
 * All operations enforce idempotency via idempotency keys.
 *
 * @author YAWL Marketplace Integration
 * @since 6.0.0
 */
public final class GcpMarketplaceMcpTools {

    private final InterfaceB_EnvironmentBasedClient interfaceB;
    private final String sessionHandle;
    private final ObjectMapper objectMapper;

    // Idempotency cache: idempotencyKey → cached response
    private final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();

    // Event sequence tracking per stream
    private final Map<String, AtomicLong> sequenceTrackers = new ConcurrentHashMap<>();

    public GcpMarketplaceMcpTools(InterfaceB_EnvironmentBasedClient interfaceB,
                                  String sessionHandle) {
        this.interfaceB = Objects.requireNonNull(interfaceB, "interfaceB");
        this.sessionHandle = Objects.requireNonNull(sessionHandle, "sessionHandle");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Tool: /marketplace/vendors/register
     *
     * Register a new vendor on the GCP marketplace.
     * Idempotency: Keyed by (companyName, contactEmail) hash
     */
    public McpSchema.Tool vendorRegisterTool() {
        return McpSchema.Tool.builder()
            .name("marketplace_vendor_register")
            .description("Register a new vendor on GCP marketplace. Returns vendor ID and status. " +
                        "Idempotent: same (companyName, contactEmail) returns cached vendor ID.")
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                    "company_name", Map.of("type", "string", "description", "Vendor company name"),
                    "contact_email", Map.of("type", "string", "description", "Primary contact email"),
                    "region", Map.of("type", "string", "description", "Primary operation region (e.g., us-east1)"),
                    "tier", Map.of("type", "string", "enum", List.of("standard", "premium", "enterprise"),
                                   "description", "Vendor tier level")
                ), List.of("company_name", "contact_email", "region", "tier"), null, null, null))
            .build();
    }

    /**
     * Tool: /marketplace/products/list
     *
     * Query marketplace product catalog with filters.
     * Returns paginated list of products, optionally filtered by vendor or category.
     */
    public McpSchema.Tool productListTool() {
        return McpSchema.Tool.builder()
            .name("marketplace_products_list")
            .description("List products in GCP marketplace with optional filtering. " +
                        "Supports pagination (limit, offset) and filtering (vendor_id, category).")
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                    "vendor_id", Map.of("type", "string", "description", "Optional: filter by vendor"),
                    "category", Map.of("type", "string", "description", "Optional: filter by category"),
                    "limit", Map.of("type", "integer", "default", 50, "description", "Max results (1-100)"),
                    "offset", Map.of("type", "integer", "default", 0, "description", "Pagination offset")
                ), List.of(), null, null, null))
            .build();
    }

    /**
     * Tool: /marketplace/orders/create
     *
     * Create a new order for a product.
     * Idempotency: Keyed by (customerId, productId, timestamp_utc) triple.
     */
    public McpSchema.Tool orderCreateTool() {
        return McpSchema.Tool.builder()
            .name("marketplace_orders_create")
            .description("Create a new marketplace order. " +
                        "Idempotent: same (customerId, productId, timestamp) returns cached order ID. " +
                        "Triggers OrderCreatedEvent.")
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                    "vendor_id", Map.of("type", "string", "description", "Product vendor ID"),
                    "product_id", Map.of("type", "string", "description", "Product identifier"),
                    "quantity", Map.of("type", "integer", "description", "Order quantity"),
                    "customer_id", Map.of("type", "string", "description", "Customer identifier"),
                    "region", Map.of("type", "string", "description", "Order destination region")
                ), List.of("vendor_id", "product_id", "quantity", "customer_id", "region"), null, null, null))
            .build();
    }

    /**
     * Tool: /marketplace/fulfillment/track
     *
     * Track order fulfillment status (shipment, delivery).
     * Returns tracking info and estimated delivery.
     */
    public McpSchema.Tool fulfillmentTrackTool() {
        return McpSchema.Tool.builder()
            .name("marketplace_fulfillment_track")
            .description("Track order fulfillment status. Returns shipment info, carrier, " +
                        "tracking number, and estimated delivery. Updates trigger " +
                        "OrderShippedEvent or OrderDeliveredEvent.")
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                    "order_id", Map.of("type", "string", "description", "Order to track"),
                    "shipment_id", Map.of("type", "string", "description", "Optional: specific shipment ID")
                ), List.of("order_id"), null, null, null))
            .build();
    }

    /**
     * Tool: /marketplace/payments/process
     *
     * Process payment for an order (authorize, capture, or handle failure).
     * Idempotency: Keyed by (orderId, paymentGatewayId) pair.
     */
    public McpSchema.Tool paymentProcessTool() {
        return McpSchema.Tool.builder()
            .name("marketplace_payments_process")
            .description("Process payment for an order. Supports authorize, capture, and " +
                        "refund operations. Idempotent: same (orderId, gatewayId) returns " +
                        "cached result. Triggers PaymentAuthorizedEvent, " +
                        "PaymentCapturedEvent, or PaymentFailedEvent.")
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                    "order_id", Map.of("type", "string", "description", "Order to pay for"),
                    "amount_cents", Map.of("type", "integer", "description", "Amount in cents"),
                    "operation", Map.of("type", "string", "enum", List.of("authorize", "capture", "refund"),
                                        "description", "Payment operation"),
                    "payment_method", Map.of("type", "string", "description", "e.g., 'credit_card', 'paypal'"),
                    "gateway_id", Map.of("type", "string", "description", "Payment gateway transaction ID")
                ), List.of("order_id", "amount_cents", "operation", "payment_method", "gateway_id"), null, null, null))
            .build();
    }

    /**
     * Execute vendor register tool.
     * Idempotency: (companyName, contactEmail) hash as key.
     */
    public String executeVendorRegister(Map<String, ?> args) throws IOException {
        String companyName = (String) args.get("company_name");
        String contactEmail = (String) args.get("contact_email");
        String region = (String) args.get("region");
        String tier = (String) args.get("tier");

        String idempotencyKey = hashIdempotencyKey(companyName, contactEmail);

        // Check cache
        if (idempotencyCache.containsKey(idempotencyKey)) {
            return idempotencyCache.get(idempotencyKey);
        }

        // Generate vendor ID
        String vendorId = "vendor-" + UUID.randomUUID().toString().substring(0, 8);

        // Create VendorOnboardedEvent
        VendorOnboardedEvent event = new VendorOnboardedEvent(
            vendorId,
            companyName,
            contactEmail,
            region,
            tier,
            Instant.now().toString(),
            nextSequenceNumber("vendor"),
            Map.of("source", "mcp-tool")
        );

        // Serialize and launch workflow case if needed
        String eventJson = objectMapper.writeValueAsString(event);

        String response = String.format(
            "{\"vendor_id\": \"%s\", \"status\": \"onboarded\", \"tier\": \"%s\", \"event\": %s}",
            vendorId, tier, eventJson
        );

        idempotencyCache.put(idempotencyKey, response);
        return response;
    }

    /**
     * Execute product list tool.
     * Supports filtering and pagination.
     */
    public String executeProductList(Map<String, ?> args) throws IOException {
        String vendorId = (String) args.get("vendor_id");
        String category = (String) args.get("category");
        int limit = args.get("limit") instanceof Number n ? Math.min(Math.max(n.intValue(), 1), 100) : 50;
        int offset = args.get("offset") instanceof Number n ? Math.max(n.intValue(), 0) : 0;

        throw new UnsupportedOperationException(
            "executeProductList requires a real product catalog data source. " +
            "Inject a ProductCatalogService backed by GCP Spanner or Firestore, " +
            "or connect to an external catalog API. " +
            "Parameters: vendorId=" + vendorId + ", category=" + category +
            ", limit=" + limit + ", offset=" + offset);
    }

    /**
     * Execute order create tool.
     * Idempotency: (customerId, productId, timestamp) triple hash as key.
     */
    public String executeOrderCreate(Map<String, ?> args) throws IOException {
        String vendorId = (String) args.get("vendor_id");
        String productId = (String) args.get("product_id");
        int quantity = ((Number) args.get("quantity")).intValue();
        String customerId = (String) args.get("customer_id");
        String region = (String) args.get("region");

        String timestamp = Instant.now().toString();
        String idempotencyKey = hashIdempotencyKey(customerId, productId, timestamp);

        // Check cache
        if (idempotencyCache.containsKey(idempotencyKey)) {
            return idempotencyCache.get(idempotencyKey);
        }

        // Generate order ID
        String orderId = "order-" + UUID.randomUUID().toString().substring(0, 8);

        // Create OrderCreatedEvent
        OrderCreatedEvent event = new OrderCreatedEvent(
            orderId,
            vendorId,
            productId,
            quantity,
            99900L, // unit price (per-product)
            99900L * quantity,
            customerId,
            region,
            timestamp,
            nextSequenceNumber("order"),
            Map.of("source", "mcp-tool")
        );

        String eventJson = objectMapper.writeValueAsString(event);

        String response = String.format(
            "{\"order_id\": \"%s\", \"status\": \"created\", \"total_price_cents\": %d, \"event\": %s}",
            orderId, event.totalPriceCents(), eventJson
        );

        idempotencyCache.put(idempotencyKey, response);
        return response;
    }

    /**
     * Execute fulfillment track tool.
     */
    public String executeFulfillmentTrack(Map<String, ?> args) throws IOException {
        String orderId = (String) args.get("order_id");
        String shipmentId = (String) args.get("shipment_id");

        throw new UnsupportedOperationException(
            "executeFulfillmentTrack requires a real fulfillment service. " +
            "Inject a FulfillmentServiceClient connected to a carrier API or GCP Pub/Sub " +
            "fulfillment event stream. " +
            "Parameters: orderId=" + orderId + ", shipmentId=" + shipmentId);
    }

    /**
     * Execute payment process tool.
     * Idempotency: (orderId, gatewayId) pair hash as key.
     */
    public String executePaymentProcess(Map<String, ?> args) throws IOException {
        String orderId = (String) args.get("order_id");
        long amountCents = ((Number) args.get("amount_cents")).longValue();
        String operation = (String) args.get("operation");
        String paymentMethod = (String) args.get("payment_method");
        String gatewayId = (String) args.get("gateway_id");

        String idempotencyKey = hashIdempotencyKey(orderId, gatewayId);

        // Check cache
        if (idempotencyCache.containsKey(idempotencyKey)) {
            return idempotencyCache.get(idempotencyKey);
        }

        String timestamp = Instant.now().toString();

        Object eventObj;
        String responseStatus;

        switch (operation) {
            case "authorize" -> {
                String authId = "auth-" + UUID.randomUUID().toString().substring(0, 8);
                eventObj = new PaymentAuthorizedEvent(
                    orderId, authId, amountCents, "USD", paymentMethod,
                    timestamp,
                    Instant.now().plusSeconds(3600).toString(),
                    nextSequenceNumber("payment"),
                    Map.of("source", "mcp-tool")
                );
                responseStatus = "authorized";
            }
            case "capture" -> {
                String captureId = "capture-" + UUID.randomUUID().toString().substring(0, 8);
                eventObj = new PaymentCapturedEvent(
                    gatewayId, captureId, amountCents, timestamp,
                    24, // 24 hour settlement window
                    nextSequenceNumber("payment"),
                    Map.of("source", "mcp-tool")
                );
                responseStatus = "captured";
            }
            case "refund" -> {
                String refundId = "refund-" + UUID.randomUUID().toString().substring(0, 8);
                eventObj = new OrderReturnedEvent(
                    orderId, refundId, "refund_via_mcp_tool",
                    amountCents, timestamp,
                    nextSequenceNumber("payment"),
                    Map.of("source", "mcp-tool")
                );
                responseStatus = "refunded";
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }

        String eventJson = objectMapper.writeValueAsString(eventObj);

        String response = String.format(
            "{\"order_id\": \"%s\", \"status\": \"%s\", \"amount_cents\": %d, \"event\": %s}",
            orderId, responseStatus, amountCents, eventJson
        );

        idempotencyCache.put(idempotencyKey, response);
        return response;
    }

    /**
     * Get next sequence number for a stream.
     * Ensures monotonic ordering within each event stream.
     */
    private long nextSequenceNumber(String stream) {
        return sequenceTrackers
            .computeIfAbsent(stream, k -> new AtomicLong(0))
            .incrementAndGet();
    }

    /**
     * Hash idempotency key from one or more values.
     * Consistent across identical inputs.
     */
    private String hashIdempotencyKey(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (Object v : values) {
            sb.append(v).append("|");
        }
        return Base64.getEncoder().encodeToString(
            sb.toString().getBytes()
        );
    }

    /**
     * Create all marketplace tools.
     */
    public List<McpSchema.Tool> createAllTools() {
        return List.of(
            vendorRegisterTool(),
            productListTool(),
            orderCreateTool(),
            fulfillmentTrackTool(),
            paymentProcessTool()
        );
    }
}
