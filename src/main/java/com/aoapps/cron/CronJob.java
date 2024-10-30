/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2015, 2016, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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
 * along with ao-cron.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoapps.cron;

import com.aoapps.concurrent.Executors;

/**
 * One task that is performed on a routine basis.
 *
 * @author  AO Industries, Inc.
 */
public interface CronJob {

  /**
   * Gets the name for this cron job.
   *
   * <p>Defaults to <code>{@linkplain Object#getClass() getClass()}.{@link Class#getName() getName()}</code>.</p>
   */
  default String getName() {
    return getClass().getName();
  }

  /**
   * Gets the schedule for this cron job.
   * This is called once per minute for each job.
   */
  Schedule getSchedule();

  /**
   * The set of possible concurrency settings for cron jobs.
   */
  enum ScheduleMode {

    /**
     * Indicates the jobs should be ran concurrently when running together.
     */
    CONCURRENT,

    /**
     * Indicates the new job should be skipped to avoid running the same job concurrently.
     */
    SKIP
  }

  /**
   * Gets the job scheduling mode.
   *
   * <p>Defaults to {@link ScheduleMode#SKIP}.</p>
   *
   * @see  ScheduleMode
   */
  default ScheduleMode getScheduleMode() {
    return ScheduleMode.SKIP;
  }

  /**
   * The various executors that may be selected to run this job.
   */
  enum Executor {

    /**
     * Executors bounded by the number of processors present in the system.
     * This is best used for CPU-bound tasks.
     *
     * @see  Executors#getPerProcessor()
     */
    PER_PROCESSOR {
      @Override
      com.aoapps.concurrent.Executor getExecutor(Executors executors) {
        return executors.getPerProcessor();
      }
    },

    /**
     * This job will be executed on the main cron daemon thread.  This is
     * only appropriate for the fastest and most simple of jobs, as any job
     * not returning quickly will stall the execution of other jobs or lock
     * the daemon entirely.
     *
     * @see  Executors#getSequential()
     */
    SEQUENTIAL {
      @Override
      com.aoapps.concurrent.Executor getExecutor(Executors executors) {
        return executors.getSequential();
      }
    },

    /**
     * Unbounded executors.
     * This is more suited for I/O bound tasks.
     *
     * @see  Executors#getUnbounded()
     */
    UNBOUNDED {
      @Override
      com.aoapps.concurrent.Executor getExecutor(Executors executors) {
        return executors.getUnbounded();
      }
    };

    abstract com.aoapps.concurrent.Executor getExecutor(Executors executors);
  }

  /**
   * Gets the executor that should be used for this job.
   *
   * <p>Defaults to {@link Executor#UNBOUNDED}.</p>
   */
  default Executor getExecutor() {
    return Executor.UNBOUNDED;
  }

  /**
   * Gets the Thread priority for this job.
   *
   * <p>Defaults to {@link Thread#NORM_PRIORITY}.</p>
   *
   * @see  Thread#setPriority
   */
  default int getThreadPriority() {
    return Thread.NORM_PRIORITY;
  }

  /**
   * Performs the scheduled task.
   *
   * @see Schedule#isScheduled(int, int, int, int, int, int)
   */
  void run(int minute, int hour, int dayOfMonth, int month, int dayOfWeek, int year);
}
