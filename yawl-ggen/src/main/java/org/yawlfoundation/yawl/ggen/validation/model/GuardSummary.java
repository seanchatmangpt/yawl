/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Summary statistics for guard validation results.
 * Tracks violation counts by pattern type and total violations.
 */
public class GuardSummary {
    private int h_todo_count = 0;
    private int h_mock_count = 0;
    private int h_stub_count = 0;
    private int h_empty_count = 0;
    private int h_fallback_count = 0;
    private int h_lie_count = 0;
    private int h_silent_count = 0;

    /**
     * Increment the count for a given pattern.
     *
     * @param pattern the pattern name (e.g., H_TODO)
     */
    public void increment(String pattern) {
        switch (pattern) {
            case "H_TODO" -> h_todo_count++;
            case "H_MOCK" -> h_mock_count++;
            case "H_STUB" -> h_stub_count++;
            case "H_EMPTY" -> h_empty_count++;
            case "H_FALLBACK" -> h_fallback_count++;
            case "H_LIE" -> h_lie_count++;
            case "H_SILENT" -> h_silent_count++;
        }
    }

    /**
     * Get the total number of violations across all patterns.
     */
    public int getTotalViolations() {
        return h_todo_count + h_mock_count + h_stub_count + h_empty_count +
               h_fallback_count + h_lie_count + h_silent_count;
    }

    /**
     * Get all counts as a map for JSON serialization.
     */
    public Map<String, Integer> asMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("h_todo_count", h_todo_count);
        map.put("h_mock_count", h_mock_count);
        map.put("h_stub_count", h_stub_count);
        map.put("h_empty_count", h_empty_count);
        map.put("h_fallback_count", h_fallback_count);
        map.put("h_lie_count", h_lie_count);
        map.put("h_silent_count", h_silent_count);
        map.put("total_violations", getTotalViolations());
        return map;
    }

    // Getters
    public int getH_todo_count() { return h_todo_count; }
    public int getH_mock_count() { return h_mock_count; }
    public int getH_stub_count() { return h_stub_count; }
    public int getH_empty_count() { return h_empty_count; }
    public int getH_fallback_count() { return h_fallback_count; }
    public int getH_lie_count() { return h_lie_count; }
    public int getH_silent_count() { return h_silent_count; }
}
