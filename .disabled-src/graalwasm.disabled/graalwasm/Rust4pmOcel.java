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

package org.yawlfoundation.yawl.graalwasm;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * OCEL2 (Object-Centric Event Log) type-safe records for process mining.
 *
 * <p><strong>OCEL2 Specification</strong>:
 * See <a href="https://www.ocel-standard.org/">https://www.ocel-standard.org/</a></p>
 *
 * <p><strong>Zero-Copy Design</strong>: These records are immutable and hold references
 * to parsed WASM data. They do not perform deep copies of event logs.</p>
 *
 * <p><strong>Memory Efficiency</strong>: Use alongside {@link Rust4pmWrapper#parseOcel2XmlKeepInWasm(String)}
 * to avoid copying large logs between Java and WASM memory.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class Rust4pmOcel {

    /**
     * Opaque pointer to an OCEL2 log in WASM memory.
     *
     * <p><strong>Usage</strong>: Represents a parsed OCEL2 log that remains in WASM memory
     * for zero-copy analysis. Must be freed via {@link Rust4pmWrapper#destroyOcelPointer(long)}.</p>
     *
     * <p><strong>Immutability</strong>: The pointer value itself is immutable. The WASM data
     * it points to must not be modified externally.</p>
     *
     * <pre>{@code
     * record OcelPointer(long address) {
     *     public OcelPointer {
     *         if (address < 0) throw new IllegalArgumentException("address must be non-negative");
     *     }
     * }
     * }</pre>
     *
     * @param address WASM memory address; must be non-negative and valid
     */
    public record OcelPointer(long address) {
        public OcelPointer {
            if (address < 0) {
                throw new IllegalArgumentException("address must be non-negative, got: " + address);
            }
        }

        /**
         * Checks if this pointer is null (address == 0).
         *
         * @return true if address is 0 (null pointer)
         */
        public boolean isNull() {
            return address == 0L;
        }

        @Override
        public String toString() {
            return "OcelPointer@" + Long.toHexString(address);
        }
    }

    /**
     * OCEL2 event log metadata and structure.
     *
     * <p><strong>OCEL2 Definition</strong>: An object-centric event log containing
     * events, objects, and their relationships. Unlike traditional event logs (XES),
     * OCEL2 allows events to be related to multiple business objects.</p>
     *
     * <p><strong>Typical Structure</strong>:
     * <pre>{@code
     * {
     *   "ocel:version": "2.0",
     *   "ocel:objectTypes": ["order", "item", "shipment"],
     *   "ocel:eventTypes": ["create_order", "add_item", "ship_order"],
     *   "ocel:events": [
     *     {
     *       "ocel:eid": "e1",
     *       "ocel:type": "create_order",
     *       "ocel:timestamp": "2025-01-01T10:00:00Z",
     *       "ocel:omap": [{"ocel:oid": "o1", "ocel:type": "order"}]
     *     },
     *     ...
     *   ],
     *   "ocel:objects": [
     *     {
     *       "ocel:oid": "o1",
     *       "ocel:type": "order",
     *       "ocel:ovmap": {"customer_id": "C123", "total": 500.0}
     *     },
     *     ...
     *   ]
     * }
     * }</pre>
     * </p>
     *
     * @param version OCEL2 version string (e.g., "2.0"); must not be null
     * @param objectTypes set of object type names (e.g., {"order", "item"}); immutable
     * @param eventTypes set of event type names; immutable
     * @param eventCount total number of events in the log
     * @param objectCount total number of objects in the log
     */
    public record OcelLog(
            String version,
            List<String> objectTypes,
            List<String> eventTypes,
            long eventCount,
            long objectCount
    ) {
        /**
         * Compact constructor for validation.
         */
        public OcelLog {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("version must not be blank");
            }
            if (objectTypes == null || eventTypes == null) {
                throw new IllegalArgumentException("objectTypes and eventTypes must not be null");
            }
            if (eventCount < 0 || objectCount < 0) {
                throw new IllegalArgumentException("counts must be non-negative");
            }
            // Defensive copy to ensure immutability
            objectTypes = Collections.unmodifiableList(objectTypes);
            eventTypes = Collections.unmodifiableList(eventTypes);
        }

        /**
         * Checks if this log is empty (no events).
         *
         * @return true if eventCount is 0
         */
        public boolean isEmpty() {
            return eventCount == 0L;
        }

        /**
         * Gets the number of distinct object types.
         *
         * @return size of objectTypes set
         */
        public int objectTypeCount() {
            return objectTypes.size();
        }

        /**
         * Gets the number of distinct event types.
         *
         * @return size of eventTypes set
         */
        public int eventTypeCount() {
            return eventTypes.size();
        }
    }

    /**
     * A single event in an OCEL2 log.
     *
     * <p><strong>OCEL2 Event Structure</strong>:
     * <pre>{@code
     * {
     *   "ocel:eid": "e123",
     *   "ocel:type": "order_created",
     *   "ocel:timestamp": "2025-01-15T14:30:00Z",
     *   "ocel:omap": [
     *     {"ocel:oid": "o456", "ocel:type": "order"},
     *     {"ocel:oid": "c789", "ocel:type": "customer"}
     *   ],
     *   "ocel:vmap": {
     *     "amount": 1500.0,
     *     "currency": "USD"
     *   }
     * }
     * }</pre>
     * </p>
     *
     * <p><strong>Zero-Copy Promise</strong>: This record holds references to WASM data.
     * The values are not deep-copied; they reference the original WASM memory.</p>
     *
     * @param eventId unique event identifier (ocel:eid); must not be null
     * @param eventType type of event (e.g., "order_created"); must not be null
     * @param timestamp ISO 8601 timestamp; must not be null
     * @param relatedObjects list of (objectId, objectType) pairs; immutable
     * @param attributes key-value pairs (ocel:vmap); immutable
     */
    public record OcelEvent(
            String eventId,
            String eventType,
            Instant timestamp,
            List<OcelObjectReference> relatedObjects,
            Map<String, Object> attributes
    ) {
        public OcelEvent {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("eventId must not be blank");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType must not be blank");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp must not be null");
            }
            relatedObjects = Collections.unmodifiableList(relatedObjects != null ? relatedObjects : List.of());
            attributes = Collections.unmodifiableMap(attributes != null ? attributes : Map.of());
        }

        /**
         * Checks if this event involves a specific object.
         *
         * @param objectId the object ID to search for
         * @return true if any related object has matching ID
         */
        public boolean involvesObject(String objectId) {
            return relatedObjects.stream()
                    .anyMatch(ref -> ref.objectId().equals(objectId));
        }

        /**
         * Gets the number of related objects.
         *
         * @return size of relatedObjects list
         */
        public int objectCount() {
            return relatedObjects.size();
        }
    }

    /**
     * Reference to a business object in an OCEL event.
     *
     * <p><strong>Represents</strong>: A link from an event to one of the objects it involves.</p>
     *
     * @param objectId unique object identifier; must not be null
     * @param objectType type of object (e.g., "order", "item"); must not be null
     */
    public record OcelObjectReference(String objectId, String objectType) {
        public OcelObjectReference {
            if (objectId == null || objectId.isBlank()) {
                throw new IllegalArgumentException("objectId must not be blank");
            }
            if (objectType == null || objectType.isBlank()) {
                throw new IllegalArgumentException("objectType must not be blank");
            }
        }
    }

    /**
     * A business object in an OCEL2 log.
     *
     * <p><strong>OCEL2 Object Structure</strong>:
     * <pre>{@code
     * {
     *   "ocel:oid": "o456",
     *   "ocel:type": "order",
     *   "ocel:ovmap": {
     *     "customer_id": "C123",
     *     "amount": 1500.0,
     *     "status": "pending"
     *   }
     * }
     * }</pre>
     * </p>
     *
     * @param objectId unique object identifier; must not be null
     * @param objectType type of object; must not be null
     * @param attributes key-value pairs (ocel:ovmap); immutable
     */
    public record OcelObject(
            String objectId,
            String objectType,
            Map<String, Object> attributes
    ) {
        public OcelObject {
            if (objectId == null || objectId.isBlank()) {
                throw new IllegalArgumentException("objectId must not be blank");
            }
            if (objectType == null || objectType.isBlank()) {
                throw new IllegalArgumentException("objectType must not be blank");
            }
            attributes = Collections.unmodifiableMap(attributes != null ? attributes : Map.of());
        }

        /**
         * Gets an attribute value by key.
         *
         * @param key attribute key; must not be null
         * @return attribute value, or null if not present
         */
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        /**
         * Safely retrieves a string attribute.
         *
         * @param key attribute key; must not be null
         * @return attribute value as String, or null if not present or not a String
         */
        public String getStringAttribute(String key) {
            Object value = attributes.get(key);
            return value instanceof String ? (String) value : null;
        }

        /**
         * Safely retrieves a numeric attribute.
         *
         * @param key attribute key; must not be null
         * @return attribute value as Number, or null if not present or not a Number
         */
        public Number getNumberAttribute(String key) {
            Object value = attributes.get(key);
            return value instanceof Number ? (Number) value : null;
        }
    }

    // Private constructor to prevent instantiation
    private Rust4pmOcel() {
        throw new UnsupportedOperationException("Rust4pmOcel is a sealed types namespace");
    }
}
