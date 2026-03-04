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
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.openjdk.jmh.annotations;

/**
 * Stub implementation of JMH Blackhole.
 * Used for preventing dead code elimination in benchmarks.
 * This class is normally provided by JMH annotations at runtime.
 */
public class Blackhole {

    public Blackhole() {
        // Stub implementation
    }

    public void consume(Object obj) {
        // Prevent dead code elimination by consuming objects
        // In real JMH, this is handled by bytecode transformation
        if (obj == null) {
            throw new RuntimeException("Blackhole cannot consume null");
        }
        // Actual consumption happens at bytecode level
        // via -Djmh.blackhole=true or similar JVM options
    }

    public void consume(byte value) {
        // Stub for byte consumption
    }

    public void consume(short value) {
        // Stub for short consumption
    }

    public void consume(int value) {
        // Stub for int consumption
    }

    public void consume(long value) {
        // Stub for long consumption
    }

    public void consume(float value) {
        // Stub for float consumption
    }

    public void consume(double value) {
        // Stub for double consumption
    }

    public void consume(boolean value) {
        // Stub for boolean consumption
    }

    public void consume(char value) {
        // Stub for char consumption
    }
}