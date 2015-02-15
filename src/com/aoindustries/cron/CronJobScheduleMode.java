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
 * The set of possible concurrency settings for cron jobs.
 *
 * @see  CronJob
 *
 * @author  AO Industries, Inc.
 */
public enum CronJobScheduleMode {

    /**
     * Indicates the jobs should be ran concurrently when running together.
     */
    CONCURRENT,

    /**
     * Indicates the new job should be skipped to avoid running the same job concurrently.
     */
    SKIP
}
