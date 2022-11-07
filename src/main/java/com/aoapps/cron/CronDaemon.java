/*
 * ao-cron - Java cron-like task scheduling library.
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2015, 2016, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.concurrent.Executor;
import com.aoapps.concurrent.Executors;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run cron jobs based on their scheduling requirements.  Once per minute
 * it polls each cron job and runs it if it is currently scheduled.
 * <p>
 * If this main task is delayed for more than a minute, schedules will be missed.
 * Some possible causes include the process was stopped (<code>kill -STOP</code>)
 * or the machine was suspended/hibernated.
 * </p>
 *
 * @see  CronJob
 *
 * @author  AO Industries, Inc.
 */
public final class CronDaemon {

  /** Make no instances. */
  private CronDaemon() {
    throw new AssertionError();
  }

  private static final long MAX_SLEEP_TIME = 60L * 1000;

  /**
   * The cron daemon errors will be reported here (not the individual cron jobs).
   */
  @SuppressWarnings("NonConstantLogger")
  private static volatile Logger logger = Logger.getLogger(CronDaemon.class.getName());

  /**
   * Sets the logger for the cron daemon errors (not the individual cron jobs).
   *
   * @param logger  The logger or {@code null} to use the default logger
   */
  public static void setLogger(Logger logger) {
    CronDaemon.logger = (logger != null) ? logger : Logger.getLogger(CronDaemon.class.getName());
  }

  private static class Task implements Runnable {

    private final GregorianCalendar gcal = new GregorianCalendar();

    private int lastMinute = Integer.MIN_VALUE;
    private int lastHour = Integer.MIN_VALUE;
    private int lastDayOfMonth = Integer.MIN_VALUE;
    private int lastMonth = Integer.MIN_VALUE;
    private int lastDayOfWeek = Integer.MIN_VALUE;
    private int lastYear = Integer.MIN_VALUE;

    private Task() {
    }

    @Override
    @SuppressWarnings("TooBroadCatch")
    public void run() {
      long sleepTime = 0;
      try {
        while (sleepTime <= 0) {
          // Exit now if stopped
          synchronized (lock) {
            if (task != this) {
              return;
            }
          }
          // Get the new minute
          gcal.setTimeInMillis(System.currentTimeMillis());
          int minute     = gcal.get(Calendar.MINUTE);
          int hour       = gcal.get(Calendar.HOUR_OF_DAY);
          int dayOfMonth = gcal.get(Calendar.DAY_OF_MONTH);
          int month      = gcal.get(Calendar.MONTH);
          int dayOfWeek  = gcal.get(Calendar.DAY_OF_WEEK);
          int year       = gcal.get(Calendar.YEAR);

          // If the minute hasn't changed, then system sleep is not very precise, sleep another second
          if (
              minute     == lastMinute
                  && hour       == lastHour
                  && dayOfMonth == lastDayOfMonth
                  && month      == lastMonth
                  && dayOfWeek  == lastDayOfWeek
                  && year       == lastYear
          ) {
            sleepTime = 1000;
          } else {
            synchronized (lock) {
              for (int i = 0, size = cronJobs.size(); i < size; i++) {
                CronJob job = cronJobs.get(i);
                Logger jobLogger = loggers.get(i);
                try {
                  if (job.getSchedule().isScheduled(minute, hour, dayOfMonth, month, dayOfWeek, year)) {
                    runJob(job, jobLogger, minute, hour, dayOfMonth, month, dayOfWeek, year);
                  }
                } catch (ThreadDeath td) {
                  throw td;
                } catch (Throwable t) {
                  if (jobLogger.isLoggable(Level.SEVERE)) {
                    jobLogger.log(Level.SEVERE, "cron_job.name=" + job.getName(), t);
                  }
                }
              }
            }
            // Record last minute to avoid possible repeat on imprecise timers.
            lastMinute     = minute;
            lastHour       = hour;
            lastDayOfMonth = dayOfMonth;
            lastMonth      = month;
            lastDayOfWeek  = dayOfWeek;
            lastYear       = year;
            // Find the time until the next minute starts.
            gcal.add(Calendar.MINUTE, 1);
            gcal.set(Calendar.SECOND, 0);
            gcal.set(Calendar.MILLISECOND, 0);
            sleepTime = gcal.getTimeInMillis() - System.currentTimeMillis();
            // Be careful of system time changes
            if (sleepTime > MAX_SLEEP_TIME) {
              sleepTime = MAX_SLEEP_TIME;
            }
          }
        }
      } catch (ThreadDeath td) {
        sleepTime = MAX_SLEEP_TIME / 2;
        throw td;
      } catch (Throwable t) {
        logger.log(Level.SEVERE, null, t);
        sleepTime = MAX_SLEEP_TIME / 2;
      } finally {
        try {
          logger.log(Level.FINER, "Resubmitting task with sleepTime = {0}", sleepTime);
          assert sleepTime > 0;
          // Resubmit task in future
          synchronized (lock) {
            if (task == this && executors != null) {
              future = executors.getUnbounded().submit(this, sleepTime);
            }
          }
        } catch (ThreadDeath td) {
          throw td;
        } catch (Throwable t) {
          logger.log(Level.SEVERE, "Unable to resubmit task, cron daemon dying", t);
        }
      }
    }
  }

  private static class CronJobTask implements Runnable {

    private final CronJob job;
    private final Logger logger;
    private final int minute;
    private final int hour;
    private final int dayOfMonth;
    private final int month;
    private final int dayOfWeek;
    private final int year;

    private CronJobTask(CronJob job, Logger logger, int minute, int hour, int dayOfMonth, int month, int dayOfWeek, int year) {
      this.job        = job;
      this.logger     = logger;
      this.minute     = minute;
      this.hour       = hour;
      this.dayOfMonth = dayOfMonth;
      this.month      = month;
      this.dayOfWeek  = dayOfWeek;
      this.year       = year;
    }

    @Override
    public void run() {
      try {
        Thread thread = Thread.currentThread();
        final int oldPriority = thread.getPriority();
        int priority = job.getThreadPriority();
        try {
          if (oldPriority != priority) {
            try {
              thread.setPriority(priority);
            } catch (SecurityException e) {
              if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "cron_job.name=" + job.getName(), e);
              }
              priority = oldPriority;
            }
          }
          job.run(minute, hour, dayOfMonth, month, dayOfWeek, year);
        } finally {
          if (oldPriority != priority) {
            try {
              thread.setPriority(oldPriority);
            } catch (SecurityException e) {
              if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "cron_job.name=" + job.getName(), e);
              }
            }
          }
        }
      } catch (ThreadDeath td) {
        throw td;
      } catch (Throwable t) {
        if (logger.isLoggable(Level.SEVERE)) {
          logger.log(Level.SEVERE, "cron_job.name=" + job.getName(), t);
        }
      } finally {
        jobDone(this);
      }
    }
  }

  /**
   * The executors, closed when the last job is removed.
   */
  private static Executors executors;

  /**
   * The task for the currently running daemon.
   */
  private static Task task;
  private static Future<?> future;

  private static final List<CronJob> cronJobs = new ArrayList<>();
  private static final List<Logger> loggers = new ArrayList<>();
  private static final List<CronJobTask> runningJobTasks = new LinkedList<>();

  /**
   * Using cronJobs as the lock.
   */
  private static final Object lock = cronJobs;

  /**
   * Adds a {@link CronJob} to the list of jobs.  If the job is already
   * in the list, it will not be added again.
   */
  public static void addCronJob(CronJob job, Logger logger) {
    if (job != null) {
      synchronized (lock) {
        boolean found = false;
        for (CronJob cronJob : cronJobs) {
          if (cronJob == job) {
            found = true;
            break;
          }
        }
        if (!found) {
          cronJobs.add(job);
          loggers.add(logger);
          if (executors == null) {
            executors = new Executors();
          }
          if (task == null) {
            task = new Task();
            future = executors.getUnbounded().submit(task);
          }
        }
      }
    }
  }

  /**
   * Removes a {@link CronJob} from the list of jobs.
   */
  public static void removeCronJob(CronJob job) {
    synchronized (lock) {
      if (job != null) {
        for (int i = 0, len = cronJobs.size(); i < len; i++) {
          if (cronJobs.get(i) == job) {
            cronJobs.remove(i);
            loggers.remove(i);
            break;
          }
        }
      }
      if (cronJobs.isEmpty()) {
        task = null;
        if (future != null) {
          future.cancel(false);
          future = null;
        }
        if (executors != null) {
          executors.close();
          executors = null;
        }
      }
    }
  }

  private static void jobDone(CronJobTask task) {
    synchronized (lock) {
      Iterator<CronJobTask> iter = runningJobTasks.iterator();
      while (iter.hasNext()) {
        if (iter.next() == task) {
          iter.remove();
          return;
        }
      }
    }
    Logger l = logger;
    if (l.isLoggable(Level.WARNING)) {
      l.log(Level.WARNING, "cron_job.name=" + task.job.getName(), new Throwable("Warning: task not found"));
    }
  }

  /**
   * Shared implementation of running a job, whether scheduled or immediate.
   */
  @SuppressWarnings({"TooBroadCatch", "UseSpecificCatch"})
  private static void runJob(CronJob job, Logger logger, int minute, int hour, int dayOfMonth, int month, int dayOfWeek, int year) {
    assert Thread.holdsLock(lock);
    try {
      CronJob.ScheduleMode scheduleMode = job.getScheduleMode();
      boolean run;
      if (scheduleMode == CronJob.ScheduleMode.SKIP) {
        // Skip if already running
        run = true;
        for (CronJobTask jobTask : runningJobTasks) {
          if (jobTask.job == job) {
            run = false;
            break;
          }
        }
      } else if (scheduleMode == CronJob.ScheduleMode.CONCURRENT) {
        run = true;
      } else {
        throw new RuntimeException("Unknown value from CronJob.getScheduleMode: " + scheduleMode);
      }
      if (run) {
        CronJobTask jobTask = new CronJobTask(job, logger, minute, hour, dayOfMonth, month, dayOfWeek, year);
        runningJobTasks.add(jobTask);
        Executor executor = job.getExecutor().getExecutor(executors);
        Future<?> jobFuture = executor.submit(jobTask);
        if (executor == executors.getSequential()) {
          // Get call required to run the task
          jobFuture.get();
        }
      }
    } catch (ThreadDeath td) {
      throw td;
    } catch (InterruptedException e) {
      if (logger.isLoggable(Level.WARNING)) {
        logger.log(Level.WARNING, "cron_job.name=" + job.getName(), e);
      }
      // Restore the interrupted status
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      if (logger.isLoggable(Level.SEVERE)) {
        logger.log(Level.SEVERE, "cron_job.name=" + job.getName(), t);
      }
    }
  }

  /**
   * Runs a CronJob immediately if it is not already running or
   * allows concurrent execution.
   *
   * @exception  IllegalStateException  If the job has not been added.
   */
  public static void runImmediately(CronJob job) throws IllegalStateException {
    GregorianCalendar gcal = new GregorianCalendar();
    int minute     = gcal.get(Calendar.MINUTE);
    int hour       = gcal.get(Calendar.HOUR_OF_DAY);
    int dayOfMonth = gcal.get(Calendar.DAY_OF_MONTH);
    int month      = gcal.get(Calendar.MONTH);
    int dayOfWeek  = gcal.get(Calendar.DAY_OF_WEEK);
    int year       = gcal.get(Calendar.YEAR);
    synchronized (lock) {
      for (int i = 0, size = cronJobs.size(); i < size; i++) {
        if (job == cronJobs.get(i)) {
          runJob(job, loggers.get(i), minute, hour, dayOfMonth, month, dayOfWeek, year);
          return;
        }
      }
    }
    throw new IllegalStateException("CronJob has not been added.");
  }
}
