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

package org.yawlfoundation.yawl.engine.time.workdays;

import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.xml.datatype.Duration;

import org.yawlfoundation.yawl.util.StringUtil;

/**
 * @author Michael Adams
 * @date 2/6/17
 */
public class WorkDayAdjuster {

    private HolidayLoader _loader;


    /**
     * Adjust a Duration value so that it will apply only to working days,
     * taking the current moment as a starting date
     * @param duration the value to be adjusted
     * @return the adjusted Duration value
     */
    public Duration adjust(Duration duration) {
        return adjust(createCalendar(), duration);
    }


    /**
     * Adjust a Duration value so that it will apply only to working days
     * @param startDate the moment to start the adjustment from
     * @param duration the value to be adjusted
     * @return the adjusted Duration value
     */
    public Duration adjust(Calendar startDate, Duration duration) {
        Calendar endDate = addDuration(startDate, duration);
        endDate = adjust(startDate, endDate);
        return spanAsDuration(startDate, endDate);
    }


    /**
     * Adjust a Calendar value so that it will apply only to working days,
     * taking the current moment as a starting date
     * @param calendar the value to be adjusted
     * @return the adjusted Calendar value
     */
    public Calendar adjust(Calendar calendar) {
        return adjust(createCalendar(), calendar);
    }


    /**
     * Adjust a Calendar value so that it will apply only to working days
     * @param startDate the moment to start the adjustment from
     * @param endDate the value to be adjusted
     * @return the adjusted Calendar value
     */
    public Calendar adjust(Calendar startDate, Calendar endDate) {
        Calendar stepDate = (GregorianCalendar) startDate.clone();
        calcAdjustedEndDate(stepDate, endDate);
        return endDate;
    }


    /**
     * Adjust an Instant value so that it will apply only to working days,
     * taking the current moment as a starting date
     * @param instant the value to be adjusted
     * @return the adjusted Instant value
     */
    public Instant adjust(Instant instant) {
        return adjust(Instant.now(), instant);
    }


    /**
     * Adjust an Instant value so that it will apply only to working days
     * @param start the moment to start the adjustment from
     * @param end the value to be adjusted
     * @return the adjusted Instant value
     */
    public Instant adjust(Instant start, Instant end) {
        return adjust(createCalendar(start), createCalendar(end)).toInstant();
    }



    private Calendar createCalendar() {
        return createCalendar(Instant.now());
    }


    private Calendar createCalendar(Instant instant) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(instant.toEpochMilli());
        return calendar;
    }


    private Calendar addDuration(Calendar start, Duration duration) {
        Calendar summedDate = new GregorianCalendar();
        summedDate.setTimeInMillis(start.getTimeInMillis() + duration.getTimeInMillis(start));
        return summedDate;
    }


    private void calcAdjustedEndDate(Calendar step, Calendar end) {
        while (step.before(end)) {
            step.add(Calendar.DATE, 1);
            if (isWeekend(step) || isHoliday(step)) {
                end.add(Calendar.DATE, 1);
            }
        }
    }


    private Duration spanAsDuration(Calendar start, Calendar end) {
        long diff = end.getTimeInMillis() - start.getTimeInMillis();
        return StringUtil.msecsToDuration(diff).orElse(null);
    }


    private boolean isWeekend(Calendar date) {
        int day = date.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
    }


    private boolean isHoliday(Calendar date) {
        return getHolidayLoader().isHoliday(date);
    }


    private HolidayLoader getHolidayLoader() {
        if (_loader == null) {
            _loader = new HolidayLoader(true);
        }
        return _loader;
    }


    public static void main(String[] a) {
        Duration d = StringUtil.strToDuration("P1M3DT5H30M").orElse(null);
        Duration e = new WorkDayAdjuster().adjust(d);
        org.apache.logging.log4j.LogManager.getLogger(WorkDayAdjuster.class).info(
                "Adjusted duration: {}", e.toString());
    }
}
