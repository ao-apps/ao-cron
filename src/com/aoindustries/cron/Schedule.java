/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2011, 2015  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-cron.
 *
 * ao-cron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-cron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-cron.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.cron;

/**
 * Provides a schedule for a job.
 *
 * @author  AO Industries, Inc.
 */
public interface Schedule {

    /**
     * Determine if the job should run right now.
     *
     * @param minute 0-59
     * @param hour 0-23
     * @param dayOfMonth 1-31
     * @param month 0-11
     * @param dayOfWeek 1-7, <code>Calendar.SUNDAY</code> through <code>Calendar.SATURDAY</code>
     */
    boolean isCronJobScheduled(int minute, int hour, int dayOfMonth, int month, int dayOfWeek, int year);
}
