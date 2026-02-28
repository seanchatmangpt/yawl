package org.yawlfoundation.yawl.rust4pm.model;

/**
 * An OCEL2 object with type and identifier.
 *
 * @param objectId   unique object identifier
 * @param objectType object type name (e.g., "Order", "Item")
 */
public record OcelObject(String objectId, String objectType) {}
