/**
 * GCP Marketplace Integration Package
 *
 * Provides MCP and A2A integration points for autonomous marketplace operations:
 * - Vendor agent ↔ YAWL engine (order notifications)
 * - Fulfillment agent ↔ YAWL engine (status updates)
 * - Payment processor ↔ YAWL engine (transaction confirmations)
 *
 * Subpackages:
 * - events: Domain model for marketplace events (orders, vendors, payments)
 * - mcp: Model Context Protocol endpoints for autonomous agents
 * - a2a: Agent-to-Agent message protocols with idempotency guarantees
 * - gateway: GCP Pub/Sub integration for event sourcing
 *
 * Architecture:
 * - Event schema: JSON records with message ordering and deduplication keys
 * - MCP tools: stateless endpoints for querying/modifying marketplace state
 * - A2A messages: authenticated asynchronous notifications between agents
 * - Idempotency: all operations keyed on idempotency token + version
 *
 * Message Ordering Guarantee (per agent):
 * - Events from same agent processed strictly in order
 * - Duplicate detection via (agent_id, idempotency_token) key
 * - At-least-once delivery with idempotent processing
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 2026-02-21
 */
package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.MarketplaceEvent;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.OrderEvent;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.PaymentEvent;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.VendorEvent;
