/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

/**
 * Receipt generation and management for immutable case history (BBB model).
 *
 * <p>This package implements the BBB (Blockchain-inspired Building Block) coordination
 * model for YAWL case execution. Every case transition (state change) is recorded as
 * a receipt that forms part of an immutable, hash-chained audit trail.</p>
 *
 * <p>Contents:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.receipts.Receipt} - Immutable atom of
 *       case history. Each receipt captures a case transition with before/after state,
 *       the delta (change), admission status (COMMITTED or REJECTED), and SHA-256 hash
 *       for tamper detection.</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.receipts.ReceiptChain} - Singleton facade
 *       for BBB coordination. Main API for committing receipts, retrieving audit trails,
 *       verifying chain integrity, and computing authoritative case state.</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.receipts.ReceiptStore} - Persistent ledger
 *       of case history. Thread-safe store supporting efficient lookups by case ID,
 *       receipt ID, hash, timestamp range, and admission status.</li>
 * </ul></p>
 *
 * <p>BBB Model Properties:
 * <ul>
 *   <li>Immutable: receipts are never deleted or modified after creation</li>
 *   <li>Chained: each receipt links to parent via SHA-256 hash (forms chain)</li>
 *   <li>Atomic: append-only operations; no transactions needed</li>
 *   <li>Queryable: efficient lookups for audit, compliance, and debugging</li>
 *   <li>Tamper-evident: chain integrity verification detects any modifications</li>
 * </ul></p>
 *
 * <p>Receipt fields:
 * <ul>
 *   <li>case_id: which case was affected</li>
 *   <li>token_holder: work item or resource holding the token</li>
 *   <li>before_state: case snapshot before transition (JSON)</li>
 *   <li>delta: proposed mutation (up to 8 facts for CONSTRUCT8 bound)</li>
 *   <li>after_state: case snapshot after (null if rejected)</li>
 *   <li>admission: COMMITTED (lawful) or REJECTED_* (policy/schema violation)</li>
 *   <li>hash: SHA-256 of canonical form</li>
 *   <li>parent_hash: links to previous receipt (null for root)</li>
 *   <li>ingress_source: "MCP", "A2A", "API", or null for internal</li>
 * </ul></p>
 *
 * <p>Usage from YEngine:
 * <pre>{@code
 * // At transition time:
 * Receipt.Builder b = new Receipt.Builder(receiptId, caseId, workItemId, timestamp);
 * b.beforeState(serializedCaseState)
 *  .delta(deltaJson)
 *  .validatorId("YNetRunner")
 *  .admitted();  // or .rejected(Admission.REJECTED_POLICY, "reason")
 *
 * receiptChain.commit(caseId, b);  // Appends to ledger
 * }</pre></p>
 *
 * <p>Core principle: "Past is SELECT-only." No DELETE, only INSERT.</p>
 *
 * @since 5.2
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.engine.receipts;
