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

package org.yawlfoundation.yawl.elements;

import static org.yawlfoundation.yawl.engine.YWorkItemStatus.statusEnabled;
import static org.yawlfoundation.yawl.engine.YWorkItemStatus.statusExecuting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import jakarta.xml.bind.DatatypeConverter;

import javax.xml.datatype.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.time.YTimer;
import org.yawlfoundation.yawl.engine.time.YWorkItemTimer;
import org.yawlfoundation.yawl.engine.time.workdays.WorkDayAdjuster;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

/**
 * @author Michael Adams
 * @date 30/07/12
 */
public class YTimerParameters {

    private static final Logger _log = LogManager.getLogger(YTimerParameters.class);

    // the ways in which timer parameters may be expressed
    public enum TimerType { Duration, Expiry, Interval, LateBound, Nil }

    // the trigger conditions for a timer - when the timer activates relative to work item state
    public enum TriggerType { OnEnabled, OnExecuting }

    private String _variableName;                         // late bound net variable
    private Instant _expiryTime;                          // date param
    private Duration _duration;                           // duration param
    private long _ticks;                                  // interval params
    private YTimer.TimeUnit _timeUnit;                    // ditto
    private boolean _workDaysOnly;                        // ignore non-work days
    private YWorkItemTimer.Trigger _trigger;
    private TimerType _timerType;


    public YTimerParameters() { _timerType = TimerType.Nil; }

    public YTimerParameters(String netParamName) { set(netParamName); }

    public YTimerParameters(YWorkItemTimer.Trigger trigger, Instant expiryTime) {
        set(trigger, expiryTime);
    }

    public YTimerParameters(YWorkItemTimer.Trigger trigger, Duration duration) {
        set(trigger, duration);
    }

    public YTimerParameters(YWorkItemTimer.Trigger trigger, long ticks,
                                       YTimer.TimeUnit timeUnit) {
        set(trigger, ticks, timeUnit);
    }


    /**
     * sets
     * @param netParamName
     */
    public void set(String netParamName) {
        _variableName = netParamName;
        _timerType = TimerType.LateBound;
    }


    public void set(YWorkItemTimer.Trigger trigger, Instant expiryTime) {
        _trigger = trigger;
        _expiryTime = expiryTime;
        _timerType = TimerType.Expiry;
    }


    public void set(YWorkItemTimer.Trigger trigger, Duration duration) {
        _trigger = trigger;
        _duration = duration;
        _timerType = TimerType.Duration;
    }


    public void set(YWorkItemTimer.Trigger trigger, long ticks,
                                   YTimer.TimeUnit timeUnit) {
        _trigger = trigger;
        _ticks = ticks;
        _timeUnit = timeUnit != null ? timeUnit : YTimer.TimeUnit.MSEC;
        _timerType = TimerType.Interval;
    }


    public boolean triggerMatchesStatus(YWorkItemStatus status) {
        if (_timerType == TimerType.Nil) return false;
        return switch (_trigger) {
            case OnEnabled -> status.equals(statusEnabled);
            case OnExecuting -> status.equals(statusExecuting);
            default -> false;
        };
    }


    public String getVariableName() { return _variableName; }

    public void setVariableName(String varName) {
        _variableName = varName;
        _timerType = TimerType.LateBound;
    }


    public Instant getDate() { return _expiryTime; }

    public void setDate(Instant date) {
        _expiryTime = date;
        _timerType = TimerType.Expiry;
    }


    public Duration getWorkDayDuration() {
        return _workDaysOnly ? new WorkDayAdjuster().adjust(_duration) : _duration;
    }

    public Duration getDuration() { return _duration; }

    public void setDuration(Duration duration) {
        _duration = duration;
        _timerType = TimerType.Duration;
    }


    public long getTicks() { return _ticks; }

    public void setTicks(long ticks) {
        _ticks = ticks;
        _timerType = TimerType.Interval;
    }


    public YTimer.TimeUnit getTimeUnit() { return _timeUnit; }

    public void setTimeUnit(YTimer.TimeUnit timeUnit) {
        _timeUnit = timeUnit;
        _timerType = TimerType.Interval;
    }


    public YWorkItemTimer.Trigger getTrigger() { return _trigger; }

    public void setTrigger(YWorkItemTimer.Trigger trigger) { this._trigger = trigger; }


    public boolean isWorkDaysOnly() { return _workDaysOnly; }

    public void setWorkDaysOnly(boolean workDaysOnly) { _workDaysOnly = workDaysOnly; }


    public TimerType getTimerType() { return _timerType; }


    public boolean parseYTimerType(Element eTimerTypeValue) throws IllegalArgumentException {
        XNode node = new XNodeParser(true).parse(eTimerTypeValue);
        if (node == null) throw new IllegalArgumentException("Invalid YTimerType XML");

        String triggerStr = node.getChildText("trigger");
        if (triggerStr == null) throw new IllegalArgumentException("Missing 'trigger' parameter");

        // throws IllegalArgumentException if triggerStr is not a valid Trigger
        YWorkItemTimer.Trigger trigger = YWorkItemTimer.Trigger.valueOf(triggerStr);

        String expiry = node.getChildText("expiry");
        if (expiry == null) throw new IllegalArgumentException("Missing 'expiry' parameter");

        setWorkDaysOnly(node.getChild("workdays") != null);

        if (expiry.startsWith("P")) {         // duration types start with P
            Duration duration = StringUtil.strToDuration(expiry);
            if (duration == null) throw new IllegalArgumentException("Malformed duration value");
            set(trigger, duration);
            return true;
        }

        try {                                 // test for xsd datetime
            Calendar calendar = DatatypeConverter.parseDateTime(expiry);
            set(trigger, calendar.getTime().toInstant());
            return true;
        }
        catch (IllegalArgumentException pe) {
            _log.debug("Expiry '{}' is not an xsd:dateTime, trying epoch millis: {}",
                    expiry, pe.getMessage());
        }

        long time = StringUtil.strToLong(expiry, -1);           // test for long
        if (time < 0) throw new IllegalArgumentException("Malformed expiry value");
        set(trigger, Instant.ofEpochMilli(time));
        return true;
    }


    public String toXML() {
        if (_timerType == TimerType.Nil) {
            return new String();  // Return empty but valid XML string for Nil timer type
        }

        XNode node = new XNode("timer");
        switch (_timerType) {
            case Duration -> {
                node.addChild("trigger", _trigger.name());
                node.addChild("duration", _duration.toString());
                if (_workDaysOnly) {
                    node.addChild("workdays", true);
                }
            }
            case Expiry -> {
                node.addChild("trigger", _trigger.name());
                node.addChild("expiry", _expiryTime.toEpochMilli());
            }
            case Interval -> {
                node.addChild("trigger", _trigger.name());
                XNode params = node.addChild("durationparams");
                params.addChild("ticks", _ticks);
                params.addChild("interval", _timeUnit.name());
            }
            case LateBound -> node.addChild("netparam", _variableName);
        }
        return node.toString();
    }


    public String toString() {
        if (_timerType == TimerType.Nil) return "Nil";
        String prefix = _trigger == YWorkItemTimer.Trigger.OnExecuting ? "Start: " : "Offer: ";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yy, h:mm a")
                .withZone(ZoneId.systemDefault());
        return switch (_timerType) {
            case Duration -> prefix + _duration.toString();
            case Expiry -> prefix + formatter.format(_expiryTime);
            case Interval -> prefix + _ticks + " " + _timeUnit.name();
            case LateBound -> "Variable: " + _variableName;
            default -> "Nil";
        };
    }

}
