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

package org.yawlfoundation.yawl.scheduling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parses a 5-field cron expression and computes the next matching instant.
 *
 * <p>Field order: {@code minute hour day-of-month month day-of-week}.
 * Each field supports:
 * <ul>
 *   <li>{@code *} — any value</li>
 *   <li>A single integer value (e.g. {@code 30})</li>
 *   <li>A comma-separated list (e.g. {@code 0,15,30,45})</li>
 *   <li>A range (e.g. {@code 1-5})</li>
 *   <li>A step (e.g. {@code * /15} for every 15 minutes)</li>
 * </ul>
 *
 * <p>All computations are performed in UTC.
 *
 * @since YAWL 6.0
 */
public final class CronExpression {

    private static final int IDX_MINUTE = 0;
    private static final int IDX_HOUR   = 1;
    private static final int IDX_DOM    = 2;
    private static final int IDX_MONTH  = 3;
    private static final int IDX_DOW    = 4;

    private final String expression;
    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final Set<Integer> daysOfMonth;
    private final Set<Integer> months;
    private final Set<Integer> daysOfWeek;

    private CronExpression(String expression,
                           Set<Integer> minutes,
                           Set<Integer> hours,
                           Set<Integer> daysOfMonth,
                           Set<Integer> months,
                           Set<Integer> daysOfWeek) {
        this.expression  = expression;
        this.minutes     = minutes;
        this.hours       = hours;
        this.daysOfMonth = daysOfMonth;
        this.months      = months;
        this.daysOfWeek  = daysOfWeek;
    }

    /**
     * Parses a 5-field cron expression string.
     *
     * @param expression 5-field cron string (minute hour dom month dow)
     * @return a validated {@code CronExpression}
     * @throws SchedulingException if the expression is null, blank, or syntactically invalid
     */
    public static CronExpression parse(String expression) throws SchedulingException {
        if (expression == null || expression.isBlank()) {
            throw new SchedulingException("Cron expression must not be null or blank");
        }
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 5) {
            throw new SchedulingException(
                "Cron expression must have exactly 5 fields (minute hour dom month dow), got: " + expression);
        }
        try {
            return new CronExpression(
                expression,
                parseField(fields[IDX_MINUTE], 0, 59),
                parseField(fields[IDX_HOUR],   0, 23),
                parseField(fields[IDX_DOM],     1, 31),
                parseField(fields[IDX_MONTH],   1, 12),
                parseField(fields[IDX_DOW],     0,  6)
            );
        } catch (NumberFormatException e) {
            throw new SchedulingException("Invalid cron expression '" + expression + "': " + e.getMessage());
        }
    }

    /** Returns a cron expression that fires every day at the given hour and minute (UTC). */
    public static CronExpression dailyAt(int hour, int minute) throws SchedulingException {
        return parse(minute + " " + hour + " * * *");
    }

    /** Returns a cron expression that fires every hour at the given minute (UTC). */
    public static CronExpression hourlyAt(int minute) throws SchedulingException {
        return parse(minute + " * * * *");
    }

    /** Returns a cron expression that fires every {@code n} minutes. */
    public static CronExpression everyMinutes(int n) throws SchedulingException {
        if (n < 1 || n > 59) {
            throw new SchedulingException("Interval must be between 1 and 59, got: " + n);
        }
        return parse("*/" + n + " * * * *");
    }

    /**
     * Computes the next instant matching this expression that is strictly after {@code after}.
     *
     * @param after the reference instant (exclusive lower bound)
     * @return the next matching instant in UTC
     * @throws SchedulingException if no next occurrence can be found within a 4-year window
     */
    public Instant nextAfter(Instant after) throws SchedulingException {
        Objects.requireNonNull(after, "after must not be null");

        // Advance to the next whole minute
        ZonedDateTime candidate = ZonedDateTime.ofInstant(after, ZoneOffset.UTC)
            .withSecond(0).withNano(0).plusMinutes(1);

        ZonedDateTime limit = candidate.plusYears(4);

        while (candidate.isBefore(limit)) {
            // Match month
            if (!months.contains(candidate.getMonthValue())) {
                candidate = candidate.withDayOfMonth(1).withHour(0).withMinute(0)
                    .plusMonths(1);
                continue;
            }
            // Match day-of-month
            if (!daysOfMonth.contains(candidate.getDayOfMonth())) {
                candidate = candidate.withHour(0).withMinute(0).plusDays(1);
                continue;
            }
            // Match day-of-week (Sunday=0 in cron, but DayOfWeek.SUNDAY=7 in java)
            int javaDow = candidate.getDayOfWeek().getValue(); // Mon=1..Sun=7
            int cronDow = javaDow == 7 ? 0 : javaDow;        // Sun=0..Sat=6
            if (!daysOfWeek.contains(cronDow)) {
                candidate = candidate.withHour(0).withMinute(0).plusDays(1);
                continue;
            }
            // Match hour
            if (!hours.contains(candidate.getHour())) {
                candidate = candidate.withMinute(0).plusHours(1);
                continue;
            }
            // Match minute
            if (!minutes.contains(candidate.getMinute())) {
                candidate = candidate.plusMinutes(1);
                continue;
            }
            return candidate.toInstant();
        }
        throw new SchedulingException(
            "No occurrence found within 4 years for expression: " + expression);
    }

    /** Returns the original cron expression string. */
    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "CronExpression{" + expression + "}";
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static Set<Integer> parseField(String field, int min, int max)
            throws SchedulingException, NumberFormatException {
        Set<Integer> values = new TreeSet<>();

        if ("*".equals(field)) {
            for (int i = min; i <= max; i++) values.add(i);
            return values;
        }

        for (String part : field.split(",")) {
            if (part.contains("/")) {
                // Step: base/step  e.g. */5 or 0/15
                String[] stepParts = part.split("/", 2);
                int step = Integer.parseInt(stepParts[1]);
                if (step < 1) throw new SchedulingException("Step must be >= 1 in field: " + field);
                int start = "*".equals(stepParts[0]) ? min : Integer.parseInt(stepParts[0]);
                for (int i = start; i <= max; i += step) values.add(validated(i, min, max, field));
            } else if (part.contains("-")) {
                // Range: low-high  e.g. 1-5
                String[] rangeParts = part.split("-", 2);
                int low  = Integer.parseInt(rangeParts[0]);
                int high = Integer.parseInt(rangeParts[1]);
                if (low > high) throw new SchedulingException("Invalid range in field: " + field);
                for (int i = low; i <= high; i++) values.add(validated(i, min, max, field));
            } else {
                values.add(validated(Integer.parseInt(part), min, max, field));
            }
        }
        return values;
    }

    private static int validated(int value, int min, int max, String field) throws SchedulingException {
        if (value < min || value > max) {
            throw new SchedulingException(
                "Value " + value + " out of range [" + min + "," + max + "] in field: " + field);
        }
        return value;
    }
}
