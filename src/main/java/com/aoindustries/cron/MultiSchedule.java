/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2011, 2015, 2016  AO Industries, Inc.
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
 * A job is scheduled by matching any of a provided set of schedules.
 *
 * @author  AO Industries, Inc.
 */
public class MultiSchedule implements Schedule {

	private final Iterable<? extends Schedule> schedules;

	public MultiSchedule(Iterable<? extends Schedule> schedules) {
		this.schedules = schedules;
	}

	@Override
	public String toString() {
		// Java 8+: Use String.join
		StringBuilder sb = new StringBuilder();
		boolean didOne = false;
		for(Schedule schedule : schedules) {
			if(didOne) sb.append("; ");
			else didOne = true;
			sb.append(schedule);
		}
		return sb.toString();
	}

	@Override
	public boolean isCronJobScheduled(int minute, int hour, int dayOfMonth, int month, int dayOfWeek, int year) {
		for(Schedule schedule : schedules) {
			if(schedule.isCronJobScheduled(minute, hour, dayOfMonth, month, dayOfWeek, year)) return true;
		}
		return false;
	}
}
