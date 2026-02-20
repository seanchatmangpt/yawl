/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.interfce;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.jdom2.Element;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * A 'stringified' record of a workitem for passing across various HTTP interfaces.
 *
 * This class has been refactored to use Java 25 records for immutable components
 * while maintaining Hibernate persistence and backward compatibility.
 *
 * @author Lachlan Aldred
 * Date: 2/02/2004
 * Time: 18:30:18
 *
 * Extended & refactored for version 2.0 by Michael Adams
 * Last Date: 27/05/2008
 *
 * Refactored for Java 25 records - version 5.2
 * Last Date: 16/02/2026
 */

public class WorkItemRecord implements Cloneable {

    // workitem execution statuses
    public static final String statusEnabled = "Enabled";
    public static final String statusFired = "Fired";
    public static final String statusExecuting = "Executing";
    public static final String statusComplete = "Complete";
    public static final String statusIsParent = "Is parent";
    public static final String statusDeadlocked = "Deadlocked";
    public static final String statusForcedComplete = "ForcedComplete";
    public static final String statusFailed = "Failed";
    public static final String statusSuspended = "Suspended";
    public static final String statusDiscarded = "Discarded";

    // workitem resourcing statuses
    public static final String statusResourceOffered = "Offered" ;
    public static final String statusResourceAllocated = "Allocated" ;
    public static final String statusResourceStarted = "Started" ;
    public static final String statusResourceSuspended = "Suspended" ;
    public static final String statusResourceUnoffered = "Unoffered" ;
    public static final String statusResourceUnresourced = "Unresourced" ;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd yyyy H:mm:ss").withZone(ZoneId.systemDefault());

    // hibernate primary key
    private long _id;

    // RECORD COMPONENTS - Immutable identity, timing, and metadata
    // Note: These are lazily initialized and cached. The individual fields below
    // are maintained for Hibernate persistence compatibility.
    private transient WorkItemIdentity identity;
    private transient WorkItemTiming timing;
    private transient WorkItemMetadata metadata;

    // IDENTITY FIELDS - Persisted by Hibernate, wrapped by WorkItemIdentity record
    private String _specIdentifier;
    private String _specVersion = "0.1" ;
    private String _specURI;
    private String _caseID;
    private String _taskID;
    private String _uniqueID;                            // used by PDF Forms service

    // METADATA FIELDS - Persisted by Hibernate, wrapped by WorkItemMetadata record
    private String _taskName;                            // the unmodified task name
    private String _documentation;
    private String _allowsDynamicCreation;
    private String _requiresManualResourcing;
    private String _codelet;
    private Map<String, String> _attributeTable;         // task/decomp level attribs
    private String _deferredChoiceGroupID = null;        // deferred choice group member
    private String _customFormURL;                       // path to alternate jsp
    private String _logPredicateStarted;                 // configurable logging predicates
    private String _logPredicateCompletion;

    // TIMING FIELDS - Persisted by Hibernate, wrapped by WorkItemTiming record
    private String _enablementTimeMs;
    private String _firingTimeMs;
    private String _startTimeMs ;
    private String _completionTimeMs ;
    private String _timerTrigger;                        // timer values (if item has a timer enabled)
    private String _timerExpiry;

    // MUTABLE STATE - Current execution status
    private String _status;
    private String _resourceStatus = statusResourceUnresourced;

    // MUTABLE STATE - Resource assignment
    private String _startedBy;                           // who performed the workitem
    private String _completedBy;

    // MUTABLE STATE - Data elements
    private Element _dataList;                           // initial data params and values
    private Element _dataListUpdated;                    // interim data store for custom services

    // MUTABLE STATE - Flags and tags
    private boolean _docoChanged = false;                // documentation updated?
    private String _tag;                                 // for user-defined values

    /********************************************************************************/

    // CONSTRUCTORS //

    public WorkItemRecord() {}                     // for reflection

    // called by Marshaller.unmarshallWorkItem
    public WorkItemRecord(String caseID, String taskID, String specURI, String status) {
        _taskID = taskID;
        _caseID = caseID;
        _specURI = specURI;
        _status = status;
    }


    public void resetDataState() {
        _dataListUpdated = null ;
    }

    /********************************************************************************/

    // RECORD COMPONENT ACCESSORS //

    /**
     * Returns the immutable identity record for this work item.
     * The record is constructed on-demand and cached.
     */
    public WorkItemIdentity identity() {
        if (identity == null) {
            identity = new WorkItemIdentity(_specIdentifier, _specVersion, _specURI,
                                           _caseID, _taskID, _uniqueID);
        }
        return identity;
    }

    /**
     * Returns the immutable timing record for this work item.
     * The record is constructed on-demand and cached.
     */
    public WorkItemTiming timing() {
        if (timing == null) {
            timing = new WorkItemTiming(_enablementTimeMs, _firingTimeMs, _startTimeMs,
                                       _completionTimeMs, _timerTrigger, _timerExpiry);
        }
        return timing;
    }

    /**
     * Returns the immutable metadata record for this work item.
     * The record is constructed on-demand and cached.
     */
    public WorkItemMetadata metadata() {
        if (metadata == null) {
            metadata = new WorkItemMetadata(_attributeTable, _taskName, _documentation, _allowsDynamicCreation,
                                           _requiresManualResourcing, _codelet, _deferredChoiceGroupID,
                                           _customFormURL, _logPredicateStarted,
                                           _logPredicateCompletion);
        }
        return metadata;
    }

    /**
     * Invalidates cached record components.
     * Called internally when fields are modified to ensure records are reconstructed.
     */
    private void invalidateRecordCache() {
        identity = null;
        timing = null;
        metadata = null;
    }

    /********************************************************************************/

    // SETTERS //

    public void setSpecIdentifier(String id) {
        _specIdentifier = id;
        identity = null;  // invalidate cached identity
    }

    public void setSpecVersion(String version) {
        _specVersion = version;
        identity = null;
    }

    public void setSpecURI(String uri) {
        _specURI = uri;
        identity = null;
    }

    public void setCaseID(String caseID) {
        _caseID = caseID;
        identity = null;
    }

    public void setTaskID(String taskID) {
        _taskID = taskID;
        identity = null;
    }

    public void setUniqueID(String uniqueID) {
        _uniqueID = uniqueID;
        identity = null;
    }

    public void setTaskName(String name) {
        _taskName = name;
        metadata = null;  // invalidate cached metadata
    }

    public void setAllowsDynamicCreation(String allows) {
        _allowsDynamicCreation = allows;
        metadata = null;
    }

    public void setRequiresManualResourcing(String manual) {
        _requiresManualResourcing = manual;
        metadata = null;
    }

    public void setCodelet(String codelet) {
        _codelet = codelet;
        metadata = null;
    }

    public void setDeferredChoiceGroupID(String id) {
        _deferredChoiceGroupID = id;
        metadata = null;
    }

    public void setExtendedAttributes(Map<String, String> attrMap) {
        _attributeTable = attrMap;
        metadata = null;
    }


    public void setEnablementTimeMs(String time) {
        _enablementTimeMs = time;
        timing = null;  // invalidate cached timing
    }

    public void setFiringTimeMs(String time) {
        _firingTimeMs = time;
        timing = null;
    }

    public void setStartTimeMs(String time) {
        _startTimeMs = time;
        timing = null;
    }

    public void setCompletionTimeMs(String time) {
        _completionTimeMs = time;
        timing = null;
    }

    public void setTimerTrigger(String trigger) {
        _timerTrigger = trigger;
        timing = null;
    }

    public void setTimerExpiry(String expiry) {
        _timerExpiry = expiry;
        timing = null;
    }
    

    public void setStatus(String status) {_status = status; }
    
    public void setResourceStatus(String status) {_resourceStatus = status; }

    /** @deprecated */
    public void setAssignedTo(String whoStartedMe) { setStartedBy(whoStartedMe) ; }

    public void setStartedBy(String resource) { _startedBy = resource; }

    public void setCompletedBy(String resource) { _completedBy = resource; }


    public void setDataList(Element dataList) { _dataList = dataList; }

    public void setTag(String tag) { _tag = tag ; }

    public void setUpdatedData(Element dataListUpdated) {
        _dataListUpdated = dataListUpdated;
    }

    public void setCustomFormURL(String url) {
        _customFormURL = url;
        metadata = null;
    }

    public void setLogPredicateStarted(String predicate) {
        _logPredicateStarted = predicate;
        metadata = null;
    }

    public void setLogPredicateCompletion(String predicate) {
        _logPredicateCompletion = predicate;
        metadata = null;
    }

    public void setDocumentation(String doco) {
        _documentation = doco;
        metadata = null;
    }

    public void setDocumentationChanged(boolean added) {
        _docoChanged = added;
    }

    /********************************************************************************/

    // GETTERS //

    public String getSpecIdentifier() { return _specIdentifier; }

    public String getSpecVersion() { return _specVersion ; }

    public String getSpecURI() { return _specURI; }

    public String getCaseID() { return _caseID; }

    public String getTaskID() { return _taskID; }

    public String getUniqueID() { return _uniqueID; }

    public String getAllowsDynamicCreation() { return _allowsDynamicCreation ; }

    public boolean isDynamicCreationAllowed() {
        return metadata().isDynamicCreationAllowed();
    }

    public String getDeferredChoiceGroupID() { return _deferredChoiceGroupID ; }

    public String getRequiresManualResourcing() { return _requiresManualResourcing; }

    public boolean isManualResourcingRequired() { return metadata().isManualResourcingRequired(); }

    public String getCodelet() { return _codelet; }

    public Map<String, String> getAttributeTable() { return _attributeTable; }

    public String getID() { return identity().getID(); }

    public String getEnablementTime() { return timing().getEnablementTime(); }

    public String getFiringTime() { return timing().getFiringTime(); }

    public String getStartTime() { return timing().getStartTime(); }

    public String getCompletionTime() { return timing().getCompletionTime(); }

    public String getEnablementTimeMs() { return _enablementTimeMs; }

    public String getFiringTimeMs() { return _firingTimeMs; }

    public String getStartTimeMs() { return _startTimeMs; }

    public String getCompletionTimeMs() { return _completionTimeMs; }

    public String getTimerTrigger() { return _timerTrigger; }

    public String getTimerExpiry() { return _timerExpiry; }

    public String getStatus() { return _status; }

    public String getResourceStatus() { return _resourceStatus; }


    /** @deprecated - use getStartedBy() */
    public String getAssignedTo() { return getStartedBy(); }

    /** @deprecated - use getStartedBy() */
    public String getWhoStartedMe() { return getStartedBy(); }

    public String getStartedBy() { return _startedBy; }

    public String getCompletedBy() { return _completedBy; }


    /** @deprecated - use getDataList() */
    public Element getWorkItemData() { return getDataList(); }

    public Element getDataList() { return _dataList; }

    public String getDataListString() { return get_dataList(); }


    public String getTag() { return _tag ; }

    public Element getUpdatedData() { return _dataListUpdated; }

    public String getIDForDisplay() {
        return _caseID + ":" + _taskName ;
    }

    public String getTaskName() { return _taskName; }

    public String getDocumentation() { return _documentation; }

    public boolean hasDocumentation() { return metadata().hasDocumentation(); }

    public boolean isDocumentationChanged() { return _docoChanged; }

    /**
     * Returns the case id of the root ancestor case.
     */
    public String getRootCaseID() {
        return identity().getRootCaseID();
    }

    public String getNetID() {
        return identity().getNetID(_status);
    }

    public String getParentID() {
        return identity().getParentID(isEnabledOrFired());
    }

    public String getLogPredicateStarted() { return _logPredicateStarted; }

    public String getLogPredicateCompletion() { return _logPredicateCompletion; }


    public boolean isEdited() { return (_dataListUpdated != null); }

    public boolean isDeferredChoiceGroupMember() {
        return metadata().isDeferredChoiceGroupMember();
    }

    public boolean isAutoTask() {
        return metadata().isAutoTask();
    }


    public String getCustomFormURL() { return _customFormURL; }

    public boolean hasLiveStatus() {
        return isEnabledOrFired() || _status.equals(statusExecuting);
    }

    public boolean isEnabledOrFired() {
        return _status.equals(statusEnabled) || _status.equals(statusFired);
    }

    public boolean hasStatus(String status) {
        return (_status != null) && _status.equals(status);
    }

    public boolean hasResourceStatus(String status) {
        return (_resourceStatus != null) && _resourceStatus.equals(status);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof WorkItemRecord other) {
            return getID().equals(other.getID()) &&
                 _status.equals(other.getStatus()) &&
                 _uniqueID != null ? _uniqueID.equals(other._uniqueID) :
                    other._uniqueID == null;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = _caseID.hashCode();
        result = 31 * result + _taskID.hashCode();
        result = 31 * result + _status.hashCode();
        result = 31 * result + (_uniqueID != null ? _uniqueID.hashCode() : 0);
        return result;
    }

    /********************************************************************************/

    public String toString() {
        return getID();
    }


    public String toXML() {
        StringBuilder xml = new StringBuilder("<workItemRecord");
        xml.append(attributeTableToString())
           .append(">")
           .append(StringUtil.wrap(getID(), "id"))
           .append(StringUtil.wrap(_specVersion, "specversion"))
           .append(StringUtil.wrap(_specURI, "specuri"))
           .append(StringUtil.wrap(_caseID, "caseid"))
           .append(StringUtil.wrap(_taskID, "taskid"))
           .append(StringUtil.wrap(_uniqueID, "uniqueid"))
           .append(StringUtil.wrapEscaped(_taskName, "taskname"));

        if (_documentation != null) {
            xml.append(StringUtil.wrapEscaped(_documentation, "documentation"));
        }

        xml.append(StringUtil.wrap(_allowsDynamicCreation, "allowsdynamiccreation"))
           .append(StringUtil.wrap(_requiresManualResourcing, "requiresmanualresourcing"))
           .append(StringUtil.wrap(_codelet, "codelet"))
           .append(StringUtil.wrap(_deferredChoiceGroupID, "deferredChoiceGroupid"))
           .append(StringUtil.wrap(_enablementTimeMs, "enablementTimeMs"))
           .append(StringUtil.wrap(_firingTimeMs, "firingTimeMs"))
           .append(StringUtil.wrap(_startTimeMs, "startTimeMs"))
           .append(StringUtil.wrap(_completionTimeMs, "completionTimeMs"))
           .append(StringUtil.wrap(_timerTrigger, "timertrigger"))
           .append(StringUtil.wrap(_timerExpiry, "timerexpiry"))
           .append(StringUtil.wrap(_status, "status"))
           .append(StringUtil.wrap(_resourceStatus, "resourceStatus"))
           .append(StringUtil.wrap(_startedBy, "startedBy"))
           .append(StringUtil.wrap(_completedBy, "completedBy"))
           .append(StringUtil.wrap(_tag, "tag"))
           .append(StringUtil.wrap(_customFormURL, "customform"))
           .append(StringUtil.wrapEscaped(_logPredicateStarted, "logPredicateStarted"))
           .append(StringUtil.wrapEscaped(_logPredicateCompletion, "logPredicateCompletion"));

        if (_specIdentifier != null) {
            xml.append(StringUtil.wrap(_specIdentifier, "specidentifier"));
        }
        
        xml.append("<data>")
           .append(_dataList != null? JDOMUtil.elementToStringDump(_dataList) : "")
           .append("</data>")
           .append("<updateddata>")
           .append(_dataListUpdated != null? JDOMUtil.elementToStringDump(_dataListUpdated) : "")
           .append("</updateddata>")
           .append("</workItemRecord>");

        return xml.toString() ;
    }


    /**************************************************************************/


    private String attributeTableToString() {
        if (_attributeTable == null || _attributeTable.isEmpty()) {
            return new String();  // Return empty but valid XML attribute string
        }

        StringBuilder xml = new StringBuilder();
        for (String key : _attributeTable.keySet()) {
            xml.append(" ")
               .append(key)
               .append("=\"")
               .append(JDOMUtil.encodeAttributeEscapes(_attributeTable.get(key)))
               .append("\"");
        }
        return xml.toString();
    }


    /**
     * Formats a date for display. Delegates to WorkItemTiming record.
     * @deprecated Use timing().getEnablementTime() etc. instead
     */
    @Deprecated
    private String getFormattedDate(String msStr) {
        if (StringUtil.isNullOrEmpty(msStr)) {
            return null;
        }
        long ms = StringUtil.strToLong(msStr, 0);
        if (ms <= 0) {
            return null;
        }
        return DATE_FORMAT.format(Instant.ofEpochMilli(ms));
    }

    /*******************************************************************************/

    public WorkItemRecord clone() throws CloneNotSupportedException {
        return (WorkItemRecord) super.clone() ;
    }

 
    /********************************************************************************/

    // hibernate mappings

    private void set_id(long id) { _id = id; }

    private long get_id() { return _id; }


    private void set_dataList(String data) {
        if (data != null) {
            _dataList = JDOMUtil.stringToElement(data);
        }
    }

    private String get_dataList() {
        return _dataList != null ? JDOMUtil.elementToStringDump(_dataList) : null ;
    }


    private void set_dataListUpdated(String data) {
        if (data != null) {
            _dataListUpdated = JDOMUtil.stringToElement(data);
        }
    }

    private String get_dataListUpdated() {
        return _dataListUpdated != null ?
                JDOMUtil.elementToStringDump(_dataListUpdated) : null ;
    }


    private void set_extendedAttributes(String attrStr) {
        _attributeTable = new HashMap<String, String>();

        // split into key, value, key, value, ...
        String[] attributes = attrStr.split("\\s*=\\s*\"|\\s*\"\\s*");
        for (int i=0; i < attributes.length - 1; i=i+2) {
            _attributeTable.put(attributes[i].trim(),
                    JDOMUtil.decodeAttributeEscapes(attributes[i+1].trim()));
        }
    }

    private String get_extendedAttributes() { return attributeTableToString(); }

    /********************************************************************************/

}
