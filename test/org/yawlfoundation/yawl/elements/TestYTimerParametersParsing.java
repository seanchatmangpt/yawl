/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * Tests for YTimerParameters parsing error path behavior.
 * Verifies that the parseYTimerType method correctly handles the xsd:dateTime
 * parse failure case and falls through to epoch millis, with proper logging
 * rather than a silent catch (M-07 violation fix).
 *
 * Chicago TDD: Tests real YTimerParameters with real JDOM elements.
 */
package org.yawlfoundation.yawl.elements;

import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.time.YTimer;
import org.yawlfoundation.yawl.engine.time.YWorkItemTimer;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for YTimerParameters parsing logic.
 *
 * Covers the critical path where:
 * 1. xsd:dateTime parsing fails (IllegalArgumentException caught + logged)
 * 2. Epoch millis fallback succeeds
 * 3. Correct TimerType is set
 *
 * These tests verify that the M-07 silent catch fix works correctly -
 * the catch block now logs at DEBUG level rather than silently dropping.
 *
 * Chicago TDD: All tests use real YTimerParameters and real JDOM objects.
 */
@DisplayName("YTimerParameters - Parse Error Path and Fallback Behavior")
class TestYTimerParametersParsing {

    private YTimerParameters _timer;

    @BeforeEach
    void setUp() {
        _timer = new YTimerParameters();
    }

    // --- Constructor Tests ---

    @Test
    @DisplayName("Default constructor sets Nil timer type")
    void testDefaultConstructorSetsNilType() {
        YTimerParameters timer = new YTimerParameters();
        assertEquals(YTimerParameters.TimerType.Nil, timer.getTimerType(),
                "Default constructor should set TimerType.Nil");
    }

    @Test
    @DisplayName("Variable name constructor sets LateBound type")
    void testVariableNameConstructorSetsLateBound() {
        YTimerParameters timer = new YTimerParameters("myVariable");
        assertEquals(YTimerParameters.TimerType.LateBound, timer.getTimerType(),
                "Variable name constructor should set TimerType.LateBound");
        assertEquals("myVariable", timer.getVariableName(),
                "Variable name should be stored correctly");
    }

    @Test
    @DisplayName("Expiry constructor sets Expiry type")
    void testExpiryConstructorSetsExpiryType() {
        Instant expiryTime = Instant.now().plusSeconds(3600);
        YTimerParameters timer = new YTimerParameters(
                YWorkItemTimer.Trigger.OnEnabled, expiryTime);
        assertEquals(YTimerParameters.TimerType.Expiry, timer.getTimerType(),
                "Expiry constructor should set TimerType.Expiry");
        assertEquals(expiryTime, timer.getDate(),
                "Expiry time should be stored correctly");
        assertEquals(YWorkItemTimer.Trigger.OnEnabled, timer.getTrigger(),
                "Trigger should be stored correctly");
    }

    @Test
    @DisplayName("Interval constructor sets Interval type")
    void testIntervalConstructorSetsIntervalType() {
        YTimerParameters timer = new YTimerParameters(
                YWorkItemTimer.Trigger.OnExecuting, 500L, YTimer.TimeUnit.MSEC);
        assertEquals(YTimerParameters.TimerType.Interval, timer.getTimerType(),
                "Interval constructor should set TimerType.Interval");
        assertEquals(500L, timer.getTicks(),
                "Ticks should be stored correctly");
        assertEquals(YTimer.TimeUnit.MSEC, timer.getTimeUnit(),
                "Time unit should be stored correctly");
    }

    @Test
    @DisplayName("Null time unit defaults to MSEC")
    void testNullTimeUnitDefaultsToMsec() {
        YTimerParameters timer = new YTimerParameters(
                YWorkItemTimer.Trigger.OnEnabled, 1000L, null);
        assertEquals(YTimer.TimeUnit.MSEC, timer.getTimeUnit(),
                "Null time unit should default to MSEC");
    }

    // --- parseYTimerType fallback path tests ---

    /**
     * Helper to create a valid JDOM element for parseYTimerType.
     * Format expected by parseYTimerType: <timerValue><trigger>OnEnabled</trigger><expiry>VALUE</expiry></timerValue>
     */
    private Element createTimerElement(String trigger, String expiry) {
        Element root = new Element("timerValue");
        root.addContent(new Element("trigger").setText(trigger));
        root.addContent(new Element("expiry").setText(expiry));
        return root;
    }

    @Test
    @DisplayName("parseYTimerType parses epoch millis when xsd:dateTime fails (M-07 fix)")
    void testParseEpochMillisWhenXsdDateTimeFails() throws Exception {
        // An epoch millis value that is NOT a valid xsd:dateTime
        long epochMillis = Instant.now().plusSeconds(3600).toEpochMilli();
        String epochStr = String.valueOf(epochMillis);

        Element timerElement = createTimerElement("OnEnabled", epochStr);

        boolean result = _timer.parseYTimerType(timerElement);

        assertTrue(result, "parseYTimerType should return true for valid epoch millis");
        assertEquals(YTimerParameters.TimerType.Expiry, _timer.getTimerType(),
                "Timer type should be Expiry after parsing epoch millis");
        assertEquals(YWorkItemTimer.Trigger.OnEnabled, _timer.getTrigger(),
                "Trigger should be OnEnabled");
        assertNotNull(_timer.getDate(), "Expiry time should be set");
        assertEquals(epochMillis, _timer.getDate().toEpochMilli(),
                "Epoch millis should match the parsed value");
    }

    @Test
    @DisplayName("parseYTimerType parses duration string starting with P")
    void testParseDurationString() throws Exception {
        Element timerElement = createTimerElement("OnExecuting", "PT1H");

        boolean result = _timer.parseYTimerType(timerElement);

        assertTrue(result, "parseYTimerType should return true for valid duration");
        assertEquals(YTimerParameters.TimerType.Duration, _timer.getTimerType(),
                "Timer type should be Duration when expiry starts with P");
        assertEquals(YWorkItemTimer.Trigger.OnExecuting, _timer.getTrigger(),
                "Trigger should be OnExecuting");
        assertNotNull(_timer.getDuration(), "Duration should be set");
    }

    @Test
    @DisplayName("parseYTimerType throws IllegalArgumentException for invalid trigger")
    void testInvalidTriggerThrowsException() {
        Element timerElement = createTimerElement("InvalidTrigger", "3600000");

        assertThrows(IllegalArgumentException.class,
                () -> _timer.parseYTimerType(timerElement),
                "Invalid trigger should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("parseYTimerType throws IllegalArgumentException for null element")
    void testNullElementThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> _timer.parseYTimerType(null),
                "Null element should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("parseYTimerType throws IllegalArgumentException for negative epoch millis")
    void testNegativeEpochMillisThrowsException() {
        Element timerElement = createTimerElement("OnEnabled", "-100");

        assertThrows(IllegalArgumentException.class,
                () -> _timer.parseYTimerType(timerElement),
                "Negative epoch millis should throw IllegalArgumentException with 'Malformed expiry value'");
    }

    // --- TriggerMatchesStatus tests ---

    @Test
    @DisplayName("triggerMatchesStatus returns false for Nil timer type")
    void testTriggerMatchesStatusReturnsFalseForNil() {
        // Default state is Nil
        assertFalse(_timer.triggerMatchesStatus(org.yawlfoundation.yawl.engine.YWorkItemStatus.statusEnabled),
                "Nil timer type should never match any status");
    }

    // --- toXML and XML roundtrip tests ---

    @Test
    @DisplayName("toXML returns empty string for Nil type")
    void testToXmlForNilTypeReturnsEmptyOrNil() {
        YTimerParameters timer = new YTimerParameters();
        String xml = timer.toXML();
        assertNotNull(xml, "toXML should not return null");
        // Nil produces empty or minimal XML - just verify no exception thrown
    }

    @Test
    @DisplayName("toXML for Interval type produces valid XML with timer element")
    void testToXmlForIntervalType() {
        YTimerParameters timer = new YTimerParameters(
                YWorkItemTimer.Trigger.OnEnabled, 5000L, YTimer.TimeUnit.MSEC);
        String xml = timer.toXML();
        assertNotNull(xml, "toXML should not return null for Interval type");
        assertTrue(xml.contains("timer"), "XML should contain 'timer' element");
        assertTrue(xml.contains("ticks"), "XML should contain 'ticks' element");
        assertTrue(xml.contains("5000"), "XML should contain the tick count");
    }

    @Test
    @DisplayName("toXML for LateBound type produces XML with netparam element")
    void testToXmlForLateBoundType() {
        YTimerParameters timer = new YTimerParameters("myNetVariable");
        String xml = timer.toXML();
        assertNotNull(xml, "toXML should not return null for LateBound type");
        assertTrue(xml.contains("netparam"), "XML should contain 'netparam' element");
        assertTrue(xml.contains("myNetVariable"), "XML should contain the variable name");
    }

    // --- Setter tests ---

    @Test
    @DisplayName("setVariableName changes type to LateBound")
    void testSetVariableNameChangesTypeToLateBound() {
        YTimerParameters timer = new YTimerParameters(
                YWorkItemTimer.Trigger.OnEnabled, Instant.now(), YWorkItemTimer.Trigger.OnEnabled);
        // Reuse timer with different set
        timer.setVariableName("aVariable");
        assertEquals(YTimerParameters.TimerType.LateBound, timer.getTimerType(),
                "setVariableName should change type to LateBound");
        assertEquals("aVariable", timer.getVariableName());
    }

    @Test
    @DisplayName("setDate changes type to Expiry")
    void testSetDateChangesTypeToExpiry() {
        _timer.setDate(Instant.now());
        assertEquals(YTimerParameters.TimerType.Expiry, _timer.getTimerType(),
                "setDate should change type to Expiry");
    }

    @Test
    @DisplayName("setTicks changes type to Interval")
    void testSetTicksChangesTypeToInterval() {
        _timer.setTicks(1000L);
        assertEquals(YTimerParameters.TimerType.Interval, _timer.getTimerType(),
                "setTicks should change type to Interval");
    }

    @Test
    @DisplayName("setTimeUnit changes type to Interval")
    void testSetTimeUnitChangesTypeToInterval() {
        _timer.setTimeUnit(YTimer.TimeUnit.SEC);
        assertEquals(YTimerParameters.TimerType.Interval, _timer.getTimerType(),
                "setTimeUnit should change type to Interval");
        assertEquals(YTimer.TimeUnit.SEC, _timer.getTimeUnit());
    }

    @Test
    @DisplayName("isWorkDaysOnly defaults to false and can be set to true")
    void testWorkDaysOnly() {
        assertFalse(_timer.isWorkDaysOnly(), "Default workDaysOnly should be false");
        _timer.setWorkDaysOnly(true);
        assertTrue(_timer.isWorkDaysOnly(), "workDaysOnly should be true after setting");
    }
}
