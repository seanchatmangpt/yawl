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

package org.yawlfoundation.yawl.elements.patterns;

import org.jdom2.Document;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.util.SaxonUtil;

import net.sf.saxon.s9api.SaxonApiException;

/**
 * Implements the Milestone pattern condition (WCP-18).
 *
 * <p>A milestone condition represents a state that, when reached,
 * enables dependent tasks to execute. Milestones can expire based
 * on time or data changes.</p>
 *
 * <p>State Machine:</p>
 * <ul>
 *   <li>NOT_REACHED: Milestone condition not satisfied</li>
 *   <li>REACHED: Milestone condition satisfied, dependent tasks may execute</li>
 *   <li>EXPIRED: Milestone was reached but has been invalidated</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YMilestoneCondition extends YCondition {

    /** Whether the milestone is currently reached */
    private boolean _isReached;

    /** XQuery/XPath expression to evaluate milestone state */
    private String _milestoneExpression;

    /** Timestamp when milestone was reached */
    private long _reachedTimestamp;

    /** Expiry timeout in milliseconds (0 = no expiry) */
    private long _expiryTimeout;

    /**
     * Constructs a new milestone condition.
     *
     * @param id the condition identifier
     * @param container the containing net
     */
    public YMilestoneCondition(String id, YNet container) {
        super(id, container);
        this._isReached = false;
        this._reachedTimestamp = 0;
        this._expiryTimeout = 0;
    }

    /**
     * Constructs a new milestone condition with label.
     *
     * @param id the condition identifier
     * @param label the display label
     * @param container the containing net
     */
    public YMilestoneCondition(String id, String label, YNet container) {
        super(id, label, container);
        this._isReached = false;
        this._reachedTimestamp = 0;
        this._expiryTimeout = 0;
    }

    /**
     * Checks if the milestone is currently reached.
     *
     * @return true if reached and not expired
     */
    public boolean isReached() {
        if (!_isReached) {
            return false;
        }
        // Check expiry
        if (_expiryTimeout > 0) {
            long elapsed = System.currentTimeMillis() - _reachedTimestamp;
            return elapsed < _expiryTimeout;
        }
        return true;
    }

    /**
     * Sets the reached state.
     *
     * @param reached the reached state
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     */
    public void setReached(boolean reached, YPersistenceManager pmgr)
            throws YPersistenceException {
        if (reached && !_isReached) {
            _reachedTimestamp = System.currentTimeMillis();
        }
        this._isReached = reached;
        if (pmgr != null) {
            pmgr.updateObjectFromExternal(this);
        }
    }

    /**
     * Evaluates the milestone expression and sets reached state.
     *
     * @param dataDoc the net's data document
     * @param pmgr the persistence manager
     * @throws YPersistenceException if persistence fails
     * @throws YQueryException if query evaluation fails
     */
    public void evaluateAndSetReached(Document dataDoc, YPersistenceManager pmgr)
            throws YPersistenceException, YQueryException {
        if (_milestoneExpression == null || _milestoneExpression.isEmpty()) {
            return;
        }

        try {
            // Evaluate expression against net data
            boolean result = evaluateExpression(_milestoneExpression, dataDoc);
            setReached(result, pmgr);
        } catch (SaxonApiException e) {
            throw new YQueryException("Failed to evaluate milestone expression: " +
                                     _milestoneExpression, e);
        }
    }

    /**
     * Gets the milestone expression.
     *
     * @return the XQuery/XPath expression
     */
    public String getMilestoneExpression() {
        return _milestoneExpression;
    }

    /**
     * Sets the milestone expression.
     *
     * @param xquery the XQuery/XPath expression
     */
    public void setMilestoneExpression(String xquery) {
        this._milestoneExpression = xquery;
    }

    /**
     * Gets the expiry timeout.
     *
     * @return the timeout in milliseconds, 0 = no expiry
     */
    public long getExpiryTimeout() {
        return _expiryTimeout;
    }

    /**
     * Sets the expiry timeout.
     *
     * @param timeoutMs the timeout in milliseconds
     */
    public void setExpiryTimeout(long timeoutMs) {
        this._expiryTimeout = timeoutMs;
    }

    /**
     * Gets the timestamp when milestone was reached.
     *
     * @return the timestamp in milliseconds, 0 if not reached
     */
    public long getReachedTimestamp() {
        return _reachedTimestamp;
    }

    /**
     * Checks if the milestone is expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        if (!_isReached || _expiryTimeout <= 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - _reachedTimestamp;
        return elapsed >= _expiryTimeout;
    }

    /**
     * Gets the time since the milestone was reached.
     *
     * @return the time in milliseconds, -1 if not reached
     */
    public long getTimeSinceReached() {
        if (_reachedTimestamp == 0) {
            return -1;
        }
        return System.currentTimeMillis() - _reachedTimestamp;
    }

    /**
     * Evaluates the milestone expression.
     *
     * @param expression the XQuery/XPath expression
     * @param dataDoc the data document to evaluate against
     * @return the boolean result
     * @throws YQueryException if evaluation fails or dataDoc is null
     * @throws SaxonApiException if Saxon processing fails
     */
    private boolean evaluateExpression(String expression, Document dataDoc)
            throws YQueryException, SaxonApiException {
        if (dataDoc == null) {
            throw new YQueryException(
                "Cannot evaluate milestone expression: net data document is required " +
                "to evaluate expression '" + expression + "'");
        }
        try {
            String result = SaxonUtil.evaluateQuery(expression, dataDoc);
            // Convert result to boolean - explicitly evaluate null and empty as false
            if (result == null || result.isEmpty()) {
                return false;
            }
            // Parse boolean value from query result
            String trimmed = result.toLowerCase().trim();
            return "true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed);
        } catch (SaxonApiException e) {
            throw new YQueryException("Failed to evaluate milestone expression: " +
                                     expression, e);
        } catch (Exception e) {
            throw new YQueryException("Unexpected error evaluating milestone expression: " +
                                     expression, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toXML() {
        StringBuilder xml = new StringBuilder();
        xml.append("<milestone id=\"").append(getID()).append("\">");
        xml.append(super.toXML());
        if (_milestoneExpression != null) {
            xml.append("<expression>").append(_milestoneExpression).append("</expression>");
        }
        if (_expiryTimeout > 0) {
            xml.append("<expiryTimeout>").append(_expiryTimeout).append("</expiryTimeout>");
        }
        xml.append("</milestone>");
        return xml.toString();
    }
}
