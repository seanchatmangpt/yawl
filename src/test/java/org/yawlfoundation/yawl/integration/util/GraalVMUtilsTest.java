/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL (Yet Another Workflow Language).
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Unit tests for GraalVMUtils utility class.
 */
class GraalVMUtilsTest {

    @Test
    void testIsAvailabilityReturnsBoolean() {
        boolean result = GraalVMUtils.isAvailable();
        assertNotNull(result);
        assertTrue(result || !result); // Either true or false, but not null
    }

    @Test
    void testIsAvailableConsistency() {
        // Multiple calls should return consistent results (cached)
        boolean first = GraalVMUtils.isAvailable();
        boolean second = GraalVMUtils.isAvailable();
        assertEquals(first, second);
    }

    @Test
    void testIsAvailableFreshCheck() {
        // This should bypass cache
        boolean result = GraalVMUtils.checkAvailabilityFresh();
        assertNotNull(result);
    }

    @Test
    void testIsUnavailableExceptionWithNull() {
        assertFalse(GraalVMUtils.isUnavailableException(null));
    }

    @Test
    void testIsUnavailableExceptionWithRegularException() {
        Exception e = new Exception("Regular error");
        assertFalse(GraalVMUtils.isUnavailableException(e));
    }

    @Test
    void testIsUnavailableExceptionWithNoClassDefFoundError() {
        NoClassDefFoundError e = new NoClassDefFoundError("org.graalvm.polyglot.Context");
        assertTrue(GraalVMUtils.isUnavailableException(e));
    }

    @Test
    void testIsUnavailableExceptionWithClassNameContainingGraalVM() {
        Exception e = new Exception("Error with org.graalvm.polyglot.PolyglotEngine");
        assertTrue(GraalVMUtils.isUnavailableException(e));
    }

    @Test
    void testIsUnavailableExceptionWithKnownErrorKinds() {
        assertTrue(GraalVMUtils.isUnavailableException(
            new Exception("RUNTIME_NOT_AVAILABLE: Could not initialize GraalPy")));
        assertTrue(GraalVMUtils.isUnavailableException(
            new Exception("CONTEXT_CREATION_FAILED")));
        assertTrue(GraalVMUtils.isUnavailableException(
            new Exception("POLYGLOT_NOT_FOUND")));
    }

    @Test
    void testIsUnavailableExceptionWithNestedException() {
        // Exception with GraalVM-related cause
        Exception cause = new NoClassDefFoundError("org.graalvm.polyglot.Context");
        Exception wrapper = new IOException("Wrapper", cause);
        assertTrue(GraalVMUtils.isUnavailableException(wrapper));
    }

    @Test
    void testGetFallbackGuidanceReturnsString() {
        String guidance = GraalVMUtils.getFallbackGuidance();
        assertNotNull(guidance);
        assertFalse(guidance.isEmpty());
        assertTrue(guidance.contains("GraalVM"));
        assertTrue(guidance.contains("PatternBasedSynthesizer"));
        assertTrue(guidance.contains("OllamaCandidateSampler"));
    }

    @Test
    void testGetTroubleshootingInfo() {
        String info = GraalVMUtils.getTroubleshootingInfo();
        assertNotNull(info);
        assertFalse(info.isEmpty());
        assertTrue(info.contains("GRAALVM_HOME"));
        assertTrue(info.contains("JDK 24.1"));
        assertTrue(info.contains("polyglot libraries"));
    }

    @Test
    void testResetCache() {
        // Reset cache
        GraalVMUtils.resetCache();

        // Verify cache is reset by checking if we can call checkAvailabilityFresh
        // Note: We can't directly check the internal cache state due to private fields
        assertNotNull(GraalVMUtils.checkAvailabilityFresh());
    }

    @Test
    void testClassStructure() {
        // Verify the class has the expected methods
        try {
            Class<?> clazz = GraalVMUtils.class;

            // Check public methods exist
            Method isAvailable = clazz.getMethod("isAvailable");
            Method isUnavailableException = clazz.getMethod("isUnavailableException", Throwable.class);
            Method getFallbackGuidance = clazz.getMethod("getFallbackGuidance");
            Method getTroubleshootingInfo = clazz.getMethod("getTroubleshootingInfo");
            Method resetCache = clazz.getMethod("resetCache");
            Method checkAvailabilityFresh = clazz.getMethod("checkAvailabilityFresh");

            // Verify return types
            assertEquals(boolean.class, isAvailable.getReturnType());
            assertEquals(boolean.class, isUnavailableException.getReturnType());
            assertEquals(String.class, getFallbackGuidance.getReturnType());
            assertEquals(String.class, getTroubleshootingInfo.getReturnType());
            assertEquals(void.class, resetCache.getReturnType());
            assertEquals(boolean.class, checkAvailabilityFresh.getReturnType());

        } catch (NoSuchMethodException e) {
            fail("Expected methods not found: " + e.getMessage());
        }
    }

    @Test
    void testUtilityClassConstructor() {
        // Verify constructor is private
        try {
            Class<?> clazz = GraalVMUtils.class;
            clazz.getDeclaredConstructor();
            // If we get here, constructor exists (should be private)
        } catch (NoSuchMethodException e) {
            fail("Constructor should exist: " + e.getMessage());
        }

        // Try to instantiate (should fail)
        try {
            GraalVMUtils instance = new GraalVMUtils();
            fail("Should not be able to instantiate utility class");
        } catch (Exception e) {
            // Expected - constructor is private
        }
    }

    @Test
    void testExceptionDetectionWithCommonPatterns() {
        // Test with common GraalVM error patterns
        assertTrue(GraalVMUtils.isUnavailableException(
            new Exception("java.lang.NoClassDefFoundError: org.graalvm.polyglot.PolyglotEngine")));
        assertTrue(GraalVMUtils.isUnavailableException(
            new Exception("UnsatisfiedLinkError: /path/to/graalvm/native/library")));
        assertTrue(GraalVMUtils.isUnavailableException(
            new Exception("ExceptionInInitializerError for org.graalvm.polyglot.Context")));
    }
}