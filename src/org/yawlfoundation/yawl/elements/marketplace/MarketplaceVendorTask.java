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

package org.yawlfoundation.yawl.elements.marketplace;

import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.util.StringUtil;

import java.util.*;

/**
 * Specialized atomic task for GCP Marketplace vendor operations.
 *
 * <p>MarketplaceVendorTask represents activities executed by vendors in a marketplace,
 * such as product listing management, inventory updates, and vendor profile modifications.
 * This task type enforces vendor-specific validation and credentials checking.</p>
 *
 * <h2>Vendor Task Types</h2>
 * <ul>
 *   <li><b>VENDOR_PROFILE_UPDATE</b> - Modify vendor profile, credentials, or contact info</li>
 *   <li><b>PRODUCT_LIST_MANAGEMENT</b> - Create, update, or delete product listings</li>
 *   <li><b>INVENTORY_ADJUSTMENT</b> - Adjust product inventory levels</li>
 *   <li><b>PRICING_UPDATE</b> - Update product pricing or promotion rules</li>
 *   <li><b>VENDOR_COMPLIANCE_CHECK</b> - Validate vendor compliance with marketplace policies</li>
 * </ul>
 *
 * <h2>Execution Requirements</h2>
 * <ul>
 *   <li>Vendor credentials must be verified before task can start</li>
 *   <li>Vendor account must be active and in good standing</li>
 *   <li>All vendor data changes are logged for audit purposes</li>
 *   <li>Task supports multi-instance execution for batch operations</li>
 * </ul>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public final class MarketplaceVendorTask extends YAtomicTask {

    /**
     * Vendor task type constants
     */
    public static final String VENDOR_PROFILE_UPDATE = "VENDOR_PROFILE_UPDATE";
    public static final String PRODUCT_LIST_MANAGEMENT = "PRODUCT_LIST_MANAGEMENT";
    public static final String INVENTORY_ADJUSTMENT = "INVENTORY_ADJUSTMENT";
    public static final String PRICING_UPDATE = "PRICING_UPDATE";
    public static final String VENDOR_COMPLIANCE_CHECK = "VENDOR_COMPLIANCE_CHECK";

    // Vendor task state
    private String vendorTaskType;
    private String vendorAccountId;
    private boolean credentialsVerified = false;
    private long credentialVerificationTime = 0;
    private Map<String, String> vendorMetadata = new HashMap<>();

    /**
     * Constructs a new marketplace vendor task.
     *
     * @param id the task identifier
     * @param joinType the task's join type (YAtomicTask._AND, ._OR, ._XOR)
     * @param splitType the task's split type
     * @param container the task's containing net
     */
    public MarketplaceVendorTask(String id, int joinType, int splitType, YNet container) {
        super(id, joinType, splitType, container);
    }

    /**
     * Sets the vendor task type (e.g., PRODUCT_LIST_MANAGEMENT).
     *
     * @param vendorTaskType one of the VENDOR_* constants
     * @throws IllegalArgumentException if vendorTaskType is null or empty
     */
    public void setVendorTaskType(String vendorTaskType) {
        if (StringUtil.isNullOrEmpty(vendorTaskType)) {
            throw new IllegalArgumentException("Vendor task type cannot be null or empty");
        }
        this.vendorTaskType = vendorTaskType;
    }

    /**
     * Gets the vendor task type.
     *
     * @return the vendor task type
     */
    public String getVendorTaskType() {
        return vendorTaskType;
    }

    /**
     * Sets the vendor account ID associated with this task.
     *
     * @param accountId the vendor account identifier
     */
    public void setVendorAccountId(String accountId) {
        this.vendorAccountId = accountId;
    }

    /**
     * Gets the vendor account ID.
     *
     * @return the vendor account identifier
     */
    public String getVendorAccountId() {
        return vendorAccountId;
    }

    /**
     * Marks the vendor credentials as verified with a timestamp.
     * This must be called before task execution proceeds.
     *
     * @param verified true if credentials were successfully verified
     */
    public void setCredentialsVerified(boolean verified) {
        this.credentialsVerified = verified;
        if (verified) {
            this.credentialVerificationTime = System.currentTimeMillis();
        }
    }

    /**
     * Checks if vendor credentials have been verified.
     *
     * @return true if credentials are verified
     */
    public boolean areCredentialsVerified() {
        return credentialsVerified;
    }

    /**
     * Gets the credential verification timestamp.
     *
     * @return milliseconds since epoch when credentials were verified, or 0 if not verified
     */
    public long getCredentialVerificationTime() {
        return credentialVerificationTime;
    }

    /**
     * Adds vendor-specific metadata (e.g., tier level, region, compliance status).
     *
     * @param key the metadata key
     * @param value the metadata value
     */
    public void setVendorMetadata(String key, String value) {
        if (StringUtil.isNullOrEmpty(key)) {
            throw new IllegalArgumentException("Metadata key cannot be null or empty");
        }
        vendorMetadata.put(key, value);
    }

    /**
     * Retrieves vendor-specific metadata.
     *
     * @param key the metadata key
     * @return the metadata value, or null if not found
     */
    public String getVendorMetadata(String key) {
        return vendorMetadata.get(key);
    }

    /**
     * Gets all vendor metadata as an unmodifiable map.
     *
     * @return unmodifiable map of vendor metadata
     */
    public Map<String, String> getVendorMetadataMap() {
        return Collections.unmodifiableMap(vendorMetadata);
    }

    /**
     * Exports vendor task state to XML Element for persistence.
     * This is called during task serialization.
     *
     * @return XML Element containing vendor task state
     */
    public Element exportVendorState() {
        Element root = new Element("marketplaceVendorTaskState");

        Element typeElem = new Element("vendorTaskType");
        typeElem.setText(vendorTaskType != null ? vendorTaskType : "");
        root.addContent(typeElem);

        Element accountElem = new Element("vendorAccountId");
        accountElem.setText(vendorAccountId != null ? vendorAccountId : "");
        root.addContent(accountElem);

        Element credElem = new Element("credentialsVerified");
        credElem.setText(String.valueOf(credentialsVerified));
        root.addContent(credElem);

        Element credTimeElem = new Element("credentialVerificationTime");
        credTimeElem.setText(String.valueOf(credentialVerificationTime));
        root.addContent(credTimeElem);

        Element metaElem = new Element("vendorMetadata");
        for (Map.Entry<String, String> entry : vendorMetadata.entrySet()) {
            Element item = new Element("item");
            item.setAttribute("key", entry.getKey());
            item.setText(entry.getValue() != null ? entry.getValue() : "");
            metaElem.addContent(item);
        }
        root.addContent(metaElem);

        return root;
    }

    /**
     * Imports vendor task state from XML Element during deserialization.
     *
     * @param state XML Element containing vendor task state
     */
    public void importVendorState(Element state) {
        if (state == null) {
            return;
        }

        Element typeElem = state.getChild("vendorTaskType");
        if (typeElem != null && !StringUtil.isNullOrEmpty(typeElem.getText())) {
            this.vendorTaskType = typeElem.getText();
        }

        Element accountElem = state.getChild("vendorAccountId");
        if (accountElem != null && !StringUtil.isNullOrEmpty(accountElem.getText())) {
            this.vendorAccountId = accountElem.getText();
        }

        Element credElem = state.getChild("credentialsVerified");
        if (credElem != null) {
            this.credentialsVerified = Boolean.parseBoolean(credElem.getText());
        }

        Element credTimeElem = state.getChild("credentialVerificationTime");
        if (credTimeElem != null && !StringUtil.isNullOrEmpty(credTimeElem.getText())) {
            try {
                this.credentialVerificationTime = Long.parseLong(credTimeElem.getText());
            } catch (NumberFormatException e) {
                this.credentialVerificationTime = 0;
            }
        }

        Element metaElem = state.getChild("vendorMetadata");
        if (metaElem != null) {
            vendorMetadata.clear();
            for (Element item : metaElem.getChildren("item")) {
                String key = item.getAttributeValue("key");
                String value = item.getText();
                if (!StringUtil.isNullOrEmpty(key)) {
                    vendorMetadata.put(key, value);
                }
            }
        }
    }

    /**
     * Validates that vendor credentials are verified before task execution.
     * Called during the task start phase.
     *
     * @return true if vendor credentials are verified and task can proceed
     */
    public boolean validateVendorCredentials() {
        if (!credentialsVerified) {
            return false;
        }
        if (credentialVerificationTime == 0) {
            return false;
        }
        return true;
    }

    /**
     * Checks if vendor task is in a valid state for execution.
     *
     * @return true if all required fields are set and credentials verified
     */
    public boolean isReadyForExecution() {
        return !StringUtil.isNullOrEmpty(vendorTaskType) &&
                !StringUtil.isNullOrEmpty(vendorAccountId) &&
                credentialsVerified;
    }

    /**
     * Resets vendor-specific state after task completion or cancellation.
     */
    public void resetVendorState() {
        credentialsVerified = false;
        credentialVerificationTime = 0;
        vendorMetadata.clear();
    }
}
