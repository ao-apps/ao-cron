/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2015, 2016, 2019, 2020  AO Industries, Inc.
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
 * One task that is performed on a routine basis.
 *
 * @author  AO Industries, Inc.
 */
public interface CronJob {

	/**
	 * Gets the schedule for this cron job.
	 * This is called once per minute for each job.
	 */
	Schedule getCronJobSchedule();

	/**
	 * Gets the job scheduling mode.
	 * <p>
	 * Defaults to {@link CronJobScheduleMode#SKIP}.
	 * </p>
	 *
	 * @see  CronJobScheduleMode
	 */
	default CronJobScheduleMode getCronJobScheduleMode() {
		return CronJobScheduleMode.SKIP;
	}

	/**
	 * Gets the name for this cron job.
	 * <p>
	 * Defaults to <code>{@linkplain Object#getClass() getClass()}.{@link Class#getName() getName()}</code>.
	 * </p>
	 */
	default String getCronJobName() {
		return getClass().getName();
	}

	/**
	 * Performs the scheduled task.
	 *
	 * @see Schedule#isCronJobScheduled(int, int, int, int, int, int)
	 */
	void runCronJob(int minute, int hour, int dayOfMonth, int month, int dayOfWeek, int year);

	/**
	 * Gets the Thread priority for this job.
	 * <p>
	 * Defaults to <code>{@linkplain Thread#NORM_PRIORITY} - 2</code>.
	 * </p>
	 *
	 * @see  Thread#setPriority
	 */
	default int getCronJobThreadPriority() {
		return Thread.NORM_PRIORITY - 2;
	}

	// TODO: Executor.Type
}
